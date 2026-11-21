package id.sysfile.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layar pembaca .dex (DX1+DX2+DX3, r186). JANGAN ubah Viewers.kt untuk
 * fitur ini — pembaca teks adalah topik terpisah.
 *
 * Navigasi internal: daftar kelas -> detail kelas -> smali metode.
 * Semua parse berat (buka berkas, class_data, smali) berjalan di IO.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DexBrowseScreen(path: String, onBack: () -> Unit, isLiquid: Boolean = false) {
    val t = txt()
    var state by remember { mutableStateOf<DexLoadState>(DexLoadState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf<Int?>(null) }
    var selectedMethod by remember { mutableStateOf<DexMethodInfo?>(null) }

    LaunchedEffect(path) {
        state = when (val result = withContext(Dispatchers.IO) { DexOps.open(File(path)) }) {
            is DexOps.OpenResult.Ok -> DexLoadState.Loaded(result.file)
            DexOps.OpenResult.TooLarge -> DexLoadState.TooLarge
            DexOps.OpenResult.Invalid -> DexLoadState.Failed
        }
    }

    val loaded = state as? DexLoadState.Loaded
    val dex = loaded?.file
    val back: () -> Unit = {
        when {
            selectedMethod != null -> selectedMethod = null
            selectedClass != null -> selectedClass = null
            else -> onBack()
        }
    }
    BackHandler(enabled = selectedMethod != null || selectedClass != null) { back() }

    val title = when {
        selectedMethod != null -> selectedMethod!!.name
        selectedClass != null -> dex?.classNames?.getOrNull(selectedClass!!)
            ?.substringAfterLast('.') ?: File(path).name
        else -> File(path).name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, t.home) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).liquidGlassBackground()) {
            when (val s = state) {
                DexLoadState.Loading -> LoadingIndicator(
                    Modifier.align(Alignment.Center).size(52.dp)
                )
                DexLoadState.TooLarge -> Text(
                    t.dexTooLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                DexLoadState.Failed -> Text(
                    t.readFailed,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is DexLoadState.Loaded -> {
                    val method = selectedMethod
                    val classIndex = selectedClass
                    when {
                        method != null && dex != null -> DexSmaliView(dex, method)
                        classIndex != null && dex != null -> DexClassDetailView(
                            dex = dex,
                            classIndex = classIndex,
                            onMethodClick = { selectedMethod = it }
                        )
                        dex != null -> DexClassListView(
                            dex = dex,
                            query = query,
                            onQueryChange = { query = it },
                            onClassClick = { selectedClass = it },
                            isLiquid = isLiquid
                        )
                    }
                }
            }
        }
    }
}

private sealed interface DexLoadState {
    data object Loading : DexLoadState
    data object TooLarge : DexLoadState
    data object Failed : DexLoadState
    data class Loaded(val file: DexFile) : DexLoadState
}

