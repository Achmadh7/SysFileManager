package id.fileku.manager

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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Layar tambahan yang menimpa layar utama (Beranda/peramban). */
private sealed interface Overlay {
    data class Search(val rootPath: String) : Overlay
    data object Analysis : Overlay
    data class ImageView(val path: String) : Overlay
    data class TextView(val path: String) : Overlay
}

private enum class ClipMode { COPY, CUT }

private sealed interface DialogKind {
    data object NewFolder : DialogKind
    data class Rename(val entry: FEntry) : DialogKind
    data class Delete(val entries: List<FEntry>) : DialogKind
    data class Properties(val entry: FEntry) : DialogKind
    data class Compress(val entries: List<FEntry>) : DialogKind
    data object Sort : DialogKind
    data object Settings : DialogKind
    data object About : DialogKind
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FileKuApp() }
    }
}

private fun Context.hasFileAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileKuApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("fileku_prefs", Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var language by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
    AppLang.code = language
    val t = txt()

    var accessGranted by remember { mutableStateOf(context.hasFileAccess()) }
    var roots by remember { mutableStateOf<List<RootInfo>>(emptyList()) }
    var browseStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var entries by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    var entriesLoaded by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<Pair<List<String>, ClipMode>?>(null) }
    var working by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }

    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var gridView by remember { mutableStateOf(prefs.getBoolean("grid_view", false)) }
    var sortKey by remember {
        mutableStateOf(
            when (prefs.getString("sort_key", "NAME")) {
                "DATE" -> SortKey.DATE
                "SIZE" -> SortKey.SIZE
                else -> SortKey.NAME
            }
        )
    }
    var sortAsc by remember { mutableStateOf(prefs.getBoolean("sort_asc", true)) }

    val currentPath = browseStack.lastOrNull()
    val currentRoot = currentPath?.let { p -> roots.filter { p == it.path || p.startsWith(it.path + "/") }.maxByOrNull { it.path.length } }

    fun notify(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun refreshRoots() {
        roots = FileOps.storageRoots(context)
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessGranted = context.hasFileAccess()
        refreshRoots()
    }
    val legacyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        accessGranted = context.hasFileAccess()
        refreshRoots()
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
                settingsLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    notify(t.opFailed)
                }
            }
        } else {
            legacyPermLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    LaunchedEffect(Unit) { refreshRoots() }

    LaunchedEffect(currentPath, reloadKey, showHidden, sortKey, sortAsc, accessGranted) {
        val path = currentPath
        if (path == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        entriesLoaded = false
        val loaded = withContext(Dispatchers.IO) {
            FileOps.listDir(File(path), showHidden, sortKey, sortAsc)
        }
        entries = loaded ?: emptyList()
        entriesLoaded = true
        selected = emptySet()
    }

    fun openExternal(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileOps.mimeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            notify(t.openFailed)
        }
    }

    fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = FileOps.mimeOf(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, t.share))
        } catch (e: Exception) {
            notify(t.openFailed)
        }
    }

    fun openEntry(entry: FEntry) {
        if (entry.isDir) {
            browseStack = browseStack + entry.path
            return
        }
        val f = entry.file
        when {
            entry.category == Category.IMAGE -> overlay = Overlay.ImageView(entry.path)
            FileOps.isTextFile(f) && f.length() <= 2_000_000 -> overlay = Overlay.TextView(entry.path)
            entry.category == Category.ARCHIVE && FileOps.extOf(entry.name) == "zip" -> {
                working = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { FileOps.unzip(f, f.parentFile ?: f) }
                    working = false
                    if (ok) { notify(t.extractDone); reloadKey++ } else notify(t.opFailed)
                }
            }
            else -> openExternal(f)
        }
    }

    fun runOp(successMsg: String, block: suspend () -> Boolean) {
        working = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            working = false
            if (ok) {
                notify(successMsg)
                reloadKey++
            } else {
                notify(t.opFailed)
            }
            selected = emptySet()
        }
    }

    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = overlay == null && selected.isEmpty() && browseStack.isNotEmpty()) {
        browseStack = browseStack.dropLast(1)
    }

    FileKuTheme(themeMode) {
        when (val ov = overlay) {
            is Overlay.ImageView -> ImageViewerScreen(ov.path, onBack = { overlay = null })
            is Overlay.TextView -> TextViewerScreen(ov.path, onBack = { overlay = null }, notify = { notify(it) })
            is Overlay.Search -> SearchScreen(
                rootPath = ov.rootPath,
                onBack = { overlay = null },
                onOpenEntry = { entry ->
                    overlay = null
                    if (entry.isDir) {
                        browseStack = listOf(entry.path)
                    } else {
                        entry.parentPath()?.let { parent ->
                            if (browseStack.lastOrNull() != parent) browseStack = listOf(parent)
                        }
                        openEntry(entry)
                    }
                }
            )
            is Overlay.Analysis -> AnalysisScreen(
                roots = roots,
                onBack = { overlay = null },
                onOpenFolder = { path -> overlay = null; browseStack = listOf(path) },
                onOpenFile = { entry -> overlay = null; openEntry(entry) }
            )
            null -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        if (currentPath == null) {
                            TopAppBar(
                                title = { Text("FileKu") },
                                actions = {
                                    IconButton(onClick = { dialog = DialogKind.Settings }) {
                                        Icon(Icons.Default.Settings, t.settings)
                                    }
                                }
                            )
                        } else if (selected.isNotEmpty()) {
                            TopAppBar(
                                title = { Text(t.selected(selected.size)) },
                                navigationIcon = {
                                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, t.cancel) }
                                },
                                actions = {
                                    IconButton(onClick = { selected = entries.map { it.path }.toSet() }) {
                                        Icon(Icons.Default.DoneAll, t.selectAll)
                                    }
                                }
                            )
                        } else {
                            TopAppBar(
                                title = {
                                    Text(
                                        if (currentRoot != null && currentPath == currentRoot.path) {
                                            rootLabel(currentRoot, t)
                                        } else {
                                            File(currentPath).name
                                        },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { browseStack = browseStack.dropLast(1) }) {
                                        Icon(Icons.Default.ArrowBack, t.home)
                                    }
                                },
                                actions = {
                                    val isFav = favorites.contains(currentPath)
                                    IconButton(onClick = {
                                        val updated = if (isFav) favorites - currentPath else favorites + currentPath
                                        favorites = updated
                                        prefs.edit().putStringSet("favorites", updated).apply()
                                        notify(if (isFav) t.removeFavorite else t.addFavorite)
                                    }) {
                                        Icon(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, t.favorites)
                                    }
                                    IconButton(onClick = { overlay = Overlay.Search(currentPath) }) {
                                        Icon(Icons.Default.Search, t.search)
                                    }
                                    IconButton(onClick = { browseStack = emptyList() }) {
                                        Icon(Icons.Default.Home, t.home)
                                    }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text(t.newFolder) },
                                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                                onClick = { menuOpen = false; dialog = DialogKind.NewFolder }
                                            )
                                            if (clipboard != null) {
                                                DropdownMenuItem(
                                                    text = { Text(t.paste) },
                                                    leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                                    onClick = {
                                                        menuOpen = false
                                                        val (paths, mode) = clipboard!!
                                                        val destDir = File(currentPath)
                                                        runOp(if (mode == ClipMode.COPY) t.copiedDone else t.movedDone) {
                                                            val files = paths.map { File(it) }
                                                            if (mode == ClipMode.COPY) {
                                                                FileOps.copyInto(files, destDir) > 0
                                                            } else {
                                                                FileOps.moveInto(files, destDir) > 0
                                                            }
                                                        }
                                                        if (mode == ClipMode.CUT) clipboard = null
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(t.sortBy) },
                                                onClick = { menuOpen = false; dialog = DialogKind.Sort }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (gridView) t.listView else t.gridView) },
                                                leadingIcon = { Icon(if (gridView) Icons.Default.ViewList else Icons.Default.GridView, null) },
                                                onClick = {
                                                    menuOpen = false
                                                    gridView = !gridView
                                                    prefs.edit().putBoolean("grid_view", gridView).apply()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(t.showHidden) },
                                                trailingIcon = {
                                                    Switch(checked = showHidden, onCheckedChange = { v ->
                                                        showHidden = v
                                                        prefs.edit().putBoolean("show_hidden", v).apply()
                                                    })
                                                },
                                                onClick = {
                                                    showHidden = !showHidden
                                                    prefs.edit().putBoolean("show_hidden", showHidden).apply()
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (selected.isNotEmpty() && currentPath != null) {
                            SelectionBar(
                                single = selected.size == 1,
                                singleEntry = entries.firstOrNull { it.path == selected.first() },
                                onCopy = { clipboard = selected.toList() to ClipMode.COPY; selected = emptySet() },
                                onCut = { clipboard = selected.toList() to ClipMode.CUT; selected = emptySet() },
                                onDelete = { dialog = DialogKind.Delete(entries.filter { selected.contains(it.path) }) },
                                onShare = { entries.firstOrNull { it.path == selected.first() }?.let { shareFile(it.file) } },
                                onRename = { entries.firstOrNull { it.path == selected.first() }?.let { dialog = DialogKind.Rename(it) } },
                                onCompress = { dialog = DialogKind.Compress(entries.filter { selected.contains(it.path) }) },
                                onProperties = { entries.firstOrNull { it.path == selected.first() }?.let { dialog = DialogKind.Properties(it) } }
                            )
                        } else if (clipboard != null && currentPath != null) {
                            ClipboardBar(
                                count = clipboard!!.first.size,
                                onPaste = {
                                    val (paths, mode) = clipboard!!
                                    val destDir = File(currentPath)
                                    runOp(if (mode == ClipMode.COPY) t.copiedDone else t.movedDone) {
                                        val files = paths.map { File(it) }
                                        if (mode == ClipMode.COPY) FileOps.copyInto(files, destDir) > 0
                                        else FileOps.moveInto(files, destDir) > 0
                                    }
                                    if (mode == ClipMode.CUT) clipboard = null
                                },
                                onClear = { clipboard = null }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (currentPath == null) {
                            HomeScreen(
                                accessGranted = accessGranted,
                                roots = roots,
                                favorites = favorites,
                                onRequestAccess = { requestAccess() },
                                onOpenRoot = { browseStack = listOf(it) },
                                onOpenFavorite = { browseStack = listOf(it) },
                                onOpenCategory = { overlay = Overlay.Analysis }
                            )
                        } else {
                            BrowseScreen(
                                currentPath = currentPath,
                                currentRoot = currentRoot,
                                entries = entries,
                                loaded = entriesLoaded,
                                gridView = gridView,
                                selected = selected,
                                onNavigate = { browseStack = it },
                                onOpenEntry = { entry ->
                                    if (selected.isNotEmpty()) {
                                        selected = if (selected.contains(entry.path)) selected - entry.path else selected + entry.path
                                    } else {
                                        openEntry(entry)
                                    }
                                },
                                onLongPress = { entry -> selected = selected + entry.path },
                                onGoUp = {
                                    val parent = File(currentPath).parent
                                    if (parent != null && currentRoot != null && parent.startsWith(currentRoot.path)) {
                                        browseStack = browseStack.dropLast(1) + parent
                                    } else {
                                        browseStack = browseStack.dropLast(1)
                                    }
                                },
                                canGoUp = currentRoot != null && currentPath != currentRoot.path
                            )
                        }
                    }
                }
            }
        }

        if (working) {
            AlertDialog(
                onDismissRequest = {},
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(16.dp))
                        Text(t.working)
                    }
                },
                confirmButton = {}
            )
        }

        when (val d = dialog) {
            is DialogKind.NewFolder -> NameDialog(
                title = t.newFolder, label = t.folderName, initial = "",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    dialog = null
                    val dir = currentPath?.let { File(it) } ?: return@NameDialog
                    runOp(t.folderCreated) { FileOps.newFolder(dir, name) != null }
                }
            )
            is DialogKind.Rename -> NameDialog(
                title = t.rename, label = t.newName, initial = d.entry.name,
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    dialog = null
                    runOp(t.renamedDone) { FileOps.rename(d.entry.file, name) != null }
                }
            )
            is DialogKind.Compress -> NameDialog(
                title = t.compress, label = t.zipName, initial = "archive",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    dialog = null
                    val dir = currentPath?.let { File(it) } ?: return@NameDialog
                    runOp(t.zipDone) { FileOps.zipFiles(d.entries.map { it.file }, dir, name) != null }
                }
            )
            is DialogKind.Delete -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(t.delete) },
                text = { Text(t.deleteConfirm(if (d.entries.size == 1) d.entries[0].name else t.items(d.entries.size))) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        runOp(t.deletedDone) {
                            d.entries.all { FileOps.deleteRecursively(it.file) }
                        }
                    }) { Text(t.delete) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(t.cancel) } }
            )
            is DialogKind.Properties -> PropertiesDialog(entry = d.entry, onDismiss = { dialog = null })
            is DialogKind.Sort -> SortDialog(
                sortKey = sortKey, ascending = sortAsc,
                onDismiss = { dialog = null },
                onPick = { key, asc ->
                    sortKey = key
                    sortAsc = asc
                    prefs.edit().putString("sort_key", key.name).putBoolean("sort_asc", asc).apply()
                    dialog = null
                }
            )
            is DialogKind.Settings -> SettingsDialog(
                themeMode = themeMode,
                language = language,
                showHidden = showHidden,
                onTheme = { mode ->
                    themeMode = mode
                    prefs.edit().putString("theme_mode", mode).apply()
                },
                onLanguage = { lang ->
                    language = lang
                    AppLang.code = lang
                    prefs.edit().putString("app_language", lang).apply()
                },
                onHidden = { v ->
                    showHidden = v
                    prefs.edit().putBoolean("show_hidden", v).apply()
                },
                onAbout = { dialog = DialogKind.About },
                onDismiss = { dialog = null }
            )
            is DialogKind.About -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("FileKu") },
                text = { Text(t.slogan + "\n\n" + t.version("1.0.0")) },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(t.close) } }
            )
            null -> {}
        }
    }
}

