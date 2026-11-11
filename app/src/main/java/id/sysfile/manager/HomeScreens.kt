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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Layar Beranda SysFile: kartu penyimpanan, kategori, favorit, dan bilah navigasi pil.

fun rootLabel(root: RootInfo, t: Txt): String {
    return when (root.kind) {
        "root" -> t.rootSystem
        "sd" -> t.sdCard
        "usb" -> t.usbStorage
        else -> t.internalStorage
    }
}

// ---------------------------------------------------------------------------
// Layar Beranda
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    accessGranted: Boolean,
    roots: List<RootInfo>,
    rootActive: Boolean,
    onRequestAccess: () -> Unit,
    onOpenRoot: (String) -> Unit,
    onOpenCategory: (Category) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val t = txt()
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(t.greetingHello, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.greetingLine, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (!accessGranted) {
            item {
                SectionCard(icon = Icons.Default.Warning, title = t.permissionTitle) {
                    Text(t.permissionBody, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                        Text(t.permissionButton)
                    }
                    Text(t.permissionRationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (roots.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = Color(0xFFAAC8F0), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.storage, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    roots.forEach { root ->
                        if (root.kind != "internal" && root.total > 0) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                                    .clickable { onOpenRoot(root.path) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    when (root.kind) {
                                        "root" -> Icons.Default.Terminal
                                        "sd" -> Icons.Default.SdStorage
                                        "usb" -> Icons.Default.Usb
                                        else -> Icons.Default.Storage
                                    },
                                    null, tint = Color(0xFFAAC8F0), modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(rootLabel(root, t), style = MaterialTheme.typography.titleSmall)
                                    LinearWavyProgressIndicator(
                                        progress = { ((root.total - root.free).toFloat() / root.total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    Text(
                                        if (root.kind == "root") {
                                            if (rootActive) t.rootActive else t.rootLimited
                                        } else {
                                            FileOps.formatSize(root.total - root.free) + " " + t.usedWord
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        FileOps.formatSize(root.free),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        t.freeWord,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else if (root.total > 0) {
                            val (statusText, statusDot) = storageStatus(root.free.toFloat() / root.total.toFloat())
                            GradientStorageCard(
                                label = rootLabel(root, t),
                                usedText = FileOps.formatSize(root.total - root.free),
                                detailText = t.ofTotal(FileOps.formatSize(root.total)),
                                percentText = String.format(appLocale(), "%.1f%%", (root.total - root.free) * 100.0 / root.total),
                                usedWord = t.usedWord,
                                statusText = statusText,
                                statusDot = statusDot,
                                fraction = (root.total - root.free).toFloat() / root.total.toFloat(),
                                icon = Icons.Default.Smartphone,
                                onClick = { onOpenRoot(root.path) }
                            )
                        } else {
                            SectionCard(icon = Icons.Default.Storage, title = rootLabel(root, t)) {
                                Text(root.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.categories, style = MaterialTheme.typography.titleMedium)
                }
                val cats = listOf(
                    Category.IMAGE to t.catImagesS,
                    Category.VIDEO to t.catVideosS,
                    Category.AUDIO to t.catAudioS,
                    Category.DOCUMENT to t.catDocumentsS,
                    Category.APP to t.catAppsS,
                    Category.ARCHIVE to t.catArchivesS,
                    Category.OTHER to t.catOtherS
                )
                cats.chunked(2).forEach { rowCats ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCats.forEach { (cat, label) ->
                            val color = categoryColor(cat)
                            Row(
                                Modifier.weight(1f).height(76.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(color.copy(alpha = 0.15f))
                                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable { onOpenCategory(cat) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(categoryIcon(cat, ""), null, tint = Color.White, modifier = Modifier.size(23.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowCats.size == 1) {
                            val trashColor = Color(0xFFEF5350)
                            Row(
                                Modifier.weight(1f).height(76.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(trashColor.copy(alpha = 0.15f))
                                    .border(1.dp, trashColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable { onOpenTrash() }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(trashColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(23.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    t.trash,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            repeat(2 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Layar peramban folder
// ---------------------------------------------------------------------------

/** Bilah tab bawah gaya kapsul melayang: item aktif menjadi pil terisi di dalam kapsul. */
@Composable
fun PillNavBar(homeTab: Int, onSelect: (Int) -> Unit) {
    val t = txt()
    val items = listOf(
        Triple(0, Icons.Default.Home, t.home),
        Triple(1, Icons.Default.History, t.recentShort),
        Triple(2, Icons.Default.Analytics, t.analysisShort)
    )
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (index, icon, label) ->
                val active = homeTab == index
                val fg = if (active) {
                    MaterialTheme.colorScheme.inverseOnSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.clip(RoundedCornerShape(percent = 50))
                        .background(if (active) MaterialTheme.colorScheme.inverseSurface else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }
    }
}
