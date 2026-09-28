package dev.smoreg.raa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.smoreg.raa.R
import dev.smoreg.raa.data.DismissMode
import dev.smoreg.raa.data.ShakeLevel
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.GroundMorning
import dev.smoreg.raa.ui.theme.GroundNight
import dev.smoreg.raa.ui.theme.SkyStops
import dev.smoreg.raa.ui.theme.SunHigh
import dev.smoreg.raa.ui.theme.SunLow
import dev.smoreg.raa.ui.theme.Type
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

fun skyColor(p: Float): Color {
    val x = p.coerceIn(0f, 1f) * (SkyStops.size - 1)
    val i = x.toInt().coerceAtMost(SkyStops.size - 2)
    return lerp(SkyStops[i], SkyStops[i + 1], x - i)
}

/** Text colour that stays readable on the sky at [p]: past this point the sky is light enough for dark text. */
private const val SKY_LIGHT_FROM = 0.72f

fun onSky(p: Float): Color = if (p > SKY_LIGHT_FROM) SkyStops.first() else SkyStops.last()

/**
 * Horizon over water with a sun behind it: 0 is night with the sun below the line, 1 is morning with
 * it high up. Below the line the sun is reflected as slowly rippling stripes.
 */
@Composable
fun Sky(progress: Float, modifier: Modifier = Modifier, horizon: Float = 0.78f) {
    val ripple by rememberInfiniteTransition(label = "ripple").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(RIPPLE_PERIOD_MS, easing = LinearEasing)),
        label = "phase",
    )
    // The glow is wider than the sky; without clipping it leaks onto whatever sits below.
    Canvas(modifier.clipToBounds()) {
        val p = progress.coerceIn(0f, 1f)
        val horizonY = size.height * horizon
        val r = size.minDimension * 0.16f
        val eased = 1f - (1f - p) * (1f - p)
        val center = Offset(size.width * 0.5f, lerp(horizonY + r * 1.1f, size.height * 0.2f, eased))
        val sun = lerp(SunLow, SunHigh, p)

        // Sky and sun end at the horizon; below it only the water and the reflection are drawn.
        clipRect(bottom = horizonY) {
            drawRect(Brush.verticalGradient(listOf(skyColor(p * 0.7f), skyColor((p + 0.18f).coerceAtMost(1f))), endY = horizonY))
            drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.5f), Color.Transparent), center, r * 3.2f), r * 3.2f, center)
            drawCircle(sun, r, center)
        }

        val water = size.height - horizonY
        drawRect(
            // Fades out at the bottom so the water melts into whatever background is below.
            Brush.verticalGradient(listOf(lerp(GroundNight, GroundMorning, p), Color.Transparent), startY = horizonY, endY = size.height),
            Offset(0f, horizonY),
        )

        // Reflection: the sun mirrored in the horizon, sliced into stripes that thin out and
        // spread apart with depth, each drifting sideways on its own phase.
        val mirroredY = 2 * horizonY - center.y
        val stripe = 2.dp.toPx()
        var y = horizonY + stripe * 2
        var i = 0
        while (y < size.height) {
            val depth = (y - horizonY) / water
            val dy = y - mirroredY
            if (abs(dy) < r) {
                val half = sqrt(r * r - dy * dy) * (1f - 0.35f * depth)
                val drift = sin(ripple + i * 0.9f) * r * 0.12f
                val shimmer = 0.75f + 0.25f * sin(ripple * 2 + i * 1.7f)
                val alpha = 0.9f * (1f - depth) * (0.6f + 0.4f * p) * shimmer
                drawRect(sun.copy(alpha = alpha), Offset(center.x - half + drift, y), Size(half * 2, stripe))
            }
            y += stripe * (2f + depth * 3f)
            i++
        }
        drawRect(sun.copy(alpha = 0.35f + 0.4f * p), Offset(0f, horizonY), Size(size.width, 1.5.dp.toPx()))
    }
}

private const val RIPPLE_PERIOD_MS = 4000

/** Slide the thumb to the end to confirm; a tap does nothing, so half-asleep fingers cannot fire it. */
@Composable
fun SlideToConfirm(label: String, onProgress: (Float) -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(p.paper.copy(alpha = 0.72f))
            .border(2.dp, p.dawn, CircleShape),
    ) {
        val thumb = 64.dp
        val max = with(LocalDensity.current) { (maxWidth - thumb - 8.dp).toPx() }
        Text(
            label,
            Modifier.align(Alignment.Center).padding(start = thumb),
            style = Type.title,
            color = p.ink,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumb)
                .clip(CircleShape)
                .background(p.dawn)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        scope.launch {
                            offset.snapTo((offset.value + d).coerceIn(0f, max))
                            onProgress(offset.value / max)
                        }
                    },
                    onDragStopped = {
                        if (offset.value >= max * 0.92f) {
                            onConfirm()
                        } else {
                            offset.animateTo(0f)
                            onProgress(0f)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("→", style = Type.headline, color = p.onDawn)
        }
    }
}

/** A row of mutually exclusive options. Used instead of dropdowns: every choice stays visible. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, text) ->
            val on = value == selected
            Text(
                text,
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) p.dawn else p.paper2)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (on) p.onDawn else p.ink,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 28.dp, bottom = 10.dp), style = Type.title, color = LocalPalette.current.ink)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.inkMuted) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
fun Selectable(title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) p.paper3 else p.paper2)
            .border(2.dp, if (selected) p.dawn else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, style = Type.title, color = p.ink)
        Hint(text, Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.fillMaxWidth().height(56.dp), shape = CircleShape) { Text(text, style = Type.title) }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = p.ink) }
        Text(title, Modifier.weight(1f), style = Type.title, color = p.ink)
        actions()
    }
}

/** The one surface style for grouped content. */
@Composable
fun Modifier.card(): Modifier = clip(RoundedCornerShape(16.dp)).background(LocalPalette.current.paper2).padding(16.dp)

@Composable
fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp)) {
        Hint(label)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).roundToInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            colors = SliderDefaults.colors(inactiveTrackColor = p.paper3, activeTickColor = p.onDawn, inactiveTickColor = p.line),
        )
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = LocalPalette.current.ink)
        Switch(checked, onChange)
    }
}

fun modeName(m: DismissMode) = when (m) {
    DismissMode.BUTTON -> R.string.mode_button
    DismissMode.QR -> R.string.mode_qr
    DismissMode.SHAKE -> R.string.mode_shake
}

fun shakeLevelName(l: ShakeLevel) = when (l) {
    ShakeLevel.LIGHT -> R.string.shake_light
    ShakeLevel.AAAGH -> R.string.shake_aaagh
    ShakeLevel.EARTHQUAKE -> R.string.shake_earthquake
}
