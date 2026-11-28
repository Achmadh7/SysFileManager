package id.sysfile.manager

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.abs

const val UI_STYLE_M3 = "m3"
const val UI_STYLE_NOTHING = "nothing"
const val UI_STYLE_LIQUID_GLASS = "liquid_glass"

fun isLiquidGlassStyle(uiStyle: String): Boolean = uiStyle == UI_STYLE_LIQUID_GLASS

fun sanitizeUiStyle(saved: String?): String = when (saved) {
    UI_STYLE_NOTHING -> UI_STYLE_NOTHING
    UI_STYLE_LIQUID_GLASS -> UI_STYLE_LIQUID_GLASS
    else -> UI_STYLE_M3
}

fun resolveFallbackUiStyle(currentStyle: String, savedFallback: String?): String {
    return when (currentStyle) {
        UI_STYLE_NOTHING -> UI_STYLE_NOTHING
        UI_STYLE_M3 -> UI_STYLE_M3
        else -> if (savedFallback == UI_STYLE_NOTHING) UI_STYLE_NOTHING else UI_STYLE_M3
    }
}

fun toggleLiquidGlassUiStyle(savedFallback: String?, enable: Boolean): String {
    return if (enable) UI_STYLE_LIQUID_GLASS else resolveFallbackUiStyle(UI_STYLE_LIQUID_GLASS, savedFallback)
}

@Immutable
data class LiquidGlassStyle(
    val enabled: Boolean = false,
    val dark: Boolean = false,
    val reducedMotion: Boolean = false
)

val LocalLiquidGlass = compositionLocalOf { LiquidGlassStyle() }

val LiquidGlassEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
val LiquidGlassDrawerCurve = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Redam gestur yang melewati batas [min]..[max] secara progresif (rubber-banding Apple). */
fun rubberbandClamp(
    value: Float,
    min: Float,
    max: Float,
    dimension: Float,
    constant: Float = 0.55f
): Float {
    val safeDim = dimension.coerceAtLeast(1f)
    return when {
        value < min -> {
            val overshoot = value - min
            min + (overshoot * safeDim * constant) / (safeDim + constant * abs(overshoot))
        }
        value > max -> {
            val overshoot = value - max
            max + (overshoot * safeDim * constant) / (safeDim + constant * abs(overshoot))
        }
        else -> value
    }
}

fun nearestTabByCenter(pillCenterPx: Float, tabWidthPx: Float, count: Int): Int {
    if (tabWidthPx <= 0f || count <= 0) return 0
    return (pillCenterPx / tabWidthPx).toInt().coerceIn(0, count - 1)
}

fun liquidGlassProgressFromPill(pillX: Float, maxX: Float): Float {
    return if (maxX > 0f) (pillX / maxX).coerceIn(0f, 1f) else 0f
}

fun liquidGlassTabTransition(forward: Boolean, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) {
        return fadeIn(tween(150)) togetherWith fadeOut(tween(120))
    }
    val enter = fadeIn(tween(220, easing = LiquidGlassEaseOut)) +
        slideInHorizontally(tween(260, easing = LiquidGlassDrawerCurve)) { w ->
            if (forward) w / 14 else -w / 14
        } +
        scaleIn(tween(260, easing = LiquidGlassEaseOut), initialScale = 0.97f)
    val exit = fadeOut(tween(150, easing = LiquidGlassEaseOut)) +
        slideOutHorizontally(tween(200, easing = LiquidGlassDrawerCurve)) { w ->
            if (forward) -w / 18 else w / 18
        } +
        scaleOut(tween(180, easing = LiquidGlassEaseOut), targetScale = 0.985f)
    return enter togetherWith exit
}

fun liquidGlassFolderTransition(push: Boolean, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) {
        return fadeIn(tween(150)) togetherWith fadeOut(tween(120))
    }
    return if (push) {
        val enter = slideInHorizontally(tween(280, easing = LiquidGlassDrawerCurve)) { it / 3 } +
            fadeIn(tween(220, easing = LiquidGlassEaseOut))
        val exit = slideOutHorizontally(tween(240, easing = LiquidGlassDrawerCurve)) { -it / 8 } +
            scaleOut(tween(240, easing = LiquidGlassDrawerCurve), targetScale = 0.96f) +
            fadeOut(tween(180, easing = LiquidGlassEaseOut))
        enter togetherWith exit
    } else {
        val enter = slideInHorizontally(tween(260, easing = LiquidGlassDrawerCurve)) { -it / 8 } +
            scaleIn(tween(260, easing = LiquidGlassDrawerCurve), initialScale = 0.96f) +
            fadeIn(tween(220, easing = LiquidGlassEaseOut))
        val exit = slideOutHorizontally(tween(220, easing = LiquidGlassDrawerCurve)) { it / 3 } +
            fadeOut(tween(170, easing = LiquidGlassEaseOut))
        enter togetherWith exit
    }
}

