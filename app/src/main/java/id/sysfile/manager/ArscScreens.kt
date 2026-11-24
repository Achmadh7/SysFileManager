package id.sysfile.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Layar pembaca .arsc (tabel resource Android terkompilasi) — AR3 (r235).
 * JANGAN ubah Viewers.kt untuk fitur ini.
 *
 * Navigasi internal: package -> tipe -> daftar entry -> detail entry
 * (semua varian konfigurasi + baris map untuk entry kompleks).
 * Parse berat (buka berkas) berjalan di IO; filter pencarian memakai
 * contains(ignoreCase = true) supaya tidak mengalokasi lowercase per
 * baris pada tiap ketikan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArscBrowseScreen(path: String, onBack: () -> Unit) {
    val t = txt()
    var state by remember { mutableStateOf<ArscLoadState>(ArscLoadState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<ArscPackage?>(null) }
    var selectedType by remember { mutableStateOf<ArscType?>(null) }
    var selectedGroup by remember { mutableStateOf<ArscEntryGroup?>(null) }

    LaunchedEffect(path) {
        state = when (val result = withContext(Dispatchers.IO) { ArscOps.open(File(path)) }) {
            is ArscOps.OpenResult.Ok -> ArscLoadState.Loaded(result.file)
            ArscOps.OpenResult.TooLarge -> ArscLoadState.TooLarge
            ArscOps.OpenResult.Invalid -> ArscLoadState.Failed
        }
    }

    val loaded = state as? ArscLoadState.Loaded
    val file = loaded?.file
    val back: () -> Unit = {
        when {
            selectedGroup != null -> selectedGroup = null
            selectedType != null -> selectedType = null
            selectedPackage != null -> selectedPackage = null
            else -> onBack()
        }
    }
    BackHandler(
        enabled = selectedGroup != null || selectedType != null || selectedPackage != null
    ) { back() }

    val title = selectedGroup?.key
        ?: selectedType?.name
        ?: selectedPackage?.name
        ?: File(path).name

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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ArscLoadState.Loading -> LoadingIndicator(
                    Modifier.align(Alignment.Center).size(52.dp)
                )
                ArscLoadState.TooLarge -> Text(
                    t.arscTooLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                ArscLoadState.Failed -> Text(
                    t.readFailed,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is ArscLoadState.Loaded -> {
                    val group = selectedGroup
                    val type = selectedType
                    val pkg = selectedPackage
                    when {
                        group != null && file != null && type != null -> ArscEntryDetailView(
                            file = file,
                            type = type,
                            group = group
                        )
                        type != null && file != null -> ArscEntryListView(
                            file = file,
                            type = type,
                            query = query,
                            onQueryChange = { query = it },
                            onGroupClick = { selectedGroup = it }
                        )
                        pkg != null -> ArscTypeListView(
                            pkg = pkg,
                            onTypeClick = { selectedType = it }
                        )
                        file != null -> ArscPackageListView(
                            file = file,
                            onPackageClick = { selectedPackage = it }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface ArscLoadState {
    data object Loading : ArscLoadState
    data object TooLarge : ArscLoadState
    data object Failed : ArscLoadState
    data class Loaded(val file: ArscFile) : ArscLoadState
}

/** AR3: kartu info + daftar package. */
@Composable
private fun ArscPackageListView(
    file: ArscFile,
    onPackageClick: (ArscPackage) -> Unit
) {
    val t = txt()
    val h = file.header
    Column(Modifier.fillMaxSize()) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArscInfoRow(t.arscFileSize, FileOps.formatSize(h.fileSize))
                ArscInfoRow(t.arscGlobalStrings, h.globalStringCount.toString())
                HorizontalDivider(
                    Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ArscStat(t.arscPackages, file.packages.size)
                    ArscStat(t.arscTypes, file.totalTypes)
                    ArscStat(t.arscEntries, file.totalEntries)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(file.packages, key = { it.id }) { pkg ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPackageClick(pkg) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pkg.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "id ${pkg.id} • ${pkg.types.size} ${t.arscTypes.lowercase()} • " +
                                "${pkg.types.sumOf { it.entryCount }} ${t.arscEntries.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

/** AR3: daftar tipe dalam satu package. */
@Composable
private fun ArscTypeListView(
    pkg: ArscPackage,
    onTypeClick: (ArscType) -> Unit
) {
    val t = txt()
    LazyColumn(Modifier.fillMaxSize()) {
        items(pkg.types, key = { it.id }) { type ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onTypeClick(type) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        type.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${type.variants.size} ${t.arscVariants.lowercase()} • " +
                            "${type.entryCount} ${t.arscEntries.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

/** AR3: cari + daftar entry dalam satu tipe. */
@Composable
private fun ArscEntryListView(
    file: ArscFile,
    type: ArscType,
    query: String,
    onQueryChange: (String) -> Unit,
    onGroupClick: (ArscEntryGroup) -> Unit
) {
    val t = txt()
    val filtered = remember(type, query) {
        val q = query.trim()
        if (q.isEmpty()) type.groups
        else type.groups.filter { g ->
            g.key.contains(q, ignoreCase = true) ||
                g.variants.any { it.entry.display.contains(q, ignoreCase = true) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(t.arscSearchHint) },
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
                Text(t.arscNoMatch, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.index }) { group ->
                    val first = group.variants.firstOrNull()?.entry
                    val isComplex = first?.isComplex == true
                    val badge = when {
                        group.variants.size > 1 -> "${group.variants.size} ${t.arscVariants.lowercase()}"
                        first != null && first.display.isEmpty() -> ""
                        else -> group.variants.firstOrNull()
                            ?.let { type.variants[it.variantIndex].config.qualifier }
                            .orEmpty()
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onGroupClick(group) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                group.key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                isComplex -> t.arscComplex
                                first != null -> first.display
                                else -> ""
                            }
                            if (subtitle.isNotEmpty() || badge.isNotEmpty()) {
                                Text(
                                    listOf(subtitle, badge).filter { it.isNotEmpty() }
                                        .joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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

/** AR3: detail satu entry — semua varian konfigurasi + baris map. */
@Composable
private fun ArscEntryDetailView(
    file: ArscFile,
    type: ArscType,
    group: ArscEntryGroup
) {
    val t = txt()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                group.key,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold
            )
            Text(
                "0x" + group.resId.toUInt().toString(16).uppercase().padStart(8, '0'),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }
        group.variants.forEach { variantEntry ->
            val variant = type.variants[variantEntry.variantIndex]
            val entry = variantEntry.entry
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val qualifier = variant.config.qualifier
                        Text(
                            if (qualifier.isEmpty()) t.arscDefault
                            else qualifier,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (entry.isComplex) {
                            if (entry.parentRef != 0) {
                                ArscInfoRow(t.arscParent, file.mapName(entry.parentRef))
                            }
                            entry.maps.forEach { map ->
                                ArscInfoRow(
                                    file.mapName(map.nameRef),
                                    file.formatValue(map.value)
                                )
                            }
                        } else if (entry.value != null) {
                            ArscInfoRow(t.arscValue, entry.display)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ArscInfoRow(label: String, value: String) {
    // Kolom (bukan baris) supaya nilai string yang panjang terbungkus
    // penuh, bukan terpotong oleh lebar baris.
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ArscStat(label: String, value: Int) {
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
