package id.sysfile.manager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Kartu seksi bergaya Material 3 Expressive dengan ikon judul. */
@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
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
        Category.IMAGE -> Color(0xFF8B7CFF)
        Category.VIDEO -> Color(0xFFFF5C8A)
        Category.AUDIO -> Color(0xFF2DD4BF)
        Category.DOCUMENT -> Color(0xFF4DA3FF)
        Category.APP -> Color(0xFF7ED957)
        Category.ARCHIVE -> Color(0xFFFFB020)
        Category.OTHER -> Color(0xFF9AA7B8)
    }
}

/** Warna ikon satu entri: folder amber, PDF merah, sisanya warna kategorinya. */
fun entryColor(entry: FEntry): Color {
    if (entry.isDir) return Color(0xFFFFA000)
    if (FileOps.extOf(entry.name) == "pdf") return Color(0xFFE53935)
    return categoryColor(entry.category)
}

/** Cache ikon APK agar daftar tidak mengurai ulang berkas yang sama. */
private val apkIconCache = android.util.LruCache<String, android.graphics.Bitmap>(64)

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

@Composable
fun rememberThumbLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}

/** Miniatur foto/video, atau ikon kategori untuk berkas lain. */
@Composable
fun FileThumb(entry: FEntry, sizeDp: Int) {
    val isMedia = entry.category == Category.IMAGE || entry.category == Category.VIDEO
    if (isMedia && !entry.isDir) {
        AsyncImage(
            model = File(entry.path),
            contentDescription = null,
            imageLoader = rememberThumbLoader(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        val color = entryColor(entry)
        Box(
            Modifier.size(sizeDp.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
                null,
                tint = color,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        }
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
                if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
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

/** Baris berkas untuk tampilan daftar. */
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
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FileThumbTile(entry, 54)
            if (locked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (entry.isDir) {
                listOfNotNull(itemCount?.let { txt().items(it) }, formatDate(entry.modified))
                    .joinToString(" • ")
            } else {
                formatDate(entry.modified)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        } else if (entry.isDir) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                FileOps.formatSize(entry.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
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
                if (entry.isDir) Icons.Default.Folder else categoryIcon(entry.category, entry.name),
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

/** Kartu penyimpanan biru baja solid: label, angka terpakai besar, detail total/kosong,
 *  garis gelombang putih selebar kartu, lalu baris bawah berisi persen sedang + kata
 *  "terpakai" di kiri dan chip status kesehatan di kanan (desain "Ekspresif Konsisten"
 *  varian B pilihan pemilik). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradientStorageCard(
    label: String,
    usedText: String,
    detailText: String,
    percentText: String,
    usedWord: String,
    statusText: String,
    statusDot: Color,
    fraction: Float,
    icon: ImageVector = Icons.Default.Storage,
    onClick: () -> Unit
) {
    val frac = fraction.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF2E5A88))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    usedText + " " + detailText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            LinearWavyProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.35f)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    percentText + " " + usedWord,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusDot))
                Spacer(Modifier.width(8.dp))
                Text(statusText, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}