fun liquidGlassOverlayTransition(reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) {
        return fadeIn(tween(150)) togetherWith fadeOut(tween(120))
    }
    val enter = fadeIn(tween(220, easing = LiquidGlassEaseOut)) +
        scaleIn(tween(240, easing = LiquidGlassDrawerCurve), initialScale = 0.96f)
    val exit = fadeOut(tween(150, easing = LiquidGlassEaseOut)) +
        scaleOut(tween(160, easing = LiquidGlassEaseOut), targetScale = 0.98f)
    return enter togetherWith exit
}

fun liquidGlassPanelEnter(reducedMotion: Boolean): EnterTransition {
    if (reducedMotion) return fadeIn(tween(160))
    return slideInHorizontally(tween(260, easing = LiquidGlassDrawerCurve)) { it / 3 } +
        scaleIn(tween(260, easing = LiquidGlassEaseOut), initialScale = 0.97f) +
        fadeIn(tween(220, easing = LiquidGlassEaseOut))
}

fun liquidGlassPanelExit(reducedMotion: Boolean): ExitTransition {
    if (reducedMotion) return fadeOut(tween(130))
    return slideOutHorizontally(tween(200, easing = LiquidGlassDrawerCurve)) { it / 3 } +
        scaleOut(tween(180, easing = LiquidGlassEaseOut), targetScale = 0.98f) +
        fadeOut(tween(160, easing = LiquidGlassEaseOut))
}

fun liquidGlassBarEnter(reducedMotion: Boolean): EnterTransition {
    if (reducedMotion) return fadeIn(tween(150))
    return fadeIn(tween(200, easing = LiquidGlassEaseOut)) +
        slideInVertically(spring<IntOffset>(dampingRatio = 0.84f, stiffness = 460f)) { it / 2 } +
        scaleIn(spring(dampingRatio = 0.84f, stiffness = 460f), initialScale = 0.94f) +
        expandVertically(spring<IntSize>(dampingRatio = 1.0f, stiffness = 460f), expandFrom = Alignment.Bottom)
}

fun liquidGlassBarExit(reducedMotion: Boolean): ExitTransition {
    if (reducedMotion) return fadeOut(tween(120))
    return fadeOut(tween(140, easing = LiquidGlassEaseOut)) +
        slideOutVertically(tween(150, easing = LiquidGlassEaseOut)) { it / 2 } +
        scaleOut(tween(140, easing = LiquidGlassEaseOut), targetScale = 0.95f) +
        shrinkVertically(tween(160, easing = LiquidGlassEaseOut), shrinkTowards = Alignment.Bottom)
}

// Tema 1 — Nordic Slate & Cobalt (Opsi 1): hierarki permukaan bertingkat tegas
// (#0B0E14 gelap / #F8FAFC terang) + garis tepi 1dp + aksen Cobalt (#3B82F6 / #2563EB).
private val SenjaLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    inversePrimary = Color(0xFF60A5FA),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceTint = Color(0xFF2563EB),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF8FAFC),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E8F0),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFDBEAFE),
    primaryFixedDim = Color(0xFF93C5FD),
    onPrimaryFixed = Color(0xFF1E3A8A),
    onPrimaryFixedVariant = Color(0xFF1D4ED8),
    secondaryFixed = Color(0xFFE2E8F0),
    secondaryFixedDim = Color(0xFFCBD5E1),
    onSecondaryFixed = Color(0xFF0F172A),
    onSecondaryFixedVariant = Color(0xFF334155),
    tertiaryFixed = Color(0xFFE0F2FE),
    tertiaryFixedDim = Color(0xFF7DD3FC),
    onTertiaryFixed = Color(0xFF0C4A6E),
    onTertiaryFixedVariant = Color(0xFF0369A1),
)

