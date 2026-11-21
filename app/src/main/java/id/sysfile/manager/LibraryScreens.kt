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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

// Layar Terbaru, Kategori, Pencarian, dan Analisis penyimpanan.

/** Tab File Terbaru: berkas terbaru dikelompokkan per folder induk (varian Grup Folder). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    files: List<FEntry>,
    accessGranted: Boolean,
    onOpenEntry: (FEntry) -> Unit,
    isLiquid: Boolean = false
) {
    // r212: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val t = txt()
    if (!accessGranted) {
        Box(Modifier.fillMaxSize().padding(32.dp).liquidWashBackground(), contentAlignment = Alignment.Center) {
            Text(
                t.permissionBody,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp).liquidWashBackground(), contentAlignment = Alignment.Center) {
            Text(
                t.noRecent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    // T2 (r58): daftar datar terbaru dulu + pembatas tanggal kecil saat
    // hari berganti — tanpa kartu grup per folder.
    val sorted = remember(files) { files.sortedByDescending { it.modified } }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).liquidWashBackground(),
        contentPadding = PaddingValues(bottom = if (isLiquid) 96.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(sorted, key = { _, entry -> entry.path }) { index, entry ->
            val day = recentDay(entry.modified, t)
            val prevDay = sorted.getOrNull(index - 1)?.let { recentDay(it.modified, t) }
            Column {
                if (index == 0 || day != prevDay) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isLiquid) liquid.chipBg else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiquid) liquid.chipFg else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                ListItem(
                    headlineContent = {
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            recentWhen(entry.modified, t),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    },
                    trailingContent = {
                        Text(
                            FileOps.formatSize(entry.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingContent = { FileThumbTile(entry, 48) },
                    colors = ListItemDefaults.colors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
                        .clickable { onOpenEntry(entry) }
                )
            }
        }
    }
}

/** Label waktu relatif untuk baris Terbaru: "Hari ini 14.45", "Kemarin 09.12", atau tanggal penuh. */
private fun recentWhen(modified: Long, t: Txt): String {
    val time = SimpleDateFormat("HH:mm", appLocale()).format(Date(modified))
    val cal = Calendar.getInstance().apply { timeInMillis = modified }
    val now = Calendar.getInstance()
    fun sameDay(c: Calendar) =
        cal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(now) -> t.today + " " + time
        sameDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) ->
            t.yesterday + " " + time
        else -> SimpleDateFormat("dd MMM yyyy", appLocale()).format(Date(modified)) + " " + time
    }
}
/** Label hari saja untuk pembatas Terbaru T2: "Hari ini", "Kemarin", atau "dd MMM yyyy". */
private fun recentDay(modified: Long, t: Txt): String {
    val cal = Calendar.getInstance().apply { timeInMillis = modified }
    val now = Calendar.getInstance()
    fun sameDay(c: Calendar) =
        cal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(now) -> t.today
        sameDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }) -> t.yesterday
        else -> SimpleDateFormat("dd MMM yyyy", appLocale()).format(Date(modified))
    }
}

/** Cache kategori dianggap segar selama ini (r128): buka ulang kategori
 *  dalam jeda ini tidak memindai ulang bila tak ada perubahan berkas. */
private const val CATEGORY_CACHE_FRESH_MS = 60_000L

