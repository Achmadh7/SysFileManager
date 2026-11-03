package id.sysfile.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Layar penjelajah berkas: breadcrumb, daftar/grid, bilah pilihan dan papan klip.

/** Baris chip jalur folder, kini menempel di bawah bilah judul atas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbBar(
    currentPath: String,
    currentRoot: RootInfo?,
    onNavigate: (List<String>) -> Unit
) {
    val t = txt()
    val rootPath = currentRoot?.path ?: currentPath
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    val crumbPaths = segments.scan(rootPath) { acc, seg -> "$acc/$seg" }
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = segments.isEmpty(),
                onClick = { onNavigate(listOf(rootPath)) },
                label = { Text(currentRoot?.let { rootLabel(it, t) } ?: rootPath) }
            )
        }
        itemsIndexed(segments) { index, seg ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(
                selected = index == segments.lastIndex,
                onClick = { onNavigate(crumbPaths.drop(1).take(index + 1).let { listOf(rootPath) + it }) },
                label = { Text(seg) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseScreen(
    currentPath: String,
    currentRoot: RootInfo?,
    entries: List<FEntry>,
    loaded: Boolean,
    gridView: Boolean,
    selected: Set<String>,
    onNavigate: (List<String>) -> Unit,
    onOpenEntry: (FEntry) -> Unit,
    onLongPress: (FEntry) -> Unit,
    onGoUp: () -> Unit,
    canGoUp: Boolean,
    onNewFolder: () -> Unit,
    sortLabel: String,
    onSortClick: () -> Unit,
    onToggleView: () -> Unit,
    counts: Map<String, Int>,
    lockedPaths: Set<String> = emptySet()
) {
    val t = txt()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onSortClick,
                label = { Text(t.sortBy + ": " + sortLabel) },
                leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = gridView,
                onClick = onToggleView,
                label = { Text(if (gridView) t.gridView else t.listView) },
                leadingIcon = {
                    Icon(if (gridView) Icons.Default.GridView else Icons.Default.ViewList, null, Modifier.size(18.dp))
                }
            )
            Spacer(Modifier.weight(1f))
            if (currentRoot != null && currentRoot.total > 0) {
                Text(
                    rootLabel(currentRoot, t) + " · " + t.free(FileOps.formatSize(currentRoot.free)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (canGoUp) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onGoUp() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowUpward, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("..", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                !loaded -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                entries.isEmpty() -> Text(
                    if (File(currentPath).canRead()) t.emptyFolder else t.folderInaccessible,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                gridView -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        FileCell(
                            entry = entry,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = selected.contains(entry.path),
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) },
                            locked = entry.path in lockedPaths
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = selected.contains(entry.path),
                            itemCount = counts[entry.path],
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) },
                            locked = entry.path in lockedPaths
                        )
                    }
                }
            }
            if (selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNewFolder,
                    icon = { Icon(Icons.Default.CreateNewFolder, null) },
                    text = { Text(t.newFolder) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bilah aksi pilihan & papan klip
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionBar(
    single: Boolean,
    singleEntry: FEntry?,
    canExtract: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onProperties: () -> Unit,
    onSelectAll: () -> Unit,
    singleIsDir: Boolean = false,
    singleLocked: Boolean = false,
    onToggleLock: () -> Unit = {}
) {
    val t = txt()
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillMicroAction(Icons.Default.ContentCopy, t.copy, onCopy)
                PillExtractAction(canExtract, t.extractShort, onExtract)
                PillMicroAction(Icons.Default.Archive, t.compressShort, onCompress)
                PillMicroAction(Icons.Default.Delete, t.delete, onDelete)
                Box {
                    PillMicroAction(Icons.Default.MoreVert, t.more, { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(t.cut) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                            onClick = { menuOpen = false; onCut() }
                        )
                        if (single) {
                            DropdownMenuItem(
                                text = { Text(t.rename) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuOpen = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text(t.share) },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = { menuOpen = false; onShare() }
                            )
                            DropdownMenuItem(
                                text = { Text(t.properties) },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { menuOpen = false; onProperties() }
                            )
                            if (singleIsDir) {
                                DropdownMenuItem(
                                    text = { Text(if (singleLocked) t.unlockFolder else t.lockFolder) },
                                    leadingIcon = {
                                        Icon(if (singleLocked) Icons.Default.LockOpen else Icons.Default.Lock, null)
                                    },
                                    onClick = { menuOpen = false; onToggleLock() }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(t.selectAll) },
                            leadingIcon = { Icon(Icons.Default.DoneAll, null) },
                            onClick = { menuOpen = false; onSelectAll() }
                        )
                    }
                }
            }
        }
    }
}

/** Aksi berlabel mikro di pill seleksi: ikon dengan label kecil di bawahnya. */
@Composable
private fun PillMicroAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, label, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Aksi Ekstrak di pill seleksi: segmen slate berlabel mikro saat pilihan berisi
 * arsip; selain itu polos dan nonaktif. Labelnya pendek ("Ekstrak") sesuai
 * varian Label Mikro — teks panjang "Ekstrak di Sini" tidak dipakai di sini.
 */
@Composable
private fun PillExtractAction(enabled: Boolean, label: String, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (enabled) Color(0xFF3D4B63) else Color.Transparent,
        label = "extractBg"
    )
    val fg = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(shape = RoundedCornerShape(18.dp), color = bg, contentColor = fg) {
        Column(
            Modifier
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 13.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(Icons.Default.Unarchive, label, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClipboardBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    val t = txt()
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            Text(
                t.pasteHere(count),
                Modifier.padding(start = 14.dp, end = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onPaste) { Text(t.paste) }
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, t.cancel) }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar pencarian
// ---------------------------------------------------------------------------