private val SenjaDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color(0xFFDBEAFE),
    inversePrimary = Color(0xFF2563EB),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B0E14),
    secondaryContainer = Color(0xFF131823),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceTint = Color(0xFF3B82F6),
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF232B3B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1E2636),
    surfaceDim = Color(0xFF080A0F),
    surfaceContainer = Color(0xFF131823),
    surfaceContainerHigh = Color(0xFF1A2130),
    surfaceContainerHighest = Color(0xFF1E2636),
    surfaceContainerLow = Color(0xFF0F131C),
    surfaceContainerLowest = Color(0xFF080A0F),
    primaryFixed = Color(0xFFDBEAFE),
    primaryFixedDim = Color(0xFF60A5FA),
    onPrimaryFixed = Color(0xFF1E3A8A),
    onPrimaryFixedVariant = Color(0xFF1D4ED8),
    secondaryFixed = Color(0xFFE2E8F0),
    secondaryFixedDim = Color(0xFF94A3B8),
    onSecondaryFixed = Color(0xFF0F172A),
    onSecondaryFixedVariant = Color(0xFF334155),
    tertiaryFixed = Color(0xFFE0F2FE),
    tertiaryFixedDim = Color(0xFF38BDF8),
    onTertiaryFixed = Color(0xFF0C4A6E),
    onTertiaryFixedVariant = Color(0xFF0369A1),
)

// Tema 2 — Nothing OS Dot-Matrix: hitam AMOLED #000000 / kertas batu #F2F2EF,
// aksen merah sinyal #D71921, border tipis 1dp hardware, dan tipografi monospace.
val NothingSignalRed = Color(0xFFD71921)
val LocalNothingTheme = compositionLocalOf { false }

private val NothingLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE6E7),
    onPrimaryContainer = Color(0xFF7A090E),
    inversePrimary = Color(0xFFD71921),
    secondary = Color(0xFF3F3F46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFFC8141B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD71921),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2EF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF2F2EF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE2E2DC),
    onSurfaceVariant = Color(0xFF525259),
    surfaceTint = Color(0xFF111111),
    inverseSurface = Color(0xFF18181B),
    inverseOnSurface = Color(0xFFF5F5F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF8A8A92),
    outlineVariant = Color(0xFFCFCFC8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E2DC),
    surfaceContainer = Color(0xFFE8E8E3),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFE4E4E7),
    primaryFixedDim = Color(0xFFD4D4D8),
    onPrimaryFixed = Color(0xFF111111),
    onPrimaryFixedVariant = Color(0xFF27272A),
    secondaryFixed = Color(0xFFE4E4E7),
    secondaryFixedDim = Color(0xFFD4D4D8),
    onSecondaryFixed = Color(0xFF111111),
    onSecondaryFixedVariant = Color(0xFF3F3F46),
    tertiaryFixed = Color(0xFFFCE6E7),
    tertiaryFixedDim = Color(0xFFF9BFC2),
    onTertiaryFixed = Color(0xFF540408),
    onTertiaryFixedVariant = Color(0xFF8C0D13),
)

private val NothingDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2A1215),
    onPrimaryContainer = Color(0xFFFFD9DC),
    inversePrimary = Color(0xFFD71921),
    secondary = Color(0xFFC8C8D0),
    onSecondary = Color(0xFF111113),
    secondaryContainer = Color(0xFF111113),
    onSecondaryContainer = Color(0xFFF5F5F7),
    tertiary = Color(0xFFFF454B),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFD71921),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF222228),
    onSurfaceVariant = Color(0xFF9E9EA8),
    surfaceTint = Color(0xFFF5F5F7),
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF111113),
    error = Color(0xFFFF5449),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF2A2A32),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF27272E),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF16161B),
    surfaceContainerHighest = Color(0xFF131317),
    surfaceContainerLow = Color(0xFF0C0C0F),
    surfaceContainerLowest = Color(0xFF050507),
    primaryFixed = Color(0xFFE4E4E7),
    primaryFixedDim = Color(0xFFA1A1AA),
    onPrimaryFixed = Color(0xFF09090B),
    onPrimaryFixedVariant = Color(0xFF27272A),
    secondaryFixed = Color(0xFFE4E4E7),
    secondaryFixedDim = Color(0xFFA1A1AA),
    onSecondaryFixed = Color(0xFF09090B),
    onSecondaryFixedVariant = Color(0xFF27272A),
    tertiaryFixed = Color(0xFFFCE6E7),
    tertiaryFixedDim = Color(0xFFFF8A8E),
    onTertiaryFixed = Color(0xFF540408),
    onTertiaryFixedVariant = Color(0xFF8C0D13),
)

