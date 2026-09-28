package dev.smoreg.raa.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.alarm.NextTrigger
import dev.smoreg.raa.alarm.Phase
import dev.smoreg.raa.alarm.RingSession
import dev.smoreg.raa.alarm.Ringer
import dev.smoreg.raa.alarm.RingingService
import dev.smoreg.raa.data.Alarm
import dev.smoreg.raa.data.DismissMode
import dev.smoreg.raa.data.LightMode
import dev.smoreg.raa.data.ShakeLevel
import dev.smoreg.raa.data.Sound
import dev.smoreg.raa.data.dayBit
import dev.smoreg.raa.ui.Choice
import dev.smoreg.raa.ui.Hint
import dev.smoreg.raa.ui.LabeledSwitch
import dev.smoreg.raa.ui.PrimaryButton
import dev.smoreg.raa.ui.SectionTitle
import dev.smoreg.raa.ui.Selectable
import dev.smoreg.raa.ui.Stepper
import dev.smoreg.raa.ui.TopBar
import dev.smoreg.raa.ui.card
import dev.smoreg.raa.ui.dayShort
import dev.smoreg.raa.ui.qr.CreateCodeFlow
import dev.smoreg.raa.ui.shakeLevelName
import dev.smoreg.raa.ui.ringing.RingingActivity
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import dev.smoreg.raa.ui.weekDays
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LABEL_MAX = 60
/** A test compresses the sunrise so the whole thing fits in a few seconds. */
private const val TEST_SUNRISE_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(id: Long, onDone: () -> Unit) {
    val c = RaaApp.container
    val context = LocalContext.current
    var draft by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(id) { draft = if (id == 0L) Alarm() else c.db.alarms().get(id) ?: Alarm() }
    val loaded = draft ?: return
    val time = rememberTimePickerState(loaded.hour, loaded.minute, DateFormat.is24HourFormat(context))
    // The picker owns hour and minute; everything else lives in the draft.
    val a = loaded.copy(hour = time.hour, minute = time.minute)
    val edit: ((Alarm) -> Alarm) -> Unit = { f -> draft = f(a) }

    fun save() = c.scope.launch {
        // Editing clears a pending snooze. A skip stays, but moves to the next ring of the edited time.
        val skipped = a.handledUntil > System.currentTimeMillis()
        val base = a.copy(snoozeUntil = 0, snoozeCount = 0, handledUntil = 0)
        val clean = if (skipped) base.copy(handledUntil = nextRingMs(base)) else base
        val rowId = c.db.alarms().upsert(clean)
        c.scheduler.schedule(if (clean.id == 0L) clean.copy(id = rowId) else clean)
    }

    Column(Modifier.fillMaxSize().background(LocalPalette.current.paper).statusBarsPadding().imePadding()) {
        TopBar(stringResource(if (id == 0L) R.string.new_alarm else R.string.edit_alarm), onDone) {
            if (id != 0L) {
                IconButton(onClick = {
                    c.scope.launch {
                        c.scheduler.cancel(a.id)
                        c.db.alarms().delete(a)
                    }
                    onDone()
                }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = LocalPalette.current.inkMuted) }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            TimeSection(time)
            DaysSection(a, edit)
            OutlinedTextField(
                a.label,
                { v -> edit { it.copy(label = v.take(LABEL_MAX)) } },
                Modifier.fillMaxWidth().padding(top = 20.dp),
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
            )
            DismissSection(a, edit)
            SoundSection(a, edit)
            LightSection(a, edit)
            SnoozeSection(a, edit)
            if (id != 0L && a.enabled) SkipSwitch(a, edit)
            Spacer(Modifier.height(24.dp))
        }

        Row(Modifier.navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { test(context, a) }, Modifier.weight(1f).height(56.dp), shape = CircleShape) {
                Text(stringResource(R.string.test), color = LocalPalette.current.ink)
            }
            PrimaryButton(stringResource(R.string.save), { save(); onDone() }, Modifier.weight(1.4f))
        }
    }
}

private fun nextRingMs(a: Alarm) = NextTrigger.ring(a, ZonedDateTime.now())?.toInstant()?.toEpochMilli() ?: 0

