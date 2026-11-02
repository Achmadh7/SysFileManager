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
                    glyphTint = Color(0xFF1A1A1A),
                    title = t.theme,
                    subtitle = themeOptions.firstOrNull { it.first == themeMode }?.second,
                    onClick = { themeDialog = true }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Translate,
                    circleColor = Color(0xFF4DA3FF),
                    glyphTint = Color.White,
                    title = t.language,
                    subtitle = langOptions.firstOrNull { it.first == language }?.second,
                    onClick = { langDialog = true }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    circleColor = Color(0xFF2DD4BF),
                    glyphTint = Color(0xFF10201C),
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
                    glyphTint = Color.White,
                    title = t.showHidden,
                    trailing = { Switch(checked = showHidden, onCheckedChange = onHidden) }
                )
            }
            item { SettingsSectionLabel(t.otherSection, topPadding = 22.dp) }
            item {
                SettingsRow(
                    icon = Icons.Default.Security,
                    circleColor = Color(0xFFC62828),
                    glyphTint = Color.White,
                    title = t.rootAccessTitle,
                    subtitle = t.rootAccessDesc,
                    trailing = { Switch(checked = showRootEntry, onCheckedChange = onRootEntry) }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Archive,
                    circleColor = Color(0xFF8B7CFF),
                    glyphTint = Color.White,
                    title = t.formatsTitle,
                    onClick = onFormats
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    circleColor = Color(0xFFE3C8F5),
                    glyphTint = Color(0xFF3B2B4D),
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
    glyphTint: Color,
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
            Modifier.size(40.dp).clip(CircleShape).background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = glyphTint, modifier = Modifier.size(22.dp))
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
