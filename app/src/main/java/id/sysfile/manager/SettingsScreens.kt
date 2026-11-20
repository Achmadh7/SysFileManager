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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Layar Pengaturan dan Format yang Didukung.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    language: String,
    showHidden: Boolean,
    showRootEntry: Boolean,
    trashEnabled: Boolean,
    onTheme: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onHidden: (Boolean) -> Unit,
    onRootEntry: (Boolean) -> Unit,
    onTrashEnabled: (Boolean) -> Unit,
    onApps: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
    exiting: Boolean = false
) {
    val t = txt()
    val motion = MaterialTheme.motionScheme
    // Animasi perpindahan ke Pengaturan (S3, MotionScheme resmi): masuk geser dari kanan
    // + memudar; keluar (exiting) arah berlawanan. MainActivity menunda
    // pergantian layar 300ms supaya animasi keluar sempat selesai.
    val visState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visState.targetState = true }
    LaunchedEffect(exiting) { visState.targetState = !exiting }
    AnimatedVisibility(
        visibleState = visState,
        enter = slideInHorizontally(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
            fadeIn(animationSpec = motion.defaultEffectsSpec()),
        exit = slideOutHorizontally(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
            fadeOut(animationSpec = motion.defaultEffectsSpec())
    ) {
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
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // PS1 Baris ringkas (pilihan Achmad 2026-11-19): kartu per
            // baris dihapus — baris langsung di latar, dipisah garis
            // tipis di dalam seksi; judul grup (Tampilan / Berkas /
            // Aplikasi) tetap sebagai pemisah. Baris Warna Dinamis
            // dihapus sekalian: fitur dynamic color dicabut seluruhnya.
            // r166 DS1 Rapat Modern (pilihannya): baris lebih rapat —
            // ubin 40->32dp, sudut kartu 28->16dp, padding baris 10->6dp,
            // celah seksi 8->4dp + penutup versi di bawah.
            item { SettingsSectionLabel(t.tampilanSection) }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        SettingsRow(
                            icon = when (themeMode) {
                                "light" -> Icons.Default.LightMode
                                "dark" -> Icons.Default.DarkMode
                                else -> Icons.Default.BrightnessAuto
                            },
                            circleColor = Color(0xFFFFB020),
                            title = t.theme,
                            subtitle = when (themeMode) {
                                "light" -> t.themeLight
                                "dark" -> t.themeDark
                                else -> t.themeSystem
                            },
                            // Ketuk 1x langsung ganti Gelap <-> Terang; opsi
                            // "Ikuti Sistem" dihapus (nilai lama "system" ikut
                            // hilang begitu petak ini diketuk).
                            onClick = { onTheme(if (themeMode == "dark") "light" else "dark") }
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Translate,
                            circleColor = Color(0xFF4DA3FF),
                            title = t.language,
                            subtitle = if (language == "id") "Indonesia" else "English",
                            // Ketuk 1x langsung ganti Indonesia <-> English.
                            onClick = { onLanguage(if (language == "id") "en" else "id") }
                        )
                    }
                }
            }
item { SettingsSectionLabel(t.fileSection, topPadding = 4.dp) }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        SettingsRow(
                            icon = Icons.Default.Folder,
                            circleColor = Color(0xFFF06292),
                            title = t.showHidden,
                            trailing = { Switch(checked = showHidden, onCheckedChange = onHidden) }
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Security,
                            circleColor = Color(0xFFC62828),
                            title = t.rootAccessTitle,
                            subtitle = t.rootAccessDesc,
                            trailing = { Switch(checked = showRootEntry, onCheckedChange = onRootEntry) }
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Delete,
                            circleColor = Color(0xFF9AA8B5),
                            title = t.trash,
                            subtitle = if (trashEnabled) t.trashHintOn else t.trashHintOff,
                            trailing = { Switch(checked = trashEnabled, onCheckedChange = onTrashEnabled) }
                        )
                    }
                }
            }
item { SettingsSectionLabel(t.aplikasiSection, topPadding = 4.dp) }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        SettingsRow(
                            icon = Icons.Default.Apps,
                            circleColor = Color(0xFF7ED957),
                            title = t.appsTitle,
                            onClick = onApps
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Info,
                            circleColor = Color(0xFFB39DDB),
                            title = t.about,
                            onClick = onAbout
                        )
                    }
                }
            }
            // r166 DS1: penutup versi di bawah — pakai t.version +
            // BuildConfig.VERSION_NAME seperti AboutScreen (tanpa string baru).
            item {
                Text(
                    t.version(BuildConfig.VERSION_NAME),
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    }
}