private val BaseTypography = Typography()
private val NothingTypography: Typography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.Monospace),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.Monospace),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.Monospace),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.3.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.3.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp),
)

/** Latar matriks titik (12dp tile GPU shader) khas Nothing OS. */
@Composable
fun Modifier.nothingDotMatrixBg(): Modifier {
    if (!LocalNothingTheme.current) return this
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val tilePx = with(density) { 12.dp.roundToPx().coerceAtLeast(6) }
    val radiusPx = with(density) { 0.9.dp.toPx().coerceAtLeast(1f) }
    val dotColor = if (isDark) Color(0xFF1F1F26) else Color(0xFFD5D5CE)
    val brush = remember(tilePx, radiusPx, dotColor) {
        val bmp = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor.toArgb() }
        val half = tilePx / 2f
        c.drawCircle(half, half, radiusPx, p)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    return this
        .background(scheme.background)
        .drawBehind { drawRect(brush = brush) }
}

/** Border tipis 1dp untuk memisahkan kartu di atas kanvas hitam/batu saat Tema Nothing aktif. */
@Composable
fun Modifier.nothingBorder(shape: Shape): Modifier {
    if (!LocalNothingTheme.current) return this
    return this.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

private fun glassRimBrush(dark: Boolean): Brush {
    return if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f),
                Color(0xFF476999).copy(alpha = 0.32f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.96f),
                Color.White.copy(alpha = 0.44f),
                Color(0xFFA9C4E4).copy(alpha = 0.56f)
            )
        )
    }
}

/** Border 1dp untuk kartu di Tema 1 (Nordic Slate & Cobalt), Tema 2 (Nothing), dan Tema 3 (iOS Liquid Glass). */
@Composable
fun Modifier.themeCardBorder(shape: Shape): Modifier {
    val glass = LocalLiquidGlass.current
    if (glass.enabled) return this.border(1.dp, glassRimBrush(glass.dark), shape)
    return this.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

// Tema 3 — iOS Liquid Glass (Terang & Gelap Obsidian):
// Permukaan optik tembus pandang, highlight spekular, serta kontras tinggi untuk keterbacaan.
private val LiquidGlassLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EAFF),
    onPrimaryContainer = Color(0xFF00325E),
    inversePrimary = Color(0xFF8CC8FF),
    secondary = Color(0xFF3E638E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xE0FFFFFF),
    onSecondaryContainer = Color(0xFF0D1420),
    tertiary = Color(0xFF0B7A75),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFF2EE),
    onTertiaryContainer = Color(0xFF053835),
    background = Color(0xFFEAF0F8),
    onBackground = Color(0xFF0D1420),
    surface = Color(0xFFF3F7FD),
    onSurface = Color(0xFF0D1420),
    surfaceVariant = Color(0xFFDCE6F4),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color(0xFF0066CC),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6B7C93),
    outlineVariant = Color(0xFFC3D2E5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6E0EE),
    surfaceContainer = Color(0xD8F2F7FE),
    surfaceContainerHigh = Color(0xE0EBF3FD),
    surfaceContainerHighest = Color(0xECFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainerLowest = Color(0xE6FFFFFF),
    primaryFixed = Color(0xFFD6EAFF),
    primaryFixedDim = Color(0xFF8CC8FF),
    onPrimaryFixed = Color(0xFF001C38),
    onPrimaryFixedVariant = Color(0xFF004B96),
    secondaryFixed = Color(0xFFEBF3FC),
    secondaryFixedDim = Color(0xFFBBD0E8),
    onSecondaryFixed = Color(0xFF0D1420),
    onSecondaryFixedVariant = Color(0xFF2B486B),
    tertiaryFixed = Color(0xFFCFF2EE),
    tertiaryFixedDim = Color(0xFF9BD9D2),
    onTertiaryFixed = Color(0xFF042624),
    onTertiaryFixedVariant = Color(0xFF0B5E5A),
)

