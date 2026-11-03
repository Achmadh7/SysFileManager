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
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.NoteAdd
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Layar tambahan yang menimpa layar utama (Beranda/peramban). */
private sealed interface Overlay {
    data class Search(val rootPath: String) : Overlay
    data class ImageView(val path: String) : Overlay
    data class VideoView(val path: String) : Overlay
    data class TextView(val path: String) : Overlay
    data class CategoryView(val category: Category) : Overlay
    data class ArchiveView(val path: String) : Overlay
    data class PayloadView(val path: String) : Overlay
}

private enum class ClipMode { COPY, CUT }

private sealed interface DialogKind {
    data object NewFolder : DialogKind
    data object NewFile : DialogKind
    data class Rename(val entry: FEntry) : DialogKind
    data class Delete(val entries: List<FEntry>) : DialogKind
    data class Properties(val entry: FEntry) : DialogKind
    data class Compress(val entries: List<FEntry>) : DialogKind
    data class ZipPassword(val entry: FEntry) : DialogKind
    data class ApkActions(val entry: FEntry) : DialogKind
    data class ArchiveActions(val entry: FEntry) : DialogKind
    data class PickExtractFolder(val entry: FEntry) : DialogKind
    data class ArchiveContents(val entry: FEntry, val items: List<Pair<String, Long>>) : DialogKind
    data object Sort : DialogKind
}

