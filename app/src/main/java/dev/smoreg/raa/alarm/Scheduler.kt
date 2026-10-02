package dev.smoreg.raa.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.smoreg.raa.MainActivity
import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.AlarmDao
import dev.smoreg.raa.data.Settings
import dev.smoreg.raa.wake.RadioTuner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

enum class Trigger { SUNRISE, RING, UPCOMING, WATCHDOG, RADIO_CHECK }

/** The two-hour notice is not time-critical, but an unbounded inexact alarm can be an hour late. */
private const val NOTICE_WINDOW_MS = 10 * 60_000L
/** The stream starts muted this long before the ring, to prove it plays before it is needed. */
private const val RADIO_LEAD_MS = 2 * 60_000L
/** Less time than this before the ring is not enough to call a stream steady: the melody it is. */
private const val RADIO_MIN_LEAD_MS = RadioTuner.STEADY_MS + 10_000L

class Scheduler(
    private val context: Context,
    private val alarms: AlarmDao,
    private val settings: Settings,
) {
    private val am = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    suspend fun rescheduleAll() = alarms.all().forEach { schedule(it) }

    /** Serialized: two overlapping calls for one alarm must not leave a cancelled alarm behind. */
    suspend fun schedule(alarm: Alarm) = lock.withLock {
        cancel(alarm.id)
        val now = ZonedDateTime.now()
        val ring = NextTrigger.ring(alarm, now) ?: return
        val ringMs = ring.toInstant().toEpochMilli()

        setClock(ringMs, AlarmReceiver.pending(context, Trigger.RING, alarm.id, ringMs))
        NextTrigger.sunrise(alarm, ring, now)?.let {
            setExact(it.toInstant().toEpochMilli(), AlarmReceiver.pending(context, Trigger.SUNRISE, alarm.id, ringMs))
        }
        if (alarm.usesRadio) {
            val nowMs = now.toInstant().toEpochMilli()
            val checkAt = maxOf(ringMs - RADIO_LEAD_MS, nowMs)
            if (ringMs - checkAt >= RADIO_MIN_LEAD_MS) {
                setExact(checkAt, AlarmReceiver.pending(context, Trigger.RADIO_CHECK, alarm.id, ringMs))
            }
        }
        val notice = ring.minusHours(2)
        if (settings.current().upcomingNotice && alarm.snoozeUntil == 0L && notice.isAfter(now)) {
            am.setWindow(
                AlarmManager.RTC,
                notice.toInstant().toEpochMilli(),
                NOTICE_WINDOW_MS,
                AlarmReceiver.pending(context, Trigger.UPCOMING, alarm.id, ringMs),
            )
        }
    }

    fun cancel(alarmId: Long) {
        for (t in listOf(Trigger.SUNRISE, Trigger.RING, Trigger.UPCOMING, Trigger.RADIO_CHECK)) {
            AlarmReceiver.existing(context, t, alarmId)?.let(am::cancel)
        }
        Notifications.cancelUpcoming(context, alarmId)
    }

    /** Re-raises a killed ringing process within a minute; see [RingingService]. */
    fun armWatchdog(alarmId: Long) {
        val at = System.currentTimeMillis() + 60_000
        val op = AlarmReceiver.pending(context, Trigger.WATCHDOG, alarmId, at)
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    fun disarmWatchdog(alarmId: Long) {
        AlarmReceiver.existing(context, Trigger.WATCHDOG, alarmId)?.let(am::cancel)
    }

    fun canExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** Exact and Doze-proof, but not shown as the phone's next alarm: that is the ring's job. */
    private fun setExact(at: Long, op: PendingIntent) {
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    private fun setClock(at: Long, op: PendingIntent) {
        if (canExact()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }
}