/** Layar satu kategori: semua berkas kategori itu lintas volume, terbaru dulu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryScreen(
    category: Category,
    roots: List<RootInfo>,
    showHidden: Boolean,
    accessGranted: Boolean,
    categoryCache: MutableMap<Category, List<FEntry>>,
    categoryCacheAt: MutableMap<Category, Long>,
    categoryCacheKey: MutableMap<Category, Int>,
    reloadKey: Int,
    onBack: () -> Unit,
    onOpenEntry: (FEntry, List<FEntry>) -> Unit,
    isLiquid: Boolean = false
) {
    val t = txt()
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val context = LocalContext.current
    val lockedFolders = remember {
        FileOps.canonicalizeLocked(
            context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
                .getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet()
        )
    }
    // r84: daftar dari sesi sebelumnya tampil seketika (bila ada).
    // r128: pindai segar dilewati bila cache masih segar (< 60 dtk)
    // DAN tidak ada perubahan berkas sejak pindai itu (reloadKey) —
    // buka-tutup kategori berulang tidak lagi menelusuri ulang.
    var files by remember { mutableStateOf<List<FEntry>?>(categoryCache[category]) }
    LaunchedEffect(category, accessGranted, reloadKey) {
        if (!accessGranted) {
            files = emptyList()
            return@LaunchedEffect
        }
        val cached = categoryCache[category]
        val at = categoryCacheAt[category] ?: 0L
        val key = categoryCacheKey[category]
        if (cached != null && key == reloadKey &&
            System.currentTimeMillis() - at < CATEGORY_CACHE_FRESH_MS
        ) {
            files = cached
            return@LaunchedEffect
        }
        val fresh = withContext(Dispatchers.IO) {
            loadCategoryFiles(
                roots, category, showHidden, lockedFolders,
                mediaQuery = { cat -> MediaStoreOps.queryMedia(context.contentResolver, cat, lockedFolders) }
            )
        }
        categoryCache[category] = fresh
        categoryCacheAt[category] = System.currentTimeMillis()
        categoryCacheKey[category] = reloadKey
        files = fresh
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = {
                        Text(
                            when (category) {
                                Category.IMAGE -> t.catImages
                                Category.VIDEO -> t.catVideos
                                Category.AUDIO -> t.catAudio
                                Category.DOCUMENT -> t.catDocuments
                                Category.APP -> t.catApps
                                Category.ARCHIVE -> t.catArchives
                                Category.OTHER -> t.catOther
                            }
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            val loaded = files
            val contentState = when {
                loaded == null -> 0
                loaded.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                },
                modifier = Modifier.fillMaxSize(),
                label = "categoryContentFade"
            ) { state ->
                when (state) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLiquid) LiquidSpinner() else LoadingIndicator()
                            Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.categoryEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (isLiquid) {
                            item {
                                Text(
                                    when (category) {
                                        Category.IMAGE -> t.catImages
                                        Category.VIDEO -> t.catVideos
                                        Category.AUDIO -> t.catAudio
                                        Category.DOCUMENT -> t.catDocuments
                                        Category.APP -> t.catApps
                                        Category.ARCHIVE -> t.catArchives
                                        Category.OTHER -> t.catOther
                                    },
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        items(loaded.orEmpty(), key = { it.path }) { entry ->
                            FileRow(
                                entry = entry,
                                selectionMode = false,
                                isSelected = false,
                                itemCount = null,
                                onClick = { onOpenEntry(entry, loaded.orEmpty()) },
                                onLongClick = {},
                                isLiquid = isLiquid
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Anak-anak langsung dari [prefix] di dalam arsip: folder (dengan jumlah isi) dulu, lalu berkas. */
private fun archiveChildren(items: List<ArchiveItem>, prefix: String): List<Pair<ArchiveItem, Int?>> {
    val folderPaths = linkedSetOf<String>()
    val files = mutableListOf<ArchiveItem>()
    for (item in items) {
        if (!item.name.startsWith(prefix)) continue
        val rest = item.name.removePrefix(prefix)
        if (rest.isBlank()) continue
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            folderPaths += prefix + rest.substring(0, slash + 1)
        } else if (item.isDir) {
            folderPaths += if (item.name.endsWith("/")) item.name else item.name + "/"
        } else {
            files += item
        }
    }
    val folders = folderPaths.map { path ->
        val count = items.count { it.name.length > path.length && it.name.startsWith(path) }
        ArchiveItem(path, true, 0L) to count
    }
    return folders.sortedBy { it.first.name.lowercase() } +
        files.sortedBy { it.name.lowercase() }.map { it to null }
}

@Composable
private fun ArchiveItemRow(item: ArchiveItem, count: Int?, extra: String? = null, onExtract: (() -> Unit)? = null, onClick: () -> Unit) {
    val displayName = item.name.removeSuffix("/").substringAfterLast('/')
    val category = if (item.isDir) Category.OTHER else FileOps.categoryOf(displayName)
    val color = entryColor(FEntry(item.name, displayName, item.isDir, item.size, 0L, category))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.isDir) Icons.Default.Folder else categoryIcon(category, displayName),
                null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (item.isDir) count?.let { txt().items(it) } else if (item.size > 0L) FileOps.formatSize(item.size) else ""
            if (!sub.isNullOrEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!extra.isNullOrEmpty()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onExtract != null) {
            IconButton(onClick = onExtract) { Icon(Icons.Default.Download, null) }
        }
    }
}

/**
 * Layar Isi Arsip: mengetuk arsip membuka layar ini (bukan langsung ekstrak).
 * Folder di dalam arsip bisa dimasuki; mengetuk berkas mengekstrak berkas itu
 * saja lalu membukanya; tombol Ekstrak Semua mengekstrak seluruh isi.
 */

/**
 * Pindai segar satu kategori (r128: L1+L2+L3).
 * - Media (Gambar/Video/Audio) tanpa file tersembunyi: MediaStore
 *   (mediaQuery) untuk root biasa; root USB tetap dipindai biasa
 *   karena MediaStore belum tentu mengindeksnya. Bila MediaStore
 *   gagal (null) -> jalan biasa untuk semua root.
 * - File tersembunyi ditampilkan: MediaStore tidak bisa melihatnya,
 *   jadi seluruh kategori dipindai biasa.
 * - Jalan biasa: tiap root dipindai paralel (L2), digabung, terbaru
 *   dulu, maks 1000. [mediaQuery] bisa diisi palsu di tes.
 */
