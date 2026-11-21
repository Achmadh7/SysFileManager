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
 * Layar sambutan (Opsi 3 Thumb-Zone Power Spec + Logo Aurora Opsi 1):
 * hero logo SF bercahaya di atas, grid kapabilitas 2x2, lalu banner izin
 * dan tombol lanjut di area jempol bawah. Muncul sekali sampai welcome_done.
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
    val statusColor = if (accessGranted) Color(0xFF81C995) else Color(0xFFF5C542)

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(28.dp))

                // Hero gaya Opsi 2: logo SF dengan radial glow halus.
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(124.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        SysFileLogo(
                            modifier = Modifier.size(72.dp),
                            cornerRadius = 22.dp
                        )
                    }
                    Text(
                        t.welcomeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        t.welcomePitch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Grid 2x2 Spec Bento (Opsi 3).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WelcomeSpecTile(
                            icon = Icons.Default.Terminal,
                            title = "Root & Shell",
                            subtitle = "Full / & su ops",
                            modifier = Modifier.weight(1f)
                        )
                        WelcomeSpecTile(
                            icon = Icons.Default.Storage,
                            title = "Disk Images",
                            subtitle = "Ext4, FAT, LP, Payload",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WelcomeSpecTile(
                            icon = Icons.Default.Inventory2,
                            title = "APK & DEX",
                            subtitle = "Smali & ARSC inspect",
                            modifier = Modifier.weight(1f)
                        )
                        WelcomeSpecTile(
                            icon = Icons.Default.Description,
                            title = "Built-in Viewers",
                            subtitle = "Office, EPUB, EXIF",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Thumb-Zone Bottom Action Stack (Opsi 3).
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (accessGranted) Icons.Default.CheckCircle else Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (accessGranted) statusColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.welcomeAccessTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (accessGranted) t.welcomeGranted else t.welcomeNotGranted,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                    if (!accessGranted) {
                        FilledTonalButton(onClick = onRequestAccess) {
                            Text(t.welcomeGrant)
                        }
                    }
                }
            }

            if (accessGranted) {
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) { Text(t.welcomeStart) }
            } else {
                OutlinedButton(
                    onClick = onFinish,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) { Text(t.welcomeStart) }
            }
        }

        // WT1: tombol ganti tema di pojok kanan atas.
        IconButton(
            onClick = { onTheme(if (themeMode == "dark") "light" else "dark") },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 8.dp)
        ) {
            Icon(
                if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = t.theme,
                tint = MaterialTheme.colorScheme.onSurface
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
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
