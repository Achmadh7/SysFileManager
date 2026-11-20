package id.sysfile.manager

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * Daftar media dari indeks MediaStore (L3): Gambar/Video/Audio dibaca
 * dari indeks bawaan Android, bukan dengan menelusuri seluruh pohon
 * berkas. Dipakai layar kategori bila file tersembunyi tidak
 * ditampilkan — MediaStore memang tidak mengindeks file tersembunyi
 * dan folder .nomedia, dan tidak tahu soal folder terkunci, jadi
 * kedua hal itu disaring di sini.
 * Gagal apa pun (kolom DATA hilang, OEM aneh) -> null: pemanggil
 * memakai jalan biasa (paralel walk).
 */
object MediaStoreOps {
    fun isMediaCategory(category: Category): Boolean =
        category == Category.IMAGE || category == Category.VIDEO || category == Category.AUDIO

    fun mediaUri(category: Category): Uri = when (category) {
        Category.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        Category.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Category.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> throw IllegalArgumentException("bukan kategori media: $category")
    }

    /** Benar bila [path] berada di dalam salah satu folder terkunci.
     *  Didelegasikan ke FileOps (r175 H4): cek ancestor-aware +
     *  kanonis, jadi alias path (/sdcard/...) ikut tertutup. */
    fun isLockedPath(path: String, locked: Set<String>): Boolean =
        FileOps.isLockedPath(path, locked)

    @Suppress("DEPRECATION")
    // Kolom DATA deprecated sejak API 29, tapi tetap terbaca bila aplikasi
    // punya All Files Access (yang memang disyaratkan aplikasi ini).
    fun queryMedia(resolver: ContentResolver, category: Category, locked: Set<String> = emptySet(), limit: Int = 1000): List<FEntry>? {
        if (!isMediaCategory(category)) return null
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        return try {
            resolver.query(
                mediaUri(category), projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                // Tanpa kolom DATA tak ada jalur berkas -> jalan biasa saja.
                if (dataIdx < 0) return@use null
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val out = mutableListOf<FEntry>()
                while (cursor.moveToNext() && out.size < limit) {
                    val path = cursor.getString(dataIdx) ?: continue
                    if (path.isBlank()) continue
                    val file = File(path)
                    // Hanya yang kategorinya cocok (MediaStore bisa
                    // mengembalikan ekstensi di luar daftar aplikasi)
                    // dan bukan di dalam folder terkunci.
                    if (FileOps.categoryOf(file.name) != category) continue
                    if (isLockedPath(path, locked)) continue
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val modifiedSec = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    out += entryFromMediaRow(path, size, modifiedSec, category)
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Satu baris MediaStore -> FEntry (murni, bisa dites di JVM). */
    fun entryFromMediaRow(path: String, size: Long, modifiedSec: Long, category: Category): FEntry {
        val file = File(path)
        return FEntry(
            path = file.absolutePath,
            name = file.name,
            isDir = false,
            size = size,
            modified = modifiedSec * 1000L,
            category = category
        )
    }
}
