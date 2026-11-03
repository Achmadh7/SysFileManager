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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// Layar Terbaru, Kategori, Pencarian, dan Analisis penyimpanan.

/** Tab File Terbaru: berkas terbaru dikelompokkan per folder induk (varian Grup Folder). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    files: List<FEntry>,
    roots: List<RootInfo>,
    accessGranted: Boolean,
    onOpenEntry: (FEntry) -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val t = txt()
    if (!accessGranted) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
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
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                t.noRecent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    val groups = remember(files) {
        files.groupBy { it.file.parentFile?.absolutePath ?: "" }
            .entries
            .map { (parent, items) -> parent to items.sortedByDescending { it.modified } }
            .sortedByDescending { (_, items) -> items.firstOrNull()?.modified ?: 0L }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(groups, key = { it.first }) { (parentPath, items) ->
            val root = roots.firstOrNull { it.path == parentPath }
            val parentName = root?.let { rootLabel(it, t) } ?: File(parentPath).name
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenFolder(parentPath) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            parentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.clickable { onOpenEntry(entry) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                        recentWhen(entry.modified, t) + " \u00b7 " + FileOps.formatSize(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
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

/** Layar satu kategori: semua berkas kategori itu lintas volume, terbaru dulu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryScreen(
    category: Category,
    roots: List<RootInfo>,
    showHidden: Boolean,
    accessGranted: Boolean,
    onBack: () -> Unit,
    onOpenEntry: (FEntry) -> Unit
) {
    val t = txt()
    val context = LocalContext.current
    val lockedFolders = remember {
        context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
            .getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet()
    }
    var files by remember { mutableStateOf<List<FEntry>?>(null) }
    LaunchedEffect(category, accessGranted) {
        files = null
        files = if (accessGranted) {
            withContext(Dispatchers.IO) {
                FileOps.categoryFiles(roots.map { File(it.path) }, category, showHidden, lockedFolders)
            }
        } else {
            emptyList()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val loaded = files
            val contentState = when {
                loaded == null -> 0
                loaded.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 300))
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
                            LoadingIndicator()
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
                    else -> if (category == Category.IMAGE) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(loaded.orEmpty(), key = { it.path }) { entry ->
                                AsyncImage(
                                    model = File(entry.path),
                                    contentDescription = entry.name,
                                    imageLoader = rememberThumbLoader(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onOpenEntry(entry) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(loaded.orEmpty(), key = { it.path }) { entry ->
                                FileRow(
                                    entry = entry,
                                    selectionMode = false,
                                    isSelected = false,
                                    itemCount = null,
                                    onClick = { onOpenEntry(entry) },
                                    onLongClick = {}
                                )
                            }
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
private fun ArchiveItemRow(item: ArchiveItem, count: Int?, onClick: () -> Unit) {
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
        }
    }
}

/**
 * Layar Isi Arsip: mengetuk arsip membuka layar ini (bukan langsung ekstrak).
 * Folder di dalam arsip bisa dimasuki; mengetuk berkas mengekstrak berkas itu
 * saja lalu membukanya; tombol Ekstrak Semua mengekstrak seluruh isi.
 */

