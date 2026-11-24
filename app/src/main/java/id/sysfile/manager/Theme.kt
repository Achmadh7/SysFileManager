package id.sysfile.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

// Skema seed biru senja (r80, pilihan Achmad 2026-11-19 dari papan
// warna WN2): nilai di bawah DIBANGKITKAN oleh Material Color
// Utilities resmi Google (material-foundation/material-color-
// utilities, main) dari seed #3F5A73 (Biru Senja) via
// SchemeTonalSpot — spec Tonal Spot yang sama dengan dynamic
// color. Bukan dari ingatan: dicetak program GenScheme.java dan
// disalin apa adanya (48 peran, konstruktor penuh ColorScheme
// material3 1.5.0-alpha08 incl. peran Fixed). Hasilnya biru baja:
// primary #2E628C terang / #9ACBFA gelap; kartu #CDE5FF /
// #3A4857; latar #F7F9FF / #101418. Coklat Kopi #6D4C41 (r63)
// diganti atas pilihannya sendiri.
private val SenjaLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF2E628C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF0B4A72),
    inversePrimary = Color(0xFF9ACBFA),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F6),
    onSecondaryContainer = Color(0xFF3A4857),
    tertiary = Color(0xFF67587A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEDCFF),
    onTertiaryContainer = Color(0xFF4F4061),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceTint = Color(0xFF2E628C),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F9FF),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceContainer = Color(0xFFECEEF3),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE0E2E8),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFCDE5FF),
    primaryFixedDim = Color(0xFF9ACBFA),
    onPrimaryFixed = Color(0xFF001D32),
    onPrimaryFixedVariant = Color(0xFF0B4A72),
    secondaryFixed = Color(0xFFD5E4F6),
    secondaryFixedDim = Color(0xFFB9C8DA),
    onSecondaryFixed = Color(0xFF0E1D2A),
    onSecondaryFixedVariant = Color(0xFF3A4857),
    tertiaryFixed = Color(0xFFEEDCFF),
    tertiaryFixedDim = Color(0xFFD2BFE7),
    onTertiaryFixed = Color(0xFF221533),
    onTertiaryFixedVariant = Color(0xFF4F4061),
)

private val SenjaDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF9ACBFA),
    onPrimary = Color(0xFF003352),
    primaryContainer = Color(0xFF0B4A72),
    onPrimaryContainer = Color(0xFFCDE5FF),
    inversePrimary = Color(0xFF2E628C),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F6),
    tertiary = Color(0xFFD2BFE7),
    onTertiary = Color(0xFF382A49),
    tertiaryContainer = Color(0xFF4F4061),
    onTertiaryContainer = Color(0xFFEEDCFF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceTint = Color(0xFF9ACBFA),
    inverseSurface = Color(0xFFE0E2E8),
    inverseOnSurface = Color(0xFF2D3135),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C9198),
    outlineVariant = Color(0xFF42474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF36393E),
    surfaceDim = Color(0xFF101418),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF323539),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainerLowest = Color(0xFF0B0F12),
    primaryFixed = Color(0xFFCDE5FF),
    primaryFixedDim = Color(0xFF9ACBFA),
    onPrimaryFixed = Color(0xFF001D32),
    onPrimaryFixedVariant = Color(0xFF0B4A72),
    secondaryFixed = Color(0xFFD5E4F6),
    secondaryFixedDim = Color(0xFFB9C8DA),
    onSecondaryFixed = Color(0xFF0E1D2A),
    onSecondaryFixedVariant = Color(0xFF3A4857),
    tertiaryFixed = Color(0xFFEEDCFF),
    tertiaryFixedDim = Color(0xFFD2BFE7),
    onTertiaryFixed = Color(0xFF221533),
    onTertiaryFixedVariant = Color(0xFF4F4061),
)

// Tema 2 Liquid Glass (Kyant0/AndroidLiquidGlass): skema transparan dengan
// refraksi AGSL (API 33+), blur RenderEffect (API 31+), dan fallback tint pekat.
private val LiquidGlassDarkScheme: ColorScheme = SenjaDarkScheme.copy(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF002A3D),
    primaryContainer = Color(0x4D5AC8FA),
    onPrimaryContainer = Color(0xFFD9F3FF),
    secondaryContainer = Color(0x8A101624),
    onSecondaryContainer = Color(0xFFF0F4FC),
    surfaceTint = Color(0xFF5AC8FA),
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceContainerLowest = Color(0x52101624),
    surfaceContainerLow = Color(0x66101624),
    surfaceContainer = Color(0x7A101624),
    surfaceContainerHigh = Color(0xA6101624),
    surfaceContainerHighest = Color(0x7A101624),
    outlineVariant = Color.White.copy(alpha = 0.22f),
)

