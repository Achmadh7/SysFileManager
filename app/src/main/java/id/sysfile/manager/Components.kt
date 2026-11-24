package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.decode.SvgDecoder
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** Kartu seksi bergaya Material 3 Expressive dengan ikon judul. */
@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().nothingBorder(RoundedCornerShape(28.dp))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Warna + ikon per kategori berkas. */
fun categoryIcon(category: Category, name: String): ImageVector {
    return when (category) {
        Category.IMAGE -> Icons.Default.Image
        Category.VIDEO -> Icons.Default.Movie
        Category.AUDIO -> Icons.Default.MusicNote
        Category.DOCUMENT -> if (FileOps.extOf(name) == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Description
        Category.APP -> Icons.Default.Android
        Category.ARCHIVE -> Icons.Default.Archive
        Category.OTHER -> Icons.Default.InsertDriveFile
    }
}

fun categoryColor(category: Category): Color {
    return when (category) {
        Category.IMAGE -> Color(0xFF7FB2F0)
        Category.VIDEO -> Color(0xFFA78BFA)
        Category.AUDIO -> Color(0xFF2DD4BF)
        Category.DOCUMENT -> Color(0xFF40C4FF)
        Category.APP -> Color(0xFF7ED957)
        Category.ARCHIVE -> Color(0xFFFFB020)
        Category.OTHER -> Color(0xFF9AA7B8)
    }
}

/** Warna ikon satu entri: folder amber, PDF merah, sisanya warna kategorinya. */
fun entryColor(entry: FEntry): Color {
    if (entry.isDir) return folderColor(entry.name)
    if (FileOps.extOf(entry.name) == "pdf") return Color(0xFFE53935)
    return categoryColor(entry.category)
}

/** Gaya folder dikenal: ikon + warna per nama (huruf besar/kecil diabaikan), sisanya netral. */
private data class FolderStyle(val icon: ImageVector, val color: Color)

private val folderStyles: Map<String, FolderStyle> = mapOf(
    "download" to FolderStyle(Icons.Default.Download, Color(0xFF0EA5E9)),
    "downloads" to FolderStyle(Icons.Default.Download, Color(0xFF0EA5E9)),
    "dcim" to FolderStyle(Icons.Default.PhotoCamera, Color(0xFF8B5CF6)),
    "pictures" to FolderStyle(Icons.Default.Image, Color(0xFF8B5CF6)),
    "documents" to FolderStyle(Icons.Default.Description, Color(0xFF14B8A6)),
    "android" to FolderStyle(Icons.Default.Android, Color(0xFF10B981)),
    "music" to FolderStyle(Icons.Default.MusicNote, Color(0xFFF43F5E)),
    "audiobooks" to FolderStyle(Icons.Default.MusicNote, Color(0xFFF43F5E)),
    "movies" to FolderStyle(Icons.Default.Movie, Color(0xFFF59E0B)),
    "alarms" to FolderStyle(Icons.Default.Alarm, Color(0xFF64748B)),
    "system" to FolderStyle(Icons.Default.Settings, Color(0xFFF59E0B)),
    "vendor" to FolderStyle(Icons.Default.Build, Color(0xFF06B6D4)),
    "product" to FolderStyle(Icons.Default.Storage, Color(0xFF6366F1)),
    "data" to FolderStyle(Icons.Default.Security, Color(0xFFEC4899)),
    "priv-app" to FolderStyle(Icons.Default.Security, Color(0xFFF59E0B)),
    "app" to FolderStyle(Icons.Default.Android, Color(0xFF10B981)),
    "etc" to FolderStyle(Icons.Default.Terminal, Color(0xFF38BDF8)),
    "mnt" to FolderStyle(Icons.Default.Storage, Color(0xFF06B6D4))
)
private val defaultFolderStyle = FolderStyle(Icons.Default.Folder, Color(0xFF6366F1))

fun folderIcon(name: String): ImageVector =
    (folderStyles[name.lowercase()] ?: defaultFolderStyle).icon

fun folderColor(name: String): Color =
    (folderStyles[name.lowercase()] ?: defaultFolderStyle).color

/** Cache ikon APK agar daftar tidak mengurai ulang berkas yang sama. */
// M9: cache dihitung per byte (max 8 MB) — dulu 64 entri tanpa batas
// byte, jadi 64 ikon 512x512 (1 MB tiap) menahan 64 MB sekaligus.
private val apkIconCache = object : android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}

