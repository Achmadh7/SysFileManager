package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Photo
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
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Color
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
    isLiquid: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    // r212: kaca saat gaya Liquid.
    val liquid = LocalLiquid.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLiquid) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = null,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
            .then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier)
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

/** Warna + ikon per kategori berkas (gaya iOS SF Symbol saat isLiquid). */
fun categoryIcon(category: Category, name: String = "", isLiquid: Boolean = false): ImageVector {
    if (isLiquid) {
        return when (category) {
            Category.IMAGE -> Icons.Default.Photo
            Category.VIDEO -> Icons.Default.Videocam
            Category.AUDIO -> Icons.Default.Headphones
            Category.DOCUMENT -> if (FileOps.extOf(name) == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Article
            Category.APP -> Icons.Default.Apps
            Category.ARCHIVE -> Icons.Default.Inventory2
            Category.OTHER -> Icons.Default.Folder
        }
    }
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
    "download" to FolderStyle(Icons.Default.Download, Color(0xFF9ACBFA)),
    "downloads" to FolderStyle(Icons.Default.Download, Color(0xFF9ACBFA)),
    "dcim" to FolderStyle(Icons.Default.PhotoCamera, Color(0xFFCFBCFF)),
    "pictures" to FolderStyle(Icons.Default.Image, Color(0xFFCFBCFF)),
    "documents" to FolderStyle(Icons.Default.Description, Color(0xFF7FD8C3)),
    "android" to FolderStyle(Icons.Default.Android, Color(0xFFA8D98A)),
    "music" to FolderStyle(Icons.Default.MusicNote, Color(0xFFF2A7C3)),
    "audiobooks" to FolderStyle(Icons.Default.MusicNote, Color(0xFFF2A7C3)),
    "movies" to FolderStyle(Icons.Default.Movie, Color(0xFFFFB870)),
    "alarms" to FolderStyle(Icons.Default.Alarm, Color(0xFF9AA8B5))
)
private val defaultFolderStyle = FolderStyle(Icons.Default.Folder, Color(0xFF9AA8B5))

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

/** Ikon asli aplikasi dari berkas APK, dimuat di latar; null untuk non-APK atau gagal urai. */
@Composable
fun rememberApkIcon(entry: FEntry): android.graphics.Bitmap? {
    val context = LocalContext.current
    val key = entry.path + ":" + entry.modified
    var icon by remember(key) { mutableStateOf(apkIconCache.get(key)) }
    LaunchedEffect(key) {
        if (icon == null && entry.category == Category.APP && !entry.isDir) {
            val bmp = withContext(Dispatchers.IO) { FileOps.apkIcon(context, entry.file) }
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

/** Miniatur besar untuk sel kisi 2 kolom: foto/video memenuhi sel, lainnya panel ikon berwarna. */
@Composable
fun FileThumbLarge(entry: FEntry) {
    val isMedia = (entry.category == Category.IMAGE || entry.category == Category.VIDEO) && !entry.isDir
    val apkIcon = rememberApkIcon(entry)
    if (apkIcon != null) {
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
    } else {
        val color = entryColor(entry)
        Box(
            Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (entry.isDir) folderIcon(entry.name) else categoryIcon(entry.category, entry.name),
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
    locked: Boolean = false,
    isLiquid: Boolean = false
) {
    val t = txt()
    // r212: kaca saat gaya Liquid.
    val liquid = LocalLiquid.current
    val emptyDir = entry.isDir && itemCount == 0
    val sub = if (entry.isDir) {
        if (emptyDir) t.emptyShort
        else listOfNotNull(itemCount?.let { t.items(it) }, shortDate(entry.modified, t))
            .joinToString(" • ")
    } else {
        shortDate(entry.modified, t)
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isLiquid) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        border = null,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .alpha(if (emptyDir && !isSelected) 0.6f else 1f)
            .then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(16.dp), if (isSelected) MaterialTheme.colorScheme.primaryContainer else liquid.glassTint) else Modifier)
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
                if (isLiquid) {
                    IosCheckCircle(checked = isSelected, onClick = onClick)
                } else {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                }
            } else if (!entry.isDir) {
                Text(
                    FileOps.formatSize(entry.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            } else if (isToday(entry.modified)) {
                // RM1: badge New — folder yang berubah hari ini.
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1D4367))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.newShort,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** Ubin kotak membulat untuk kartu daftar: foto/video memenuhi ubin, lainnya ikon berwarna. */
@Composable
fun FileThumbTile(entry: FEntry, sizeDp: Int) {
    val isMedia = entry.category == Category.IMAGE || entry.category == Category.VIDEO
    val apkIcon = rememberApkIcon(entry)
    if (apkIcon != null) {
        Image(
            bitmap = apkIcon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else if (isMedia && !entry.isDir) {
        AsyncImage(
            model = File(entry.path),
            contentDescription = null,
            imageLoader = rememberThumbLoader(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        val color = entryColor(entry)
        Box(
            Modifier.size(sizeDp.dp).clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (entry.isDir) folderIcon(entry.name) else categoryIcon(entry.category, entry.name),
                null,
                tint = color,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
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
    locked: Boolean = false,
    isLiquid: Boolean = false
) {
    // r212: kaca saat gaya Liquid.
    val liquid = LocalLiquid.current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(16.dp), if (isSelected) MaterialTheme.colorScheme.primaryContainer else liquid.glassTint) else Modifier)
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
            if (isLiquid) {
                IosCheckCircle(checked = isSelected, onClick = onClick)
            } else {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
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
    isLiquid: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // r211: kaca saat gaya Liquid — tint + tepi terang seperti GlassSurface.
    val liquid = LocalLiquid.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .then(if (isLiquid) Modifier.glassTint(RoundedCornerShape(28.dp), liquid.glassTint) else Modifier.background(scheme.secondaryContainer))
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
                Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
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
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(statusDot))
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusDot)
                        }
                    }
                }
                Text(
                    usedText + " " + usedWord,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(detailText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (LocalIsLiquid.current) {
                    LiquidProgressBar(
                        progress = { usedFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
}
