package dev.smoreg.raa.ui.health

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.AppSettings

/** Everything the phone can do to stop an alarm from ringing, and where to fix it. */
object Health {
    fun notifications(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun fullScreen(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun exact() = RaaApp.container.scheduler.canExact()

    fun battery(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun camera(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Problems that can make an alarm stay silent or hidden. The camera has a fallback, so it does not count. */
    fun issues(context: Context, s: AppSettings): Int = listOf(
        notifications(context),
        fullScreen(context),
        exact(),
        battery(context),
        overlay(context),
        s.vendorSettingsDone,
    ).count { !it }

    private fun pkg(context: Context) = "package:${context.packageName}".toUri()

    fun notificationSettings(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenSettings(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context))
        } else {
            appDetails(context)
        }

    fun exactSettings(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(context))
        } else {
            appDetails(context)
        }

    /**
     * The direct "allow this app?" dialog; the general list makes the user hunt for the app.
     * Play allows this for apps whose core function breaks under battery optimisation; an alarm is the textbook case.
     */
    @SuppressLint("BatteryLife")
    fun batterySettings(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(context))

    /** Without it Android shows a heads-up instead of the ringing screen while the phone is in use. */
    fun overlay(context: Context) = Settings.canDrawOverlays(context)

    fun overlaySettings(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg(context))

    /** Per-manufacturer instructions, maintained by the community at dontkillmyapp.com. */
    fun vendorGuide(): Intent {
        val maker = Build.MANUFACTURER.lowercase().replace(' ', '-')
        return Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/$maker".toUri())
    }

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(context))
}
