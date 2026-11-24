package id.sysfile.manager

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** Tema aplikasi: mode "light" / "dark" / "system"; uiStyle "m3" / "nothing". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(
    themeMode: String,
    uiStyle: String = "m3",
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val isNothing = uiStyle == "nothing"
    val scheme = when {
        isNothing && dark -> NothingDarkScheme
        isNothing -> NothingLightScheme
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
            LocalNothingTheme provides isNothing
        ) {
            Box(Modifier.fillMaxSize().nothingDotMatrixBg()) { content() }
        }
    }
}
