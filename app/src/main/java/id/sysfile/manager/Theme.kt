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

// Skema seed coklat (r63, pilihan Achmad 2026-11-19 dari papan warna W1):
// nilai di bawah DIBANGKITKAN oleh Material Color Utilities resmi
// Google (material-foundation/material-color-utilities, main)
// dari seed #6D4C41 (Coklat Kopi) via SchemeTonalSpot — spec Tonal Spot
// yang sama dengan dynamic color. Bukan dari ingatan: dicetak
// program GenScheme.java dan disalin apa adanya (48 peran,
// konstruktor penuh ColorScheme material3 1.5.0-alpha08 incl.
// peran Fixed). Hasilnya coklat hangat: primary #8F4C34 terang /
// #FFB59C gelap; kartu #FFDBCF / #5D4036; latar #FFF8F6 / #1A110F.
// Teal #00696D (r36) diganti atas pilihannya sendiri.
private val CoklatLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF8F4C34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF723520),
    inversePrimary = Color(0xFFFFB59C),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF5D4036),
    tertiary = Color(0xFF6A5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E2A7),
    onTertiaryContainer = Color(0xFF51461A),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceTint = Color(0xFF8F4C34),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFE8D6D1),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF7E4DF),
    surfaceContainerHighest = Color(0xFFF1DFD9),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFFFDBCF),
    primaryFixedDim = Color(0xFFFFB59C),
    onPrimaryFixed = Color(0xFF390C00),
    onPrimaryFixedVariant = Color(0xFF723520),
    secondaryFixed = Color(0xFFFFDBCF),
    secondaryFixedDim = Color(0xFFE7BDB0),
    onSecondaryFixed = Color(0xFF2C160E),
    onSecondaryFixedVariant = Color(0xFF5D4036),
    tertiaryFixed = Color(0xFFF3E2A7),
    tertiaryFixedDim = Color(0xFFD6C68D),
    onTertiaryFixed = Color(0xFF221B00),
    onTertiaryFixedVariant = Color(0xFF51461A),
)

private val CoklatDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFFFB59C),
    onPrimary = Color(0xFF55200C),
    primaryContainer = Color(0xFF723520),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFF8F4C34),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D4036),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD6C68D),
    onTertiary = Color(0xFF393005),
    tertiaryContainer = Color(0xFF51461A),
    onTertiaryContainer = Color(0xFFF3E2A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BB),
    surfaceTint = Color(0xFFFFB59C),
    inverseSurface = Color(0xFFF1DFD9),
    inverseOnSurface = Color(0xFF392E2B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423733),
    surfaceDim = Color(0xFF1A110F),
    surfaceContainer = Color(0xFF271D1A),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D322F),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainerLowest = Color(0xFF140C0A),
    primaryFixed = Color(0xFFFFDBCF),
    primaryFixedDim = Color(0xFFFFB59C),
    onPrimaryFixed = Color(0xFF390C00),
    onPrimaryFixedVariant = Color(0xFF723520),
    secondaryFixed = Color(0xFFFFDBCF),
    secondaryFixedDim = Color(0xFFE7BDB0),
    onSecondaryFixed = Color(0xFF2C160E),
    onSecondaryFixedVariant = Color(0xFF5D4036),
    tertiaryFixed = Color(0xFFF3E2A7),
    tertiaryFixedDim = Color(0xFFD6C68D),
    onTertiaryFixed = Color(0xFF221B00),
    onTertiaryFixedVariant = Color(0xFF51461A),
)

/** Tema aplikasi: mode "light" / "dark" / "system" dari preferensi. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) CoklatDarkScheme else CoklatLightScheme
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
