package id.sysfile.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Akses SAF (Storage Access Framework) untuk folder yang dibatasi sistem,
 * misalnya Android/data di Android 13+. Jalur berkas biasa (All Files Access)
 * tetap menjadi mesin utama; SAF hanya dipakai untuk pohon folder yang sudah
 * diberi izin pengguna lewat pemilih berkas sistem.
 */
object SafOps {

    /** URI awal pemilih berkas agar langsung membuka folder [path]. */
    fun initialUriFor(path: String): Uri? {
        val internal = Environment.getExternalStorageDirectory().absolutePath
        // L16: /sdcard dan /storage/self/primary adalah alias storage
        // internal — dulu jatuh ke null ("/sdcard") atau volume "self"
        // yang salah, sehingga pemilih SAF dibuka di lokasi default
        // sistem, bukan di folder yang diminta.
        val normalized = when {
            path == "/sdcard" || path == "/storage/self/primary" -> internal
            path.startsWith("/sdcard/") -> internal + path.removePrefix("/sdcard")
            path.startsWith("/storage/self/primary/") -> internal + path.removePrefix("/storage/self/primary")
            else -> path
        }
        val vol: String
        val rel: String
        when {
            normalized == internal || normalized.startsWith("$internal/") -> {
                vol = "primary"
                rel = normalized.removePrefix(internal).trim('/')
            }
            normalized.startsWith("/storage/") -> {
                val rest = normalized.removePrefix("/storage/")
                vol = rest.substringBefore('/')
                rel = rest.substringAfter('/', "")
            }
            else -> return null
        }
        // AOSP DocumentsUI membuang URI awal di dalam /Android/data|obb|sandbox
        // (beserta subfoldernya) — pemilih malah terbuka di lokasi terakhir.
        // Minta akar volume saja (SafPath); dari sana pengguna mengetuk
        // Android -> obb/data -> folder paket, lalu "Gunakan folder ini".
        val docId = SafPath.initialDocId(vol, rel)
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents", docId
        )
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    /** Jalur berkas dari URI pohon SAF (kebalikan [initialUriFor]). */
    fun pathOfTree(uri: Uri): String {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            return ""
        }
        // L16: docId tanpa ':' bukan path storage (mis. "home") — dulu
        // dipetakan ke "/storage/<seluruh-docId>" yang palsu.
        if (!docId.contains(':')) return ""
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$vol"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /** Jalur berkas dari URI sebuah dokumen di dalam pohon SAF (bukan
     *  hanya akar pohon). getDocumentId melempar untuk URI pohon murni —
     *  pemanggil memakai [pathOfTree] untuk akar. Gagal -> "". */
    fun pathOfDoc(uri: Uri): String {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            return ""
        }
        // L16: docId tanpa ':' bukan path storage (lihat pathOfTree).
        if (!docId.contains(':')) return ""
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$vol"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    /** Jumlah anak langsung di bawah pohon SAF (untuk angka "N item" di layar induk). */
    fun countChildren(context: Context, treeUri: String, relPath: String): Int? {
        return try {
            var cur = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
            for (seg in relPath.split('/')) {
                if (seg.isEmpty()) continue
                cur = cur.listFiles().firstOrNull { it.isDirectory && it.name == seg } ?: return null
            }
            cur.listFiles().size
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SafBrowseScreen(
    treeUri: String,
    relPath: String,
    onExit: () -> Unit,
    notify: (String) -> Unit,
    basePath: String = "",
    lockedPaths: Set<String> = emptySet(),
    unlockedPaths: Set<String> = emptySet(),
    onRequestUnlock: (String, () -> Unit) -> Unit = { _, grant -> grant() },
    isLiquid: Boolean = false
) {
    val t = txt()
    // r215: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(treeUri) { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }
    var stack by remember(treeUri) { mutableStateOf(listOfNotNull(root)) }
    var ready by remember(treeUri) { mutableStateOf(relPath.isEmpty()) }

    if (root == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    // Turun lebih dulu ke subfolder yang diminta (relPath dari folder terlarang).
    LaunchedEffect(treeUri) {
        if (relPath.isNotEmpty()) {
            val r: DocumentFile = root
            val built = withContext(Dispatchers.IO) {
                val acc = mutableListOf(r)
                var cur = r
                for (seg in relPath.split('/')) {
                    if (seg.isEmpty()) continue
                    val next = cur.listFiles()
                        .firstOrNull { it.isDirectory && it.name == seg } ?: break
                    acc += next
                    cur = next
                }
                acc.toList()
            }
            stack = built
            ready = true
        }
    }

    val current = stack.lastOrNull() ?: root
    var children by remember { mutableStateOf<List<DocumentFile>?>(null) }
    LaunchedEffect(current.uri, ready) {
        if (ready) {
            children = null
            children = withContext(Dispatchers.IO) {
                current.listFiles().sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }
                        .thenBy { it.name?.lowercase() ?: "" }
                )
            }
        }
    }

    var copyDoc by remember { mutableStateOf<DocumentFile?>(null) }

    // r175 H3: petakan DocumentFile ke jalur berkas untuk gerbang kunci.
    // Akar pohon = basePath (diketahui pemanggil); anak diturunkan dari
    // document ID-nya (skema yang sama dengan SafOps.pathOfTree).
    fun fsPathOf(doc: DocumentFile): String {
        if (doc.uri == root.uri) return basePath
        return SafOps.pathOfDoc(doc.uri)
    }

    // Benar bila [fsPath] butuh biometrik: berada di dalam folder terkunci
    // dan belum dibuka pada sesi ini. Jalur yang tidak bisa dipetakan
    // ("" — bukan dari provider storage eksternal) dianggap terkunci
    // bila ada folder terkunci (fail closed).
    fun needsUnlock(fsPath: String): Boolean {
        if (lockedPaths.isEmpty()) return false
        if (fsPath.isEmpty()) return true
        return FileOps.isLockedPath(fsPath, lockedPaths) &&
            !FileOps.isUnlockedPath(fsPath, unlockedPaths)
    }

    fun guarded(fsPath: String, action: () -> Unit) {
        if (needsUnlock(fsPath)) {
            onRequestUnlock(if (fsPath.isEmpty()) basePath else fsPath, action)
        } else {
            action()
        }
    }

    fun goBack() {
        if (stack.size > 1) stack = stack.dropLast(1) else onExit()
    }
    BackHandler { goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquid) liquid.glassTint else Color.Unspecified),
                    title = {
                        Text(
                            current.name ?: "SAF",
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { goBack() }) {
                            Icon(if (isLiquid) Icons.AutoMirrored.Filled.ArrowBackIos else Icons.Default.ArrowBack, t.home)
                        }
                    }
                )
                if (isLiquid) HorizontalDivider(thickness = 0.5.dp, color = liquid.separator)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).liquidGlassBackground()) {
            when {
                children == null -> if (isLiquid) LiquidSpinner(
                    Modifier.align(Alignment.Center).size(52.dp)
                ) else LoadingIndicator(
                    Modifier.align(Alignment.Center).size(52.dp)
                )
                children!!.isEmpty() -> Text(
                    t.emptyFolder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (isLiquid) {
                        item {
                            Text(
                                current.name ?: "SAF",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(children!!, key = { it.uri.toString() }) { doc ->
                        SafRow(
                            doc = doc,
                            onClick = {
                                if (doc.isDirectory) {
                                    guarded(fsPathOf(doc)) { stack = stack + doc }
                                } else {
                                    guarded(fsPathOf(current)) { openSafDoc(context, doc, t.openFailed, notify) }
                                }
                            },
                            onCopy = { copyDoc = doc },
                            isLiquid = isLiquid
                        )
                    }
                }
            }
        }
    }

    copyDoc?.let { doc ->
        FolderPickerDialog(
            startPath = Environment.getExternalStorageDirectory().absolutePath,
            title = t.safCopyOut,
            onPick = { dir ->
                copyDoc = null
                guarded(fsPathOf(current)) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { copySafOut(context, doc, dir) }
                        notify(if (ok) t.safCopied(doc.name ?: "") else t.safCopyFailed)
                    }
                }
            },
            onCancel = { copyDoc = null }
        )
    }
}

