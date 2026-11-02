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
import androidx.compose.material3.Surface
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

/** Layar tambahan yang menimpa layar utama (Beranda/peramban). */
private sealed interface Overlay {
    data class Search(val rootPath: String) : Overlay
    data class ImageView(val path: String) : Overlay
    data class TextView(val path: String) : Overlay
    data class CategoryView(val category: Category) : Overlay
}

private enum class ClipMode { COPY, CUT }

private sealed interface DialogKind {
    data object NewFolder : DialogKind
    data class Rename(val entry: FEntry) : DialogKind
    data class Delete(val entries: List<FEntry>) : DialogKind
    data class Properties(val entry: FEntry) : DialogKind
    data class Compress(val entries: List<FEntry>) : DialogKind
    data class ZipPassword(val entry: FEntry) : DialogKind
    data object Sort : DialogKind
    data object About : DialogKind
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SysFileApp() }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SysFileApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE) }

    var homeTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showFormats by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", false)) }
    var language by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
    AppLang.code = language
    val t = txt()

    var accessGranted by remember { mutableStateOf(context.hasFileAccess()) }
    var roots by remember { mutableStateOf<List<RootInfo>>(emptyList()) }
    val rootFs = remember { FileOps.rootFilesystem() }
    var rootActive by remember { mutableStateOf(false) }
    val allRoots = roots + rootFs
    var browseStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var entries by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    var entryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var entriesLoaded by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<Pair<List<String>, ClipMode>?>(null) }
    var working by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recentFiles by remember { mutableStateOf<List<FEntry>>(emptyList()) }

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
    val currentRoot = currentPath?.let { p -> allRoots.filter { p == it.path || it.path == "/" || p.startsWith(it.path + "/") }.maxByOrNull { it.path.length } }

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

    LaunchedEffect(currentRoot?.kind) {
        if (currentRoot?.kind == "root" && RootShell.state == RootShell.UNKNOWN) {
            working = true
            val granted = withContext(Dispatchers.IO) { RootShell.ensureRoot() }
            working = false
            rootActive = granted
            reloadKey++
        }
    }

    LaunchedEffect(currentPath, reloadKey, showHidden, sortKey, sortAsc, accessGranted) {
        val path = currentPath
        if (path == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        entriesLoaded = false
        val loaded = withContext(Dispatchers.IO) {
            if (RootShell.shouldUseShell(path)) {
                RootShell.listDir(File(path), showHidden, sortKey, sortAsc)
                    ?: FileOps.listDir(File(path), showHidden, sortKey, sortAsc)
            } else {
                FileOps.listDir(File(path), showHidden, sortKey, sortAsc)
            }
        }
        entries = loaded ?: emptyList()
        entryCounts = if (loaded == null) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) {
                loaded.filter { it.isDir }.associate { it.path to FileOps.childCount(it.file) }
            }
        }
        entriesLoaded = true
        selected = emptySet()
    }

    LaunchedEffect(accessGranted, roots, showHidden, reloadKey) {
        if (!accessGranted || roots.isEmpty()) {
            recentFiles = emptyList()
            return@LaunchedEffect
        }
        recentFiles = withContext(Dispatchers.IO) {
            FileOps.recentFiles(roots.map { File(it.path) }, showHidden)
        }
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
        if (!f.canRead() && RootShell.state == RootShell.GRANTED) {
            working = true
            scope.launch {
                val staged = withContext(Dispatchers.IO) { RootShell.stageToCache(f, context.cacheDir) }
                working = false
                if (staged != null) openEntry(FileOps.entryOf(staged)) else notify(t.opFailed)
            }
            return
        }
        when {
            entry.category == Category.IMAGE -> overlay = Overlay.ImageView(entry.path)
            FileOps.isTextFile(f) && f.length() <= 2_000_000 -> overlay = Overlay.TextView(entry.path)
            entry.category == Category.ARCHIVE -> {
                val kind = ArchiveOps.kindOf(f.name)
                when {
                    kind == null -> openExternal(f)
                    kind == "zip" -> {
                        working = true
                        scope.launch {
                            val encrypted = withContext(Dispatchers.IO) { ArchiveOps.isZipEncrypted(f) }
                            working = false
                            if (encrypted) {
                                dialog = DialogKind.ZipPassword(entry)
                            } else {
                                runOp(t.extractDone) { ArchiveOps.extract(f, f.parentFile ?: f) }
                            }
                        }
                    }
                    else -> runOp(t.extractDone) { ArchiveOps.extract(f, f.parentFile ?: f) }
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

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && overlay != null) { overlay = null }
    BackHandler(enabled = !showSettings && overlay == null && browseStack.isEmpty() && homeTab != 0) { homeTab = 0 }
    BackHandler(enabled = showFormats) { showFormats = false }
    BackHandler(enabled = overlay == null && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = overlay == null && selected.isEmpty() && browseStack.isNotEmpty()) {
        browseStack = browseStack.dropLast(1)
    }

    SysFileTheme(themeMode, dynamicColor) {
        if (showFormats) {
            FormatsScreen(onBack = { showFormats = false })
        } else if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                language = language,
                showHidden = showHidden,
                dynamicColor = dynamicColor,
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
                onDynamicColor = { v ->
                    dynamicColor = v
                    prefs.edit().putBoolean("dynamic_color", v).apply()
                },
                onFormats = { showFormats = true },
                onAbout = { dialog = DialogKind.About },
                onBack = { showSettings = false }
            )
        } else when (val ov = overlay) {
            is Overlay.ImageView -> ImageViewerScreen(
                path = ov.path,
                onBack = { overlay = null },
                onShare = { shareFile(File(ov.path)) },
                onInfo = { dialog = DialogKind.Properties(FileOps.entryOf(File(ov.path))) },
                onDelete = { dialog = DialogKind.Delete(listOf(FileOps.entryOf(File(ov.path)))) }
            )
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
            is Overlay.CategoryView -> CategoryScreen(
                category = ov.category,
                roots = roots,
                showHidden = showHidden,
                accessGranted = accessGranted,
                onBack = { overlay = null },
                onOpenEntry = { entry ->
                    entry.parentPath()?.let { parent ->
                        if (browseStack.lastOrNull() != parent) browseStack = listOf(parent)
                    }
                    openEntry(entry)
                }
            )
            null -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        Column {
                        if (currentPath == null) {
                            TopAppBar(
                                title = { Text("SysFile") },
                                actions = {
                                    IconButton(onClick = { showSettings = true }) {
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
                        if (currentPath != null && selected.isEmpty()) {
                            BreadcrumbBar(
                                currentPath = currentPath,
                                currentRoot = currentRoot,
                                onNavigate = { browseStack = it }
                            )
                        }
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
                        } else if (currentPath == null) {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PillNavBar(homeTab = homeTab, onSelect = { homeTab = it })
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AnimatedContent(
                            targetState = currentPath,
                            transitionSpec = {
                                val fromDepth = initialState?.count { it == '/' } ?: -1
                                val toDepth = targetState?.count { it == '/' } ?: -1
                                if (toDepth >= fromDepth) {
                                    (slideInHorizontally { w -> w / 3 } + fadeIn()) togetherWith
                                        (slideOutHorizontally { w -> -w / 3 } + fadeOut())
                                } else {
                                    (slideInHorizontally { w -> -w / 3 } + fadeIn()) togetherWith
                                        (slideOutHorizontally { w -> w / 3 } + fadeOut())
                                }
                            },
                            label = "folderAnim"
                        ) { animatedPath ->
                            if (animatedPath == null) {
                                AnimatedContent(
                                    targetState = homeTab,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "tabAnim"
                                ) { tab ->
                                    when (tab) {
                                        1 -> RecentScreen(
                                            files = recentFiles,
                                            accessGranted = accessGranted,
                                            onOpenEntry = { entry ->
                                                if (entry.isDir) browseStack = listOf(entry.path) else openEntry(entry)
                                            }
                                        )
                                        2 -> AnalysisScreen(
                                            roots = roots,
                                            onOpenFolder = { path -> homeTab = 0; browseStack = listOf(path) },
                                            onOpenFile = { entry -> homeTab = 0; openEntry(entry) }
                                        )
                                        else -> HomeScreen(
                                            accessGranted = accessGranted,
                                            roots = allRoots,
                                            rootActive = rootActive,
                                            favorites = favorites,
                                            onRequestAccess = { requestAccess() },
                                            onOpenRoot = { browseStack = listOf(it) },
                                            onOpenFavorite = { browseStack = listOf(it) },
                                            onOpenCategory = { cat -> overlay = Overlay.CategoryView(cat) }
                                        )
                                    }
                                }
                            } else {
                                BrowseScreen(
                                currentPath = animatedPath,
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
                                    val parent = File(animatedPath).parent
                                    if (parent != null && currentRoot != null && parent.startsWith(currentRoot.path)) {
                                        browseStack = browseStack.dropLast(1) + parent
                                    } else {
                                        browseStack = browseStack.dropLast(1)
                                    }
                                },
                                canGoUp = currentRoot != null && animatedPath != currentRoot.path,
                                onNewFolder = { dialog = DialogKind.NewFolder },
                                sortLabel = when (sortKey) {
                                    SortKey.NAME -> t.sortName
                                    SortKey.DATE -> t.sortDate
                                    SortKey.SIZE -> t.sortSize
                                },
                                onSortClick = { dialog = DialogKind.Sort },
                                onToggleView = {
                                    gridView = !gridView
                                    prefs.edit().putBoolean("grid_view", gridView).apply()
                                },
                                counts = entryCounts
                                )
                            }
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
                        LoadingIndicator(Modifier.size(52.dp))
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
            is DialogKind.Compress -> CompressDialog(
                onDismiss = { dialog = null },
                onConfirm = { name, kind, password ->
                    dialog = null
                    currentPath?.let { File(it) }?.let { dir ->
                        runOp(t.zipDone) {
                            ArchiveOps.create(d.entries.map { it.file }, dir, name, kind, password) != null
                        }
                    }
                }
            )
            is DialogKind.ZipPassword -> ZipPasswordDialog(
                onDismiss = { dialog = null },
                onConfirm = { password ->
                    dialog = null
                    val f = d.entry.file
                    runOp(t.extractDone) {
                        ArchiveOps.extractEncryptedZip(f, f.parentFile ?: f, password)
                    }
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
                        val viewing = overlay as? Overlay.ImageView
                        if (viewing != null && d.entries.any { it.path == viewing.path }) {
                            overlay = null
                        }
                    }) { Text(t.delete) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(t.cancel) } }
            )
            is DialogKind.Properties -> PropertiesDialog(entry = d.entry, onDismiss = { dialog = null }, onNotify = { notify(it) })
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
            is DialogKind.About -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("SysFile") },
                text = { Text(t.slogan + "\n\n" + t.version("1.0.0")) },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(t.close) } }
            )
            null -> {}
        }
    }
}

private fun FEntry.parentPath(): String? = File(path).parent