internal suspend fun loadCategoryFiles(
    roots: List<RootInfo>,
    category: Category,
    showHidden: Boolean,
    locked: Set<String>,
    mediaQuery: ((Category) -> List<FEntry>?)? = null
): List<FEntry> = coroutineScope {
    if (MediaStoreOps.isMediaCategory(category) && !showHidden && mediaQuery != null) {
        val media = async(Dispatchers.IO) { mediaQuery(category) }.await()
        // r146: MediaStore KOSONG bukan berarti tidak ada berkas —
        // ia tidak mengindeks folder .nomedia / tersembunyi (mis.
        // audio WhatsApp) dan belum tentu sudah memindai berkas
        // baru; hasil kosong harus jatuh ke penelusuran biasa.
        if (media != null && media.isNotEmpty()) {
            val usb = roots.filter { it.kind == "usb" }.map { root ->
                async(Dispatchers.IO) {
                    FileOps.categoryFiles(listOf(File(root.path)), category, showHidden, locked)
                }
            }.awaitAll().flatten()
            return@coroutineScope (media + usb).sortedByDescending { it.modified }.take(1000)
        }
    }
    val perRoot = roots.map { root ->
        async(Dispatchers.IO) {
            FileOps.categoryFiles(listOf(File(root.path)), category, showHidden, locked)
        }
    }.awaitAll()
    perRoot.flatten().sortedByDescending { it.modified }.take(1000)
}

/**
 * Layar Tempat Sampah: berkas yang dihapus (dipindah ke .SysFileTrash) bisa
 * dipulihkan ke jalur asalnya atau dihapus permanen; ada juga Kosongkan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    notify: (String) -> Unit,
    lockedPaths: Set<String> = emptySet(),
    unlockedPaths: Set<String> = emptySet(),
    onRequestUnlock: (String, () -> Unit) -> Unit = { _, grant -> grant() }
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var hiddenCount by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    LaunchedEffect(reloadTick, lockedPaths, unlockedPaths) {
        items = withContext(Dispatchers.IO) { TrashOps.listTrash(lockedPaths, unlockedPaths) }
        hiddenCount = withContext(Dispatchers.IO) { TrashOps.countHiddenLocked(lockedPaths, unlockedPaths) }
    }
    // M13: pulihkan / hapus permanen item dari folder terkunci butuh
    // biometrik dulu (biasanya tak terjangkau — item semacam itu
    // disembunyikan — tapi gerbang ini menutup celah bila daftar
    // dimuat sebelum folder dikunci).
    fun guardedTrashAction(item: TrashItem, action: () -> Unit) {
        if (item.originalPath.isNotBlank() &&
            FileOps.isLockedPath(item.originalPath, lockedPaths) &&
            !FileOps.isUnlockedPath(item.originalPath, unlockedPaths)
        ) {
            onRequestUnlock(item.originalPath, action)
        } else {
            action()
        }
    }
    fun runAction(successMsg: String, block: () -> Boolean) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            busy = false
            if (ok) {
                notify(successMsg)
                reloadTick++
            } else {
                notify(t.opFailed)
            }
        }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(t.trash) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        if (!items.isNullOrEmpty()) {
                            TextButton(enabled = !busy, onClick = { confirmEmpty = true }) { Text(t.emptyTrash) }
                        }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        val loaded = items
        Box(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            when {
                loaded == null -> if (isLiquid) LiquidSpinner(Modifier.align(Alignment.Center).size(52.dp)) else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                loaded.isEmpty() -> Text(
                    // M13: bila ada item tersembunyi, jangan klaim
                    // sampah kosong — hanya jumlahnya yang disebut.
                    if (hiddenCount > 0) t.trashLockedHidden(hiddenCount) else t.trashEmpty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (hiddenCount > 0) {
                    if (isLiquid) {
                        item {
                            Text(
                                t.trash,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                        item {
                            Text(
                                t.trashLockedHidden(hiddenCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(loaded, key = { it.trashName }) { item ->
                        TrashItemRow(
                            item = item,
                            busy = busy,
                            onRestore = {
                                guardedTrashAction(item) {
                                    runAction(t.restoredDone) { TrashOps.restore(item) }
                                }
                            },
                            onDelete = { confirmDelete = item }
                        )
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        if (isLiquid) {
            IosAlertDialog(
                title = t.emptyTrash,
                text = if (hiddenCount > 0) t.emptyTrashConfirm + "\n" + t.trashLockedHidden(hiddenCount) else t.emptyTrashConfirm,
                buttons = listOf(
                    IosAlertButton(t.cancel) { confirmEmpty = false },
                    IosAlertButton(t.deleteForever, destructive = true) {
                        confirmEmpty = false
                        runAction(t.deletedDone) { TrashOps.emptyTrash(lockedPaths, unlockedPaths) }
                    }
                ),
                onDismissRequest = { confirmEmpty = false },
                twoColumn = true
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirmEmpty = false },
                title = { Text(t.emptyTrash) },
                text = {
                    Text(
                        if (hiddenCount > 0) t.emptyTrashConfirm + "\n" + t.trashLockedHidden(hiddenCount)
                        else t.emptyTrashConfirm
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmEmpty = false
                        runAction(t.deletedDone) { TrashOps.emptyTrash(lockedPaths, unlockedPaths) }
                    }) { Text(t.deleteForever) }
                },
                dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(t.cancel) } }
            )
        }
    }
    confirmDelete?.let { item ->
        if (isLiquid) {
            IosAlertDialog(
                title = t.deleteForever,
                text = t.deleteForeverConfirm(item.name),
                buttons = listOf(
                    IosAlertButton(t.cancel) { confirmDelete = null },
                    IosAlertButton(t.deleteForever, destructive = true) {
                        confirmDelete = null
                        guardedTrashAction(item) {
                            runAction(t.deletedDone) { TrashOps.deleteForever(item) }
                        }
                    }
                ),
                onDismissRequest = { confirmDelete = null },
                twoColumn = true
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(t.deleteForever) },
                text = { Text(t.deleteForeverConfirm(item.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = null
                        guardedTrashAction(item) {
                            runAction(t.deletedDone) { TrashOps.deleteForever(item) }
                        }
                    }) { Text(t.deleteForever) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t.cancel) } }
            )
        }
    }
}

@Composable
private fun TrashItemRow(item: TrashItem, busy: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    val t = txt()
    val category = if (item.isDir) Category.OTHER else FileOps.categoryOf(item.name)
    val color = entryColor(
        FEntry(item.file.absolutePath, item.name, item.isDir, if (item.size < 0L) 0L else item.size, item.deletedAt, category)
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.isDir) Icons.Default.Folder else categoryIcon(category, item.name),
                null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parent = File(item.originalPath).parent ?: item.originalPath
            Text(parent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, appLocale()
            ).format(java.util.Date(item.deletedAt))
            val sub = if (item.size >= 0L) date + " • " + FileOps.formatSize(item.size) else date
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(enabled = !busy, onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, t.restore) }
        IconButton(enabled = !busy, onClick = onDelete) { Icon(Icons.Default.DeleteForever, t.deleteForever) }
    }
}

/**
 * Layar isi payload.bin: daftar partisi di dalamnya, dibuka TANPA mengekstrak.
 * "Ekstrak Semua" mengekstrak semua partisi ke folder "payload"; mengetuk satu
 * partisi mengekstrak partisi itu saja lalu membukanya.
 */
