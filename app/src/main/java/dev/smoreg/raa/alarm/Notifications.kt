package dev.smoreg.raa.alarm

import android.Manifest
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
import dev.smoreg.raa.ui.formatTime
import dev.smoreg.raa.ui.ringing.RingingActivity

object Notifications {
    const val RINGING_ID = 1
    private const val CH_RINGING = "ringing"
    private const val CH_UPCOMING = "upcoming"
    private const val CH_GAVE_UP = "gave_up"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                NotificationChannel(CH_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_GAVE_UP, context.getString(R.string.channel_gave_up), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun ringing(context: Context, s: RingSession?): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarm = s?.alarm
        val title = alarm?.label?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        return NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(howToStop(alarm?.dismissMode)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    fun upcoming(context: Context, alarm: Alarm, ringAt: Long) {
        val early = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            RingingActivity.early(context, alarm.id, ringAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.upcoming_title, formatTime(context, alarm.hour, alarm.minute)))
            .setContentText(alarm.label.ifBlank { context.getString(howToStop(alarm.dismissMode)) })
            .setContentIntent(early)
            .addAction(0, context.getString(R.string.upcoming_dismiss_early), early)
            .setAutoCancel(true)
            .build()
        post(context, upcomingId(alarm.id), n)
    }

    fun cancelUpcoming(context: Context, alarmId: Long) =
        NotificationManagerCompat.from(context).cancel(upcomingId(alarmId))

    fun gaveUp(context: Context, alarm: Alarm) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_GAVE_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.gave_up_title, formatTime(context, alarm.hour, alarm.minute)))
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
