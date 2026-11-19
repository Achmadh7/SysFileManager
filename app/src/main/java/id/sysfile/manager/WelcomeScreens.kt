package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WB4 Layar sambutan (r113; r121: jadi 1 layar; r139 WS12 Hero Lepas):
 * perkenalan tanpa kartu + kartu izin All Files Access di bawahnya,
 * konten bisa di-scroll. Muncul sekali sampai selesai
 * (pref "welcome_done"). Semua teks berbahasa Inggris di kedua mode
 * bahasa (permintaannya). Tulisan kecil rationale dihapus (r139).
 */
@Composable
fun WelcomeScreen(
    accessGranted: Boolean,
    onRequestAccess: () -> Unit,
    onFinish: () -> Unit
) {
    val t = txt()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.size(32.dp))
            // WS12 Hero Lepas (r139, pilihannya): hero TANPA kartu — logo +
            // judul + pitch langsung di latar, terpusat.
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ubin logo M11 "SF" (sama seperti bilah atas Beranda + Tentang).
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SF",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp
                    )
                    Box(
                        Modifier.size(width = 3.dp, height = 88.dp)
                            .rotate(30f)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Text(
                    t.welcomeTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    t.welcomePitch,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            t.welcomeAccessTitle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // Chip status: ikut berubah saat pulang dari Pengaturan.
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(
                                1.dp,
                                if (accessGranted) Color(0xFF81C995) else Color(0xFFF5C542)
                            ),
                            color = Color.Transparent
                        ) {
                            Text(
                                if (accessGranted) t.welcomeGranted else t.welcomeNotGranted,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (accessGranted) Color(0xFF81C995) else Color(0xFFF5C542),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        t.welcomeAccessDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t.welcomeGrant) }
                }
            }
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) { Text(t.welcomeStart) }
    }
}