private val LiquidGlassDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF4DA6FF),
    onPrimary = Color(0xFF002447),
    primaryContainer = Color(0xFF143866),
    onPrimaryContainer = Color(0xFFD6EBFF),
    inversePrimary = Color(0xFF0066CC),
    secondary = Color(0xFF93B8E0),
    onSecondary = Color(0xFF081426),
    secondaryContainer = Color(0xCC141F33),
    onSecondaryContainer = Color(0xFFF1F5FA),
    tertiary = Color(0xFF4DD4C8),
    onTertiary = Color(0xFF002B27),
    tertiaryContainer = Color(0xFF0D4440),
    onTertiaryContainer = Color(0xFFCFF7F3),
    background = Color(0xFF080D18),
    onBackground = Color(0xFFF1F5FA),
    surface = Color(0xFF0E1525),
    onSurface = Color(0xFFF1F5FA),
    surfaceVariant = Color(0xFF1B273D),
    onSurfaceVariant = Color(0xFF9AB0CC),
    surfaceTint = Color(0xFF4DA6FF),
    inverseSurface = Color(0xFFF1F5FA),
    inverseOnSurface = Color(0xFF0D1420),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4D0007),
    errorContainer = Color(0xFF7A1418),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF536B8E),
    outlineVariant = Color(0xFF293B58),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1E2C45),
    surfaceDim = Color(0xFF060A13),
    surfaceContainer = Color(0xCC131D30),
    surfaceContainerHigh = Color(0xD818253B),
    surfaceContainerHighest = Color(0xE01D2C45),
    surfaceContainerLow = Color(0xFF0B111E),
    surfaceContainerLowest = Color(0xCC0F1728),
    primaryFixed = Color(0xFFD6EAFF),
    primaryFixedDim = Color(0xFF8CC8FF),
    onPrimaryFixed = Color(0xFF001C38),
    onPrimaryFixedVariant = Color(0xFF004B96),
    secondaryFixed = Color(0xFFDCE9F7),
    secondaryFixedDim = Color(0xFF93B8E0),
    onSecondaryFixed = Color(0xFF081426),
    onSecondaryFixedVariant = Color(0xFF1E3757),
    tertiaryFixed = Color(0xFFCFF2EE),
    tertiaryFixedDim = Color(0xFF4DD4C8),
    onTertiaryFixed = Color(0xFF002B27),
    onTertiaryFixedVariant = Color(0xFF0D5952),
)

private fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

/** Latar kristal lembut untuk tema iOS Liquid Glass; memberi kedalaman optik di belakang kapsul kaca. */
@Composable
fun LiquidGlassBackground(enabled: Boolean, dark: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val baseColor = if (dark) Color(0xFF070B14) else Color(0xFFEAF0F8)
    val glows = if (dark) {
        listOf(
            Triple(Offset(0.08f, 0.06f), Color(0xFF0B4488), 0.44f),
            Triple(Offset(0.92f, 0.14f), Color(0xFF1F3670), 0.38f),
            Triple(Offset(0.88f, 0.86f), Color(0xFF0D5470), 0.38f),
            Triple(Offset(0.10f, 0.94f), Color(0xFF122C58), 0.36f),
        )
    } else {
        listOf(
            Triple(Offset(0.08f, 0.06f), Color(0xFF9ED0FF), 0.52f),
            Triple(Offset(0.92f, 0.14f), Color(0xFFC9D9FF), 0.48f),
            Triple(Offset(0.88f, 0.86f), Color(0xFFAEEBF2), 0.48f),
            Triple(Offset(0.10f, 0.94f), Color(0xFFC2DCFF), 0.44f),
        )
    }
    Box(Modifier.fillMaxSize().background(baseColor)) {
        Canvas(Modifier.fillMaxSize().blur(48.dp)) {
            val r = size.minDimension * 0.95f
            glows.forEach { (normCenter, color, alpha) ->
                val center = Offset(size.width * normCenter.x, size.height * normCenter.y)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }
        }
        content()
    }
}

/** Permukaan kaca optik untuk kontrol & navigasi melayang: fill tembus pandang + tepi spekular + bayangan lembut. */
@Composable
fun Modifier.liquidGlassChrome(shape: Shape, elevated: Boolean = true): Modifier {
    val dark = LocalLiquidGlass.current.dark
    val fill = if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1D2B42).copy(alpha = 0.82f),
                Color(0xFF121C2E).copy(alpha = 0.72f),
                Color(0xFF17243A).copy(alpha = 0.80f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.84f),
                Color(0xFFEEF4FC).copy(alpha = 0.70f),
                Color(0xFFE4EFFB).copy(alpha = 0.78f),
            )
        )
    }
    val rim = glassRimBrush(dark)
    val shadowTint = if (dark) Color(0xFF02060E) else Color(0xFF1A365D)
    return this
        .shadow(
            elevation = if (elevated) 12.dp else 4.dp,
            shape = shape,
            clip = false,
            ambientColor = shadowTint.copy(alpha = if (dark) 0.36f else 0.14f),
            spotColor = shadowTint.copy(alpha = if (dark) 0.45f else 0.20f)
        )
        .clip(shape)
        .background(fill)
        .border(1.2.dp, rim, shape)
}

