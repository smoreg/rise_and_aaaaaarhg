package dev.smoreg.raa.ui.ringing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.alarm.Outcome
import dev.smoreg.raa.alarm.Phase
import dev.smoreg.raa.alarm.RingSession
import dev.smoreg.raa.alarm.Ringer
import dev.smoreg.raa.alarm.STROBE_HALF_PERIOD_MS
import dev.smoreg.raa.alarm.elapsed
import dev.smoreg.raa.data.DismissMode
import dev.smoreg.raa.data.LightMode
import dev.smoreg.raa.data.ShakeLevel
import dev.smoreg.raa.mission.QrScanner
import dev.smoreg.raa.mission.ShakeDetector
import dev.smoreg.raa.mission.ShakeListener
import dev.smoreg.raa.mission.TapDetector
import dev.smoreg.raa.mission.hasAccelerometer
import dev.smoreg.raa.ui.PrimaryButton
import dev.smoreg.raa.ui.Sky
import dev.smoreg.raa.ui.SlideToConfirm
import dev.smoreg.raa.ui.formatTime
import dev.smoreg.raa.ui.onSky
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Task { BUTTON, QR, SHAKE, TAP }

/** "Lost my code" is a last resort: offered from the third quiet walk on, and costs a proper shake. */
private const val LOST_CODE_AFTER_QUIET_WALKS = 3
private val LOST_CODE_SHAKE = ShakeLevel.AAAGH
private const val WRONG_CODE_SHOWN_MS = 1500L
/** No accelerometer readings for this long means the sensor is dead: fall back to tapping. */
private const val SENSOR_SILENCE_MS = 2000L
/** How long the alarm stays silent after the last shake or tap before it screams again. */
private const val SHAKE_GRACE_MS = 1000L
private const val TAP_GRACE_MS = 350L

