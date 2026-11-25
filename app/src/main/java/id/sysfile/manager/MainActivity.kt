package id.sysfile.manager

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Layar tambahan yang menimpa layar utama (Beranda/peramban). */
private sealed interface Overlay {
    data class Search(val rootPath: String) : Overlay
    data class ImageView(val paths: List<String>, val startIndex: Int) : Overlay
    data class VideoView(val paths: List<String>, val startIndex: Int) : Overlay
    data class TextView(val path: String, val originalPath: String? = null) : Overlay
    data class ArchiveText(val name: String, val content: String, val archivePath: String, val canSave: Boolean) : Overlay
    data class RamdiskText(val name: String, val content: String) : Overlay
    data class Ext4View(val path: String, val lpPath: String? = null) : Overlay
    data class Ext4Text(val name: String, val content: String) : Overlay
    data class FatView(val path: String) : Overlay
    data class FatText(val name: String, val content: String) : Overlay
    data class LpView(val path: String) : Overlay
    data class PdfView(val path: String) : Overlay
    data class EpubView(val path: String) : Overlay
    data class OfficeView(val path: String) : Overlay
    data class FontView(val path: String) : Overlay
    data class CategoryView(val category: Category) : Overlay
    data class ArchiveView(val path: String, val reload: Int = 0) : Overlay
    data class ImgView(val path: String) : Overlay
    data class PayloadView(val path: String) : Overlay
    data class DexView(val path: String) : Overlay
    data class ArscView(val path: String) : Overlay
    data class ProfView(val path: String, val originalPath: String? = null) : Overlay
    data class SqliteView(val path: String) : Overlay
    data class AxmlView(val path: String) : Overlay
    data class HexView(val path: String) : Overlay
}

private enum class ClipMode { COPY, CUT }

private sealed interface DialogKind {
    data object NewFolder : DialogKind
    data object NewFile : DialogKind
    data class Rename(val entry: FEntry) : DialogKind
    data class Delete(val entries: List<FEntry>) : DialogKind
    data class Properties(val entry: FEntry) : DialogKind
    data class Exif(val entry: FEntry) : DialogKind
    data class Compress(val entries: List<FEntry>) : DialogKind
    data class ZipPassword(val entry: FEntry) : DialogKind
    data class RarPassword(val entry: FEntry) : DialogKind
    data class ApkActions(val entry: FEntry) : DialogKind
    data class ArchiveActions(val entry: FEntry) : DialogKind
    data class PickExtractFolder(val entry: FEntry) : DialogKind
    data class ArchiveContents(val entry: FEntry, val items: List<Pair<String, Long>>) : DialogKind
    data class PayloadExtract(val path: String, val partition: String?) : DialogKind
    data object Sort : DialogKind
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (targetSdk 35) mewajibkan edge-to-edge: tanpa ini, sistem
        // tetap menggambar di bawah status/navigation bar dan konten bisa
        // tertutup. Scaffold + TopAppBar sudah mengonsumsi inset sistem, dan
        // bottomBar diberi navigationBarsPadding() di bawah.
        enableEdgeToEdge()
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
    val prefs = remember { context.getSharedPreferences("sysfile_prefs", Context.MODE_PRIVATE) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val motion = MaterialTheme.motionScheme

    var homeTab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showApps by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSpecs by rememberSaveable { mutableStateOf(false) }
    var showFormats by rememberSaveable { mutableStateOf(false) }
    var settingsExiting by remember { mutableStateOf(false) }
    var safTreeUri by remember { mutableStateOf<String?>(null) }
    var safRelPath by remember { mutableStateOf("") }
    var safBasePath by remember { mutableStateOf<String?>(null) }
    // M14: folder yang diminta saat pemilih SAF dibuka — grant hanya
    // disimpan bila pohon yang dipilih melingkupinya.
    var safRequestedPath by remember { mutableStateOf<String?>(null) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    // r137 TM1 Pudar Utuh: tangkapan layar tema lama + alfa memudarnya.
    // Saat tema diganti, layar lama (bitmap) memudar di atas layar baru
    // ~300 ms — bar atas tidak ketinggalan lagi.
    // r140: tangkapan hanya saat tidak ada fade yang sedang berjalan
    // (themeShot == null) — lihat onTheme.
    val rootView = LocalView.current
    var themeShot by remember { mutableStateOf<Bitmap?>(null) }
    // r143: Animatable dikunci ke themeShot — tiap tangkapan baru
    // dapat Animatable BARU yang mulai dari 1f. Sebelumnya
    // Animatable dibuat sekali (remember tanpa kunci): setelah
    // fade pertama nilainya 0, jadi frame pertama ganti tema
    // berikut merender overlay di alfa 0 (tema baru berkedip
    // 1 frame) sebelum LaunchedEffect sempat snapTo(1f).
    val themeShotAlpha = remember(themeShot) { Animatable(1f) }
    LaunchedEffect(themeShot) {
        val shot = themeShot
        if (shot != null) {
            themeShotAlpha.animateTo(0f, animationSpec = tween(300))
            themeShot = null
        }
    }
    // r165 WT1: jalur ganti tema dipakai bersama Pengaturan + Welcome —
    // satu lambda supaya TM1 fade + simpan pref tidak ditulis dua kali.
    val onThemeChange: (String) -> Unit = { mode ->
        // r137 TM1: tangkap layar lama SEBELUM ganti tema;
        // gagal tangkap = ganti instan seperti biasa.
        // r140: tangkap HANYA bila tidak ada fade yang sedang
        // berjalan. Ketukan saat overlay masih memudar akan
        // menangkap overlay itu juga (makin buram tiap
        // ketukan) + bar atas yang masih beranimasi; tanpa
        // tangkap ulang, fade lama terus jalan di atas tema
        // terbaru tanpa tumpukan.
        if (themeShot == null) {
            themeShot = try {
                if (rootView.width > 0 && rootView.height > 0) rootView.drawToBitmap() else null
            } catch (_: Exception) {
                null
            }
        }
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }
    var uiStyle by remember {
        val saved = prefs.getString("ui_style", "m3") ?: "m3"
        mutableStateOf(if (saved == "nothing") "nothing" else "m3")
    }
    val onUiStyleChange: (String) -> Unit = { style ->
        if (themeShot == null) {
            themeShot = try {
                if (rootView.width > 0 && rootView.height > 0) rootView.drawToBitmap() else null
            } catch (_: Exception) {
                null
            }
        }
        uiStyle = style
        prefs.edit().putString("ui_style", style).apply()
    }

    var language by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
    AppLang.code = language
    val t = txt()

    var accessGranted by remember { mutableStateOf(context.hasFileAccess()) }
    var showAccessDialog by remember { mutableStateOf(false) }
    // WB4 Layar sambutan (r113, pilihannya): tampil sekali sampai
    // selesai/dilewati; dialog izin lama baru muncul di buka berikutnya.
    var welcomeDone by remember { mutableStateOf(prefs.getBoolean("welcome_done", false)) }
    var roots by remember { mutableStateOf<List<RootInfo>>(emptyList()) }
    val rootFs = remember { FileOps.rootFilesystem() }
    var rootActive by remember { mutableStateOf(false) }
    val allRoots = roots + rootFs
    var browseStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var overlayReturn by remember { mutableStateOf<Overlay?>(null) }
    // r108: subfolder arsip yang sedang dibuka — disimpan di sini supaya
    // kembali dari berkas di dalam arsip mendarat di subfolder yang sama,
    // bukan di akar arsip (remember() ArchiveScreen terbuang saat overlay
    // berganti ke penampil berkas).
    var archivePrefix by rememberSaveable { mutableStateOf("") }
    var archivePrefixPath by rememberSaveable { mutableStateOf<String?>(null) }
    // r152: sama seperti archivePrefix (r108) — subfolder ramdisk
    // yang sedang dibuka, supaya kembali dari berkas di dalam
    // ramdisk mendarat di subfolder yang sama.
    var imgPrefix by rememberSaveable { mutableStateOf("") }
    var imgPrefixPath by rememberSaveable { mutableStateOf<String?>(null) }
    // r154: sesi baca ext4 (reader + temp unsparse bila sparse) —
    // dibuat saat membuka .img ext4/sparse, ditutup saat layar
    // Ext4View ditutup (onBack/onError). Bertahan saat overlay
    // berganti ke Ext4Text supaya kembali mendarat di folder yang sama.
    var ext4Session by remember { mutableStateOf<Ext4Session?>(null) }
    var ext4Prefix by rememberSaveable { mutableStateOf("") }
    var ext4PrefixPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun closeExt4Session() {
        ext4Session?.close()
        ext4Session = null
        ext4Prefix = ""
        ext4PrefixPath = null
    }
    // r155: pembaca FAT (.img FAT12/16/32) — dibuka saat membuka
    // .img FAT, ditutup saat layar FatView ditutup (onBack/onError).
    // Bertahan saat overlay berganti ke FatText supaya kembali
    // mendarat di folder yang sama (pola r108/r154).
    var fatReader by remember { mutableStateOf<FatReader?>(null) }
    var fatPrefix by rememberSaveable { mutableStateOf("") }
    var fatPrefixPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun closeFatReader() {
        fatReader?.close()
        fatReader = null
        fatPrefix = ""
        fatPrefixPath = null
    }
    // r156: sesi baca super.img (reader + temp unsparse bila sparse) —
    // dibuat saat membuka .img super, ditutup saat layar LpView
    // ditutup (onBack/onError) atau saat pengguna meninggalkan alur
    // lewat Ekstrak Semua dari partisi. Bertahan saat overlay
    // berganti ke Ext4View partisi supaya kembali mendarat di daftar
    // partisi tanpa unsparse ulang (pola r108/r154).
    var lpSession by remember { mutableStateOf<LpSession?>(null) }

    fun closeLpSession() {
        lpSession?.close()
        lpSession = null
    }

    // AU2 (r74): pemutar audio mini — hidup di SysFileApp (bukan di
    // overlay) supaya tetap berbunyi saat pengguna pindah folder/tab.
    var audioPath by remember { mutableStateOf<String?>(null) }
    // r82: layar tempat audio dimulai ("cat:AUDIO" /
    // "folder:<path>" / "tab:<n>") — MiniPlayerBar hanya
    // tampil selama pengguna masih di layar itu.
    var audioOrigin by remember { mutableStateOf<String?>(null) }
    var audioPlaying by remember { mutableStateOf(false) }
    var audioPositionMs by remember { mutableStateOf(0) }
    var audioDurationMs by remember { mutableStateOf(0) }
    val audioPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    // Posisi berjalan selama audio diputar (500ms).
    LaunchedEffect(audioPath, audioPlaying) {
        val mp = audioPlayer.value
        if (mp != null && audioPlaying) {
            while (true) {
                audioPositionMs = try { mp.currentPosition } catch (_: Exception) { 0 }
                delay(500)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { audioPlayer.value?.release() } catch (_: Exception) { } }
    }

    // L25: kunci folder yang volumenya sedang tidak terpasang TIDAK
    // lagi dibuang — dulu filter isDirectory menghapusnya permanen
    // (kunci hilang begitu volume dicabut).
    var lockedFolders by remember {
        mutableStateOf(
            // r175 H4: simpan ejaan mentah + kanonis tiap folder
            // terkunci, supaya alias path (/sdcard/...) dikenali.
            FileOps.canonicalizeLocked(
                prefs.getStringSet("locked_folders", emptySet()) ?: emptySet()
            )
        )
    }

    // Berkas yang sedang tampil di penampil foto/video (diperbarui tiap
    // ganti halaman) — dipakai aksi Bagikan/Properti/Hapus + penanda hapus.
    var viewingPath by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    var entryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var entriesLoaded by remember { mutableStateOf(true) }
    // Cache sesi: folder yang pernah dibuka tampil seketika, lalu dimuat ulang di latar.
    val listingCache = remember { mutableMapOf<String, List<FEntry>>() }
    // r84: cache daftar kategori per sesi (pola yang sama dengan
    // listingCache) — buka Foto/Video/Audio tidak memindai ulang
    // seluruh penyimpanan dari nol; daftar lama tampil seketika,
    // hasil segar menyusul di latar.
    val categoryCache = remember { mutableMapOf<Category, List<FEntry>>() }
    val categoryCacheAt = remember { mutableMapOf<Category, Long>() }
    val categoryCacheKey = remember { mutableMapOf<Category, Int>() }
    val countCache = remember { mutableMapOf<String, Int>() }
    var reloadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var homeRefreshing by remember { mutableStateOf(false) }
    var recentRefreshing by remember { mutableStateOf(false) }
    var browseRefreshing by remember { mutableStateOf(false) }

    fun invalidateRootCaches() {
        listingCache.clear()
        countCache.clear()
        reloadKey++
    }

    // r116: refreshRoots dideklarasikan di sini — receiver OT1 di
    // bawah memakainya, dan fungsi lokal harus dideklarasi sebelum
    // pemakaian pertama.
    // L18: refreshRoots TIDAK lagi memblokir main thread (storageRoots
    // membaca /proc/mounts + sysfs — dulu ANR saat storage lambat).
    fun refreshRoots(onFinished: (() -> Unit)? = null) {
        scope.launch {
            try {
            val rootOn = prefs.getBoolean("root_entry", false)
            if (!rootOn) {
                if (rootActive) {
                    rootActive = false
                    invalidateRootCaches()
                }
            } else {
                if (RootShell.state != RootShell.GRANTED) {
                    RootShell.resetRootState()
                    withContext(Dispatchers.IO) { RootShell.ensureRoot() }
                }
                val isGranted = RootShell.state == RootShell.GRANTED
                if (isGranted && !prefs.getBoolean("root_granted_before", false)) {
                    prefs.edit().putBoolean("root_granted_before", true).apply()
                }
                if (rootActive != isGranted) {
                    rootActive = isGranted
                    invalidateRootCaches()
                }
            }
            val fresh = withContext(Dispatchers.IO) { FileOps.storageRoots(context) }.toMutableList()
            // r201: OTG yang tak terlihat di /storage (di HP ini mount
            // FUSE-nya tidak diekspos ke aplikasi) — baca titik mount
            // mentah /mnt/media_rw lewat root; browsing-nya jatuh ke
            // RootShell.listDir seperti folder root lainnya.
            if (fresh.none { it.kind == "usb" } && rootOn &&
                RootShell.state == RootShell.GRANTED
            ) {
                val raw = withContext(Dispatchers.IO) {
                    RootShell.listDir(File("/mnt/media_rw"), false, SortKey.NAME, true)
                }
                // r207: isi ukuran volume via df root — tanpa total,
                // heroRoots menyaring USB keluar dari pager (hanya jadi
                // baris kecil di bawahnya).
                for (e in raw?.filter { it.isDir } ?: emptyList()) {
                    val p = "/mnt/media_rw/" + e.name
                    if (fresh.none { it.path == p }) {
                        val size = withContext(Dispatchers.IO) { RootShell.volumeSize(p) }
                        fresh += if (size != null) RootInfo(p, "usb", size.first, size.second)
                        else RootInfo(p, "usb", 0L, 0L)
                    }
                }
            }
            roots = fresh
            lockedFolders = FileOps.canonicalizeLocked(
                prefs.getStringSet("locked_folders", emptySet()) ?: emptySet()
            )
            } finally {
                onFinished?.invoke()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (prefs.getBoolean("root_entry", false) && !rootActive) {
                    refreshRoots()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // OT1 (r110): pantau colok/cabut media + perangkat USB — daftar
    // penyimpanan di-refresh seketika supaya kartu USB/OTG muncul (atau
    // hilang) tanpa restart aplikasi. Media broadcast butuh skema "file";
    // USB_DEVICE_* tidak membawa data, jadi filter-nya dipisah. Di API 33+
    // registerReceiver butuh flag eksplisit (NOT_EXPORTED: siaran ini
    // datang dari sistem).
    DisposableEffect(Unit) {
        val mediaReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshRoots() }
        }
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshRoots()
                // r172: USB_DEVICE_ATTACHED datang SEBELUM volume selesai
                // dipasang — refresh seketika belum melihat apa-apa, dan
                // siaran MEDIA_MOUNTED tidak sampai ke aplikasi untuk
                // volume USB (tidak VISIBLE, lih. VolumeInfo). Refresh
                // ulang tertunda supaya volume yang baru terpasang ikut
                // terbaca tanpa restart aplikasi.
                if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                    scope.launch {
                        delay(1500)
                        refreshRoots()
                        delay(2500)
                        refreshRoots()
                    }
                }
            }
        }
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
        register(mediaReceiver, mediaFilter)
        register(usbReceiver, usbFilter)
        onDispose {
            try { context.unregisterReceiver(mediaReceiver) } catch (_: Exception) { }
            try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) { }
        }
    }
    var clipboard by remember { mutableStateOf<Pair<List<String>, ClipMode>?>(null) }
    var working by remember { mutableStateOf(false) }
    var workProgress by remember { mutableStateOf<Float?>(null) }
    var workCancelFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
    var workStage by remember { mutableStateOf<String?>(null) }
    var workProgressText by remember { mutableStateOf<String?>(null) }
    // r182: kartu progres ekstrak di TENGAH layar (EX2, dipindah ke
    // tengah atas permintaan Achmad) — non-modal, terpisah dari dialog
    // modal `working` supaya ekstrak tidak lagi memakai dialog itu.
    var extractCardVisible by remember { mutableStateOf(false) }
    var extractCardName by remember { mutableStateOf("") }
    var extractCardProgress by remember { mutableStateOf<Float?>(null) }
    var extractCardText by remember { mutableStateOf<String?>(null) }
    var extractCardCancelFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var unlockedSession by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recentFiles by remember { mutableStateOf<List<FEntry>>(emptyList()) }

    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var showRootEntry by remember { mutableStateOf(prefs.getBoolean("root_entry", false)) }
    var trashEnabled by remember { mutableStateOf(prefs.getBoolean("trash_enabled", true)) }
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

    /** Identitas layar saat ini (r82): kategori yang sedang
     *  dibuka, folder yang sedang dijelajahi, atau tab atas. */
    fun currentScreenId(): String = when (val ov = overlay) {
        is Overlay.CategoryView -> "cat:" + ov.category.name
        else -> if (currentPath != null) "folder:$currentPath" else "tab:$homeTab"
    }

    // r193 NP2: pill umum — menggantikan snackbar untuk SEMUA
    // notifikasi. Sukses = centang biru, gagal = silang merah.
    // Pesan panjang terpotong (ellipsis) di UI. Auto-hilang 2,6 dtk,
    // ketuk menutup; timer di-reset bila pill baru datang selagi tampil.
    var pillVisible by remember { mutableStateOf(false) }
    var pillNonce by remember { mutableStateOf(0) }
    var pillMsg by remember { mutableStateOf("") }
    var pillOk by remember { mutableStateOf(true) }
    fun showPill(msg: String, ok: Boolean = true) {
        pillMsg = msg
        pillOk = ok
        pillNonce++
        pillVisible = true
    }
    LaunchedEffect(pillNonce) {
        if (pillVisible) {
            delay(2600)
            pillVisible = false
        }
    }

    // r193 NP2: [msg] diklasifikasi gagal bila sama dengan salah satu
    // pesan gagal di bawah — semua pesan gagal adalah string polos.
    // Pesan sukses berparameter (safCopied, payloadDone) dan pesan
    // sukses dari runOp/runLongOp/runAction tidak pernah sama dengan
    // daftar ini, jadi default-nya sukses.
    fun notify(msg: String) {
        val failed = msg == t.opFailed || msg == t.workCancelled ||
            msg == t.openFailed || msg == t.readFailed ||
            msg == t.unlockFailed || msg == t.safWrongFolder ||
            msg == t.safCopyFailed || msg == t.payloadUnsupported ||
            msg == t.payloadInvalid || msg == t.payloadCancelled ||
            msg == t.payloadHashMismatch
        showPill(msg, ok = !failed)
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
            unavailableMessage = t.biometricUnavailable,
            onSuccess = {
                // r175 H4: simpan ejaan mentah + kanonis — membuka
                // lewat alias (/sdcard/...) tidak meminta ulang.
                val canon = FileOps.canonicalOrNull(path)
                unlockedSession = if (canon != null && canon != path) {
                    unlockedSession + path + canon
                } else {
                    unlockedSession + path
                }
                onGranted()
            },
            onError = { msg -> notify(msg.ifBlank { t.unlockFailed }) }
        )
    }

