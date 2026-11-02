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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", false)) }
    var language by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
    AppLang.code = language
    val t = txt()

    var accessGranted by remember { mutableStateOf(context.hasFileAccess()) }
    var roots by remember { mutableStateOf<List<RootInfo>>(emptyList()) }
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

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && overlay != null) { overlay = null }
    BackHandler(enabled = !showSettings && overlay == null && browseStack.isEmpty() && homeTab == 1) { homeTab = 0 }
    BackHandler(enabled = overlay == null && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = overlay == null && selected.isEmpty() && browseStack.isNotEmpty()) {
        browseStack = browseStack.dropLast(1)
    }

    SysFileTheme(themeMode, dynamicColor) {
        if (showSettings) {
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
                                title = { Text("SysFileManager") },
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
                                    if (tab == 1) {
                                        AnalysisScreen(
                                            roots = roots,
                                            onOpenFolder = { path -> homeTab = 0; browseStack = listOf(path) },
                                            onOpenFile = { entry -> homeTab = 0; openEntry(entry) }
                                        )
                                    } else {
                                        HomeScreen(
                                            accessGranted = accessGranted,
                                            roots = roots,
                                            favorites = favorites,
                                            recentFiles = recentFiles,
                                            onRequestAccess = { requestAccess() },
                                            onOpenRoot = { browseStack = listOf(it) },
                                            onOpenFavorite = { browseStack = listOf(it) },
                                            onOpenCategory = { cat -> overlay = Overlay.CategoryView(cat) },
                                            onOpenRecent = { entry ->
                                                if (entry.isDir) browseStack = listOf(entry.path) else openEntry(entry)
                                            }
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
                title = { Text("SysFileManager") },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeScreen(
    accessGranted: Boolean,
    roots: List<RootInfo>,
    favorites: Set<String>,
    recentFiles: List<FEntry>,
    onRequestAccess: () -> Unit,
    onOpenRoot: (String) -> Unit,
    onOpenFavorite: (String) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenRecent: (FEntry) -> Unit
) {
    val t = txt()
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("SysFileManager", style = MaterialTheme.typography.headlineSmall)
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 4..10 -> t.greetingMorning
                    in 11..14 -> t.greetingAfternoon
                    in 15..18 -> t.greetingEvening
                    else -> t.greetingNight
                }
                Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    roots.forEach { root ->
                        if (root.kind != "internal" && root.total > 0) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onOpenRoot(root.path) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(rootLabel(root, t), style = MaterialTheme.typography.titleSmall)
                                    LinearWavyProgressIndicator(
                                        progress = { ((root.total - root.free).toFloat() / root.total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        t.free(FileOps.formatSize(root.free)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (root.total > 0) {
                            val (statusText, statusDot) = storageStatus(root.free.toFloat() / root.total.toFloat())
                            GradientStorageCard(
                                label = rootLabel(root, t),
                                usedText = FileOps.formatSize(root.total - root.free),
                                detailText = t.ofTotal(FileOps.formatSize(root.total)) + " · " + t.free(FileOps.formatSize(root.free)),
                                percentText = ((root.total - root.free) * 100 / root.total).toString() + "%",
                                usedWord = t.usedWord,
                                statusText = statusText,
                                statusDot = statusDot,
                                fraction = (root.total - root.free).toFloat() / root.total.toFloat(),
                                onClick = { onOpenRoot(root.path) }
                            )
                        } else {
                            SectionCard(icon = Icons.Default.Storage, title = rootLabel(root, t)) {
                                Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (accessGranted && recentFiles.isNotEmpty()) {
            item {
                SectionCard(icon = Icons.Default.History, title = t.recentFiles) {
                    recentFiles.forEach { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = false,
                            isSelected = false,
                            itemCount = null,
                            onClick = { onOpenRecent(entry) },
                            onLongClick = {}
                        )
                    }
                }
            }
        }
        item {
            SectionCard(icon = Icons.Default.Analytics, title = t.categories) {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowCats.forEach { (cat, label) ->
                            val color = categoryColor(cat)
                            Column(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(color.copy(alpha = 0.13f))
                                    .clickable { onOpenCategory(cat) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape).background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(categoryIcon(cat, ""), null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.favorites, style = MaterialTheme.typography.titleMedium)
                }
                if (favorites.isEmpty()) {
                    Text(t.noFavorites, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favorites.sorted(), key = { it }) { path ->
                            FilterChip(
                                selected = false,
                                onClick = { onOpenFavorite(path) },
                                label = { Text(File(path).name.ifEmpty { path }, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) }
                            )
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

/** Bilah tab bawah gaya kapsul melayang: item aktif menjadi pil terisi di dalam kapsul. */
@Composable
private fun PillNavBar(homeTab: Int, onSelect: (Int) -> Unit) {
    val t = txt()
    val items = listOf(
        Triple(0, Icons.Default.Home, t.home),
        Triple(1, Icons.Default.Analytics, t.analysisShort)
    )
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (index, icon, label) ->
                val active = homeTab == index
                val fg = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.clip(RoundedCornerShape(percent = 50))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}

/** Baris chip jalur folder, kini menempel di bawah bilah judul atas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    currentRoot: RootInfo?,
    onNavigate: (List<String>) -> Unit
) {
    val t = txt()
    val rootPath = currentRoot?.path ?: currentPath
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    val crumbPaths = segments.scan(rootPath) { acc, seg -> "$acc/$seg" }
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = segments.isEmpty(),
                onClick = { onNavigate(listOf(rootPath)) },
                label = { Text(currentRoot?.let { rootLabel(it, t) } ?: rootPath) }
            )
        }
        itemsIndexed(segments) { index, seg ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(
                selected = index == segments.lastIndex,
                onClick = { onNavigate(crumbPaths.drop(1).take(index + 1).let { listOf(rootPath) + it }) },
                label = { Text(seg) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    canGoUp: Boolean,
    onNewFolder: () -> Unit,
    sortLabel: String,
    onSortClick: () -> Unit,
    onToggleView: () -> Unit,
    counts: Map<String, Int>
) {
    val t = txt()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onSortClick,
                label = { Text(t.sortBy + ": " + sortLabel) },
                leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = gridView,
                onClick = onToggleView,
                label = { Text(if (gridView) t.gridView else t.listView) },
                leadingIcon = {
                    Icon(if (gridView) Icons.Default.GridView else Icons.Default.ViewList, null, Modifier.size(18.dp))
                }
            )
            Spacer(Modifier.weight(1f))
            if (currentRoot != null && currentRoot.total > 0) {
                Text(
                    rootLabel(currentRoot, t) + " · " + t.free(FileOps.formatSize(currentRoot.free)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
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
                !loaded -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                entries.isEmpty() -> Text(
                    if (File(currentPath).canRead()) t.emptyFolder else t.folderInaccessible,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                gridView -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            itemCount = counts[entry.path],
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) }
                        )
                    }
                }
            }
            if (selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNewFolder,
                    icon = { Icon(Icons.Default.CreateNewFolder, null) },
                    text = { Text(t.newFolder) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bilah aksi pilihan & papan klip
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, t.copy) }
            IconButton(onClick = onCut) { Icon(Icons.Default.ContentCut, t.cut) }
            if (single) IconButton(onClick = onShare) { Icon(Icons.Default.Share, t.share) }
            if (single) IconButton(onClick = onProperties) { Icon(Icons.Default.Info, t.properties) }
            IconButton(onClick = onCompress) { Icon(Icons.Default.Archive, t.compress) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, t.delete) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    val t = txt()
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            Text(
                t.pasteHere(count),
                Modifier.padding(start = 14.dp, end = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onPaste) { Text(t.paste) }
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, t.cancel) }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar pencarian
// ---------------------------------------------------------------------------

/** Layar satu kategori: semua berkas kategori itu lintas volume, terbaru dulu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryScreen(
    category: Category,
    roots: List<RootInfo>,
    showHidden: Boolean,
    accessGranted: Boolean,
    onBack: () -> Unit,
    onOpenEntry: (FEntry) -> Unit
) {
    val t = txt()
    var files by remember { mutableStateOf<List<FEntry>?>(null) }
    LaunchedEffect(category, accessGranted) {
        files = null
        files = if (accessGranted) {
            withContext(Dispatchers.IO) {
                FileOps.categoryFiles(roots.map { File(it.path) }, category, showHidden)
            }
        } else {
            emptyList()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryLabel(category, t)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val loaded = files
            when {
                loaded == null -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator()
                        Text(t.scanning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                loaded.isEmpty() -> {
                    Text(
                        t.categoryEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(loaded, key = { it.path }) { entry ->
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
        val showHidden = context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE)
            .getBoolean("show_hidden", false)
        results = withContext(Dispatchers.IO) { FileOps.search(File(rootPath), q, showHidden) }
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
private fun AnalysisScreen(
    roots: List<RootInfo>,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (FEntry) -> Unit
) {
    val t = txt()
    var results by remember { mutableStateOf<Map<String, AnalysisResult>>(emptyMap()) }
    var scanningPath by remember { mutableStateOf<String?>(null) }
    var selectedRootPath by remember { mutableStateOf<String?>(null) }
    var expandedLargest by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                                LoadingIndicator(Modifier.size(30.dp))
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
                                    LinearWavyProgressIndicator(
                                        progress = { (size.toFloat() / totalCat.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (res.largest.isNotEmpty()) {
                                Text(t.largestFiles, style = MaterialTheme.typography.titleSmall)
                                val shownLargest = if (expandedLargest.contains(root.path)) res.largest else res.largest.take(5)
                                shownLargest.forEach { entry ->
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
private fun PropertiesDialog(entry: FEntry, onDismiss: () -> Unit, onNotify: (String) -> Unit) {
    val t = txt()
    val clipboard = LocalClipboardManager.current
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
            Column {
                ListItem(
                    headlineContent = { Text(if (entry.isDir) t.propFolder else t.propFile) },
                    leadingContent = {
                        Icon(if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name), null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propPath) },
                    supportingContent = { Text(entry.path) },
                    leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                    trailingContent = {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(entry.path))
                            onNotify(t.copiedDone)
                        }) {
                            Icon(Icons.Default.ContentCopy, t.copy)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propSize) },
                    supportingContent = { Text(dirSize?.let { FileOps.formatSize(it) } ?: "…") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propModified) },
                    supportingContent = {
                        Text(
                            java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, appLocale()
                            ).format(java.util.Date(entry.modified))
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Schedule, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    themeMode: String,
    language: String,
    showHidden: Boolean,
    dynamicColor: Boolean,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val t = txt()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.settings) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(icon = Icons.Default.Palette, title = t.appearance) {
                    Text(t.theme, style = MaterialTheme.typography.titleSmall)
                    val themeOptions = listOf("light" to t.themeLight, "dark" to t.themeDark, "system" to t.themeSystem)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                    Text(t.language, style = MaterialTheme.typography.titleSmall)
                    val langOptions = listOf("id" to "Indonesia", "en" to "English")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        langOptions.forEachIndexed { index, (code, label) ->
                            SegmentedButton(
                                selected = language == code,
                                onClick = { onLanguage(code) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = langOptions.size),
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.dynamicColor)
                            Text(
                                t.dynamicColorDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }
            item {
                SectionCard(icon = Icons.Default.Folder, title = t.fileSection) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t.showHidden, Modifier.weight(1f))
                        Switch(checked = showHidden, onCheckedChange = onHidden)
                    }
                }
            }
            item {
                SectionCard(icon = Icons.Default.Info, title = t.otherSection) {
                    ListItem(
                        headlineContent = { Text(t.about) },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onAbout() }
                    )
                }
            }
        }
    }
}
