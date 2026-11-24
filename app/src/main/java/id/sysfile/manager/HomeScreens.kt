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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlin.math.sin
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
                val pagerState = rememberPagerState(pageCount = { roots.size })
                val scope = rememberCoroutineScope()
                val currentRoot = roots[pagerState.currentPage.coerceIn(0, roots.lastIndex)]
                val currentAccent = storageVolumeAccent(currentRoot.kind, rootActive, MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Storage,
                                null,
                                tint = if (LocalNothingTheme.current) NothingSignalRed else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (LocalNothingTheme.current) t.storage.uppercase() else t.storage,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (roots.size > 1) {
                            val isNothing = LocalNothingTheme.current
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isNothing) MaterialTheme.colorScheme.surfaceContainer
                                        else currentAccent.copy(alpha = 0.15f)
                                    )
                                    .nothingBorder(RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${roots.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isNothing) MaterialTheme.colorScheme.onSurface else currentAccent
                                )
                            }
                        }
                    }

                    // Opsi 1 — Peek Hero Carousel (HorizontalPager + Neighbor Edge Peek + Dynamic Pill Indicator):
                    // Semua penyimpanan (Internal, Root /, USB OTG, SD Card) dalam 1 slot hemat ruang
                    // dengan kartu berikutnya mengintip di sisi kanan sebagai petunjuk swipe.
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = if (roots.size > 1) PaddingValues(end = 38.dp) else PaddingValues(0.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val pageRoot = roots[page]
                        val pageOffset = abs(
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).coerceIn(0f, 1f)
                        val scale = lerp(0.95f, 1f, 1f - pageOffset)
                        val alpha = lerp(0.58f, 1f, 1f - pageOffset)
                        PeekStorageHeroCard(
                            root = pageRoot,
                            rootActive = rootActive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                            onOpenRoot = onOpenRoot
                        )
                    }

                    if (roots.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            roots.forEachIndexed { idx, r ->
                                val selected = idx == pagerState.currentPage
                                val dotAccent = storageVolumeAccent(r.kind, rootActive, MaterialTheme.colorScheme.primary)
                                val dotWidth by animateDpAsState(
                                    targetValue = if (selected) 24.dp else 7.dp,
                                    label = "storagePagerDotWidth"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = when {
                                        LocalNothingTheme.current && selected -> NothingSignalRed
                                        LocalNothingTheme.current -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                                        selected -> dotAccent
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    },
                                    label = "storagePagerDotColor"
                                )
                                Box(
                                    Modifier
                                        .padding(horizontal = 3.5.dp)
                                        .height(7.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .clickable {
                                            scope.launch {
                                                pagerState.animateScrollToPage(idx)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Analytics,
                        null,
                        tint = if (LocalNothingTheme.current) NothingSignalRed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (LocalNothingTheme.current) t.categories.uppercase() else t.categories,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                                    highlightRed = cat == Category.APP,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenCategory(cat) }
                                )
                            } else {
                                val trashColor = Color(0xFFEF5350)
                                CategoryTile(
                                    icon = Icons.Default.Delete,
                                    label = t.trash,
                                    color = trashColor,
                                    highlightRed = true,
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

private fun storageVolumeAccent(kind: String, rootActive: Boolean, defaultPrimary: Color): Color {
    return when (kind) {
        "root" -> if (rootActive) Color(0xFF10B981) else Color(0xFFF59E0B)
        "usb" -> Color(0xFFA855F7)
        "sd" -> Color(0xFF06B6D4)
        else -> defaultPrimary
    }
}

/**
 * Opsi 1 — Peek Hero Carousel Card:
 * Kartu penyimpanan tunggal untuk HorizontalPager dengan kotak ikon berwarna,
 * nama volume + path/status, badge persentase/status di kanan atas,
 * Split-Gap Solid Progress Bar (selaras Opsi 1A dengan titik stop di ujung kanan track),
 * dan baris ringkasan kapasitas terpakai & total.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PeekStorageHeroCard(
    root: RootInfo,
    rootActive: Boolean,
    modifier: Modifier = Modifier,
    onOpenRoot: (String) -> Unit
) {
    val t = txt()
    val scheme = MaterialTheme.colorScheme
    val data = heroCardData(root)
    val accentColor = storageVolumeAccent(root.kind, rootActive, scheme.primary)
    val icon = when (root.kind) {
        "root" -> Icons.Default.Terminal
        "sd" -> Icons.Default.SdStorage
        "usb" -> Icons.Default.Usb
        else -> Icons.Default.Smartphone
    }
    val subtitleText = when (root.kind) {
        "root" -> if (rootActive) t.rootActive else t.rootLimited
        else -> root.path
    }
    val badgeText = when {
        root.kind == "root" -> if (rootActive) t.rootActive else t.rootLimited
        data.totalBytes > 0 -> String.format(appLocale(), "%.0f%%", data.usedPercent)
        else -> root.kind.uppercase()
    }
    val clampedFraction = when {
        data.totalBytes > 0 -> data.usedFraction.coerceIn(0.06f, 1f)
        root.kind == "root" -> if (rootActive) 0.68f else 0.25f
        else -> 0.08f
    }
    val remainFraction = (1f - clampedFraction).coerceAtLeast(0f)

    val isNothing = LocalNothingTheme.current
    val peekShape = RoundedCornerShape(if (isNothing) 22.dp else 26.dp)
    val iconShape = if (isNothing) CircleShape else RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(peekShape)
            .background(scheme.secondaryContainer)
            .then(
                if (isNothing) Modifier.border(1.dp, scheme.outlineVariant, peekShape)
                else Modifier
            )
            .clickable { onOpenRoot(root.path) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Baris atas: kotak ikon + judul/subjudul volume + badge persentase/status
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(iconShape)
                        .background(if (isNothing) scheme.surfaceVariant else accentColor.copy(alpha = 0.16f))
                        .nothingBorder(iconShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            isNothing && root.kind == "root" -> NothingSignalRed
                            isNothing -> scheme.onSurface
                            else -> accentColor
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rootLabel(root, t),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isNothing) NothingSignalRed else accentColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isNothing) badgeText.uppercase() else badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isNothing) Color.White else accentColor,
                    maxLines = 1
                )
            }
        }

        // Baris tengah: Nothing Matrix Segmented Bar (tema 2) / M3 Expressive Wavy (tema 1, Opsi B)
        if (isNothing) {
            NothingMatrixProgressBar(fraction = clampedFraction)
        } else {
            LinearWavyProgressIndicator(
                progress = { clampedFraction },
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = scheme.surfaceContainerHighest
            )
        }

        // Baris bawah: info kapasitas terpakai & total/bebas
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (data.totalBytes > 0) {
                Text(
                    text = "${t.usedWord.replaceFirstChar { it.uppercase() }} ${FileOps.formatSize(data.usedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FileOps.formatSize(data.freeBytes)} · ${t.ofTotal(FileOps.formatSize(data.totalBytes))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = root.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (root.kind == "root" && rootActive) "R/W →" else "→",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(
    icon: ImageVector,
    label: String,
    color: Color,
    highlightRed: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isNothing = LocalNothingTheme.current
    val tileShape = RoundedCornerShape(if (isNothing) 18.dp else 22.dp)
    val icShape = if (isNothing) CircleShape else RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(74.dp)
            .clip(tileShape)
            .background(
                if (isNothing) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .then(
                if (isNothing) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, tileShape)
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(icShape)
                .background(
                    if (isNothing) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = when {
                    isNothing && highlightRed -> NothingSignalRed
                    isNothing -> MaterialTheme.colorScheme.onSurface
                    else -> color
                },
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
    val isNothing = LocalNothingTheme.current
    val barColor = if (isNothing) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val activeBg = if (isNothing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val activeBorder = MaterialTheme.colorScheme.primary
    val activeFg = if (isNothing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val inactiveFg = if (isDark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant

    val safeTab = homeTab.coerceIn(0, 2)
    val pillProgress by animateFloatAsState(
        targetValue = safeTab.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 380f
        ),
        label = "navPillSlide"
    )
    val flightArc = sin((pillProgress - pillProgress.toInt()).coerceIn(0f, 1f) * Math.PI.toFloat())
    val pulseAnim = remember { Animatable(1f) }
    val firstRender = remember { mutableStateOf(true) }
    LaunchedEffect(safeTab) {
        if (firstRender.value) {
            firstRender.value = false
        } else {
            pulseAnim.snapTo(0f)
            pulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520)
            )
        }
    }

    val navShape = if (isNothing) RoundedCornerShape(50) else RoundedCornerShape(26.dp)
    val pillShape = if (isNothing) RoundedCornerShape(50) else RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .clip(navShape)
            .background(barColor)
            .then(if (isNothing) Modifier.border(1.dp, borderColor, navShape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val gap = 8.dp
        val slotWidth = (maxWidth - gap * 2) / 3f
        val stepWidth = slotWidth + gap
        val pillX = stepWidth * pillProgress
        val targetX = stepWidth * safeTab.toFloat()
        val pillLift = (-3).dp * flightArc
        val pillScaleX = 1f + 0.04f * flightArc
        val pillScaleY = 1f + 0.04f * flightArc

        // Cincin pulsa tonal Material 3 (Opsi 3: Flat M3 Tonal Ripple Ring saat mendarat)
        val p = pulseAnim.value
        if (!isNothing && p < 0.99f) {
            Box(
                Modifier
                    .offset(x = targetX)
                    .width(slotWidth)
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = 0.94f + 0.24f * p
                        scaleY = 0.94f + 0.32f * p
                        alpha = (1f - p) * 0.75f
                    }
                    .border(1.5.dp, activeFg, pillShape)
            )
        }

        // Kapsul tab aktif tonal M3.
        Box(
            Modifier
                .offset(x = pillX, y = pillLift)
                .width(slotWidth)
                .height(52.dp)
                .graphicsLayer {
                    scaleX = pillScaleX
                    scaleY = pillScaleY
                }
                .clip(pillShape)
                .background(activeBg)
                .then(if (isNothing) Modifier.border(1.dp, activeBorder, pillShape) else Modifier)
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (index, icon, label) ->
                val selected = safeTab == index
                val fg by animateColorAsState(
                    targetValue = if (selected) activeFg else inactiveFg,
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "navTileFg"
                )
                val tabLift by animateDpAsState(
                    targetValue = if (selected) (-2).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navTabLift"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(index) }
                        .offset(y = tabLift),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = if (selected) icon else outlined[index],
                        contentDescription = label,
                        tint = fg,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isNothing) label.uppercase() else label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

