package id.sysfile.manager

import android.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Detail EXIF foto (DF1, pilihan Achmad 2026-11-20): dibaca lewat
 *  android.media.ExifInterface framework (API 24+, minSdk 26 — tanpa
 *  dependensi baru). Hanya baca; berkas tidak diubah. */
object ExifOps {

    /** Satu baris info; semua null kecuali yang memang ada di berkas. */
    data class ExifInfo(
        val camera: String?,
        val dateTaken: String?,
        val dimensions: String?,
        val size: String?,
        val location: String?
    ) {
        /** True bila ada SATU saja data EXIF asli (ukuran tidak dihitung:
         *  ukuran selalu ada untuk berkas apa pun). */
        fun hasExif(): Boolean = camera != null || dateTaken != null ||
            dimensions != null || location != null
    }

    /** -6.1751, 106.6345 -> "-6,1751, 106,6345" (ID). Murni. */
    fun formatGps(lat: Double, lon: Double, locale: Locale): String =
        String.format(locale, "%.4f, %.4f", lat, lon)

    /** "2026:11:20 14:15:30" -> "20 Nov 2026, 14.15" (ID). Murni. */
    fun formatExifDate(raw: String?, locale: Locale): String? {
        if (raw.isNullOrBlank()) return null
        val parsed = try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                .apply { isLenient = false }.parse(raw.trim())
        } catch (e: Exception) {
            null
        } ?: return null
        return SimpleDateFormat("d MMM yyyy, HH.mm", locale).format(parsed)
    }

    /** Baca EXIF dari [path]; null bila berkas bukan gambar, rusak, atau
     *  tidak mengandung satu pun data EXIF. */
    fun readExif(path: String): ExifInfo? {
        return try {
            val file = File(path)
            val exif = ExifInterface(file)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val camera = listOf(make, model).filter { it.isNotEmpty() }
                .joinToString(" ").takeIf { it.isNotEmpty() }
            val dateRaw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val dateTaken = formatExifDate(dateRaw, appLocale())
            val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                .takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
                    .takeIf { it > 0 }
            val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                .takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
                    .takeIf { it > 0 }
            val dimensions = if (w != null && h != null) "$w \u00d7 $h" else null
            val size = if (file.isFile) FileOps.formatSize(file.length()) else null
            val latLong = FloatArray(2)
            val location = if (exif.getLatLong(latLong)) {
                formatGps(latLong[0].toDouble(), latLong[1].toDouble(), appLocale())
            } else {
                null
            }
            val info = ExifInfo(
                camera, dateTaken, dimensions, size, location
            )
            if (info.hasExif()) info else null
        } catch (e: Exception) {
            null
        }
    }

    /** Tag GPS yang dihapus dari salinan yang dibagikan (L9). */
    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_DIRECTION,
        ExifInterface.TAG_GPS_DIRECTION_REF,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_VERSION_ID
    )

    /** L9: salin [file] ke [cacheDir]/share-strip, hapus semua tag GPS
     *  dari salinan, dan kembalikan salinan; null bila tidak ada GPS
     *  atau salinan gagal. Berkas asli tidak disentuh. */
    fun stripGpsCopy(file: File, cacheDir: File): File? {
        val exif = try {
            ExifInterface(file)
        } catch (e: Exception) {
            return null
        }
        if (GPS_TAGS.none { exif.getAttribute(it) != null }) return null
        val dir = File(cacheDir, "share-strip").apply {
            FileOps.evictStaleCache(this)
            mkdirs()
        }
        val copy = File(dir, file.name)
        try {
            file.copyTo(copy, overwrite = true)
        } catch (e: Exception) {
            return null
        }
        return try {
            val copyExif = ExifInterface(copy)
            for (tag in GPS_TAGS) copyExif.setAttribute(tag, null)
            copyExif.saveAttributes()
            copy
        } catch (e: Exception) {
            copy.delete()
            null
        }
    }
}