/** Ikon asli aplikasi dari berkas APK maupun folder paket/sistem aplikasi, dimuat di latar. */
@Composable
fun rememberApkIcon(entry: FEntry): android.graphics.Bitmap? {
    val context = LocalContext.current
    val key = entry.path + ":" + entry.modified
    var icon by remember(key) { mutableStateOf(apkIconCache.get(key)) }
    val eligible = (!entry.isDir && entry.category == Category.APP) ||
        (entry.isDir && FileOps.mayHaveAppIcon(entry.file))
    LaunchedEffect(key, eligible) {
        if (icon == null && eligible) {
            val bmp = withContext(Dispatchers.IO) {
                FileOps.entryAppIcon(context, entry.file, entry.isDir)
            }
            if (bmp != null) {
                apkIconCache.put(key, bmp)
                icon = bmp
            }
        }
    }
    return icon
}

// Satu ImageLoader untuk SEMUA miniatur (r84): sebelumnya setiap
// baris FileThumb membuat loader sendiri (remember per baris) — tiap
// loader punya memory cache + disk cache sendiri, jadi miniatur
// foto/video di-decode ulang tiap baris dan saat scroll kembali.
// Loader dibuat sekali per proses (applicationContext supaya tidak
// menahan Activity) dan dipakai ulang oleh semua AsyncImage miniatur.
private var sharedThumbLoader: ImageLoader? = null

@Composable
fun rememberThumbLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        sharedThumbLoader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
            .also { sharedThumbLoader = it }
    }
}

/**
 * Opsi 2 — Pocket Folder (Kantong Folder Terbuka):
 * Pelat folder belakang + logo asli aplikasi (atau ikon kategori) yang menyembul
 * keluar dari mulut kantong folder + bibir kantong depan di bagian bawah.
 */
