package id.sysfile.manager

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

// Skema warna resmi Material 3 Expressive Google (r21, pilihan Achmad
// 2026-11-18): skema statis = lightColorScheme()/darkColorScheme() bawaan
// library material3 1.5.0-alpha08 — tanpa lagi navy/ungu tulisan tangan.
// Peran warna yang dipakai app (primary, secondaryContainer, dst.) semua
// tersedia di skema bawaan.

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
    } else if (dark) darkColorScheme() else lightColorScheme()
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
