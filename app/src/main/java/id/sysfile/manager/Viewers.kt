package id.sysfile.manager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/** Penampil foto layar penuh: geser antar foto sefolder, cubit-zoom, ketuk untuk mode imersif, toolbar aksi melayang. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageViewerScreen(
    paths: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onInfo: (String) -> Unit,
    onExif: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPathChange: (String) -> Unit
) {
    val t = txt()
    // r224: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    val currentPath = paths[pagerState.currentPage]
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    var zooming by remember { mutableStateOf(false) }
    // Satu loader Coil dipakai ulang penampil + prefetch tetangga (r84).
    val imageLoader = rememberThumbLoader()
    // Ganti halaman: zoom direset + laporkan berkas yang sedang tampil
    // (untuk Bagikan/Properti/Detail EXIF/Hapus + penanda hapus di MainActivity).
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onPathChange(paths[pagerState.currentPage])
        // r174: prefetch tetangga (halaman +-1) dengan ukuran asli ke
        // memory cache loader yang sama, supaya bitmap sudah siap saat
        // halaman digeser — bukan decode di tengah geser. Efek ini juga
        // jalan saat penampil dibuka (currentPage awal = startIndex).
        // Kenapa ORIGINAL, bukan seukuran layar: kunci cache Coil 2.7.0
        // (MemoryCacheService) tidak memuat ukuran; yang dicek adalah
        // validitas ukuran bitmap tersimpan (isSizeValid). Bitmap
        // full-res yang belum di-sample memenuhi request berukuran apa
        // pun karena AsyncImagePainter memaksa Precision.INEXACT —
        // sedangkan prefetch seukuran layar rapuh: ukuran layout berubah
        // saat chrome tampil/sembunyi. Biaya: dua bitmap full-res di
        // memory cache selama penampil dibuka.
        for (neighbor in listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)) {
            if (neighbor in paths.indices) {
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(File(paths[neighbor]))
                        .size(Size.ORIGINAL)
                        .build()
                )
            }
        }
    }
    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                        title = {
                            Column {
                                Text(File(currentPath).name, maxLines = 1)
                                if (paths.size > 1) {
                                    Text(
                                        "${pagerState.currentPage + 1} / ${paths.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) }
                        }
                    )
                    if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                // Halaman tetangga dikompos + Coil mulai decode SEBELUM
                // geser (r162, r174: 2 tetangga tiap sisi + prefetch
                // ORIGINAL di LaunchedEffect): tanpa ini decode foto baru
                // mulai saat halaman masuk viewport di tengah geser,
                // halaman hitam sampai decode selesai lalu foto pop.
                // Video tidak kena karena VideoView + MediaPlayer
                // menyiapkan frame pertama jauh lebih cepat (~1 frame).
                // Default 0 di foundation 1.9.3.
                beyondViewportPageCount = 2,
                // Saat diperbesar atau sedang dicubit dua jari, geser halaman
                // dimatikan supaya cubit/pan milik gambar; saat skala 1 dan
                // satu jari, geser milik pager.
                userScrollEnabled = scale == 1f && !zooming,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pagePath = paths[page]
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(pagePath) {
                            detectTapGestures { chrome = !chrome }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = remember(pagePath) {
                            // Tanpa crossfade (r174): bitmap yang telat
                            // lalu fade-in di atas latar hitam terbaca
                            // sebagai bayangan ganda; tanpa crossfade foto
                            // muncul seketika begitu siap.
                            ImageRequest.Builder(context)
                                .data(File(pagePath))
                                .build()
                        },
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                            .pointerInput(pagePath) {
                                // Detektor sendiri (bukan detectTransformGestures):
                                // yang lama menelan SEMUA seret — termasuk seret
                                // satu jari saat skala 1 — sehingga pager di
                                // atasnya tidak pernah menerima geser halaman.
                                // Aturan baru: satu jari + skala 1 = jangan
                                // konsumsi apa pun (pager yang geser); dua jari
                                // = cubit-zoom milik gambar; skala > 1 = pan
                                // satu jari milik gambar.
                                awaitEachGesture {
                                    awaitFirstDown()
                                    var pinching = false
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.isConsumed }) break
                                        val down = event.changes.count { it.pressed }
                                        if (down >= 2) {
                                            if (!pinching) {
                                                pinching = true
                                                zooming = true
                                            }
                                            val pan = event.calculatePan()
                                            val newScale = (scale * event.calculateZoom())
                                                .coerceIn(1f, 6f)
                                            scale = newScale
                                            if (newScale > 1f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (scale > 1f) {
                                            val pan = event.calculatePan()
                                            offsetX += pan.x
                                            offsetY += pan.y
                                            event.changes.forEach { it.consume() }
                                        }
                                        // Skala 1 + satu jari: tidak konsumsi —
                                        // seretnya diteruskan ke pager.
                                    } while (event.changes.any { it.pressed })
                                    if (pinching) zooming = false
                                }
                            }
                    )
                }
            }
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                HorizontalFloatingToolbar(expanded = true) {
                    IconButton(onClick = { onShare(currentPath) }) { Icon(Icons.Default.Share, t.share) }
                    IconButton(onClick = { onInfo(currentPath) }) { Icon(Icons.Default.Info, t.properties) }
                    IconButton(onClick = { onExif(currentPath) }) { Icon(Icons.Default.CameraAlt, t.exifDetails) }
                    IconButton(onClick = { onDelete(currentPath) }) { Icon(Icons.Default.Delete, t.delete) }
                }
            }
        }
    }
}

/** Penampil/pengubah berkas teks sederhana (maks 2 MB yang dimuat).
 *  .md dan .html/.htm punya tombol pratinjau di bilah atas: HTML tampil
 *  apa adanya, Markdown di-render dulu jadi HTML (Markdown.kt) — keduanya
 *  di WebView yang sama (JS mati, base file:// folder induk supaya gambar
 *  relatif termuat). Teks/kode tetap tampilan default. Bila dipanggil
 *  dengan extractedText (teks hasil ekstrak Office, BC3), tampilannya
 *  read-only: tidak ada tombol edit/simpan. Pengecualian (AT1): bila
 *  onSaveArchiveText diberikan, teks dari dalam arsip ZIP bisa diedit
 *  dan disimpan kembali ke arsipnya. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextViewerScreen(
    path: String,
    onBack: () -> Unit,
    notify: (String) -> Unit,
    extractedText: String? = null,
    onSaveArchiveText: ((String) -> Boolean)? = null,
    onSaved: (() -> Unit)? = null,
    originalPath: String? = null,
    isLiquid: Boolean = false
) {
    val t = txt()
    // r214: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var failed by remember { mutableStateOf(false) }
    val displayPath = originalPath ?: path
    val fileName = remember(displayPath) { File(displayPath).name }
    val fileExt = remember(displayPath) { FileOps.extOf(fileName) }
    val isHtml = fileExt == "html" || fileExt == "htm"
    val isMarkdown = fileExt == "md"
    var preview by remember { mutableStateOf(false) }
    var previewTick by remember { mutableStateOf(0) }

    // Redesain: state navigasi & editor
    var wrapLines by remember { mutableStateOf(true) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatchIndex by remember { mutableStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }

    val vScrollView = rememberScrollState()
    val hScrollView = rememberScrollState()
    val vScrollEdit = rememberScrollState()

    // Deteksi apakah berkas berada di partisi sistem/root atau hanya bisa ditulis oleh superuser
    val isRootTarget = remember(displayPath) {
        val f = File(displayPath)
        (displayPath.startsWith("/") && !displayPath.startsWith("/storage") && !displayPath.startsWith("/sdcard")) ||
        (f.exists() && !f.canWrite())
    }

    LaunchedEffect(path) {
        if (extractedText != null) {
            content = extractedText
            editorValue = TextFieldValue(extractedText)
            return@LaunchedEffect
        }
        val text = withContext(Dispatchers.IO) {
            try {
                val f = File(path)
                val buf = ByteArray(8192)
                val out = ByteArrayOutputStream()
                f.inputStream().use { input ->
                    var remaining = 2_000_000
                    while (remaining > 0) {
                        val n = input.read(buf, 0, minOf(buf.size, remaining))
                        if (n < 0) break
                        out.write(buf, 0, n)
                        remaining -= n
                    }
                }
                String(out.toByteArray(), Charsets.UTF_8)
            } catch (e: Exception) {
                // Fallback: berkas sistem izin 0600 dibaca lewat staging root
                if (RootShell.state == RootShell.GRANTED || RootShell.ensureRoot()) {
                    val staged = RootShell.stageToCache(File(path), context.cacheDir)
                    if (staged != null && staged.exists()) {
                        try {
                            staged.readText(Charsets.UTF_8)
                        } catch (_: Exception) { null }
                    } else null
                } else null
            }
        }
        if (text == null) failed = true else {
            content = text
            editorValue = TextFieldValue(text)
        }
    }

    val currentText = if (editing) editorValue.text else (content ?: "")
    val lines = remember(currentText) { currentText.lines() }
    val lineCount = maxOf(lines.size, 1)

    // Pencarian
    val searchMatches = remember(currentText, searchQuery) {
        if (searchQuery.length < 2) emptyList<Int>()
        else {
            val list = mutableListOf<Int>()
            var idx = 0
            while (idx < currentText.length) {
                val found = currentText.indexOf(searchQuery, idx, ignoreCase = true)
                if (found == -1) break
                list.add(found)
                idx = found + searchQuery.length
            }
            list
        }
    }

    val saveAction = {
        val toSave = if (editing) editorValue.text else (content ?: "")
        isSaving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (onSaveArchiveText != null) {
                    onSaveArchiveText(toSave)
                } else {
                    val targetFile = File(displayPath)
                    val success = FileOps.writeTextSafe(targetFile, toSave, context.cacheDir)
                    if (success && originalPath != null && path != originalPath) {
                        try { File(path).writeText(toSave, Charsets.UTF_8) } catch (_: Exception) {}
                    }
                    success
                }
            }
            isSaving = false
            if (ok) {
                content = toSave
                editing = false
                notify(t.textSaved)
                if (onSaveArchiveText != null) onSaved?.invoke()
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
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fileName, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (isRootTarget) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "ROOT",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                displayPath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) }
                    },
                    actions = {
                        if (content != null) {
                            // Tombol pencarian
                            IconButton(onClick = { searchVisible = !searchVisible }) {
                                Icon(Icons.Default.Search, "Cari", tint = if (searchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isHtml || isMarkdown) {
                                if (preview) {
                                    IconButton(onClick = { previewTick++ }) { Icon(Icons.Default.Refresh, t.refresh) }
                                    IconButton(onClick = { preview = false }) { Icon(Icons.Default.Code, t.codeLabel) }
                                } else {
                                    IconButton(onClick = { preview = true }) { Icon(Icons.Default.Visibility, t.preview) }
                                }
                            }

                            if (!preview && editing) {
                                Button(
                                    onClick = { saveAction() },
                                    shape = RoundedCornerShape(20.dp),
                                    enabled = !isSaving,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(t.save, style = MaterialTheme.typography.labelMedium)
                                }
                            } else if (!preview && (extractedText == null || onSaveArchiveText != null)) {
                                IconButton(onClick = {
                                    editorValue = TextFieldValue(content ?: "")
                                    editing = true
                                }) { Icon(Icons.Default.Edit, t.edit) }
                            }
                        }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        },
        bottomBar = {
            if (content != null && !preview) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Accessory Bar saat mode edit (simbol-simbol konfigurasi yang sering dipakai)
                        if (editing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val quickChars = listOf("=", "\"", ":", "#", "[", "]", "{", "}", "/", "_", "-", ".")
                                    for (ch in quickChars) {
                                        Surface(
                                            onClick = {
                                                val cur = editorValue.text
                                                val sel = editorValue.selection
                                                val start = sel.min
                                                val end = sel.max
                                                val newText = cur.substring(0, start) + ch + cur.substring(end)
                                                editorValue = TextFieldValue(newText, TextRange(start + ch.length))
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow
                                        ) {
                                            Text(
                                                text = ch,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Status Info Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (editing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (editing) "Mode Edit" else "Mode Baca",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "$lineCount baris",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "UTF-8",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { wrapLines = !wrapLines },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (wrapLines) (if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest) else Color.Transparent,
                                    border = null,
                                    modifier = Modifier.then(if (wrapLines && isLiquid) Modifier.glassTint(RoundedCornerShape(4.dp), liquid.glassTint) else Modifier)
                                ) {
                                    Text(
                                        text = if (wrapLines) "Wrap: ON" else "Wrap: OFF",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (wrapLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = null,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(4.dp), liquid.glassTint) else Modifier)
                                ) {
                                    Text(
                                        text = fileExt.uppercase().ifEmpty { "TXT" },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).liquidWashBackground()) {
            // Bilah Pencarian Cepat (Expandable)
            AnimatedVisibility(visible = searchVisible && content != null) {
                Surface(
                    color = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(12.dp), liquid.glassTint) else Modifier),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f).padding(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Cari dalam berkas...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        )
                        if (searchMatches.isNotEmpty()) {
                            Text(
                                "${searchMatches.size} temuan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Bersihkan", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxSize().weight(1f)) {
                when {
                    failed -> Text(t.readFailed, Modifier.align(Alignment.Center))
                    content == null -> if (isLiquid) LiquidSpinner(Modifier.align(Alignment.Center).size(52.dp)) else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                    preview && (isHtml || isMarkdown) -> key(previewTick) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowFileAccessFromFileURLs = false
                                    settings.allowUniversalAccessFromFileURLs = false
                                    settings.domStorageEnabled = false
                                    settings.blockNetworkLoads = true
                                    val allowedDir = File(path).parent
                                        ?.takeIf { it.isNotEmpty() }?.let { File(it) }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): Boolean {
                                            if (WebViewPolicy.isExternalHttpLink(request.url.toString())) {
                                                try {
                                                    ctx.startActivity(
                                                        Intent.createChooser(
                                                            Intent(Intent.ACTION_VIEW, request.url),
                                                            null
                                                        )
                                                    )
                                                } catch (_: Exception) {
                                                    notify(t.openFailed)
                                                }
                                            }
                                            return true
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): WebResourceResponse? {
                                            if (request.isForMainFrame) return null
                                            val scheme = request.url.scheme?.lowercase()
                                            if (scheme != "file" && scheme != "http" && scheme != "https") return null
                                            val file = WebViewPolicy.resolveAllowedSubresource(
                                                request.url.toString(), allowedDir
                                            )
                                            if (file != null) {
                                                return try {
                                                    val mime = WebViewPolicy.mimeTypeFor(file.name)
                                                    val encoding = if (mime.startsWith("text/") ||
                                                        mime == "image/svg+xml" ||
                                                        mime == "application/json"
                                                    ) "UTF-8" else null
                                                    WebResourceResponse(mime, encoding, FileInputStream(file))
                                                } catch (_: Exception) {
                                                    WebResourceResponse(
                                                        "text/plain", "UTF-8",
                                                        ByteArrayInputStream(ByteArray(0))
                                                    )
                                                }
                                            }
                                            return WebResourceResponse(
                                                "text/plain", "UTF-8",
                                                ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                    }
                                }
                            },
                            update = { wv ->
                                val baseUrl = "file://" + (File(path).parent ?: "") + "/"
                                val html = if (isMarkdown) Markdown.toHtml(content ?: "") else (content ?: "")
                                wv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                            },
                            onRelease = { it.stopLoading(); it.destroy() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    editing -> Row(Modifier.fillMaxSize()) {
                        // Gutter nomor baris sinkron
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                                .verticalScroll(vScrollEdit)
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            val editLines = editorValue.text.lines().size
                            for (i in 1..maxOf(editLines, 1)) {
                                Text(
                                    text = "$i",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        lineHeight = 22.sp
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                        BasicTextField(
                            value = editorValue,
                            onValueChange = { editorValue = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(vScrollEdit)
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    else -> Row(Modifier.fillMaxSize()) {
                        // Gutter nomor baris sinkron mode baca
                        Column(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                                .verticalScroll(vScrollView)
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = "$i",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        lineHeight = 22.sp
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (!wrapLines) Modifier.horizontalScroll(hScrollView) else Modifier)
                                .verticalScroll(vScrollView)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = highlightSyntax(
                                    code = content ?: "",
                                    ext = fileExt,
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    secondaryColor = MaterialTheme.colorScheme.secondary,
                                    tertiaryColor = MaterialTheme.colorScheme.tertiary,
                                    outlineColor = MaterialTheme.colorScheme.outline,
                                    errorColor = MaterialTheme.colorScheme.error,
                                    searchQuery = searchQuery
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Penampil PDF bawaan: geser antar halaman, cubit-zoom, ketuk untuk
 *  menyembunyikan bilah atas. Render memakai PdfRenderer bawaan Android
 *  (tanpa dependensi baru); tiap halaman dirender saat tampil saja (IO
 *  thread, skala maks 2x, sisi terpanjang maks 2048px). Bitmap diisi
 *  putih dulu sebelum render (halaman PDF umumnya tanpa latar — kalau
 *  tidak, teks hitam hilang di latar hitam penampil). PDF terenkripsi /
 *  rusak tidak bisa dibuka PdfRenderer — tampil pesan gagal + tombol buka
 *  dengan aplikasi lain. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerScreen(path: String, onBack: () -> Unit, onOpenExternal: () -> Unit) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var openFailed by remember { mutableStateOf(false) }
    DisposableEffect(path) {
        var r: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        renderer = null
        openFailed = false
        try {
            pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            r = PdfRenderer(pfd)
            renderer = r
        } catch (e: Exception) {
            try { pfd?.close() } catch (_: Exception) { }
            openFailed = true
        }
        onDispose {
            try { r?.close() } catch (_: Exception) { }
        }
    }
    val pageCount = renderer?.pageCount ?: 0
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    var zooming by remember { mutableStateOf(false) }
    // Ganti halaman: zoom direset.
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                        title = {
                            Column {
                                Text(File(path).name, maxLines = 1)
                                if (pageCount > 1) {
                                    Text(
                                        "${pagerState.currentPage + 1} / $pageCount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) }
                        }
                    )
                    if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when {
                openFailed -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            t.pdfOpenFailed,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onOpenExternal) { Text(t.pdfOpenExternal) }
                    }
                }
                renderer == null -> {
                    if (isLiquid) LiquidSpinner(Modifier.align(Alignment.Center).size(52.dp)) else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                }
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        // Sama seperti penampil foto: saat diperbesar atau
                        // sedang dicubit dua jari, geser halaman dimatikan.
                        userScrollEnabled = scale == 1f && !zooming,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val r = renderer
                        var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
                        var renderFailed by remember(page) { mutableStateOf(false) }
                        LaunchedEffect(page, r) {
                            if (r == null) return@LaunchedEffect
                            val bmp = withContext(Dispatchers.IO) {
                                try {
                                    r.openPage(page).use { pg ->
                                        val s = minOf(2f, 2048f / maxOf(pg.width, pg.height).toFloat())
                                        val b = Bitmap.createBitmap(
                                            (pg.width * s).toInt().coerceAtLeast(1),
                                            (pg.height * s).toInt().coerceAtLeast(1),
                                            Bitmap.Config.ARGB_8888
                                        )
                                        // Halaman PDF umumnya tanpa latar:
                                        // isi putih dulu, kalau tidak teks
                                        // hitam hilang di latar hitam penampil.
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        b
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            bitmap = bmp
                            if (bmp == null) renderFailed = true
                        }
                        Box(
                            Modifier.fillMaxSize()
                                .pointerInput(page) {
                                    detectTapGestures { chrome = !chrome }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                renderFailed -> Text(
                                    t.pdfOpenFailed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                                bitmap != null -> Image(
                                    bitmap = bitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                                        .pointerInput(page) {
                                            // Detektor yang sama dengan penampil
                                            // foto: satu jari + skala 1 jangan
                                            // konsumsi apa pun (pager yang geser);
                                            // dua jari = cubit-zoom; skala > 1 =
                                            // pan satu jari milik halaman.
                                            awaitEachGesture {
                                                awaitFirstDown()
                                                var pinching = false
                                                do {
                                                    val event = awaitPointerEvent()
                                                    if (event.changes.any { it.isConsumed }) break
                                                    val down = event.changes.count { it.pressed }
                                                    if (down >= 2) {
                                                        if (!pinching) {
                                                            pinching = true
                                                            zooming = true
                                                        }
                                                        val pan = event.calculatePan()
                                                        val newScale = (scale * event.calculateZoom())
                                                            .coerceIn(1f, 6f)
                                                        scale = newScale
                                                        if (newScale > 1f) {
                                                            offsetX += pan.x
                                                            offsetY += pan.y
                                                        } else {
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        }
                                                        event.changes.forEach { it.consume() }
                                                    } else if (scale > 1f) {
                                                        val pan = event.calculatePan()
                                                        offsetX += pan.x
                                                        offsetY += pan.y
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                } while (event.changes.any { it.pressed })
                                                if (pinching) zooming = false
                                            }
                                        }
                                )
                                else -> if (isLiquid) LiquidSpinner(Modifier.size(52.dp)) else LoadingIndicator(Modifier.size(52.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pemutar video bawaan: geser antar video sefolder, kontrol Compose
 *  buatan sendiri (VI1, r74) — tombol play/pause, slider + waktu,
 *  ketuk video untuk tampil/sembunyi, Back selalu keluar. VideoView
 *  tetap dipakai untuk pemutaran (framework, tanpa library baru);
 *  kontrol bawaan Android diganti sepenuhnya. Kontrol bawah berupa
 *  kapsul mengambang (VP2, r76; VP6 r85: slider + waktu di dalam
 *  kapsul, baris atas; tombol prev/play/next di baris bawah). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoViewerScreen(paths: List<String>, startIndex: Int, onBack: () -> Unit, onPathChange: (String) -> Unit, notify: (String) -> Unit, isLiquid: Boolean = false) {
    val t = txt()
    // r214: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    val currentPath = paths[pagerState.currentPage]
    // VideoView tiap halaman (pager memuat halaman tetangga juga);
    // kontrol hanya mengendalikan halaman yang sedang tampil.
    val views = remember { mutableStateMapOf<Int, android.widget.VideoView>() }
    fun currentView() = views[pagerState.currentPage]
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekMs by remember { mutableStateOf(0) }
    // Skema gerak M3 Expressive untuk animasi kontrol (r104).
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(pagerState.currentPage) {
        onPathChange(paths[pagerState.currentPage])
        views.forEach { (p, vv) -> if (p != pagerState.currentPage) vv.pause() }
        currentView()?.let { vv ->
            if (!vv.isPlaying) vv.start()
            isPlaying = vv.isPlaying
            durationMs = vv.duration
            positionMs = vv.currentPosition
        }
        controlsVisible = true
    }
    // Posisi berjalan selama video diputar.
    LaunchedEffect(pagerState.currentPage, isPlaying) {
        val vv = currentView()
        if (vv != null && isPlaying) {
            while (true) {
                if (!isSeeking) positionMs = try { vv.currentPosition } catch (_: Exception) { positionMs }
                isPlaying = vv.isPlaying
                delay(250)
            }
        }
    }
    // Kontrol sembunyi sendiri 3 detik setelah tampil saat video jalan.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = motion.defaultEffectsSpec<Float>()) +
                    slideInVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { -it } +
                    expandVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), expandFrom = Alignment.Top),
                exit = fadeOut(animationSpec = motion.defaultEffectsSpec<Float>()) +
                    slideOutVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { -it } +
                    shrinkVertically(animationSpec = motion.defaultSpatialSpec<IntSize>(), shrinkTowards = Alignment.Top)
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(File(currentPath).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (paths.size > 1) {
                                    Text(
                                        "${pagerState.currentPage + 1} / ${paths.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                    if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black).liquidWashBackground()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pagePath = paths[page]
                key(pagePath) {
                    // Ketuk video tampil/sembunyi kontrol: detektor tap
                    // ada DI DALAM tiap halaman (pola penampil foto) —
                    // overlay di atas pager menelan DOWN sehingga pager
                    // tidak pernah menerima geser halaman (slide antar
                    // video mati; pindah video hanya bisa lewat tombol
                    // prev/next di kapsul kontrol). OnTouchListener
                    // VideoView tidak bisa dipakai: ia mengembalikan
                    // false pada ACTION_DOWN sehingga ACTION_UP tidak
                    // pernah sampai ke listener.
                    Box(
                        Modifier.fillMaxSize().pointerInput(pagePath) {
                            detectTapGestures { controlsVisible = !controlsVisible }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                views[page] = this
                                setVideoURI(android.net.Uri.fromFile(File(pagePath)))
                                setOnErrorListener { _, _, _ ->
                                    notify(t.opFailed)
                                    onBack()
                                    true
                                }
                                setOnPreparedListener { mp ->
                                    if (page == pagerState.currentPage) {
                                        durationMs = mp.duration
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                                setOnCompletionListener {
                                    if (page == pagerState.currentPage) {
                                        isPlaying = false
                                        positionMs = durationMs
                                    }
                                }
                            }
                        },
                        onRelease = { it.stopPlayback(); views.remove(page) },
                        modifier = Modifier.fillMaxSize()
                    )
                    }
                }
            }
            // Kontrol kapsul mengambang (VP2, r76; VP6 r85: slider +
            // waktu masuk ke dalam kapsul, baris atas; tombol di baris
            // bawah — satu kesatuan kontrol). Muncul/hilang dengan
            // animasi halus (r104): fade + geser dari bawah.
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(animationSpec = motion.defaultEffectsSpec<Float>()) +
                    slideInVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { it },
                exit = fadeOut(animationSpec = motion.defaultEffectsSpec<Float>()) +
                    slideOutVertically(animationSpec = motion.defaultSpatialSpec<IntOffset>()) { it }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = null,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth().then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    FileOps.formatDuration(if (isSeeking) seekMs else positionMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (isLiquid) {
                                    Slider(
                                        value = (if (isSeeking) seekMs else positionMs).toFloat()
                                            .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                                        onValueChange = { isSeeking = true; seekMs = it.toInt() },
                                        onValueChangeFinished = {
                                            currentView()?.seekTo(seekMs)
                                            positionMs = seekMs
                                            isSeeking = false
                                        },
                                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                            thumbColor = Color.White
                                        ),
                                        thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White)) }
                                    )
                                } else {
                                    Slider(
                                        value = (if (isSeeking) seekMs else positionMs).toFloat()
                                            .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                                        onValueChange = { isSeeking = true; seekMs = it.toInt() },
                                        onValueChangeFinished = {
                                            currentView()?.seekTo(seekMs)
                                            positionMs = seekMs
                                            isSeeking = false
                                        },
                                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                }
                                Text(
                                    FileOps.formatDuration(durationMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            IconButton(onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                            }) {
                                Icon(Icons.Default.SkipPrevious, null, tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    currentView()?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1))
                                }
                            }) {
                                Icon(Icons.Default.SkipNext, null, tint = Color.White)
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* Pratinjau font TrueType (.ttf): nama + gaya dibaca dari tabel nama
 *  font, contoh teks dirender dengan font itu lewat TextView native —
 *  Compose FontFamily dari berkas tidak punya API publik yang stabil. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FontViewerScreen(path: String, onBack: () -> Unit, isLiquid: Boolean = false) {
    val t = txt()
    // r224: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val file = File(path)
    val ink = MaterialTheme.colorScheme.onSurface
    var info by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    var typeface by remember { mutableStateOf<android.graphics.Typeface?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        val (fam, sty) = withContext(Dispatchers.IO) {
            FontOps.familyName(file) to FontOps.styleName(file)
        }
        info = fam to sty
        val loaded = withContext(Dispatchers.IO) {
            try {
                android.graphics.Typeface.createFromFile(file)
            } catch (_: Exception) {
                null
            }
        }
        typeface = loaded
        loadFailed = loaded == null
    }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home) } }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).liquidWashBackground(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(icon = Icons.Default.Info, title = t.properties, isLiquid = isLiquid) {
                FontInfoRow(t.fontFamily, info?.first ?: "—")
                FontInfoRow(t.fontStyle, info?.second ?: "—")
                FontInfoRow(t.propSize, FileOps.formatSize(file.length()))
                FontInfoRow(t.propPath, file.path)
            }
            Text(t.preview, style = MaterialTheme.typography.titleMedium)
            if (loadFailed) {
                Text(t.fontInvalid, color = MaterialTheme.colorScheme.error)
            } else {
                AndroidView(
                    factory = { ctx ->
                        android.widget.TextView(ctx).apply {
                            textSize = 26f
                            setTextColor(ink.toArgb())
                            text = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz\n0123456789\nThe quick brown fox jumps over the lazy dog"
                        }
                    },
                    update = {
                        it.typeface = typeface ?: android.graphics.Typeface.DEFAULT
                        it.setTextColor(ink.toArgb())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Baris label–nilai di kartu info pratinjau font (pola PropGridRow). */