    /** Masuk ke folder dengan gerbang kunci: folder terkunci yang belum dibuka pada
     *  sesi ini harus lolos biometrik terlebih dahulu, baru [land] dijalankan. */
    fun openFolderGuarded(path: String, land: () -> Unit) {
        // r175 H4: gerbang ancestor-aware + kanonis — alias path
        // (/sdcard/Private) dikenali sebagai folder terkunci.
        if (FileOps.isLockedPath(path, lockedFolders) &&
            !FileOps.isUnlockedPath(path, unlockedSession)
        ) {
            requestUnlock(path, land)
        } else {
            land()
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        accessGranted = context.hasFileAccess()
        refreshRoots()
    }
    val legacyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        accessGranted = context.hasFileAccess()
        refreshRoots()
    }

    // Izin SAF tersimpan: entri "jalur<0x01>uriPohon" di set pref saf_grants.
    // r175 H3: kembalikan juga akar grant (jalur berkas pohon SAF) supaya
    // layar SAF bisa memetakan dokumen ke jalur berkas untuk gerbang kunci.
    /** Benar bila URI masih ada di persistedUriPermissions sistem (M14). */
    fun isSafUriPersisted(uriStr: String): Boolean {
        return try {
            context.contentResolver.persistedUriPermissions.any { it.uri.toString() == uriStr }
        } catch (_: Exception) {
            false
        }
    }

    /** Hapus grant SAF basi dari prefs (dicabut di Pengaturan sistem)
     *  supaya UI tidak mengklaim grant yang sudah tidak ada (M14). */
    fun pruneStaleSafGrants() {
        val set = prefs.getStringSet("saf_grants", emptySet()) ?: emptySet()
        val kept = set.filter { e ->
            val u = e.substringAfter('\u0001', "")
            u.isNotEmpty() && isSafUriPersisted(u)
        }.toSet()
        if (kept.size != set.size) {
            prefs.edit().putStringSet("saf_grants", kept).apply()
        }
    }

