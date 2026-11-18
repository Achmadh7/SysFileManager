package id.sysfile.manager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Penampil foto layar penuh: geser antar foto sefolder, cubit-zoom, ketuk untuk mode imersif, toolbar aksi melayang. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageViewerScreen(
    paths: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onInfo: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPathChange: (String) -> Unit
) {
    val t = txt()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    val currentPath = paths[pagerState.currentPage]
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    var zooming by remember { mutableStateOf(false) }
    // Ganti halaman: zoom direset + laporkan berkas yang sedang tampil
    // (untuk Bagikan/Properti/Hapus + penanda hapus di MainActivity).
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onPathChange(paths[pagerState.currentPage])
    }
    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
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
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
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
                        model = File(pagePath),
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
                    IconButton(onClick = { onDelete(currentPath) }) { Icon(Icons.Default.Delete, t.delete) }
                }
            }
        }
    }
}

/** Penampil/pengubah berkas teks sederhana (maks 1 MB yang dimuat). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextViewerScreen(path: String, onBack: () -> Unit, notify: (String) -> Unit) {
    val t = txt()
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val fileExt = remember(path) { FileOps.extOf(File(path).name) }
    val isHtml = fileExt == "html" || fileExt == "htm"
    val isMarkup = isHtml || fileExt == "xml"
    var preview by remember { mutableStateOf(false) }
    var previewTick by remember { mutableStateOf(0) }

    LaunchedEffect(path) {
        val text = withContext(Dispatchers.IO) {
            try {
                val f = File(path)
                val bytes = f.inputStream().use { it.readBytes() }
                val capped = if (bytes.size > 1_000_000) bytes.copyOf(1_000_000) else bytes
                String(capped, Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
        if (text == null) failed = true else content = text
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                },
                actions = {
                    if (content != null) {
                        if (isHtml) {
                            if (preview) {
                                IconButton(onClick = { previewTick++ }) { Icon(Icons.Default.Refresh, t.refresh) }
                                IconButton(onClick = { preview = false }) { Icon(Icons.Default.Code, t.codeLabel) }
                            } else {
                                IconButton(onClick = { preview = true }) { Icon(Icons.Default.Visibility, t.preview) }
                            }
                        }
                        if (!preview && editing) {
                            IconButton(onClick = {
                                val toSave = content ?: ""
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        try {
                                            File(path).writeText(toSave, Charsets.UTF_8)
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                    if (ok) {
                                        editing = false
                                        notify(t.textSaved)
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            }) { Icon(Icons.Default.Save, t.save) }
                        } else if (!preview) {
                            IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, t.edit) }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                failed -> Text(t.readFailed, Modifier.align(Alignment.Center))
                content == null -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                preview && isHtml -> key(previewTick) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = true
                                settings.domStorageEnabled = true
                            }
                        },
                        update = { wv ->
                            val baseUrl = "file://" + (File(path).parent ?: "") + "/"
                            wv.loadDataWithBaseURL(baseUrl, content ?: "", "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                editing -> OutlinedTextField(
                    value = content ?: "",
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (isMarkup) {
                        Text(
                            highlightMarkup(
                                content ?: "",
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        )
                    } else {
                        Text(content ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}

/** MediaController yang tidak menelan tombol Back: jendela melayang
 *  kontrol bawaan Android menelan Back sistem selama video diputar (hanya
 *  menyembunyikan kontrol), sehingga penonton harus menunggu video selesai.
 *  Subclass ini mengalihkan Back di dispatchKeyEvent langsung ke onBackPress
 *  — satu ketukan keluar dari penampil. Bila kontrol sedang tersembunyi,
 *  Back sampai ke Activity seperti biasa (BackHandler di atas). */