/** Umpan balik tekan cepat (130ms ease-out ke 0.965x), otomatis dilewati bila reduced motion sistem aktif. */
@Composable
fun Modifier.liquidGlassPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f
): Modifier {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled || glass.reducedMotion) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 130, easing = LiquidGlassEaseOut),
        label = "glassPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun LiquidGlassSurface(
    shape: Shape,
    defaultColor: Color,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    defaultTonalElevation: Dp = 3.dp,
    defaultShadowElevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val isLiquidGlass = LocalLiquidGlass.current.enabled
    Surface(
        shape = shape,
        color = if (isLiquidGlass) Color.Transparent else defaultColor,
        tonalElevation = if (isLiquidGlass) 0.dp else defaultTonalElevation,
        shadowElevation = if (isLiquidGlass) 0.dp else defaultShadowElevation,
        modifier = modifier.then(
            if (isLiquidGlass) Modifier.liquidGlassChrome(shape, elevated = elevated) else Modifier
        ),
        content = content
    )
}

@Composable
fun glassCardColor(defaultColor: Color): Color {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled) return defaultColor
    return if (glass.dark) Color(0xFF142034).copy(alpha = 0.80f) else Color.White.copy(alpha = 0.84f)
}

@Composable
fun glassCardBorder(): BorderStroke? {
    val glass = LocalLiquidGlass.current
    if (!glass.enabled) return null
    return BorderStroke(width = 1.dp, brush = glassRimBrush(glass.dark))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors(defaultContainerColor: Color? = null): TopAppBarColors {
    val glass = LocalLiquidGlass.current
    return if (glass.enabled) {
        val scrolled = if (glass.dark) Color(0xFF0E1626).copy(alpha = 0.84f) else Color(0xFFF3F7FD).copy(alpha = 0.84f)
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = scrolled
        )
    } else if (defaultContainerColor != null) {
        TopAppBarDefaults.topAppBarColors(containerColor = defaultContainerColor)
    } else {
        TopAppBarDefaults.topAppBarColors()
    }
}

/** Bilah progres matriks segmen merah sinyal #D71921 khas Nothing OS. */
@Composable
fun NothingMatrixProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val clamped = fraction.coerceIn(0.04f, 1f)
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val segW = 5.dp.toPx()
        val gapW = 2.5.dp.toPx()
        val step = segW + gapW
        val cr = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val fillLimit = size.width * clamped
        var x = 0f
        while (x + segW <= size.width - step) {
            drawRoundRect(
                color = if (x < fillLimit) NothingSignalRed else trackColor,
                topLeft = Offset(x, 0f),
                size = Size(segW, size.height),
                cornerRadius = cr
            )
            x += step
        }
        val dotR = (size.height * 0.28f).coerceAtLeast(2f)
        drawCircle(
            color = NothingSignalRed,
            radius = dotR,
            center = Offset(size.width - dotR - 1.dp.toPx(), size.height / 2f)
        )
    }
}

/** Tema aplikasi: mode "light" / "dark" / "system"; uiStyle "m3" / "nothing" / "liquid_glass". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(
    themeMode: String,
    uiStyle: String = UI_STYLE_M3,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val isNothing = uiStyle == UI_STYLE_NOTHING
    val isLiquidGlass = isLiquidGlassStyle(uiStyle)
    val glassStyle = remember(isLiquidGlass, dark, reducedMotion) {
        LiquidGlassStyle(enabled = isLiquidGlass, dark = dark, reducedMotion = reducedMotion)
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    val scheme = when {
        isNothing && dark -> NothingDarkScheme
        isNothing -> NothingLightScheme
        isLiquidGlass && dark -> LiquidGlassDarkScheme
        isLiquidGlass -> LiquidGlassLightScheme
        dark -> SenjaDarkScheme
        else -> SenjaLightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = if (isNothing) NothingTypography else BaseTypography,
        motionScheme = MotionScheme.expressive()
    ) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalNothingTheme provides isNothing,
            LocalLiquidGlass provides glassStyle
        ) {
            Box(Modifier.fillMaxSize().nothingDotMatrixBg()) {
                LiquidGlassBackground(enabled = isLiquidGlass, dark = dark) {
                    content()
                }
            }
        }
    }
}
