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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.NoteAdd
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
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
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

/** Kepala jelajah: bar pencarian menyatu (urut + tampilan di ujungnya) dan baris jalur berupa pil yang bisa diketuk (KS2 r69). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseHeaderBar(
    currentPath: String,
    currentRoot: RootInfo?,
    gridView: Boolean,
    sortKey: SortKey,
    sortAsc: Boolean,
    onSearch: () -> Unit,
    onSortPick: (SortKey, Boolean) -> Unit,
    onToggleView: () -> Unit,
    onNavigate: (List<String>) -> Unit
) {
    val t = txt()
    val rootPath = currentRoot?.path ?: currentPath
    val relative = currentPath.removePrefix(rootPath).trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    // KS2 (r69): jalur jadi pil — tumpukan dibangun ulang dari root ke
    // tiap segmen, jadi ketuk pil = lompat ke folder itu.
    val ancestorPaths = buildList {
        add(rootPath)
        var acc = rootPath
        for (seg in segments) {
            acc = if (acc.endsWith("/")) acc + seg else "$acc/$seg"
            add(acc)
        }
    }
    val labels = listOf(currentRoot?.let { rootLabel(it, t) } ?: rootPath) + segments
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = "",
                        onQueryChange = {},
                        onSearch = { onSearch() },
                        expanded = false,
                        onExpandedChange = { if (it) onSearch() },
                        placeholder = { Text(t.searchInFolder) },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f),
                // r144: inset NOL — SearchBar bawaan mencadangkan tinggi
                // status bar di atas pil (windowInsets default), padahal
                // ia duduk di bawah TopAppBar yang inset-nya sudah
                // dikonsumsi Scaffold; tanpa ini pil terdorong ~36dp.
                windowInsets = WindowInsets(0.dp)
            ) {}
            Spacer(Modifier.width(8.dp))
            // Tombol tunggal: ikon menunjukkan tampilan TUJUAN (bukan yang aktif).
            FilledTonalIconButton(onClick = onToggleView) {
                Icon(
                    if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (gridView) t.listShort else t.gridShort
                )
            }
        }
        // Opsi 2 (Status Volume Banner): tampil hanya untuk Root / USB OTG / SD (Internal dihapus).
        if (currentRoot != null && currentRoot.kind != "internal") {
            val rootActive = RootShell.state == RootShell.GRANTED
            val bannerColor = when (currentRoot.kind) {
                "root" -> if (rootActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                "usb", "sd" -> Color(0xFF06B6D4)
                else -> MaterialTheme.colorScheme.primary
            }
            val bannerPrefix = when (currentRoot.kind) {
                "root" -> "\u26A1 "
                "usb", "sd" -> "\uD83D\uDD0C "
                else -> "\uD83D\uDCF1 "
            }
            val bannerStatus = when (currentRoot.kind) {
                "root" -> if (rootActive) t.rootActive else t.rootLimited
                else -> if (currentRoot.total > 0) t.freeOf(FileOps.formatSize(currentRoot.free)) else currentRoot.path
            }
            val rightBadge = when (currentRoot.kind) {
                "root" -> if (rootActive) "RW" else "r-x"
                "usb", "sd" -> "RW"
                else -> if (currentRoot.total > 0) {
                    val usedPct = (((currentRoot.total - currentRoot.free).coerceAtLeast(0L).toDouble() / currentRoot.total.toDouble()) * 100.0).toInt()
                    "$usedPct%"
                } else "OK"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bannerColor.copy(alpha = 0.14f))
                    .border(1.dp, bannerColor.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = bannerPrefix + rootLabel(currentRoot, t) + " \u2022 " + bannerStatus,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = bannerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bannerColor.copy(alpha = 0.22f))
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = rightBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = bannerColor
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                val active = index == labels.lastIndex
                val isNothing = LocalNothingTheme.current
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        isNothing && active -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (isNothing && !active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    onClick = { onNavigate(ancestorPaths.take(index + 1)) },
                    enabled = !active
                ) {
                    Text(
                        if (isNothing && active) label.uppercase() else label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isNothing && active -> MaterialTheme.colorScheme.onPrimary
                            active -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (index < labels.lastIndex) {
                    Text(
                        " \u203a ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Chip urutan: ketuk chip aktif = balik arah (panah), ketuk lainnya = pilih
        // kunci itu. Nama mulai naik (A→Z); tanggal & ukuran mulai turun.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                SortKey.NAME to t.sortName,
                SortKey.DATE to t.sortDate,
                SortKey.SIZE to t.sortSize
            ).forEach { (key, label) ->
                val active = sortKey == key
                val isNothing = LocalNothingTheme.current
                FilterChip(
                    selected = active,
                    onClick = {
                        if (active) onSortPick(key, !sortAsc)
                        else onSortPick(key, key == SortKey.NAME)
                    },
                    shape = RoundedCornerShape(50),
                    colors = if (isNothing) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = active,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    label = { Text(label) },
                    leadingIcon = if (active) {
                        {
                            Icon(
                                if (sortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                null,
                                Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowseScreen(
    currentPath: String,
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
    onNewFile: () -> Unit,
    counts: Map<String, Int>,
    lockedPaths: Set<String> = emptySet(),
    safHasGrant: Boolean = false,
    onSafAction: (() -> Unit)? = null,
    safSubtreeBlocked: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val t = txt()
    Column(Modifier.fillMaxSize()) {
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
        IosSpokesPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                !loaded -> LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
                entries.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val readable = File(currentPath).canRead()
                    // Folder terkunci sistem (Android/data, Android/obb):
                    // canRead() bisa true padahal listFiles() kosong, jadi
                    // tanpa ini pengguna hanya melihat "Folder ini kosong"
                    // tanpa jalan keluar — tawarkan SAF seperti saat
                    // foldernya memang tak terbaca.
                    val restricted = FileOps.isRestrictedDir(currentPath)
                    Text(
                        if (readable && !restricted) t.emptyFolder else t.folderInaccessible,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (restricted && safSubtreeBlocked && !safHasGrant && onSafAction != null) {
                        // r170: sejak perbaikan CVE-2025-26427 (buletin Mei
                        // 2025, patch 2025-05-01) DocumentsUI menolak memilih
                        // subfolder di dalam Android/data|obb|sandbox — tombol
                        // "Gunakan folder ini" tidak akan aktif, jadi jangan
                        // tawarkan jalan yang mustahil; arahkan ke akses root.
                        Spacer(Modifier.height(14.dp))
                        Text(
                            t.safSubtreeBlocked,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        if ((!readable || restricted) && onSafAction != null) {
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onSafAction) {
                                Text(if (safHasGrant) t.safOpen else t.safGrant)
                            }
                        }
                        if (restricted && !safHasGrant && onSafAction != null) {
                            // Pemilih sistem membuang URI awal di dalam Android/data|obb,
                            // jadi ia tidak terbuka di folder ini — beri tahu pengguna
                            // persis folder mana yang harus dibuka di pemilih.
                            val hintRel = currentPath.substringAfter("/Android/", "")
                            val hintPath = if (hintRel.isNotEmpty()) "Android/$hintRel"
                                else File(currentPath).name
                            Spacer(Modifier.height(10.dp))
                            Text(
                                t.safPickHint(hintPath),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                gridView -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
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
                            locked = FileOps.isLockedFolder(entry.path, lockedPaths)
                        )
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            selectionMode = selected.isNotEmpty(),
                            isSelected = selected.contains(entry.path),
                            itemCount = counts[entry.path],
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPress(entry) },
                            locked = FileOps.isLockedFolder(entry.path, lockedPaths)
                        )
                    }
                }
            }
            if (selected.isEmpty()) {
                var fabOpen by remember { mutableStateOf(false) }
                if (fabOpen) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { fabOpen = false }
                    )
                }
                val isNothing = LocalNothingTheme.current
                FloatingActionButtonMenu(
                    expanded = fabOpen,
                    button = {
                        // Berlabel saat tertutup ("Baru"/"New"); menciut jadi ikon X saat menu terbuka.
                        ExtendedFloatingActionButton(
                            onClick = { fabOpen = !fabOpen },
                            expanded = !fabOpen,
                            shape = if (isNothing) CircleShape else FloatingActionButtonDefaults.extendedFabShape,
                            containerColor = if (isNothing) NothingSignalRed else FloatingActionButtonDefaults.containerColor,
                            contentColor = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = { Icon(if (fabOpen) Icons.Default.Close else Icons.Default.Add, null) },
                            text = { Text(if (isNothing) t.newShort.uppercase() else t.newShort) }
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; onNewFolder() },
                        text = { Text(t.newFolder) },
                        icon = { Icon(Icons.Default.CreateNewFolder, null) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = { fabOpen = false; onNewFile() },
                        text = { Text(t.newFile) },
                        icon = { Icon(Icons.Default.NoteAdd, null) }
                    )
                }
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
            shadowElevation = 6.dp,
            modifier = Modifier.nothingBorder(RoundedCornerShape(28.dp))
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

@Composable
fun ClipboardBar(
    count: Int,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    val t = txt()
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .nothingBorder(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFFB020).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ContentPaste, null,
                            tint = Color(0xFFFFB020), modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.clipboard,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            t.pasteHere(count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, t.cancel) }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
                    Text(t.pasteHereAction)
                }
            }
        }
    }
}
/** AU2 (r74): bar mini-player audio — tetap tampil di bawah layar
 *  selama audio diputar, walau pengguna pindah folder/tab. */
@Composable
fun MiniPlayerBar(
    name: String,
    positionMs: Int,
    durationMs: Int,
    playing: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.nothingBorder(RoundedCornerShape(28.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2DD4BF).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${FileOps.formatDuration(positionMs)} / ${FileOps.formatDuration(durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null)
                }
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Layar pencarian
// ---------------------------------------------------------------------------
