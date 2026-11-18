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

// Skema seed teal (r36, pilihan Achmad 2026-11-18 dari papan tema):
// nilai di bawah DIBANGKITKAN oleh Material Color Utilities resmi
// Google (material-foundation/material-color-utilities, main)
// dari seed #00696D (Teal) via SchemeTonalSpot — spec Tonal Spot
// yang sama dengan dynamic color. Bukan dari ingatan: dicetak
// program GenScheme.java dan disalin apa adanya (48 peran,
// konstruktor penuh ColorScheme material3 1.5.0-alpha08 incl.
// peran Fixed). Hasilnya teal tenang: primary #00696D terang /
// #80D4D8 gelap; kartu #CCE8E9 / #324B4C; latar #F4FBFA / #0E1415.
// Biru #0B57D0 (r22) diganti atas pilihannya sendiri.
private val TealLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F5),
    onPrimaryContainer = Color(0xFF004F52),
    inversePrimary = Color(0xFF80D4D8),
    secondary = Color(0xFF4A6364),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF324B4C),
    tertiary = Color(0xFF4E5F7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E3FF),
    onTertiaryContainer = Color(0xFF364764),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceTint = Color(0xFF00696D),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FBFA),
    surfaceDim = Color(0xFFD5DBDB),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDDE4E3),
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFF9CF1F5),
    primaryFixedDim = Color(0xFF80D4D8),
    onPrimaryFixed = Color(0xFF002021),
    onPrimaryFixedVariant = Color(0xFF004F52),
    secondaryFixed = Color(0xFFCCE8E9),
    secondaryFixedDim = Color(0xFFB1CCCD),
    onSecondaryFixed = Color(0xFF041F21),
    onSecondaryFixedVariant = Color(0xFF324B4C),
    tertiaryFixed = Color(0xFFD5E3FF),
    tertiaryFixedDim = Color(0xFFB5C7E9),
    onTertiaryFixed = Color(0xFF071C36),
    onTertiaryFixedVariant = Color(0xFF364764),
)

private val TealDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF80D4D8),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9CF1F5),
    inversePrimary = Color(0xFF00696D),
    secondary = Color(0xFFB1CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4C),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB5C7E9),
    onTertiary = Color(0xFF1F314C),
    tertiaryContainer = Color(0xFF364764),
    onTertiaryContainer = Color(0xFFD5E3FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceTint = Color(0xFF80D4D8),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3232),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3B),
    surfaceDim = Color(0xFF0E1415),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainerLowest = Color(0xFF090F10),
    primaryFixed = Color(0xFF9CF1F5),
    primaryFixedDim = Color(0xFF80D4D8),
    onPrimaryFixed = Color(0xFF002021),
    onPrimaryFixedVariant = Color(0xFF004F52),
    secondaryFixed = Color(0xFFCCE8E9),
    secondaryFixedDim = Color(0xFFB1CCCD),
    onSecondaryFixed = Color(0xFF041F21),
    onSecondaryFixedVariant = Color(0xFF324B4C),
    tertiaryFixed = Color(0xFFD5E3FF),
    tertiaryFixedDim = Color(0xFFB5C7E9),
    onTertiaryFixed = Color(0xFF071C36),
    onTertiaryFixedVariant = Color(0xFF364764),
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
    val scheme = if (dark) TealDarkScheme else TealLightScheme
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