class MainActivity : FragmentActivity() {
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
    var showTrash by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
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
    var overlayReturn by remember { mutableStateOf<Overlay?>(null) }
    var entries by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    var entryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var entriesLoaded by remember { mutableStateOf(true) }
    // Cache sesi: folder yang pernah dibuka tampil seketika, lalu dimuat ulang di latar.
    val listingCache = remember { mutableMapOf<String, List<FEntry>>() }
    val countCache = remember { mutableMapOf<String, Int>() }
    var reloadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<Pair<List<String>, ClipMode>?>(null) }
    var working by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var lockedFolders by remember {
        mutableStateOf(
            prefs.getStringSet("locked_folders", emptySet())?.filter { File(it).isDirectory }?.toSet() ?: emptySet()
        )
    }
    var unlockedSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentFiles by remember { mutableStateOf<List<FEntry>>(emptyList()) }

    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var showRootEntry by remember { mutableStateOf(prefs.getBoolean("root_entry", false)) }
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

    /** Minta verifikasi biometrik/PIN untuk membuka folder terkunci [path]. */
    fun requestUnlock(path: String, onGranted: () -> Unit) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            notify(t.unlockFailed)
            return
        }
        BiometricGate.authenticate(
            activity = activity,
            title = t.unlockTitle,
            subtitle = t.unlockPrompt(File(path).name),
            onSuccess = {
                unlockedSession = unlockedSession + path
                onGranted()
            },
            onError = { notify(t.unlockFailed) }
        )
    }

    /** Masuk ke folder dengan gerbang kunci: folder terkunci yang belum dibuka pada
     *  sesi ini harus lolos biometrik terlebih dahulu, baru [land] dijalankan. */
    fun openFolderGuarded(path: String, land: () -> Unit) {
        if (path in lockedFolders && path !in unlockedSession) {
            requestUnlock(path, land)
        } else {
            land()
        }
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
        val cacheKey = path + "|" + showHidden + "|" + sortKey + "|" + sortAsc
        val cached = listingCache[cacheKey]
        if (cached != null) {
            // Tampilkan hasil sebelumnya seketika; data segar menyusul di bawah.
            entries = cached
            entriesLoaded = true
        } else {
            entriesLoaded = false
        }
        val loaded = withContext(Dispatchers.IO) {
            if (RootShell.shouldUseShell(path)) {
                RootShell.listDir(File(path), showHidden, sortKey, sortAsc)
                    ?: FileOps.listDir(File(path), showHidden, sortKey, sortAsc)
            } else {
                FileOps.listDir(File(path), showHidden, sortKey, sortAsc)
            }
        }
        entries = loaded ?: emptyList()
        if (loaded != null) {
            listingCache[cacheKey] = loaded
            if (listingCache.size > 16) listingCache.remove(listingCache.keys.first())
        } else {
            listingCache.remove(cacheKey)
        }
        entryCounts = if (loaded == null) {
            emptyMap()
        } else {
            val dirs = loaded.filter { it.isDir }
            val missing = dirs.filter { it.path !in countCache }
            if (missing.isNotEmpty()) {
                // Hitungan isi folder dihitung paralel, bukan satu per satu.
                val fresh = withContext(Dispatchers.IO) {
                    coroutineScope {
                        missing.map { entry ->
                            async { entry.path to FileOps.childCount(entry.file) }
                        }.awaitAll()
                    }
                }
                if (countCache.size > 512) countCache.clear()
                fresh.forEach { (p, c) -> countCache[p] = c }
            }
            dirs.associate { it.path to (countCache[it.path] ?: 0) }
        }
        entriesLoaded = true
        selected = emptySet()
    }

    LaunchedEffect(accessGranted, roots, showHidden, reloadKey, lockedFolders) {
        if (!accessGranted || roots.isEmpty()) {
            recentFiles = emptyList()
            return@LaunchedEffect
        }
        recentFiles = withContext(Dispatchers.IO) {
            FileOps.recentFiles(roots.map { File(it.path) }, showHidden, lockedFolders, limit = 60)
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
            openFolderGuarded(entry.path) { browseStack = browseStack + entry.path }
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
        if (FileOps.extOf(f.name) == "bin") {
            // Semua berkas .bin: bila benar payload, buka layar isi (daftar
            // partisi) dulu — jangan langsung mengekstrak. Bila ternyata bukan
            // payload, buka seperti berkas biasa.
            working = true
            scope.launch {
                val listing = withContext(Dispatchers.IO) { PayloadOps.listPartitions(f) }
                working = false
                when {
                    listing.error == "notpayload" -> openExternal(f)
                    listing.error == "unsupported" -> notify(t.payloadUnsupported)
                    listing.error != null -> notify(t.payloadInvalid)
                    else -> overlay = Overlay.PayloadView(entry.path)
                }
            }
            return
        }
        if (entry.category == Category.APP) {
            val ext = FileOps.extOf(f.name)
            if (ext == "apk" || ext == "apks" || ext == "xapk" || ext == "aab") {
                dialog = DialogKind.ApkActions(entry)
                return
            }
        }
        when {
            entry.category == Category.IMAGE -> overlay = Overlay.ImageView(entry.path)
            entry.category == Category.VIDEO -> overlay = Overlay.VideoView(entry.path)
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
                                dialog = DialogKind.ArchiveActions(entry)
                            }
                        }
                    }
                    else -> overlay = Overlay.ArchiveView(entry.path)
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
    BackHandler(enabled = !showSettings && overlay != null) { overlay = overlayReturn; overlayReturn = null }
    BackHandler(enabled = !showSettings && overlay == null && browseStack.isEmpty() && homeTab != 0) { homeTab = 0 }
    BackHandler(enabled = showFormats) { showFormats = false }
    BackHandler(enabled = showTrash) { showTrash = false }
    BackHandler(enabled = showApps) { showApps = false }
    BackHandler(enabled = showAbout) { showAbout = false }
    BackHandler(enabled = overlay == null && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = overlay == null && selected.isEmpty() && browseStack.isNotEmpty()) {
        browseStack = browseStack.dropLast(1)
    }

    SysFileTheme(themeMode, dynamicColor) {
        if (showTrash) {
            TrashScreen(onBack = { showTrash = false }, notify = { notify(it) })
        } else if (showFormats) {
            FormatsScreen(onBack = { showFormats = false })
        } else if (showApps) {
            AppsScreen(notify = { notify(it) }, onBack = { showApps = false })
        } else if (showAbout) {
            AboutScreen(
                onBack = { showAbout = false },
                onFormats = { showFormats = true },
                onOpenAnalysis = { showAbout = false; showSettings = false; homeTab = 2 }
            )
        } else if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                language = language,
                showHidden = showHidden,
                dynamicColor = dynamicColor,
                showRootEntry = showRootEntry,
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
                onRootEntry = { v ->
                    showRootEntry = v
                    prefs.edit().putBoolean("root_entry", v).apply()
                },
                onFormats = { showFormats = true },
                onApps = { showApps = true },
                onAbout = { showAbout = true },
                onBack = { showSettings = false }
            )
        } else {
            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    if (initialState is Overlay.CategoryView || targetState is Overlay.CategoryView ||
                        initialState is Overlay.ArchiveView || targetState is Overlay.ArchiveView ||
                        initialState is Overlay.PayloadView || targetState is Overlay.PayloadView) {
                        fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "overlayAnim"
            ) { ov ->
                when (ov) {
                    is Overlay.ImageView -> ImageViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onShare = { shareFile(File(ov.path)) },
                        onInfo = { dialog = DialogKind.Properties(FileOps.entryOf(File(ov.path))) },
                        onDelete = { dialog = DialogKind.Delete(listOf(FileOps.entryOf(File(ov.path)))) }
                    )
                    is Overlay.TextView -> TextViewerScreen(ov.path, onBack = { overlay = overlayReturn; overlayReturn = null }, notify = { notify(it) })
                    is Overlay.VideoView -> VideoViewerScreen(ov.path, onBack = { overlay = overlayReturn; overlayReturn = null }, notify = { notify(it) })
                    is Overlay.Search -> SearchScreen(
                        rootPath = ov.rootPath,
                        onBack = { overlay = null; overlayReturn = null },
                        onOpenEntry = { entry ->
                            overlay = null
                            overlayReturn = null
                            if (entry.isDir) {
                                openFolderGuarded(entry.path) { browseStack = listOf(entry.path) }
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
                        onBack = { overlay = null; overlayReturn = null },
                        onOpenEntry = { entry ->
                            val before = overlay
                            openEntry(entry)
                            overlayReturn = if (overlay !== before) ov else null
                        }
                    )
                    is Overlay.ArchiveView -> ArchiveScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed) },
                        onExtractAll = {
                            val archiveFile = File(ov.path)
                            overlay = null
                            overlayReturn = null
                            runOp(t.extractDone) { ArchiveOps.extract(archiveFile, archiveFile.parentFile ?: archiveFile) }
                        },
                        onOpenFile = { itemName ->
                            val archiveFile = File(ov.path)
                            working = true
                            scope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    ArchiveOps.extractSingle(archiveFile, itemName, archiveFile.parentFile ?: archiveFile)
                                }
                                working = false
                                if (out != null) {
                                    reloadKey++
                                    openEntry(FileOps.entryOf(out))
                                } else {
                                    notify(t.opFailed)
                                }
                            }
                        }
                    )
                    is Overlay.PayloadView -> PayloadScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed) },
                        onUnsupported = { overlay = overlayReturn; overlayReturn = null; notify(t.payloadUnsupported) },
                        onExtractAll = {
                            val payloadFile = File(ov.path)
                            overlay = null
                            overlayReturn = null
                            working = true
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    PayloadOps.extract(payloadFile, payloadFile.parentFile ?: payloadFile)
                                }
                                working = false
                                when {
                                    outcome.error == "unsupported" -> notify(t.payloadUnsupported)
                                    outcome.error != null -> notify(t.payloadInvalid)
                                    else -> {
                                        notify(t.payloadDone(outcome.partitions))
                                        reloadKey++
                                    }
                                }
                            }
                        },
                        onOpenPartition = { name ->
                            val payloadFile = File(ov.path)
                            working = true
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    PayloadOps.extractOne(payloadFile, name, payloadFile.parentFile ?: payloadFile)
                                }
                                working = false
                                when {
                                    outcome.error == "unsupported" -> notify(t.payloadUnsupported)
                                    outcome.error != null -> notify(t.opFailed)
                                    outcome.file != null -> {
                                        overlay = null
                                        openEntry(FileOps.entryOf(outcome.file))
                                    }
                                }
                            }
                        }
                    )
                    null -> {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbar) },
                            topBar = {
                                Column {
                                if (currentPath == null) {
                                    TopAppBar(
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                                                        .background(Color(0xFF2E5A88)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Folder, null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(25.dp)
                                                    )
                                                    Icon(
                                                        Icons.Default.ArrowUpward, null,
                                                        tint = Color(0xFF2E5A88),
                                                        modifier = Modifier.size(13.dp).offset(y = 3.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text("SysFile")
                                                    Text(
                                                        t.appTagline,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        actions = {
                                            val searchRoot = roots.firstOrNull { it.kind == "internal" }?.path
                                                ?: roots.firstOrNull()?.path
                                            if (searchRoot != null) {
                                                IconButton(onClick = { overlay = Overlay.Search(searchRoot) }) {
                                                    Icon(Icons.Default.Search, t.search)
                                                }
                                            }
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
                                            Column {
                                                Text(
                                                    if (currentRoot != null && currentPath == currentRoot.path) {
                                                        rootLabel(currentRoot, t)
                                                    } else {
                                                        File(currentPath).name
                                                    },
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    if (currentRoot != null && currentPath == currentRoot.path) {
                                                        currentPath
                                                    } else {
                                                        currentRoot?.let { rootLabel(it, t) } ?: ""
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { browseStack = browseStack.dropLast(1) }) {
                                                Icon(Icons.Default.ArrowBack, t.home)
                                            }
                                        },
                                        actions = {
                                            val isFav = favorites.contains(currentPath)
                                            Box {
                                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (isFav) t.removeFavorite else t.addFavorite) },
                                                        leadingIcon = {
                                                            Icon(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, null)
                                                        },
                                                        onClick = {
                                                            menuOpen = false
                                                            val updated = if (isFav) favorites - currentPath else favorites + currentPath
                                                            favorites = updated
                                                            prefs.edit().putStringSet("favorites", updated).apply()
                                                            notify(if (isFav) t.removeFavorite else t.addFavorite)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(t.home) },
                                                        leadingIcon = { Icon(Icons.Default.Home, null) },
                                                        onClick = { menuOpen = false; browseStack = emptyList() }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(t.newFolder) },
                                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                                        onClick = { menuOpen = false; dialog = DialogKind.NewFolder }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(t.newFile) },
                                                        leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                                                        onClick = { menuOpen = false; dialog = DialogKind.NewFile }
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
                                    BrowseHeaderBar(
                                        currentPath = currentPath,
                                        currentRoot = currentRoot,
                                        gridView = gridView,
                                        onSearch = { overlay = Overlay.Search(currentPath) },
                                        onSortClick = { dialog = DialogKind.Sort },
                                        onToggleView = {
                                            gridView = !gridView
                                            prefs.edit().putBoolean("grid_view", gridView).apply()
                                        }
                                    )
                                }
                                }
                            },
                            bottomBar = {
                                if (selected.isNotEmpty() && currentPath != null) {
                                    SelectionBar(
                                        single = selected.size == 1,
                                        singleEntry = entries.firstOrNull { it.path == selected.first() },
                                        canExtract = entries.any { selected.contains(it.path) && !it.isDir && ArchiveOps.kindOf(it.name) != null },
                                        onCopy = { clipboard = selected.toList() to ClipMode.COPY; selected = emptySet() },
                                        onCut = { clipboard = selected.toList() to ClipMode.CUT; selected = emptySet() },
                                        onDelete = { dialog = DialogKind.Delete(entries.filter { selected.contains(it.path) }) },
                                        onShare = { entries.firstOrNull { it.path == selected.first() }?.let { shareFile(it.file) } },
                                        onRename = { entries.firstOrNull { it.path == selected.first() }?.let { dialog = DialogKind.Rename(it) } },
                                        onCompress = { dialog = DialogKind.Compress(entries.filter { selected.contains(it.path) }) },
                                        onProperties = { entries.firstOrNull { it.path == selected.first() }?.let { dialog = DialogKind.Properties(it) } },
                                        onExtract = {
                                            val targets = entries.filter {
                                                selected.contains(it.path) && !it.isDir && ArchiveOps.kindOf(it.name) != null
                                            }
                                            val only = targets.singleOrNull()
                                            if (only != null && ArchiveOps.kindOf(only.name) == "zip") {
                                                working = true
                                                scope.launch {
                                                    val enc = withContext(Dispatchers.IO) { ArchiveOps.isZipEncrypted(only.file) }
                                                    working = false
                                                    if (enc) {
                                                        dialog = DialogKind.ZipPassword(only)
                                                    } else {
                                                        runOp(t.extractDone) {
                                                            ArchiveOps.extract(only.file, only.file.parentFile ?: only.file)
                                                        }
                                                    }
                                                }
                                            } else if (targets.isNotEmpty()) {
                                                runOp(t.extractDone) {
                                                    targets.count {
                                                        ArchiveOps.extract(it.file, it.file.parentFile ?: it.file)
                                                    } > 0
                                                }
                                            }
                                        },
                                        onSelectAll = { selected = entries.map { it.path }.toSet() },
                                        singleIsDir = entries.firstOrNull { it.path == selected.first() }?.isDir == true,
                                        singleLocked = selected.singleOrNull()?.let { it in lockedFolders } == true,
                                        onToggleLock = {
                                            entries.firstOrNull { it.path == selected.first() }?.let { entry ->
                                                if (entry.path in lockedFolders) {
                                                    requestUnlock(entry.path) {
                                                        val updated = lockedFolders - entry.path
                                                        lockedFolders = updated
                                                        prefs.edit().putStringSet("locked_folders", updated).apply()
                                                        unlockedSession = unlockedSession - entry.path
                                                        selected = emptySet()
                                                        notify(t.folderUnlockedDone)
                                                    }
                                                } else if (entry.name != FileOps.TRASH_DIR_NAME) {
                                                    val updated = lockedFolders + entry.path
                                                    lockedFolders = updated
                                                    prefs.edit().putStringSet("locked_folders", updated).apply()
                                                    selected = emptySet()
                                                    notify(t.folderLockedDone)
                                                }
                                            }
                                        }
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
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                                                    fadeOut(animationSpec = tween(durationMillis = 300))
                                            },
                                            label = "tabAnim"
                                        ) { tab ->
                                            when (tab) {
                                                1 -> RecentScreen(
                                                    files = recentFiles,
                                                    roots = roots,
                                                    accessGranted = accessGranted,
                                                    onOpenEntry = { entry ->
                                                        if (entry.isDir) {
                                                            openFolderGuarded(entry.path) { browseStack = listOf(entry.path) }
                                                        } else {
                                                            openEntry(entry)
                                                        }
                                                    },
                                                    onOpenFolder = { path ->
                                                        openFolderGuarded(path) { browseStack = listOf(path) }
                                                    }
                                                )
                                                2 -> AnalysisScreen(
                                                    roots = roots,
                                                    onOpenFolder = { path -> homeTab = 0; browseStack = listOf(path) },
                                                    onOpenFile = { entry -> homeTab = 0; openEntry(entry) }
                                                )
                                                else -> HomeScreen(
                                                    accessGranted = accessGranted,
                                                    roots = if (showRootEntry) allRoots else roots,
                                                    rootActive = rootActive,
                                                    onRequestAccess = { requestAccess() },
                                                    onOpenRoot = { browseStack = listOf(it) },
                                                    onOpenCategory = { cat -> overlay = Overlay.CategoryView(cat) },
                                                    onOpenTrash = { showTrash = true }
                                                )
                                            }
                                        }
                                    } else {
                                        BrowseScreen(
                                        currentPath = animatedPath,
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
                                        onNewFile = { dialog = DialogKind.NewFile },
                                        counts = entryCounts,
                                        lockedPaths = lockedFolders
                                        )
                                    }
                                }
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
            is DialogKind.NewFile -> NameDialog(
                title = t.newFile, label = t.fileName, initial = "",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    dialog = null
                    val dir = currentPath?.let { File(it) } ?: return@NameDialog
                    runOp(t.fileCreated) { FileOps.newFile(dir, name) != null }
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
                        runOp(t.movedToTrash) {
                            d.entries.all { TrashOps.moveToTrash(it.file) }
                        }
                        val viewingPath = when (val ov = overlay) {
                            is Overlay.ImageView -> ov.path
                            is Overlay.VideoView -> ov.path
                            else -> null
                        }
                        if (viewingPath != null && d.entries.any { it.path == viewingPath }) {
                            overlay = null
                            overlayReturn = null
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
            is DialogKind.ArchiveActions -> ArchiveActionsDialog(
                entry = d.entry,
                folderName = ArchiveOps.archiveBaseName(d.entry.name),
                onDismiss = { dialog = null },
                onExtractHere = {
                    dialog = null
                    val f = d.entry.file
                    runOp(t.extractDone) { ArchiveOps.extractToFolder(f, f.parentFile ?: f) }
                },
                onOpenArchive = {
                    dialog = null
                    overlay = Overlay.ArchiveView(d.entry.path)
                },
                onCustomPath = { dialog = DialogKind.PickExtractFolder(d.entry) }
            )
            is DialogKind.PickExtractFolder -> FolderPickerDialog(
                startPath = d.entry.file.parent ?: d.entry.path,
                title = t.extractCustom,
                onPick = { picked ->
                    dialog = null
                    val f = d.entry.file
                    runOp(t.extractDone) { ArchiveOps.extractToFolder(f, File(picked)) }
                },
                onCancel = { dialog = null }
            )
            is DialogKind.ApkActions -> ApkActionsDialog(
                entry = d.entry,
                onDismiss = { dialog = null },
                onInspect = {
                    val f = d.entry.file
                    working = true
                    scope.launch {
                        val items = withContext(Dispatchers.IO) { ArchiveOps.listZipContents(f) }
                        working = false
                        if (items != null) {
                            dialog = DialogKind.ArchiveContents(d.entry, items)
                        } else {
                            dialog = null
                            notify(t.opFailed)
                        }
                    }
                },
                onExtract = {
                    dialog = null
                    val f = d.entry.file
                    runOp(t.extractDone) { ArchiveOps.extract(f, f.parentFile ?: f) }
                },
                onInstall = {
                    dialog = null
                    openExternal(d.entry.file)
                }
            )
            is DialogKind.ArchiveContents -> ArchiveContentsDialog(
                entry = d.entry,
                items = d.items,
                onDismiss = { dialog = null }
            )
            null -> {}
        }
    }
}

private fun FEntry.parentPath(): String? = File(path).parent
