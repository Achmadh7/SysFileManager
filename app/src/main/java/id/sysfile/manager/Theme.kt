package id.sysfile.manager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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

// Tema 2 "iOS Liquid Glass": skema Senja dengan latar/surface transparan
// dan kontainer kaca (putih tembus pandang) di atas wallpaper blob.
// Kontainer High (dialog/menu) tetap hampir pekat supaya terbaca tanpa blur.
private val GlassDarkScheme: ColorScheme = SenjaDarkScheme.copy(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF002A3D),
    surfaceTint = Color(0xFF5AC8FA),
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceContainerLowest = Color.White.copy(alpha = 0.06f),
    surfaceContainerLow = Color.White.copy(alpha = 0.09f),
    surfaceContainer = Color(0xD91A2030),
    surfaceContainerHigh = Color(0xF21C2232),
    surfaceContainerHighest = Color.White.copy(alpha = 0.12f),
    outlineVariant = Color.White.copy(alpha = 0.22f),
)

private val GlassLightScheme: ColorScheme = SenjaLightScheme.copy(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    surfaceTint = Color(0xFF0A84FF),
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceContainerLowest = Color.White.copy(alpha = 0.40f),
    surfaceContainerLow = Color.White.copy(alpha = 0.45f),
    surfaceContainer = Color(0xE6F4F7FD),
    surfaceContainerHigh = Color(0xF7FFFFFF),
    surfaceContainerHighest = Color.White.copy(alpha = 0.55f),
    outlineVariant = Color.White.copy(alpha = 0.75f),
)

/** true bila tema 2 (Liquid Glass) aktif — dipakai untuk rim kaca. */
val LocalGlass = staticCompositionLocalOf { false }

/** Garis tepi kaca: gradasi vertikal putih .45 → .05. */
fun glassRim(): Brush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))
)

/** Gelap/terang aktif di mode kaca (untuk wallpaper per layar). */
val LocalGlassDark = staticCompositionLocalOf { false }

/** Inset bawah navbar melayang: konten boleh lewat di bawahnya (blur). */
val LocalNavInset = compositionLocalOf { 0.dp }

/** Warna dasar wallpaper kaca (juga latar blur Haze). */
fun glassBaseColor(dark: Boolean): Color = if (dark) Color(0xFF070A14) else Color(0xFFEAF0FB)

/**
 * Wallpaper blob digambar dalam koordinat jendela: tiap layar yang
 * memakainya tampak satu wallpaper yang sama tanpa sambungan, dan
 * pekat — dua layar yang sedang bertukar tidak saling tembus.
 */
@Composable
fun Modifier.glassScreenBg(): Modifier {
    val win = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (!LocalGlass.current) return this
    val dark = LocalGlassDark.current
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            drawRect(glassBaseColor(dark))
            val a = if (dark) 0.55f else 0.35f
            val w = win.width.toFloat().takeIf { it > 0f } ?: size.width
            val h = win.height.toFloat().takeIf { it > 0f } ?: size.height
            fun blob(c: Color, x: Float, y: Float, r: Float) {
                val center = Offset(w * x, h * y) - origin
                val radius = minOf(w, h) * r
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, radius),
                    radius = radius,
                    center = center
                )
            }
            blob(Color(0xFF2563EB), 0.15f, 0.12f, 0.9f)
            blob(Color(0xFFC026D3), 0.95f, 0.45f, 0.8f)
            blob(Color(0xFF0EA5E9), 0.25f, 0.92f, 0.85f)
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
        glass && dark -> GlassDarkScheme
        glass -> GlassLightScheme
        dark -> SenjaDarkScheme
        else -> SenjaLightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive()
    ) {
        // Struktur pohon sama untuk dua gaya supaya state layar tidak
        // ter-reset saat ganti tema; mode kaca hanya menambah wallpaper.
        //
        // Warna konten bawaan mengikuti onSurface skema (putih di mode
        // gelap, hitam di mode terang). Tanpa penyedia ini, teks tanpa
        // warna eksplisit yang berada di luar Surface - nama berkas,
        // judul seksi, label petak kategori - jatuh ke hitam bawaan
        // CompositionLocal, seperti terlihat pada tangkapan layar
        // Beranda mode gelap 2026-11-11.
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalGlass provides glass,
            LocalGlassDark provides dark
        ) {
            Box(if (glass) Modifier.fillMaxSize().glassScreenBg() else Modifier) { content() }
        }
    }
}
