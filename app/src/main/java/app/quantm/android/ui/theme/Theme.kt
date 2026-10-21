package app.quantm.android.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette

/** A Quantm color theme: the same nine colors as desktop Quantm's themes */
@Immutable
data class QuantmColors(
    val accent: Color,
    val accentText: Color,
    val bg: Color,
    val surface: Color,
    val bar: Color,
    val text: Color,
    val muted: Color,
    val wave: Color,
    val wavePlayed: Color,
) {
    val isLight: Boolean get() = bg.luminance() > 0.5f
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private fun preset(accent: Long, accentText: Long, bg: Long, surface: Long, bar: Long, text: Long, muted: Long, wave: Long, played: Long) =
    QuantmColors(c(accent), c(accentText), c(bg), c(surface), c(bar), c(text), c(muted), c(wave), c(played))

const val ALBUM_MATCH = "Album Match"

/** Desktop Quantm's public themes, in the same order */
val PRESETS: Map<String, QuantmColors> = linkedMapOf(
    "SoundCloud Dark" to preset(0xff5500, 0xffffff, 0x121212, 0x1e1e1e, 0x181818, 0xf2f2f2, 0xa0a0a0, 0x5c5c5c, 0xff5500),
    "SoundCloud Light" to preset(0xff5500, 0xffffff, 0xf6f6f6, 0xffffff, 0xffffff, 0x1a1a1a, 0x6b6b6b, 0xc4c4c4, 0xff5500),
    "Midnight" to preset(0x5b8cff, 0xffffff, 0x0d1117, 0x161b22, 0x10151c, 0xe6edf3, 0x8b949e, 0x30363d, 0x5b8cff),
    "Synthwave" to preset(0xff2e97, 0xffffff, 0x140b24, 0x1f1235, 0x190e2d, 0xf7e9ff, 0xa991c7, 0x3b2a5a, 0x2ee6ff),
    "Forest" to preset(0x3ddc84, 0x06210f, 0x0f1712, 0x17221b, 0x121c16, 0xe4f1e8, 0x8aa594, 0x2c3d32, 0x3ddc84),
    "Y2K" to preset(0x2f6fe0, 0xffffff, 0xece9d8, 0xffffff, 0x245edb, 0x000000, 0x4d4d4d, 0xa9c3ef, 0x2f6fe0),
    "Glass" to preset(0x7c9cff, 0x0b0f1e, 0x0b1020, 0x18203a, 0x121a30, 0xeef2ff, 0x9aa6c8, 0x2e3a63, 0x7c9cff),
    "Neon" to preset(0x00f0ff, 0x001014, 0x07020f, 0x120821, 0x0c0418, 0xf5e9ff, 0x9a7fbf, 0x2a1745, 0xff2bd6),
    "Pixel" to preset(0xffcc00, 0x1a1200, 0x1d1b2e, 0x2b2840, 0x151325, 0xf4f1de, 0xa8a3c7, 0x3d3957, 0xffcc00),
    "Minimal" to preset(0x111111, 0xffffff, 0xffffff, 0xf7f7f7, 0xffffff, 0x111111, 0x8a8a8a, 0xe2e2e2, 0x111111),
    "Liquid Glass" to preset(0x0a84ff, 0xffffff, 0xe9eef6, 0xffffff, 0xffffff, 0x0b0b0f, 0x5d6472, 0xc3cad6, 0x0a84ff),
    "Liquid Glass Dark" to preset(0x0a84ff, 0xffffff, 0x0a0a0f, 0x1c1c22, 0x16161c, 0xf5f5f7, 0x9a9aa5, 0x3a3a44, 0x0a84ff),
    "Hextech" to preset(0xc8aa6e, 0x010a13, 0x010a13, 0x0a1428, 0x091428, 0xf0e6d2, 0xa09b8c, 0x1e2328, 0x0ac8b9),
    "OLED" to preset(0x3d9bff, 0x000000, 0x000000, 0x0a0a0a, 0x000000, 0xf5f5f5, 0x8a8a8a, 0x262626, 0x3d9bff),
    "E-ink" to preset(0x111111, 0xf4f1ea, 0xf4f1ea, 0xece8df, 0xf4f1ea, 0x111111, 0x5a5a5a, 0xc9c4b8, 0x111111),
    "Industrial" to preset(0xf2b705, 0x111111, 0x1c1d1f, 0x2a2b2e, 0x222326, 0xe6e6e3, 0x9a9b95, 0x3a3b3e, 0xf2b705),
    "Frutiger Aero" to preset(0x08638b, 0xffffff, 0xe5f4fb, 0xf8fcff, 0xd9edf8, 0x143348, 0x466173, 0x668b9e, 0x32732c),
    "Terminal" to preset(0x75f59b, 0x031008, 0x030906, 0x0a170e, 0x061009, 0xc2ffd0, 0x8dc59b, 0x39774a, 0x75f59b),
    "Windows 98" to preset(0x000080, 0xffffff, 0xc0c0c0, 0xdfdfdf, 0xc0c0c0, 0x000000, 0x404040, 0x808080, 0x000080),
    "Vinyl" to preset(0xf1ba7d, 0x25160f, 0x1c1410, 0x30241c, 0x241b15, 0xfff1da, 0xcbb59b, 0x806b57, 0xf1ba7d),
    "Brutalist" to preset(0xb51d12, 0xffffff, 0xffffff, 0xffffff, 0xffffff, 0x000000, 0x4b4b4b, 0x777777, 0xb51d12),
    "Pocket Quantm" to preset(0x405634, 0xf5f5e9, 0xe5e0d5, 0xf5f3ea, 0xeae7de, 0x292d27, 0x565c50, 0x949b89, 0x405634),
    "Terminal Multiplexer" to preset(0x85efa1, 0x061009, 0x060c08, 0x101e14, 0x0b150e, 0xd4f7dc, 0xa1bda7, 0x497855, 0xefc478),
    ALBUM_MATCH to preset(0xff5500, 0xffffff, 0x121212, 0x1e1e1e, 0x181818, 0xf4f4f4, 0xa0a0a0, 0x5c5c5c, 0xff5500),
    "Listening Room" to preset(0xedbd91, 0x25180f, 0x151210, 0x25201c, 0x1b1714, 0xf6ede3, 0xc3b3a4, 0x706052, 0xedbd91),
    "Mono" to preset(0xffffff, 0x000000, 0x000000, 0x111111, 0x0a0a0a, 0xffffff, 0x8a8a8a, 0x333333, 0xffffff),
)

val DefaultColors = PRESETS.getValue("SoundCloud Dark")

/** Album Match: a dark theme built from the artwork's colors */
fun colorsFromArtwork(bitmap: Bitmap): QuantmColors {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val dominant = Color(palette.getDominantColor(0xFF202020.toInt()))
    val vivid = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch)
        ?.let { Color(it.rgb) } ?: DefaultColors.accent
    val accent = if (vivid.luminance() < 0.18f) lerpColor(vivid, Color.White, 0.45f) else vivid
    val bg = lerpColor(dominant, Color.Black, 0.82f)
    val surface = lerpColor(dominant, Color.Black, 0.70f)
    return QuantmColors(
        accent = accent,
        accentText = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White,
        bg = bg,
        surface = surface,
        bar = lerpColor(dominant, Color.Black, 0.76f),
        text = Color(0xFFF4F4F4),
        muted = lerpColor(accent, Color(0xFFB0B0B0), 0.75f),
        wave = lerpColor(dominant, Color(0xFF6A6A6A), 0.7f),
        wavePlayed = accent,
    )
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

val LocalQuantmColors = staticCompositionLocalOf { DefaultColors }

object Quantm {
    val colors: QuantmColors @Composable get() = LocalQuantmColors.current
}

private fun schemeFor(q: QuantmColors): ColorScheme {
    val tint = { amount: Float -> q.text.copy(alpha = amount).compositeOver(q.bg) }
    val container = q.accent.copy(alpha = 0.18f).compositeOver(q.surface)
    return if (q.isLight) lightColorScheme(
        primary = q.accent, onPrimary = q.accentText,
        primaryContainer = container, onPrimaryContainer = q.text,
        secondary = q.accent, onSecondary = q.accentText,
        secondaryContainer = container, onSecondaryContainer = q.text,
        tertiary = q.wavePlayed,
        background = q.bg, onBackground = q.text,
        surface = q.bg, onSurface = q.text,
        surfaceVariant = q.surface, onSurfaceVariant = q.muted,
        surfaceContainerLowest = q.bg, surfaceContainerLow = q.surface,
        surfaceContainer = q.surface, surfaceContainerHigh = tint(0.06f).compositeOver(q.surface),
        surfaceContainerHighest = tint(0.10f),
        inverseSurface = q.text, inverseOnSurface = q.bg,
        outline = q.muted.copy(alpha = 0.6f).compositeOver(q.bg), outlineVariant = tint(0.12f),
        surfaceTint = Color.Transparent,
    ) else darkColorScheme(
        primary = q.accent, onPrimary = q.accentText,
        primaryContainer = container, onPrimaryContainer = q.text,
        secondary = q.accent, onSecondary = q.accentText,
        secondaryContainer = container, onSecondaryContainer = q.text,
        tertiary = q.wavePlayed,
        background = q.bg, onBackground = q.text,
        surface = q.bg, onSurface = q.text,
        surfaceVariant = q.surface, onSurfaceVariant = q.muted,
        surfaceContainerLowest = q.bg, surfaceContainerLow = tint(0.03f),
        surfaceContainer = q.surface, surfaceContainerHigh = tint(0.09f),
        surfaceContainerHighest = tint(0.13f),
        inverseSurface = q.text, inverseOnSurface = q.bg,
        outline = q.muted.copy(alpha = 0.6f).compositeOver(q.bg), outlineVariant = tint(0.10f),
        surfaceTint = Color.Transparent,
    )
}

/** Material You: build Quantm colors from the phone's wallpaper scheme */
private fun fromScheme(s: ColorScheme) = QuantmColors(
    accent = s.primary, accentText = s.onPrimary, bg = s.background, surface = s.surfaceContainer,
    bar = s.surfaceContainerLow, text = s.onBackground, muted = s.onSurfaceVariant,
    wave = s.outlineVariant, wavePlayed = s.primary,
)

@Composable
private fun animated(q: QuantmColors): QuantmColors {
    val spec = tween<Color>(600)
    val accent by animateColorAsState(q.accent, spec, label = "accent")
    val accentText by animateColorAsState(q.accentText, spec, label = "accentText")
    val bg by animateColorAsState(q.bg, spec, label = "bg")
    val surface by animateColorAsState(q.surface, spec, label = "surface")
    val bar by animateColorAsState(q.bar, spec, label = "bar")
    val text by animateColorAsState(q.text, spec, label = "text")
    val muted by animateColorAsState(q.muted, spec, label = "muted")
    val wave by animateColorAsState(q.wave, spec, label = "wave")
    val played by animateColorAsState(q.wavePlayed, spec, label = "played")
    return QuantmColors(accent, accentText, bg, surface, bar, text, muted, wave, played)
}

val QuantmTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val QuantmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SectionTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

/**
 * [preset] is a theme name; [dynamic] uses the wallpaper colors; [artwork] (Album Match) wins
 * over both when set.
 */
@Composable
fun QuantmTheme(preset: String, dynamic: Boolean, artwork: QuantmColors?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = when {
        artwork != null -> artwork
        dynamic && Build.VERSION.SDK_INT >= 31 -> {
            val light = PRESETS[preset]?.isLight == true
            fromScheme(if (light) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context))
        }
        else -> PRESETS[preset] ?: DefaultColors
    }
    val q = animated(base)
    CompositionLocalProvider(LocalQuantmColors provides q) {
        MaterialTheme(colorScheme = schemeFor(q), typography = QuantmTypography, shapes = QuantmShapes, content = content)
    }
}
