package id.liquidglass.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual intensity presets inspired by iOS 26 Liquid Glass materials. */
enum class LiquidGlassVariant {
    /** Crystal-clear glass with crisp refraction and minimal blur (best for floating controls). */
    Clear,
    /** Balanced translucent glass for cards and general surfaces. */
    Regular,
    /** Higher blur & stronger tint for dense text or readability-critical overlays. */
    Frosted
}

/** Optical and surface parameters for a Liquid Glass surface. */
@Immutable
data class LiquidGlassSpec(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val depthEffect: Boolean,
    val chromaticAberration: Boolean,
    val darkTintAlpha: Float,
    val lightTintAlpha: Float,
    val darkSheenAlpha: Float,
    val lightSheenAlpha: Float
)

/** Returns the default optical specification for the given [variant] and elevation role. */
fun liquidGlassSpecFor(variant: LiquidGlassVariant, floating: Boolean): LiquidGlassSpec = when (variant) {
    LiquidGlassVariant.Clear -> LiquidGlassSpec(
        blurRadius = if (floating) 3.dp else 2.dp,
        refractionHeight = if (floating) 20.dp else 12.dp,
        refractionAmount = if (floating) 42.dp else 24.dp,
        depthEffect = true,
        chromaticAberration = true,
        darkTintAlpha = if (floating) 0.08f else 0.05f,
        lightTintAlpha = if (floating) 0.36f else 0.28f,
        darkSheenAlpha = if (floating) 0.18f else 0.12f,
        lightSheenAlpha = 0.48f
    )
    LiquidGlassVariant.Regular -> LiquidGlassSpec(
        blurRadius = if (floating) 6.dp else 4.dp,
        refractionHeight = if (floating) 16.dp else 10.dp,
        refractionAmount = if (floating) 32.dp else 18.dp,
        depthEffect = true,
        chromaticAberration = floating,
        darkTintAlpha = if (floating) 0.16f else 0.11f,
        lightTintAlpha = if (floating) 0.48f else 0.38f,
        darkSheenAlpha = if (floating) 0.16f else 0.10f,
        lightSheenAlpha = 0.44f
    )
    LiquidGlassVariant.Frosted -> LiquidGlassSpec(
        blurRadius = if (floating) 14.dp else 10.dp,
        refractionHeight = if (floating) 12.dp else 8.dp,
        refractionAmount = if (floating) 22.dp else 12.dp,
        depthEffect = floating,
        chromaticAberration = false,
        darkTintAlpha = if (floating) 0.34f else 0.24f,
        lightTintAlpha = if (floating) 0.68f else 0.56f,
        darkSheenAlpha = 0.12f,
        lightSheenAlpha = 0.38f
    )
}

/** Dark Material 3 ColorScheme tuned for Liquid Glass transparency and 4.5:1+ legibility. */
val LiquidGlassDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF002A3D),
    primaryContainer = Color(0x4D5AC8FA),
    onPrimaryContainer = Color(0xFFD9F3FF),
    secondary = Color(0xFFB4C8E8),
    onSecondary = Color(0xFF102033),
    secondaryContainer = Color(0x66101624),
    onSecondaryContainer = Color(0xFFF0F4FC),
    tertiary = Color(0xFFD9B8FF),
    onTertiary = Color(0xFF28123D),
    background = Color.Transparent,
    onBackground = Color(0xFFF4F7FC),
    surface = Color.Transparent,
    onSurface = Color(0xFFF4F7FC),
    surfaceVariant = Color(0x4D101624),
    onSurfaceVariant = Color(0xFFB8C4D8),
    surfaceContainerLowest = Color(0x3D101624),
    surfaceContainerLow = Color(0x4D101624),
    surfaceContainer = Color(0x66101624),
    surfaceContainerHigh = Color(0x8C101624),
    surfaceContainerHighest = Color(0x66101624),
    outline = Color.White.copy(alpha = 0.28f),
    outlineVariant = Color.White.copy(alpha = 0.18f)
)

/** Light Material 3 ColorScheme tuned for Liquid Glass transparency and crisp readability. */
val LiquidGlassLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0x380A84FF),
    onPrimaryContainer = Color(0xFF003875),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0x8CF4F8FF),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFF0F172A),
    surface = Color.Transparent,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0x7AF4F8FF),
    onSurfaceVariant = Color(0xFF334155),
    surfaceContainerLowest = Color(0x52F4F8FF),
    surfaceContainerLow = Color(0x66F4F8FF),
    surfaceContainer = Color(0x80F4F8FF),
    surfaceContainerHigh = Color(0xB3FFFFFF),
    surfaceContainerHighest = Color(0x80F4F8FF),
    outline = Color(0x4D0F172A),
    outlineVariant = Color.White.copy(alpha = 0.84f)
)