class ViewerMediaController(context: android.content.Context) :
    android.widget.MediaController(context) {
    var onBackPress: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            onBackPress?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Penampil PDF bawaan: geser antar halaman, cubit-zoom, ketuk untuk
 *  menyembunyikan bilah atas. Render memakai PdfRenderer bawaan Android
 *  (tanpa dependensi baru); tiap halaman dirender saat tampil saja (IO
 *  thread, skala maks 2x, sisi terpanjang maks 2048px). PDF terenkripsi /
 *  rusak tidak bisa dibuka PdfRenderer — tampil pesan gagal + tombol buka
 *  dengan aplikasi lain. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerScreen(path: String, onBack: () -> Unit, onOpenExternal: () -> Unit) {
    val t = txt()
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
                TopAppBar(
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
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                    }
                )
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
                    LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
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
                                else -> LoadingIndicator(Modifier.size(52.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pemutar video bawaan: geser antar video sefolder, VideoView dengan kontrol media, langsung putar di dalam aplikasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(paths: List<String>, startIndex: Int, onBack: () -> Unit, onPathChange: (String) -> Unit, notify: (String) -> Unit) {
    val t = txt()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    val currentPath = paths[pagerState.currentPage]
    LaunchedEffect(pagerState.currentPage) {
        onPathChange(paths[pagerState.currentPage])
    }
    // Back saat kontrol tersembunyi sampai ke sini (BackHandler). Saat kontrol
    // tampil, Back ditangkap ViewerMediaController (lihat di bawah) karena
    // kunci tidak pernah sampai ke Activity selama jendela kontrol ada.
    BackHandler { onBack() }
    Scaffold(
        topBar = {
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
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)
        ) { page ->
            val pagePath = paths[page]
            key(pagePath) {
                AndroidView(
                    factory = { ctx ->
                        val videoView = android.widget.VideoView(ctx)
                        // ViewerMediaController: Back di jendela kontrol langsung
                        // keluar dari penampil (bukan sekadar menyembunyikan
                        // kontrol — itu yang membuat Back terasa mati selama
                        // video diputar). OnKeyListener lama dihapus: kunci
                        // tidak pernah sampai ke VideoView selama kontrol tampil.
                        val controller = ViewerMediaController(ctx).apply {
                            onBackPress = onBack
                        }
                        // Jangkar controller HARUS VideoView-nya. Versi lama
                        // memakai apply bersarang sehingga this@apply terikat ke
                        // MediaController (controller menjangkar ke dirinya sendiri)
                        // dan jendela kontrolnya menelan sentuhan selama video diputar.
                        controller.setAnchorView(videoView)
                        videoView.setMediaController(controller)
                        videoView.setVideoURI(android.net.Uri.fromFile(File(pagePath)))
                        videoView.setOnErrorListener { _, _, _ ->
                            notify(t.opFailed)
                            onBack()
                            true
                        }
                        videoView.setOnPreparedListener { it.start() }
                        // VideoView menelan semua sentuhan (untuk memunculkan
                        // MediaController), jadi pager di atasnya tidak pernah
                        // menerima geser. Tangkap seret horizontal di sini dan
                        // pindahkan halaman secara manual; ketukan tetap
                        // diteruskan ke VideoView (controller tetap muncul).
                        var downX = 0f
                        var downY = 0f
                        var swiping = false
                        videoView.setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    swiping = false
                                    false
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (!swiping && abs(dx) > 24f && abs(dx) > abs(dy) * 1.5f) {
                                        swiping = true
                                    }
                                    swiping
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    if (swiping) {
                                        val dx = event.x - downX
                                        if (abs(dx) > 120f) {
                                            val target = (pagerState.currentPage +
                                                if (dx < 0) 1 else -1)
                                                .coerceIn(0, pagerState.pageCount - 1)
                                            if (target != pagerState.currentPage) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(target)
                                                }
                                            }
                                        }
                                        swiping = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                        videoView
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Penyorotan sintaks ringan untuk markup (HTML/XML): tag, nilai atribut, komentar. */
private fun highlightMarkup(
    code: String,
    tagColor: Color,
    stringColor: Color,
    commentColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        Regex("<[^>]+>").findAll(code).forEach { m ->
            addStyle(SpanStyle(color = tagColor), m.range.first, m.range.last + 1)
            val tagText = code.substring(m.range.first, m.range.last + 1)
            Regex("\"[^\"]*\"|'[^']*'").findAll(tagText).forEach { sm ->
                addStyle(
                    SpanStyle(color = stringColor),
                    m.range.first + sm.range.first,
                    m.range.first + sm.range.last + 1
                )
            }
        }
        // Komentar terakhir agar warnanya menimpa tag di dalamnya.
        Regex("<!--[\\s\\S]*?-->").findAll(code).forEach { m ->
            addStyle(SpanStyle(color = commentColor), m.range.first, m.range.last + 1)
        }
    }
}
