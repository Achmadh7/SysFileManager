package id.sysfile.manager

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Pengelola Aplikasi: daftar aplikasi terpasang beserta ukurannya, dengan aksi
// buka, salin APK, bagikan APK, dan info aplikasi bawaan Android.

data class AppEntry(
    val name: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val apkSize: Long,
    val totalSize: Long?,
    val isSystem: Boolean,
    val icon: Bitmap?
)

object AppOps {

    /** Akses Penggunaan aktif? Menentukan apakah ukuran total (data + cache) tersedia. */
    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** Semua aplikasi terpasang, terurut nama. Ukuran total hanya bila Akses Penggunaan aktif. */
    fun listApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val stats = if (hasUsageAccess(context)) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return packages.mapNotNull { pkg ->
            try {
                val info = pkg.applicationInfo ?: return@mapNotNull null
                val apk = File(info.sourceDir ?: return@mapNotNull null)
                val total = try {
                    stats?.queryStatsForUid(StorageManager.UUID_DEFAULT, info.uid)
                        ?.let { it.appBytes + it.dataBytes + it.cacheBytes }
                } catch (e: Exception) {
                    null
                }
                AppEntry(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "",
                    apkPath = apk.absolutePath,
                    apkSize = if (apk.exists()) apk.length() else 0L,
                    totalSize = total,
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    icon = try {
                        info.loadIcon(pm).toBitmap(108, 108)
                    } catch (e: Exception) {
                        null
                    }
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Salin berkas APK aplikasi ke folder tujuan dengan nama aplikasi yang aman. */
    fun copyApk(app: AppEntry, destDir: File): Boolean {
        return try {
            val clean = app.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                .ifEmpty { app.packageName }
            val dest = File(destDir, FileOps.uniqueName(destDir, "$clean.apk"))
            File(app.apkPath).copyTo(dest, overwrite = false)
            dest.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(notify: (String) -> Unit, onBack: () -> Unit) {
    val t = txt()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var filter by remember { mutableStateOf(1) }
    var sortBySize by remember { mutableStateOf(true) }
    var usageAccess by remember { mutableStateOf(AppOps.hasUsageAccess(context)) }
    var actionApp by remember { mutableStateOf<AppEntry?>(null) }
    var copyTarget by remember { mutableStateOf<AppEntry?>(null) }

    fun reload() {
        scope.launch {
            usageAccess = AppOps.hasUsageAccess(context)
            apps = withContext(Dispatchers.IO) { AppOps.listApps(context) }
        }
    }
    LaunchedEffect(Unit) { reload() }

    val visible = remember(apps, filter, sortBySize) {
        val base = (apps ?: emptyList()).filter {
            when (filter) {
                1 -> !it.isSystem
                2 -> it.isSystem
                else -> true
            }
        }
        if (sortBySize) base.sortedByDescending { it.totalSize ?: it.apkSize } else base
    }

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
                title = { Text(t.appsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                },
                actions = {
                    IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, t.refresh) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to t.all, 1 to t.appsUser, 2 to t.appsSystem).forEach { (idx, label) ->
                    FilterChip(
                        selected = filter == idx,
                        onClick = { filter = idx },
                        label = { Text(label) },
                        colors = obsidianFilterChipColors(),
                        border = obsidianFilterChipBorder(filter == idx)
                    )
                }
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = { sortBySize = !sortBySize },
                    label = { Text(if (sortBySize) t.sortSize else t.sortName) },
                    leadingIcon = { Icon(Icons.Default.Sort, null, Modifier.size(18.dp)) }
                )
            }
            if (!usageAccess) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SectionCard(icon = Icons.Default.Info, title = t.usageAccessTitle) {
                        Text(t.usageAccessBody, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (e: Exception) {
                                    notify(t.opFailed)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(t.usageAccessButton) }
                    }
                }
            }
            when {
                apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(52.dp))
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t.appsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    app.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        app.versionName.takeIf { it.isNotEmpty() }?.let { "v$it" },
                                        app.packageName
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    Icon(Icons.Default.Info, null, Modifier.size(48.dp))
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        FileOps.formatSize(app.totalSize ?: app.apkSize),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (app.totalSize != null && app.totalSize != app.apkSize) {
                                        Text(
                                            "APK " + FileOps.formatSize(app.apkSize),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable { actionApp = app }
                        )
                    }
                }
            }
        }
    }
    }

    actionApp?.let { app ->
        AlertDialog(
            onDismissRequest = { actionApp = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bmp = app.icon
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Android, null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "v" + app.versionName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column {
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.totalSize != null) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(
                                    t.totalWord + " " + FileOps.formatSize(app.totalSize),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(
                                "APK " + FileOps.formatSize(app.apkSize),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppActionTile(
                                icon = Icons.Default.PlayArrow,
                                label = t.appOpen,
                                desc = t.appOpenDesc,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    actionApp = null
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            notify(t.opFailed)
                                        }
                                    } else {
                                        notify(t.opFailed)
                                    }
                                }
                            )
                            AppActionTile(
                                icon = Icons.Default.ContentCopy,
                                label = t.appCopyApk,
                                desc = t.appCopyDesc,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    actionApp = null
                                    copyTarget = app
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppActionTile(
                                icon = Icons.Default.Share,
                                label = t.appShareApk,
                                desc = t.appShareDesc,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    actionApp = null
                                    scope.launch {
                                        val staged = withContext(Dispatchers.IO) {
                                            try {
                                                val dir = File(context.cacheDir, "apkshare").apply { FileOps.evictStaleCache(this); mkdirs() }
                                                val out = File(dir, app.packageName + ".apk")
                                                File(app.apkPath).copyTo(out, overwrite = true)
                                                out
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (staged == null) {
                                            notify(t.opFailed)
                                        } else {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context, context.packageName + ".fileprovider", staged
                                                )
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.android.package-archive"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, t.share))
                                                notify(t.appSharedDone)
                                            } catch (e: Exception) {
                                                notify(t.opFailed)
                                            }
                                        }
                                    }
                                }
                            )
                            AppActionTile(
                                icon = Icons.Default.Info,
                                label = t.appInfo,
                                desc = t.appInfoDesc,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = {
                                    actionApp = null
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:" + app.packageName)
                                            }
                                        )
                                    } catch (e: Exception) {
                                        notify(t.opFailed)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionApp = null }) { Text(t.close) }
            }
        )
    }

    copyTarget?.let { app ->
        FolderPickerDialog(
            startPath = Environment.getExternalStorageDirectory().absolutePath,
            title = t.appChooseFolder,
            onPick = { picked ->
                copyTarget = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { AppOps.copyApk(app, File(picked)) }
                    notify(if (ok) t.appCopiedDone else t.opFailed)
                }
            },
            onCancel = { copyTarget = null }
        )
    }
}

/** Satu ubin aksi di dialog aplikasi (AD3): ikon + label + keterangan singkat. */
@Composable
private fun AppActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