@Composable
fun RingingScreen(s: RingSession) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var sensorDead by remember { mutableStateOf(false) }
    var awake by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }

    val canShake = !sensorDead && hasAccelerometer(context)
    val task = when {
        s.lostCode || s.alarm.dismissMode == DismissMode.SHAKE -> if (canShake) Task.SHAKE else Task.TAP
        s.alarm.dismissMode == DismissMode.QR -> Task.QR
        else -> Task.BUTTON
    }
    LaunchedEffect(task) { progress = 0f }

    val loud = s.phase == Phase.RING && !s.muted()
    val base = if (s.phase == Phase.SUNRISE) 0.5f * s.sunriseProgress(now) else 0.4f
    val sky = base + (1f - base) * progress
    val strobe = s.alarm.light == LightMode.STROBE && loud && (now / STROBE_HALF_PERIOD_MS) % 2 == 0L
    val text = if (strobe) Color.White else onSky(sky)

    ScreenBrightness(
        when {
            s.phase == Phase.SUNRISE -> 0.02f + 0.98f * s.sunriseProgress(now)
            s.alarm.light != LightMode.NONE && s.phase == Phase.RING -> 1f
            else -> -1f
        },
    )

    fun done() = Ringer.finish(Outcome.DISMISSED)

    Box(Modifier.fillMaxSize()) {
        Sky(sky, Modifier.fillMaxSize(), horizon = 0.62f)
        if (strobe) Box(Modifier.fillMaxSize().background(LocalPalette.current.scream))

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            val t = LocalTime.now()
            Text(formatTime(context, t.hour, t.minute), style = Type.clock, color = text)
            Text(
                s.alarm.label.ifBlank { stringResource(headline(s, task)) },
                style = Type.headline,
                color = text,
            )
            if (s.phase == Phase.SUNRISE) {
                Text(
                    stringResource(R.string.sunrise_until, formatTime(context, s.alarm.hour, s.alarm.minute)),
                    Modifier.padding(top = 6.dp),
                    color = text.copy(alpha = 0.8f),
                )
            }
            if (s.test) {
                Text(stringResource(R.string.test_ring), Modifier.padding(top = 6.dp), color = text.copy(alpha = 0.8f))
            }
            Spacer(Modifier.weight(1f))

            if (s.phase == Phase.SUNRISE && !awake) {
                TextButton(onClick = { awake = true }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.already_awake), color = text, style = Type.title)
                }
            } else {
                when (task) {
                    Task.BUTTON -> SlideToConfirm(stringResource(R.string.slide_to_wake), { progress = it }, ::done)
                    Task.QR -> QrTask(s, now, text, onDone = ::done)
                    Task.SHAKE -> ShakeTask(
                        level = if (s.lostCode) LOST_CODE_SHAKE else s.alarm.shakeLevel,
                        text = text,
                        onProgress = { progress = it },
                        onDead = { sensorDead = true },
                        onDone = ::done,
                    )
                    Task.TAP -> TapTask(text, onProgress = { progress = it }, onDone = ::done)
                }
            }

            if (s.canSnooze) {
                TextButton(onClick = { Ringer.finish(Outcome.SNOOZED) }, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                    val left = s.alarm.snoozeMax - s.alarm.snoozeCount
                    Text(
                        stringResource(
                            R.string.snooze_button,
                            pluralStringResource(R.plurals.minutes, s.alarm.snoozeMinutes, s.alarm.snoozeMinutes),
                            pluralStringResource(R.plurals.snooze_left, left, left),
                        ),
                        color = text.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

private fun headline(s: RingSession, task: Task) = when {
    s.phase == Phase.EARLY -> R.string.early_title
    s.phase == Phase.SUNRISE -> R.string.sunrise_title
    task == Task.QR -> R.string.go_scan
    task == Task.SHAKE -> R.string.shake_it
    task == Task.TAP -> R.string.tap_it
    else -> R.string.wake_up
}

@Composable
private fun ScreenBrightness(level: Float) {
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(level) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }
}

@Composable
private fun QrTask(s: RingSession, now: Long, text: Color, onDone: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val c = RaaApp.container
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val scope = rememberCoroutineScope()
    var torch by remember { mutableStateOf(false) }
    var wrongAt by remember { mutableLongStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    val wrong = now - wrongAt < WRONG_CODE_SHOWN_MS

    fun check(value: String) {
        if (checking) return
        checking = true
        scope.launch {
            if (c.db.qrCodes().find(value) != null) onDone() else wrongAt = System.currentTimeMillis()
            delay(800)
            checking = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (granted) {
            DisposableEffect(Unit) {
                Ringer.setCamera(true)
                onDispose { Ringer.setCamera(false) }
            }
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(3.dp, if (wrong) p.scream else p.dawn, RoundedCornerShape(28.dp)),
            ) {
                QrScanner(
                    onScan = ::check,
                    torch = torch,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                stringResource(if (wrong) R.string.wrong_code else R.string.point_at_code),
                Modifier.padding(top = 10.dp),
                color = text,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { torch = !torch }) {
                Text(stringResource(if (torch) R.string.torch_off else R.string.torch_on), color = text)
            }
        } else {
            Text(stringResource(R.string.camera_needed), color = text, textAlign = TextAlign.Center)
            PrimaryButton(stringResource(R.string.allow_camera), { ask.launch(Manifest.permission.CAMERA) }, Modifier.padding(top = 12.dp))
        }

        if (s.phase == Phase.RING) {
            if (s.muted()) {
                val left = s.mutedLeftMs() / 1000
                Text(
                    stringResource(R.string.quiet_left, "%d:%02d".format(left / 60, left % 60)),
                    Modifier.padding(top = 4.dp),
                    style = Type.title,
                    color = text,
                )
            } else {
                PrimaryButton(
                    stringResource(R.string.going_to_code, s.nextQuietMinutes),
                    { Ringer.goQuiet() },
                    Modifier.padding(top = 4.dp),
                )
            }
        }

        if (s.quietCount >= LOST_CODE_AFTER_QUIET_WALKS) {
            TextButton(onClick = { Ringer.loseCode() }, Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.lost_code), color = text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ShakeTask(level: ShakeLevel, text: Color, onProgress: (Float) -> Unit, onDead: () -> Unit, onDone: () -> Unit) {
    val detector = remember(level) { ShakeDetector(ShakeDetector.target(level)) }
    var shown by remember { mutableFloatStateOf(0f) }
    val samples = remember { longArrayOf(0) }
    val lastShake = remember { longArrayOf(0) }
    SilentWhileActive(SHAKE_GRACE_MS) { lastShake[0] }
    ShakeListener { accel, dt ->
        samples[0]++
        if (detector.counts(accel)) lastShake[0] = elapsed()
        detector.feed(accel, dt)
        shown = detector.progress
        onProgress(detector.progress)
        if (detector.done) onDone()
    }
    LaunchedEffect(Unit) {
        delay(SENSOR_SILENCE_MS)
        if (samples[0] == 0L) onDead()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(shown, text) { Text("${(shown * 100).toInt()}%", style = Type.scream, color = text) }
        Text(stringResource(R.string.shake_hint), Modifier.padding(top = 16.dp), color = text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TapTask(text: Color, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    val detector = remember { TapDetector() }
    var shown by remember { mutableFloatStateOf(0f) }
    val lastTap = remember { longArrayOf(0) }
    SilentWhileActive(TAP_GRACE_MS) { lastTap[0] }
    LaunchedEffect(Unit) {
        while (!detector.done) {
            delay(50)
            detector.tick(50)
            shown = detector.progress
            onProgress(shown)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(shown, text) {
            Box(
                Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(p.dawn)
                    // No ripple: at eight taps a second it would only blur the button.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        lastTap[0] = elapsed()
                        detector.tap()
                        shown = detector.progress
                        onProgress(shown)
                        if (detector.done) onDone()
                    },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.tap_button), style = Type.scream, color = p.onDawn) }
        }
        Text(stringResource(R.string.tap_hint), Modifier.padding(top = 16.dp), color = text, textAlign = TextAlign.Center)
    }
}

/** Silent while the last shake or tap is fresher than [graceMs], screaming again once it is not. */
@Composable
private fun SilentWhileActive(graceMs: Long, lastActive: () -> Long) {
    LaunchedEffect(Unit) {
        while (true) {
            Ringer.setEngaged(elapsed() - lastActive() < graceMs)
            delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { Ringer.setEngaged(false) } }
}

@Composable
private fun ProgressRing(progress: Float, color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(w))
            drawArc(color, -90f, 360f * progress, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

