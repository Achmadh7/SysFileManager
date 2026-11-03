package id.sysfile.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        val vol: String
        val rel: String
        when {
            path == internal || path.startsWith("$internal/") -> {
                vol = "primary"
                rel = path.removePrefix(internal).trim('/')
            }
            path.startsWith("/storage/") -> {
                val rest = path.removePrefix("/storage/")
                vol = rest.substringBefore('/')
                rel = rest.substringAfter('/', "")
            }
            else -> return null
        }
        val docId = DocumentsContract.buildTreeDocumentId(vol, rel)
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
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$vol"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SafBrowseScreen(
    treeUri: String,
    relPath: String,
    onExit: () -> Unit,
    notify: (String) -> Unit
) {
    val t = txt()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(treeUri) { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }
    var stack by remember(treeUri) { mutableStateOf(listOf(root)) }
    var ready by remember(treeUri) { mutableStateOf(relPath.isEmpty()) }

    // Turun lebih dulu ke subfolder yang diminta (relPath dari folder terlarang).
    LaunchedEffect(treeUri) {
        if (relPath.isNotEmpty() && root != null) {
            val built = withContext(Dispatchers.IO) {
                val acc = mutableListOf(root)
                var cur = root
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

    val current = stack.last()
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

    fun goBack() {
        if (stack.size > 1) stack = stack.dropLast(1) else onExit()
    }
    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current.name ?: "SAF",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.Default.ArrowBack, t.home)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                children == null -> LoadingIndicator(
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
                    items(children!!, key = { it.uri.toString() }) { doc ->
                        SafRow(
                            doc = doc,
                            onClick = {
                                if (doc.isDirectory) {
                                    stack = stack + doc
                                } else {
                                    openSafDoc(context, doc, t.openFailed, notify)
                                }
                            },
                            onCopy = { copyDoc = doc }
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
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { copySafOut(context, doc, dir) }
                    notify(if (ok) t.safCopied(doc.name ?: "") else t.safCopyFailed)
                }
            },
            onCancel = { copyDoc = null }
        )
    }
}

@Composable
private fun SafRow(doc: DocumentFile, onClick: () -> Unit, onCopy: () -> Unit) {
    val t = txt()
    val name = doc.name ?: "?"
    val isDir = doc.isDirectory
    val color = if (isDir) Color(0xFFFFA000) else categoryColor(FileOps.categoryOf(name))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isDir) t.foldersLabel else FileOps.formatSize(doc.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isDir) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.Download, t.safCopyOut,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
    return try {
        val input = context.contentResolver.openInputStream(doc.uri) ?: return false
        input.use { inp ->
            FileOutputStream(File(dir, name)).use { out -> inp.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        false
    }
}
