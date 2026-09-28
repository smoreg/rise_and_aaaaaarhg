package dev.smoreg.raa.ui.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.AppSettings
import dev.smoreg.raa.ui.Hint
import dev.smoreg.raa.ui.PrimaryButton
import dev.smoreg.raa.ui.card
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import kotlinx.coroutines.launch

private data class Statuses(
    val notifications: Boolean,
    val fullScreen: Boolean,
    val exact: Boolean,
    val battery: Boolean,
    val overlay: Boolean,
    val camera: Boolean,
)

private fun read(context: Context) = Statuses(
    Health.notifications(context),
    Health.fullScreen(context),
    Health.exact(),
    Health.battery(context),
    Health.overlay(context),
    Health.camera(context),
)

private fun saveSettings(f: (AppSettings) -> AppSettings) {
    val c = RaaApp.container
    c.scope.launch { c.settings.update(f) }
}

@Composable
fun HealthScreen(onboarding: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val settings by remember { RaaApp.container.settings.flow }.collectAsStateWithLifecycle(initialValue = null)
    // Re-read after every trip to system settings or a permission dialog.
    var st by remember { mutableStateOf(read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { st = read(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { st = read(context) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { st = read(context) }
    val s = settings ?: return

    fun open(i: Intent) = runCatching { context.startActivity(i) }

    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(stringResource(if (onboarding) R.string.onboarding_title else R.string.health_title), style = Type.headline, color = p.ink)
            Hint(stringResource(R.string.health_intro), Modifier.padding(top = 8.dp, bottom = 16.dp))

            Item(st.notifications, R.string.h_notifications, R.string.h_notifications_text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(Health.notificationSettings(context))
            }
            Item(st.fullScreen, R.string.h_fullscreen, R.string.h_fullscreen_text) { open(Health.fullScreenSettings(context)) }
            Item(st.exact, R.string.h_exact, R.string.h_exact_text) { open(Health.exactSettings(context)) }
            Item(st.battery, R.string.h_battery, R.string.h_battery_text) { open(Health.batterySettings(context)) }
            Item(st.overlay, R.string.h_overlay, R.string.h_overlay_text) { open(Health.overlaySettings(context)) }
            Item(
                s.vendorSettingsDone, R.string.h_vendor, R.string.h_vendor_text,
                extra = {
                    TextButton(onClick = { open(Health.vendorGuide()) }) { Text(stringResource(R.string.h_vendor_guide)) }
                    TextButton(onClick = { saveSettings { it.copy(vendorSettingsDone = !it.vendorSettingsDone) } }) {
                        Text(stringResource(if (s.vendorSettingsDone) R.string.h_vendor_undo else R.string.h_vendor_done))
                    }
                },
            ) { open(Health.appDetails(context)) }
            Item(st.camera, R.string.h_camera, R.string.h_camera_text) { askCamera.launch(Manifest.permission.CAMERA) }
            Hint(stringResource(R.string.h_watch_text), Modifier.padding(top = 16.dp))
        }
        PrimaryButton(
            stringResource(if (onboarding) R.string.onboarding_done else R.string.done),
            {
                if (onboarding) saveSettings { it.copy(onboardingDone = true) }
                onDone()
            },
            Modifier.navigationBarsPadding().padding(20.dp),
        )
    }
}

@Composable
private fun StatusTitle(ok: Boolean, title: Int, trailing: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) p.inkMuted else p.dawn))
        Text(stringResource(title), Modifier.padding(start = 10.dp).weight(1f), style = Type.title, color = p.ink)
        trailing()
    }
}

@Composable
private fun Item(ok: Boolean, title: Int, text: Int, extra: @Composable () -> Unit = {}, fix: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card()) {
        StatusTitle(ok, title) { if (ok) Text(stringResource(R.string.ok_mark), color = LocalPalette.current.inkMuted) }
        Hint(stringResource(text), Modifier.padding(top = 6.dp))
        Row {
            if (!ok) TextButton(onClick = fix) { Text(stringResource(R.string.fix)) }
            extra()
        }
    }
}
