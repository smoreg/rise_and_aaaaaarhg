package dev.smoreg.raa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.smoreg.raa.R
import dev.smoreg.raa.data.ThemeMode

@Immutable
data class Palette(
    val paper: Color,
    val paper2: Color,
    val paper3: Color,
    val ink: Color,
    val inkMuted: Color,
    val line: Color,
    val dawn: Color,
    val onDawn: Color,
    val scream: Color,
)

val Night = Palette(
    paper = Color(0xFF0B0E17),
    paper2 = Color(0xFF141926),
    paper3 = Color(0xFF1F2536),
    ink = Color(0xFFF2EDE6),
    inkMuted = Color(0xFF9CA1B0),
    line = Color(0xFF2A3042),
    dawn = Color(0xFFFF8A3D),
    onDawn = Color(0xFF1A0E06),
    scream = Color(0xFFF03B3B),
)

val Day = Palette(
    paper = Color(0xFFF6F1EA),
    paper2 = Color(0xFFECE5DB),
    paper3 = Color(0xFFE0D7CA),
    ink = Color(0xFF151823),
    inkMuted = Color(0xFF5C6070),
    line = Color(0xFFD4CABB),
    dawn = Color(0xFFB9501A),
    onDawn = Color(0xFFFFF6EE),
    scream = Color(0xFFC62828),
)

/** Sky from night to full morning. The ringing screen walks it as the mission progresses. */
val SkyStops = listOf(
    Color(0xFF0B0E17),
    Color(0xFF2A0F1E),
    Color(0xFF7A1F1A),
    Color(0xFFE0661F),
    Color(0xFFFFC37A),
    Color(0xFFFFF1DC),
)

val SunLow = Color(0xFF9A2A1A)
val SunHigh = Color(0xFFFFE2B0)
val GroundNight = Color(0xFF07090F)
val GroundMorning = Color(0xFF2B1712)

val LocalPalette = staticCompositionLocalOf { Night }

@OptIn(ExperimentalTextApi::class)
private fun unbounded(weight: Int) =
    Font(R.font.unbounded, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Display = FontFamily(unbounded(500), unbounded(700), unbounded(900))

object Type {
    val clock = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 88.sp, letterSpacing = (-0.04).em, lineHeight = 1.0.em)
    val time = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 44.sp, letterSpacing = (-0.03).em)
    val headline = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 26.sp, letterSpacing = (-0.02).em, lineHeight = 1.15.em)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 17.sp, letterSpacing = (-0.01).em)
    val scream = TextStyle(fontFamily = Display, fontWeight = FontWeight(900), fontSize = 40.sp, letterSpacing = (-0.03).em, lineHeight = 1.05.em)
}

@Composable
fun RaaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    RaaTheme(if (dark) Night else Day, dark, content)
}

@Composable
fun RaaTheme(p: Palette, dark: Boolean, content: @Composable () -> Unit) {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.dawn, onPrimary = p.onDawn,
        primaryContainer = p.paper3, onPrimaryContainer = p.ink,
        secondary = p.dawn, onSecondary = p.onDawn,
        secondaryContainer = p.paper3, onSecondaryContainer = p.ink,
        tertiary = p.dawn, onTertiary = p.onDawn,
        background = p.paper, onBackground = p.ink,
        surface = p.paper, onSurface = p.ink,
        surfaceVariant = p.paper3, onSurfaceVariant = p.inkMuted,
        surfaceContainerLowest = p.paper, surfaceContainerLow = p.paper2,
        surfaceContainer = p.paper2, surfaceContainerHigh = p.paper3, surfaceContainerHighest = p.paper3,
        surfaceBright = p.paper3, surfaceDim = p.paper,
        outline = p.line, outlineVariant = p.line,
        error = p.scream, onError = p.ink,
    )
    val t = Typography()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = t.copy(
                headlineLarge = Type.headline,
                headlineMedium = Type.headline.copy(fontSize = 22.sp),
                titleLarge = Type.title.copy(fontSize = 20.sp),
                titleMedium = Type.title,
            ),
            content = content,
        )
    }
}
