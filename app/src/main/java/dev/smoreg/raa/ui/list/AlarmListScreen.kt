package dev.smoreg.raa.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.alarm.NextTrigger
import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.LightMode
import dev.smoreg.raa.ui.PrimaryButton
import dev.smoreg.raa.ui.Sky
import dev.smoreg.raa.ui.amPm
import dev.smoreg.raa.ui.describeDays
import dev.smoreg.raa.ui.describeIn
import dev.smoreg.raa.ui.formatClock
import dev.smoreg.raa.ui.formatTime
import dev.smoreg.raa.ui.health.Health
import dev.smoreg.raa.ui.modeName
import dev.smoreg.raa.ui.onSky
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlarmListScreen(onEdit: (Long) -> Unit, onQr: () -> Unit, onSettings: () -> Unit, onHealth: () -> Unit) {
    val c = RaaApp.container
    val context = LocalContext.current
    val p = LocalPalette.current
    val alarms by remember { c.db.alarms().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by remember { c.settings.flow }.collectAsStateWithLifecycle(initialValue = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var issues by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = System.currentTimeMillis()
        settings?.let { issues = Health.issues(context, it) }
    }
    LaunchedEffect(settings) { settings?.let { issues = Health.issues(context, it) } }

    val zNow = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val next = alarms.mapNotNull { a -> NextTrigger.ring(a, zNow)?.let { a to it.toInstant().toEpochMilli() } }
        .minByOrNull { it.second }
    // The sky over the header brightens as the next alarm gets closer: 12 h away is night.
    val closeness = next?.let { 1f - ((it.second - now) / (12 * 3_600_000f)).coerceIn(0f, 1f) } ?: 0f
    val sky = 0.08f + 0.5f * closeness
    val text = onSky(sky)

    Column(Modifier.fillMaxSize().background(p.paper)) {
        // In landscape a fixed 300 dp header would leave no room for the list.
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val header = (windowHeight * 0.45f).coerceAtMost(380.dp)
        // Landscape leaves the header about half its portrait height: smaller clock, countdown
        // beside it instead of below, horizon lower so nothing sits in the water.
        val compact = header < 240.dp
        val clockSize = if (compact) 44.sp else Type.clock.fontSize
        Box(Modifier.fillMaxWidth().height(header)) {
            Sky(sky, Modifier.fillMaxSize(), horizon = if (compact) 0.9f else 0.76f)
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), Modifier.weight(1f), style = Type.title, color = text)
                    TextButton(onClick = onQr) { Text(stringResource(R.string.qr_codes), color = text) }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings), tint = text)
                    }
                }
                Spacer(Modifier.height(if (compact) 4.dp else 24.dp))
                if (next == null) {
                    Text(stringResource(R.string.no_alarms_title), style = Type.headline, color = text)
                    if (!compact) Text(stringResource(R.string.no_alarms_text), Modifier.padding(top = 8.dp), color = text.copy(alpha = 0.8f))
                } else {
                    val (a, at) = next
                    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                    val details = listOfNotNull(describeIn(context, at - now), a.label.ifBlank { null }).joinToString(" · ")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatTime(context, t.hour, t.minute), style = Type.clock.copy(fontSize = clockSize, lineHeight = clockSize), color = text)
                        amPm(context, t.hour)?.let { Text(it, Modifier.padding(start = 6.dp, bottom = 12.dp), style = Type.title, color = text) }
                        if (compact) {
                            Text(details, Modifier.padding(start = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = text.copy(alpha = 0.85f))
                        }
                    }
                    if (!compact) {
                        Text(details, style = MaterialTheme.typography.titleMedium, color = text.copy(alpha = 0.85f))
                    }
                }
            }
        }

        if (issues > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHealth)
                    .background(p.paper2)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(p.dawn))
                Text(
                    pluralStringResource(R.plurals.health_issues, issues, issues),
                    Modifier.padding(start = 12.dp).weight(1f),
                    color = p.ink,
                )
                Text(stringResource(R.string.fix), color = p.dawn, style = MaterialTheme.typography.labelLarge)
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(alarms, key = { it.id }) { a ->
                AlarmRow(a, now, onClick = { onEdit(a.id) }, onToggle = { on ->
                    c.scope.launch {
                        val updated = a.copy(enabled = on, snoozeUntil = 0, snoozeCount = 0, handledUntil = 0)
                        c.db.alarms().upsert(updated)
                        c.scheduler.schedule(updated)
                    }
                })
            }
        }

        PrimaryButton(stringResource(R.string.new_alarm), { onEdit(0) }, Modifier.navigationBarsPadding().padding(20.dp))
    }
}

@Composable
private fun AlarmRow(a: Alarm, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val ink = if (a.enabled) p.ink else p.inkMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatTime(context, a.hour, a.minute), style = Type.time, color = ink)
                amPm(context, a.hour)?.let { Text(it, Modifier.padding(start = 4.dp, bottom = 6.dp), color = ink) }
            }
            val details = buildList {
                add(describeDays(context, a.days))
                add(context.getString(modeName(a.dismissMode)))
                if (a.light == LightMode.SUNRISE) add(context.getString(R.string.light_sunrise))
                if (a.light == LightMode.STROBE) add(context.getString(R.string.light_strobe))
                if (a.label.isNotBlank()) add(a.label)
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = p.inkMuted)
            if (a.enabled && a.handledUntil > now && a.snoozeUntil <= now) {
                Text(stringResource(R.string.next_skipped), style = MaterialTheme.typography.bodySmall, color = p.dawn)
            }
            if (a.enabled && a.snoozeUntil > now) {
                val t = Instant.ofEpochMilli(a.snoozeUntil).atZone(ZoneId.systemDefault())
                Text(
                    stringResource(R.string.snoozed_until, formatClock(context, t.hour, t.minute)),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.dawn,
                )
            }
        }
        Switch(checked = a.enabled, onCheckedChange = onToggle)
    }
}
