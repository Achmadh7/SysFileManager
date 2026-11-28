package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val isNothing = LocalNothingTheme.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = if (isNothing) RoundedCornerShape(50) else RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isNothing) {
                    val bg = if (isDark) Color(0xFF131317) else Color(0xFFEAEAE5)
                    Brush.linearGradient(listOf(bg, bg))
                } else {
                    val bgTop = if (isDark) Color(0xFF162238) else Color(0xFF1E3A8A)
                    val bgBot = if (isDark) Color(0xFF0F172A) else Color(0xFF1D4ED8)
                    Brush.linearGradient(listOf(bgTop, bgBot))
                }
            )
            .themeCardBorder(shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = size.minDimension / 64f
            if (isNothing) {
                // Split-Pocket Nothing Monochrome + Red Dot (Opsi 1 Tema 2)
                val cardColor = if (isDark) Color(0xFFF5F5F7) else Color.White
                val pocketColor = if (isDark) Color(0xFF23232A) else Color(0xFF18181D)
                val pocketBorder = if (isDark) Color(0xFF454552) else Color.Transparent

                drawRoundRect(
                    color = cardColor,
                    topLeft = Offset(18f * u, 18f * u),
                    size = Size(28f * u, 13f * u),
                    cornerRadius = CornerRadius(4f * u, 4f * u)
                )
                if (!isDark) {
                    drawRoundRect(
                        color = Color(0xFFD2D2CA),
                        topLeft = Offset(18f * u, 18f * u),
                        size = Size(28f * u, 13f * u),
                        cornerRadius = CornerRadius(4f * u, 4f * u),
                        style = Stroke(width = 1.2f * u)
                    )
                }
                drawRoundRect(
                    color = pocketColor,
                    topLeft = Offset(15f * u, 26f * u),
                    size = Size(34f * u, 21f * u),
                    cornerRadius = CornerRadius(6f * u, 6f * u)
                )
                if (isDark) {
                    drawRoundRect(
                        color = pocketBorder,
                        topLeft = Offset(15f * u, 26f * u),
                        size = Size(34f * u, 21f * u),
                        cornerRadius = CornerRadius(6f * u, 6f * u),
                        style = Stroke(width = 1.4f * u)
                    )
                }
                drawRoundRect(
                    color = NothingSignalRed,
                    topLeft = Offset(26f * u, 34.5f * u),
                    size = Size(12f * u, 3.2f * u),
                    cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
                )
            } else {
                // Split-Pocket Nordic Slate & Cobalt (Opsi 1 Tema 1)
                drawRoundRect(
                    color = Color(0xFF93C5FD),
                    topLeft = Offset(18f * u, 18f * u),
                    size = Size(28f * u, 13f * u),
                    cornerRadius = CornerRadius(4f * u, 4f * u)
                )
                drawRoundRect(
                    color = Color(0xFF3B82F6),
                    topLeft = Offset(15f * u, 26f * u),
                    size = Size(34f * u, 21f * u),
                    cornerRadius = CornerRadius(6f * u, 6f * u)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(26f * u, 34.5f * u),
                    size = Size(12f * u, 3.2f * u),
                    cornerRadius = CornerRadius(1.6f * u, 1.6f * u)
                )
            }
        }
    }
}

/**
 * Layar sambutan (Style 1 — Terminal Spec Sheet):
 * - Header ringkas logo Split-Pocket + versi + toggle tema di pojok kanan.
 * - Daftar kapabilitas teknis 3 baris dengan kotak ikon 12dp selaras Beranda Opsi A.
 * - Hirarki CTA tunggal di zona jempol: Beri Akses saat belum aktif (plus opsi Lewati),
 *   dan Mulai Jelajahi saat izin sudah diberikan.
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
    val isNothing = LocalNothingTheme.current
    val isDark = scheme.background.luminance() < 0.5f
    val isId = t.locale.language == "id"
    val statusColor = when {
        accessGranted -> if (isDark) Color(0xFF34D399) else Color(0xFF047857)
        isNothing -> NothingSignalRed
        else -> if (isDark) Color(0xFFF59E0B) else Color(0xFFB45309)
    }
    val ctaShape = if (isNothing) CircleShape else RoundedCornerShape(14.dp)

    Box(
        Modifier
            .fillMaxSize()
            .then(if (isNothing || LocalLiquidGlass.current.enabled) Modifier else Modifier.background(scheme.background))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        SysFileLogo(
                            modifier = Modifier.size(46.dp),
                            cornerRadius = 14.dp
                        )
                        Column {
                            Text(
                                text = "SysFile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onBackground
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} \u00b7 ${t.appTagline}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = { onTheme(if (themeMode == "dark") "light" else "dark") },
                        shape = CircleShape,
                        color = scheme.secondaryContainer,
                        border = BorderStroke(1.dp, scheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = t.theme,
                                tint = if (isNothing) scheme.onSurface else scheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = t.theme,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isNothing) t.welcomeTitle.uppercase() else t.welcomeTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onBackground
                    )
                    Text(
                        text = t.welcomePitch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 21.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WelcomeSpecRow(
                        icon = Icons.Default.Storage,
                        title = if (isId) "Penyimpanan, USB OTG & Arsip" else "Storage, USB OTG & Archives",
                        subtitle = "ZIP, 7Z, RAR, TAR & SAF",
                        accentColor = scheme.primary
                    )
                    WelcomeSpecRow(
                        icon = Icons.Default.Terminal,
                        title = if (isId) "Root & Inspektor Biner" else "Root & Binary Inspector",
                        subtitle = "Ext4/FAT/LP, APK/DEX/ARSC, .prof & SQLite",
                        accentColor = scheme.primary
                    )
                    WelcomeSpecRow(
                        icon = if (accessGranted) Icons.Default.CheckCircle else Icons.Default.Folder,
                        title = t.welcomeAccessTitle,
                        subtitle = t.welcomeAccessDesc,
                        accentColor = statusColor,
                        statusText = if (accessGranted) t.welcomeGranted else t.welcomeNotGranted,
                        highlightRedIcon = isNothing && !accessGranted,
                        borderOverride = statusColor.copy(alpha = 0.55f),
                        onClick = if (accessGranted) null else onRequestAccess
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (accessGranted) {
                    Button(
                        onClick = onFinish,
                        shape = ctaShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = t.welcomeStart,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onRequestAccess,
                        shape = ctaShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = t.welcomeGrant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = onFinish,
                        shape = ctaShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "${t.later} \u00b7 ${t.welcomeStart}",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeSpecRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    statusText: String? = null,
    highlightRedIcon: Boolean = false,
    borderOverride: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val isNothing = LocalNothingTheme.current
    val glass = LocalLiquidGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(if (isNothing) 18.dp else 14.dp)
    val icShape = if (isNothing) CircleShape else RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && glass.enabled) Modifier.liquidGlassPressScale(interactionSource)
                else Modifier
            )
            .clip(rowShape)
            .background(glassCardColor(scheme.secondaryContainer))
            .then(
                if (borderOverride != null) Modifier.border(1.dp, borderOverride, rowShape)
                else Modifier.themeCardBorder(rowShape)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(icShape)
                .background(if (isNothing) scheme.surfaceVariant else accentColor.copy(alpha = 0.16f))
                .nothingBorder(icShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    highlightRedIcon -> NothingSignalRed
                    isNothing -> scheme.onSurface
                    else -> accentColor
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (statusText != null) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}
