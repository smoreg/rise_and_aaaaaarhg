package dev.smoreg.raa.ui.settings

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smoreg.raa.BuildConfig
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.AppSettings
import dev.smoreg.raa.data.ThemeMode
import dev.smoreg.raa.ui.Choice
import dev.smoreg.raa.ui.Hint
import dev.smoreg.raa.ui.LabeledSwitch
import dev.smoreg.raa.ui.SectionTitle
import dev.smoreg.raa.ui.TopBar
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onHealth: () -> Unit) {
    val c = RaaApp.container
    val context = LocalContext.current
    val p = LocalPalette.current
    val settings by remember { c.settings.flow }.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    fun set(f: (AppSettings) -> AppSettings) = c.scope.launch {
        c.settings.update(f)
        // The upcoming-alarm notice is scheduled per alarm, so toggling it has to reschedule.
        c.scheduler.rescheduleAll()
    }

    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onHealth).padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.health_title), Modifier.weight(1f), style = Type.title, color = p.ink)
                Text("→", color = p.dawn, style = Type.title)
            }

            SectionTitle(stringResource(R.string.auto_stop))
            val stops = listOf(0 to stringResource(R.string.never)) +
                listOf(15, 30, 60).map { it to stringResource(R.string.minutes_n, it) }
            Choice(stops, s.autoStopMinutes, { v -> set { it.copy(autoStopMinutes = v) } })
            Hint(stringResource(R.string.auto_stop_hint), Modifier.padding(top = 8.dp))

            LabeledSwitch(stringResource(R.string.upcoming_notice), s.upcomingNotice) { v -> set { it.copy(upcomingNotice = v) } }
            Hint(stringResource(R.string.upcoming_notice_hint))

            SectionTitle(stringResource(R.string.theme))
            val themes = listOf(
                ThemeMode.DARK to stringResource(R.string.theme_dark),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
            )
            Choice(themes, s.theme, { v -> set { it.copy(theme = v) } })

            SectionTitle(stringResource(R.string.language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
            val languages = listOf(
                "" to stringResource(R.string.language_system),
                "en" to "English",
                "ru" to "Русский",
                "es" to "Español",
                "zh" to "中文",
            )
            Choice(languages, current, { tag ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (tag == "zh") "zh-CN" else tag))
            })

            SectionTitle(stringResource(R.string.about))
            Hint(stringResource(R.string.about_text, BuildConfig.VERSION_NAME))
            val source = stringResource(R.string.source_url)
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, source.toUri())) } }, Modifier.padding(bottom = 32.dp)) {
                Text(stringResource(R.string.source_code))
            }
        }
    }
}