@Composable
private fun SettingsSectionLabel(text: String, topPadding: Dp = 4.dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 2.dp)
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .background(circleColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = circleColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
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

// ---------------------------------------------------------------------------
// Format yang Didukung (dikembalikan 2026-11-18 atas permintaan Achmad;
// layar ini dihapus 2026-11-11, kini dibangun ulang dengan daftar SAAT INI:
// tarball (.tar.gz/.tgz/.tar.bz2/.tar.xz) + aliran tunggal (.gz/.bz2/.xz)
// TIDAK dikembalikan — dihapus r1).
// ---------------------------------------------------------------------------

@Composable
private fun FormatRow(name: String, caps: List<String>, desc: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // FM1 (r87): judul + chip sebaris; chip membungkus ke baris
        // berikut bila judul panjang (mis. APK / APKS / XAPK / AAB).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            caps.forEach { cap ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        cap,
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FormatsScreen(onBack: () -> Unit) {
    val t = txt()
    val visState = remember { MutableTransitionState(false) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(Unit) { visState.targetState = true }
    AnimatedVisibility(
        visibleState = visState,
        enter = slideInHorizontally(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
            fadeIn(animationSpec = motion.defaultEffectsSpec())
    ) {
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
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        FormatGroupHeader(Icons.Default.Archive, t.formatGroupStandard)
                        Spacer(Modifier.height(8.dp))
                        FormatRow("ZIP (.zip, .obb, .jar)", listOf(t.chipCreate, t.chipExtract, t.chipEncrypt), t.fmtZipDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("7Z (.7z)", listOf(t.chipExtract, t.chipHighRatio), t.fmt7zDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("TAR (.tar)", listOf(t.chipCreate, t.chipExtract), t.fmtTarDesc)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        FormatGroupHeader(Icons.Default.Android, t.formatGroupSpecial)
                        Spacer(Modifier.height(8.dp))
                        FormatRow("RAR (.rar)", listOf(t.chipExtract, t.chipEncrypt), t.fmtRarDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("APK / APKS / XAPK / AAB", listOf(t.chipExtract, t.chipInspect), t.fmtApkDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("PAYLOAD.BIN", listOf(t.chipExtract, t.chipNative), t.fmtPayloadDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("DEX (.dex)", listOf(t.chipInspect), t.fmtDexDesc)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        FormatGroupHeader(Icons.Default.Image, t.formatGroupMedia)
                        Spacer(Modifier.height(8.dp))
                        FormatRow("Gambar", listOf(t.chipView), t.fmtImageDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("Video", listOf(t.chipView), t.fmtVideoDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("Audio", listOf(t.chipPlay), t.fmtAudioDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("Teks", listOf(t.chipView), t.fmtTextDesc)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        FormatGroupHeader(Icons.Default.Description, t.formatGroupDocs)
                        Spacer(Modifier.height(8.dp))
                        FormatRow("Dokumen (PDF)", listOf(t.chipView), t.fmtPdfDesc)
                        HorizontalDivider(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        FormatRow("Dokumen Office", listOf(t.chipExternal), t.fmtOfficeDesc)
                    }
                }
            }
        }
    }
    }
}

// ---------------------------------------------------------------------------
// Layar Tentang (halaman penuh, menggantikan dialog Tentang lama)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // r145: animasi masuk sama seperti FormatsScreen — geser
    // dari kanan 1/3 lebar + pudar (motionScheme).
    val visState = remember { MutableTransitionState(false) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(Unit) { visState.targetState = true }
    AnimatedVisibility(
        visibleState = visState,
        enter = slideInHorizontally(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
            fadeIn(animationSpec = motion.defaultEffectsSpec())
    ) {
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
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Ubin logo M11 "SF" terpotong diagonal (sama seperti
                    // bilah atas Beranda, lebih besar) — pita warna ubin
                    // di atas huruf (rotate 30° = diagonal "/").
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SF",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp
                        )
                        Box(
                            Modifier.size(width = 4.dp, height = 130.dp)
                                .rotate(30f)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "SysFile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF2E628C)) {
                        Text(
                            t.version(BuildConfig.VERSION_NAME),
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
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        SettingsRow(
                            icon = Icons.Default.Storage,
                            circleColor = Color(0xFF4DA3FF),
                            title = t.storage,
                            subtitle = internal?.let {
                                t.storageUsedLine(FileOps.formatSize(it.total - it.free), FileOps.formatSize(it.total))
                            } ?: "",
                            onClick = onOpenStorage
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Info,
                            circleColor = Color(0xFFB39DDB),
                            title = t.appSpecs,
                            subtitle = "Kotlin • Jetpack Compose • Material 3",
                            onClick = onOpenSpecs
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsRow(
                            icon = Icons.Default.Archive,
                            circleColor = Color(0xFF40C4FF),
                            title = t.formatsTitle,
                            subtitle = "ZIP · TAR · 7Z · RAR · APK · JAR · PDF · payload.bin",
                            onClick = onFormats
                        )
                    }
                }
            }
            item {
                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF7ED957).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person, null,
                            tint = Color(0xFF7ED957), modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            item {
                Text(
                    "© 2026 Reffan Lintang",
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpecScreen(onBack: () -> Unit) {
    val t = txt()
    // r145: animasi masuk sama seperti FormatsScreen — geser
    // dari kanan 1/3 lebar + pudar (motionScheme).
    val visState = remember { MutableTransitionState(false) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(Unit) { visState.targetState = true }
    AnimatedVisibility(
        visibleState = visState,
        enter = slideInHorizontally(animationSpec = motion.defaultSpatialSpec()) { it / 3 } +
            fadeIn(animationSpec = motion.defaultEffectsSpec())
    ) {
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
                        t.versionLabel to BuildConfig.VERSION_NAME,
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
                        t.specStorageEngine to "All Files Access, SAF & Java NIO",
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
}

@Composable
private fun SpecGroup(
    icon: ImageVector,
    color: Color,
    title: String,
    rows: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(Modifier.padding(20.dp)) {
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
