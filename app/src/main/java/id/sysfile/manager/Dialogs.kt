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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
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
    var stats by remember { mutableStateOf<FileOps.FolderStats?>(null) }
    var sums by remember(entry.path) { mutableStateOf<Pair<String, String>?>(null) }
    var computing by remember(entry.path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(entry.path) {
        if (entry.isDir) {
            stats = withContext(Dispatchers.IO) { FileOps.folderStats(entry.file) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3D4B63)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column {
                        PropGroupRow(t.propType, if (entry.isDir) t.propFolder else t.propFile)
                        PropGroupDivider()
                        PropGroupRow(
                            t.propSize,
                            if (entry.isDir) {
                                stats?.let { FileOps.formatSize(it.totalSize) } ?: "\u2026"
                            } else {
                                FileOps.formatSize(entry.size)
                            }
                        )
                        if (entry.isDir) {
                            PropGroupDivider()
                            PropGroupRow(t.propFileCount, stats?.fileCount?.toString() ?: "\u2026")
                            PropGroupDivider()
                            PropGroupRow(t.propFolderCount, stats?.dirCount?.toString() ?: "\u2026")
                            if (stats?.largestName != null) {
                                PropGroupDivider()
                                PropGroupRow(
                                    t.propLargest,
                                    stats!!.largestName + " \u00b7 " + FileOps.formatSize(stats!!.largestSize)
                                )
                            }
                        }
                        PropGroupDivider()
                        PropGroupRow(
                            t.propModified,
                            java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, appLocale()
                            ).format(java.util.Date(entry.modified))
                        )
                        PropGroupDivider()
                        PropGroupRow(t.propPath, ellipsizeMiddle(entry.path)) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(entry.path))
                                onNotify(t.copiedDone)
                            }) { Icon(Icons.Default.ContentCopy, t.copy, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                if (!entry.isDir) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.checksum, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        t.checksumDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (sums == null) {
                                    Button(
                                        enabled = !computing,
                                        onClick = {
                                            computing = true
                                            scope.launch {
                                                sums = withContext(Dispatchers.IO) { FileOps.checksums(entry.file) }
                                                computing = false
                                                if (sums == null) onNotify(t.opFailed)
                                            }
                                        }
                                    ) { Text(if (computing) t.scanning else t.calcShort) }
                                }
                            }
                            if (sums != null) {
                                Spacer(Modifier.height(14.dp))
                                listOf("MD5" to sums!!.first, "SHA-256" to sums!!.second).forEach { (label, hash) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(label, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                hash,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString(hash))
                                            onNotify(t.copiedDone)
                                        }) { Icon(Icons.Default.ContentCopy, t.copy, modifier = Modifier.size(18.dp)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.close) } }
    )
}

/** Satu baris label-nilai di dalam kartu grup Properti (nilai rata kanan). */
@Composable
private fun PropGroupRow(label: String, value: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/** Garis pemisah tipis di dalam kartu grup Properti. */
@Composable
private fun PropGroupDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** Potong string panjang di tengah: awal\u2026akhir, agar jalur tetap terbaca. */
private fun ellipsizeMiddle(s: String, max: Int = 34): String {
    if (s.length <= max) return s
    val head = max / 2
    val tail = max - head - 1
    return s.take(head) + "\u2026" + s.takeLast(tail)
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
private fun ApkActionRow(icon: ImageVector, color: Color, label: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        icon = {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFF8B7CFF).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Unarchive, null,
                    tint = Color(0xFF8B7CFF), modifier = Modifier.size(26.dp)
                )
            }
        },
        title = { Text(t.extractArchive) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                )
                ApkActionRow(
                    Icons.Default.Unarchive, Color(0xFFFF9E45),
                    t.extract, "$folderName/", onExtractHere
                )
                ApkActionRow(
                    Icons.Default.Folder, Color(0xFF8B7CFF),
                    t.openArchive, t.browseFirst, onOpenArchive
                )
                ApkActionRow(
                    Icons.Default.CreateNewFolder, Color(0xFF4DA3FF),
                    t.extractCustom, t.pickLocation, onCustomPath
                )
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
    val context = LocalContext.current
    var info by remember(entry.path) { mutableStateOf<ApkInfo?>(null) }
    var iconBmp by remember(entry.path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry.path) {
        val loaded = withContext(Dispatchers.IO) {
            FileOps.apkInfo(context, entry.file) to FileOps.apkIcon(context, entry.file)?.asImageBitmap()
        }
        info = loaded.first
        iconBmp = loaded.second
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = iconBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(Icons.Default.Archive, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(info?.appName ?: entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val inf = info
                    if (inf != null) {
                        Text(
                            t.version(inf.versionName) + " (" + inf.versionCode + ")",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            inf.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ApkActionRow(Icons.Default.Search, Color(0xFFFF9E45), t.apkInspect, t.apkInspectDesc, onInspect)
                ApkActionRow(Icons.Default.Archive, Color(0xFF8B7CFF), t.apkExtract, t.apkExtractDesc, onExtract)
                ApkActionRow(Icons.Default.InstallMobile, Color(0xFF7ED957), t.apkInstall, t.apkInstallDesc, onInstall)
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