private fun test(context: Context, a: Alarm) {
    val now = System.currentTimeMillis()
    val sunrise = a.light == LightMode.SUNRISE
    val started = Ringer.begin(
        RingSession(
            alarm = a,
            phase = if (sunrise) Phase.SUNRISE else Phase.RING,
            ringAt = if (sunrise) now + TEST_SUNRISE_MS else now,
            sunriseStart = now,
            test = true,
        ),
    )
    if (!started) return
    RingingService.show(context)
    context.startActivity(Intent(context, RingingActivity::class.java))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSection(time: TimePickerState) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(
            time,
            colors = TimePickerDefaults.colors(
                clockDialColor = p.paper2,
                timeSelectorUnselectedContainerColor = p.paper2,
                timeSelectorUnselectedContentColor = p.ink,
            ),
        )
    }
}

@Composable
private fun DaysSection(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    SectionTitle(stringResource(R.string.repeat))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        weekDays(context).forEach { d ->
            val on = a.days and dayBit(d) != 0
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (on) p.dawn else p.paper2)
                    .clickable(role = Role.Checkbox) { edit { it.copy(days = it.days xor dayBit(d)) } },
                contentAlignment = Alignment.Center,
            ) { Text(dayShort(context, d).take(2), color = if (on) p.onDawn else p.ink) }
        }
    }
    Hint(stringResource(if (a.days == 0) R.string.repeat_hint_once else R.string.repeat_hint_days), Modifier.padding(top = 8.dp))
}

@Composable
private fun DismissSection(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    val p = LocalPalette.current
    val codes by remember { RaaApp.container.db.qrCodes().observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    var creating by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.how_to_stop))
    val modes = listOf(
        DismissMode.BUTTON to (R.string.mode_button to stringResource(R.string.mode_button_desc)),
        DismissMode.QR to (R.string.mode_qr to stringResource(R.string.mode_qr_desc, a.quietMinutes)),
        DismissMode.SHAKE to (R.string.mode_shake to stringResource(R.string.mode_shake_desc)),
    )
    modes.forEach { (mode, texts) ->
        Selectable(stringResource(texts.first), texts.second, a.dismissMode == mode) { edit { it.copy(dismissMode = mode) } }
    }
    when (a.dismissMode) {
        DismissMode.QR -> Stepper(stringResource(R.string.quiet_minutes, a.quietMinutes), a.quietMinutes, 1..5) { v ->
            edit { it.copy(quietMinutes = v) }
        }
        DismissMode.SHAKE -> {
            Hint(stringResource(R.string.shake_level), Modifier.padding(top = 16.dp, bottom = 8.dp))
            Choice(ShakeLevel.entries.map { it to stringResource(shakeLevelName(it)) }, a.shakeLevel, { v -> edit { it.copy(shakeLevel = v) } })
        }
        DismissMode.BUTTON -> Unit
    }
    if (a.dismissMode == DismissMode.QR && codes?.isEmpty() == true) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).border(2.dp, p.dawn, RoundedCornerShape(16.dp)).card()) {
            Text(stringResource(R.string.no_codes_warning), color = p.ink)
            TextButton(onClick = { creating = true }, Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.go_make_code)) }
        }
    }
    if (creating) CreateCodeFlow(onDismiss = { creating = false })
}