/** DX1: kartu info header + cari + daftar kelas. */
@Composable
private fun DexClassListView(
    dex: DexFile,
    query: String,
    onQueryChange: (String) -> Unit,
    onClassClick: (Int) -> Unit,
    isLiquid: Boolean = false
) {
    val t = txt()
    // r215: warna kaca dibaca sekali di sini; hanya dipakai di cabang liquid.
    val liquid = LocalLiquid.current
    val h = dex.header
    val filtered = remember(dex, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) dex.classDefs.indices.toList()
        else dex.classDefs.indices.filter { dex.classNames[it].lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest),
            border = null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(if (isLiquid) Modifier.glassBlur(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DexInfoRow(t.version(h.version), FileOps.formatSize(h.fileSize))
                DexInfoRow(
                    t.checksum,
                    if (h.checksumOk) t.dexChecksumOk else t.dexChecksumBad,
                    good = h.checksumOk
                )
                DexInfoRow(
                    t.dexSignature,
                    if (h.signatureOk) t.dexChecksumOk else t.dexChecksumBad,
                    good = h.signatureOk
                )
                HorizontalDivider(
                    Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DexStat(t.dexStrings, h.stringCount)
                    DexStat(t.dexTypes, h.typeCount)
                    DexStat(t.dexProtos, h.protoCount)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DexStat(t.dexFields, h.fieldCount)
                    DexStat(t.dexMethods, h.methodCount)
                    DexStat(t.dexClasses, h.classCount)
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(t.dexSearchHint) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, t.close)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text(t.dexNoMatch, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it }) { index ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onClassClick(index) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            dex.classNames[index],
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DexInfoRow(label: String, value: String, good: Boolean? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = when (good) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun DexStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** DX2: detail satu kelas — superclass, interface, field, metode. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DexClassDetailView(
    dex: DexFile,
    classIndex: Int,
    onMethodClick: (DexMethodInfo) -> Unit
) {
    val t = txt()
    var detail by remember(classIndex) { mutableStateOf<DexClassDetail?>(null) }
    var failed by remember(classIndex) { mutableStateOf(false) }
    LaunchedEffect(classIndex) {
        detail = withContext(Dispatchers.IO) { dex.classDetail(classIndex) }
        if (detail == null) failed = true
    }
    val d = detail
    when {
        failed -> Box(Modifier.fillMaxSize()) {
            Text(t.readFailed, modifier = Modifier.align(Alignment.Center))
        }
        d == null -> Box(Modifier.fillMaxSize()) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
        }
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    d.info.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold
                )
                val flags = DexOps.formatAccessFlags(d.info.accessFlags)
                if (flags.isNotEmpty()) {
                    Text(flags, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                d.info.sourceFile?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                DexInfoRow(t.dexSuperclass, d.info.superclass ?: "-")
                if (d.info.interfaces.isNotEmpty()) {
                    DexInfoRow(t.dexInterfaces, d.info.interfaces.joinToString(", "))
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                Text(
                    "${t.dexFields} (${d.fields.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
            }
            items(d.fields.size) { i ->
                val f = d.fields[i]
                DexMemberRow(
                    title = "${f.name}: ${f.type}",
                    subtitle = DexOps.formatAccessFlags(f.accessFlags)
                        .let { if (it.isEmpty()) null else it },
                    onClick = null
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "${t.dexMethods} (${d.methods.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
            }
            items(d.methods, key = { it.index }) { m ->
                val hasCode = m.codeOff != 0
                val subParts = ArrayList<String>()
                val flags = DexOps.formatAccessFlags(m.accessFlags)
                if (flags.isNotEmpty()) subParts.add(flags)
                if (!hasCode) subParts.add(t.dexNoCode)
                val subtitle: String? = if (subParts.isEmpty()) null
                else subParts.joinToString(" • ")
                DexMemberRow(
                    title = m.signature(),
                    subtitle = subtitle,
                    onClick = if (hasCode) ({ onMethodClick(m) }) else null
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DexMemberRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** DX3: smali satu metode — hanya baca, monospace, geser dua arah. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DexSmaliView(dex: DexFile, method: DexMethodInfo) {
    val t = txt()
    var smali by remember(method) { mutableStateOf<SmaliResult?>(null) }
    LaunchedEffect(method) {
        smali = withContext(Dispatchers.IO) { dex.methodSmali(method) }
    }
    val s = smali
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            method.signature(),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold
        )
        val flags = DexOps.formatAccessFlags(method.accessFlags)
        if (flags.isNotEmpty()) {
            Text(flags, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        if (s == null) {
            Box(Modifier.fillMaxSize()) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
            }
            return@Column
        }
        if (s.truncated) {
            Text(
                t.dexSmaliTruncated(s.lines.size, s.totalUnits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        val text = buildString {
            s.lines.forEach { append(it).append('\n') }
            if (s.note != null) append("; -- ").append(s.note).append('\n')
        }
        Box(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}
