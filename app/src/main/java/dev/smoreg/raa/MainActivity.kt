package dev.smoreg.raa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.smoreg.raa.alarm.Phase
import dev.smoreg.raa.alarm.Ringer
import dev.smoreg.raa.data.AppSettings
import dev.smoreg.raa.data.ThemeMode
import dev.smoreg.raa.ui.editor.EditorScreen
import dev.smoreg.raa.ui.health.HealthScreen
import dev.smoreg.raa.ui.list.AlarmListScreen
import dev.smoreg.raa.ui.qr.QrScreen
import dev.smoreg.raa.ui.ringing.RingingActivity
import dev.smoreg.raa.ui.settings.SettingsScreen
import dev.smoreg.raa.ui.theme.RaaTheme

class MainActivity : AppCompatActivity() {
    /** While an alarm rings the app itself is off limits: no editing, no test rings, no new codes. */
    override fun onResume() {
        super.onResume()
        val phase = Ringer.session.value?.phase
        if (phase == Phase.RING || phase == Phase.SUNRISE) {
            startActivity(Intent(this, RingingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsFlow = RaaApp.container.settings.flow
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            val dark = when (s.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // An alarm that starts while the app itself is open: the service cannot bring the ringing
            // screen forward without the overlay permission, but a foreground activity can.
            LaunchedEffect(Unit) {
                // Only while this activity is in front: from the background the start would just be refused.
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    Ringer.session.collect { session ->
                        if (session?.phase == Phase.RING || session?.phase == Phase.SUNRISE) {
                            startActivity(Intent(this@MainActivity, RingingActivity::class.java))
                        }
                    }
                }
            }
            // Status-bar icons follow the app theme, not the phone's: the app is dark by default.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            RaaTheme(s.theme) { App(s) }
        }
    }
}

@Composable
private fun App(settings: AppSettings) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (settings.onboardingDone) "list" else "onboarding") {
        composable("onboarding") {
            HealthScreen(onboarding = true, onDone = {
                nav.navigate("list") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable("list") {
            AlarmListScreen(
                onEdit = { nav.navigate("edit/$it") },
                onQr = { nav.navigate("qr") },
                onSettings = { nav.navigate("settings") },
                onHealth = { nav.navigate("health") },
            )
        }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            EditorScreen(
                id = it.arguments?.getLong("id") ?: 0,
                onDone = { nav.popBackStack() },
                onQr = { nav.navigate("qr") },
            )
        }
        composable("qr") { QrScreen(onBack = { nav.popBackStack() }) }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() }, onHealth = { nav.navigate("health") })
        }
        composable("health") { HealthScreen(onboarding = false, onDone = { nav.popBackStack() }) }
    }
}