    fun safGrantFor(path: String): Triple<String, String, String>? {
        val set = prefs.getStringSet("saf_grants", emptySet()) ?: emptySet()
        for (e in set) {
            val p = e.substringBefore('\u0001')
            val u = e.substringAfter('\u0001', "")
            // M14: grant prefs-only yang URI-nya sudah tidak persisten
            // dilewati (dicabut di sistem / tidak pernah tersimpan).
            if (u.isEmpty() || !isSafUriPersisted(u)) continue
            if (path == p) return Triple(u, "", p)
            if (path.startsWith("$p/")) return Triple(u, path.removePrefix("$p/"), p)
        }
        return null
    }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // M14: grant HANYA disimpan bila takePersistableUriPermission
            // berhasil — dulu gagal pun tetap ditulis ke saf_grants.
            var persisted = false
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                persisted = true
            } catch (e: Exception) {
                // Izin tidak persisten pun tetap bisa dipakai selama sesi ini.
            }
            val p = SafOps.pathOfTree(uri)
            // M14: pohon yang dipilih harus melingkupi folder yang
            // diminta (atau sebaliknya — pemilih dibuka di akar volume,
            // jadi memilih induknya sah); pilihan lain tidak disimpan.
            val requested = safRequestedPath
            val covers = requested == null || p.isEmpty() ||
                SafPath.coversRequested(p, requested)
            if (persisted && p.isNotEmpty() && covers) {
                val old = prefs.getStringSet("saf_grants", emptySet()) ?: emptySet()
                val kept = old.filter { it.substringBefore('\u0001') != p }
                prefs.edit()
                    .putStringSet("saf_grants", (kept + "$p\u0001$uri").toSet())
                    .apply()
            } else if (!covers) {
                notify(t.safWrongFolder)
            }
            // Hitungan "0 item" yang telanjur tersimpan untuk folder ini
            // harus dihitung ulang lewat SAF saat layar induk dibuka lagi.
            if (p.isNotEmpty()) countCache.remove(p)
            // r175 H3: pohon yang dipilih bisa jadi folder terkunci —
            // gerbang biometrik dulu sebelum layar SAF dibuka.
            val openPicked = {
                safRelPath = ""
                safTreeUri = uri.toString()
                safBasePath = p
            }
            if (p.isNotEmpty() && FileOps.isLockedPath(p, lockedFolders) &&
                !FileOps.isUnlockedPath(p, unlockedSession)
            ) {
                requestUnlock(p, openPicked)
            } else {
                openPicked()
            }
            safRequestedPath = null
        }
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
    // M14: bersihkan grant SAF basi (dicabut di Pengaturan sistem)
    // supaya UI tidak mengklaim grant yang sudah tidak ada.
    LaunchedEffect(Unit) { pruneStaleSafGrants() }

    // Dialog izin penyimpanan saat buka app (r6): muncul tiap buka app
    // selama izin belum diberikan; "Nanti" menutupnya dan dialog muncul
    // lagi di buka berikutnya. r113: tidak muncul di buka pertama —
    // layar sambutan WB4 yang meminta izin di sana.
    LaunchedEffect(Unit) {
        if (!accessGranted && welcomeDone) showAccessDialog = true
    }
    // Pulang dari halaman Pengaturan sistem dengan izin diberikan:
    // tutup dialog bila masih tampil.
    LaunchedEffect(accessGranted) {
        if (accessGranted) showAccessDialog = false
    }

    LaunchedEffect(currentRoot?.kind, showRootEntry) {
        if (showRootEntry && currentRoot?.kind == "root" && RootShell.state != RootShell.GRANTED) {
            RootShell.resetRootState()
            withContext(Dispatchers.IO) { RootShell.ensureRoot() }
        }
        val granted = showRootEntry && RootShell.state == RootShell.GRANTED
        if (rootActive != granted) {
            rootActive = granted
            invalidateRootCaches()
        } else if (granted) {
            reloadKey++
        }
    }

    LaunchedEffect(currentPath, reloadKey, showHidden, sortKey, sortAsc, accessGranted, rootActive) {
        val path = currentPath
        if (path == null) {
            entries = emptyList()
            browseRefreshing = false
            return@LaunchedEffect
        }
        if (showRootEntry && !rootActive &&
            (!path.startsWith("/storage/") && path != "/storage" && !path.startsWith("/sdcard") ||
                FileOps.isRestrictedDir(path))
        ) {
            RootShell.resetRootState()
            val ok = withContext(Dispatchers.IO) { RootShell.ensureRoot() }
            if (ok) {
                prefs.edit().putBoolean("root_granted_before", true).apply()
                rootActive = true
                listingCache.clear()
                countCache.clear()
            }
        }
        val cacheKey = path + "|" + showHidden + "|" + sortKey + "|" + sortAsc + "|" + rootActive
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
                // Folder penyimpanan yang dikunci sistem (mis. Android/data,
                // Android/obb): jalur berkas biasa gagal (null) atau mengaku
                // kosong padahal foldernya tak terbaca (sebagian ROM menjawab
                // array kosong untuk folder terkunci). Bila fitur root aktif,
                // minta/pakai akses root lalu baca lewat shell — status root
                // di-cache per sesi, jadi prompt su hanya muncul sekali.
                // Tanpa fitur root, hasil biasa dipakai dan UI menawarkan SAF.
                val dirFile = File(path)
                val plain = FileOps.listDir(dirFile, showHidden, sortKey, sortAsc)
                // r170: folder terkunci sistem bisa mengaku kosong (listFiles()
                // = []) padahal canRead() true — hasil kosong untuk folder
                // terlarang jangan dipercaya; lewatkan ke cabang root di bawah
                // (RootShell.listDir membaca Android/obb lewat /data/media/0).
                if (plain != null && (plain.isNotEmpty() ||
                    (dirFile.canRead() && !FileOps.isRestrictedDir(path)))) {
                    plain
                } else if (showRootEntry && RootShell.state == RootShell.GRANTED) {
                    RootShell.listDir(dirFile, showHidden, sortKey, sortAsc) ?: plain
                } else {
                    plain
                }
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
                    // Folder dianggap tak terbaca bila listFiles null, atau
                    // kosong padahal canRead false (sebagian ROM menjawab
                    // array kosong untuk folder terkunci).
                    fun unreadable(f: File): Boolean {
                        val lf = f.listFiles()
                        // r170: folder terlarang yang mengaku kosong
                        // (canRead() true) juga tak terbaca — hitung lewat
                        // shell root / SAF, bukan percaya angka 0.
                        return lf == null || (lf.isEmpty() &&
                            (!f.canRead() || FileOps.isRestrictedDir(f.absolutePath)))
                    }
                    coroutineScope {
                        missing.map { entry ->
                            async {
                                val direct = FileOps.childCount(entry.file)
                                // Angka 0 karena folder tak terbaca langsung
                                // (wilayah root atau Android/data): hitung
                                // lewat shell root bila aksesnya sudah ada.
                                // Folder terkunci yang sudah diberi izin SAF
                                // dihitung lewat DocumentFile.
                                val count = if (direct == 0 &&
                                    RootShell.state == RootShell.GRANTED &&
                                    unreadable(entry.file)
                                ) {
                                    RootShell.childCount(entry.file) ?: direct
                                } else if (direct == 0 &&
                                    FileOps.isRestrictedDir(entry.path) &&
                                    // Carry-over: jangan hitung isi folder
                                    // terkunci lewat SAF — angkanya sendiri
                                    // metadata (jumlah isi) yang bocor.
                                    (!FileOps.isLockedPath(entry.path, lockedFolders) ||
                                        FileOps.isUnlockedPath(entry.path, unlockedSession))
                                ) {
                                    safGrantFor(entry.path)?.let { (uri, rel, _) ->
                                        SafOps.countChildren(context, uri, rel)
                                    } ?: direct
                                } else {
                                    direct
                                }
                                entry.path to count
                            }
                        }.awaitAll()
                    }
                }
                if (countCache.size > 512) countCache.clear()
                fresh.forEach { (p, c) -> countCache[p] = c }
            }
            dirs.associate { it.path to (countCache[it.path] ?: 0) }
        }
        entriesLoaded = true
        browseRefreshing = false
        // L29: pertahankan seleksi yang masih ada di daftar baru —
        // dulu seleksi dihapus di setiap reload (termasuk saat folder
        // berubah, entri yang hilang ikut terbuang dari seleksi).
        val kept = entries.map { it.path }.toSet()
        selected = selected.intersect(kept)
    }

    LaunchedEffect(accessGranted, roots, showHidden, reloadKey, lockedFolders) {
        if (!accessGranted || roots.isEmpty()) {
            recentFiles = emptyList()
            recentRefreshing = false
            return@LaunchedEffect
        }
        recentFiles = withContext(Dispatchers.IO) {
            FileOps.recentFiles(roots.map { File(it.path) }, showHidden, lockedFolders, limit = 60)
        }
        recentRefreshing = false
    }

    fun openExternal(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileOps.mimeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // L21: selalu lewat chooser — dulu aplikasi default
            // langsung dibuka tanpa pilihan (pengguna tak bisa memilih
            // penampil lain).
            context.startActivity(Intent.createChooser(intent, t.openWith))
        } catch (e: Exception) {
            notify(t.openFailed)
        }
    }

    fun stopAudio() {
        try { audioPlayer.value?.release() } catch (_: Exception) { }
        audioPlayer.value = null
        audioPath = null
        audioOrigin = null
        audioPlaying = false
        audioPositionMs = 0
        audioDurationMs = 0
    }

    fun startAudio(entry: FEntry) {
        stopAudio()
        audioOrigin = currentScreenId()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(entry.file.path)
            mp.setOnPreparedListener {
                audioDurationMs = it.duration
                it.start()
                audioPlaying = true
            }
            mp.setOnCompletionListener {
                audioPlaying = false
                audioPositionMs = 0
            }
            mp.setOnErrorListener { _, _, _ ->
                notify(t.opFailed)
                stopAudio()
                true
            }
            audioPlayer.value = mp
            audioPath = entry.file.path
            mp.prepareAsync()
        } catch (e: Exception) {
            try { mp.release() } catch (_: Exception) { }
            audioPlayer.value = null
            audioPath = null
            notify(t.opFailed)
        }
    }

    fun toggleAudio() {
        val mp = audioPlayer.value ?: return
        if (mp.isPlaying) {
            mp.pause()
            audioPlaying = false
            audioPositionMs = try { mp.currentPosition } catch (_: Exception) { audioPositionMs }
        } else {
            mp.start()
            audioPlaying = true
        }
    }

    fun shareFile(file: File) {
        try {
            // L9: foto yang dibagikan distrip GPS-nya dulu (salinan di
            // cache) supaya lokasi tidak ikut terkirim tanpa sadar.
            val stripped = if (FileOps.categoryOf(file.name) == Category.IMAGE) {
                ExifOps.stripGpsCopy(file, context.cacheDir)
            } else {
                null
            }
            if (stripped != null) notify(t.shareGpsStripped)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", stripped ?: file)
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

    // r167: dipindah ke atas openEntry — local fun WAJIB dideklarasi
    // sebelum pemakaian pertamanya. r159 menaruh blok ini di bawah
    // (setelah openEntry), jadi beginLongWork di 936 tidak ter-resolve
    // dan compileDebugKotlin gagal (Unresolved name + Unresolved 'clear').
    /** LD3 r159: siapkan dialog kerja berprogres untuk satu operasi
     *  panjang .img (pola yang sama seperti PayloadExtract: onProgress
     *  per 64 KiB + AtomicBoolean). Kembalikan (flag, reporter, clear)
     *  — pemanggil WAJIB memanggil clear() di finally supaya state
     *  dialog tidak tertinggal. [format] mengubah (done, total) menjadi
     *  teks di bawah bar; default-nya "1,2 GB / 4,0 GB • 30%". */
    fun beginLongWork(
        stage: String,
        format: ((Long, Long) -> String)? = null
    ): Triple<AtomicBoolean, (Long, Long) -> Unit, () -> Unit> {
        val flag = AtomicBoolean(false)
        workCancelFlag = flag
        workStage = stage
        workProgress = 0f
        workProgressText = null
        working = true
        val fmt = format ?: { done: Long, total: Long ->
            "${FileOps.formatSize(done)} / ${FileOps.formatSize(total)} • ${(done * 100 / total).toInt()}%"
        }
        val report: (Long, Long) -> Unit = { done, total ->
            if (total > 0L) {
                workProgress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                workProgressText = fmt(done, total)
            } else {
                workProgress = null
                workProgressText = null
            }
        }
        val clear: () -> Unit = {
            working = false
            workProgress = null
            workProgressText = null
            workCancelFlag = null
            workStage = null
        }
        return Triple(flag, report, clear)
    }

    /** LD3 r159: jalankan satu operasi panjang .img dengan dialog
     *  berprogres + tombol Batal — [block] menerima flag + reporter
     *  dan mengembalikan true bila berhasil; batal dilempar sebagai
     *  ImgCancelledException. Selesai: sukses -> successMsg, batal ->
     *  workCancelled, gagal -> opFailed. */
    fun runLongOp(
        stage: String,
        successMsg: String,
        format: ((Long, Long) -> String)? = null,
        block: suspend (flag: AtomicBoolean, onProgress: (Long, Long) -> Unit) -> Boolean
    ) {
        val (flag, report, clear) = beginLongWork(stage, format)
        scope.launch {
            val outcome = try {
                val ok = withContext(Dispatchers.IO) { block(flag, report) }
                if (ok) 0 else 1
            } catch (_: ImgCancelledException) {
                2
            } catch (_: Exception) {
                1
            } finally {
                clear()
            }
            when (outcome) {
                0 -> {
                    notify(successMsg)
                    reloadKey++
                    selected = emptySet()
                }
                2 -> notify(t.workCancelled)
                else -> notify(t.opFailed)
            }
        }
    }

    /** r182: jalankan satu operasi ekstrak dengan kartu progres di TENGAH
     *  layar (EX2, dipindah ke tengah atas permintaan Achmad) — non-modal,
     *  pengguna tetap bisa browsing selagi ekstrak berjalan. [name] tampil
     *  di kartu; [format] mengubah (done, total) menjadi teks di bawah bar
     *  (default format ukuran "1,2 GB / 4,0 GB • 30%" seperti dialog modal;
     *  jalur entri memakai t.extractProgress). Bila [determinate] false,
     *  bar tak tentu tanpa tombol Batal (ekstrak satu entri yang tidak
     *  melaporkan progres). Selesai: sukses -> pill ekstrak, batal/gagal ->
     *  pill merah (r193 NP2). Dialog modal `working` tidak
     *  dipakai di sini — runLongOp/runOp tetap untuk operasi lain. */
    fun runExtractOp(
        name: String,
        format: ((Long, Long) -> String)? = null,
        determinate: Boolean = true,
        block: suspend (flag: AtomicBoolean, onProgress: (Long, Long) -> Unit) -> Boolean
    ) {
        val flag = AtomicBoolean(false)
        extractCardName = name
        extractCardProgress = if (determinate) 0f else null
        extractCardText = null
        extractCardCancelFlag = if (determinate) flag else null
        extractCardVisible = true
        val fmt = format ?: { done: Long, total: Long ->
            "${FileOps.formatSize(done)} / ${FileOps.formatSize(total)} • ${(done * 100 / total).toInt()}%"
        }
        val report: (Long, Long) -> Unit = { done, total ->
            if (total > 0L) {
                extractCardProgress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                extractCardText = fmt(done, total)
            } else {
                extractCardProgress = null
                extractCardText = null
            }
        }
        scope.launch {
            val outcome = try {
                val ok = withContext(Dispatchers.IO) { block(flag, report) }
                if (ok) 0 else 1
            } catch (_: ImgCancelledException) {
                2
            } catch (_: Exception) {
                1
            } finally {
                extractCardVisible = false
                extractCardProgress = null
                extractCardText = null
                extractCardCancelFlag = null
            }
            when (outcome) {
                0 -> {
                    showPill(t.extractPill)
                    reloadKey++
                    selected = emptySet()
                }
                2 -> notify(t.workCancelled)
                else -> notify(t.opFailed)
            }
        }
    }

    /** r197: jalankan satu operasi dengan kartu progres di TENGAH layar
     *  (kartu yang sama dengan ekstrak r182 — state extractCard* dipakai
     *  bersama; jangan ganti nama tanpa mengubah semua call site ekstrak).
     *  Dipakai hapus (pindah ke Sampah) dan tempel (salin/pindah):
     *  bar TAK TENTU karena moveToTrash/copyInto tidak melaporkan progres,
     *  tanpa tombol Batal (tidak bisa dibatalkan
     *  di tengah jalan dengan aman). Selesai: sukses -> successMsg, gagal
     *  -> opFailed (pill r193). Dialog modal `working` TIDAK dipakai di
     *  sini — saat hapus/tempel hanya kartu progres yang tampil. Bila ekstrak
     *  sedang berjalan, kartu ini menimpa kartu ekstrak (sama seperti
     *  ekstrak kedua menimpa yang pertama, r182). */
    fun runCardOp(
        name: String,
        successMsg: String,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> Boolean
    ) {
        extractCardName = name
        extractCardProgress = null
        extractCardText = null
        extractCardCancelFlag = null
        extractCardVisible = true
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { block() }
            } catch (_: Exception) {
                false
            } finally {
                extractCardVisible = false
                extractCardProgress = null
                extractCardText = null
                extractCardCancelFlag = null
            }
            if (ok) {
                notify(successMsg)
                reloadKey++
                selected = emptySet()
                onSuccess?.invoke()
            } else {
                notify(t.opFailed)
            }
        }
    }

    // L26: [onOverlayOpened] dipanggil setiap kali overlay dibuka —
    // pemanggil (mis. CategoryScreen) bisa mencatat overlayReturn
    // dengan benar juga untuk jalur async (.img/.bin/arsip); dulu
    // overlayReturn ditebak dari perbandingan state sesudah pemanggilan
    // sinkron, sehingga jalur async kehilangan jalan kembali.
    fun openEntry(entry: FEntry, siblings: List<FEntry>? = null, onOverlayOpened: ((Overlay) -> Unit)? = null) {
        fun show(ov: Overlay) {
            overlay = ov
            onOverlayOpened?.invoke(ov)
        }
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
                if (staged != null) {
                    if (FileOps.isTextFile(f)) {
                        show(Overlay.TextView(staged.absolutePath, originalPath = f.absolutePath))
                    } else if (FileOps.extOf(f.name) in setOf("prof", "profm")) {
                        show(Overlay.ProfView(staged.absolutePath, originalPath = f.absolutePath))
                    } else {
                        openEntry(FileOps.entryOf(staged))
                    }
                } else notify(t.opFailed)
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
                    else -> show(Overlay.PayloadView(entry.path))
                }
            }
            return
        }
        if (FileOps.extOf(entry.name) == "img") {
            // r152: boot.img dibuka di layar isi ramdisk; r154: ext4
            // mentah dibuka di layar isi ext4; r155: FAT dibuka di
            // layar isi FAT; r156: super.img (mentah/sparse) dibuka
            // di daftar partisi logis, partisi dibuka di layar isi
            // ext4; jenis lain tetap tampil di Properti (r151).
            // r158: sparse FAT dan sparse super.img dibaca langsung tanpa unsparse (LD2).
            // openEntry bukan suspend, jadi deteksi berjalan di
            // scope.launch — withContext langsung di sini tidak compile.
            working = true
            scope.launch {
                val detected = withContext(Dispatchers.IO) { ImgOps.detectImgType(f) }
                when (detected) {
                    ImgType.BOOT -> {
                        working = false
                        show(Overlay.ImgView(entry.path))
                    }
                    ImgType.SUPER -> {
                        val reader = withContext(Dispatchers.IO) { LpReader.open(f) }
                        working = false
                        if (reader != null) {
                            lpSession = LpSession(f, null, reader)
                            show(Overlay.LpView(entry.path))
                        } else {
                            notify(t.opFailed)
                        }
                    }
                    ImgType.EXT4 -> {
                        // Unsparse multi-GB bisa makan waktu — working
                        // tetap menyala sampai sesi siap.
                        val session = withContext(Dispatchers.IO) { Ext4Reader.openSession(f) }
                        working = false
                        if (session != null) {
                            ext4Session = session
                            show(Overlay.Ext4View(entry.path))
                        } else {
                            notify(t.opFailed)
                        }
                    }
                    ImgType.SPARSE -> {
                        // LD2 r157+r158: sparse dibaca langsung tanpa
                        // unsparse — dicoba berurutan: ext4, FAT, lalu
                        // super.img. Gagal semua -> jalur lama (unsparse
                        // sekali ke temp, dicoba sebagai super.img dulu
                        // baru ext4 — r156).
                        val direct: Triple<Ext4Session?, FatReader?, LpReader?> =
                            withContext(Dispatchers.IO) {
                                var ext: Ext4Session? = null
                                var fat: FatReader? = null
                                var lp: LpReader? = null
                                ext = Ext4Reader.openSession(f)
                                if (ext == null) fat = FatReader.openSparse(f)
                                if (ext == null && fat == null) lp = LpReader.openSparse(f)
                                Triple(ext, fat, lp)
                            }
                        val ext = direct.first
                        val fat = direct.second
                        val lp = direct.third
                        if (ext != null) {
                            working = false
                            ext4Session = ext
                            show(Overlay.Ext4View(entry.path))
                        } else if (fat != null) {
                            working = false
                            fatReader = fat
                            show(Overlay.FatView(entry.path))
                        } else if (lp != null) {
                            working = false
                            lpSession = LpSession(f, null, lp)
                            show(Overlay.LpView(entry.path))
                        } else {
                            // LD3 r159: fallback unsparse (jarang — hanya
                            // bila direct ext4/FAT/LP gagal) ikut berprogres
                            // + bisa dibatalkan; clear() menutup dialog.
                            val (flag, report, clear) = beginLongWork(t.workStageUnsparse)
                            var unsparseCancelled = false
                            val opened: Pair<LpSession?, Ext4Session?> = withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "lp-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                val tmp = File(dir, "super-${System.nanoTime()}.img")
                                try {
                                    when (ImgOps.unsparse(f, tmp, onProgress = report, cancelFlag = flag)) {
                                        is UnsparseResult.Ok -> {
                                            val lpReader = LpReader.open(tmp)
                                            if (lpReader != null) {
                                                LpSession(tmp, tmp, lpReader) to null
                                            } else {
                                                val extReader = Ext4Reader.open(tmp)
                                                if (extReader != null) {
                                                    null to Ext4Session(tmp, tmp, extReader)
                                                } else {
                                                    tmp.delete()
                                                    null to null
                                                }
                                            }
                                        }
                                        is UnsparseResult.Cancelled -> {
                                            unsparseCancelled = true
                                            null to null
                                        }
                                        else -> null to null
                                    }
                                } catch (_: Exception) {
                                    tmp.delete()
                                    null to null
                                }
                            }
                            clear()
                            val lp2 = opened.first
                            val ext2 = opened.second
                            if (lp2 != null) {
                                lpSession = lp2
                                show(Overlay.LpView(entry.path))
                            } else if (ext2 != null) {
                                ext4Session = ext2
                                show(Overlay.Ext4View(entry.path))
                            } else if (unsparseCancelled) {
                                notify(t.workCancelled)
                            } else {
                                notify(t.opFailed)
                            }
                        }
                    }
                    ImgType.FAT -> {
                        val reader = withContext(Dispatchers.IO) { FatReader.open(f) }
                        working = false
                        if (reader != null) {
                            fatReader = reader
                            show(Overlay.FatView(entry.path))
                        } else {
                            notify(t.opFailed)
                        }
                    }
                    ImgType.UNKNOWN -> {
                        working = false
                        notify(t.opFailed)
                    }
                    else -> {
                        working = false
                        dialog = DialogKind.Properties(entry)
                    }
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
            entry.category == Category.IMAGE -> {
                val (paths, idx) = FileOps.mediaSiblings(siblings ?: entries, entry)
                show(Overlay.ImageView(paths, idx))
            }
            entry.category == Category.VIDEO -> {
                stopAudio()
                val (paths, idx) = FileOps.mediaSiblings(siblings ?: entries, entry)
                show(Overlay.VideoView(paths, idx))
            }
            entry.category == Category.AUDIO -> {
                startAudio(entry)
            }
            FileOps.extOf(f.name) == "pdf" -> show(Overlay.PdfView(entry.path))
            FileOps.extOf(f.name) == "epub" -> show(Overlay.EpubView(entry.path))
            FileOps.extOf(f.name) == "ttf" -> show(Overlay.FontView(entry.path))
            FileOps.extOf(f.name) == "dex" -> show(Overlay.DexView(entry.path))
            FileOps.extOf(f.name) == "arsc" -> show(Overlay.ArscView(entry.path))
            FileOps.extOf(f.name) in setOf("prof", "profm") -> show(Overlay.ProfView(entry.path))
            FileOps.extOf(f.name) in setOf("db", "sqlite", "sqlite3") -> show(Overlay.SqliteView(entry.path))
            FileOps.extOf(f.name) == "xml" -> {
                if (AxmlOps.isBinaryXml(f)) show(Overlay.AxmlView(entry.path))
                else if (f.length() <= 2_000_000) show(Overlay.TextView(entry.path))
                else show(Overlay.HexView(entry.path))
            }
            FileOps.extOf(f.name) in setOf("bin", "dat", "so", "o", "elf", "rom") -> show(Overlay.HexView(entry.path))
            FileOps.extOf(f.name) in setOf("docx", "xlsx", "pptx", "odt") -> show(Overlay.OfficeView(entry.path))
            FileOps.isTextFile(f) && f.length() <= 2_000_000 -> show(Overlay.TextView(entry.path))
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
                                show(Overlay.ArchiveView(entry.path))
                            }
                        }
                    }
                    kind == "rar" -> {
                        working = true
                        scope.launch {
                            val encrypted = withContext(Dispatchers.IO) { ArchiveOps.isRarEncrypted(f) }
                            working = false
                            if (encrypted) {
                                dialog = DialogKind.RarPassword(entry)
                            } else {
                                show(Overlay.ArchiveView(entry.path))
                            }
                        }
                    }
                    else -> show(Overlay.ArchiveView(entry.path))
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
                // Seleksi hanya dibersihkan bila operasi berhasil (B5): bila
                // gagal, seleksi dipertahankan supaya pengguna bisa mengulang.
                selected = emptySet()
            } else {
                notify(t.opFailed)
            }
        }
    }

    BackHandler(enabled = showSettings && !settingsExiting) { settingsExiting = true }
    BackHandler(enabled = !showSettings && overlay != null) { overlay = overlayReturn; overlayReturn = null }
    BackHandler(enabled = !showSettings && overlay == null && browseStack.isEmpty() && homeTab != 0) { homeTab = 0 }
    BackHandler(enabled = showTrash) { showTrash = false }
    BackHandler(enabled = showApps) { showApps = false }
    BackHandler(enabled = showAbout && !showSpecs) { showAbout = false }
    BackHandler(enabled = showSpecs) { showSpecs = false }
    BackHandler(enabled = showFormats) { showFormats = false }
    BackHandler(enabled = overlay == null && selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(enabled = overlay == null && selected.isEmpty() && browseStack.isNotEmpty() && safTreeUri == null) {
        browseStack = browseStack.dropLast(1)
    }

    // Animasi keluar Pengaturan: tunggu 300ms (durasi exit) sebelum
    // benar-benar menutup, supaya layar sempat meluncur keluar.
    LaunchedEffect(settingsExiting) {
        if (settingsExiting) {
            delay(300)
            showSettings = false
            settingsExiting = false
        }
    }

    // r137 TM1: Box akar supaya tangkapan layar lama bisa menutupi
    // seluruh isi (semua cabang layar) saat memudar.
    Box(Modifier.fillMaxSize()) {
    SysFileTheme(themeMode, uiStyle) {
        if (safTreeUri != null) {
            SafBrowseScreen(
                treeUri = safTreeUri!!,
                relPath = safRelPath,
                basePath = safBasePath ?: "",
                lockedPaths = lockedFolders,
                unlockedPaths = unlockedSession,
                onRequestUnlock = { path, grant -> requestUnlock(path, grant) },
                onExit = { safTreeUri = null; safBasePath = null },
                notify = { notify(it) }
            )
        } else if (showTrash) {
            TrashScreen(
                onBack = { showTrash = false },
                notify = { notify(it) },
                lockedPaths = lockedFolders,
                unlockedPaths = unlockedSession,
                onRequestUnlock = { path, grant -> requestUnlock(path, grant) }
            )
        } else if (showApps) {
            AppsScreen(notify = { notify(it) }, onBack = { showApps = false })
        } else if (showSpecs) {
            SpecScreen(onBack = { showSpecs = false })
        } else if (showFormats) {
            FormatsScreen(onBack = { showFormats = false })
        } else if (showAbout) {
            AboutScreen(
                onBack = { showAbout = false },
                onFormats = { showFormats = true },
                onOpenStorage = {
                    showAbout = false
                    showSettings = false
                    homeTab = 0
                    // L18: pakai state roots (bukan storageRoots di main
                    // thread); bila root internal belum ada di state,
                    // segarkan dulu.
                    val root = roots.firstOrNull { it.kind == "internal" }?.path
                    if (root != null) {
                        openFolderGuarded(root) { browseStack = listOf(root) }
                    } else {
                        refreshRoots()
                    }
                },
                onOpenSpecs = { showSpecs = true }
            )
        } else if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                language = language,
                showHidden = showHidden,
                showRootEntry = showRootEntry,
                trashEnabled = trashEnabled,
                onTheme = onThemeChange,
                uiStyle = uiStyle,
                onUiStyle = onUiStyleChange,
                onLanguage = { lang ->
                    language = lang
                    AppLang.code = lang
                    prefs.edit().putString("app_language", lang).apply()
                },
                onHidden = { v ->
                    showHidden = v
                    prefs.edit().putBoolean("show_hidden", v).apply()
                },
                onRootEntry = { v ->
                    showRootEntry = v
                    prefs.edit().putBoolean("root_entry", v).apply()
                    // Prompt root hanya saat sakelar dihidupkan. Bila belum/tidak
                    // diberi akses root, sakelar tetap ON agar kartu Root tampil
                    // dalam mode terbatas (read-only non-root).
                    if (v) {
                        scope.launch {
                            // r204: tiap kali dihidupkan, coba dari nol —
                            // DENIED dari percobaan sebelumnya (su sempat
                            // gagal) tidak boleh mengunci root seumur proses.
                            RootShell.resetRootState()
                            val ok = withContext(Dispatchers.IO) { RootShell.ensureRoot() }
                            rootActive = ok
                            invalidateRootCaches()
                            // r204: hanya ingat keberhasilan — kegagalan
                            // sementara tidak boleh menghapus ingatan bahwa
                            // root pernah diberikan.
                            if (ok) {
                                prefs.edit().putBoolean("root_granted_before", true).apply()
                                refreshRoots()
                            } else {
                                notify(t.rootLimited)
                            }
                        }
                    } else {
                        rootActive = false
                        invalidateRootCaches()
                    }
                },
                onTrashEnabled = { v ->
                    trashEnabled = v
                    prefs.edit().putBoolean("trash_enabled", v).apply()
                },
                onApps = { showApps = true },
                onAbout = { showAbout = true },
                exiting = settingsExiting,
                onBack = { if (!settingsExiting) settingsExiting = true }
            )
        } else if (!welcomeDone) {
            // WB4 Layar sambutan (r113, pilihannya): pager 2 halaman —
            // perkenalan, lalu izin. Selesai/dilewati = pref welcome_done.
            WelcomeScreen(
                accessGranted = accessGranted,
                onRequestAccess = { requestAccess() },
                onFinish = {
                    prefs.edit().putBoolean("welcome_done", true).apply()
                    welcomeDone = true
                },
                themeMode = themeMode,
                onTheme = onThemeChange
            )
        } else {
            // r88: Box pembungkus supaya pill audio bisa melayang
            // di atas layar overlay (kategori/arsip/viewer).
            Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    if (initialState is Overlay.CategoryView || targetState is Overlay.CategoryView ||
                        initialState is Overlay.ArchiveView || targetState is Overlay.ArchiveView ||
                        initialState is Overlay.ImgView || targetState is Overlay.ImgView ||
                        initialState is Overlay.Ext4View || targetState is Overlay.Ext4View ||
                        initialState is Overlay.FatView || targetState is Overlay.FatView ||
                        initialState is Overlay.LpView || targetState is Overlay.LpView ||
                        initialState is Overlay.PayloadView || targetState is Overlay.PayloadView ||
                        initialState is Overlay.DexView || targetState is Overlay.DexView ||
                        initialState is Overlay.ArscView || targetState is Overlay.ArscView ||
                        initialState is Overlay.ProfView || targetState is Overlay.ProfView) {
                        fadeIn(animationSpec = fadeSpec) togetherWith
                            fadeOut(animationSpec = fadeSpec)
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "overlayAnim"
            ) { ov -> Box(Modifier.fillMaxSize()) {
                when (ov) {
                    is Overlay.ImageView -> ImageViewerScreen(
                        paths = ov.paths,
                        startIndex = ov.startIndex,
                        onBack = { overlay = overlayReturn; overlayReturn = null; viewingPath = null },
                        onShare = { p -> shareFile(File(p)) },
                        onInfo = { p -> dialog = DialogKind.Properties(FileOps.entryOf(File(p))) },
                        onExif = { p -> dialog = DialogKind.Exif(FileOps.entryOf(File(p))) },
                        onDelete = { p -> dialog = DialogKind.Delete(listOf(FileOps.entryOf(File(p)))) },
                        onPathChange = { viewingPath = it }
                    )
                    is Overlay.TextView -> TextViewerScreen(ov.path, onBack = { overlay = overlayReturn; overlayReturn = null }, notify = { notify(it) }, originalPath = ov.originalPath)
                    is Overlay.FontView -> FontViewerScreen(path = ov.path, onBack = { overlay = overlayReturn; overlayReturn = null })
                    is Overlay.ArchiveText -> TextViewerScreen(
                        ov.name,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        notify = { notify(it) },
                        extractedText = ov.content,
                        onSaveArchiveText = if (ov.canSave) { { newText -> ArchiveOps.replaceZipEntry(File(ov.archivePath), ov.name, newText) } } else null,
                        onSaved = { val cur = overlayReturn; if (cur is Overlay.ArchiveView) overlayReturn = cur.copy(reload = cur.reload + 1) }
                    )
                    is Overlay.RamdiskText -> TextViewerScreen(
                        ov.name,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        notify = { notify(it) },
                        extractedText = ov.content
                    )
                    is Overlay.Ext4Text -> TextViewerScreen(
                        ov.name,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        notify = { notify(it) },
                        extractedText = ov.content
                    )
                    is Overlay.FatText -> TextViewerScreen(
                        ov.name,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        notify = { notify(it) },
                        extractedText = ov.content
                    )
                    is Overlay.PdfView -> PdfViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onOpenExternal = { openExternal(File(ov.path)) }
                    )
                    is Overlay.EpubView -> EpubReaderScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    is Overlay.OfficeView -> OfficeViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        notify = { notify(it) },
                    )
                    is Overlay.VideoView -> VideoViewerScreen(
                        paths = ov.paths,
                        startIndex = ov.startIndex,
                        onBack = { overlay = overlayReturn; overlayReturn = null; viewingPath = null },
                        onPathChange = { viewingPath = it },
                        notify = { notify(it) }
                    )
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
                                    if (browseStack.lastOrNull() != parent) {
                                        // L12: masuk folder terkunci lewat
                                        // gerbang (biometrik), bukan
                                        // browseStack langsung.
                                        openFolderGuarded(parent) {
                                            browseStack = listOf(parent)
                                            openEntry(entry)
                                        }
                                    } else {
                                        openEntry(entry)
                                    }
                                }
                            }
                        }
                    )
                    is Overlay.CategoryView -> CategoryScreen(
                        category = ov.category,
                        categoryCache = categoryCache,
                        categoryCacheAt = categoryCacheAt,
                        categoryCacheKey = categoryCacheKey,
                        reloadKey = reloadKey,
                        roots = roots,
                        showHidden = showHidden,
                        accessGranted = accessGranted,
                        lockedFolders = lockedFolders,
                        onBack = { overlay = null; overlayReturn = null },
                        onOpenEntry = { entry, siblings ->
                            overlayReturn = null
                            openEntry(entry, siblings) { _ -> overlayReturn = ov }
                        }
                    )
                    is Overlay.ArchiveView -> ArchiveScreen(
                        path = ov.path,
                        reloadKey = ov.reload,
                        initialPrefix = if (archivePrefixPath == ov.path) archivePrefix else "",
                        onPrefixChange = { archivePrefixPath = ov.path; archivePrefix = it },
                        onBack = { overlay = overlayReturn; overlayReturn = null; archivePrefix = ""; archivePrefixPath = null },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed); archivePrefix = ""; archivePrefixPath = null },
                        onExtractAll = {
                            val archiveFile = File(ov.path)
                            overlay = null
                            overlayReturn = null
                            archivePrefix = ""; archivePrefixPath = null
                            // LD3 r160: ekstrak arsip berprogres + bisa dibatalkan.
                            runExtractOp(
                                name = archiveFile.name,
                                format = { done, total -> t.extractProgress(done, total) }
                            ) { flag, onProgress ->
                                ArchiveOps.extractToFolder(
                                    archiveFile, archiveFile.parentFile ?: archiveFile,
                                    onProgress = onProgress, cancelFlag = flag
                                )
                            }
                        },
                        onOpenFile = { itemName ->
                            val archiveFile = File(ov.path)
                            working = true
                            scope.launch {
                                // Berkas teks (HTML, txt, md, ...) dibaca dari
                                // dalam arsip ke memori — tidak ada berkas yang
                                // diekstrak ke penyimpanan. Selain teks, ekstrak
                                // ke cache aplikasi (privat, tak terlihat) lalu
                                // buka dari sana — bukan lagi ke folder ZIP.
                                val (text, canSave) = withContext(Dispatchers.IO) {
                                    val t = if (FileOps.isTextFile(File(itemName))) {
                                        ArchiveOps.readEntryText(archiveFile, itemName)
                                    } else null
                                    t to (t != null && ArchiveOps.canReplaceEntry(archiveFile))
                                }
                                if (text != null) {
                                    working = false
                                    overlayReturn = ov
                                    overlay = Overlay.ArchiveText(itemName, text, archiveFile.path, canSave)
                                } else {
                                    val out = withContext(Dispatchers.IO) {
                                        val cache = File(context.cacheDir, "archive-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                        ArchiveOps.extractSingle(archiveFile, itemName, cache)
                                    }
                                    working = false
                                    if (out != null) {
                                        overlayReturn = ov
                                        openEntry(FileOps.entryOf(out))
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }
                        }
                    )
                    is Overlay.ImgView -> ImgBrowseScreen(
                        path = ov.path,
                        initialPrefix = if (imgPrefixPath == ov.path) imgPrefix else "",
                        onPrefixChange = { imgPrefixPath = ov.path; imgPrefix = it },
                        onBack = { overlay = overlayReturn; overlayReturn = null; imgPrefix = ""; imgPrefixPath = null },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed); imgPrefix = ""; imgPrefixPath = null },
                        onExtractAll = {
                            val imgFile = File(ov.path)
                            overlay = null
                            overlayReturn = null
                            imgPrefix = ""; imgPrefixPath = null
                            // LD3 r160: ekstrak ramdisk berprogres + bisa dibatalkan.
                            runExtractOp(
                                name = imgFile.name,
                                format = { done, total -> t.extractProgress(done, total) }
                            ) { flag, onProgress ->
                                ImgOps.extractRamdiskAll(
                                    imgFile, imgFile.parentFile ?: imgFile,
                                    onProgress = onProgress, cancelFlag = flag
                                )
                            }
                        },
                        onOpenFile = { itemName ->
                            val imgFile = File(ov.path)
                            working = true
                            scope.launch {
                                // Teks ramdisk (init.rc, *.prop) tidak punya
                                // ekstensi teks — kenali dari isi (byte NUL
                                // = biner), jangan lebarkan isTextFile.
                                // Bukan teks -> ekstrak ke cache lalu buka
                                // seperti berkas biasa.
                                val text = withContext(Dispatchers.IO) { ImgOps.readRamdiskEntryText(imgFile, itemName) }
                                if (text != null) {
                                    working = false
                                    overlayReturn = ov
                                    overlay = Overlay.RamdiskText(itemName, text)
                                } else {
                                    val out = withContext(Dispatchers.IO) {
                                        val cache = File(context.cacheDir, "ramdisk-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                        ImgOps.extractRamdiskEntry(imgFile, itemName, cache)
                                    }
                                    working = false
                                    if (out != null) {
                                        overlayReturn = ov
                                        openEntry(FileOps.entryOf(out))
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }
                        },
                        onExtractOne = { itemName ->
                            val imgFile = File(ov.path)
                            runExtractOp(name = itemName, determinate = false) { _, _ ->
                                ImgOps.extractRamdiskEntry(imgFile, itemName, imgFile.parentFile ?: imgFile) != null
                            }
                        }
                    )
                    is Overlay.Ext4View -> Ext4BrowseScreen(
                        path = ov.path,
                        session = ext4Session,
                        initialPrefix = if (ext4PrefixPath == ov.path) ext4Prefix else "",
                        onPrefixChange = { ext4PrefixPath = ov.path; ext4Prefix = it },
                        onBack = { overlay = overlayReturn; overlayReturn = null; closeExt4Session() },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed); closeExt4Session() },
                        onExtractAll = {
                            val imgFile = File(ov.path)
                            val session = ext4Session
                            overlay = null
                            overlayReturn = null
                            ext4Prefix = ""; ext4PrefixPath = null
                            ext4Session = null
                            // Bila layar ini dibuka dari daftar partisi
                            // super.img, sesi LP ikut ditutup — pengguna
                            // meninggalkan alur super.img sepenuhnya.
                            closeLpSession()
                            if (session == null) {
                                notify(t.opFailed)
                            } else {
                                // Sesi harus tetap hidup selama ekstrak
                                // berjalan — ditutup di finally blok runLongOp.
                                // LD3 r160: ekstrak ext4 berprogres + bisa dibatalkan.
                                runExtractOp(
                                    name = imgFile.name,
                                    format = { done, total -> t.extractProgress(done, total) }
                                ) { flag, onProgress ->
                                    try {
                                        session.reader.extractAll(
                                            imgFile.parentFile ?: imgFile,
                                            onProgress = onProgress, cancelFlag = flag
                                        )
                                    } finally {
                                        session.close()
                                    }
                                }
                            }
                        },
                        onOpenFile = { item ->
                            val imgFile = File(ov.path)
                            val session = ext4Session
                            working = true
                            scope.launch {
                                val text = if (session != null) {
                                    withContext(Dispatchers.IO) { session.reader.readFileText(item.inode) }
                                } else null
                                if (text != null) {
                                    working = false
                                    overlayReturn = ov
                                    overlay = Overlay.Ext4Text(item.name, text)
                                } else {
                                    val out = if (session != null) {
                                        withContext(Dispatchers.IO) {
                                            val cache = File(context.cacheDir, "ext4-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                            session.reader.extractEntry(item.inode, item.name, cache)
                                        }
                                    } else null
                                    working = false
                                    if (out != null) {
                                        overlayReturn = ov
                                        openEntry(FileOps.entryOf(out))
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }
                        },
                        onExtractOne = { item ->
                            val imgFile = File(ov.path)
                            val session = ext4Session
                            if (session == null) {
                                notify(t.opFailed)
                            } else {
                                runExtractOp(name = item.name, determinate = false) { _, _ ->
                                    session.reader.extractEntry(item.inode, item.name, imgFile.parentFile ?: imgFile) != null
                                }
                            }
                        }
                    )
                    is Overlay.FatView -> FatBrowseScreen(
                        path = ov.path,
                        reader = fatReader,
                        initialPrefix = if (fatPrefixPath == ov.path) fatPrefix else "",
                        onPrefixChange = { fatPrefixPath = ov.path; fatPrefix = it },
                        onBack = { overlay = overlayReturn; overlayReturn = null; closeFatReader() },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed); closeFatReader() },
                        onExtractAll = {
                            val imgFile = File(ov.path)
                            val reader = fatReader
                            overlay = null
                            overlayReturn = null
                            fatPrefix = ""; fatPrefixPath = null
                            fatReader = null
                            if (reader == null) {
                                notify(t.opFailed)
                            } else {
                                // Pembaca harus tetap hidup selama ekstrak
                                // berjalan — ditutup di finally blok runLongOp.
                                // LD3 r160: ekstrak FAT berprogres + bisa dibatalkan.
                                runExtractOp(
                                    name = imgFile.name,
                                    format = { done, total -> t.extractProgress(done, total) }
                                ) { flag, onProgress ->
                                    try {
                                        reader.extractAll(
                                            imgFile.parentFile ?: imgFile,
                                            onProgress = onProgress, cancelFlag = flag
                                        )
                                    } finally {
                                        reader.close()
                                    }
                                }
                            }
                        },
                        onOpenFile = { itemPath ->
                            val reader = fatReader
                            working = true
                            scope.launch {
                                val text = if (reader != null) {
                                    withContext(Dispatchers.IO) { reader.readFileText(itemPath) }
                                } else null
                                if (text != null) {
                                    working = false
                                    overlayReturn = ov
                                    overlay = Overlay.FatText(itemPath.substringAfterLast('/'), text)
                                } else {
                                    val out = if (reader != null) {
                                        withContext(Dispatchers.IO) {
                                            val cache = File(context.cacheDir, "fat-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                            reader.extractEntry(itemPath, cache)
                                        }
                                    } else null
                                    working = false
                                    if (out != null) {
                                        overlayReturn = ov
                                        openEntry(FileOps.entryOf(out))
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }
                        },
                        onExtractOne = { itemPath ->
                            val imgFile = File(ov.path)
                            val reader = fatReader
                            if (reader == null) {
                                notify(t.opFailed)
                            } else {
                                runExtractOp(name = itemPath.substringAfterLast('/'), determinate = false) { _, _ ->
                                    reader.extractEntry(itemPath, imgFile.parentFile ?: imgFile) != null
                                }
                            }
                        }
                    )
                    is Overlay.LpView -> LpBrowseScreen(
                        path = ov.path,
                        session = lpSession,
                        onBack = { overlay = overlayReturn; overlayReturn = null; closeLpSession() },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed); closeLpSession() },
                        onOpenPartition = { part ->
                            val lp = lpSession
                            // Prefix ext4 direset supaya partisi yang
                            // dibuka berikutnya mulai dari root, bukan
                            // dari subfolder partisi sebelumnya (judul
                            // layar tetap nama super.img).
                            ext4Prefix = ""; ext4PrefixPath = null
                            if (lp == null) {
                                notify(t.opFailed)
                            } else {
                                // LD3 r159: materialize partisi (bisa
                                // multi-GB) berprogres + bisa dibatalkan.
                                val (flag, report, clear) = beginLongWork(t.workStageMaterialize)
                                scope.launch {
                                    var cancelled = false
                                    val session = try {
                                        withContext(Dispatchers.IO) {
                                            val dir = File(context.cacheDir, "lp-view").apply { FileOps.evictStaleCache(this); mkdirs() }
                                            val tmp = File(dir, "part-${System.nanoTime()}.img")
                                            if (lp.reader.materialize(part, tmp, onProgress = report, cancelFlag = flag)) {
                                                val reader = Ext4Reader.open(tmp)
                                                if (reader != null) Ext4Session(tmp, tmp, reader) else { tmp.delete(); null }
                                            } else null
                                        }
                                    } catch (_: ImgCancelledException) {
                                        cancelled = true
                                        null
                                    } finally {
                                        clear()
                                    }
                                    if (session != null) {
                                        ext4Session = session
                                        overlayReturn = Overlay.LpView(ov.path)
                                        overlay = Overlay.Ext4View(ov.path, lpPath = ov.path)
                                    } else if (cancelled) {
                                        notify(t.workCancelled)
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }
                        },
                        onExtractPartition = { part ->
                            val imgFile = File(ov.path)
                            val lp = lpSession
                            if (lp == null) {
                                notify(t.opFailed)
                            } else {
                                // LD3 r159: unduh partisi berprogres + bisa dibatalkan.
                                runExtractOp(name = part.name + ".img") { flag, onProgress ->
                                    lp.reader.materialize(
                                        part,
                                        File(imgFile.parentFile ?: imgFile, part.name + ".img"),
                                        onProgress = onProgress,
                                        cancelFlag = flag
                                    )
                                }
                            }
                        }
                    )
                    is Overlay.PayloadView -> PayloadScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onError = { overlay = overlayReturn; overlayReturn = null; notify(t.opFailed) },
                        onUnsupported = { overlay = overlayReturn; overlayReturn = null; notify(t.payloadUnsupported) },
                        onExtractAll = { dialog = DialogKind.PayloadExtract(ov.path, null) },
                        onOpenPartition = { name -> dialog = DialogKind.PayloadExtract(ov.path, name) }
                    )
                    is Overlay.DexView -> DexBrowseScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    is Overlay.ArscView -> ArscBrowseScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    is Overlay.ProfView -> ProfBrowseScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null },
                        onOpenAsText = { overlay = Overlay.TextView(ov.path, originalPath = ov.originalPath) },
                        onOpenAsHex = { overlay = Overlay.HexView(ov.path) }
                    )
                    is Overlay.SqliteView -> SqliteViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    is Overlay.AxmlView -> AxmlViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    is Overlay.HexView -> HexViewerScreen(
                        path = ov.path,
                        onBack = { overlay = overlayReturn; overlayReturn = null }
                    )
                    null -> {
                        val isNothing = LocalNothingTheme.current
                        val barContainer = if (isNothing) Color.Transparent else MaterialTheme.colorScheme.surface
                        Scaffold(
                            // r164: latar dari skema, bukan Transparent —
                            // kecuali saat Tema 2 Nothing aktif (kanvas dot-matrix di Box akar).
                            containerColor = if (isNothing) Color.Transparent else MaterialTheme.colorScheme.background,
                            topBar = {
                                Column {
                                if (currentPath == null) {
                                    TopAppBar(
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = barContainer
                                        ),
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                SysFileLogo(
                                                    modifier = Modifier.size(40.dp),
                                                    cornerRadius = 11.dp
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(if (isNothing) "SYSFILE" else "SysFile")
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
                                    if (isNothing) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                } else if (selected.isNotEmpty()) {
                                    TopAppBar(
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = barContainer
                                        ),
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
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = barContainer
                                        ),
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
                                                // RM1: subjudul jadi ringkasan isi —
                                                // "8 folder · 30 item di Download" (folder
                                                // yang berubah hari ini + isinya); tanpa
                                                // folder hari ini, hanya jumlah folder.
                                                val browseFolders = entries.filter { it.isDir }
                                                val browseHighlight = browseFolders
                                                    .filter { isToday(it.modified) }
                                                    .maxByOrNull { it.modified }
                                                val browseHlCount = browseHighlight?.let { entryCounts[it.path] }
                                                val browseSubtitle = if (browseFolders.isEmpty()) {
                                                    if (entries.isEmpty()) "" else t.items(entries.size)
                                                } else if (browseHighlight != null && browseHlCount != null) {
                                                    "${t.folders(browseFolders.size)} · ${t.itemsIn(browseHlCount, browseHighlight.name)}"
                                                } else {
                                                    t.folders(browseFolders.size)
                                                }
                                                if (browseSubtitle.isNotEmpty()) {
                                                    Text(
                                                        browseSubtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { browseStack = browseStack.dropLast(1) }) {
                                                Icon(Icons.Default.ArrowBack, t.home)
                                            }
                                        },
                                        actions = {
                                            Box {
                                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, null) }
                                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text(t.home) },
                                                        leadingIcon = { Icon(Icons.Default.Home, null) },
                                                        onClick = { menuOpen = false; browseStack = emptyList() }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(t.selectAll) },
                                                        leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                                                        onClick = {
                                                            menuOpen = false
                                                            selected = entries.map { it.path }.toSet()
                                                        }
                                                    )
                                                    if (clipboard != null) {
                                                        DropdownMenuItem(
                                                            text = { Text(t.paste) },
                                                            leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                                            onClick = {
                                                                menuOpen = false
                                                                val (paths, mode) = clipboard ?: return@DropdownMenuItem
                                                                val destDir = File(currentPath)
                                                                runCardOp(
                                                                    name = if (paths.size == 1) File(paths[0]).name else t.items(paths.size),
                                                                    successMsg = if (mode == ClipMode.COPY) t.copiedDone else t.movedDone,
                                                                    onSuccess = { clipboard = null }
                                                                ) {
                                                                    val files = paths.map { File(it) }
                                                                    if (mode == ClipMode.COPY) {
                                                                        FileOps.copyInto(files, destDir) > 0
                                                                    } else {
                                                                        FileOps.moveInto(files, destDir) > 0
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(t.sortBy) },
                                                        leadingIcon = { Icon(Icons.Default.Sort, null) },
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
                                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
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
                                        sortKey = sortKey,
                                        sortAsc = sortAsc,
                                        onNavigate = { browseStack = it },
                                        onSearch = { overlay = Overlay.Search(currentPath) },
                                        onSortPick = { key, asc ->
                                            sortKey = key
                                            sortAsc = asc
                                            prefs.edit().putString("sort_key", key.name)
                                                .putBoolean("sort_asc", asc).apply()
                                        },
                                        onToggleView = {
                                            gridView = !gridView
                                            prefs.edit().putBoolean("grid_view", gridView).apply()
                                        }
                                    )
                                }
                                }
                            },
                            bottomBar = {
                                // navigationBarsPadding: nav bar mengambang tidak
                                // boleh menempel pada navigation bar sistem (gestur).
                                Column(Modifier.navigationBarsPadding()) {
                                    // r82: bar hanya tampil di layar asal audio
                                    // (folder/kategori/Recent tempat audio dimulai);
                                    // di Beranda dan layar lain disembunyikan.
                                    if (audioOrigin == currentScreenId()) {
                                        audioPath?.let { path ->
                                            MiniPlayerBar(
                                                name = File(path).name,
                                                positionMs = audioPositionMs,
                                                durationMs = audioDurationMs,
                                                playing = audioPlaying,
                                                onToggle = { toggleAudio() },
                                                onClose = { stopAudio() }
                                            )
                                        }
                                    }
                                    // r123: nilai pilihan pertama dihitung
                                    // SEKALI di sini dengan firstOrNull —
                                    // isi AnimatedVisibility tetap terkomposisi
                                    // selama animasi keluar, dan selected
                                    // sudah kosong saat itu; first()
                                    // di dalam bar melempar
                                    // NoSuchElementException -> FC.
                                    val selFirst = selected.firstOrNull()
                                    val selEntry = selFirst?.let { f -> entries.firstOrNull { it.path == f } }
                                    // Bar aksi seleksi (r109): muncul/hilang dengan animasi
                                    // halus yang sama seperti kapsul kontrol video (r104) —
                                    // fade + geser dari bawah + kembang/susut vertikal.
                                    AnimatedVisibility(
                                        visible = selected.isNotEmpty() && currentPath != null,
                                        enter = fadeIn(animationSpec = motion.defaultEffectsSpec<Float>()) +
                                            slideInVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { it } +
                                            expandVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), expandFrom = Alignment.Bottom),
                                        exit = fadeOut(animationSpec = motion.defaultEffectsSpec<Float>()) +
                                            slideOutVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { it } +
                                            shrinkVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), shrinkTowards = Alignment.Bottom)
                                    ) {
                                        SelectionBar(
                                            single = selected.size == 1,
                                            singleEntry = selEntry,
                                            canExtract = entries.any { selected.contains(it.path) && !it.isDir && ArchiveOps.kindOf(it.name) != null },
                                            onCopy = { clipboard = selected.toList() to ClipMode.COPY; selected = emptySet() },
                                            onCut = { clipboard = selected.toList() to ClipMode.CUT; selected = emptySet() },
                                            onDelete = { dialog = DialogKind.Delete(entries.filter { selected.contains(it.path) }) },
                                            onShare = { selEntry?.let { shareFile(it.file) } },
                                            onRename = { selEntry?.let { dialog = DialogKind.Rename(it) } },
                                            onCompress = { dialog = DialogKind.Compress(entries.filter { selected.contains(it.path) }) },
                                            onProperties = { selEntry?.let { dialog = DialogKind.Properties(it) } },
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
                                                            dialog = DialogKind.ArchiveActions(only)
                                                        }
                                                    }
                                                } else if (only != null && ArchiveOps.kindOf(only.name) == "rar") {
                                                    working = true
                                                    scope.launch {
                                                        val enc = withContext(Dispatchers.IO) { ArchiveOps.isRarEncrypted(only.file) }
                                                        working = false
                                                        if (enc) {
                                                            dialog = DialogKind.RarPassword(only)
                                                        } else {
                                                            dialog = DialogKind.ArchiveActions(only)
                                                        }
                                                    }
                                                } else if (only != null) {
                                                    dialog = DialogKind.ArchiveActions(only)
                                                } else if (targets.isNotEmpty()) {
                                                    runExtractOp(
                                                        name = if (targets.size == 1) targets.first().name else t.items(targets.size),
                                                        format = { done, total -> t.extractProgress(done, total) }
                                                    ) { _, onProgress ->
                                                        var done = 0L
                                                        targets.count {
                                                            val r = ArchiveOps.extractToFolder(it.file, it.file.parentFile ?: it.file)
                                                            done++
                                                            onProgress(done, targets.size.toLong())
                                                            r
                                                        } > 0
                                                    }
                                                }
                                            },
                                            onSelectAll = { selected = entries.map { it.path }.toSet() },
                                            singleIsDir = selEntry?.isDir == true,
                                            singleLocked = selected.singleOrNull()?.let { FileOps.isLockedFolder(it, lockedFolders) } == true,
                                            onToggleLock = {
                                                selEntry?.let { entry ->
                                                    // r175 H4: identitas kunci
                                                    // per folder (mentah atau
                                                    // kanonis), bukan anaknya.
                                                    val canon = FileOps.canonicalOrNull(entry.path)
                                                    if (FileOps.isLockedFolder(entry.path, lockedFolders)) {
                                                        requestUnlock(entry.path) {
                                                            val updated = lockedFolders.filterNot {
                                                                it == entry.path || (canon != null && it == canon)
                                                            }.toSet()
                                                            lockedFolders = updated
                                                            prefs.edit().putStringSet("locked_folders", updated).apply()
                                                            unlockedSession = unlockedSession.filterNot {
                                                                it == entry.path || (canon != null && it == canon)
                                                            }.toSet()
                                                            categoryCache.clear()
                                                            reloadKey++
                                                            selected = emptySet()
                                                            notify(t.folderUnlockedDone)
                                                        }
                                                    } else if (entry.name != FileOps.TRASH_DIR_NAME) {
                                                        val updated = FileOps.canonicalizeLocked(lockedFolders + entry.path)
                                                        lockedFolders = updated
                                                        prefs.edit().putStringSet("locked_folders", updated).apply()
                                                        categoryCache.clear()
                                                        reloadKey++
                                                        selected = emptySet()
                                                        notify(t.folderLockedDone)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    if (selected.isEmpty() || currentPath == null) {
                                        if (currentPath != null) {
                                        clipboard?.let { clip ->
                                            ClipboardBar(
                                                count = clip.first.size,
                                                onPaste = {
                                                    val (paths, mode) = clip
                                                    val destDir = File(currentPath)
                                                    runCardOp(
                                                        name = if (paths.size == 1) File(paths[0]).name else t.items(paths.size),
                                                        successMsg = if (mode == ClipMode.COPY) t.copiedDone else t.movedDone,
                                                        onSuccess = { clipboard = null }
                                                    ) {
                                                        val files = paths.map { File(it) }
                                                        if (mode == ClipMode.COPY) FileOps.copyInto(files, destDir) > 0
                                                        else FileOps.moveInto(files, destDir) > 0
                                                    }
                                                },
                                                onClear = { clipboard = null }
                                            )
                                        }
                                    } else {
                                        SysFileNavBar(homeTab = homeTab, onSelect = { homeTab = it })
                                    }
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
                                ) { animatedPath -> Box(Modifier.fillMaxSize()) {
                                    if (animatedPath == null) {
                                        AnimatedContent(
                                            targetState = homeTab,
                                            transitionSpec = {
                                                fadeIn(animationSpec = fadeSpec) togetherWith
                                                    fadeOut(animationSpec = fadeSpec)
                                            },
                                            label = "tabAnim"
                                        ) { tab -> Box(Modifier.fillMaxSize()) {
                                            when (tab) {
                                                1 -> RecentScreen(
                                                    files = recentFiles,
                                                    accessGranted = accessGranted,
                                                    onOpenEntry = { entry ->
                                                        if (entry.isDir) {
                                                            openFolderGuarded(entry.path) { browseStack = listOf(entry.path) }
                                                        } else {
                                                            openEntry(entry, recentFiles)
                                                        }
                                                    },
                                                    isRefreshing = recentRefreshing,
                                                    onRefresh = {
                                                        recentRefreshing = true
                                                        reloadKey++
                                                    }
                                                )
                                                2 -> AnalysisScreen(
                                                    roots = roots,
                                                    lockedFolders = lockedFolders,
                                                    reloadKey = reloadKey,
                                                    trashEnabled = trashEnabled,
                                                    onChanged = { reloadKey++ },
                                                    onOpenFolder = { path -> homeTab = 0; openFolderGuarded(path) { browseStack = listOf(path) } },
                                                    onOpenFile = { entry -> homeTab = 0; openEntry(entry) }
                                                )
                                                else -> HomeScreen(
                                                    accessGranted = accessGranted,
                                                    roots = if (showRootEntry) allRoots else roots,
                                                    rootActive = rootActive,
                                                    trashEnabled = trashEnabled,
                                                    onRequestAccess = { requestAccess() },
                                                    onOpenRoot = { browseStack = listOf(it) },
                                                    onOpenCategory = { cat -> overlay = Overlay.CategoryView(cat) },
                                                    onOpenTrash = { showTrash = true },
                                                    isRefreshing = homeRefreshing,
                                                    onRefresh = {
                                                        homeRefreshing = true
                                                        refreshRoots { homeRefreshing = false }
                                                    }
                                                )
                                            }
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
                                        lockedPaths = lockedFolders,
                                        safHasGrant = safGrantFor(animatedPath) != null,
                                        onSafAction = {
                                            val g = safGrantFor(animatedPath)
                                            if (g != null) {
                                                // r175 H3: folder terkunci
                                                // butuh biometrik sebelum
                                                // layar SAF dibuka.
                                                val openSaf = {
                                                    safRelPath = g.second
                                                    safTreeUri = g.first
                                                    safBasePath = g.third
                                                }
                                                if (FileOps.isLockedPath(animatedPath, lockedFolders) &&
                                                    !FileOps.isUnlockedPath(animatedPath, unlockedSession)
                                                ) {
                                                    requestUnlock(animatedPath, openSaf)
                                                } else {
                                                    openSaf()
                                                }
                                            } else {
                                                safRequestedPath = animatedPath
                                                safLauncher.launch(SafOps.initialUriFor(animatedPath))
                                            }
                                        },
                                        safSubtreeBlocked = SafPath.isSubtreeBlocked(Build.VERSION.SECURITY_PATCH),
                                        isRefreshing = browseRefreshing,
                                        onRefresh = {
                                            browseRefreshing = true
                                            invalidateRootCaches()
                                        }
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            } }
                // r88: pill audio di layar overlay — Scaffold +
                // bottomBar hanya ada saat overlay == null, jadi
                // tanpa ini audio dari kategori Audio tidak pernah
                // memunculkan pill. Aturan r82 tetap: hanya di layar
                // asal audio dimulai.
                if (overlay != null && audioOrigin == currentScreenId()) {
                    audioPath?.let { path ->
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            MiniPlayerBar(
                                name = File(path).name,
                                positionMs = audioPositionMs,
                                durationMs = audioDurationMs,
                                playing = audioPlaying,
                                onToggle = { toggleAudio() },
                                onClose = { stopAudio() }
                            )
                        }
                    }
                }
                // r194 PP3: pill pindah ke bawah header (di bawah
                // TopAppBar 64dp + jarak 8dp) + dibuat lebih compact.
                // Posisi atas tetap terlihat saat keyboard terbuka
                // (pill bawah lama bisa tertutup keyboard). Menempel
                // di Box akar, jadi tampil di layar browse maupun overlay.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp)
                ) {
                    AnimatedVisibility(
                        visible = pillVisible,
                        enter = fadeIn(animationSpec = motion.defaultEffectsSpec<Float>()) +
                            slideInVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { -it } +
                            expandVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), expandFrom = Alignment.Top),
                        exit = fadeOut(animationSpec = motion.defaultEffectsSpec<Float>()) +
                            slideOutVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { -it } +
                            shrinkVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), shrinkTowards = Alignment.Top)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 3.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier.widthIn(max = 320.dp)
                                .nothingBorder(RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { pillVisible = false }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(if (pillOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pillOk) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                    } else {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    pillMsg,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        themeShot?.let { shot ->
            Image(
                bitmap = shot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(themeShotAlpha.value),
                contentScale = ContentScale.FillBounds
            )
        }
        // r182: kartu progres ekstrak di TENGAH layar (EX2, dipindah ke
        // tengah atas permintaan Achmad) — non-modal: lapisan ini tidak
        // punya penangan sentuh apa pun, jadi sentuhan di luar kartu jatuh
        // ke konten di bawahnya; hanya kartu + tombol Batal yang menerima
        // sentuhan. Menempel di Box akar seperti pill, jadi tampil di
        // layar browse maupun overlay.
        val extractCardProg = extractCardProgress
        val extractCardFlag = extractCardCancelFlag
        AnimatedVisibility(
            visible = extractCardVisible,
            enter = fadeIn(animationSpec = motion.defaultEffectsSpec<Float>()) +
                scaleIn(animationSpec = motion.defaultSpatialSpec<Float>(), initialScale = 0.92f),
            exit = fadeOut(animationSpec = motion.defaultEffectsSpec<Float>()) +
                scaleOut(animationSpec = motion.defaultSpatialSpec<Float>(), targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .nothingBorder(RoundedCornerShape(24.dp))
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                extractCardName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (extractCardProg != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${(extractCardProg * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        SplitGapSolidProgressBar(
                            progress = extractCardProg,
                            modifier = Modifier.fillMaxWidth().padding(top = 13.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                extractCardText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (extractCardFlag != null) {
                                TextButton(onClick = { extractCardFlag.set(true) }) { Text(t.cancel) }
                            }
                        }
                    }
                }
            }
        }
        // r142: penutup SysFileTheme DIPINDAH ke akhir dialog
        // (dulu di sini) — host dialog di bawah ini harus di
        // DALAM tema, kalau tidak dialog memakai skema default
        // M3 terang (ungu) walau aplikasi mode gelap.

        if (working) {
            val prog = workProgress
            val flag = workCancelFlag
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().nothingBorder(RoundedCornerShape(24.dp))
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = workStage ?: t.working,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (prog != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "${(prog * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        SplitGapSolidProgressBar(
                            progress = prog,
                            modifier = Modifier.fillMaxWidth().padding(top = 13.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = workProgressText ?: (if (prog != null) "${(prog * 100).toInt()}%" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (flag != null) {
                                TextButton(onClick = { flag.set(true) }) { Text(t.cancel) }
                            }
                        }
                    }
                }
            }
        }

        if (showAccessDialog) {
            AlertDialog(
                onDismissRequest = { showAccessDialog = false },
                title = { Text(t.permissionTitle) },
                text = { Text(t.permissionBody) },
                confirmButton = {
                    TextButton(onClick = {
                        showAccessDialog = false
                        requestAccess()
                    }) { Text(t.permissionButton) }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessDialog = false }) { Text(t.later) }
                }
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
            is DialogKind.ZipPassword -> ArchivePasswordDialog(
                title = t.zipPasswordTitle,
                body = t.zipPasswordBody,
                onDismiss = { dialog = null },
                onConfirm = { password ->
                    dialog = null
                    val f = d.entry.file
                    // M2: ekstrak terenkripsi kini berprogres + bisa dibatalkan.
                    runExtractOp(
                        name = f.name,
                        format = { done, total -> t.extractProgress(done, total) }
                    ) { flag, onProgress ->
                        ArchiveOps.extractEncryptedZip(f, f.parentFile ?: f, password, onProgress = onProgress, cancelFlag = flag)
                    }
                }
            )
            is DialogKind.RarPassword -> ArchivePasswordDialog(
                title = t.rarPasswordTitle,
                body = t.rarPasswordBody,
                onDismiss = { dialog = null },
                onConfirm = { password ->
                    dialog = null
                    val f = d.entry.file
                    // M2: ekstrak terenkripsi kini berprogres + bisa dibatalkan.
                    runExtractOp(
                        name = f.name,
                        format = { done, total -> t.extractProgress(done, total) }
                    ) { flag, onProgress ->
                        ArchiveOps.extractEncryptedRar(f, f.parentFile ?: f, password, onProgress = onProgress, cancelFlag = flag)
                    }
                }
            )
            is DialogKind.Delete -> {
                val hasRootTarget = d.entries.any { RootShell.shouldUseShell(it.path) }
                val useTrash = trashEnabled && !hasRootTarget
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(if (useTrash) t.delete else t.deleteForever) },
                    text = {
                        val name = if (d.entries.size == 1) d.entries[0].name else t.items(d.entries.size)
                        Text(if (useTrash) t.deleteConfirm(name) else t.deleteForeverConfirm(name))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = null
                            // M13: memindahkan folder terkunci ke sampah
                            // butuh biometrik dulu — bila beberapa folder
                            // terkunci dipilih, minta satu per satu.
                            fun doDelete() {
                                val blockingLocked = d.entries.firstNotNullOfOrNull { entry ->
                                    FileOps.blockingLockedInside(entry.path, lockedFolders, unlockedSession)
                                }
                                if (blockingLocked != null) {
                                    requestUnlock(blockingLocked, ::doDelete)
                                } else {
                                    runCardOp(
                                        name = if (d.entries.size == 1) d.entries[0].name else t.items(d.entries.size),
                                        successMsg = if (useTrash) t.movedToTrash else t.deletedDone
                                    ) {
                                        var allOk = true
                                        d.entries.forEach {
                                            val ok = if (useTrash) TrashOps.moveToTrash(it.file)
                                            else FileOps.deleteRecursively(it.file)
                                            if (!ok) allOk = false
                                        }
                                        allOk
                                    }
                                    val viewing = viewingPath
                                    if (viewing != null && d.entries.any { it.path == viewing }) {
                                        overlay = null
                                        overlayReturn = null
                                        viewingPath = null
                                    }
                                }
                            }
                            doDelete()
                        }) { Text(if (useTrash) t.delete else t.deleteForever) }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text(t.cancel) } }
                )
            }
            is DialogKind.PayloadExtract -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(if (d.partition == null) t.extractAll else "${d.partition}.img") },
                text = { Text(if (d.partition == null) t.payloadExtractAllConfirm else t.payloadExtractOneConfirm(d.partition)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        val payloadFile = File(d.path)
                        val part = d.partition
                        if (part == null) {
                            overlay = null
                            overlayReturn = null
                            val flag = AtomicBoolean(false)
                            workCancelFlag = flag
                            workProgress = 0f
                            working = true
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    PayloadOps.extract(
                                        payloadFile, payloadFile.parentFile ?: payloadFile,
                                        onProgress = { done, total ->
                                            if (total > 0L) {
                                                workProgress =
                                                    (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            }
                                        },
                                        cancelFlag = flag
                                    )
                                }
                                working = false
                                workProgress = null
                                workCancelFlag = null
                                when {
                                    outcome.error == "unsupported" -> notify(t.payloadUnsupported)
                                    outcome.error == "cancelled" -> notify(t.payloadCancelled)
                                    outcome.error == "hash" -> notify(t.payloadHashMismatch)
                                    outcome.error != null -> notify(t.payloadInvalid)
                                    else -> {
                                        notify(t.payloadDone(outcome.partitions))
                                        reloadKey++
                                    }
                                }
                            }
                        } else {
                            val flag = AtomicBoolean(false)
                            workCancelFlag = flag
                            workProgress = 0f
                            working = true
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    PayloadOps.extractOne(
                                        payloadFile, part, payloadFile.parentFile ?: payloadFile,
                                        onProgress = { done, total ->
                                            if (total > 0L) {
                                                workProgress =
                                                    (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            }
                                        },
                                        cancelFlag = flag
                                    )
                                }
                                working = false
                                workProgress = null
                                workCancelFlag = null
                                when {
                                    outcome.error == "unsupported" -> notify(t.payloadUnsupported)
                                    outcome.error == "cancelled" -> notify(t.payloadCancelled)
                                    outcome.error == "hash" -> notify(t.payloadHashMismatch)
                                    outcome.error != null -> notify(t.opFailed)
                                    outcome.file != null -> {
                                        overlay = null
                                        openEntry(FileOps.entryOf(outcome.file))
                                    }
                                }
                            }
                        }
                    }) { Text(if (d.partition == null) t.extractAll else t.extract) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(t.cancel) } }
            )
            is DialogKind.Properties -> PropertiesDialog(entry = d.entry, onDismiss = { dialog = null }, onNotify = { notify(it) })
            is DialogKind.Exif -> ExifDialog(entry = d.entry, onDismiss = { dialog = null })
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
                    runExtractOp(
                        name = f.name,
                        format = { done, total -> t.extractProgress(done, total) }
                    ) { flag, onProgress ->
                        ArchiveOps.extractToFolder(f, f.parentFile ?: f, onProgress = onProgress, cancelFlag = flag)
                    }
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
                    runExtractOp(
                        name = f.name,
                        format = { done, total -> t.extractProgress(done, total) }
                    ) { flag, onProgress ->
                        ArchiveOps.extractToFolder(f, File(picked), onProgress = onProgress, cancelFlag = flag)
                    }
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
                    runExtractOp(
                        name = f.name,
                        format = { done, total -> t.extractProgress(done, total) }
                    ) { flag, onProgress ->
                        ArchiveOps.extractToFolder(f, f.parentFile ?: f, onProgress = onProgress, cancelFlag = flag)
                    }
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
    } // r142: penutup SysFileTheme (dipindah dari atas)
    }
}

private fun FEntry.parentPath(): String? = File(path).parent
