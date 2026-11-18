package id.sysfile.manager

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

// Skema seed biru (r22, pilihan Achmad 2026-11-18): nilai di bawah
// DIBANGKITKAN oleh Material Color Utilities resmi Google
// (material-foundation/material-color-utilities, main) dari seed
// #0B57D0 (Google Blue) via SchemeTonalSpot — spec Tonal Spot yang
// sama dengan dynamic color. Bukan dari ingatan: dicetak program
// GenScheme.java dan disalin apa adanya (48 peran, konstruktor penuh
// ColorScheme material3 1.5.0-alpha08 incl. peran Fixed). Hasilnya biru
// kalem: primary #495D92 terang / #B2C5FF gelap; kartu #DCE2F9 / #404659;
// latar #FAF8FF / #121318. Ungu resmi r21 sudah tidak ada lagi.
private val BlueLightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF495D92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF304578),
    inversePrimary = Color(0xFFB2C5FF),
    secondary = Color(0xFF585E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFF735471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFED7F9),
    onTertiaryContainer = Color(0xFF5A3D59),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF495D92),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFDAE2FF),
    primaryFixedDim = Color(0xFFB2C5FF),
    onPrimaryFixed = Color(0xFF001847),
    onPrimaryFixedVariant = Color(0xFF304578),
    secondaryFixed = Color(0xFFDCE2F9),
    secondaryFixedDim = Color(0xFFC0C6DD),
    onSecondaryFixed = Color(0xFF151B2C),
    onSecondaryFixedVariant = Color(0xFF404659),
    tertiaryFixed = Color(0xFFFED7F9),
    tertiaryFixedDim = Color(0xFFE1BBDD),
    onTertiaryFixed = Color(0xFF2B122B),
    onTertiaryFixedVariant = Color(0xFF5A3D59),
)

private val BlueDarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFFB2C5FF),
    onPrimary = Color(0xFF172E60),
    primaryContainer = Color(0xFF304578),
    onPrimaryContainer = Color(0xFFDAE2FF),
    inversePrimary = Color(0xFF495D92),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE1BBDD),
    onTertiary = Color(0xFF412741),
    tertiaryContainer = Color(0xFF5A3D59),
    onTertiaryContainer = Color(0xFFFED7F9),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFB2C5FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33343A),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainerLowest = Color(0xFF0D0E13),
    primaryFixed = Color(0xFFDAE2FF),
    primaryFixedDim = Color(0xFFB2C5FF),
    onPrimaryFixed = Color(0xFF001847),
    onPrimaryFixedVariant = Color(0xFF304578),
    secondaryFixed = Color(0xFFDCE2F9),
    secondaryFixedDim = Color(0xFFC0C6DD),
    onSecondaryFixed = Color(0xFF151B2C),
    onSecondaryFixedVariant = Color(0xFF404659),
    tertiaryFixed = Color(0xFFFED7F9),
    tertiaryFixedDim = Color(0xFFE1BBDD),
    onTertiaryFixed = Color(0xFF2B122B),
    onTertiaryFixedVariant = Color(0xFF5A3D59),
)

/** Latar aurora: warna dasar skema + empat gumpalan cahaya radial dari
 *  peran container skema, digambar sekali di belakang konten. Di tema
 *  terang cahayanya jauh lebih lembut. r21: gumpalan memakai peran skema
 *  resmi Google, bukan lagi navy tulisan tangan.
 *  UX2 Kaca Penuh (pilihan Achmad 2026-11-18): gumpalan lebih besar
 *  (radius 1.15x) dan lebih pekat, supaya kaca punya sesuatu untuk
 *  ditembus di semua layar. */
@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val gelap = scheme.background.luminance() < 0.05f
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 1.15f
            // Warna dinamis nyala: gumpalan mengikuti skema dinamis
            // (container = gelap di tema gelap, pastel di tema terang),
            // supaya latar selaras dengan warna wallpaper. Mati:
            // gumpalan arah C asli (cyan/ungu/biru) tidak berubah.
            // r21: gumpalan selalu dari peran container skema resmi
            // (primary/secondary/tertiaryContainer) — tidak ada lagi
            // warna navy tulisan tangan; aurora mengikuti skema Google.
            val blobs = listOf(
                Triple(Offset(size.width * 0.02f, size.height * 0.04f), scheme.primaryContainer, if (gelap) 0.65f else 0.45f),
                Triple(Offset(size.width * 0.98f, size.height * 0.16f), scheme.secondaryContainer, if (gelap) 0.60f else 0.40f),
                Triple(Offset(size.width * 0.92f, size.height * 0.88f), scheme.tertiaryContainer, if (gelap) 0.65f else 0.45f),
                Triple(Offset(size.width * 0.04f, size.height * 0.96f), scheme.primaryContainer, if (gelap) 0.55f else 0.38f)
            )
            blobs.forEach { (pusat, warna, alfa) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(warna.copy(alpha = alfa), Color.Transparent),
                        center = pusat,
                        radius = r
                    ),
                    radius = r,
                    center = pusat
                )
            }
        }
        content()
    }
}

/** Tema aplikasi: mode "light" / "dark" / "system" dari preferensi. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileTheme(themeMode: String, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) BlueDarkScheme else BlueLightScheme
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