@Composable
private fun PocketFolderGraphic(
    entry: FEntry,
    apkIcon: android.graphics.Bitmap?,
    boxHeightDp: Int,
    modifier: Modifier = Modifier
) {
    val baseColor = entryColor(entry)
    val isAppFolder = apkIcon != null
    val backColor = if (isAppFolder) Color(0xFF334155) else baseColor.copy(alpha = 0.62f)
    val lipColor = if (isAppFolder) Color(0xFF475569) else baseColor
    val peekSize = (boxHeightDp * 0.44f).dp
    val peekOffsetY = -(boxHeightDp * 0.08f).dp

    Box(
        modifier = modifier.background(
            if (isAppFolder) Color(0xFF1E293B) else baseColor.copy(alpha = 0.14f)
        ),
        contentAlignment = Alignment.Center
    ) {
        // Lapis 1: Pelat Belakang Folder + Tab Atas
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val backPath = Path().apply {
                moveTo(w * 0.16f, h * 0.28f)
                lineTo(w * 0.16f, h * 0.23f)
                lineTo(w * 0.42f, h * 0.23f)
                lineTo(w * 0.49f, h * 0.29f)
                lineTo(w * 0.84f, h * 0.29f)
                lineTo(w * 0.84f, h * 0.78f)
                lineTo(w * 0.16f, h * 0.78f)
                close()
            }
            drawPath(backPath, color = backColor)
        }

        // Lapis 2: Logo Asli Aplikasi atau Ikon Kategori yang Menyembul dari Mulut Kantong
        if (apkIcon != null) {
            Image(
                bitmap = apkIcon.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(peekSize)
                    .offset(y = peekOffsetY)
                    .clip(RoundedCornerShape((boxHeightDp * 0.13f).dp))
                    .border(
                        1.2.dp,
                        Color.White.copy(alpha = 0.85f),
                        RoundedCornerShape((boxHeightDp * 0.13f).dp)
                    )
            )
        } else {
            Box(
                Modifier
                    .size(peekSize)
                    .offset(y = peekOffsetY)
                    .clip(RoundedCornerShape((boxHeightDp * 0.13f).dp))
                    .background(Color.White.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = folderIcon(entry.name),
                    contentDescription = null,
                    tint = baseColor,
                    modifier = Modifier.size((boxHeightDp * 0.26f).dp)
                )
            }
        }

        // Lapis 3: Bibir Kantong Depan (menutupi bagian bawah logo yang masuk ke kantong) + garis slot
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lipTop = h * 0.50f
            val lipHeight = h * 0.34f
            drawRoundRect(
                color = lipColor,
                topLeft = Offset(w * 0.13f, lipTop),
                size = Size(w * 0.74f, lipHeight),
                cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
            )
            // Garis aksen slot kantong
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(w * 0.37f, lipTop + lipHeight * 0.48f),
                end = Offset(w * 0.63f, lipTop + lipHeight * 0.48f),
                strokeWidth = (h * 0.055f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Opsi 2 — Zipper Archive Graphic (Ilustrasi Ritsleting untuk ZIP / 7Z / TAR / RAR).
 */
@Composable
private fun ZipperArchiveGraphic(boxHeightDp: Int, modifier: Modifier = Modifier) {
    val amber = Color(0xFFF59E0B)
    Box(
        modifier = modifier.background(amber.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Badan arsip
            drawRoundRect(
                color = amber,
                topLeft = Offset(w * 0.22f, h * 0.18f),
                size = Size(w * 0.56f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
            )
            // Jalur ritsleting putus-putus
            drawLine(
                color = Color(0xFF451A03),
                start = Offset(w * 0.50f, h * 0.18f),
                end = Offset(w * 0.50f, h * 0.58f),
                strokeWidth = (w * 0.08f).coerceAtLeast(3f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.06f, h * 0.05f), 0f)
            )
            // Kepala ritsleting (zipper pull)
            drawRoundRect(
                color = Color(0xFFFEF3C7),
                topLeft = Offset(w * 0.42f, h * 0.52f),
                size = Size(w * 0.16f, h * 0.17f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
            )
        }
    }
}

/** Miniatur besar untuk sel kisi 2 kolom: foto/video memenuhi sel, folder memakai Pocket Folder Opsi 2. */
@Composable
fun FileThumbLarge(entry: FEntry) {
    val isMedia = (entry.category == Category.IMAGE || entry.category == Category.VIDEO) && !entry.isDir
    val apkIcon = rememberApkIcon(entry)
    if (entry.isDir) {
        PocketFolderGraphic(
            entry = entry,
            apkIcon = apkIcon,
            boxHeightDp = 96,
            modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
        )
    } else if (apkIcon != null) {
        Image(
            bitmap = apkIcon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (isMedia) {
        AsyncImage(
            model = File(entry.path),
            contentDescription = null,
            imageLoader = rememberThumbLoader(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (entry.category == Category.ARCHIVE) {
        ZipperArchiveGraphic(
            boxHeightDp = 96,
            modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
        )
    } else {
        val color = entryColor(entry)
        Box(
            Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon(entry.category, entry.name),
                null,
                tint = color,
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

fun formatDate(millis: Long): String {
    if (millis <= 0) return "—"
    return DateFormat.getDateInstance(DateFormat.MEDIUM, appLocale()).format(Date(millis))
}

/** Tanggal ringkas untuk baris daftar: "Hari ini", "Kemarin", "18 Nov", atau "18 Nov 2025" untuk tahun lain. */
fun shortDate(millis: Long, t: Txt): String {
    if (millis <= 0) return "—"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    fun sameDay(c: Calendar) =
        cal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(now) -> t.today
        sameDay(yesterday) -> t.yesterday
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("d MMM", appLocale()).format(Date(millis))
        else -> SimpleDateFormat("d MMM yyyy", appLocale()).format(Date(millis))
    }
}

/** Benar bila millis jatuh pada hari ini (zona waktu perangkat). */
fun isToday(millis: Long): Boolean {
    if (millis <= 0) return false
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
}

/** Baris berkas untuk tampilan daftar (LB2: 72dp, tanpa chevron, folder kosong diredupkan). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    itemCount: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    locked: Boolean = false
) {
    val t = txt()
    val emptyDir = entry.isDir && itemCount == 0
    val sub = if (entry.isDir) {
        if (emptyDir) t.emptyShort
        else listOfNotNull(itemCount?.let { t.items(it) }, shortDate(entry.modified, t))
            .joinToString(" • ")
    } else {
        shortDate(entry.modified, t)
    }
    val isNothing = LocalNothingTheme.current
    Card(
        shape = RoundedCornerShape(16.dp),
        border = if (isNothing) {
            BorderStroke(1.dp, if (isSelected) NothingSignalRed else MaterialTheme.colorScheme.outlineVariant)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .alpha(if (emptyDir && !isSelected) 0.6f else 1f)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .height(72.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FileThumbTile(entry, 48)
                if (locked) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else if (!entry.isDir) {
                Text(
                    FileOps.formatSize(entry.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            } else if (isToday(entry.modified)) {
                // RM1: badge New — folder yang berubah hari ini.
                Box(
                    Modifier.clip(RoundedCornerShape(if (isNothing) 50 else 10))
                        .background(if (isNothing) NothingSignalRed else MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isNothing) t.newShort.uppercase() else t.newShort,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isNothing) Color.White else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/** Ubin kotak membulat untuk kartu daftar: folder memakai Pocket Folder Opsi 2, arsip memakai ritsleting, APK memakai logo asli. */
@Composable
fun FileThumbTile(entry: FEntry, sizeDp: Int) {
    val isMedia = entry.category == Category.IMAGE || entry.category == Category.VIDEO
    val apkIcon = rememberApkIcon(entry)
    val isNothing = LocalNothingTheme.current
    if (entry.isDir && isNothing && apkIcon == null) {
        Box(
            Modifier.size(sizeDp.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                folderIcon(entry.name),
                null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size((sizeDp * 0.48f).dp)
            )
        }
    } else if (entry.isDir) {
        PocketFolderGraphic(
            entry = entry,
            apkIcon = apkIcon,
            boxHeightDp = sizeDp,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
        )
    } else if (apkIcon != null) {
        Image(
            bitmap = apkIcon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (isMedia) {
        AsyncImage(
            model = File(entry.path),
            contentDescription = null,
            imageLoader = rememberThumbLoader(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (entry.category == Category.ARCHIVE) {
        ZipperArchiveGraphic(
            boxHeightDp = sizeDp,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
        )
    } else {
        val color = entryColor(entry)
        val tileShape = if (isNothing) CircleShape else RoundedCornerShape(16.dp)
        Box(
            Modifier.size(sizeDp.dp).clip(tileShape)
                .background(if (isNothing) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.16f))
                .then(if (isNothing) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon(entry.category, entry.name),
                null,
                tint = if (isNothing) MaterialTheme.colorScheme.onSurface else color,
                modifier = Modifier.size((sizeDp * 0.50f).dp)
            )
        }
    }
}

/** Sel berkas untuk tampilan kisi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCell(
    entry: FEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    locked: Boolean = false
) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            FileThumbLarge(entry)
            if (locked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        }
    }
}

/** Status kesehatan penyimpanan dari sisa ruang: teks + warna titik indikator. */
@Composable
fun storageStatus(freeFraction: Float): Pair<String, Color> {
    val t = txt()
    return when {
        freeFraction >= 0.25f -> t.statusHealthy to Color(0xFF4ADE80)
        freeFraction >= 0.10f -> t.statusAlmostFull to Color(0xFFFFC107)
        else -> t.statusFull to Color(0xFFFF5252)
    }
}

/** Kartu penyimpanan (D1 Solid Murni, r37 — rombak total tanpa kaca):
 *  kartu solid secondaryContainer; baris kepala berisi ikon + label volume dan
 *  chip status berwarna mengikuti kesehatan penyimpanan; angka terpakai
 *  besar dengan detail total di bawahnya; dasar kartu berisi bilah progres
 *  wavy resmi (bukan lagi gelombang cyan transparan) + baris persen.
 *  Tanda tangan sama seperti kartu sebelumnya agar pemanggil tidak
 *  berubah (ditambah usedFraction sebelum onClick). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageCard(
    label: String,
    usedText: String,
    detailText: String,
    percentText: String,
    usedWord: String,
    statusText: String,
    statusDot: Color,
    icon: ImageVector = Icons.Default.Storage,
    usedFraction: Float = 0f,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(28.dp)
    val isNothing = LocalNothingTheme.current
    Box(
        Modifier.fillMaxWidth().clip(cardShape)
            .background(scheme.secondaryContainer)
            .then(
                if (isNothing) Modifier.border(1.dp, scheme.outlineVariant, cardShape)
                else Modifier
            )
            .clickable { onClick() }
    ) {
        StorageCardContent(
            label = label,
            usedText = usedText,
            detailText = detailText,
            percentText = percentText,
            usedWord = usedWord,
            statusText = statusText,
            statusDot = statusDot,
            icon = icon,
            usedFraction = usedFraction
        )
    }
}

/** Isi kartu penyimpanan tanpa wadah kartu — dipakai Beranda saat
 *  beberapa penyimpanan digabung dalam satu kartu (B1, 2026-11-18). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageCardContent(
    label: String,
    usedText: String,
    detailText: String,
    percentText: String,
    usedWord: String,
    statusText: String,
    statusDot: Color,
    icon: ImageVector = Icons.Default.Storage,
    usedFraction: Float = 0f
) {
    val scheme = MaterialTheme.colorScheme
    Column {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusDot.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, statusDot.copy(alpha = 0.6f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(statusDot))
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = statusDot)
                        }
                    }
                }
                Text(
                    usedText + " " + usedWord,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(detailText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (LocalNothingTheme.current) {
                    NothingMatrixProgressBar(fraction = usedFraction)
                } else {
                    LinearWavyProgressIndicator(
                        progress = { usedFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest
                    )
                }
                Text(
                    percentText + " " + usedWord,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
}

/**
 * Progress bar operasi berkas Variasi 1A (Split-Gap Solid Capsule):
 * kapsul warna solid murni (tanpa gradien) dengan celah pemisah 5dp
 * antara bagian progres terisi dan sisa track, serta titik ujung di dalam bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitGapSolidProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier
) {
    val clamped = (progress ?: 0.35f).coerceIn(0.06f, 1f)
    if (LocalNothingTheme.current) {
        NothingMatrixProgressBar(fraction = clamped, modifier = modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = { clamped },
            modifier = modifier.fillMaxWidth(),
            color = scheme.primary,
            trackColor = scheme.surfaceContainerHighest
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = scheme.primary,
            trackColor = scheme.surfaceContainerHighest
        )
    }
}

