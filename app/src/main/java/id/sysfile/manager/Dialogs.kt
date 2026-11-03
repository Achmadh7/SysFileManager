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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
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

// Semua dialog SysFile: folder baru/ganti nama, properti, urutkan, kompres, kata sandi ZIP.

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = txt()
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text(if (title == t.newFolder) t.create else t.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@Composable
fun PropertiesDialog(entry: FEntry, onDismiss: () -> Unit, onNotify: (String) -> Unit) {
    val t = txt()
    val clipboard = LocalClipboardManager.current
    var dirSize by remember { mutableStateOf<Long?>(if (entry.isDir) null else entry.size) }
    LaunchedEffect(entry.path) {
        if (entry.isDir) {
            dirSize = withContext(Dispatchers.IO) { FileOps.sizeOf(entry.file) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(if (entry.isDir) t.propFolder else t.propFile) },
                    leadingContent = {
                        Icon(if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name), null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propPath) },
                    supportingContent = { Text(entry.path) },
                    leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                    trailingContent = {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(entry.path))
                            onNotify(t.copiedDone)
                        }) {
                            Icon(Icons.Default.ContentCopy, t.copy)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propSize) },
                    supportingContent = { Text(dirSize?.let { FileOps.formatSize(it) } ?: "…") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(t.propModified) },
                    supportingContent = {
                        Text(
                            java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, appLocale()
                            ).format(java.util.Date(entry.modified))
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Schedule, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } }
    )
}

@Composable
fun SortDialog(
    sortKey: SortKey,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onPick: (SortKey, Boolean) -> Unit
) {
    val t = txt()
    var key by remember { mutableStateOf(sortKey) }
    var asc by remember { mutableStateOf(ascending) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.sortBy) },
        text = {
            Column {
                listOf(SortKey.NAME to t.sortName, SortKey.DATE to t.sortDate, SortKey.SIZE to t.sortSize).forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { key = k }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == k, onClick = { key = k })
                        Text(label)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = asc, onClick = { asc = true })
                    Text(t.sortAscending)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = false }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !asc, onClick = { asc = false })
                    Text(t.sortDescending)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(key, asc) }) { Text(t.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    val t = txt()
    var name by remember { mutableStateOf("archive") }
    var kind by remember { mutableStateOf("zip") }
    var password by remember { mutableStateOf("") }
    val kinds = listOf("zip" to "ZIP", "tar" to "TAR", "tar.gz" to "TAR.GZ", "tar.bz2" to "TAR.BZ2", "tar.xz" to "TAR.XZ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.compress) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(t.archiveName) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(kinds) { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
                }
                if (kind == "zip") {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(t.zipPassword) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), kind, password.ifBlank { null }) },
                enabled = name.isNotBlank()
            ) { Text(t.compress) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = txt()
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(t.zipPasswordTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.zipPasswordBody, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(t.zipPasswordTitle) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) { Text(t.extract) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@Composable
private fun ApkActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}


/** Satu baris pilihan berikon + judul + subjudul pada dialog aksi arsip. */
@Composable
private fun ArchiveActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Pilihan saat berkas ZIP diketuk (meniru referensi yang dipilih Achmad):
 * Ekstrak Di Sini (ke folder bernama seperti arsip), Buka Arsip (lihat isi
 * dulu), atau Ekstrak ke Jalur Kustom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveActionsDialog(
    entry: FEntry,
    folderName: String,
    onDismiss: () -> Unit,
    onExtractHere: () -> Unit,
    onOpenArchive: () -> Unit,
    onCustomPath: () -> Unit
) {
    val t = txt()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Unarchive, null) },
        title = { Text(t.extractArchive) },
        text = {
            Column {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ArchiveActionRow(Icons.Default.Unarchive, t.extract, "$folderName/", onExtractHere)
                ArchiveActionRow(Icons.Default.Folder, t.openArchive, t.browseFirst, onOpenArchive)
                ArchiveActionRow(Icons.Default.CreateNewFolder, t.extractCustom, t.pickLocation, onCustomPath)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

/** Pemilih folder tujuan (untuk "Ekstrak ke Jalur Kustom"): telusuri folder, lalu pilih yang sedang dibuka. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    startPath: String,
    title: String,
    onPick: (String) -> Unit,
    onCancel: () -> Unit
) {
    val t = txt()
    var current by remember { mutableStateOf(startPath) }
    var dirs by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    LaunchedEffect(current) {
        dirs = withContext(Dispatchers.IO) {
            FileOps.listDir(File(current), true, SortKey.NAME, true)?.filter { it.isDir } ?: emptyList()
        }
    }
    val parent = remember(current) { File(current).parent }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (parent != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { current = parent }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowUpward, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("..", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    items(dirs, key = { it.path }) { dir ->
                        Row(
                            Modifier.fillMaxWidth().clickable { current = dir.path }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = Color(0xFFFFA000))
                            Spacer(Modifier.width(12.dp))
                            Text(dir.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text(t.selectFolder) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(t.cancel) } }
    )
}

/** Pilihan aksi saat berkas APK/APKS/XAPK/AAB diketuk: Inspeksi, Ekstrak, atau Instal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkActionsDialog(
    entry: FEntry,
    onDismiss: () -> Unit,
    onInspect: () -> Unit,
    onExtract: () -> Unit,
    onInstall: () -> Unit
) {
    val t = txt()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Archive, null) },
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ApkActionRow(Icons.Default.Search, t.apkInspect, onInspect)
                ApkActionRow(Icons.Default.Archive, t.apkExtract, onExtract)
                ApkActionRow(Icons.Default.InstallMobile, t.apkInstall, onInstall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

/** Daftar isi arsip (Inspeksi): nama entri + ukuran, folder ditandai tanpa ukuran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveContentsDialog(
    entry: FEntry,
    items: List<Pair<String, Long>>,
    onDismiss: () -> Unit
) {
    val t = txt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (items.isEmpty()) {
                Text(t.archiveEmpty)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(items) { (name, size) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (size < 0) Icons.Default.Folder else Icons.Default.Description,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (size >= 0) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    FileOps.formatSize(size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } }
    )
}
