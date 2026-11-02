package id.sysfile.manager

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
        Category.IMAGE -> Color(0xFF7C4DFF)
        Category.VIDEO -> Color(0xFFEC407A)
        Category.AUDIO -> Color(0xFF009688)
        Category.DOCUMENT -> Color(0xFF2196F3)
        Category.APP -> Color(0xFF689F38)
        Category.ARCHIVE -> Color(0xFF8D6E63)
        Category.OTHER -> Color(0xFF757575)
    }
}

/** Warna ikon satu entri: folder amber, PDF merah, sisanya warna kategorinya. */
fun entryColor(entry: FEntry): Color {
    if (entry.isDir) return Color(0xFFFFA000)
    if (FileOps.extOf(entry.name) == "pdf") return Color(0xFFE53935)
    return categoryColor(entry.category)
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
    if (isMedia) {
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
    onLongClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumb(entry, 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (entry.isDir) {
                listOfNotNull(itemCount?.let { txt().items(it) }, formatDate(entry.modified))
                    .joinToString(" • ")
            } else {
                FileOps.formatSize(entry.size) + " • " + formatDate(entry.modified)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() })
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
    onLongClick: () -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FileThumbLarge(entry)
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

/** Kartu penyimpanan gradasi ungu→oranye dengan cincin persen pemakaian (arah "Gelap Gradasi"). */
@Composable
fun GradientStorageCard(
    label: String,
    usedText: String,
    freeText: String,
    fraction: Float,
    onClick: () -> Unit
) {
    val frac = fraction.coerceIn(0f, 1f)
    val pct = (frac * 100).toInt()
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF7B1FA2), Color(0xFFE65100))))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 9.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.28f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFFFD54F),
                        startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text("$pct%", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFE0B2))
                Text(usedText, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(freeText, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFF3E0))
            }
        }
    }
}
