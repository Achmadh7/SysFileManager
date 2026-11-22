package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Logo Aurora SF Monogram (Opsi 1): latar gradasi gelap aurora + vektor
 * `ic_launcher_foreground` sehingga konsisten di launcher, welcome, beranda, dan setelan.
 */
@Composable
fun SysFileLogo(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF082F49))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "SysFile Logo",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.28f)
        )
    }
}

/**
 * Layar sambutan (Opsi 4 — Clean Stage + Floating Bottom Sheet):
 * - Panggung atas bersih tanpa pendaran aurora dan tanpa badge chip di bawah judul.
 * - Logo dan tipografi judul/subjudul diperbesar.
 * - Paruh bawah berupa Floating Bottom Sheet 28dp yang merangkum kartu kapabilitas,
 *   kartu izin penyimpanan, dan tombol mulai di zona jempol.
 */
@Composable
fun WelcomeScreen(
    accessGranted: Boolean,
    onRequestAccess: () -> Unit,
    onFinish: () -> Unit,
    themeMode: String,
    onTheme: (String) -> Unit
) {
    val t = txt()
    val scheme = MaterialTheme.colorScheme
    val statusColor = if (accessGranted) Color(0xFF34D399) else Color(0xFFF59E0B)

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(24.dp))

            // Panggung Atas: Logo & Tulisan Diperbesar (Tanpa Aurora Glow & Tanpa Chip Root/DEX/OTG)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SysFileLogo(
                    modifier = Modifier.size(96.dp),
                    cornerRadius = 28.dp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = t.welcomeTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = t.welcomePitch,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // Paruh Bawah: Floating Bottom Sheet 28dp di Zona Jempol
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = scheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 2 Kartu Fitur Berdampingan
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WelcomeSpecTile(
                            icon = Icons.Default.Terminal,
                            title = "Root & Disk",
                            subtitle = "Partisi /, Ext4, Payload",
                            modifier = Modifier.weight(1f)
                        )
                        WelcomeSpecTile(
                            icon = Icons.Default.Inventory2,
                            title = "APK & Viewer",
                            subtitle = "Smali, Office, EPUB",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Kartu Status Izin Penyimpanan
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = scheme.surfaceContainerHighest
                        ),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (accessGranted) Icons.Default.CheckCircle else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (accessGranted) statusColor else scheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = t.welcomeAccessTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (accessGranted) t.welcomeGranted else t.welcomeNotGranted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = statusColor
                                )
                            }
                            if (!accessGranted) {
                                FilledTonalButton(
                                    onClick = onRequestAccess,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = t.welcomeGrant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Tombol Mulai Utama
                    if (accessGranted) {
                        Button(
                            onClick = onFinish,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = t.welcomeStart,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onFinish,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = t.welcomeStart,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // WT1: tombol ganti tema di pojok kanan atas.
        IconButton(
            onClick = { onTheme(if (themeMode == "dark") "light" else "dark") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 12.dp)
        ) {
            Icon(
                if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = t.theme,
                tint = scheme.onSurface
            )
        }
    }
}

@Composable
private fun WelcomeSpecTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