private fun FEntry.parentPath(): String? = File(path).parent

fun rootLabel(root: RootInfo, t: Txt): String {
    return when (root.kind) {
        "sd" -> t.sdCard
        "usb" -> t.usbStorage
        else -> t.internalStorage
    }
}

// ---------------------------------------------------------------------------
// Layar Beranda
// ---------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    accessGranted: Boolean,
    roots: List<RootInfo>,
    favorites: Set<String>,
    onRequestAccess: () -> Unit,
    onOpenRoot: (String) -> Unit,
    onOpenFavorite: (String) -> Unit,
    onOpenCategory: () -> Unit
) {
    val t = txt()
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("FileKu", style = MaterialTheme.typography.headlineSmall)
                Text(t.slogan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                SectionCard(icon = Icons.Default.Storage, title = t.storage) {
                    roots.forEachIndexed { index, root ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onOpenRoot(root.path) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (root.kind) {
                                    "sd" -> Icons.Default.SdStorage
                                    "usb" -> Icons.Default.Usb
                                    else -> Icons.Default.Storage
                                },
                                null, tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(rootLabel(root, t), style = MaterialTheme.typography.titleSmall)
                                if (root.total > 0) {
                                    StorageBar(((root.total - root.free).toFloat() / root.total.toFloat()))
                                    Text(
                                        t.usedOf(FileOps.formatSize(root.total - root.free), FileOps.formatSize(root.total)) +
                                            " • " + t.free(FileOps.formatSize(root.free)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(icon = Icons.Default.Analytics, title = t.categories) {
                val cats = listOf(
                    Category.IMAGE to t.catImages,
                    Category.VIDEO to t.catVideos,
                    Category.AUDIO to t.catAudio,
                    Category.DOCUMENT to t.catDocuments,
                    Category.APP to t.catApps,
                    Category.ARCHIVE to t.catArchives,
                    Category.OTHER to t.catOther
                )
                cats.forEach { (cat, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onOpenCategory() }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(categoryIcon(cat, ""), null, tint = categoryColor(cat))
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            SectionCard(icon = Icons.Default.Star, title = t.favorites) {
                if (favorites.isEmpty()) {
                    Text(t.noFavorites, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    favorites.sorted().forEach { path ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onOpenFavorite(path) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(File(path).name.ifEmpty { path }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onOpenCategory, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Analytics, null)
                Spacer(Modifier.width(8.dp))
                Text(t.storageAnalysis)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar peramban folder
// ---------------------------------------------------------------------------

@Composable
private fun BrowseScreen(
    currentPath: String,
    currentRoot: RootInfo?,
    entries: List<FEntry>,
    loaded: Boolean,
    gridView: Boolean,
    selected: Set<String>,
    onNavigate: (List<String>) -> Unit,
    onOpenEntry: (FEntry) -> Unit,
    onLongPress: (FEntry) -> Unit,
    onGoUp: () -> Unit,
    canGoUp: Boolean
) {
    val t = txt()
    val rootPath = currentRoot?.path ?: currentPath
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    val crumbPaths = segments.scan(rootPath) { acc, seg -> "$acc/$seg" }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                TextButton(onClick = { onNavigate(listOf(rootPath)) }) {
                    Text(currentRoot?.let { rootLabel(it, t) } ?: rootPath)
                }
            }
            itemsIndexed(segments) { index, seg ->
                Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onNavigate(crumbPaths.drop(1).take(index + 1).let { listOf(rootPath) + it }) }) {
                    Text(seg)
                }
            }
        }
        if (canGoUp) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onGoUp() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowUpward, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("..", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                entries.isEmpty() -> Text(
                    if (File(currentPath).canRead()) t.emptyFolder else t.folderInaccessible,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                gridView -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        FileCell(
                            entry = entry,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = selected.contains(entry.path),
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) }
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = selected.contains(entry.path),
                            itemCount = null,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bilah aksi pilihan & papan klip
// ---------------------------------------------------------------------------

@Composable
private fun SelectionBar(
    single: Boolean,
    singleEntry: FEntry?,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit
) {
    val t = txt()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BarAction(Icons.Default.ContentCopy, t.copy, onCopy)
        BarAction(Icons.Default.ContentCut, t.cut, onCut)
        if (single) BarAction(Icons.Default.Share, t.share, onShare)
        if (single) BarAction(Icons.Default.Info, t.properties, onProperties)
        BarAction(Icons.Default.Archive, t.compress, onCompress)
        BarAction(Icons.Default.Delete, t.delete, onDelete)
    }
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ClipboardBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    val t = txt()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t.pasteHere(count), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPaste) { Text(t.paste) }
        IconButton(onClick = onClear) { Icon(Icons.Default.Close, t.cancel) }
    }
}

// ---------------------------------------------------------------------------
// Layar pencarian
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    rootPath: String,
    onBack: () -> Unit,
    onOpenEntry: (FEntry) -> Unit
) {
    val t = txt()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FEntry>>(emptyList()) }
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
        val showHidden = context.getSharedPreferences("fileku_prefs", Context.MODE_PRIVATE)
            .getBoolean("show_hidden", false)
        results = withContext(Dispatchers.IO) { FileOps.search(File(rootPath), q, showHidden) }
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.search) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(t.searchHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (searching) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.searching, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!searching && query.isNotBlank() && results.isEmpty()) {
                Text(
                    t.searchNoResults,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.path }) { entry ->
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

// ---------------------------------------------------------------------------
// Layar analisis penyimpanan
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisScreen(
    roots: List<RootInfo>,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FEntry) -> Unit
) {
    val t = txt()
    var results by remember { mutableStateOf<Map<String, AnalysisResult>>(emptyMap()) }
    var scanningPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(roots) {
        for (root in roots) {
            if (results.containsKey(root.path)) continue
            scanningPath = root.path
            val res = withContext(Dispatchers.IO) {
                try { FileOps.analyze(File(root.path)) } catch (e: Exception) { null }
            }
            if (res != null) results = results + (root.path to res)
        }
        scanningPath = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.storageAnalysis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            roots.forEach { root ->
                val res = results[root.path]
                item(key = "root-" + root.path) {
                    SectionCard(
                        icon = when (root.kind) {
                            "sd" -> Icons.Default.SdStorage
                            "usb" -> Icons.Default.Usb
                            else -> Icons.Default.Storage
                        },
                        title = rootLabel(root, t)
                    ) {
                        if (res == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(if (scanningPath == root.path) t.scanning else t.scanFailed)
                            }
                        } else {
                            Text(t.items(res.totalFiles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val totalCat = res.byCategory.values.sum().coerceAtLeast(1)
                            val labels = mapOf(
                                Category.IMAGE to t.catImages,
                                Category.VIDEO to t.catVideos,
                                Category.AUDIO to t.catAudio,
                                Category.DOCUMENT to t.catDocuments,
                                Category.APP to t.catApps,
                                Category.ARCHIVE to t.catArchives,
                                Category.OTHER to t.catOther
                            )
                            res.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, size) ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(categoryIcon(cat, ""), null, tint = categoryColor(cat), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(labels[cat] ?: cat.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(FileOps.formatSize(size), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    LinearProgressIndicator(
                                        progress = { (size.toFloat() / totalCat.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (res.largest.isNotEmpty()) {
                                Text(t.largestFiles, style = MaterialTheme.typography.titleSmall)
                                res.largest.forEach { entry ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { onOpenFile(entry) }
                                            .padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FileThumb(entry, 36)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(FileOps.formatSize(entry.size), style = MaterialTheme.typography.bodyMedium)
                                    }
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

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = txt()
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text(if (title == t.newFolder) t.create else t.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@Composable
private fun PropertiesDialog(entry: FEntry, onDismiss: () -> Unit) {
    val t = txt()
    var dirSize by remember { mutableStateOf<Long?>(if (entry.isDir) null else entry.size) }
    LaunchedEffect(entry.path) {
        if (entry.isDir) {
            dirSize = withContext(Dispatchers.IO) { FileOps.sizeOf(entry.file) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((if (entry.isDir) t.propFolder else t.propFile), style = MaterialTheme.typography.bodyMedium)
                Text(t.propPath + ": " + (entry.file.parent ?: ""), style = MaterialTheme.typography.bodySmall)
                Text(t.propSize + ": " + (dirSize?.let { FileOps.formatSize(it) } ?: "…"), style = MaterialTheme.typography.bodySmall)
                Text(
                    t.propModified + ": " + java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, appLocale()
                    ).format(java.util.Date(entry.modified)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } }
    )
}

@Composable
private fun SortDialog(
    sortKey: SortKey,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onPick: (SortKey, Boolean) -> Unit
) {
    val t = txt()
    var key by remember { mutableStateOf(sortKey) }
    var asc by remember { mutableStateOf(ascending) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.sortBy) },
        text = {
            Column {
                listOf(SortKey.NAME to t.sortName, SortKey.DATE to t.sortDate, SortKey.SIZE to t.sortSize).forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { key = k }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == k, onClick = { key = k })
                        Text(label)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = asc, onClick = { asc = true })
                    Text(t.sortAscending)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = false }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !asc, onClick = { asc = false })
                    Text(t.sortDescending)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(key, asc) }) { Text(t.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@Composable
private fun SettingsDialog(
    themeMode: String,
    language: String,
    showHidden: Boolean,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = txt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.settings) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.theme, style = MaterialTheme.typography.titleSmall)
                listOf("light" to t.themeLight, "dark" to t.themeDark, "system" to t.themeSystem).forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onTheme(mode) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { onTheme(mode) })
                        Text(label, Modifier.weight(1f))
                        if (themeMode == mode) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(t.language, style = MaterialTheme.typography.titleSmall)
                listOf("id" to "Bahasa Indonesia", "en" to "English").forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onLanguage(code) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == code, onClick = { onLanguage(code) })
                        Text(label, Modifier.weight(1f))
                        if (language == code) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t.showHidden, Modifier.weight(1f))
                    Switch(checked = showHidden, onCheckedChange = onHidden)
                }
                ListItem(
                    headlineContent = { Text(t.about) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onAbout() }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } }
    )
}