private val LiquidGlassLightScheme: ColorScheme = SenjaLightScheme.copy(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0x380A84FF),
    onPrimaryContainer = Color(0xFF003875),
    secondaryContainer = Color(0xA3F4F8FF),
    onSecondaryContainer = Color(0xFF0F172A),
    surfaceTint = Color(0xFF0A84FF),
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceContainerLowest = Color(0x66F4F8FF),
    surfaceContainerLow = Color(0x7AF4F8FF),
    surfaceContainer = Color(0x94F4F8FF),
    surfaceContainerHigh = Color(0xB8FFFFFF),
    surfaceContainerHighest = Color(0x94F4F8FF),
    outlineVariant = Color.White.copy(alpha = 0.86f),
)

/** true bila tema 2 (Liquid Glass) aktif. */
val LocalLiquidGlass = staticCompositionLocalOf { false }

/** Mode gelap aktif di tema Liquid Glass. */
val LocalLiquidGlassDark = staticCompositionLocalOf { false }

/** LayerBackdrop latar wallpaper untuk refraksi kaca real-time. */
val LocalBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Inset bawah navbar melayang agar konten mengalir di bawah kaca. */
val LocalNavInset = compositionLocalOf { 0.dp }

/** Warna container transparan saat Liquid Glass aktif agar efek backdrop tidak tertutup. */
@Composable
fun liquidContainerColor(fallback: Color): Color =
    if (LocalLiquidGlass.current) Color.Transparent else fallback

/**
 * Modifier Liquid Glass terpadu (Kyant0/AndroidLiquidGlass):
 * vibrancy + blur + AGSL lens refraction (+ chromatic aberration pada kontrol mengambang)
 * + highlight specular bawaan.
 */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    floating: Boolean = false,
    selected: Boolean = false,
    backdrop: LayerBackdrop? = LocalBackdrop.current
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = LocalLiquidGlassDark.current
    val primary = MaterialTheme.colorScheme.primary
    val surfaceTint = when {
        selected -> primary.copy(alpha = if (dark) 0.26f else 0.18f)
        dark && floating -> Color(0x8C101624)
        dark -> Color(0x66101624)
        floating -> Color(0xA6FFFFFF)
        else -> Color(0x80F4F8FF)
    }
    if (backdrop == null) {
        return this.clip(shape).background(surfaceTint)
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(if (floating) 10.dp.toPx() else 6.dp.toPx())
            lens(
                refractionHeight = if (floating) 14.dp.toPx() else 8.dp.toPx(),
                refractionAmount = if (floating) 28.dp.toPx() else 14.dp.toPx(),
                depthEffect = floating,
                chromaticAberration = floating
            )
        },
        shadow = if (floating) ({ Shadow.Default }) else null,
        onDrawSurface = { drawRect(surfaceTint) }
    )
}

/** Wallpaper gradien ambient untuk memunculkan refraksi Liquid Glass. */
@Composable
fun Modifier.liquidGlassBg(): Modifier {
    val win = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (!LocalLiquidGlass.current) return this
    val dark = LocalLiquidGlassDark.current
    val base = if (dark) Color(0xFF070B16) else Color(0xFFEAF1FC)
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            drawRect(base)
            val a = if (dark) 0.58f else 0.36f
            val w = win.width.toFloat().takeIf { it > 0f } ?: size.width
            val h = win.height.toFloat().takeIf { it > 0f } ?: size.height
            fun orb(c: Color, x: Float, y: Float, r: Float) {
                val center = Offset(w * x, h * y) - origin
                val radius = minOf(w, h) * r
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, radius),
                    radius = radius,
                    center = center
                )
            }
            orb(Color(0xFF2563EB), 0.14f, 0.12f, 0.90f)
            orb(Color(0xFFC026D3), 0.94f, 0.44f, 0.80f)
            orb(Color(0xFF0EA5E9), 0.24f, 0.92f, 0.85f)
        }
}

/** Tema aplikasi: mode "light" / "dark" / "system"; uiStyle "m3" / "glass". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(themeMode: String, uiStyle: String = "m3", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val glass = uiStyle == "glass"
    val scheme = when {
        glass && dark -> LiquidGlassDarkScheme
        glass -> LiquidGlassLightScheme
        dark -> SenjaDarkScheme
        else -> SenjaLightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive()
    ) {
        val bgBackdrop = if (glass) rememberLayerBackdrop() else null
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalLiquidGlass provides glass,
            LocalLiquidGlassDark provides dark,
            LocalBackdrop provides bgBackdrop
        ) {
            if (glass && bgBackdrop != null) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().liquidGlassBg().layerBackdrop(bgBackdrop))
                    content()
                }
            } else {
                Box(Modifier) { content() }
            }
        }
    }
}