@Composable
private fun SoundSection(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var dialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { importSound(context, uri)?.let { s -> edit { it.copy(sound = s) } } }
    }

    SectionTitle(stringResource(R.string.sound))
    Row(Modifier.fillMaxWidth().clickable { dialog = true }.card(), verticalAlignment = Alignment.CenterVertically) {
        Text(soundName(a.sound), Modifier.weight(1f), style = Type.title, color = p.ink)
        Text(stringResource(R.string.change), color = p.dawn)
    }
    if (a.sound != Sound.SILENT) {
        Stepper(stringResource(R.string.volume, a.volume), a.volume, 10..100, step = 10) { v -> edit { it.copy(volume = v) } }
        Hint(stringResource(R.string.ramp), Modifier.padding(top = 16.dp, bottom = 8.dp))
        val ramps = listOf(
            0 to stringResource(R.string.ramp_off),
            30 to stringResource(R.string.ramp_30s),
            60 to stringResource(R.string.ramp_1m),
            120 to stringResource(R.string.ramp_2m),
            300 to stringResource(R.string.ramp_5m),
        )
        Choice(ramps, a.rampSeconds, { v -> edit { it.copy(rampSeconds = v) } })
    }
    LabeledSwitch(stringResource(R.string.vibrate), a.vibrate) { v -> edit { it.copy(vibrate = v) } }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.sound)) },
            text = {
                Column {
                    (Sound.BUILTIN.map(Sound::builtin) + Sound.SYSTEM + Sound.SILENT).forEach { s ->
                        DialogOption(soundName(s), selected = s == a.sound) {
                            edit { it.copy(sound = s) }
                            dialog = false
                        }
                    }
                    DialogOption(stringResource(R.string.sound_pick_file), selected = false) {
                        dialog = false
                        pickFile.launch(arrayOf("audio/*"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Copies a picked file into the app: a cloud document can be slow or gone at 7 am, and the copy
 * sits in device-protected storage, so it plays even before the first unlock after a reboot.
 */
private suspend fun importSound(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.createDeviceProtectedStorageContext().filesDir, "sounds").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.audio")
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(file).toString()
    }.getOrNull()
}

@Composable
private fun DialogOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        color = if (selected) p.dawn else p.ink,
        style = Type.title,
    )
}

@Composable
private fun soundName(sound: String): String = stringResource(
    when (sound) {
        Sound.SILENT -> R.string.sound_silent
        Sound.SYSTEM -> R.string.sound_system
        Sound.builtin("rise_and_shine") -> R.string.sound_rise_and_shine
        Sound.builtin("klaxon") -> R.string.sound_klaxon
        Sound.builtin("beeps") -> R.string.sound_beeps
        Sound.builtin("dawn") -> R.string.sound_dawn
        else -> R.string.sound_file
    },
)

@Composable
private fun LightSection(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    val p = LocalPalette.current
    var strobeWarning by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.light))
    val lights = listOf(
        LightMode.NONE to stringResource(R.string.light_none),
        LightMode.SUNRISE to stringResource(R.string.light_sunrise),
        LightMode.STROBE to stringResource(R.string.light_strobe),
    )
    Choice(lights, a.light, { v ->
        if (v == LightMode.STROBE && a.light != LightMode.STROBE) strobeWarning = true else edit { it.copy(light = v) }
    })
    when (a.light) {
        LightMode.SUNRISE -> {
            Stepper(stringResource(R.string.sunrise_minutes, a.sunriseMinutes), a.sunriseMinutes, 1..30) { v ->
                edit { it.copy(sunriseMinutes = v) }
            }
            Hint(stringResource(R.string.sunrise_hint))
        }
        LightMode.STROBE -> Hint(stringResource(R.string.strobe_hint), Modifier.padding(top = 8.dp))
        LightMode.NONE -> Unit
    }
    if (a.sound == Sound.SILENT && a.light == LightMode.NONE && !a.vibrate) {
        Hint(stringResource(R.string.nothing_wakes_you), Modifier.padding(top = 12.dp), color = p.scream)
    }

    if (strobeWarning) {
        AlertDialog(
            onDismissRequest = { strobeWarning = false },
            title = { Text(stringResource(R.string.strobe_warning_title)) },
            text = { Text(stringResource(R.string.strobe_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    strobeWarning = false
                    edit { it.copy(light = LightMode.STROBE) }
                }) { Text(stringResource(R.string.strobe_enable)) }
            },
            dismissButton = { TextButton(onClick = { strobeWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SnoozeSection(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    SectionTitle(stringResource(R.string.snooze))
    val options = listOf(
        0 to stringResource(R.string.snooze_off),
        5 to stringResource(R.string.minutes_n, 5),
        10 to stringResource(R.string.minutes_n, 10),
        15 to stringResource(R.string.minutes_n, 15),
    )
    Choice(options, a.snoozeMinutes, { v -> edit { it.copy(snoozeMinutes = v) } })
    if (a.snoozeMinutes > 0) {
        Stepper(pluralStringResource(R.plurals.snooze_max, a.snoozeMax, a.snoozeMax), a.snoozeMax, 1..5) { v ->
            edit { it.copy(snoozeMax = v) }
        }
    }
}

@Composable
private fun SkipSwitch(a: Alarm, edit: ((Alarm) -> Alarm) -> Unit) {
    LabeledSwitch(stringResource(R.string.skip_next), a.handledUntil > System.currentTimeMillis()) { on ->
        edit {
            it.copy(handledUntil = if (on) nextRingMs(it.copy(snoozeUntil = 0, handledUntil = 0)) else 0)
        }
    }
}
