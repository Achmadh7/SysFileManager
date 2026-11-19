package id.sysfile.manager

import android.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

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
        val iso: String?,
        val aperture: String?,
        val focalLength: String?,
        val exposure: String?,
        val location: String?
    ) {
        /** True bila ada SATU saja data EXIF asli (ukuran tidak dihitung:
         *  ukuran selalu ada untuk berkas apa pun). */
        fun hasExif(): Boolean = camera != null || dateTaken != null ||
            dimensions != null || iso != null || aperture != null ||
            focalLength != null || exposure != null || location != null
    }

    /** "18/10" -> 1.8; "100" -> 100.0; null/rusak/"0/0" -> null. Murni. */
    fun parseRational(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if ("/" !in s) return s.toDoubleOrNull()
        val parts = s.split("/")
        if (parts.size != 2) return null
        val num = parts[0].toDoubleOrNull() ?: return null
        val den = parts[1].toDoubleOrNull() ?: return null
        if (den == 0.0) return null
        return num / den
    }

    /** Satu desimal, tanpa ",0" di belakang ("50,0" -> "50"). Murni. */
    fun formatDecimal1(value: Double, locale: Locale): String {
        val s = String.format(locale, "%.1f", value)
        val sep = if (locale.language == "id") ",0" else ".0"
        return if (s.endsWith(sep)) s.dropLast(2) else s
    }

    /** 1.8 -> "f/1,8" (ID) / "f/1.8" (EN). Murni. */
    fun formatAperture(value: Double?, locale: Locale): String? {
        if (value == null || value <= 0) return null
        return "f/" + formatDecimal1(value, locale)
    }

    /** 4.7 -> "4,7 mm" (ID) / "4.7 mm" (EN). Murni. */
    fun formatFocalLength(value: Double?, locale: Locale): String? {
        if (value == null || value <= 0) return null
        return formatDecimal1(value, locale) + " mm"
    }

    /** 0.008 -> "1/125 s"; 0.5 -> "0,5 s"; 2.0 -> "2 s". Murni. */
    fun formatExposure(seconds: Double?, locale: Locale): String? {
        if (seconds == null || seconds <= 0) return null
        if (seconds >= 0.5) return formatDecimal1(seconds, locale) + " s"
        val denom = (1.0 / seconds).roundToInt()
        if (denom < 2) return formatDecimal1(seconds, locale) + " s"
        return "1/$denom s"
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
            val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                .takeIf { it > 0 }?.toString()
            val aperture = formatAperture(
                parseRational(exif.getAttribute(ExifInterface.TAG_F_NUMBER)),
                appLocale()
            )
            val focalLength = formatFocalLength(
                parseRational(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)),
                appLocale()
            )
            val exposureRaw = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                ?.toDoubleOrNull()
                ?: parseRational(exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
            val exposure = formatExposure(exposureRaw, appLocale())
            val latLong = FloatArray(2)
            val location = if (exif.getLatLong(latLong)) {
                formatGps(latLong[0].toDouble(), latLong[1].toDouble(), appLocale())
            } else {
                null
            }
            val info = ExifInfo(
                camera, dateTaken, dimensions, size, iso,
                aperture, focalLength, exposure, location
            )
            if (info.hasExif()) info else null
        } catch (e: Exception) {
            null
        }
    }
}
