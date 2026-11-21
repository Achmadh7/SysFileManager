package id.sysfile.manager

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/** Pembaca EPUB v1 (BC2): ekstrak ke cache, baca bab per bab di WebView
 *  (base file:// folder bab supaya gambar/CSS relatif termuat), navigasi
 *  bab sebelum/berikut + daftar isi dari spine (judul dari toc.ncx bila
 *  ada, kalau tidak nama berkas). Tanpa pencarian, bookmark, atau
 *  pengaturan font. WebView dikeraskan seperti pratinjau HTML (r176):
 *  JavaScript + DOM storage mati; akses file + jaringan diblokir,
 *  subresource lokal dilayani lewat WebViewPolicy. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EpubReaderScreen(path: String, onBack: () -> Unit) {
    val t = txt()
    // r222: gaya Liquid dibaca dari local (layar ini tak terima param gaya).
    val isLiquid = LocalIsLiquid.current
    val context = LocalContext.current
    var book by remember { mutableStateOf<EpubBook?>(null) }
    var failed by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var showToc by remember { mutableStateOf(false) }
    var chapterHtml by remember { mutableStateOf<String?>(null) }
    var chapterFailed by remember { mutableStateOf(false) }
    // M15: nama dir staging unik per buka (nanoTime) supaya dua
    // buku yang sama namanya tidak saling menimpa staging.
    val bookDir = remember(path) {
        File(
            File(context.cacheDir, "epub"),
            File(path).nameWithoutExtension + "-" + System.nanoTime()
        )
    }

    LaunchedEffect(path) {
        book = null
        failed = false
        index = 0
        val opened = withContext(Dispatchers.IO) {
            // M15: evict cache EPUB basi sebelum staging buku baru.
            FileOps.evictStaleCache(bookDir.parentFile)
            try {
                EpubOps.open(File(path), bookDir)
            } catch (e: Exception) {
                null
            }
        }
        if (opened == null) failed = true else book = opened
    }

    val chapter = book?.chapters?.getOrNull(index)
    LaunchedEffect(book, index) {
        chapterHtml = null
        chapterFailed = false
        val b = book ?: return@LaunchedEffect
        val ch = b.chapters.getOrNull(index) ?: return@LaunchedEffect
        val text = withContext(Dispatchers.IO) {
            try {
                EpubOps.chapterFile(bookDir, b, ch)
                    ?.takeIf { it.isFile }
                    // M8: bab dicap 1 MB (sama seperti teks arsip) —
                    // readText() tanpa cap memuat bab ratusan MB utuh.
                    ?.let { ArchiveOps.readTextCapped(it, 1_000_000) }
            } catch (e: Exception) {
                null
            }
        }
        if (text == null) chapterFailed = true else chapterHtml = text
    }
    val chapterDir = remember(book, index) {
        val b = book
        val ch = b?.chapters?.getOrNull(index)
        if (b == null || ch == null) null
        else EpubOps.chapterFile(bookDir, b, ch)?.parent
    }

    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(File(path).name, maxLines = 1)
                        val b = book
                        if (b != null && chapter != null) {
                            Text(
                                "${index + 1} / ${b.chapters.size} — ${chapter.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { showToc = true }) {
                            Icon(Icons.Default.List, t.chapters)
                        }
                    }
                }
            )
        },
        bottomBar = {
            val b = book
            if (b != null) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (index > 0) index-- },
                        enabled = index > 0
                    ) { Text(t.prevChapter) }
                    Text(
                        "${index + 1} / ${b.chapters.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { if (index < b.chapters.size - 1) index++ },
                        enabled = index < b.chapters.size - 1
                    ) { Text(t.nextChapter) }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                failed -> Text(t.epubFailed, Modifier.align(Alignment.Center))
                book == null -> if (isLiquid) LiquidSpinner(Modifier.align(Alignment.Center).size(52.dp)) else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                chapterFailed -> Text(t.readFailed, Modifier.align(Alignment.Center))
                chapterHtml == null -> if (isLiquid) LiquidSpinner(Modifier.align(Alignment.Center).size(52.dp)) else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                else -> key(index) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                // Sama seperti pratinjau HTML (r176: H5/M11/M12):
                                // JS mati, DOM storage mati, akses file WebView
                                // dimatikan dan muatan jaringan diblokir.
                                // Subresource file:// yang sah (gambar/CSS di
                                // folder bab) dilayani klien di bawah lewat
                                // WebViewPolicy — file di luar folder bab dan
                                // semua http(s) diblokir. Tautan hanya boleh
                                // http/https (dibuka eksternal lewat chooser).
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.domStorageEnabled = false
                                settings.blockNetworkLoads = true
                                val allowedDir = chapterDir
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
                                                // Tidak ada browser: abaikan saja.
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
                            val baseUrl = "file://" + (chapterDir ?: "") + "/"
                            wv.loadDataWithBaseURL(baseUrl, chapterHtml ?: "", "text/html", "UTF-8", null)
                        },
                        // L8 (r176): WebView dihancurkan saat ganti bab /
                        // keluar layar, kalau tidak resource native-nya bocor.
                        onRelease = { it.stopLoading(); it.destroy() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    val b = book
    if (showToc && b != null) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text(t.chapters) },
            text = {
                LazyColumn {
                    itemsIndexed(b.chapters) { i, ch ->
                        TextButton(onClick = { index = i; showToc = false }) {
                            Text(
                                ch.title,
                                maxLines = 1,
                                color = if (i == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToc = false }) { Text(t.cancel) }
            }
        )
    }
}
