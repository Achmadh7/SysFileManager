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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    val t = txt()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t.settings) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(icon = Icons.Default.Palette, title = t.appearance) {
                    Text(t.theme, style = MaterialTheme.typography.titleSmall)
                    val themeOptions = listOf("light" to t.themeLight, "dark" to t.themeDark, "system" to t.themeSystem)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                    Text(t.language, style = MaterialTheme.typography.titleSmall)
                    val langOptions = listOf("id" to "Indonesia", "en" to "English")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        langOptions.forEachIndexed { index, (code, label) ->
                            SegmentedButton(
                                selected = language == code,
                                onClick = { onLanguage(code) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = langOptions.size),
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.dynamicColor)
                            Text(
                                t.dynamicColorDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColor)
                    }
                }
            }
            item {
                SectionCard(icon = Icons.Default.Folder, title = t.fileSection) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t.showHidden, Modifier.weight(1f))
                        Switch(checked = showHidden, onCheckedChange = onHidden)
                    }
                }
            }
            item {
                SectionCard(icon = Icons.Default.Info, title = t.otherSection) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFC62828)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.rootAccessTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                t.rootAccessDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showRootEntry, onCheckedChange = onRootEntry)
                    }
                    ListItem(
                        headlineContent = { Text(t.formatsTitle) },
                        leadingContent = { Icon(Icons.Default.Archive, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onFormats() }
                    )
                    ListItem(
                        headlineContent = { Text(t.about) },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onAbout() }
                    )
                }
            }
        }
    }
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
fun FormatRow(name: String, desc: String, chips: List<Triple<String, Color, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (label, bg, fg) -> FormatChip(label, bg, fg) }
            }
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                SectionCard(icon = Icons.Default.Archive, title = t.formatGroupStandard) {
                    FormatRow("ZIP (.zip)", t.fmtZipDesc, listOf(chipCreate, chipExtract, chipEncrypt))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FormatRow("7Z (.7z)", t.fmt7zDesc, listOf(chipExtract, chipRatio))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FormatRow("TAR (.tar)", t.fmtTarDesc, listOf(chipCreate, chipExtract))
                }
            }
            item {
                SectionCard(icon = Icons.Default.Inventory2, title = t.formatGroupTarball) {
                    FormatRow("TAR.GZ (.tar.gz, .tgz)", t.fmtTarGzDesc, listOf(chipCreate, chipExtract))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FormatRow("TAR.BZ2 (.tar.bz2, .tbz2)", t.fmtTarBz2Desc, listOf(chipCreate, chipExtract))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    FormatRow("TAR.XZ (.tar.xz, .txz)", t.fmtTarXzDesc, listOf(chipCreate, chipExtract))
                }
            }
        }
    }
}
