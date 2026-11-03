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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Layar Pengaturan dan Format yang Didukung.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    language: String,
    showHidden: Boolean,
    dynamicColor: Boolean,
    showRootEntry: Boolean,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRootEntry: (Boolean) -> Unit,
    onFormats: () -> Unit,
    onApps: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val t = txt()
    var themeDialog by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    val themeOptions = listOf("light" to t.themeLight, "dark" to t.themeDark, "system" to t.themeSystem)
    val langOptions = listOf("id" to "Indonesia", "en" to "English")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.settings) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { SettingsSectionLabel(t.appearance) }
            item {
                SettingsRow(
                    icon = when (themeMode) {
                        "light" -> Icons.Default.LightMode
                        "dark" -> Icons.Default.DarkMode
                        else -> Icons.Default.BrightnessAuto
                    },
                    circleColor = Color(0xFFFFB020),
                    title = t.theme,
                    subtitle = themeOptions.firstOrNull { it.first == themeMode }?.second,
                    onClick = { themeDialog = true }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Translate,
                    circleColor = Color(0xFF4DA3FF),
                    title = t.language,
                    subtitle = langOptions.firstOrNull { it.first == language }?.second,
                    onClick = { langDialog = true }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    circleColor = Color(0xFF2DD4BF),
                    title = t.dynamicColor,
                    subtitle = t.dynamicColorDesc,
                    trailing = { Switch(checked = dynamicColor, onCheckedChange = onDynamicColor) }
                )
            }
            item { SettingsSectionLabel(t.fileSection, topPadding = 22.dp) }
            item {
                SettingsRow(
                    icon = Icons.Default.Folder,
                    circleColor = Color(0xFFF06292),
                    title = t.showHidden,
                    trailing = { Switch(checked = showHidden, onCheckedChange = onHidden) }
                )
            }
            item { SettingsSectionLabel(t.otherSection, topPadding = 22.dp) }
            item {
                SettingsRow(
                    icon = Icons.Default.Security,
                    circleColor = Color(0xFFC62828),
                    title = t.rootAccessTitle,
                    subtitle = t.rootAccessDesc,
                    trailing = { Switch(checked = showRootEntry, onCheckedChange = onRootEntry) }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Archive,
                    circleColor = Color(0xFF8B7CFF),
                    title = t.formatsTitle,
                    onClick = onFormats
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Apps,
                    circleColor = Color(0xFF7ED957),
                    title = t.appsTitle,
                    onClick = onApps
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    circleColor = Color(0xFFB39DDB),
                    title = t.about,
                    onClick = onAbout
                )
            }
        }
        if (themeDialog) {
            SettingsChoiceDialog(
                title = t.theme,
                options = themeOptions,
                selected = themeMode,
                onPick = { onTheme(it); themeDialog = false },
                onDismiss = { themeDialog = false }
            )
        }
        if (langDialog) {
            SettingsChoiceDialog(
                title = t.language,
                options = langOptions,
                selected = language,
                onPick = { onLanguage(it); langDialog = false },
                onDismiss = { langDialog = false }
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String, topPadding: Dp = 6.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    circleColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(circleColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = circleColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val t = txt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == value, onClick = { onPick(value) })
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

@Composable
fun FormatChip(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun FormatCard(name: String, desc: String, chips: List<Triple<String, Color, Color>>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { (label, bg, fg) -> FormatChip(label, bg, fg) }
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatGroupHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatsScreen(onBack: () -> Unit) {
    val t = txt()
    val chipCreate = Triple(t.chipCreate, Color(0xFF2E5A88), Color.White)
    val chipExtract = Triple(
        t.chipExtract,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurface
    )
    val chipEncrypt = Triple(t.chipEncrypt, Color(0xFFE3C8F5), Color(0xFF3B2B4D))
    val chipRatio = Triple(
        t.chipHighRatio,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurface
    )
    val chipDecompress = Triple(
        t.chipDecompress,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurface
    )
    val chipInspect = Triple(
        t.chipInspect,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurface
    )
    val chipNative = Triple(t.chipNative, Color(0xFFB71C1C), Color.White)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.formatsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatGroupHeader(Icons.Default.Archive, t.formatGroupStandard)
                    FormatCard("ZIP (.zip)", t.fmtZipDesc, listOf(chipCreate, chipExtract, chipEncrypt))
                    FormatCard("7Z (.7z)", t.fmt7zDesc, listOf(chipExtract, chipRatio))
                    FormatCard("TAR (.tar)", t.fmtTarDesc, listOf(chipCreate, chipExtract))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatGroupHeader(Icons.Default.Inventory2, t.formatGroupTarball)
                    FormatCard("TAR.GZ (.tar.gz, .tgz)", t.fmtTarGzDesc, listOf(chipCreate, chipExtract))
                    FormatCard("TAR.BZ2 (.tar.bz2, .tbz2)", t.fmtTarBz2Desc, listOf(chipCreate, chipExtract))
                    FormatCard("TAR.XZ (.tar.xz, .txz)", t.fmtTarXzDesc, listOf(chipCreate, chipExtract))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatGroupHeader(Icons.Default.Compress, t.formatGroupSingle)
                    FormatCard("GZIP (.gz)", t.fmtGzipDesc, listOf(chipDecompress))
                    FormatCard("BZIP2 (.bz2)", t.fmtBzip2Desc, listOf(chipDecompress))
                    FormatCard("XZ (.xz)", t.fmtXzDesc, listOf(chipDecompress))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatGroupHeader(Icons.Default.Android, t.formatGroupSpecial)
                    FormatCard("RAR (.rar)", t.fmtRarDesc, listOf(chipExtract))
                    FormatCard("APK / APKS / XAPK", t.fmtApkDesc, listOf(chipExtract, chipInspect))
                    FormatCard("PAYLOAD.BIN", t.fmtPayloadDesc, listOf(chipExtract, chipNative))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar Tentang (halaman penuh, menggantikan dialog Tentang lama)
// ---------------------------------------------------------------------------

private const val MIT_LICENSE_TEXT = """Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onFormats: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSpecs: () -> Unit
) {
    val t = txt()
    val context = LocalContext.current
    val internal = remember { FileOps.storageRoots(context).firstOrNull { it.kind == "internal" } }
    var showLicense by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.about, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Ubin ikon aplikasi (sama seperti bilah atas Beranda, lebih besar).
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF2E5A88)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder, null,
                            tint = Color.White, modifier = Modifier.size(50.dp)
                        )
                        Icon(
                            Icons.Default.ArrowUpward, null,
                            tint = Color(0xFF2E5A88),
                            modifier = Modifier.size(26.dp).offset(y = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "SysFile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF2E5A88)) {
                        Text(
                            t.version("1.0.0"),
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        t.appTagline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                AboutCard(
                    icon = Icons.Default.Storage,
                    color = Color(0xFF4DA3FF),
                    title = t.storage,
                    subtitle = internal?.let {
                        t.storageUsedLine(FileOps.formatSize(it.total - it.free), FileOps.formatSize(it.total))
                    } ?: "",
                    onClick = onOpenStorage
                )
            }
            item {
                AboutCard(
                    icon = Icons.Default.Archive,
                    color = Color(0xFFFFB020),
                    title = t.supportedFormats,
                    subtitle = t.formatsSummary,
                    onClick = onFormats
                )
            }
            item {
                AboutCard(
                    icon = Icons.Default.Info,
                    color = Color(0xFFB39DDB),
                    title = t.appSpecs,
                    subtitle = "Kotlin • Jetpack Compose • Material 3",
                    onClick = onOpenSpecs
                )
            }
            item {
                AboutCard(
                    icon = Icons.Default.Gavel,
                    color = Color(0xFF2DD4BF),
                    title = t.licenseTitle,
                    subtitle = "Copyright © 2026 Reffan Lintang",
                    onClick = { showLicense = true }
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF7ED957).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                tint = Color(0xFF7ED957), modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.developer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Reffan Lintang",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "© 2026 Reffan Lintang",
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
    if (showLicense) {
        Dialog(onDismissRequest = { showLicense = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF3D4B63)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Gavel, null,
                                tint = Color.White, modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            t.licenseTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Copyright © 2026 Reffan Lintang",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .height(380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            MIT_LICENSE_TEXT,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showLicense = false }) { Text(t.close) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecScreen(onBack: () -> Unit) {
    val t = txt()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.appSpecs, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                SpecGroup(
                    icon = Icons.Default.GridView,
                    color = Color(0xFF4DA3FF),
                    title = t.specGroupIdentity,
                    rows = listOf(
                        t.specAppName to "SysFile",
                        t.packageLabel to "id.sysfile.manager",
                        t.versionLabel to "1.0.0",
                        t.specBuildEnv to "AGP 8.11.1"
                    )
                )
            }
            item {
                SpecGroup(
                    icon = Icons.Default.Archive,
                    color = Color(0xFFFFB020),
                    title = t.specGroupArchive,
                    rows = listOf(
                        t.specCompression to "Commons Compress 1.27.1",
                        "ZIP AES" to "Zip4j 2.11.5",
                        "RAR" to "Junrar 7.5.5",
                        "Payload.bin" to t.payloadParserValue
                    )
                )
            }
            item {
                SpecGroup(
                    icon = Icons.Default.Storage,
                    color = Color(0xFFB39DDB),
                    title = t.specGroupUiStorage,
                    rows = listOf(
                        "Toolkit UI" to "Jetpack Compose",
                        t.specDesignLabel to "Material 3 Expressive",
                        t.minAndroidLabel to "8.0 (API 26)",
                        t.targetAndroidLabel to "14 (API 34)"
                    )
                )
            }
        }
    }
}

@Composable
private fun SpecGroup(
    icon: ImageVector,
    color: Color,
    title: String,
    rows: List<Pair<String, String>>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 5.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = if (i == 0) 12.dp else 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}
