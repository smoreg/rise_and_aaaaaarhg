package dev.smoreg.raa

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.smoreg.raa.alarm.Phase
import dev.smoreg.raa.alarm.Ringer
import dev.smoreg.raa.data.AppSettings
import dev.smoreg.raa.ui.editor.EditorScreen
import dev.smoreg.raa.ui.health.HealthScreen
import dev.smoreg.raa.ui.list.AlarmListScreen
import dev.smoreg.raa.ui.qr.QrScreen
import dev.smoreg.raa.ui.ringing.RingingActivity
import dev.smoreg.raa.ui.settings.SettingsScreen
import dev.smoreg.raa.ui.theme.RaaTheme

class MainActivity : AppCompatActivity() {
    /** While an alarm rings the app itself is off limits: no editing, no new codes, no gyroscope "fix". */
    override fun onResume() {
        super.onResume()
        val phase = Ringer.session.value?.phase
        if (phase == Phase.RING || phase == Phase.SUNRISE) {
            startActivity(Intent(this, RingingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = RaaApp.container.settings.flow
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
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
