package dev.smoreg.raa.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.smoreg.raa.RaaApp
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = Trigger.valueOf(intent.action ?: return)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val at = intent.getLongExtra(EXTRA_AT, 0)
        when (trigger) {
            Trigger.RING -> RingingService.start(context, id, at, sunrise = false)
            Trigger.SUNRISE -> RingingService.start(context, id, at, sunrise = true)
            Trigger.WATCHDOG -> RingingService.resume(context)
            Trigger.RADIO_CHECK -> launchAsync {
                val alarm = RaaApp.container.db.alarms().get(id) ?: return@launchAsync
                if (alarm.enabled && alarm.usesRadio) RadioService.start(context, id, alarm.radioUrl, at)
            }
            Trigger.UPCOMING -> launchAsync {
                val c = RaaApp.container
                val alarm = c.db.alarms().get(id) ?: return@launchAsync
                if (alarm.enabled) Notifications.upcoming(context, alarm, at)
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "alarm_id"
        private const val EXTRA_AT = "at"

        private fun intent(context: Context, t: Trigger, id: Long) =
            Intent(context, AlarmReceiver::class.java).setAction(t.name).putExtra(EXTRA_ID, id)

        /**
         * Fixed at the four triggers there were first, so alarms set before an update keep their codes.
         * A code shared with another alarm's trigger is still a distinct PendingIntent: the action differs.
         */
        private const val CODE_STRIDE = 4

        private fun code(t: Trigger, id: Long) = (id * CODE_STRIDE + t.ordinal).toInt()

        fun pending(context: Context, t: Trigger, id: Long, at: Long): PendingIntent = PendingIntent.getBroadcast(
            context, code(t, id), intent(context, t, id).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun existing(context: Context, t: Trigger, id: Long): PendingIntent? = PendingIntent.getBroadcast(
            context, code(t, id), intent(context, t, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Alarms do not survive reboots or clock changes; put them back. After a reboot, also resume a ring cut short. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = launchAsync {
        RaaApp.container.scheduler.rescheduleAll()
        if (intent.action in BOOT_ACTIONS) RingingService.resume(context)
    }

    private companion object {
        val BOOT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)
    }
}

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    RaaApp.container.scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