@Composable
private fun SafRow(doc: DocumentFile, onClick: () -> Unit, onCopy: () -> Unit, isLiquid: Boolean = false) {
    val t = txt()
    // r215: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val name = doc.name ?: "?"
    val isDir = doc.isDirectory
    val color = if (isDir) Color(0xFFFFA000) else categoryColor(FileOps.categoryOf(name))
    ListItem(
        headlineContent = {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                if (isDir) t.foldersLabel else FileOps.formatSize(doc.length()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDir) Icons.Default.Folder
                    else categoryIcon(FileOps.categoryOf(name), name),
                    null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailingContent = {
            if (!isDir) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.Download, t.safCopyOut,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(if (isLiquid) Modifier.clip(RoundedCornerShape(28.dp)).glassBlur(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
            .clickable { onClick() }
    )
}

private fun openSafDoc(
    context: Context,
    doc: DocumentFile,
    failMsg: String,
    notify: (String) -> Unit
) {
    val name = doc.name ?: ""
    val mime = doc.type
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(doc.uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        notify(failMsg)
    }
}

private fun copySafOut(context: Context, doc: DocumentFile, dirPath: String): Boolean {
    val dir = File(dirPath)
    if (!dir.isDirectory) return false
    val name = FileOps.uniqueName(dir, doc.name ?: "file")
    val outFile = File(dir, name)
    // L6: total byte dicap (2 GiB) dan output sebagian dihapus bila
    // gagal — dulu copyTo tanpa batas + tanpa cleanup (berkas SAF
    // setengah jadi tertinggal di folder lokal).
    return try {
        val input = context.contentResolver.openInputStream(doc.uri) ?: return false
        input.use { inp ->
            CountingOutputStream(FileOutputStream(outFile), FileOps.MAX_EXTRACT_BYTES).use { out -> inp.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        outFile.delete()
        false
    }
}
