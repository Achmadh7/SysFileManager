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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Semua dialog SysFile: folder baru/ganti nama, properti, urutkan, kompres, kata sandi ZIP.

/** Tombol dialog ala iOS (r225 X1): label biru, merah bila destruktif. */
data class IosAlertButton(
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** Dialog ala iOS (r225 X1), hanya untuk gaya Liquid: kartu kaca
 *  (tint + rim, tanpa blur — dialog adalah window terpisah sehingga
 *  backdrop tidak bisa menangkap konten aplikasi), judul + teks rata
 *  tengah, tombol bertumpuk dipisah hairline; tepat 2 tombol tanpa
 *  field input boleh berdampingan (twoColumn). Material memakai
 *  AlertDialog asal. */
@Composable
fun IosAlertDialog(
    title: String?,
    text: String?,
    buttons: List<IosAlertButton>,
    content: @Composable (ColumnScope.() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    titleContent: @Composable (() -> Unit)? = null,
    dismissOnClickOutside: Boolean = true,
    twoColumn: Boolean = false
) {
    val liquid = LocalLiquid.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = dismissOnClickOutside)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(min = 280.dp, max = 320.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(liquid.glassTint)
                .border(1.dp, Brush.linearGradient(listOf(liquid.rimStart, liquid.rimEnd)), RoundedCornerShape(28.dp))
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (titleContent != null) {
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                        titleContent()
                    }
                } else if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)
                    )
                }
                if (text != null) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (title != null || titleContent != null) 8.dp else 20.dp)
                    )
                }
                if (content != null) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        content()
                    }
                }
                if (buttons.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = liquid.separator, thickness = 0.5.dp)
                    if (twoColumn && buttons.size == 2) {
                        Row(Modifier.fillMaxWidth().height(52.dp)) {
                            IosAlertButtonItem(buttons[0], Modifier.weight(1f))
                            Box(Modifier.width(0.5.dp).fillMaxHeight().background(liquid.separator))
                            IosAlertButtonItem(buttons[1], Modifier.weight(1f))
                        }
                    } else {
                        buttons.forEachIndexed { i, button ->
                            if (i > 0) HorizontalDivider(color = liquid.separator, thickness = 0.5.dp)
                            IosAlertButtonItem(button, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** Satu tombol dialog ala iOS: teks biru (merah bila destruktif), abu-abu bila nonaktif. */
@Composable
private fun IosAlertButtonItem(button: IosAlertButton, modifier: Modifier) {
    Box(
        modifier
            .clickable(enabled = button.enabled) { button.onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            button.label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = when {
                !button.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                button.destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

/** Lingkaran radio ala iOS (r225 X1): kosong / berisi titik biru. */
@Composable
private fun IosRadioCircle(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    var text by remember(initial) { mutableStateOf(initial) }
    if (isLiquid) {
        IosAlertDialog(
            title = title,
            text = null,
            buttons = listOf(
                IosAlertButton(if (title == t.newFolder) t.create else t.save, enabled = text.isNotBlank()) { onConfirm(text) },
                IosAlertButton(t.cancel) { onDismiss() }
            ),
            content = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            },
            onDismissRequest = onDismiss
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
}

@Composable
private fun imgTypeName(type: ImgType, t: Txt): String {
    return when (type) {
        ImgType.BOOT -> t.imgTypeBoot
        ImgType.SPARSE -> t.imgTypeSparse
        ImgType.EXT4 -> t.imgTypeExt4
        ImgType.FAT -> t.imgTypeFat
        ImgType.SUPER -> t.imgTypeSuper
        ImgType.RAW -> t.imgTypeRaw
        ImgType.UNKNOWN -> t.imgTypeUnknown
    }
}

@Composable
fun PropertiesDialog(entry: FEntry, onDismiss: () -> Unit, onNotify: (String) -> Unit) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    val clipboard = LocalClipboardManager.current
    var stats by remember { mutableStateOf<FileOps.FolderStats?>(null) }
    var stat by remember(entry.path) { mutableStateOf<FileOps.StatInfo?>(null) }
    var imgType by remember(entry.path) { mutableStateOf<ImgType?>(null) }
    // r153: info ext4 (mentah / hasil unsparse sparse); ext4Loaded
    // membedakan "masih memuat" (tampil …) dari "gagal parse"
    // (baris disembunyikan).
    var ext4Info by remember(entry.path) { mutableStateOf<Ext4Info?>(null) }
    var ext4Loaded by remember(entry.path) { mutableStateOf(false) }
    var expanded by remember(entry.path) { mutableStateOf(false) }
    var showSums by remember(entry.path) { mutableStateOf(false) }
    var showPerms by remember(entry.path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var sums by remember(entry.path) { mutableStateOf<Pair<String, String>?>(null) }
    var computing by remember(entry.path) { mutableStateOf(false) }
    LaunchedEffect(entry.path) {
        if (entry.isDir) {
            stats = withContext(Dispatchers.IO) { FileOps.folderStats(entry.file) }
        }
        stat = withContext(Dispatchers.IO) { FileOps.statInfo(entry.path) }
        if (FileOps.extOf(entry.name) == "img") {
            imgType = withContext(Dispatchers.IO) { ImgOps.detectImgType(entry.file) }
            // r153: ext4 tampil ukuran volume, ukuran blok, dan label
            // di bawah Jenis citra. r157: sparse dibaca langsung tanpa
            // unsparse. LaunchedEffect memang suspend, jadi withContext
            // di sini legal.
            if (imgType == ImgType.SPARSE || imgType == ImgType.EXT4) {
                ext4Info = withContext(Dispatchers.IO) { ImgOps.readExt4Info(entry.file) }
                ext4Loaded = true
            }
        }
    }
    LaunchedEffect(showSums) {
        if (showSums && sums == null && !computing) {
            computing = true
            sums = withContext(Dispatchers.IO) { FileOps.checksums(entry.file) }
            computing = false
            if (sums == null) onNotify(t.opFailed)
        }
    }
    if (isLiquid) {
        IosAlertDialog(
            title = null,
            text = null,
            buttons = buildList {
                if (showSums) add(IosAlertButton(t.back.uppercase()) { showSums = false })
                else add(IosAlertButton(t.propMore.uppercase()) { expanded = !expanded })
                if (!entry.isDir && !showSums) add(IosAlertButton(t.checksumBtn.uppercase()) { showSums = true })
                add(IosAlertButton(t.close.uppercase()) { onDismiss() })
            },
            content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (showSums) {
                    // Tampilan CEKSUM: MD5 + SHA-256 dihitung otomatis
                    // begitu dibuka, masing-masing dengan tombol salin.
                    Text(t.checksum, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.checksumDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    if (sums == null) {
                        Text(
                            if (computing) t.scanning else t.opFailed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
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
                } else {
                    // PR1 Grid (r111, pilihannya): semua baris jadi grid
                    // 2 kolom (label kiri, nilai kanan) dalam SATU kartu —
                    // info + izin; dialog jadi jauh lebih pendek.
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                        Column {
                            PropGridRow(
                                t.propParent,
                                ellipsizeMiddle(
                                    entry.file.parent?.let { if (it.endsWith("/")) it else "$it/" } ?: "/"
                                )
                            )
                            PropGroupDivider()
                            PropGridRow(t.propType, if (entry.isDir) t.propFolder else t.propFile)
                            if (imgType != null) {
                                PropGroupDivider()
                                PropGridRow(t.imgTypeLabel, imgTypeName(imgType!!, t))
                                if (imgType == ImgType.SPARSE || imgType == ImgType.EXT4) {
                                    if (!ext4Loaded) {
                                        PropGroupDivider()
                                        PropGridRow(t.imgExt4Volume, "\u2026")
                                        PropGroupDivider()
                                        PropGridRow(t.imgExt4BlockSize, "\u2026")
                                        PropGroupDivider()
                                        PropGridRow(t.imgExt4Label, "\u2026")
                                    } else if (ext4Info != null) {
                                        PropGroupDivider()
                                        PropGridRow(t.imgExt4Volume, FileOps.formatSizeExact(ext4Info!!.totalBytes))
                                        PropGroupDivider()
                                        PropGridRow(t.imgExt4BlockSize, FileOps.formatSizeExact(ext4Info!!.blockSize))
                                        if (ext4Info!!.label.isNotEmpty()) {
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4Label, ext4Info!!.label)
                                        }
                                    }
                                }
                            }
                            PropGroupDivider()
                            PropGridRow(
                                t.propSize,
                                if (entry.isDir) {
                                    stats?.let { FileOps.formatSizeExact(it.totalSize) } ?: "\u2026"
                                } else {
                                    FileOps.formatSizeExact(entry.size)
                                }
                            )
                            if (entry.isDir) {
                                PropGroupDivider()
                                PropGridRow(t.propFileCount, stats?.fileCount?.toString() ?: "\u2026")
                                PropGroupDivider()
                                PropGridRow(t.propFolderCount, stats?.dirCount?.toString() ?: "\u2026")
                                if (stats?.largestName != null) {
                                    PropGroupDivider()
                                    PropGridRow(
                                        t.propLargest,
                                        stats!!.largestName + " \u00b7 " + FileOps.formatSize(stats!!.largestSize)
                                    )
                                }
                            }
                            PropGroupDivider()
                            PropGridRow(t.propModified, FileOps.formatIsoTime(entry.modified))
                            if (stat != null) {
                                PropGroupDivider()
                                Row(
                                    Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            Icons.Default.Lock, null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(6.dp).size(15.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        t.propPermsGroup.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                PropGridRow(t.propPerms, FileOps.formatPerms(stat!!.mode), monospace = true) {
                                    IconButton(onClick = { showPerms = true }) {
                                        Icon(Icons.Default.Edit, t.edit, modifier = Modifier.size(18.dp))
                                    }
                                }
                                PropGroupDivider()
                                PropGridRow(t.propOwner, FileOps.ownerName(stat!!.uid))
                                PropGroupDivider()
                                PropGridRow(t.propGroup, FileOps.groupName(stat!!.gid))
                            }
                        }
                    }
                    if (expanded) {
                        // Isi tombol LAGI: jalur lengkap (dengan salin) +
                        // rincian stat lanjutan bila tersedia.
                        Spacer(Modifier.height(12.dp))
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                            Column {
                                PropGridRow(t.propPath, ellipsizeMiddle(entry.path)) {
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(entry.path))
                                        onNotify(t.copiedDone)
                                    }) { Icon(Icons.Default.ContentCopy, t.copy, modifier = Modifier.size(18.dp)) }
                                }
                                if (stat != null) {
                                    PropGroupDivider()
                                    PropGridRow(t.propAccessed, FileOps.formatIsoTime(stat!!.atimeSec * 1000))
                                    PropGroupDivider()
                                    PropGridRow(t.propChanged, FileOps.formatIsoTime(stat!!.ctimeSec * 1000))
                                    PropGroupDivider()
                                    PropGridRow(t.propInode, stat!!.ino.toString())
                                    PropGroupDivider()
                                    PropGridRow(t.propLinks, stat!!.nlink.toString())
                                }
                            }
                        }
                    }
                }
            }
            },
            onDismissRequest = onDismiss,
            titleContent = {
            // Kepala Properti (r23, pilihan Achmad 2026-11-18): pita
            // primaryContainer, petak ikon primary, ikon onPrimary,
            // nama onPrimaryContainer — selalu dari skema resmi
            // (dulu navy #0B1526 + cyan, kini disamakan dengan skema).
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(
                    MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(
                            MaterialTheme.colorScheme.primary
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            }
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
            onDismissRequest = onDismiss,
            title = {
                // Kepala Properti (r23, pilihan Achmad 2026-11-18): pita
                // primaryContainer, petak ikon primary, ikon onPrimary,
                // nama onPrimaryContainer — selalu dari skema resmi
                // (dulu navy #0B1526 + cyan, kini disamakan dengan skema).
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(
                        MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(
                                MaterialTheme.colorScheme.primary
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (showSums) {
                        // Tampilan CEKSUM: MD5 + SHA-256 dihitung otomatis
                        // begitu dibuka, masing-masing dengan tombol salin.
                        Text(t.checksum, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            t.checksumDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        if (sums == null) {
                            Text(
                                if (computing) t.scanning else t.opFailed,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
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
                    } else {
                        // PR1 Grid (r111, pilihannya): semua baris jadi grid
                        // 2 kolom (label kiri, nilai kanan) dalam SATU kartu —
                        // info + izin; dialog jadi jauh lebih pendek.
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                            Column {
                                PropGridRow(
                                    t.propParent,
                                    ellipsizeMiddle(
                                        entry.file.parent?.let { if (it.endsWith("/")) it else "$it/" } ?: "/"
                                    )
                                )
                                PropGroupDivider()
                                PropGridRow(t.propType, if (entry.isDir) t.propFolder else t.propFile)
                                if (imgType != null) {
                                    PropGroupDivider()
                                    PropGridRow(t.imgTypeLabel, imgTypeName(imgType!!, t))
                                    if (imgType == ImgType.SPARSE || imgType == ImgType.EXT4) {
                                        if (!ext4Loaded) {
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4Volume, "\u2026")
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4BlockSize, "\u2026")
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4Label, "\u2026")
                                        } else if (ext4Info != null) {
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4Volume, FileOps.formatSizeExact(ext4Info!!.totalBytes))
                                            PropGroupDivider()
                                            PropGridRow(t.imgExt4BlockSize, FileOps.formatSizeExact(ext4Info!!.blockSize))
                                            if (ext4Info!!.label.isNotEmpty()) {
                                                PropGroupDivider()
                                                PropGridRow(t.imgExt4Label, ext4Info!!.label)
                                            }
                                        }
                                    }
                                }
                                PropGroupDivider()
                                PropGridRow(
                                    t.propSize,
                                    if (entry.isDir) {
                                        stats?.let { FileOps.formatSizeExact(it.totalSize) } ?: "\u2026"
                                    } else {
                                        FileOps.formatSizeExact(entry.size)
                                    }
                                )
                                if (entry.isDir) {
                                    PropGroupDivider()
                                    PropGridRow(t.propFileCount, stats?.fileCount?.toString() ?: "\u2026")
                                    PropGroupDivider()
                                    PropGridRow(t.propFolderCount, stats?.dirCount?.toString() ?: "\u2026")
                                    if (stats?.largestName != null) {
                                        PropGroupDivider()
                                        PropGridRow(
                                            t.propLargest,
                                            stats!!.largestName + " \u00b7 " + FileOps.formatSize(stats!!.largestSize)
                                        )
                                    }
                                }
                                PropGroupDivider()
                                PropGridRow(t.propModified, FileOps.formatIsoTime(entry.modified))
                                if (stat != null) {
                                    PropGroupDivider()
                                    Row(
                                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                Icons.Default.Lock, null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(6.dp).size(15.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            t.propPermsGroup.uppercase(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    PropGridRow(t.propPerms, FileOps.formatPerms(stat!!.mode), monospace = true) {
                                        IconButton(onClick = { showPerms = true }) {
                                            Icon(Icons.Default.Edit, t.edit, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    PropGroupDivider()
                                    PropGridRow(t.propOwner, FileOps.ownerName(stat!!.uid))
                                    PropGroupDivider()
                                    PropGridRow(t.propGroup, FileOps.groupName(stat!!.gid))
                                }
                            }
                        }
                        if (expanded) {
                            // Isi tombol LAGI: jalur lengkap (dengan salin) +
                            // rincian stat lanjutan bila tersedia.
                            Spacer(Modifier.height(12.dp))
                            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                                Column {
                                    PropGridRow(t.propPath, ellipsizeMiddle(entry.path)) {
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString(entry.path))
                                            onNotify(t.copiedDone)
                                        }) { Icon(Icons.Default.ContentCopy, t.copy, modifier = Modifier.size(18.dp)) }
                                    }
                                    if (stat != null) {
                                        PropGroupDivider()
                                        PropGridRow(t.propAccessed, FileOps.formatIsoTime(stat!!.atimeSec * 1000))
                                        PropGroupDivider()
                                        PropGridRow(t.propChanged, FileOps.formatIsoTime(stat!!.ctimeSec * 1000))
                                        PropGroupDivider()
                                        PropGridRow(t.propInode, stat!!.ino.toString())
                                        PropGroupDivider()
                                        PropGridRow(t.propLinks, stat!!.nlink.toString())
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (showSums) {
                        PropPillButton(t.back.uppercase()) { showSums = false }
                    } else {
                        PropPillButton(t.propMore.uppercase()) { expanded = !expanded }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!entry.isDir && !showSums) {
                        PropPillButton(t.checksumBtn.uppercase()) { showSums = true }
                    }
                    Spacer(Modifier.width(8.dp))
                    PropPillButton(t.close.uppercase()) { onDismiss() }
                }
            }
        )
    }
    if (showPerms && stat != null) {
        PermissionsDialog(
            initialBits = stat!!.mode and 0xFFF,
            onDismiss = { showPerms = false },
            onApply = { bitsBaru ->
                showPerms = false
                scope.launch {
                    val sukses = withContext(Dispatchers.IO) { FileOps.chmod(entry.path, bitsBaru) }
                    if (sukses) {
                        stat = withContext(Dispatchers.IO) { FileOps.statInfo(entry.path) }
                        onNotify(t.permApplied)
                    } else {
                        onNotify(t.permFailed)
                    }
                }
            }
        )
    }
}

/** Dialog Detail EXIF foto (DF1, pilihan Achmad 2026-11-20): baris-baris
 *  dari ExifOps.readExif; bila foto tidak punya data EXIF, tampilkan
 *  pesan t.exifNoData. Kepala mengikuti gaya Properti (r23). */
@Composable
fun ExifDialog(entry: FEntry, onDismiss: () -> Unit) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    var info by remember(entry.path) { mutableStateOf<ExifOps.ExifInfo?>(null) }
    var done by remember(entry.path) { mutableStateOf(false) }
    LaunchedEffect(entry.path) {
        info = withContext(Dispatchers.IO) { ExifOps.readExif(entry.path) }
        done = true
    }
    if (isLiquid) {
        IosAlertDialog(
            title = null,
            text = null,
            buttons = listOf(IosAlertButton(t.close) { onDismiss() }),
            content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!done) {
                    Text("\u2026", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val rows = listOfNotNull(
                        info?.camera?.let { t.exifCamera to it },
                        info?.dateTaken?.let { t.exifDateTaken to it },
                        info?.dimensions?.let { t.exifDimensions to it },
                        info?.size?.let { t.propSize to it },
                        info?.location?.let { t.exifLocation to it }
                    )
                    if (rows.isEmpty()) {
                        Text(t.exifNoData, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                            Column {
                                rows.forEachIndexed { i, (label, value) ->
                                    if (i > 0) PropGroupDivider()
                                    PropGroupRow(label, value)
                                }
                            }
                        }
                    }
                }
            }
            },
            onDismissRequest = onDismiss,
            titleContent = {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(
                    MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(
                            MaterialTheme.colorScheme.primary
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            }
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
            onDismissRequest = onDismiss,
            title = {
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(
                        MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(
                                MaterialTheme.colorScheme.primary
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!done) {
                        Text("\u2026", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val rows = listOfNotNull(
                            info?.camera?.let { t.exifCamera to it },
                            info?.dateTaken?.let { t.exifDateTaken to it },
                            info?.dimensions?.let { t.exifDimensions to it },
                            info?.size?.let { t.propSize to it },
                            info?.location?.let { t.exifLocation to it }
                        )
                        if (rows.isEmpty()) {
                            Text(t.exifNoData, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest), border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null) {
                                Column {
                                    rows.forEachIndexed { i, (label, value) ->
                                        if (i > 0) PropGroupDivider()
                                        PropGroupRow(label, value)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(t.close) }
            }
        )
    }
}

/** Editor izin (chmod) ala pengelola berkas referensi: kotak centang
 *  Baca/Tulis/Eksekutif untuk Pemilik/Kelompok/Lainnya, izin khusus
 *  (setuid/setgid/lengket), dan pratinjau simbolik + oktal langsung. */
@Composable
private fun PermissionsDialog(
    initialBits: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    var bits by remember { mutableStateOf(initialBits and 0xFFF) }
    fun ubah(bit: Int, on: Boolean) {
        bits = if (on) bits or bit else bits and bit.inv()
    }
    if (isLiquid) {
        IosAlertDialog(
            title = t.propPerms,
            text = null,
            buttons = listOf(
                IosAlertButton(t.okLabel.uppercase()) { onApply(bits) },
                IosAlertButton(t.cancel.uppercase()) { onDismiss() }
            ),
            content = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1.1f))
                    Text(t.permRead, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    Text(t.permWrite, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    Text(t.permExec, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
                BarisIzin(t.propOwner, 256, 128, 64, bits, ::ubah)
                BarisIzin(t.propGroup, 32, 16, 8, bits, ::ubah)
                BarisIzin(t.permOthers, 4, 2, 1, bits, ::ubah)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        "  " + t.permSpecial + "  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    KolomKhusus(t.permSetUid, 2048, bits, ::ubah)
                    KolomKhusus(t.permSetGid, 1024, bits, ::ubah)
                    KolomKhusus(t.permSticky, 512, bits, ::ubah)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    FileOps.permPreview(bits),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
            },
            onDismissRequest = onDismiss
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
            onDismissRequest = onDismiss,
            title = { Text(t.propPerms) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1.1f))
                        Text(t.permRead, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        Text(t.permWrite, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        Text(t.permExec, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                    BarisIzin(t.propOwner, 256, 128, 64, bits, ::ubah)
                    BarisIzin(t.propGroup, 32, 16, 8, bits, ::ubah)
                    BarisIzin(t.permOthers, 4, 2, 1, bits, ::ubah)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(
                            "  " + t.permSpecial + "  ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        KolomKhusus(t.permSetUid, 2048, bits, ::ubah)
                        KolomKhusus(t.permSetGid, 1024, bits, ::ubah)
                        KolomKhusus(t.permSticky, 512, bits, ::ubah)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        FileOps.permPreview(bits),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onApply(bits) }) { Text(t.okLabel.uppercase()) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(t.cancel.uppercase()) }
            }
        )
    }
}

/** Satu baris kotak centang izin: label + Baca/Tulis/Eksekutif. */
@Composable
private fun BarisIzin(
    label: String,
    bitR: Int,
    bitW: Int,
    bitX: Int,
    bits: Int,
    onToggle: (Int, Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.1f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (LocalIsLiquid.current) IosCheckCircle(checked = bits and bitR != 0, onClick = { onToggle(bitR, bits and bitR == 0) }) else Checkbox(checked = bits and bitR != 0, onCheckedChange = { onToggle(bitR, it) })
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (LocalIsLiquid.current) IosCheckCircle(checked = bits and bitW != 0, onClick = { onToggle(bitW, bits and bitW == 0) }) else Checkbox(checked = bits and bitW != 0, onCheckedChange = { onToggle(bitW, it) })
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (LocalIsLiquid.current) IosCheckCircle(checked = bits and bitX != 0, onClick = { onToggle(bitX, bits and bitX == 0) }) else Checkbox(checked = bits and bitX != 0, onCheckedChange = { onToggle(bitX, it) })
        }
    }
}

/** Satu kolom izin khusus di dalam Row: kotak centang + label di bawahnya. */
@Composable
private fun RowScope.KolomKhusus(
    label: String,
    bit: Int,
    bits: Int,
    onToggle: (Int, Boolean) -> Unit
) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (LocalIsLiquid.current) IosCheckCircle(checked = bits and bit != 0, onClick = { onToggle(bit, bits and bit == 0) }) else Checkbox(checked = bits and bit != 0, onCheckedChange = { onToggle(bit, it) })
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** Satu blok label-nilai di dalam kartu grup Properti (P15): label
 *  kapital kecil cyan di atas, nilai selebar kartu di bawahnya agar
 *  nilai panjang (ukuran persis, izin) tidak terpotong. Nilai
 *  monospace tampil di dalam chip timbul. */
@Composable
private fun PropGroupRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (monospace) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null
                ) {
                    Text(
                        value,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

/** Baris grid 2 kolom untuk dialog Properti (PR1 r111): label di
 *  kiri, nilai di kanan dalam satu baris — dialog jadi jauh lebih
 *  pendek daripada label-di-atas-nilai. Dipakai dialog Properti
 *  saja; ExifDialog tetap memakai PropGroupRow. */
@Composable
private fun PropGridRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        if (monospace) {
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (LocalIsLiquid.current) BorderStroke(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd))) else null
            ) {
                Text(
                    value,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Text(
                value,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium
            )
        }
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

/** Tombol aksi pil tonal untuk dialog Properti (P15). */
@Composable
private fun PropPillButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        onClick = onClick
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
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
    val isLiquid = LocalIsLiquid.current
    var key by remember { mutableStateOf(sortKey) }
    var asc by remember { mutableStateOf(ascending) }
    if (isLiquid) {
        IosAlertDialog(
            title = t.sortBy,
            text = null,
            buttons = listOf(
                IosAlertButton(t.cancel) { onDismiss() },
                IosAlertButton(t.save) { onPick(key, asc) }
            ),
            content = {
            Column {
                listOf(SortKey.NAME to t.sortName, SortKey.DATE to t.sortDate, SortKey.SIZE to t.sortSize).forEach { (k, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { key = k }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (LocalIsLiquid.current) IosRadioCircle(selected = key == k, onClick = { key = k }) else RadioButton(selected = key == k, onClick = { key = k })
                        Text(label)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (LocalIsLiquid.current) IosRadioCircle(selected = asc, onClick = { asc = true }) else RadioButton(selected = asc, onClick = { asc = true })
                    Text(t.sortAscending)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { asc = false }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (LocalIsLiquid.current) IosRadioCircle(selected = !asc, onClick = { asc = false }) else RadioButton(selected = !asc, onClick = { asc = false })
                    Text(t.sortDescending)
                }
            }
            },
            onDismissRequest = onDismiss,
            twoColumn = true
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    var name by remember { mutableStateOf("archive") }
    var kind by remember { mutableStateOf("zip") }
    var password by remember { mutableStateOf("") }
    val kinds = listOf("zip" to "ZIP", "tar" to "TAR")
    if (isLiquid) {
        IosAlertDialog(
            title = t.compress,
            text = null,
            buttons = listOf(
                IosAlertButton(t.compress, enabled = name.isNotBlank()) { onConfirm(name.trim(), kind, password.ifBlank { null }) },
                IosAlertButton(t.cancel) { onDismiss() }
            ),
            content = {
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
            onDismissRequest = onDismiss
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePasswordDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = txt()
    val isLiquid = LocalIsLiquid.current
    var password by remember { mutableStateOf("") }
    if (isLiquid) {
        IosAlertDialog(
            title = title,
            text = body,
            buttons = listOf(
                IosAlertButton(t.extract, enabled = password.isNotEmpty()) { onConfirm(password) },
                IosAlertButton(t.cancel) { onDismiss() }
            ),
            content = {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(title) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onDismissRequest = onDismiss
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(title) }, singleLine = true,
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
}

@Composable
private fun ApkActionRow(icon: ImageVector, color: Color, label: String, desc: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(desc) },
        leadingContent = {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


/**
 * Pilihan saat berkas ZIP diketuk (meniru referensi yang dipilih Achmad):
 * Ekstrak Di Sini (ke folder bernama seperti arsip), Buka Arsip (lihat isi
 * dulu), atau Ekstrak ke Jalur Kustom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val isLiquid = LocalIsLiquid.current
    if (isLiquid) {
        IosAlertDialog(
            title = null,
            text = null,
            buttons = listOf(IosAlertButton(t.cancel) { onDismiss() }),
            content = {
            // EA1: dropdown dihapus — ketiga aksi jadi baris penuh.
            Column {
                ApkActionRow(
                    Icons.Default.Unarchive, MaterialTheme.colorScheme.primary,
                    t.extract, t.extractHereDesc, onExtractHere
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ApkActionRow(
                    Icons.Default.FolderOpen, MaterialTheme.colorScheme.primary,
                    t.extractCustom, t.extractCustomDesc, onCustomPath
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ApkActionRow(
                    Icons.Default.Folder, MaterialTheme.colorScheme.primary,
                    t.openArchive, t.browseFirst, onOpenArchive
                )
            }
            },
            onDismissRequest = onDismiss,
            titleContent = {
            // EA1 Daftar aksi (r112, pilihannya): kepala rata kiri —
            // petak ikon + judul + nama berkas; tanpa ikon tengah.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Unarchive, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.extractArchive)
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            }
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
            onDismissRequest = onDismiss,
            title = {
                // EA1 Daftar aksi (r112, pilihannya): kepala rata kiri —
                // petak ikon + judul + nama berkas; tanpa ikon tengah.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Unarchive, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.extractArchive)
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            text = {
                // EA1: dropdown dihapus — ketiga aksi jadi baris penuh.
                Column {
                    ApkActionRow(
                        Icons.Default.Unarchive, MaterialTheme.colorScheme.primary,
                        t.extract, t.extractHereDesc, onExtractHere
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ApkActionRow(
                        Icons.Default.FolderOpen, MaterialTheme.colorScheme.primary,
                        t.extractCustom, t.extractCustomDesc, onCustomPath
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ApkActionRow(
                        Icons.Default.Folder, MaterialTheme.colorScheme.primary,
                        t.openArchive, t.browseFirst, onOpenArchive
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
        )
    }
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
    val isLiquid = LocalIsLiquid.current
    var current by remember { mutableStateOf(startPath) }
    var dirs by remember { mutableStateOf<List<FEntry>>(emptyList()) }
    LaunchedEffect(current) {
        dirs = withContext(Dispatchers.IO) {
            FileOps.listDir(File(current), true, SortKey.NAME, true)?.filter { it.isDir } ?: emptyList()
        }
    }
    val parent = remember(current) { File(current).parent }
    if (isLiquid) {
        IosAlertDialog(
            title = title,
            text = null,
            buttons = listOf(
                IosAlertButton(t.cancel) { onCancel() },
                IosAlertButton(t.selectFolder) { onPick(current) }
            ),
            content = {
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
                            Icon(Icons.Default.Folder, null, tint = Color(0xFF8FC1F5))
                            Spacer(Modifier.width(12.dp))
                            Text(dir.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            },
            onDismissRequest = onCancel,
            twoColumn = true
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
                                Icon(Icons.Default.Folder, null, tint = Color(0xFF8FC1F5))
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
    val isLiquid = LocalIsLiquid.current
    val context = LocalContext.current
    var info by remember(entry.path) { mutableStateOf<ApkInfo?>(null) }
    var iconBmp by remember(entry.path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var permsExpanded by remember(entry.path) { mutableStateOf(false) }
    LaunchedEffect(entry.path) {
        val loaded = withContext(Dispatchers.IO) {
            FileOps.apkInfo(context, entry.file) to FileOps.apkIcon(context, entry.file)?.asImageBitmap()
        }
        info = loaded.first
        iconBmp = loaded.second
    }
    if (isLiquid) {
        IosAlertDialog(
            title = null,
            text = null,
            buttons = listOf(IosAlertButton(t.cancel) { onDismiss() }),
            content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ApkActionRow(Icons.Default.Search, Color(0xFFFFB020), t.apkInspect, t.apkInspectDesc, onInspect)
                ApkActionRow(Icons.Default.Archive, Color(0xFFA78BFA), t.apkExtract, t.apkExtractDesc, onExtract)
                ApkActionRow(Icons.Default.InstallMobile, Color(0xFF7ED957), t.apkInstall, t.apkInstallDesc, onInstall)
                val perms = info?.permissions.orEmpty()
                if (perms.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .then(if (LocalIsLiquid.current) Modifier.border(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd)), RoundedCornerShape(18.dp)) else Modifier)
                            .clickable { permsExpanded = !permsExpanded }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t.apkPermissions(perms.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (permsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (permsExpanded) {
                        LazyColumn(Modifier.heightIn(max = 180.dp).fillMaxWidth()) {
                            items(perms) { perm ->
                                Text(
                                    perm.substringAfterLast('.'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 3.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            },
            onDismissRequest = onDismiss,
            titleContent = {
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
            }
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
                    ApkActionRow(Icons.Default.Search, Color(0xFFFFB020), t.apkInspect, t.apkInspectDesc, onInspect)
                    ApkActionRow(Icons.Default.Archive, Color(0xFFA78BFA), t.apkExtract, t.apkExtractDesc, onExtract)
                    ApkActionRow(Icons.Default.InstallMobile, Color(0xFF7ED957), t.apkInstall, t.apkInstallDesc, onInstall)
                    val perms = info?.permissions.orEmpty()
                    if (perms.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (LocalIsLiquid.current) LocalLiquid.current.glassTint else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(if (LocalIsLiquid.current) Modifier.border(1.dp, Brush.linearGradient(listOf(LocalLiquid.current.rimStart, LocalLiquid.current.rimEnd)), RoundedCornerShape(18.dp)) else Modifier)
                                .clickable { permsExpanded = !permsExpanded }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                t.apkPermissions(perms.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (permsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (permsExpanded) {
                            LazyColumn(Modifier.heightIn(max = 180.dp).fillMaxWidth()) {
                                items(perms) { perm ->
                                    Text(
                                        perm.substringAfterLast('.'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 3.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
        )
    }
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
    val isLiquid = LocalIsLiquid.current
    if (isLiquid) {
        IosAlertDialog(
            title = entry.name,
            text = null,
            buttons = listOf(IosAlertButton(t.close) { onDismiss() }),
            content = {
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
            onDismissRequest = onDismiss
        )
    } else {
        AlertDialog(
            containerColor = liquidDialogContainerColor(),
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
}
