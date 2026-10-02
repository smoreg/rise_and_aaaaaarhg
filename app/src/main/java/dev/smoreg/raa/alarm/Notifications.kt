package dev.smoreg.raa.alarm

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.smoreg.raa.MainActivity
import dev.smoreg.raa.R
import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.DismissMode
import dev.smoreg.raa.ui.formatClock
import dev.smoreg.raa.ui.ringing.RingingActivity

object Notifications {
    const val RINGING_ID = 1
    const val RADIO_CHECK_ID = 3
    private const val CH_RINGING = "ringing"
    /** Same notification while the ringing screen is already in front: must not pop up over it. */
    private const val CH_RINGING_QUIET = "ringing_quiet"
    private const val CH_UPCOMING = "upcoming"
    private const val CH_GAVE_UP = "gave_up"
    private const val CH_RADIO_CHECK = "radio_check"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                NotificationChannel(CH_RINGING_QUIET, context.getString(R.string.channel_ringing_quiet), NotificationManager.IMPORTANCE_LOW)
                    .apply { setSound(null, null); enableVibration(false) },
                NotificationChannel(CH_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_GAVE_UP, context.getString(R.string.channel_gave_up), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_RADIO_CHECK, context.getString(R.string.channel_radio_check), NotificationManager.IMPORTANCE_MIN),
            ),
        )
    }

    /**
     * [screenVisible]: the ringing screen is already in front, so no heads-up should cover it.
     * The notification stays ongoing either way; it is what keeps the service alive.
     */
    fun ringing(context: Context, s: RingSession?, screenVisible: Boolean = false): android.app.Notification {
        val open = openRinging(context)
        val alarm = s?.alarm
        val title = alarm?.label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        return NotificationCompat.Builder(context, if (screenVisible) CH_RINGING_QUIET else CH_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(howToStop(alarm?.dismissMode)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (screenVisible) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Android otherwise holds a foreground service's notification back for 10 s: 10 s of sound with no screen.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .apply { if (!screenVisible) setFullScreenIntent(open, true) }
            .build()
    }

    private fun openRinging(context: Context): PendingIntent {
        val intent = Intent(context, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // From API 34 the creator must say that this intent may start an activity from the background.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        return PendingIntent.getActivity(context, 0, intent, flags, options)
    }

    fun upcoming(context: Context, alarm: Alarm, ringAt: Long) {
        val early = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            RingingActivity.early(context, alarm.id, ringAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.upcoming_title, formatClock(context, alarm.hour, alarm.minute)))
            .setContentText(alarm.label.ifBlank { context.getString(howToStop(alarm.dismissMode)) })
            .setContentIntent(early)
            .addAction(0, context.getString(R.string.upcoming_dismiss_early), early)
            .setAutoCancel(true)
            .build()
        post(context, upcomingId(alarm.id), n)
    }

    fun update(context: Context, id: Int, n: android.app.Notification) = post(context, id, n)

    fun cancelUpcoming(context: Context, alarmId: Long) =
        NotificationManagerCompat.from(context).cancel(upcomingId(alarmId))

    fun gaveUp(context: Context, alarm: Alarm) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_GAVE_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.gave_up_title, formatClock(context, alarm.hour, alarm.minute)))
            .setContentText(context.getString(R.string.gave_up_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(context, gaveUpId(alarm.id), n)
    }

    fun startFailed(context: Context) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.start_failed_title))
            .setContentText(context.getString(R.string.start_failed_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        post(context, START_FAILED_ID, n)
    }

    /** Required to keep the stream check running; minimal importance, so it stays out of sight. */
    fun radioCheck(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CH_RADIO_CHECK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.radio_check_notice))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun howToStop(mode: DismissMode?) = when (mode) {
        DismissMode.QR -> R.string.notif_stop_qr
        DismissMode.SHAKE -> R.string.notif_stop_shake
        else -> R.string.notif_stop_phone
    }

    // Separate id ranges per kind so an alarm's notices never replace each other.
    private const val START_FAILED_ID = 2
    private fun upcomingId(alarmId: Long) = 1_000_000 + alarmId.toInt()
    private fun gaveUpId(alarmId: Long) = 2_000_000 + alarmId.toInt()

    private fun post(context: Context, id: Int, n: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, n)
    }
}
