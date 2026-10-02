package dev.smoreg.raa.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.smoreg.raa.RaaApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Past the ring the alarm has either taken the stream or does not want it. */
private const val GIVE_UP_AFTER_RING_MS = 60_000L
private const val POLL_MS = 1_000L

/**
 * Keeps the process and the network up while [dev.smoreg.raa.wake.RadioTuner] plays the stream
 * muted before the ring. Stops once the ringing alarm claims the stream, or the stream fails, in
 * which case the alarm plays its melody.
 */
class RadioService : LifecycleService() {
    private var watch: Job? = null
    private var deadline = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifications.RADIO_CHECK_ID, Notifications.radioCheck(this), type)
        }.onFailure { Log.e("RadioService", "foreground refused", it) }.isSuccess
        val url = intent?.getStringExtra(EXTRA_URL)
        if (!started || url == null) {
            // No check without the foreground grant; the alarm will use its melody.
            if (watch?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        val tuner = RaaApp.container.radio
        tuner.warmUp(intent.getLongExtra(EXTRA_ID, -1), url)
        deadline = maxOf(deadline, intent.getLongExtra(EXTRA_AT, 0) + GIVE_UP_AFTER_RING_MS)
        if (watch?.isActive != true) {
            watch = lifecycleScope.launch {
                while (tuner.alive && !tuner.claimed && System.currentTimeMillis() < deadline) delay(POLL_MS)
                if (!tuner.claimed) tuner.release()
                ServiceCompat.stopForeground(this@RadioService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_ID = "alarm_id"
        private const val EXTRA_URL = "url"
        private const val EXTRA_AT = "at"

        fun start(context: Context, alarmId: Long, url: String, ringAt: Long) {
            val intent = Intent(context, RadioService::class.java)
                .putExtra(EXTRA_ID, alarmId).putExtra(EXTRA_URL, url).putExtra(EXTRA_AT, ringAt)
            // Refused means no check, and no check means the melody: nothing else to do.
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.e("RadioService", "start refused", it) }
        }
    }
}
