package id.sysfile.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Layar Beranda SysFile: kartu penyimpanan, kategori, favorit, dan bilah navigasi pil.

fun rootLabel(root: RootInfo, t: Txt): String {
    return when (root.kind) {
        "root" -> t.rootSystem
        "sd" -> t.sdCard
        "usb" -> t.usbStorage
        else -> t.internalStorage
    }
}

// ---------------------------------------------------------------------------
// Layar Beranda
// ---------------------------------------------------------------------------

/** Kartu hero satu volume (BR4, r161): angka besar + persen selalu
 *  TERPAKAI, baris kedua "dari TOTAL \u00b7 bebas FREE" — Internal dan
 *  Root/SD/USB kini tampil sama (sebelumnya Root memakai tersisa, dan
 *  bar + persen mengikuti fraksi sisa). Status kesehatan tetap dari
 *  fraksi bebas. */
@Composable
fun StorageHeroCard(
    root: RootInfo,
    rootActive: Boolean,
    onOpenRoot: (String) -> Unit,
) {
    val t = txt()
    val data = heroCardData(root)
    val freeFraction =
        if (data.totalBytes > 0) data.freeBytes.toFloat() / data.totalBytes.toFloat() else 0f
    val (healthText, healthDot) = storageStatus(freeFraction)
    StorageCard(
        label = rootLabel(root, t),
        usedText = FileOps.formatSize(data.usedBytes),
        detailText = t.ofTotal(FileOps.formatSize(data.totalBytes)) + " \u00b7 " + t.freeOf(FileOps.formatSize(data.freeBytes)),
        percentText = String.format(appLocale(), "%.1f%%", data.usedPercent),
        usedWord = t.usedWord,
        statusText = if (root.kind == "root") {
            if (rootActive) t.rootActive else t.rootLimited
        } else {
            healthText
        },
        statusDot = healthDot,
        icon = when (root.kind) {
            "root" -> Icons.Default.Terminal
            "sd" -> Icons.Default.SdStorage
            "usb" -> Icons.Default.Usb
            else -> Icons.Default.Smartphone
        },
        usedFraction = data.usedFraction,
        onClick = { onOpenRoot(root.path) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    accessGranted: Boolean,
    roots: List<RootInfo>,
    rootActive: Boolean,
    trashEnabled: Boolean,
    onRequestAccess: () -> Unit,
    onOpenRoot: (String) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val t = txt()
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!accessGranted) {
            item {
                SectionCard(icon = Icons.Default.Warning, title = t.permissionTitle) {
                    Text(t.permissionBody, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                        Text(t.permissionButton)
                    }
                    Text(t.permissionRationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (roots.isNotEmpty()) {
            item {
                val heroes = heroRoots(roots)
                val primaryRoot = heroes.firstOrNull() ?: roots.first()
                val secondaryRoots = roots.filter { it.path != primaryRoot.path }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.storage, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    // Opsi 4D (Expressive Split Volumes — kartu diperbesar):
                    // Volume utama (Internal) tampil penuh & besar di atas,
                    // diikuti kartu volume sekunder (Root /, USB OTG, SD)
                    // yang tampil langsung di bawahnya tanpa harus geser.
                    StorageHeroCard(
                        root = primaryRoot,
                        rootActive = rootActive,
                        onOpenRoot = onOpenRoot
                    )
                    if (secondaryRoots.isNotEmpty()) {
                        secondaryRoots.chunked(2).forEach { rowRoots ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowRoots.forEach { secRoot ->
                                    SecondaryStorageSplitCard(
                                        root = secRoot,
                                        rootActive = rootActive,
                                        modifier = Modifier.weight(1f),
                                        onOpenRoot = onOpenRoot
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.categories, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                // Opsi 4D (Kartu Kategori 2-Kolom Diperbesar):
                val cats = buildList {
                    add(Triple(Category.IMAGE, t.catImagesS, Color(0xFF8B5CF6)))
                    add(Triple(Category.VIDEO, t.catVideosS, Color(0xFF06B6D4)))
                    add(Triple(Category.APP, t.catAppsS, Color(0xFF10B981)))
                    add(Triple(Category.DOCUMENT, t.catDocumentsS, Color(0xFFF59E0B)))
                    add(Triple(Category.AUDIO, t.catAudioS, Color(0xFFF43F5E)))
                    add(Triple(Category.ARCHIVE, t.catArchivesS, Color(0xFFA855F7)))
                    add(Triple(Category.OTHER, t.catOtherS, Color(0xFF64748B)))
                }
                val totalTiles = if (trashEnabled) cats.size + 1 else cats.size
                val indices = (0 until totalTiles).chunked(2)
                indices.forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { idx ->
                            if (idx < cats.size) {
                                val (cat, label, color) = cats[idx]
                                CategoryTile(
                                    icon = categoryIcon(cat, ""),
                                    label = label,
                                    color = color,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenCategory(cat) }
                                )
                            } else {
                                val trashColor = Color(0xFFEF5350)
                                CategoryTile(
                                    icon = Icons.Default.Delete,
                                    label = t.trash,
                                    color = trashColor,
                                    modifier = Modifier.weight(1f),
                                    onClick = onOpenTrash
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryStorageSplitCard(
    root: RootInfo,
    rootActive: Boolean,
    modifier: Modifier = Modifier,
    onOpenRoot: (String) -> Unit
) {
    val t = txt()
    val scheme = MaterialTheme.colorScheme
    val data = heroCardData(root)
    val freeFraction =
        if (data.totalBytes > 0) data.freeBytes.toFloat() / data.totalBytes.toFloat() else 0f
    val (healthText, healthDot) = storageStatus(freeFraction)
    val accentColor = when (root.kind) {
        "root" -> if (rootActive) Color(0xFF10B981) else Color(0xFFF59E0B)
        "usb" -> Color(0xFF06B6D4)
        "sd" -> Color(0xFF38BDF8)
        else -> scheme.primary
    }
    val statusLabel = if (root.kind == "root") {
        if (rootActive) t.rootActive else t.rootLimited
    } else {
        healthText
    }
    val badgeColor = if (root.kind == "root") accentColor else healthDot
    val icon = when (root.kind) {
        "root" -> Icons.Default.Terminal
        "sd" -> Icons.Default.SdStorage
        "usb" -> Icons.Default.Usb
        else -> Icons.Default.Smartphone
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.secondaryContainer)
            .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(24.dp))
            .clickable { onOpenRoot(root.path) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = rootLabel(root, t),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    maxLines = 1
                )
            }
        }
        if (data.totalBytes > 0) {
            Text(
                text = FileOps.formatSize(data.usedBytes) + " / " + FileOps.formatSize(data.totalBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(data.usedFraction.coerceIn(0.04f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            Text(
                text = String.format(appLocale(), "%.1f%%", data.usedPercent) + " \u00b7 " + t.freeOf(FileOps.formatSize(data.freeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = root.path,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Layar peramban folder
// ---------------------------------------------------------------------------

/** Nav bawah NV23 "Lebar Biru" (r135, 2026-11-20): bentuk bar video
 *  Auriya — bar lebar 296dp (margin samping 48dp, ~75% layar 393dp)
 *  setinggi 60dp + pill primary #9ACBFA 44dp mengikuti konten
 *  (ikon 24dp + celah 8dp + label titleMedium + padding 11dp tiap
 *  sisi), ikon + label sebaris HANYA di tab aktif (gelap #003352
 *  di atas pill); tab tidak aktif hanya ikon outline putih. Bar
 *  #1C2024 (surfaceContainer — nyaris sama dengan #212121 di video).
 *  Pill dijepit (coerceIn) supaya tidak keluar dari bar saat tab
 *  Analisis aktif (pill ~126dp > slot ~99dp). Animasi pill NP3
 *  Pudar (r136, diperbaiki r138): pill lama memudar di tempatnya,
 *  pill baru muncul di tempatnya (~250 ms) — tidak ada geser.
 *  r138: TANPA AnimatedContent — r136 membuat pill melebar
 *  selebar bar (308dp di screenshot, harusnya ~109dp).
 *  Menggantikan HS1 rapat (r94).
 *  r164: bar/pill/on-pill kini dari peran skema (surfaceContainer /
 *  diganti di bawah). */
/**
 * Bilah navigasi bawah gaya Opsi 3 (Split Power Dock tanpa tombol su/img):
 * dock kapsul rounded-2xl dengan border halus dan tile ikon+label bertumpuk.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileNavBar(homeTab: Int, onSelect: (Int) -> Unit) {
    val t = txt()
    val motion = MaterialTheme.motionScheme
    val items = listOf(
        Triple(0, Icons.Default.Home, t.home),
        Triple(1, Icons.Default.History, t.recentShort),
        Triple(2, Icons.Default.Analytics, t.analysisShort)
    )
    val outlined = listOf(
        Icons.Outlined.Home, Icons.Outlined.History, Icons.Outlined.Analytics
    )
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val activeFg = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inactiveFg = if (isDark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(barColor)
            .border(1.dp, borderColor, RoundedCornerShape(26.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (index, icon, label) ->
            val selected = homeTab == index
            val bg by animateColorAsState(
                targetValue = if (selected) activeBg else Color.Transparent,
                animationSpec = motion.defaultEffectsSpec(),
                label = "navTileBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) activeFg else inactiveFg,
                animationSpec = motion.defaultEffectsSpec(),
                label = "navTileFg"
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = if (selected) icon else outlined[index],
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

