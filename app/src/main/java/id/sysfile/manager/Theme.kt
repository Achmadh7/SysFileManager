package id.sysfile.manager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/** Tema aplikasi: mode "light" / "dark" / "system" dari preferensi,
 *  gaya "material" (Biru Senja) atau "liquid" (Liquid Glass, r210). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(themeMode: String, themeStyle: String = "material", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // r210: gaya Liquid Glass — warna + skema dari LiquidKit; semua
    // layar membaca peran skema yang sama, jadi warnanya ikut sekali.
    if (themeStyle == "liquid") {
        LiquidTheme(dark) { content() }
        return
    }
    val scheme = if (dark) SenjaDarkScheme else SenjaLightScheme
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive()
    ) {
        // Warna konten bawaan mengikuti onSurface skema (putih di mode
        // gelap, hitam di mode terang). Tanpa penyedia ini, teks tanpa
        // warna eksplisit yang berada di luar Surface - nama berkas,
        // judul seksi, label petak kategori - jatuh ke hitam bawaan
        // CompositionLocal, seperti terlihat pada tangkapan layar
        // Beranda mode gelap 2026-11-11.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            content()
        }
    }
}
