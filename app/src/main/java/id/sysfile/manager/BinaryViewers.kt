package id.sysfile.manager

import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ---------------------------------------------------------------------------
// Opsi 1: SQLite Database Viewer (.db, .sqlite, .sqlite3) — read-only
// ---------------------------------------------------------------------------

private data class SqliteTableInfo(val name: String, val rowCount: Long)
private data class SqliteTableData(val columns: List<String>, val rows: List<List<String>>)

private object SqliteOps {
    fun listTables(file: File): List<SqliteTableInfo>? {
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val names = mutableListOf<String>()
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                    null
                ).use { c ->
                    while (c.moveToNext()) names.add(c.getString(0))
                }
                names.map { name ->
                    val safe = name.replace("\"", "\"\"")
                    val count = try {
                        db.rawQuery("SELECT COUNT(*) FROM \"$safe\"", null).use { c ->
                            if (c.moveToFirst()) c.getLong(0) else 0L
                        }
                    } catch (_: Exception) { 0L }
                    SqliteTableInfo(name, count)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readRows(file: File, tableName: String, limit: Int = 200): SqliteTableData? {
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val safe = tableName.replace("\"", "\"\"")
                db.rawQuery("SELECT * FROM \"$safe\" LIMIT $limit", null).use { c ->
                    val cols = c.columnNames.toList()
                    val rows = ArrayList<List<String>>(c.count.coerceAtMost(limit))
                    while (c.moveToNext()) {
                        val row = ArrayList<String>(cols.size)
                        for (i in cols.indices) {
                            val v = when (c.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                                android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                                android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
                                android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB ${c.getBlob(i).size}B>"
                                else -> c.getString(i) ?: ""
                            }
                            row.add(v)
                        }
                        rows.add(row)
                    }
                    SqliteTableData(cols, rows)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SqliteViewerScreen(path: String, onBack: () -> Unit) {
    val t = txt()
    val file = remember(path) { File(path) }
    var tables by remember(path) { mutableStateOf<List<SqliteTableInfo>?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var selectedTable by remember(path) { mutableStateOf<String?>(null) }
    var tableData by remember(path, selectedTable) { mutableStateOf<SqliteTableData?>(null) }
    var loadingRows by remember(path, selectedTable) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val res = withContext(Dispatchers.IO) { SqliteOps.listTables(file) }
        if (res == null) failed = true else tables = res
    }
    LaunchedEffect(selectedTable) {
        val tbl = selectedTable ?: return@LaunchedEffect
        loadingRows = true
        tableData = withContext(Dispatchers.IO) { SqliteOps.readRows(file, tbl) }
        loadingRows = false
    }

    val backAction: () -> Unit = {
        if (selectedTable != null) {
            selectedTable = null
            tableData = null
        } else {
            onBack()
        }
    }
    BackHandler(enabled = selectedTable != null) { backAction() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(selectedTable ?: file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (selectedTable != null) file.name else "SQLite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = backAction) { Icon(Icons.Default.ArrowBack, t.back) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                failed -> Text(t.opFailed, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                tables == null || loadingRows -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                selectedTable != null -> {
                    val data = tableData
                    if (data == null || data.rows.isEmpty()) {
                        Text(t.emptyFolder, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    } else {
                        val hScroll = rememberScrollState()
                        Column(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                            Row(
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                data.columns.forEach { col ->
                                    Text(
                                        col,
                                        modifier = Modifier.width(140.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(data.rows) { row ->
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                cell,
                                                modifier = Modifier.width(140.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
                else -> {
                    val list = tables ?: emptyList()
                    if (list.isEmpty()) {
                        Text(t.emptyFolder, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list, key = { it.name }) { tbl ->
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.fillMaxWidth().nothingBorder(RoundedCornerShape(18.dp))
                                        .clickable { selectedTable = tbl.name }
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.TableChart,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(tbl.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${tbl.rowCount} rows",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

// ---------------------------------------------------------------------------
// Opsi 2: Binary Android XML (AXML) Decoder
// ---------------------------------------------------------------------------

object AxmlOps {
    fun isBinaryXml(file: File): Boolean {
        if (!file.isFile || file.length() < 8) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val b = ByteArray(4)
                if (raf.read(b) != 4) return false
                b[0] == 0x03.toByte() && b[1] == 0x00.toByte() && b[2] == 0x08.toByte() && b[3] == 0x00.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun decode(file: File): String? {
        if (file.length() > 8_000_000L) return null
        return try {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buf.remaining() < 8) return null
            val magic = buf.int
            if (magic != 0x00080003) return null
            buf.int // total size

            var strings: List<String> = emptyList()
            val nsMap = mutableMapOf<String, String>()
            val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            var indent = 0

            while (buf.remaining() >= 8) {
                val chunkStart = buf.position()
                val type = buf.short.toInt() and 0xFFFF
                val headerSize = buf.short.toInt() and 0xFFFF
                val chunkSize = buf.int
                if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) break

                when (type) {
                    0x0001 -> if (chunkStart + headerSize <= chunkStart + chunkSize) {
                        strings = parseStringPool(bytes, chunkStart, headerSize, chunkStart + chunkSize) ?: return null
                    }
                    0x0100 -> {
                        // StartNamespace
                        if (headerSize < 8 || chunkStart + headerSize > chunkStart + chunkSize) break
                        buf.position(chunkStart + headerSize)
                        if (buf.remaining() >= 8) {
                            val prefixIdx = buf.int
                            val uriIdx = buf.int
                            val prefix = strings.getOrNull(prefixIdx) ?: ""
                            val uri = strings.getOrNull(uriIdx) ?: ""
                            if (uri.isNotEmpty() && prefix.isNotEmpty()) nsMap[uri] = prefix
                        }
                    }
                    0x0102 -> {
                        // StartTag
                        if (headerSize < 8 || chunkStart + headerSize > chunkStart + chunkSize) break
                        buf.position(chunkStart + headerSize)
                        if (buf.remaining() >= 20) {
                            val nsIdx = buf.int
                            val nameIdx = buf.int
                            buf.short // attrStart
                            val attrSize = buf.short.toInt() and 0xFFFF
                            val attrCount = buf.short.toInt() and 0xFFFF
                            buf.short; buf.short; buf.short // id/class/style

                            val tag = qualifiedName(strings, nsMap, nsIdx, nameIdx)
                            out.append("  ".repeat(indent)).append('<').append(tag)
                            if (indent == 0) {
                                nsMap.forEach { (uri, prefix) ->
                                    out.append(" xmlns:").append(prefix).append("=\"").append(uri).append('"')
                                }
                            }
                            for (i in 0 until attrCount) {
                                val aPos = chunkStart + headerSize + 20 + i * attrSize
                                if (aPos + 20 > chunkStart + chunkSize) break
                                buf.position(aPos)
                                val aNs = buf.int
                                val aName = buf.int
                                val aRaw = buf.int
                                buf.short // size
                                buf.get() // res0
                                val vType = buf.get().toInt() and 0xFF
                                val vData = buf.int
                                val attrName = qualifiedName(strings, nsMap, aNs, aName)
                                val attrVal = formatAttrValue(strings, aRaw, vType, vData)
                                out.append("\n").append("  ".repeat(indent + 1))
                                    .append(attrName).append("=\"").append(escapeXml(attrVal)).append('"')
                            }
                            out.append(">\n")
                            indent++
                        }
                    }
                    0x0103 -> {
                        // EndTag
                        if (headerSize < 8 || chunkStart + headerSize > chunkStart + chunkSize) break
                        buf.position(chunkStart + headerSize)
                        if (buf.remaining() >= 8) {
                            val nsIdx = buf.int
                            val nameIdx = buf.int
                            indent = (indent - 1).coerceAtLeast(0)
                            val tag = qualifiedName(strings, nsMap, nsIdx, nameIdx)
                            out.append("  ".repeat(indent)).append("</").append(tag).append(">\n")
                        }
                    }
                    0x0104 -> {
                        // Text
                        if (headerSize < 8 || chunkStart + headerSize > chunkStart + chunkSize) break
                        buf.position(chunkStart + headerSize)
                        if (buf.remaining() >= 4) {
                            val textIdx = buf.int
                            val text = strings.getOrNull(textIdx)?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                out.append("  ".repeat(indent)).append(escapeXml(text)).append('\n')
                            }
                        }
                    }
                }
                buf.position(chunkStart + chunkSize)
            }
            out.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun qualifiedName(strings: List<String>, nsMap: Map<String, String>, nsIdx: Int, nameIdx: Int): String {
        val name = strings.getOrNull(nameIdx) ?: "?"
        val uri = if (nsIdx >= 0) strings.getOrNull(nsIdx) else null
        val prefix = uri?.let { nsMap[it] }
        return if (!prefix.isNullOrEmpty()) "$prefix:$name" else name
    }

    private fun formatAttrValue(strings: List<String>, rawIdx: Int, vType: Int, vData: Int): String {
        if (rawIdx >= 0) return strings.getOrNull(rawIdx) ?: ""
        return when (vType) {
            0x03 -> strings.getOrNull(vData) ?: ""
            0x01 -> "@0x${vData.toUInt().toString(16)}"
            0x10 -> vData.toString()
            0x11 -> "0x${vData.toUInt().toString(16)}"
            0x12 -> (vData != 0).toString()
            else -> "0x${vData.toUInt().toString(16)}"
        }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    internal fun parseStringPool(bytes: ByteArray, start: Int, headerSize: Int, chunkEnd: Int = bytes.size): List<String>? {
        val endBound = chunkEnd.coerceAtMost(bytes.size)
        if (start < 0 || headerSize < 28 || start + headerSize > endBound) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(start + 8)
        val count = buf.int
        if (count < 0 || count > 1_000_000) return null
        buf.int // styleCount
        val flags = buf.int
        val stringsOffset = buf.int
        buf.int // stylesOffset
        val isUtf8 = (flags and 0x100) != 0
        val offsetsStart = start + headerSize
        if (offsetsStart.toLong() + count.toLong() * 4L > endBound) return null
        if (stringsOffset < 0 || start.toLong() + stringsOffset > endBound) return null
        val dataBase = start + stringsOffset
        return List(count) { i ->
            buf.position(offsetsStart + i * 4)
            val rel = buf.int
            if (rel < 0 || dataBase.toLong() + rel >= endBound) return@List ""
            var p = dataBase + rel
            if (isUtf8) {
                if (p >= endBound) return@List ""
                val u16 = bytes[p++].toInt() and 0xFF
                if ((u16 and 0x80) != 0 && p < endBound) p++
                if (p >= endBound) return@List ""
                var u8 = bytes[p++].toInt() and 0xFF
                if ((u8 and 0x80) != 0) {
                    if (p >= endBound) return@List ""
                    u8 = ((u8 and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF)
                }
                val end = (p.toLong() + u8).coerceAtMost(endBound.toLong()).toInt()
                String(bytes, p, end - p, Charsets.UTF_8)
            } else {
                if (p + 2 > endBound) return@List ""
                buf.position(p)
                var len = buf.short.toInt() and 0xFFFF
                if ((len and 0x8000) != 0) {
                    if (buf.remaining() < 2) return@List ""
                    len = ((len and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
                }
                val maxChars = (endBound - buf.position()) / 2
                val byteLen = len.coerceIn(0, maxChars) * 2
                val arr = ByteArray(byteLen)
                buf.get(arr)
                String(arr, Charsets.UTF_16LE)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxmlViewerScreen(path: String, onBack: () -> Unit) {
    val t = txt()
    val clipboard = LocalClipboardManager.current
    val file = remember(path) { File(path) }
    var xmlText by remember(path) { mutableStateOf<String?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val decoded = withContext(Dispatchers.IO) { AxmlOps.decode(file) }
        if (decoded == null) failed = true else xmlText = decoded
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Android Binary XML", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.back) } },
                actions = {
                    xmlText?.let { text ->
                        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                            Icon(Icons.Default.ContentCopy, t.copy)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                failed -> Text(t.opFailed, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                xmlText == null -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                else -> SelectionContainer {
                    Text(
                        text = xmlText ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Opsi 3: Hex / Binary Viewer (chunked 64 KB per halaman — hemat RAM)
// ---------------------------------------------------------------------------

private const val HEX_PAGE_SIZE = 64 * 1024

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HexViewerScreen(path: String, onBack: () -> Unit) {
    val t = txt()
    val file = remember(path) { File(path) }
    val totalBytes = remember(path) { file.length().coerceAtLeast(0L) }
    val totalPages = remember(totalBytes) { ((totalBytes + HEX_PAGE_SIZE - 1) / HEX_PAGE_SIZE).coerceAtLeast(1L).toInt() }
    var page by remember(path) { mutableStateOf(0) }
    var lines by remember(path, page) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(path, page) {
        lines = withContext(Dispatchers.IO) {
            try {
                val offset = page.toLong() * HEX_PAGE_SIZE
                val toRead = (totalBytes - offset).coerceIn(0L, HEX_PAGE_SIZE.toLong()).toInt()
                val bytes = ByteArray(toRead)
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    raf.readFully(bytes)
                }
                val out = ArrayList<String>((toRead + 15) / 16)
                var i = 0
                while (i < bytes.size) {
                    val rowLen = (bytes.size - i).coerceAtMost(16)
                    val hex = StringBuilder(48)
                    val ascii = StringBuilder(16)
                    for (j in 0 until 16) {
                        if (j < rowLen) {
                            val b = bytes[i + j].toInt() and 0xFF
                            hex.append(String.format("%02X ", b))
                            ascii.append(if (b in 32..126) b.toChar() else '.')
                        } else {
                            hex.append("   ")
                        }
                    }
                    out.add(String.format("%08X  %s |%s|", offset + i, hex.toString(), ascii.toString()))
                    i += 16
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "HEX • ${FileOps.formatSize(totalBytes)} • ${page + 1}/$totalPages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.back) } },
                actions = {
                    if (totalPages > 1) {
                        TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("<") }
                        TextButton(onClick = { if (page + 1 < totalPages) page++ }, enabled = page + 1 < totalPages) { Text(">") }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = lines
            if (current == null) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
            } else if (current.isEmpty()) {
                Text(t.emptyFolder, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            } else {
                SelectionContainer {
                    LazyColumn(
                        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(current) { line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
