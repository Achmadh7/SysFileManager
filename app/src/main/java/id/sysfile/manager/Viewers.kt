package id.sysfile.manager

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
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
                // Saat diperbesar, geser halaman dimatikan supaya cubit/pan
                // milik gambar; saat skala 1, geser milik pager.
                userScrollEnabled = scale == 1f,
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
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
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

/** Pemutar video bawaan: geser antar video sefolder, VideoView dengan kontrol media, langsung putar di dalam aplikasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(paths: List<String>, startIndex: Int, onBack: () -> Unit, onPathChange: (String) -> Unit, notify: (String) -> Unit) {
    val t = txt()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    val currentPath = paths[pagerState.currentPage]
    LaunchedEffect(pagerState.currentPage) {
        onPathChange(paths[pagerState.currentPage])
    }
    // Back ditangani di layar ini juga: jendela melayang MediaController bisa
    // menelan tombol Back sistem selama video diputar.
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
                        val controller = android.widget.MediaController(ctx)
                        // Jangkar controller HARUS VideoView-nya. Versi sebelumnya
                        // memakai apply bersarang sehingga this@apply terikat ke
                        // MediaController (controller menjangkar ke dirinya sendiri)
                        // dan jendela kontrolnya bisa menelan tombol Back & sentuhan
                        // selama video diputar - kembali baru bisa setelah video selesai.
                        controller.setAnchorView(videoView)
                        videoView.setMediaController(controller)
                        videoView.setVideoURI(android.net.Uri.fromFile(File(pagePath)))
                        videoView.setOnKeyListener { _, keyCode, event ->
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                                event.action == android.view.KeyEvent.ACTION_UP
                            ) {
                                onBack()
                                true
                            } else {
                                false
                            }
                        }
                        videoView.setOnErrorListener { _, _, _ ->
                            notify(t.opFailed)
                            onBack()
                            true
                        }
                        videoView.setOnPreparedListener { it.start() }
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
