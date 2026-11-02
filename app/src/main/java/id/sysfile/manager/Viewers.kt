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
import androidx.compose.runtime.key
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Penampil foto layar penuh: cubit-zoom, geser, ketuk untuk mode imersif, toolbar aksi melayang. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageViewerScreen(
    path: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val t = txt()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var chrome by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    title = { Text(File(path).name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(Color.Black)
                .pointerInput(path) {
                    detectTapGestures { chrome = !chrome }
                }
                .pointerInput(path) {
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
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
            )
            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                HorizontalFloatingToolbar(expanded = true) {
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, t.share) }
                    IconButton(onClick = onInfo) { Icon(Icons.Default.Info, t.properties) }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, t.delete) }
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