/**
 * Layar Tempat Sampah: berkas yang dihapus (dipindah ke .SysFileTrash) bisa
 * dipulihkan ke jalur asalnya atau dihapus permanen; ada juga Kosongkan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrashScreen(onBack: () -> Unit, notify: (String) -> Unit) {
    val t = txt()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    LaunchedEffect(reloadTick) {
        items = withContext(Dispatchers.IO) { TrashOps.listTrash() }
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
            TopAppBar(
                title = { Text(t.trash) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        TextButton(enabled = !busy, onClick = { confirmEmpty = true }) { Text(t.emptyTrash) }
                    }
                }
            )
        }
    ) { padding ->
        val loaded = items
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loaded == null -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                loaded.isEmpty() -> Text(
                    t.trashEmpty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(loaded, key = { it.trashName }) { item ->
                        TrashItemRow(
                            item = item,
                            busy = busy,
                            onRestore = { runAction(t.restoredDone) { TrashOps.restore(item) } },
                            onDelete = { confirmDelete = item }
                        )
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(t.emptyTrash) },
            text = { Text(t.emptyTrashConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    runAction(t.deletedDone) { TrashOps.emptyTrash() }
                }) { Text(t.deleteForever) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(t.cancel) } }
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t.deleteForever) },
            text = { Text(t.deleteForeverConfirm(item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    runAction(t.deletedDone) { TrashOps.deleteForever(item) }
                }) { Text(t.deleteForever) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t.cancel) } }
        )
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
    val payloadFile = remember(path) { File(path) }
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
            TopAppBar(
                title = { Text(payloadFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } },
                actions = {
                    TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                }
            )
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
                fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 300))
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "payloadContentFade"
        ) { state ->
            when (state) {
                0 -> Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator()
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
                    items(parts, key = { it.name }) { part ->
                        ArchiveItemRow(ArchiveItem(part.name + ".img", false, part.size), null) {
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
    onBack: () -> Unit,
    onError: () -> Unit,
    onExtractAll: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val t = txt()
    val archiveFile = remember(path) { File(path) }
    var items by remember(path) { mutableStateOf<List<ArchiveItem>?>(null) }
    var prefix by remember(path) { mutableStateOf("") }
    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) { ArchiveOps.listContents(archiveFile) }
        if (loaded == null) onError() else items = loaded
    }
    BackHandler(enabled = prefix.isNotEmpty()) {
        val parent = prefix.removeSuffix("/").substringBeforeLast('/', "")
        prefix = if (parent.isEmpty()) "" else "$parent/"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(archiveFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } },
                actions = {
                    TextButton(onClick = onExtractAll) { Text(t.extractAll) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 300))
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
                            LoadingIndicator()
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
                            items(children, key = { it.first.name }) { (item, count) ->
                                ArchiveItemRow(item = item, count = count, onClick = {
                                    if (item.isDir) prefix = item.name else onOpenFile(item.name)
                                })
                            }
                        }
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
    onOpenEntry: (FEntry) -> Unit
) {
    val t = txt()
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
        val lockedFolders = prefs.getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet()
        results = withContext(Dispatchers.IO) { FileOps.search(File(rootPath), q, showHidden, lockedFolders) }
        searching = false
    }

    val filtered = results.filter { filterCat == null || it.category == filterCat }
    val dirResults = filtered.filter { it.isDir }
    val fileResults = filtered.filter { !it.isDir }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
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
                    LoadingIndicator(Modifier.size(30.dp))
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
                            onLongClick = {}
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
                            onLongClick = {}
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
    onOpenFile: (FEntry) -> Unit
) {
    val t = txt()
    val context = LocalContext.current
    val lockedFolders = remember {
        context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
            .getStringSet("locked_folders", emptySet())?.toSet() ?: emptySet()
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
            Modifier.fillMaxSize().padding(16.dp),
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
                    GradientStorageCard(
                        label = t.storageAnalysis,
                        usedText = FileOps.formatSize(usedStorage),
                        detailText = t.ofTotal(FileOps.formatSize(totalStorage)) + " · " + t.items(totalFilesAll),
                        percentText = (if (totalStorage > 0) usedStorage * 100 / totalStorage else 0).toString() + "%",
                        usedWord = t.usedWord,
                        statusText = statusText,
                        statusDot = statusDot,
                        fraction = if (totalStorage > 0) usedStorage.toFloat() / totalStorage.toFloat() else 0f,
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
                        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF2E5A88)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (root.kind) {
                                        "sd" -> Icons.Default.SdStorage
                                        "usb" -> Icons.Default.Usb
                                        else -> Icons.Default.Storage
                                    },
                                    null, tint = Color.White, modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        rootLabel(root, t),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        if (res != null) {
                                            t.items(res.totalFiles) + " \u00b7 " +
                                                FileOps.formatSize(res.byCategory.values.sum())
                                        } else {
                                            t.scanning
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC8DCF5)
                                    )
                                }
                            }
                        }
                        if (res == null) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LoadingIndicator(Modifier.size(30.dp))
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
                            res.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, size) ->
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                                                .background(categoryColor(cat).copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                categoryIcon(cat, ""), null,
                                                tint = categoryColor(cat),
                                                modifier = Modifier.size(28.dp)
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
                                            LinearProgressIndicator(
                                                progress = { (size.toFloat() / maxCat.toFloat()).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = categoryColor(cat),
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
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
                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
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