/** Ringkasan operasi satu partisi: "REPLACE 12 • ZERO 3" (hanya jenis yang ada). */
private fun opSummary(opCounts: Map<Long, Int>): String {
    val labels = listOf(0L to "REPLACE", 1L to "REPLACE_BZ", 8L to "REPLACE_XZ", 9L to "ZERO", 10L to "DISCARD")
    return labels.mapNotNull { (code, label) -> opCounts[code]?.let { "$label $it" } }.joinToString(" • ")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PayloadScreen(
    path: String,
    onBack: () -> Unit,
    onError: () -> Unit,
    onUnsupported: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenPartition: (String) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val payloadFile = remember(path) { File(path) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var listing by remember(path) { mutableStateOf<PayloadOps.Listing?>(null) }
    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) { PayloadOps.listPartitions(payloadFile) }
        when {
            loaded.error == "unsupported" -> onUnsupported()
            loaded.error != null -> onError()
            else -> listing = loaded
        }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(payloadFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        val loaded = listing
        val parts = loaded?.partitions.orEmpty()
        val contentState = when {
            loaded == null -> 0
            parts.isEmpty() -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = contentState,
            transitionSpec = {
                fadeIn(animationSpec = fadeSpec) togetherWith
                    fadeOut(animationSpec = fadeSpec)
            },
            modifier = Modifier.fillMaxSize().padding(padding).liquidWashBackground(),
            label = "payloadContentFade"
        ) { state ->
            when (state) {
                0 -> Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLiquid) LiquidSpinner() else LoadingIndicator()
                        Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                1 -> Box(Modifier.fillMaxSize()) {
                    Text(
                        t.archiveEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                    if (isLiquid) {
                            Text(
                                payloadFile.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                    }
                        val meta = loaded
                        if (meta != null) {
                            Text(
                                t.payloadMeta(meta.version, FileOps.formatSize(meta.blockSize), parts.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(parts, key = { it.name }) { part ->
                        ArchiveItemRow(
                            ArchiveItem(part.name + ".img", false, part.size),
                            null,
                            extra = opSummary(part.opCounts)
                        ) {
                            onOpenPartition(part.name)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArchiveScreen(
    path: String,
    initialPrefix: String = "",
    reloadKey: Int = 0,
    onPrefixChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onError: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val archiveFile = remember(path) { File(path) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var items by remember(path) { mutableStateOf<List<ArchiveItem>?>(null) }
    var prefix by remember(path) { mutableStateOf(initialPrefix) }
    LaunchedEffect(path, reloadKey) {
        val loaded = withContext(Dispatchers.IO) { ArchiveOps.listContents(archiveFile) }
        if (loaded == null) onError() else items = loaded
    }
    BackHandler(enabled = prefix.isNotEmpty()) {
        val parent = prefix.removeSuffix("/").substringBeforeLast('/', "")
        val next = if (parent.isEmpty()) "" else "$parent/"
        prefix = next
        onPrefixChange(next)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(archiveFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            if (prefix.isNotEmpty()) {
                Text(
                    "/" + prefix.removeSuffix("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val loaded = items
            val contentState = when {
                loaded == null -> 0
                loaded.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = (contentState to prefix),
                transitionSpec = {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                },
                modifier = Modifier.fillMaxSize(),
                label = "archiveContentFade"
            ) { (state, shownPrefix) ->
                when (state) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLiquid) LiquidSpinner() else LoadingIndicator()
                            Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.archiveEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        val children = archiveChildren(loaded.orEmpty(), shownPrefix)
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (isLiquid) {
                                item {
                                    Text(
                                        archiveFile.name,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(children, key = { it.first.name }) { (item, count) ->
                                ArchiveItemRow(item = item, count = count, onClick = {
                                    if (item.isDir) { prefix = item.name; onPrefixChange(item.name) } else onOpenFile(item.name)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Layar Isi Ramdisk boot.img (r152, IM3 fase 2): mengetuk boot.img
 * membuka layar ini — daftar isi ramdisk seperti arsip. Folder di
 * dalam ramdisk bisa dimasuki; mengetuk berkas teks membukanya di
 * penampil teks, berkas lain diekstrak ke cache lalu dibuka; tombol
 * unduh di tiap baris mengekstrak satu entri, tombol Ekstrak Semua
 * mengekstrak seluruh isi ke folder boot.img.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImgBrowseScreen(
    path: String,
    initialPrefix: String = "",
    onPrefixChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onError: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenFile: (String) -> Unit,
    onExtractOne: (String) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val imgFile = remember(path) { File(path) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var result by remember(path) { mutableStateOf<RamdiskResult?>(null) }
    var prefix by remember(path) { mutableStateOf(initialPrefix) }
    // LD3 r160: progres dekompresi ramdisk (byte yang sudah
    // didekompresi; total tak diketahui) + tombol Batal di layar.
    var ramdiskBytes by remember(path) { mutableStateOf<Long?>(null) }
    val ramdiskCancelFlag = remember(path) { AtomicBoolean(false) }
    LaunchedEffect(path) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                ImgOps.listRamdisk(
                    imgFile,
                    onProgress = { ramdiskBytes = it },
                    cancelFlag = ramdiskCancelFlag
                )
            }
            if (loaded is RamdiskResult.Invalid) onError() else result = loaded
        } catch (_: ImgCancelledException) {
            onBack()
        }
    }
    BackHandler(enabled = prefix.isNotEmpty()) {
        val parent = prefix.removeSuffix("/").substringBeforeLast('/', "")
        val next = if (parent.isEmpty()) "" else "$parent/"
        prefix = next
        onPrefixChange(next)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(imgFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            if (prefix.isNotEmpty()) {
                Text(
                    "/" + prefix.removeSuffix("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val loaded = result
            val unsupportedKind = (loaded as? RamdiskResult.UnsupportedCompression)?.kind
            val entries = (loaded as? RamdiskResult.Ok)?.entries.orEmpty()
            val contentState = when {
                loaded == null -> 0
                unsupportedKind != null -> 3
                entries.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = (contentState to prefix),
                transitionSpec = {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                },
                modifier = Modifier.fillMaxSize(),
                label = "imgContentFade"
            ) { (state, shownPrefix) ->
                when (state) {
                    0 -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 48.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLiquid) LiquidSpinner() else LoadingIndicator()
                        Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        val bytes = ramdiskBytes
                        if (bytes != null) {
                            if (LocalIsLiquid.current) {
                                LiquidProgressBar(progress = null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            } else {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }
                            Text(
                                t.ramdiskProgress(FileOps.formatSize(bytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        TextButton(
                            onClick = { ramdiskCancelFlag.set(true) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(t.cancel) }
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.imgRamdiskEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    3 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.imgRamdiskUnsupported(unsupportedKind ?: ""),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                        )
                    }
                    else -> {
                        val children = ImgOps.ramdiskChildren(entries, shownPrefix)
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (isLiquid) {
                                item {
                                    Text(
                                        imgFile.name,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(children, key = { it.first.name }) { (item, count) ->
                                ArchiveItemRow(
                                    item = ArchiveItem(item.name, item.isDir, item.size),
                                    count = count,
                                    onExtract = if (item.isDir) null else { { onExtractOne(item.name) } },
                                    onClick = {
                                        if (item.isDir) { prefix = item.name; onPrefixChange(item.name) } else onOpenFile(item.name)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** r154: layar isi citra ext4 (system.img / super.img; sparse sudah
 *  di-unsparse ke temp saat sesi dibuka). Navigasi folder, ekstrak
 *  per-berkas + Ekstrak Semua, teks dibuka read-only di penampil teks.
 *  Pola sama seperti ImgBrowseScreen (r152): prefix di-hoist ke
 *  SysFileApp (r108) supaya kembali dari berkas mendarat di
 *  subfolder yang sama. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Ext4BrowseScreen(
    path: String,
    session: Ext4Session?,
    initialPrefix: String = "",
    onPrefixChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onError: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenFile: (Ext4Entry) -> Unit,
    onExtractOne: (Ext4Entry) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val imgFile = remember(path) { File(path) }
    val scope = rememberCoroutineScope()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var entries by remember(path) { mutableStateOf<List<Ext4Entry>?>(null) }
    var prefix by remember(path) { mutableStateOf(initialPrefix) }
    LaunchedEffect(path, prefix) {
        entries = null
        val reader = session?.reader
        if (reader == null) { onError(); return@LaunchedEffect }
        val loaded = withContext(Dispatchers.IO) {
            val ino = reader.resolvePath(prefix) ?: return@withContext null
            reader.listDir(ino)
        }
        if (loaded == null) onError() else entries = loaded
    }
    BackHandler(enabled = prefix.isNotEmpty()) {
        val parent = prefix.removeSuffix("/").substringBeforeLast('/', "")
        val next = if (parent.isEmpty()) "" else "$parent/"
        prefix = next
        onPrefixChange(next)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(imgFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            if (prefix.isNotEmpty()) {
                Text(
                    "/" + prefix.removeSuffix("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val loaded = entries
            val contentState = when {
                loaded == null -> 0
                loaded.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = (contentState to prefix),
                transitionSpec = {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                },
                modifier = Modifier.fillMaxSize(),
                label = "ext4ContentFade"
            ) { (state, shownPrefix) ->
                when (state) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLiquid) LiquidSpinner() else LoadingIndicator()
                            Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.imgExt4Empty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        val items = loaded!!
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (isLiquid) {
                                item {
                                    Text(
                                        imgFile.name,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(items, key = { it.name }) { item ->
                                ArchiveItemRow(
                                    item = ArchiveItem(item.name, item.isDir, item.size),
                                    count = null,
                                    extra = if (item.isSymlink) "→ ${item.symlinkTarget ?: ""}" else null,
                                    onExtract = if (item.isDir || item.isSymlink) null else { { onExtractOne(item) } },
                                    onClick = {
                                        if (item.isDir) {
                                            prefix = shownPrefix + item.name + "/"
                                            onPrefixChange(prefix)
                                        } else if (item.isSymlink) {
                                            scope.launch {
                                                val reader = session?.reader ?: return@launch
                                                val target = item.symlinkTarget ?: return@launch
                                                val targetPath = if (target.startsWith("/")) target.removePrefix("/")
                                                else shownPrefix + target
                                                val resolved = withContext(Dispatchers.IO) { reader.resolveEntry(targetPath) }
                                                if (resolved == null) {
                                                    onOpenFile(item)
                                                } else if (resolved.isDir) {
                                                    val next = Ext4Reader.normalizePath(targetPath) + "/"
                                                    prefix = next
                                                    onPrefixChange(next)
                                                } else {
                                                    onOpenFile(resolved)
                                                }
                                            }
                                        } else onOpenFile(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** r155: layar isi citra FAT12/16/32 (citra kartu SD, partisi ESP,
 *  citra recovery). Navigasi folder, ekstrak per-berkas + Ekstrak
 *  Semua, teks dibuka read-only di penampil teks. Pola sama seperti
 *  Ext4BrowseScreen (r154): prefix di-hoist ke SysFileApp (r108)
 *  supaya kembali dari berkas mendarat di subfolder yang sama. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FatBrowseScreen(
    path: String,
    reader: FatReader?,
    initialPrefix: String = "",
    onPrefixChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onError: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenFile: (String) -> Unit,
    onExtractOne: (String) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val imgFile = remember(path) { File(path) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var entries by remember(path) { mutableStateOf<List<FatEntry>?>(null) }
    var prefix by remember(path) { mutableStateOf(initialPrefix) }
    LaunchedEffect(path, prefix) {
        entries = null
        val r = reader
        if (r == null) { onError(); return@LaunchedEffect }
        val loaded = withContext(Dispatchers.IO) { r.listDir(prefix) }
        if (loaded == null) onError() else entries = loaded
    }
    BackHandler(enabled = prefix.isNotEmpty()) {
        val parent = prefix.removeSuffix("/").substringBeforeLast('/', "")
        val next = if (parent.isEmpty()) "" else "$parent/"
        prefix = next
        onPrefixChange(next)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(imgFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } },
                    actions = {
                        TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            if (prefix.isNotEmpty()) {
                Text(
                    "/" + prefix.removeSuffix("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val loaded = entries
            val contentState = when {
                loaded == null -> 0
                loaded.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = (contentState to prefix),
                transitionSpec = {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                },
                modifier = Modifier.fillMaxSize(),
                label = "fatContentFade"
            ) { (state, shownPrefix) ->
                when (state) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLiquid) LiquidSpinner() else LoadingIndicator()
                            Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        Text(
                            t.imgFatEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        val items = loaded!!
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (isLiquid) {
                                item {
                                    Text(
                                        imgFile.name,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(items, key = { it.name }) { item ->
                                ArchiveItemRow(
                                    item = ArchiveItem(item.name, item.isDir, item.size),
                                    count = null,
                                    onExtract = if (item.isDir) null else { { onExtractOne(shownPrefix + item.name) } },
                                    onClick = {
                                        if (item.isDir) {
                                            prefix = shownPrefix + item.name + "/"
                                            onPrefixChange(prefix)
                                        } else onOpenFile(shownPrefix + item.name)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** r156: layar daftar partisi logis super.img (liblp). Daftar
 *  datar (tanpa folder): nama + ukuran tiap partisi; partisi yang
 *  tidak tersedia (dinonaktifkan / extent di perangkat lain / di
 *  luar berkas) ditandai dan tombol unduhnya disembunyikan. Ketuk
 *  partisi membukanya di layar isi ext4 (MainActivity); tombol
 *  unduh mengekstrak partisi mentah sebagai berkas .img di sebelah
 *  super.img. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LpBrowseScreen(
    path: String,
    session: LpSession?,
    onBack: () -> Unit,
    onError: () -> Unit,
    onOpenPartition: (LpPartition) -> Unit,
    onExtractPartition: (LpPartition) -> Unit
) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val imgFile = remember(path) { File(path) }
    val partitions = session?.reader?.partitions
    LaunchedEffect(session) {
        if (partitions == null) onError()
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(imgFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            val list = partitions
            when {
                list == null -> Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLiquid) LiquidSpinner() else LoadingIndicator()
                        Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    Text(
                        t.imgLpEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (isLiquid) {
                        item {
                            Text(
                                imgFile.name,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(list, key = { it.name }) { part ->
                        ArchiveItemRow(
                            item = ArchiveItem(part.name, false, part.sizeBytes),
                            count = null,
                            extra = if (part.available) null else t.imgLpUnavailable,
                            onExtract = if (part.available) { { onExtractPartition(part) } } else null,
                            onClick = { onOpenPartition(part) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    rootPath: String,
    onBack: () -> Unit,
    onOpenEntry: (FEntry) -> Unit,
    isLiquid: Boolean = false
) {
    val t = txt()
    val liquid = LocalLiquid.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    var filterCat by remember { mutableStateOf<Category?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        val prefs = context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
        val showHidden = prefs.getBoolean("show_hidden", false)
        val lockedFolders = FileOps.canonicalizeLocked(prefs.getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet())
        results = withContext(Dispatchers.IO) { FileOps.search(File(rootPath), q, showHidden, lockedFolders) }
        searching = false
    }

    val filtered = results.filter { filterCat == null || it.category == filterCat }
    val dirResults = filtered.filter { it.isDir }
    val fileResults = filtered.filter { !it.isDir }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                // r223 X3: input cari ASLI (query state) — di Liquid jadi
                // pil kaca berisi BasicTextField; Material tetap SearchBar M3.
                if (isLiquid) {
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .glassTint(RoundedCornerShape(24.dp), liquid.glassTint)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f).padding(8.dp),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (query.isEmpty()) {
                                        Text(
                                            t.searchHint,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, t.cancel)
                                }
                            }
                        }
                    }
                } else {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = {},
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text(t.searchHint) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Default.Close, t.cancel)
                                        }
                                    }
                                }
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.weight(1f)
                    ) {}
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterCat == null,
                        onClick = { filterCat = null },
                        label = { Text(t.all) }
                    )
                }
                listOf(
                    Category.IMAGE to t.catImagesS,
                    Category.VIDEO to t.catVideosS,
                    Category.AUDIO to t.catAudioS,
                    Category.DOCUMENT to t.catDocumentsS
                ).forEach { (cat, label) ->
                    item {
                        FilterChip(
                            selected = filterCat == cat,
                            onClick = { filterCat = if (filterCat == cat) null else cat },
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (searching) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isLiquid) LiquidSpinner(Modifier.size(30.dp)) else LoadingIndicator(Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.searching, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!searching && query.isNotBlank() && filtered.isEmpty()) {
                Text(
                    t.searchNoResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (dirResults.isNotEmpty()) {
                    item {
                        Text(
                            t.foldersLabel,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(dirResults, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = false,
                            isSelected = false,
                            itemCount = null,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = {},
                            isLiquid = isLiquid
                        )
                    }
                }
                if (fileResults.isNotEmpty()) {
                    item {
                        Text(
                            t.filesLabel,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(fileResults, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = false,
                            isSelected = false,
                            itemCount = null,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = {},
                            isLiquid = isLiquid
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar analisis penyimpanan
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalysisScreen(
    roots: List<RootInfo>,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FEntry) -> Unit,
    isLiquid: Boolean = false
) {
    val t = txt()
    // r215: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val context = LocalContext.current
    val lockedFolders = remember {
        FileOps.canonicalizeLocked(
            context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
                .getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet()
        )
    }
    var results by remember { mutableStateOf<Map<String, AnalysisResult>>(emptyMap()) }
    var scanningPath by remember { mutableStateOf<String?>(null) }
    var selectedRootPath by remember { mutableStateOf<String?>(null) }
    var expandedLargest by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(roots) {
        for (root in roots) {
            if (results.containsKey(root.path)) continue
            scanningPath = root.path
            val res = withContext(Dispatchers.IO) {
                try { FileOps.analyze(File(root.path), lockedFolders) } catch (e: Exception) { null }
            }
            if (res != null) results = results + (root.path to res)
        }
        scanningPath = null
    }

    val activeRootPath = selectedRootPath ?: roots.firstOrNull()?.path
    val displayRoots = if (roots.size > 1) roots.filter { it.path == activeRootPath } else roots
    LazyColumn(
            Modifier.fillMaxSize().padding(16.dp).liquidWashBackground(),
            contentPadding = PaddingValues(bottom = if (isLiquid) 96.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val done = roots.mapNotNull { results[it.path] }
            if (done.isNotEmpty()) {
                item(key = "summary") {
                    val totalFilesAll = done.sumOf { it.totalFiles }
                    val totalStorage = roots.sumOf { it.total }
                    val usedStorage = roots.sumOf { it.total - it.free }
                    val freeFracSummary = if (totalStorage > 0) (totalStorage - usedStorage).toFloat() / totalStorage.toFloat() else 0f
                    val (statusText, statusDot) = storageStatus(freeFracSummary)
                    StorageCard(
                        isLiquid = isLiquid,
                        label = t.storageAnalysis,
                        usedText = FileOps.formatSize(usedStorage),
                        detailText = t.ofTotal(FileOps.formatSize(totalStorage)) + " · " + t.items(totalFilesAll),
                        percentText = String.format(appLocale(), "%.1f%%", if (totalStorage > 0) usedStorage * 100.0 / totalStorage else 0.0),
                        usedWord = t.usedWord,
                        statusText = statusText,
                        statusDot = statusDot,
                        usedFraction = (if (totalStorage > 0) usedStorage.toFloat() / totalStorage.toFloat() else 0f).coerceIn(0f, 1f),
                        onClick = {}
                    )
                }
            }
            if (roots.size > 1) {
                item(key = "tabs") {
                    TabRow(selectedTabIndex = roots.indexOfFirst { it.path == activeRootPath }.coerceAtLeast(0)) {
                        roots.forEach { root ->
                            Tab(
                                selected = root.path == activeRootPath,
                                onClick = { selectedRootPath = root.path },
                                text = { Text(rootLabel(root, t)) }
                            )
                        }
                    }
                }
            }
            displayRoots.forEach { root ->
                val res = results[root.path]
                item(key = "root-" + root.path) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (root.kind) {
                                        "sd" -> Icons.Default.SdStorage
                                        "usb" -> Icons.Default.Usb
                                        else -> Icons.Default.Storage
                                    },
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    rootLabel(root, t),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (res != null) {
                                        t.items(res.totalFiles) + " \u00b7 " +
                                            FileOps.formatSize(res.byCategory.values.sum())
                                    } else {
                                        t.scanning
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (res == null) {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest),
                                border = null,
                                modifier = Modifier.then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
                            ) {
                                Row(
                                    Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isLiquid) LiquidSpinner(Modifier.size(30.dp)) else LoadingIndicator(Modifier.size(30.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (scanningPath == root.path) t.scanning else t.scanFailed)
                                }
                            }
                        } else {
                            val labels = mapOf(
                                Category.IMAGE to t.catImages,
                                Category.VIDEO to t.catVideos,
                                Category.AUDIO to t.catAudio,
                                Category.DOCUMENT to t.catDocuments,
                                Category.APP to t.catApps,
                                Category.ARCHIVE to t.catArchives,
                                Category.OTHER to t.catOther
                            )
                            val maxCat = (res.byCategory.values.maxOrNull() ?: 1).coerceAtLeast(1)
                            val entries = res.byCategory.entries.sortedByDescending { it.value }.toList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                entries.forEach { (cat, size) ->
                                    Card(
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest),
                                        border = null,
                                        modifier = Modifier.fillMaxWidth().then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    categoryIcon(cat, ""), null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        labels[cat] ?: cat.name,
                                                        Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        FileOps.formatSize(size),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(Modifier.height(7.dp))
                                                if (LocalIsLiquid.current) {
                                                    LiquidProgressBar(
                                                        progress = { (size.toFloat() / maxCat.toFloat()).coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                } else {
                                                    LinearWavyProgressIndicator(
                                                        progress = { (size.toFloat() / maxCat.toFloat()).coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (res.largest.isNotEmpty()) {
                                Text(
                                    t.largestFiles,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                                )
                                val shownLargest = if (expandedLargest.contains(root.path)) res.largest else res.largest.take(5)
                                shownLargest.forEach { entry ->
                                    Card(
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest),
                                        border = null,
                                        modifier = Modifier.then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable { onOpenFile(entry) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FileThumbTile(entry, 48)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    entry.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    entry.path,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                FileOps.formatSize(entry.size),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (res.largest.size > 5) {
                                    TextButton(onClick = {
                                        expandedLargest = if (expandedLargest.contains(root.path)) {
                                            expandedLargest - root.path
                                        } else {
                                            expandedLargest + root.path
                                        }
                                    }) {
                                        Text(
                                            if (expandedLargest.contains(root.path)) t.showLess
                                            else t.seeAll + " (" + res.largest.size + ")"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

// ---------------------------------------------------------------------------
// Dialog-dialog
// ---------------------------------------------------------------------------
