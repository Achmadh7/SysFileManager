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

// Skema warna "Kaca & Aurora" SysFile (arah C, pilihan pemilik 2026-11-11):
// gelap = navy sangat pekat dengan permukaan kaca bertingkat dan aksen cyan
// + ungu; terang = kaca cerah di atas latar biru pucat. Ditulis tangan
// mengikuti peran warna Material 3 (tanpa peran "shadow": peran itu membuat
// skema gagal dikompilasi di Material3 1.5.0-alpha08).
private val LightScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBFF),
    onPrimaryContainer = Color(0xFF07334D),
    inversePrimary = Color(0xFF40C4FF),
    secondary = Color(0xFF7C5CD6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DEFB),
    onSecondaryContainer = Color(0xFF372A6E),
    tertiary = Color(0xFF0E9488),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8F2EC),
    onTertiaryContainer = Color(0xFF06423C),
    background = Color(0xFFEDF2F8),
    onBackground = Color(0xFF0E1A26),
    surface = Color(0xFFF7FAFD),
    onSurface = Color(0xFF0E1A26),
    surfaceVariant = Color(0xFFDCE8F2),
    onSurfaceVariant = Color(0xFF51667A),
    surfaceTint = Color(0xFF0284C7),
    inverseSurface = Color(0xFF0B1526),
    inverseOnSurface = Color(0xFFEDF3F9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF9FB4C8),
    outlineVariant = Color(0xFFC9D8E8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8E4F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F8FC),
    surfaceContainer = Color(0xFFEAF1F8),
    surfaceContainerHigh = Color(0xFFE0EAF4),
    surfaceContainerHighest = Color(0xFFD6E4F2)
)

private val DarkScheme: ColorScheme = ColorScheme(
    primary = Color(0xFF40C4FF),
    onPrimary = Color(0xFF06283D),
    primaryContainer = Color(0xFF0E3A52),
    onPrimaryContainer = Color(0xFFBDE9FF),
    inversePrimary = Color(0xFF0284C7),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2A1B54),
    secondaryContainer = Color(0xFF2C2150),
    onSecondaryContainer = Color(0xFFDED2FF),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF053B35),
    tertiaryContainer = Color(0xFF0B3D38),
    onTertiaryContainer = Color(0xFFB9F4EC),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0A111F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1B2A44),
    onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF40C4FF),
    inverseSurface = Color(0xFFEDF3F9),
    inverseOnSurface = Color(0xFF0B1526),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3D5478),
    outlineVariant = Color(0xFF22364F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF24334D),
    surfaceDim = Color(0xFF070B14),
    surfaceContainerLowest = Color(0xFF05080F),
    surfaceContainerLow = Color(0xFF0C1424),
    surfaceContainer = Color(0xFF111C30),
    surfaceContainerHigh = Color(0xFF17263F),
    surfaceContainerHighest = Color(0xFF1E3050)
)

/** Latar aurora arah C: warna dasar skema + tiga gumpalan cahaya radial
 *  (cyan, ungu, biru) di sudut-sudut layar, digambar sekali di belakang
 *  konten. Di tema terang cahayanya jauh lebih lembut. */
@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val gelap = scheme.background.luminance() < 0.05f
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.95f
            val blobs = if (gelap) {
                listOf(
                    Triple(Offset(size.width * 0.02f, size.height * 0.04f), Color(0xFF0E4A66), 0.50f),
                    Triple(Offset(size.width * 0.98f, size.height * 0.16f), Color(0xFF3B2A6E), 0.45f),
                    Triple(Offset(size.width * 0.92f, size.height * 0.88f), Color(0xFF10365E), 0.50f),
                    Triple(Offset(size.width * 0.04f, size.height * 0.96f), Color(0xFF2A1B54), 0.40f)
                )
            } else {
                listOf(
                    Triple(Offset(size.width * 0.02f, size.height * 0.04f), Color(0xFF7ED0F7), 0.35f),
                    Triple(Offset(size.width * 0.98f, size.height * 0.16f), Color(0xFFC4B0F5), 0.30f),
                    Triple(Offset(size.width * 0.92f, size.height * 0.88f), Color(0xFF9CC3EE), 0.35f),
                    Triple(Offset(size.width * 0.04f, size.height * 0.96f), Color(0xFFD9CCFA), 0.28f)
                )
            }
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
    } else if (dark) DarkScheme else LightScheme
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