@Composable
private fun FontInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Penyorotan sintaks multi-format (XML/HTML, .conf, .toml, .prop, .ini, .json, .sh) + highlight pencarian. */
private fun highlightSyntax(
    code: String,
    ext: String,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    outlineColor: Color,
    errorColor: Color,
    searchQuery: String = ""
): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        val lowerExt = ext.lowercase()
        try {
            when {
                lowerExt in setOf("xml", "html", "htm") -> {
                    // Tag
                    Regex("<[^>]+>").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = primaryColor), m.range.first, m.range.last + 1)
                        val tagText = code.substring(m.range.first, m.range.last + 1)
                        Regex("\"[^\"]*\"|'[^']*'").findAll(tagText).forEach { sm ->
                            addStyle(
                                SpanStyle(color = tertiaryColor),
                                m.range.first + sm.range.first,
                                m.range.first + sm.range.last + 1
                            )
                        }
                    }
                    // Komentar XML/HTML
                    Regex("<!--[\\s\\S]*?-->").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = outlineColor, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                    }
                }
                lowerExt in setOf("prop", "conf", "config", "ini", "cfg", "properties", "env", "toml") -> {
                    // Header bagian [section]
                    Regex("(?m)^\\s*\\[[^\\]]+\\]").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = tertiaryColor, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
                    }
                    // Kunci = nilai
                    Regex("(?m)^\\s*([a-zA-Z0-9_.-]+)\\s*=").findAll(code).forEach { m ->
                        val keyGroup = m.groups[1]
                        if (keyGroup != null) {
                            addStyle(SpanStyle(color = primaryColor), keyGroup.range.first, keyGroup.range.last + 1)
                        }
                    }
                    // Komentar (# atau ;)
                    Regex("(?m)^\\s*[#;].*$").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = outlineColor, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                    }
                    // Teks string berpetik
                    Regex("\"[^\"]*\"|'[^']*'").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = secondaryColor), m.range.first, m.range.last + 1)
                    }
                }
                lowerExt in setOf("json", "jsonl", "jsonc") -> {
                    // Kunci JSON
                    Regex("\"([^\"]+)\"\\s*:").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = primaryColor), m.range.first, m.range.last + 1)
                    }
                    // String JSON
                    Regex(":\\s*(\"[^\"]*\")").findAll(code).forEach { m ->
                        val g = m.groups[1]
                        if (g != null) {
                            addStyle(SpanStyle(color = tertiaryColor), g.range.first, g.range.last + 1)
                        }
                    }
                    // Literal boolean / null
                    Regex("\\b(true|false|null)\\b").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
                    }
                }
                lowerExt in setOf("sh", "bash", "zsh") -> {
                    Regex("(?m)^\\s*#.*$").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = outlineColor, fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                    }
                    Regex("\"[^\"]*\"|'[^']*'").findAll(code).forEach { m ->
                        addStyle(SpanStyle(color = tertiaryColor), m.range.first, m.range.last + 1)
                    }
                }
            }

            // Penyorotan kata kunci pencarian
            if (searchQuery.isNotBlank() && searchQuery.length >= 2) {
                var start = 0
                while (start < code.length) {
                    val idx = code.indexOf(searchQuery, start, ignoreCase = true)
                    if (idx == -1) break
                    addStyle(
                        SpanStyle(
                            background = primaryColor.copy(alpha = 0.35f),
                            fontWeight = FontWeight.Bold
                        ),
                        idx,
                        idx + searchQuery.length
                    )
                    start = idx + searchQuery.length
                }
            }
        } catch (_: Exception) {}
    }
}
