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
import androidx.compose.material3.HorizontalDivider
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
    recentFiles: List<FEntry> = emptyList(),
    onRequestAccess: () -> Unit,
    onOpenRoot: (String) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenEntry: ((FEntry) -> Unit)? = null,
    onSeeAllRecents: (() -> Unit)? = null,
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
                // BR4 (r161): remember tanpa syarat di kepala blok item
                // agar slot remember stabil saat roots berubah
                // (kosong -> terisi).
                val heroes = heroRoots(roots)
                val pagerState = rememberPagerState(pageCount = { heroes.size })
                val scope = rememberCoroutineScope()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.storage, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    // BR4 Hero Geser (r161, pilihannya): kartu hero
                    // Internal/Root/SD/USB (D1, r53) jadi satu pager yang
                    // bisa digeser — Internal dulu, geser untuk Root (dan
                    // USB/SD kalau ada). Titik di bawah: aktif = aksen,
                    // ketuk titik = geser ke halaman itu.
                    if (heroes.isNotEmpty()) {
                        // r171 (HC6 Naik Pudar, pilihannya): kartu hero naik
                        // + memudar saat berpindah — translateY + alfa dari
                        // offset pager (graphicsLayer per halaman).
                        val risePx = with(LocalDensity.current) { 24.dp.toPx() }
                        // r169: pageSpacing 16dp - kartu yang
                        // bersebelahan tidak menempel saat digeser.
                        // Settled page tetap penuh (PageSize.Fill
                        // mengabaikan pageSpacing).
                        HorizontalPager(
                            state = pagerState,
                            pageSpacing = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            // r171: offset halaman -> naik + pudar (HC6).
                            val pageOffset = pagerState.getOffsetDistanceInPages(page)
                            val heroAlpha = lerp(1f, 0.5f, minOf(1f, abs(pageOffset)))
                            Box(
                                Modifier.graphicsLayer {
                                    translationY = pageOffset * risePx
                                    alpha = heroAlpha
                                }
                            ) {
                                StorageHeroCard(
                                    root = heroes[page],
                                    rootActive = rootActive,
                                    onOpenRoot = onOpenRoot
                                )
                            }
                        }
                        if (heroes.size > 1) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                heroes.forEachIndexed { i, _ ->
                                    Box(
                                        Modifier.padding(4.dp).clickable {
                                            scope.launch { pagerState.animateScrollToPage(i) }
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier.size(8.dp).clip(CircleShape)
                                                .background(
                                                    if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Volume tanpa total diketahui tetap tampil sebagai
                    // baris kecil seperti sebelumnya.
                    roots.filter { it.total <= 0 }.forEach { root ->
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(rootLabel(root, t), style = MaterialTheme.typography.titleSmall)
                                    Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.categories, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                // Redesain Opsi 1 (Expressive Hub - 4 Kolom Kompak):
                // 8 ubin (7 kategori + Tempat Sampah) disusun dalam 4 kolom x 2 baris
                // dengan warna semantik khas per kategori untuk pengenalan visual cepat.
                val cats = listOf(
                    Triple(Category.IMAGE, t.catImagesS, Color(0xFF8B5CF6)), // Violet
                    Triple(Category.VIDEO, t.catVideosS, Color(0xFFF43F5E)), // Rose/Coral
                    Triple(Category.AUDIO, t.catAudioS, Color(0xFF14B8A6)), // Teal
                    Triple(Category.DOCUMENT, t.catDocumentsS, Color(0xFF3B82F6)), // Blue
                    Triple(Category.APP, t.catAppsS, Color(0xFF10B981)), // Emerald
                    Triple(Category.ARCHIVE, t.catArchivesS, Color(0xFFF59E0B)), // Amber
                    Triple(Category.OTHER, t.catOtherS, Color(0xFF64748B)) // Slate
                )
                
                // Baris 1: Gambar, Video, Audio, Dokumen
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cats.take(4).forEach { (cat, label, color) ->
                        CategoryTile(
                            icon = categoryIcon(cat, ""),
                            label = label,
                            color = color,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCategory(cat) }
                        )
                    }
                }

                // Baris 2: APK, Arsip, Lainnya, dan Tempat Sampah
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cats.drop(4).forEach { (cat, label, color) ->
                        CategoryTile(
                            icon = categoryIcon(cat, ""),
                            label = label,
                            color = color,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCategory(cat) }
                        )
                    }
                    if (trashEnabled) {
                        val trashColor = Color(0xFFEF5350)
                        CategoryTile(
                            icon = Icons.Default.Delete,
                            label = t.trash,
                            color = trashColor,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenTrash
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // Seksi File Terbaru (Quick Recents - Hasil efisiensi ruang kategori)
        if (recentFiles.isNotEmpty() && onOpenEntry != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (t.recentShort.isNotBlank()) t.recentShort else "Baru Ditambahkan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (onSeeAllRecents != null) {
                            Text(
                                "Lihat Semua ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSeeAllRecents() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        val topRecents = recentFiles.take(3)
                        topRecents.forEachIndexed { idx, entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntry(entry) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FileThumbTile(entry, 36)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val dateStr = shortDate(entry.modified, t)
                                    val sizeStr = FileOps.formatSize(entry.size)
                                    Text(
                                        "$sizeStr \u00b7 $dateStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (idx < topRecents.size - 1) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
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
    Column(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
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
 *  primary / onPrimary) — sama persis di mode gelap, mengikuti tema
 *  di mode terang; ikon tak aktif putih di gelap, onSurfaceVariant
 *  di terang (sebelumnya keempatnya literal gelap). */
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
    // r81 NV10: tab tidak aktif memakai ikon outline (filled hanya
    // saat aktif). Urutan sejajar `items` di atas.
    val outlined = listOf(
        Icons.Outlined.Home, Icons.Outlined.History, Icons.Outlined.Analytics
    )
    // r89 NV17: lebar pill mengikuti konten (ikon + label tab aktif).
    // Lebar label diukur saat komposisi dengan TextMeasurer
    // (titleMedium, 16sp — sesuai foto); rememberTextMeasurer di
    // androidx.compose.ui.text (ui-text 1.9.3, terverifikasi r89).
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelWidths = items.map { (_, _, label) ->
        with(density) {
            textMeasurer.measure(
                label,
                style = MaterialTheme.typography.titleMedium
            ).size.width.toDp()
        }
    }
    // r135 NV23: bar lebar video (margin 48dp, tinggi 60dp), pill
    // primary #9ACBFA 44dp. Posisi pill dijepit ke dalam bar
    // (coerceIn) — pill Analisis ~126dp lebih lebar dari slot
    // ~99dp, tanpa jepitan ia keluar dari bar.
    // r141 NP2 Geser Utuh: pill BERGESER + berubah lebar ke tab
    // baru (animateDpAsState, defaultSpatialSpec) — tanpa pudar.
    // NP3 Pudar (r136, r138) diganti atas permintaannya
    // 'implement geser utuh' (2026-11-20, setelah papan Auriya).
    // Warna ikon/label (fg) tetap memakai defaultSpatialSpec;
    // label tab baru muncul saat pill tiba (selected = homeTab
    // langsung — selama geser, label tujuan gelap-di-atas-bar
    // gelap sampai pill menutupinya).
    // r164: warna bar/pill dari peran skema, bukan literal gelap —
    // bar #1C2024 = surfaceContainer, pill #9ACBFA = primary,
    // on-pill #003352 = onPrimary; ketiganya sama persis di mode
    // gelap dan mengikuti tema di mode terang.
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val pillColor = MaterialTheme.colorScheme.primary
    val onPillColor = MaterialTheme.colorScheme.onPrimary
    // Ikon tab tak aktif: tidak ada peran skema yang putih di mode
    // gelap DAN gelap di mode terang — bedakan dari kecerahan latar
    // (gelap #101418 ~0,01; terang #F7F9FF ~0,93).
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inactiveColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(50))
            .background(barColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3
            fun pillWFor(tab: Int) = 24.dp + 8.dp + labelWidths[tab] + 22.dp
            fun pillXFor(tab: Int) =
                (tabWidth * tab + (tabWidth - pillWFor(tab)) / 2)
                    .coerceIn(0.dp, maxWidth - pillWFor(tab))
            val pillX by animateDpAsState(
                targetValue = pillXFor(homeTab),
                animationSpec = motion.defaultSpatialSpec(),
                label = "pillX"
            )
            val pillW by animateDpAsState(
                targetValue = pillWFor(homeTab),
                animationSpec = motion.defaultSpatialSpec(),
                label = "pillW"
            )
            Box(
                Modifier.offset(x = pillX, y = 8.dp)
                    .size(width = pillW, height = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { (index, icon, label) ->
                    val selected = homeTab == index
                    val fg by animateColorAsState(
                        targetValue = if (selected) onPillColor
                        else inactiveColor,
                        animationSpec = motion.defaultSpatialSpec(),
                        label = "tabFg"
                    )
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (selected) icon else outlined[index], null, tint = fg, modifier = Modifier.size(24.dp))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.titleMedium,
                                color = fg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

