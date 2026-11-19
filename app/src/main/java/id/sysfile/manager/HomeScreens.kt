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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    accessGranted: Boolean,
    roots: List<RootInfo>,
    rootActive: Boolean,
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.storage, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    // r53 (2026-11-18, D1 Dua kartu hero — pilihannya):
                    // Root/SD/USB dipisah jadi kartu hero sendiri, sama
                    // seperti Internal (angka besar = sisa).
                    roots.forEach { root ->
                        if (root.kind != "internal" && root.total > 0) {
                            // D1: kartu hero seperti Internal — angka besar
                            // = sisa (tersisa); bar + persen mengikuti
                            // fraksi sisa agar konsisten.
                            val (healthText, healthDot) = storageStatus(root.free.toFloat() / root.total.toFloat())
                            StorageCard(
                                label = rootLabel(root, t),
                                usedText = FileOps.formatSize(root.free),
                                detailText = t.ofTotal(FileOps.formatSize(root.total)),
                                percentText = String.format(appLocale(), "%.1f%%", root.free * 100.0 / root.total),
                                usedWord = t.freeWord,
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
                                    else -> Icons.Default.Storage
                                },
                                usedFraction = (root.free.toFloat() / root.total.toFloat()).coerceIn(0f, 1f),
                                onClick = { onOpenRoot(root.path) }
                            )
                        } else if (root.total > 0) {
                            val (statusText, statusDot) = storageStatus(root.free.toFloat() / root.total.toFloat())
                            StorageCard(
                                label = rootLabel(root, t),
                                usedText = FileOps.formatSize(root.total - root.free),
                                detailText = t.ofTotal(FileOps.formatSize(root.total)),
                                percentText = String.format(appLocale(), "%.1f%%", (root.total - root.free) * 100.0 / root.total),
                                usedWord = t.usedWord,
                                statusText = statusText,
                                statusDot = statusDot,
                                icon = Icons.Default.Smartphone,
                                usedFraction = ((root.total - root.free).toFloat() / root.total.toFloat()).coerceIn(0f, 1f),
                                onClick = { onOpenRoot(root.path) }
                            )
                        } else {
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
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.categories, style = MaterialTheme.typography.titleMedium)
                }
                val cats = listOf(
                    Category.IMAGE to t.catImagesS,
                    Category.VIDEO to t.catVideosS,
                    Category.AUDIO to t.catAudioS,
                    Category.DOCUMENT to t.catDocumentsS,
                    Category.APP to t.catAppsS,
                    Category.ARCHIVE to t.catArchivesS,
                    Category.OTHER to t.catOtherS
                )
                cats.chunked(3).forEach { rowCats ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCats.forEach { (cat, label) ->
                            val color = MaterialTheme.colorScheme.primary
                            Column(
                                Modifier.weight(1f).height(84.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(color.copy(alpha = 0.16f))
                                    .clickable { onOpenCategory(cat) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(categoryIcon(cat, ""), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowCats.size == 1) {
                            val trashColor = Color(0xFFEF5350)
                            Column(
                                Modifier.weight(1f).height(84.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(trashColor.copy(alpha = 0.16f))
                                    .clickable { onOpenTrash() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(trashColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    t.trash,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar peramban folder
// ---------------------------------------------------------------------------

/** Nav bawah senada (r94 HS1, 2026-11-19): kapsul surfaceContainer
 *  #1C2024 48dp TANPA garis tepi (efek kaca di r93 dihapus) + pill
 *  primary #9ACBFA 35dp mengikuti konten (ikon 24dp + celah 8dp +
 *  label titleMedium + padding 11dp tiap sisi), ikon + label
 *  sebaris HANYA di tab aktif (gelap #003352 di atas pill); tab
 *  tidak aktif hanya ikon outline putih. Warna dari tema Senja
 *  supaya senada dengan elemen lain. Animasi halus (r93): pill
 *  meluncur + berubah lebar, warna ikon/label ikut dianimasikan
 *  dengan defaultSpatialSpec yang sama. Menggantikan kapsul navy
 *  + pill putih (r93 WP1). */
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
    // r94 HS1: senada — kapsul surfaceContainer #1C2024 48dp TANPA
    // garis tepi (efek kaca di r93 dihapus), pill primary #9ACBFA
    // 35dp (ikon 24dp + celah 8dp + label + padding 11dp tiap
    // sisi), ikon + label titleMedium sebaris HANYA di tab aktif
    // (gelap #003352 di atas pill); tab tidak aktif hanya ikon
    // outline putih. Warna dari tema Senja (bukan warna foto)
    // supaya senada dengan elemen lain di kedua mode. ANIMASI
    // HALUS (r93): lebar + posisi pill (pillW / pillX) dan warna
    // ikon/label (fg) semuanya memakai defaultSpatialSpec yang
    // sama — jadi warna berubah seiring pill meluncur.
    val barColor = Color(0xFF1C2024)
    val pillColor = Color(0xFF9ACBFA)
    val onPillColor = Color(0xFF003352)
    Row(
        Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(barColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3
            val pillWTarget = 24.dp + 8.dp + labelWidths[homeTab] + 22.dp
            val pillW by animateDpAsState(
                targetValue = pillWTarget,
                animationSpec = motion.defaultSpatialSpec(),
                label = "pillW"
            )
            val pillX by animateDpAsState(
                targetValue = tabWidth * homeTab + (tabWidth - pillW) / 2,
                animationSpec = motion.defaultSpatialSpec(),
                label = "pillX"
            )
            Box(
                Modifier.offset(x = pillX, y = 6.5.dp)
                    .size(width = pillW, height = 35.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { (index, icon, label) ->
                    val selected = homeTab == index
                    val fg by animateColorAsState(
                        targetValue = if (selected) onPillColor
                        else Color.White,
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

