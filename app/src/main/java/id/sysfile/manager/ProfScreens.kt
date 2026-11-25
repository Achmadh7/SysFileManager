package id.sysfile.manager

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layar inspeksi berkas ART Baseline Profile (`.prof`) & Metadata (`.profm`).
 * Jika berkas adalah profil teks biasa -> dialihkan ke [onOpenAsText]; jika biner
 * tak dikenali -> dialihkan ke [onOpenAsHex].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfBrowseScreen(
    path: String,
    onBack: () -> Unit,
    onOpenAsText: () -> Unit,
    onOpenAsHex: () -> Unit
) {
    val t = txt()
    var result by remember(path) { mutableStateOf<ProfOps.OpenResult?>(null) }
    var query by remember(path) { mutableStateOf("") }

    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) { ProfOps.open(File(path)) }
        when (loaded) {
            ProfOps.OpenResult.PlainText -> onOpenAsText()
            ProfOps.OpenResult.BinaryFallback, ProfOps.OpenResult.TooLarge -> onOpenAsHex()
            is ProfOps.OpenResult.ArtProfile -> result = loaded
        }
    }

    val isNothing = LocalNothingTheme.current
    val info = (result as? ProfOps.OpenResult.ArtProfile)?.info

    Scaffold(
        containerColor = if (isNothing) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (info != null) {
                            Text(
                                "${if (info.isMetadata) "PROFM" else "PROF"} v${info.versionCode} · ${info.androidTarget}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.back) }
                },
                actions = {
                    TextButton(onClick = onOpenAsHex) { Text("HEX") }
                }
            )
        }
    ) { padding ->
        if (info == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(52.dp))
            }
            return@Scaffold
        }

        val cardBg = if (isNothing) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
        val q = query.trim().lowercase()
        val filteredEntries = remember(info.dexEntries, q) {
            if (q.isEmpty()) info.dexEntries else info.dexEntries.filter { it.name.lowercase().contains(q) }
        }
        val filteredStrings = remember(info.extractedStrings, q) {
            if (q.isEmpty()) info.extractedStrings else info.extractedStrings.filter { it.lowercase().contains(q) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.fillMaxWidth().nothingBorder(RoundedCornerShape(22.dp))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (info.isMetadata) "ART Profile Metadata (.profm)" else "ART Baseline Profile (.prof)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ProfMetaRow(t.profVersion, "v${info.versionCode} (${if (info.isMetadata) "prm\\0" else "pro\\0"})")
                        ProfMetaRow(t.profAndroidTarget, info.androidTarget)
                        ProfMetaRow(t.propSize, FileOps.formatSize(info.rawSize))
                        ProfMetaRow(t.profDexCount, (info.dexCountDeclared ?: 0).toString())
                        if (info.inflatedBytes > 0) {
                            ProfMetaRow(
                                t.profCompression,
                                "${FileOps.formatSize(info.compressedBytes)} -> ${FileOps.formatSize(info.inflatedBytes)} (${info.zlibStreamCount} zlib)"
                            )
                        }
                    }
                }
            }

            if (info.dexEntries.isNotEmpty() || info.extractedStrings.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(t.profSearchHint) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t.close) }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (filteredEntries.isNotEmpty()) {
                item {
                    Text(
                        "${t.profDexEntries} (${filteredEntries.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(filteredEntries, key = { "dex-${it.index}-${it.name}" }) { dex ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier.fillMaxWidth().nothingBorder(RoundedCornerShape(16.dp))
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                dex.name,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaParts = listOfNotNull(
                                dex.checksumHex?.let { "CRC32: $it" },
                                dex.methodIdsCount?.let { "Methods: $it" },
                                dex.hotRegionBytes?.let { "Hot region: ${FileOps.formatSize(it)}" }
                            )
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    metaParts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            if (filteredStrings.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${t.dexStrings} (${filteredStrings.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(filteredStrings, key = { "str-$it" }) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
            } else if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        t.searchNoResults,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfMetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}
