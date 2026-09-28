package dev.smoreg.raa.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.LightMode
import dev.smoreg.raa.data.RingRecord
import dev.smoreg.raa.ui.ringing.RingingActivity
import dev.smoreg.raa.wake.Buzzer
import dev.smoreg.raa.wake.SoundPlayer
import dev.smoreg.raa.wake.Torch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the phone awake while an alarm rings and drives sound, vibration and flashlight from the
 * [Ringer] session. The notification has no action buttons on purpose: a smartwatch mirrors it,
 * and nothing on the wrist should be able to stop the alarm.
 */
class RingingService : LifecycleService() {
    private lateinit var sound: SoundPlayer
    private lateinit var buzzer: Buzzer
    private lateinit var torch: Torch
    private lateinit var wakeLock: PowerManager.WakeLock
    private var loop: Job? = null
    private var watchdogArmedAt = 0L

    override fun onCreate() {
        super.onCreate()
        sound = SoundPlayer(this)
        buzzer = Buzzer(this)
        torch = Torch(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "raa:ringing")
            .apply { acquire(WAKE_LOCK_MAX_MS) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        lifecycleScope.launch {
            when (intent?.action) {
                ACTION_START -> onTrigger(
                    intent.getLongExtra(EXTRA_ID, -1),
                    intent.getLongExtra(EXTRA_AT, 0),
                    intent.getBooleanExtra(EXTRA_SUNRISE, false),
                )
                // A null intent is a sticky restart after the process was killed.
                ACTION_RESUME, null -> onResume()
            }
            if (Ringer.session.value == null) {
                stopSelf()
                return@launch
            }
            goForeground()
            openScreen()
            if (loop == null) loop = lifecycleScope.launch { run() }
        }
        return START_STICKY
    }

    private suspend fun onTrigger(id: Long, ringAt: Long, sunrise: Boolean) {
        val c = RaaApp.container
        // A test ring or an early-dismiss screen is not a real alarm: a real trigger replaces it.
        Ringer.dropIfNotReal()
        val current = Ringer.session.value
        if (current != null) {
            when {
                current.alarm.id == id -> if (!sunrise) Ringer.startRinging()
                // Another alarm's light may start later; only its sound is worth retrying.
                !sunrise -> Ringer.deferBusy(id)
            }
            return
        }
        val alarm = c.db.alarms().get(id) ?: return
        if (!alarm.enabled) return
        val now = System.currentTimeMillis()
        val started = Ringer.begin(
            RingSession(
                alarm = alarm,
                phase = if (sunrise) Phase.SUNRISE else Phase.RING,
                ringAt = ringAt,
                sunriseStart = if (sunrise) now else ringAt,
            ),
        )
        if (started) c.settings.saveRingRecord(RingRecord(id, ringAt, now))
    }

    private suspend fun onResume() {
        if (Ringer.session.value != null) return
        val c = RaaApp.container
        val record = c.settings.ringRecord() ?: return
        val alarm = c.db.alarms().get(record.alarmId)
        val autoStop = c.settings.current().autoStopMinutes * 60_000L
        val now = System.currentTimeMillis()
        if (alarm == null || (autoStop > 0 && now - record.startedAt > autoStop)) {
            c.settings.saveRingRecord(null)
            return
        }
        val phase = if (now < record.ringAt) Phase.SUNRISE else Phase.RING
        Ringer.begin(
            RingSession(
                alarm = alarm,
                phase = phase,
                ringAt = record.ringAt,
                sunriseStart = record.startedAt,
                startedAt = record.startedAt,
                quietCount = record.quietCount,
            ),
        )
    }

    private suspend fun run() {
        val c = RaaApp.container
        val autoStopMs = c.settings.current().autoStopMinutes * 60_000L
        while (true) {
            val s = Ringer.session.value ?: break
            val now = System.currentTimeMillis()
            if (s.phase == Phase.SUNRISE && now >= s.ringAt) {
                Ringer.startRinging()
                continue
            }
            if (s.phase == Phase.RING && autoStopMs > 0 && elapsed() - s.ringingSince >= autoStopMs) {
                Ringer.finish(Outcome.GAVE_UP)
                break
            }
            if (!s.test && elapsed() - watchdogArmedAt > WATCHDOG_REARM_MS) {
                c.scheduler.armWatchdog(s.alarm.id)
                watchdogArmedAt = elapsed()
            }

            val loud = s.phase == Phase.RING && !s.muted()
            if (loud) {
                if (sound.active) sound.resume() else sound.start(s.alarm.sound, s.alarm.volume, s.alarm.rampSeconds)
                sound.tick()
            } else {
                sound.pause()
            }
            buzzer.set(loud && s.alarm.vibrate)
            torch.set(torchLevel(s, now, loud))
            delay(TICK_MS)
        }
        stopEverything()
    }

    private fun torchLevel(s: RingSession, now: Long, loud: Boolean): Float = when {
        s.cameraActive -> 0f
        s.alarm.light == LightMode.SUNRISE -> s.sunriseProgress(now)
        s.alarm.light == LightMode.STROBE && loud -> if ((now / STROBE_HALF_PERIOD_MS) % 2 == 0L) 1f else 0f
        else -> 0f
    }

    private fun stopEverything() {
        silence()
        loop = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        silence()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun silence() {
        sound.release()
        buzzer.set(false)
        torch.off()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        // systemExempted is granted with the exact-alarm permission. Without it there is no legal way to ring.
        runCatching {
            ServiceCompat.startForeground(this, Notifications.RINGING_ID, Notifications.ringing(this, Ringer.session.value), type)
        }.onFailure {
            Log.e("RingingService", "foreground refused", it)
            Notifications.startFailed(this)
        }
    }

    /** The full-screen intent covers a locked screen; on an unlocked one try to come forward ourselves. */
    private fun openScreen() {
        runCatching {
            startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_SHOW = "show"
        private const val EXTRA_ID = "alarm_id"
        private const val EXTRA_AT = "at"
        private const val EXTRA_SUNRISE = "sunrise"
        private const val TICK_MS = 125L
        private const val WATCHDOG_REARM_MS = 30_000L
        private const val WAKE_LOCK_MAX_MS = 3 * 60 * 60 * 1000L

        fun start(context: Context, id: Long, ringAt: Long, sunrise: Boolean) = launch(
            context,
            Intent(context, RingingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_AT, ringAt).putExtra(EXTRA_SUNRISE, sunrise),
        )

        fun resume(context: Context) =
            launch(context, Intent(context, RingingService::class.java).setAction(ACTION_RESUME))

        /** For sessions begun in-process (the editor's test ring). */
        fun show(context: Context) =
            launch(context, Intent(context, RingingService::class.java).setAction(ACTION_SHOW))

        private fun launch(context: Context, intent: Intent) {
            runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure {
                // Only happens without the exact-alarm grant; the notification is the last way to be heard.
                Log.e("RingingService", "start refused", it)
                Notifications.startFailed(context)
            }
        }
    }
}
