package id.sysfile.manager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Kategori berkas berdasarkan ekstensi, untuk ikon, warna, dan analisis. */
enum class Category { IMAGE, VIDEO, AUDIO, DOCUMENT, APP, ARCHIVE, OTHER }

enum class SortKey { NAME, DATE, SIZE }

/** Satu entri file/folder yang ditampilkan di daftar. */
data class FEntry(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val category: Category
) {
    val file: File get() = File(path)
}

/** Satu volume penyimpanan (internal / SD / USB). */
data class RootInfo(
    val path: String,
    val kind: String, // "internal" | "sd" | "usb"
    val total: Long,
    val free: Long
)

/** Data tampilan kartu hero penyimpanan (BR4, r161): semua angka dihitung
 *  dari TERPAKAI (total - bebas) agar Internal dan Root/SD/USB tampil
 *  konsisten (sebelumnya Root memakai tersisa). */
data class HeroCardData(
    val usedBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val usedFraction: Float,
    val usedPercent: Double
)

/** Angka kartu hero dari satu volume. Murni (tanpa Android) agar bisa
 *  diuji unit: total <= 0 atau bebas > total tidak pernah menghasilkan
 *  angka negatif. */
fun heroCardData(root: RootInfo): HeroCardData {
    val total = root.total.coerceAtLeast(0L)
    val free = root.free.coerceIn(0L, total)
    val used = total - free
    return HeroCardData(
        usedBytes = used,
        freeBytes = free,
        totalBytes = total,
        usedFraction = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f,
        usedPercent = if (total > 0) used * 100.0 / total else 0.0
    )
}

/** Volume yang punya kartu hero (total diketahui) — halaman pager BR4. */
fun heroRoots(roots: List<RootInfo>): List<RootInfo> = roots.filter { it.total > 0 }

/** Satu entri di dalam arsip, untuk layar Isi Arsip (dibuka tanpa ekstrak). */
data class ArchiveItem(val name: String, val isDir: Boolean, val size: Long)

/** Info dasar dari dalam berkas APK: nama aplikasi, paket, dan versi. */
data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: List<String> = emptyList()
)

/** Hasil analisis penyimpanan: ukuran per kategori + file terbesar. */
data class AnalysisResult(
    val byCategory: Map<Category, Long>,
    val counts: Map<Category, Int>,
    val largest: List<FEntry>,
    val totalFiles: Int
)

/**
 * Mesin berkas SysFileManager: semua operasi murni java.io.File, dipanggil dari
 * dispatcher latar oleh UI. Tidak ada status global di sini.
 */
object FileOps {
    /** Nama folder Tempat Sampah di akar penyimpanan internal; selalu dikecualikan dari pemindaian. */
    const val TRASH_DIR_NAME = ".SysFileTrash"

    /** Batas ekstraksi arsip (zip-bomb): jumlah entri dan total byte hasil ekstrak. */
    const val MAX_ARCHIVE_ENTRIES = 10_000
    const val MAX_EXTRACT_BYTES = 2L * 1024L * 1024L * 1024L // 2 GiB

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "wmv", "ts")
    private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "amr", "mid", "midi")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "epub", "prop", "config", "conf", "toml", "log")
    private val APP_EXT = setOf("apk", "xapk", "apks", "aab")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "obb", "jar")

    /** r183: ekstensi yang isinya sudah terkompresi — DEFLATE ulang
     *  hanya membuang CPU tanpa mengecilkan ukuran. Dipakai zipFiles
     *  dan createEncryptedZip untuk menulis entri STORED. */
    private val INCOMPRESSIBLE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "mov", "avi", "webm",
        "mp3", "aac", "flac", "zip", "gz", "bz2", "xz", "7z", "rar", "apk", "obb"
    )

    fun extOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    /** r183: true bila nama berkas berakhiran ekstensi yang sudah
     *  terkompresi (lihat INCOMPRESSIBLE_EXTENSIONS). Murni, diuji unit. */
    fun isIncompressibleEntry(name: String): Boolean = extOf(name) in INCOMPRESSIBLE_EXTENSIONS

    fun categoryOf(name: String): Category {
        return when (extOf(name)) {
            in IMAGE_EXT -> Category.IMAGE
            in VIDEO_EXT -> Category.VIDEO
            in AUDIO_EXT -> Category.AUDIO
            in DOC_EXT -> Category.DOCUMENT
            in APP_EXT -> Category.APP
            in ARCHIVE_EXT -> Category.ARCHIVE
            else -> Category.OTHER
        }
    }

    /** Daftar geser penampil media: semua berkas sejenis di folder saat ini,
     *  dalam urutan tampil, beserta indeks berkas yang dibuka. Bila berkas
     *  tidak ada di daftar (mis. dibuka dari hasil pencarian), kembalikan
     *  hanya dirinya sendiri. */
    fun mediaSiblings(entries: List<FEntry>, current: FEntry): Pair<List<String>, Int> {
        val same = entries.filter { it.category == current.category }.map { it.path }
        val idx = same.indexOf(current.path)
        return if (idx >= 0) same to idx else listOf(current.path) to 0
    }

    fun mimeOf(file: File): String {
        val ext = extOf(file.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (categoryOf(file.name)) {
                Category.IMAGE -> "image/*"
                Category.VIDEO -> "video/*"
                Category.AUDIO -> "audio/*"
                Category.DOCUMENT -> "text/plain"
                Category.APP -> "application/vnd.android.package-archive"
                Category.ARCHIVE -> "application/zip"
                Category.OTHER -> "application/octet-stream"
            }
    }

    fun isTextFile(file: File): Boolean {
        // r148: berkas dotfile tanpa ekstensi (.env, .editorconfig,
        // .gitignore) tidak tertangkap extOf — titiknya di indeks 0
        // sehingga ekstensinya "" — jadi dicocokkan dari namanya.
        // .env.* (mis. .env.local) juga berkas env.
        val lower = file.name.lowercase()
        if (lower == ".env" || lower.startsWith(".env.") ||
            lower == ".editorconfig" || lower == ".gitignore"
        ) return true
        val ext = extOf(file.name)
        return ext in setOf("txt", "md", "log", "json", "xml", "csv", "html", "htm", "kt", "java", "py", "js", "css", "ini", "cfg", "yaml", "yml", "properties", "sh", "srt", "prop", "config", "conf", "toml", "tsv", "jsonl", "sql", "jsonc", "reg")
    }

    /**
     * Tulis teks ke berkas secara aman dengan eskalasi izin:
     * 1. Coba penulisan langsung (jalur normal).
     * 2. Bila gagal karena izin read-only, coba setWritable(true).
     * 3. Bila tetap gagal (berkas di /system, /vendor, /data, atau folder root yang membutuhkan root shell),
     *    eskalasi ke RootShell.writeFile.
     */
    fun writeTextSafe(file: File, text: String, cacheDir: File): Boolean {
        try {
            file.writeText(text, Charsets.UTF_8)
            return true
        } catch (_: Exception) {}

        try {
            if (file.setWritable(true)) {
                file.writeText(text, Charsets.UTF_8)
                return true
            }
        } catch (_: Exception) {}

        return RootShell.writeFile(file, text, cacheDir)
    }

    /** Folder yang dibatasi sistem (Android/data, Android/obb): jalur berkas
     *  biasa bisa mengaku kosong padahal isinya tak terbaca — canRead()
     *  true tapi listFiles() kosong (sebagian ROM). Dipakai UI untuk
     *  menawarkan SAF sebagai jalan keluar. */
    fun isRestrictedDir(path: String): Boolean {
        // M16: dulu contains() — folder pengguna seperti
        // .../Backup/Android/data/... ikut dianggap restricted.
        // Sekarang dijangkar ke jalur relatif volume (dua segmen
        // pertama harus Android/data|obb|sandbox), sama seperti
        // SafPath.isRestrictedRel.
        val rel = volumeRelativePath(path) ?: return false
        return SafPath.isRestrictedRel(rel)
    }

    /** Jalur relatif terhadap akar volume penyimpanan
     *  ("/storage/emulated/0", "/sdcard", "/storage/<vol>",
     *  "/data/media/0", "/storage/self/primary"), atau null bila
     *  bukan di bawah volume yang dikenali. Dipakai M16. */
    fun volumeRelativePath(path: String): String? {
        val norm = path.replace(0x5C.toChar(), '/').trimEnd('/')
        val prefixes = listOf(
            "/storage/emulated/0", "/sdcard", "/data/media/0",
            "/storage/self/primary"
        )
        for (prefix in prefixes) {
            if (norm == prefix) return ""
            if (norm.startsWith("$prefix/")) return norm.removePrefix("$prefix/")
        }
        val parts = norm.split("/")
        // /storage/<volume>/...
        if (parts.size >= 4 && parts[1] == "storage" && parts[2].isNotEmpty()) {
            return parts.drop(3).joinToString("/")
        }
        return null
    }

    fun entryOf(file: File): FEntry {
        // isDirectory dibaca sekali saja: setiap panggilan adalah stat ke disk.
        val isDir = file.isDirectory
        return FEntry(
            path = file.absolutePath,
            name = file.name,
            isDir = isDir,
            size = if (isDir) 0L else file.length(),
            modified = file.lastModified(),
            category = if (isDir) Category.OTHER else categoryOf(file.name)
        )
    }

    /** Isi satu folder; null bila folder tidak bisa dibaca. Folder selalu di atas. */
    fun listDir(dir: File, showHidden: Boolean, sort: SortKey, ascending: Boolean): List<FEntry>? {
        if (!dir.isDirectory || !dir.canRead()) return null
        val children = dir.listFiles() ?: return null
        val entries = children
            .filter { showHidden || !it.name.startsWith(".") }
            .map { entryOf(it) }
        val comparator: Comparator<FEntry> = when (sort) {
            SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortKey.DATE -> compareBy { it.modified }
            SortKey.SIZE -> compareBy { it.size }
        }
        val sorted = entries.sortedWith(comparator)
        val ordered = if (ascending) sorted else sorted.reversed()
        val (dirs, files) = ordered.partition { it.isDir }
        return dirs + files
    }

    /** Jumlah isi langsung sebuah folder (untuk label "N item"); 0 bila tak terbaca. */
    fun childCount(dir: File): Int {
        return try { dir.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
    }

/** Volume semu untuk direktori root "/" (di luar penyimpanan bersama). */
    fun rootFilesystem(): RootInfo {
        return try {
            val stat = StatFs("/")
            RootInfo("/", "root", stat.totalBytes, stat.availableBytes)
        } catch (e: Exception) {
            RootInfo("/", "root", 0L, 0L)
        }
    }

    /** Jalur sysfs perangkat blok "major:minor" (mis. /sys/dev/block/8:1). */
    private fun sysBlockPath(major: Int, minor: Int): String? =
        try { File("/sys/dev/block/$major:$minor").canonicalPath } catch (e: Exception) { null }

    /** True bila volume [volumePath] (mis. /storage/1234-ABCD) berada di
     *  perangkat blok USB. Dibaca dari /proc/mounts: baris media_rw untuk
     *  volume ini membawa sumber "/dev/block/vold/public:MAJOR,MINOR";
     *  jalur sysfs perangkat itu mengandung segmen usbN (mis. usb1)
     *  untuk stik USB (dan "mmc" untuk kartu SD). */
    fun isUsbVolume(volumePath: String): Boolean {
        val mounts = try { File("/proc/mounts").readText() } catch (e: Exception) { return false }
        return isUsbVolume(volumePath, mounts, ::sysBlockPath)
    }

    /** Inti [isUsbVolume] yang bisa diuji: [mountsText] isi /proc/mounts,
     *  [resolveSysBlockPath] memetakan "major:minor" ke jalur sysfs. */
    internal fun isUsbVolume(
        volumePath: String,
        mountsText: String,
        resolveSysBlockPath: (Int, Int) -> String?
    ): Boolean {
        val name = File(volumePath).name
        if (name.isEmpty()) return false
        // L14: split pada rangkaian whitespace (bukan spasi tunggal);
        // semua baris yang cocok diperiksa — jalur sysfs yang tak bisa
        // di-resolve tidak lagi menghentikan seluruh scan (mis. bind
        // mount), melainkan dilewati ke match berikutnya.
        for (line in mountsText.lines()) {
            val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size < 2) continue
            val mountPoint = parts[1]
            if (mountPoint != "/mnt/media_rw/$name" && mountPoint != volumePath) continue
            val mm = Regex("""(\d+),(\d+)$""").find(parts[0]) ?: continue
            val major = mm.groupValues[1].toIntOrNull() ?: continue
            val minor = mm.groupValues[2].toIntOrNull() ?: continue
            val sysPath = resolveSysBlockPath(major, minor) ?: continue
            if (sysPath.split("/").any { it.matches(Regex("""usb\d+""")) }) return true
        }
        return false
    }

    /** Semua volume penyimpanan yang bisa dijangkau aplikasi. */
    fun storageRoots(context: Context): List<RootInfo> {
        val roots = mutableListOf<RootInfo>()
        val internal = Environment.getExternalStorageDirectory()
        fun addRoot(path: String, kind: String) {
            if (roots.any { it.path == path }) return
            val f = File(path)
            if (!f.exists()) return
            try {
                val stat = StatFs(path)
                roots += RootInfo(path, kind, stat.totalBytes, stat.availableBytes)
            } catch (e: Exception) {
                roots += RootInfo(path, kind, 0L, 0L)
            }
        }
        addRoot(internal.absolutePath, "internal")
        try {
            context.getExternalFilesDirs(null)?.forEach { dir ->
                if (dir == null) return@forEach
                val p = dir.absolutePath
                val idx = p.indexOf("/Android/")
                val rootPath = if (idx > 0) p.substring(0, idx) else return@forEach
                // L13: klasifikasikan selalu via isUsbVolume (sysfs) — dulu
                // heuristik substring "usb" pada path: volume USB yang
                // path privat aplikasinya tidak mengandung "usb" dilabeli
                // SD permanen (dan sebaliknya, sumber mana pun yang
                // terlihat dulu menetapkan kind-nya).
                val kind = if (isUsbVolume(rootPath)) "usb" else "sd"
                addRoot(rootPath, kind)
            }
        } catch (e: Exception) {
            // Volume eksternal tidak tersedia; internal tetap ditampilkan.
        }
        // r172: volume publik yang tak terlihat (mis. USB OTG) TIDAK
        // muncul di getExternalFilesDirs() — Android sengaja tidak
        // menandai volume transient sebagai VISIBLE_FOR_WRITE (KDoc
        // VolumeInfo: isinya seharusnya diakses lewat SAF), jadi
        // getExternalFilesDirs/getStorageVolumes sama-sama melewatkannya
        // dan refreshRoots() tak pernah melihatnya walau sudah terpasang.
        // Pindai /storage langsung: tiap subdir selain emulated/self
        // adalah volume sekunder yang terpasang (dengan Akses Semua File,
        // FUSE mengizinkan aplikasi membaca titik pasangnya).
        try {
            File("/storage").listFiles()?.forEach { dir ->
                val name = dir.name
                if (!dir.isDirectory || name == "emulated" || name == "self") return@forEach
                addRoot(dir.absolutePath, if (isUsbVolume(dir.absolutePath)) "usb" else "sd")
            }
        } catch (e: Exception) {
            // /storage tak terbaca; volume yang sudah dikenal tetap ada.
        }
        return roots
    }

    /** Statistik isi folder untuk Properti lengkap: total ukuran, jumlah berkas dan
     *  subfolder (rekursif), serta berkas terbesar di dalamnya. */
    data class FolderStats(
        val totalSize: Long,
        val fileCount: Int,
        val dirCount: Int,
        val largestName: String?,
        val largestSize: Long
    )

    fun folderStats(dir: File): FolderStats {
        var total = 0L
        var files = 0
        var dirs = 0
        var largestName: String? = null
        var largestSize = 0L
        if (!dir.isDirectory) return FolderStats(0L, 0, 0, null, 0L)
        // L23: guard kunjungan memangkas siklus symlink.
        val guard = newVisitGuard()
        dir.walkTopDown()
            .onEnter { guard(it) && (it.name != TRASH_DIR_NAME || it == dir) }
            .forEach { f ->
                when {
                    f == dir -> Unit
                    f.isDirectory -> dirs++
                    f.isFile -> {
                        files++
                        val len = f.length()
                        total += len
                        if (len > largestSize) {
                            largestSize = len
                            largestName = f.name
                        }
                    }
                }
            }
        return FolderStats(total, files, dirs, largestName, largestSize)
    }

    /** True bila [outCanonical] berada di dalam [destCanonical] (atau sama dengannya).
     *  Batas separator wajib: tanpa itu, ".../out2" lolos karena berawalan ".../out"
     *  (zip-slip). Akar "/" sudah berakhiran separator, jadi tidak digandakan. */
    internal fun isPathInside(destCanonical: String, outCanonical: String): Boolean {
        if (outCanonical == destCanonical) return true
        val base = if (destCanonical.endsWith(File.separator)) destCanonical else destCanonical + File.separator
        return outCanonical.startsWith(base)
    }

    /** L23: penjaga kunjungan untuk walkTopDown — walkTopDown tidak
     *  mendeteksi siklus symlink, jadi subtree yang sama bisa dikunjungi
     *  ~40x (ukuran folder terhitung berlipat, pemindaian lambat).
     *  Guard ini memangkas direktori yang canonical path-nya sudah
     *  dikunjungi (gagal canonicalize = pangkas). Satu guard per
     *  pemindaian. */
    private fun newVisitGuard(): (File) -> Boolean {
        val seen = mutableSetOf<String>()
        return { dir ->
            val canon = try { dir.canonicalPath } catch (e: Exception) { null }
            if (canon == null) false else seen.add(canon)
        }
    }

    /** L7: catat direktori yang BARU dibuat selama ekstraksi (yang belum
     *  ada sebelum mkdirs) supaya bisa dihapus bila ekstraksi gagal —
     *  dulu hanya berkas yang dilacak, direktori kosong tertinggal.
     *  [dir] dicatat bersama rantai induknya sampai [destCanonical]
     *  atau direktori yang sudah ada. */
    internal fun noteCreatedDir(dir: File?, destCanonical: String, created: MutableList<File>) {
        var d = dir
        while (d != null) {
            val canon = try { d.canonicalPath } catch (e: Exception) { break }
            if (canon == destCanonical || d.exists()) break
            created += d
            d = d.parentFile
        }
    }

    /** L7: hapus direktori tercatat, dari yang terdalam dulu; hanya
     *  yang kosong yang terhapus (delete() gagal diam-diam bila masih
     *  berisi). Dipanggil setelah berkas tertulis dihapus. */
    internal fun deleteCreatedDirs(created: List<File>) {
        created.sortedByDescending { it.absolutePath.length }.forEach { it.delete() }
    }

    /** Canonical path, atau null bila tidak bisa di-resolve (mis. siklus
     *  symlink). Dipakai gerbang kunci (r175 H4). */
    internal fun canonicalOrNull(path: String): String? =
        try { File(path).canonicalPath } catch (e: Exception) { null }

    /** Normalisasi set folder terkunci: tiap entri disimpan mentah DAN
     *  kanonisnya. Ejaan mentah menjaga folder di volume yang belum
     *  ter-mount tetap terkunci; ejaan kanonis menutup alias path
     *  (/sdcard/... -> /storage/emulated/0/...). Idempotent. */
    internal fun canonicalizeLocked(locked: Set<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (raw in locked) {
            out += raw
            canonicalOrNull(raw)?.let { out += it }
        }
        return out
    }

    /** Benar bila [path] berada di dalam (atau sama dengan) salah satu
     *  folder terkunci. Cek mentah dulu (murah, tanpa I/O); bila lolos,
     *  kanoniskan kandidat sekali lalu cek lagi — alias seperti
     *  /sdcard/Private terkanoniskan ke /storage/emulated/0/Private.
     *  Bila kandidat tidak bisa dikanoniskan (mis. siklus symlink),
     *  anggap TERKUNCI (fail closed): jalur seperti itu tidak bisa
     *  dibuka dengan benar. */
    internal fun isLockedPath(path: String, locked: Set<String>): Boolean {
        if (locked.isEmpty()) return false
        if (locked.any { isPathInside(it, path) }) return true
        val canon = canonicalOrNull(path) ?: return true
        if (canon == path) return false
        return locked.any { isPathInside(it, canon) }
    }

    /** Benar bila [path] sudah dibuka pada sesi ini: salah satu entri
     *  [unlocked] adalah [path] sendiri atau leluhurnya (mentah atau
     *  kanonis). Bila kandidat tidak bisa dikanoniskan, anggap BELUM
     *  dibuka — pemanggil akan meminta biometrik lagi (fail closed). */
    internal fun isUnlockedPath(path: String, unlocked: Set<String>): Boolean {
        if (unlocked.isEmpty()) return false
        if (unlocked.any { isPathInside(it, path) }) return true
        val canon = canonicalOrNull(path) ?: return false
        if (canon == path) return false
        return unlocked.any { isPathInside(it, canon) }
    }

    /** Benar bila [path] adalah persis salah satu folder terkunci
     *  (bukan anaknya) — ejaan mentah atau kanonis. Dipakai ikon kunci
     *  + toggle kunci/buka, yang bekerja per folder, bukan per subtree. */
    internal fun isLockedFolder(path: String, locked: Set<String>): Boolean {
        if (locked.isEmpty()) return false
        if (path in locked) return true
        val canon = canonicalOrNull(path) ?: return false
        if (canon == path) return false
        return canon in locked
    }

    /** Nama unik di dalam folder tujuan: "nama (1).ext", "nama (2).ext", dst. */
    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    /** Berkas temp unik di [dir] (tidak dibuat di disk — pemanggil
     *  yang menulis lalu rename). Null bila dir tidak bisa dipakai.
     *  Dipakai M3/M4/M15 supaya ekstrak/staging tidak pernah menulis
     *  langsung ke nama akhir. */
    fun tempFileIn(dir: File): File? {
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val tmp = File(dir, ".sysfile-tmp-" + System.nanoTime())
        return if (tmp.exists()) null else tmp
    }

    /** Hapus entri cache basi di [dir]: yang lebih tua dari [maxAgeMs]
     *  atau di luar [keepNewest] terbaru (urut lastModified turun).
     *  Dipakai M15 untuk cache staging (epub, archive-view, apkshare).
     *  Tanpa pengecualian — yang sedang dipakai viewer tidak dihapus
     *  karena entri segar selalu dalam 20 terbaru. */
    fun evictStaleCache(dir: File?, maxAgeMs: Long = 24L * 60L * 60L * 1000L, keepNewest: Int = 20) {
        if (dir == null || !dir.isDirectory) return
        val now = System.currentTimeMillis()
        val children = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        for ((i, child) in children.withIndex()) {
            val stale = now - child.lastModified() > maxAgeMs || i >= keepNewest
            if (stale) deleteRecursively(child)
        }
    }

    private fun copyOne(src: File, destDir: File): Boolean {
        val dest = File(destDir, uniqueName(destDir, src.name))
        var ok = false
        try {
            ok = if (src.isDirectory) {
                if (!dest.mkdirs() && !dest.isDirectory) {
                    false
                } else {
                    val children = src.listFiles()
                    if (children == null) {
                        false
                    } else {
                        var allOk = true
                        for (child in children) {
                            if (!copyRaw(child, File(dest, child.name))) { allOk = false; break }
                        }
                        allOk
                    }
                }
            } else {
                copyRaw(src, dest)
            }
        } catch (e: Exception) {
            ok = false
        }
        // Gagal di tengah: dest (nama unik, baru dibuat) dibersihkan supaya
        // tidak tertinggal salinan parsial; sumber tetap utuh di tangan pemanggil.
        if (!ok) deleteRecursively(dest)
        return ok
    }

    private fun copyRaw(src: File, dest: File): Boolean {
        return try {
            if (src.isDirectory) {
                if (!dest.mkdirs() && !dest.isDirectory) return false
                val children = src.listFiles() ?: return false
                for (child in children) {
                    if (!copyRaw(child, File(dest, child.name))) return false
                }
                true
            } else {
                dest.parentFile?.mkdirs()
                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
                }
                dest.setLastModified(src.lastModified())
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Salin banyak entri ke folder tujuan. Mengembalikan jumlah yang berhasil. */
    fun copyInto(sources: List<File>, destDir: File): Int {
        var ok = 0
        for (src in sources) {
            if (src.parentFile?.absolutePath == destDir.absolutePath) continue
            if (copyOne(src, destDir)) ok++
        }
        return ok
    }

    /** Pindah banyak entri ke folder tujuan (rename bila bisa, else salin+hapus). */
    fun moveInto(sources: List<File>, destDir: File): Int {
        var ok = 0
        for (src in sources) {
            if (src.parentFile?.absolutePath == destDir.absolutePath) { ok++; continue }
            val dest = File(destDir, uniqueName(destDir, src.name))
            val moved = try { src.renameTo(dest) } catch (e: Exception) { false }
            if (moved) { ok++; continue }
            if (copyRaw(src, dest)) {
                deleteRecursively(src)
                ok++
            } else {
                // Salinan gagal: sumber TIDAK dihapus (perbaikan B1), dan
                // dest parsial (nama unik, baru dibuat) dibersihkan.
                deleteRecursively(dest)
            }
        }
        return ok
    }

    /** Baca info dari dalam berkas APK (tanpa memasangnya); null bila bukan APK valid. */
    fun apkInfo(context: Context, file: File): ApkInfo? {
        return try {
            val pm = context.packageManager
            val pkg = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS) ?: return null
            val appInfo = pkg.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val label = pm.getApplicationLabel(appInfo).toString()
            ApkInfo(
                appName = label.ifBlank { file.name },
                packageName = pkg.packageName ?: "",
                versionName = pkg.versionName ?: "",
                versionCode = pkg.longVersionCode,
                permissions = pkg.requestedPermissions?.toList() ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    /** M9: skala dimensi ikon APK supaya sisi terpanjang <= [maxDim]
     *  (ikon adaptif bisa 512+; intrinsicWidth/Height yang tidak
     *  dibatasi pernah mengalokasi bitmap raksasa -> OOM). Murni. */
    fun iconBitmapSize(width: Int, height: Int, maxDim: Int = 512): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest <= maxDim) return w to h
        val scale = maxDim.toDouble() / longest.toDouble()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return nw to nh
    }

    /** Ikon aplikasi dari dalam berkas APK sebagai Bitmap; null bila gagal. */
    fun apkIcon(context: Context, file: File): android.graphics.Bitmap? {
        return try {
            val pm = context.packageManager
            val pkg = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            val appInfo = pkg.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val drawable = pm.getApplicationIcon(appInfo)
            // M9: dimensi dibatasi sebelum createBitmap (dulu intrinsic
            // langsung, bisa ribuan px -> OOM pada APK tertentu).
            val (width, height) = iconBitmapSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /** MD5 + SHA-256 sebuah berkas dalam satu kali baca; null bila gagal. */
    /** Info stat mentah dari Os.lstat (tautan simbolik dibaca apa adanya). */
    data class StatInfo(
        val mode: Int, val uid: Int, val gid: Int,
        val atimeSec: Long, val ctimeSec: Long, val ino: Long, val nlink: Long
    )

    /** null bila stat tidak tersedia (entri SAF / jalur virtual / ditolak sistem). */
    fun statInfo(path: String): StatInfo? = try {
        val st = android.system.Os.lstat(path)
        StatInfo(st.st_mode, st.st_uid, st.st_gid, st.st_atime, st.st_ctime, st.st_ino, st.st_nlink)
    } catch (e: Exception) {
        null
    }

    /** Izin gaya ls: "-rwxrwx---(770)" - simbolik + oktal dalam kurung. */
    fun formatPerms(mode: Int): String {
        val jenis = when (mode and 0xF000) {
            0x4000 -> 'd'
            0xA000 -> 'l'
            0x6000 -> 'b'
            0x2000 -> 'c'
            0x1000 -> 'p'
            0xC000 -> 's'
            else -> '-'
        }
        fun tiga(geser: Int, bitKhusus: Int, hurufKhusus: Char): String {
            val bit = (mode shr geser) and 7
            val sb = StringBuilder()
            sb.append(if (bit and 4 != 0) 'r' else '-')
            sb.append(if (bit and 2 != 0) 'w' else '-')
            val x = bit and 1 != 0
            sb.append(
                when {
                    bitKhusus == 0 -> if (x) 'x' else '-'
                    x -> hurufKhusus.lowercaseChar()
                    else -> hurufKhusus.uppercaseChar()
                }
            )
            return sb.toString()
        }
        val oktal = (mode and 0xFFF).toString(8)
        return jenis.toString() +
            tiga(6, (mode shr 11) and 1, 's') +
            tiga(3, (mode shr 10) and 1, 's') +
            tiga(0, (mode shr 9) and 1, 't') +
            "(" + oktal + ")"
    }

    /** Pratinjau izin untuk dialog editor: simbolik tanpa huruf jenis,
     *  spasi, lalu oktal - "rw-rw---- (660)". */
    fun permPreview(permBits: Int): String =
        formatPerms(permBits and 0xFFF).drop(1).replace("(", " (")

    /** Terapkan bit izin (chmod). false bila ditolak sistem
     *  (mis. jalur FUSE / kartu SD yang tidak mendukung chmod). */
    fun chmod(path: String, permBits: Int): Boolean = try {
        android.system.Os.chmod(path, permBits and 0xFFF)
        true
    } catch (e: Exception) {
        false
    }

    /** Nama pemilik dari uid: nama bawaan Android untuk uid sistem,
     * pola "u<user>_a<app>" untuk uid aplikasi, angka mentah sisanya. */
    fun ownerName(uid: Int): String = when (uid) {
        0 -> "root"
        1000 -> "system"
        2000 -> "shell"
        1013 -> "media"
        else -> appIdName(uid) ?: uid.toString()
    }

    /** Nama kelompok dari gid: tabel nama bawaan Android yang umum. */
    fun groupName(gid: Int): String = when (gid) {
        0 -> "root"
        1000 -> "system"
        2000 -> "shell"
        1001 -> "radio"
        1002 -> "bluetooth"
        1007 -> "log"
        1015 -> "sdcard_rw"
        1023 -> "media_rw"
        1028 -> "sdcard_r"
        3003 -> "inet"
        9997 -> "everybody"
        else -> appIdName(gid) ?: gid.toString()
    }

    private fun appIdName(id: Int): String? {
        if (id < 10000) return null
        val user = id / 100000
        val app = id % 100000
        return when {
            app >= 99000 -> "u" + user + "_i" + (app - 99000)
            app >= 10000 -> "u" + user + "_a" + (app - 10000)
            else -> null
        }
    }

    /** Ukuran gaya dialog Properti referensi: "301.92M (316591162)". */
    fun formatSizeExact(bytes: Long): String {
        if (bytes < 1024) return "$bytes B ($bytes)"
        val units = arrayOf("K", "M", "G", "T")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(java.util.Locale.US, "%.2f%s (%d)", value, units[unit], bytes)
    }

    /** Waktu gaya dialog Properti referensi: "2026-11-06 11:45:53". */
    fun formatIsoTime(epochMillis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(epochMillis))

    fun checksums(file: File): Pair<String, String>? {
        return try {
            val md5 = java.security.MessageDigest.getInstance("MD5")
            val sha = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md5.update(buffer, 0, read)
                    sha.update(buffer, 0, read)
                }
            }
            fun hex(bytes: ByteArray): String {
                val sb = StringBuilder(bytes.size * 2)
                for (b in bytes) sb.append(String.format("%02x", b.toInt() and 0xFF))
                return sb.toString()
            }
            hex(md5.digest()) to hex(sha.digest())
        } catch (e: Exception) {
            null
        }
    }

    fun deleteRecursively(file: File): Boolean {
        return try {
            // r175 H1: symlink ke direktori JANGAN diikuti — isDirectory()
            // mengikuti symlink, jadi tanpa cek ini isi folder target ikut
            // terhapus. Hapus link-nya saja.
            val isLink = try { Files.isSymbolicLink(file.toPath()) } catch (e: Exception) { false }
            if (!isLink && file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete() || !file.exists()
        } catch (e: Exception) {
            false
        }
    }

    fun rename(file: File, newName: String): File? {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val parent = file.parentFile ?: return null
        val dest = File(parent, clean)
        if (dest.exists() && dest.absolutePath != file.absolutePath) return null
        return try {
            if (file.renameTo(dest)) dest else null
        } catch (e: Exception) {
            null
        }
    }

    fun newFolder(dir: File, name: String): File? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val dest = File(dir, clean)
        if (dest.exists()) return null
        return if (dest.mkdirs()) dest else null
    }

    /** Membuat file kosong baru di [dir]; nama duplikat otomatis diberi akhiran angka. */
    fun newFile(dir: File, name: String): File? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/') || clean.contains('\\')) return null
        val dest = File(dir, uniqueName(dir, clean))
        return try {
            if (dest.createNewFile()) dest else null
        } catch (e: Exception) {
            null
        }
    }

    /** Pencarian rekursif berdasarkan nama, dibatasi jumlah hasil.
     *  Isi folder terkunci ([locked]) tidak pernah muncul di hasil. */
    fun search(root: File, query: String, showHidden: Boolean, locked: Set<String> = emptySet(), limit: Int = 500): List<FEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty() || !root.isDirectory) return emptyList()
        val results = mutableListOf<FEntry>()
        // L23: guard kunjungan memangkas siklus symlink.
        val guard = newVisitGuard()
        root.walkTopDown()
            .onEnter { dir -> guard(dir) && dir.name != TRASH_DIR_NAME && (dir == root || !isLockedPath(dir.absolutePath, locked)) && (showHidden || !dir.name.startsWith(".") || dir == root) }
            .forEach { f ->
                if (results.size >= limit) return results
                if (f == root) return@forEach
                if (!showHidden && f.name.startsWith(".")) return@forEach
                if (f.name.lowercase().contains(q)) results += entryOf(f)
            }
        return results
    }

    /** File terbaru lintas volume: pindai terbatas, hasil diurutkan dari yang paling baru diubah. */
    fun recentFiles(roots: List<File>, showHidden: Boolean, locked: Set<String> = emptySet(), limit: Int = 12, cap: Int = 8000): List<FEntry> {
        val found = mutableListOf<FEntry>()
        var visited = 0
        for (root in roots) {
            if (!root.isDirectory || visited >= cap) continue
            // L29: for + break — begitu cap tercapai penelusuran
            // BENAR-BENAR berhenti (dulu forEach hanya melewati sisa
            // entri sementara walk tetap menjelajahi seluruh pohon;
            // pola yang sama dengan categoryFiles r128).
            // L23: guard kunjungan memangkas siklus symlink.
            val guard = newVisitGuard()
            for (f in root.walkTopDown()
                .onEnter { dir -> guard(dir) && dir.canRead() && dir.name != TRASH_DIR_NAME && !isLockedPath(dir.absolutePath, locked) && (showHidden || !dir.name.startsWith(".") || dir == root) }) {
                if (visited >= cap) break
                if (f.isFile) {
                    visited++
                    if (showHidden || !f.name.startsWith(".")) found += entryOf(f)
                }
            }
        }
        return found.sortedByDescending { it.modified }.take(limit)
    }

    /** Semua berkas satu kategori lintas volume (untuk menu kategori di Beranda). */
    fun categoryFiles(roots: List<File>, category: Category, showHidden: Boolean, locked: Set<String> = emptySet(), cap: Int = 20000, limit: Int = 1000): List<FEntry> {
        val found = mutableListOf<FEntry>()
        var visited = 0
        for (root in roots) {
            if (!root.isDirectory || visited >= cap) continue
            // r128: for + break — begitu cap tercapai penelusuran BENAR-BENAR
            // berhenti (dulu forEach hanya melewati sisa entri sementara
            // walk tetap menjelajahi seluruh pohon).
            // L23: guard kunjungan memangkas siklus symlink.
            val guard = newVisitGuard()
            for (f in root.walkTopDown()
                .onEnter { dir -> guard(dir) && dir.canRead() && dir.name != TRASH_DIR_NAME && !isLockedPath(dir.absolutePath, locked) && (showHidden || !dir.name.startsWith(".") || dir == root) }) {
                if (visited >= cap) break
                if (f.isFile) {
                    visited++
                    if ((showHidden || !f.name.startsWith(".")) && categoryOf(f.name) == category) {
                        found += entryOf(f)
                    }
                }
            }
        }
        return found.sortedByDescending { it.modified }.take(limit)
    }

    /** Kompres daftar entri menjadi satu berkas ZIP di folder tujuan. */
    fun zipFiles(sources: List<File>, destDir: File, zipName: String): File? {
        val clean = zipName.trim().removeSuffix(".zip")
        if (clean.isEmpty()) return null
        val outFile = File(destDir, uniqueName(destDir, "$clean.zip"))
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
                // r183: BEST_SPEED (level 1) — diukur 2,1x lebih cepat
                // untuk teks (3439 -> 1613 ms / 500 MB) dengan penalti
                // ukuran ~19% pada teks realistis; STORED (di addToZip)
                // menutupi sisanya untuk foto/video.
                zos.setLevel(Deflater.BEST_SPEED)
                for (src in sources) {
                    addToZip(zos, src, src.name)
                }
            }
            outFile
        } catch (e: Exception) {
            outFile.delete()
            null
        }
    }

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { addToZip(zos, it, "$entryName/${it.name}") }
            return
        }
        if (isIncompressibleEntry(file.name)) {
            // r183: STORED — CRC + ukuran wajib diisi SEBELUM
            // putNextEntry (aturan yang sama seperti replaceZipEntry);
            // menghemat CPU tanpa membesarkan ukuran (diukur: zip
            // campuran 200 MB 5560 -> 442 ms).
            val crc = CRC32()
            val crcBuf = ByteArray(256 * 1024)
            BufferedInputStream(FileInputStream(file)).use { input ->
                var n = input.read(crcBuf)
                while (n >= 0) {
                    crc.update(crcBuf, 0, n)
                    n = input.read(crcBuf)
                }
            }
            val stored = ZipEntry(entryName)
            stored.time = file.lastModified()
            stored.method = ZipEntry.STORED
            stored.size = file.length()
            stored.compressedSize = file.length()
            stored.crc = crc.value
            zos.putNextEntry(stored)
            BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zos, 64 * 1024) }
            zos.closeEntry()
            return
        }
        val entry = ZipEntry(entryName)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zos, 64 * 1024) }
        zos.closeEntry()
    }

    /** Ekstrak berkas ZIP ke folder tujuan (dengan penjaga zip-slip + batas ekstraksi).
     *  Gagal di tengah (entri jahat / batas terlampaui / I/O): kembalikan false dan
     *  hapus berkas yang sudah tertulis selama pemanggilan ini (E3) — kecuali yang
     *  memang sudah ada sebelum ekstraksi (tidak dihapus). */
    /** Ekstrak ZIP (dipakai ArchiveOps.extract + EpubOps). LD3 r160:
     *  [onEntry] dipanggil per entri yang berhasil diekstrak;
     *  [cancelFlag] yang terpasang membatalkan lewat
     *  ImgCancelledException — berkas yang sudah tertulis dibersihkan
     *  sebelum throw, supaya pemanggil membedakan batal dari gagal. */
    fun unzip(
        zip: File,
        destDir: File,
        onEntry: () -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        var ok = true
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { it.delete() }
            deleteCreatedDirs(createdDirs)
        }
        try {
            val destCanonical = destDir.canonicalPath
            val budget = ExtractBudget()
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (cancelFlag?.get() == true) throw ImgCancelledException()
                    if (!budget.allowEntry()) { ok = false; break }
                    // Nama entri absolut bukan path relatif yang valid — tolak.
                    // (File(dest, "/abs/...") diam-diam merelatifkannya ke dalam
                    // dest, jadi penjaga isPathInside tidak melihatnya lolos.)
                    if (entry.name.startsWith("/") || entry.name.startsWith("\\")
                        || (entry.name.length >= 2 && entry.name[1] == ':')
                    ) { ok = false; break }
                    val outFile = File(destDir, entry.name)
                    if (!isPathInside(destCanonical, outFile.canonicalPath)) { ok = false; break }
                    if (entry.isDirectory) {
                        noteCreatedDir(outFile, destCanonical, createdDirs)
                        outFile.mkdirs()
                        // L1: validasi ulang canonical SETELAH mkdirs —
                        // symlink di dest yang ditukar di antara cek dan
                        // tulis (TOCTOU) tertangkap di sini.
                        val dirCanon = try { outFile.canonicalPath } catch (e: Exception) { null }
                        if (dirCanon == null || !isPathInside(destCanonical, dirCanon)) { ok = false; break }
                    } else {
                        val existed = outFile.exists()
                        noteCreatedDir(outFile.parentFile, destCanonical, createdDirs)
                        outFile.parentFile?.mkdirs()
                        // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                        val parentCanon = try { outFile.parentFile?.canonicalPath } catch (e: Exception) { null }
                        if (parentCanon == null || !isPathInside(destCanonical, parentCanon)) { ok = false; break }
                        // M1: tolak entri yang ukuran deklarasinya sudah
                        // melewati sisa anggaran SEBELUM membuka output;
                        // stream juga berhenti di tengah copy bila anggaran
                        // terlampaui (dulu seluruh entri ditulis dulu baru
                        // anggaran diperiksa).
                        if (entry.size >= 0L && entry.size > budget.remaining()) { ok = false; break }
                        if (!existed) written += outFile
                        val counting = CountingOutputStream(FileOutputStream(outFile), budget.remaining())
                        counting.use { out -> zis.copyTo(out, 64 * 1024) }
                        if (!budget.addBytes(counting.count)) { ok = false; break }
                    }
                    zis.closeEntry()
                    onEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) cleanup()
        return ok
    }

    /** Pindai satu volume: ukuran per kategori + file terbesar. */
    fun analyze(root: File, locked: Set<String> = emptySet()): AnalysisResult {
        val sizes = mutableMapOf<Category, Long>()
        val counts = mutableMapOf<Category, Int>()
        val largest = mutableListOf<FEntry>()
        var total = 0
        // L23: guard kunjungan memangkas siklus symlink.
        val guard = newVisitGuard()
        root.walkTopDown()
            .onEnter { dir -> guard(dir) && dir.canRead() && dir.name != TRASH_DIR_NAME && !isLockedPath(dir.absolutePath, locked) }
            .forEach { f ->
                if (!f.isFile) return@forEach
                total++
                val cat = categoryOf(f.name)
                sizes[cat] = (sizes[cat] ?: 0L) + f.length()
                counts[cat] = (counts[cat] ?: 0) + 1
                if (f.length() > 0) {
                    largest += entryOf(f)
                    if (largest.size > 40) {
                        largest.sortByDescending { it.size }
                        while (largest.size > 25) largest.removeAt(largest.size - 1)
                    }
                }
            }
        largest.sortByDescending { it.size }
        return AnalysisResult(sizes, counts, largest.take(20), total)
    }

    /** "1.5 GB", "230 MB", dst. */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /** Durasi m:ss (atau h:mm:ss bila >= 1 jam) untuk pemutar media. */
    fun formatDuration(ms: Int): String {
        val totalSec = (ms.coerceAtLeast(0) / 1000)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }
}

/** Anggaran ekstraksi arsip (C2): membatasi jumlah entri dan total byte yang ditulis,
 *  supaya arsip jahat (zip-bomb) tidak memenuhi penyimpanan. Internal agar bisa
 *  diuji langsung tanpa I/O. */
internal class ExtractBudget {
    var entries: Int = 0
        private set
    var bytes: Long = 0L
        private set

    /** Catat satu entri; false bila jumlah entri melewati batas. */
    fun allowEntry(): Boolean {
        entries++
        return entries <= FileOps.MAX_ARCHIVE_ENTRIES
    }

    /** Catat [n] byte yang ditulis; false bila total melewati batas. */
    fun addBytes(n: Long): Boolean {
        bytes += n
        return bytes <= FileOps.MAX_EXTRACT_BYTES
    }

    /** Sisa byte yang masih boleh ditulis sebelum mencapai batas (M1). */
    fun remaining(): Long = (FileOps.MAX_EXTRACT_BYTES - bytes).coerceAtLeast(0L)
}

/** Dilempar CountingOutputStream bila byte tertulis melewati batas anggaran
 *  (M1): ekstraktor membatalkan copy di tengah jalan daripada menulis
 *  seluruh entri baru memeriksa anggaran. */
internal class BudgetExceededException : Exception()

/** OutputStream yang menghitung byte tertulis (untuk anggaran ekstraksi).
 *  Bila [limit] terpasang, write yang melewatinya melempar
 *  [BudgetExceededException] — pemanggil membersihkan berkas sebagian. */
internal class CountingOutputStream(out: java.io.OutputStream, private val limit: Long = Long.MAX_VALUE) : java.io.FilterOutputStream(out) {
    var count = 0L
    override fun write(b: Int) {
        if (count + 1 > limit) throw BudgetExceededException()
        super.write(b)
        count++
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (count + len > limit) throw BudgetExceededException()
        // Write straight to the wrapped stream: FilterOutputStream.write(b, off, len)
        // loops write(Int), which would count every byte a second time.
        out.write(b, off, len)
        count += len
    }
}

/** Mesin arsip tambahan: TAR (Commons Compress), 7Z ekstrak, ZIP terenkripsi (zip4j). */
object ArchiveOps {

    /** r183: buffer copy untuk ekstrak tar / buat tar / ekstrak zip
     *  terenkripsi — diukur vs 8 KiB: tar ekstrak 875 -> 584 ms,
     *  tar buat 736 -> 558 ms; zip biasa tidak berubah (inflate-bound). */
    internal const val ARCHIVE_COPY_BUFFER_SIZE = 256 * 1024

    /** r183: buffer baca 7z — diukur 945 -> 495 ms vs 8 KiB; 256 KiB
     *  tidak lebih cepat dari 64 KiB. */
    internal const val SEVENZ_READ_BUFFER_SIZE = 64 * 1024

    /** Jenis arsip dari nama berkas, mis. "tar"; null bila bukan yang didukung. */
    fun kindOf(name: String): String? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar") -> "tar"
            n.endsWith(".zip") -> "zip"
            n.endsWith(".7z") -> "7z"
            n.endsWith(".rar") -> "rar"
            n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".aab") -> "zip"
            // OBB expansion file adalah kontainer ZIP (preseden yang sama seperti APK).
            n.endsWith(".obb") -> "zip"
            // JAR adalah kontainer ZIP (preseden yang sama seperti APK/OBB).
            n.endsWith(".jar") -> "zip"
            else -> null
        }
    }

    /** Ekstrak arsip apa pun yang didukung ke [destDir]; false bila gagal/tidak didukung. */
    /** Ekstrak semua isi arsip (dipakai ArchiveScreen + "Ekstrak Di
     *  Sini"). LD3 r160: [onProgress] menerima (entri selesai, total
     *  entri); [cancelFlag] yang terpasang membatalkan lewat
     *  ImgCancelledException (berkas yang sudah tertulis dibersihkan
     *  di dalam extractor). Total dihitung dulu lewat listContents —
     *  untuk tar/7z/rar itu satu kali pindai penuh sebelum ekstrak;
     *  bila daftar isi gagal dibaca, total 0 (bar tak tentu). */
    fun extract(
        file: File,
        destDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val total = listContents(file)?.size?.toLong() ?: 0L
        var done = 0L
        val onEntry: () -> Unit = { done++; onProgress(done, total) }
        return try {
            when (kindOf(file.name)) {
                "zip" -> FileOps.unzip(file, destDir, onEntry, cancelFlag)
                "7z" -> extract7z(file, destDir, onEntry, cancelFlag)
                "tar" -> extractTar(file, destDir, onEntry, cancelFlag)
                "rar" -> extractRar(file, destDir, onEntry, cancelFlag)
                else -> false
            }
        } catch (e: ImgCancelledException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Buat arsip [kind] ("zip"/"tar"); kata sandi hanya untuk ZIP. */
    fun create(sources: List<File>, destDir: File, baseName: String, kind: String, password: String?): File? {
        return try {
            when (kind) {
                "zip" -> if (password.isNullOrBlank()) FileOps.zipFiles(sources, destDir, baseName)
                         else createEncryptedZip(sources, destDir, baseName, password)
                "tar" -> createTar(sources, destDir, baseName, kind)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createTar(sources: List<File>, destDir: File, baseName: String, kind: String): File? {
        val clean = baseName.trim().trimEnd('.')
        if (clean.isBlank()) return null
        val dest = uniqueDest(destDir, "$clean.$kind")
        // L4: hapus output sebagian bila gagal (dulu tertinggal, tidak
        // seperti zipFiles); L29: raw ditutup bila konstruktor wrapper
        // melempar (dulu bocor).
        val raw = try {
            BufferedOutputStream(FileOutputStream(dest))
        } catch (e: Exception) {
            return null
        }
        return try {
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(raw).use { tos ->
                tos.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX)
                for (src in sources) addToTar(tos, src, src.name)
            }
            dest
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) { }
            dest.delete()
            null
        }
    }

    private fun addToTar(
        tos: org.apache.commons.compress.archivers.tar.TarArchiveOutputStream,
        file: File,
        entryName: String
    ) {
        if (file.isDirectory) {
            val dirEntry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(file, entryName)
            tos.putArchiveEntry(dirEntry)
            tos.closeArchiveEntry()
            file.listFiles()?.forEach { addToTar(tos, it, "$entryName/${it.name}") }
        } else {
            val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(file, entryName)
            tos.putArchiveEntry(entry)
            FileInputStream(file).use { it.copyTo(tos, ARCHIVE_COPY_BUFFER_SIZE) }
            tos.closeArchiveEntry()
        }
    }

    /** LD3 r160: seperti unzip — [onEntry] per entri, [cancelFlag]
     *  membatalkan lewat ImgCancelledException; berkas yang sudah
     *  tertulis dilacak dan dibersihkan saat batal/gagal. */
    private fun extractTar(
        archive: File,
        destDir: File,
        onEntry: () -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val destPath = destDir.canonicalPath
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        var ok = true
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        return try {
            val raw = BufferedInputStream(FileInputStream(archive))
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(raw).use { tis ->
                while (true) {
                    if (cancelFlag?.get() == true) throw ImgCancelledException()
                    val entry = tis.nextEntry ?: break
                    if (!budget.allowEntry()) { ok = false; break }
                    val out = File(destDir, entry.name)
                    if (!FileOps.isPathInside(destPath, out.canonicalPath)) { ok = false; break }
                    // L1: entri symlink/hardlink ditolak eksplisit — dulu
                    // dimaterialisasi sebagai berkas biasa berisi target link.
                    if (entry.isLink) { ok = false; break }
                    if (entry.isDirectory) {
                        FileOps.noteCreatedDir(out, destPath, createdDirs)
                        out.mkdirs()
                        // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                        val dirCanon = try { out.canonicalPath } catch (e: Exception) { null }
                        if (dirCanon == null || !FileOps.isPathInside(destPath, dirCanon)) { ok = false; break }
                    } else {
                        val existed = out.exists()
                        FileOps.noteCreatedDir(out.parentFile, destPath, createdDirs)
                        out.parentFile?.mkdirs()
                        // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                        val parentCanon = try { out.parentFile?.canonicalPath } catch (e: Exception) { null }
                        if (parentCanon == null || !FileOps.isPathInside(destPath, parentCanon)) { ok = false; break }
                        // M1: cek ukuran deklarasi dulu; stream berhenti di
                        // tengah copy bila anggaran terlampaui.
                        if (entry.size >= 0L && entry.size > budget.remaining()) { ok = false; break }
                        if (!existed) written += out
                        val counting = CountingOutputStream(FileOutputStream(out), budget.remaining())
                        counting.use { tis.copyTo(it, ARCHIVE_COPY_BUFFER_SIZE) }
                        if (!budget.addBytes(counting.count)) { ok = false; break }
                    }
                    onEntry()
                }
            }
            if (!ok) cleanup()
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /** LD3 r160: seperti unzip — [onEntry] per entri, [cancelFlag]
     *  membatalkan lewat ImgCancelledException; berkas yang sudah
     *  tertulis dilacak dan dibersihkan saat batal/gagal. */
    private fun extract7z(
        archive: File,
        destDir: File,
        onEntry: () -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val destPath = destDir.canonicalPath
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        var ok = true
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        return try {
            org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
                while (true) {
                    if (cancelFlag?.get() == true) throw ImgCancelledException()
                    val entry = seven.nextEntry ?: break
                    if (!budget.allowEntry()) { ok = false; break }
                    val out = File(destDir, entry.name)
                    if (!FileOps.isPathInside(destPath, out.canonicalPath)) { ok = false; break }
                    if (entry.isDirectory) {
                        FileOps.noteCreatedDir(out, destPath, createdDirs)
                        out.mkdirs()
                        // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                        val dirCanon = try { out.canonicalPath } catch (e: Exception) { null }
                        if (dirCanon == null || !FileOps.isPathInside(destPath, dirCanon)) { ok = false; break }
                    } else {
                        val existed = out.exists()
                        FileOps.noteCreatedDir(out.parentFile, destPath, createdDirs)
                        out.parentFile?.mkdirs()
                        // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                        val parentCanon = try { out.parentFile?.canonicalPath } catch (e: Exception) { null }
                        if (parentCanon == null || !FileOps.isPathInside(destPath, parentCanon)) { ok = false; break }
                        // M1: cek ukuran deklarasi dulu; stream berhenti di
                        // tengah copy bila anggaran terlampaui.
                        if (entry.size >= 0L && entry.size > budget.remaining()) { ok = false; break }
                        if (!existed) written += out
                        val counting = CountingOutputStream(FileOutputStream(out), budget.remaining())
                        counting.use { cos ->
                            val buffer = ByteArray(SEVENZ_READ_BUFFER_SIZE)
                            var remaining = entry.size
                            while (remaining > 0) {
                                if (cancelFlag?.get() == true) throw ImgCancelledException()
                                val read = seven.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (read <= 0) break
                                cos.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                        if (!budget.addBytes(counting.count)) { ok = false; break }
                    }
                    onEntry()
                }
            }
            if (!ok) cleanup()
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /** Ekstrak arsip RAR (junrar) dengan penjaga path-slip; arsip terenkripsi butuh kata sandi (lihat extractEncryptedRar). */
    /** LD3 r160: seperti unzip — [onEntry] per entri, [cancelFlag]
     *  membatalkan lewat ImgCancelledException; berkas yang sudah
     *  tertulis dilacak dan dibersihkan saat batal/gagal. */
    private fun extractRar(
        archive: File,
        destDir: File,
        onEntry: () -> Unit = {},
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val destPath = destDir.canonicalPath
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        var ok = true
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        return try {
            com.github.junrar.Archive(archive).use { rar ->
                while (true) {
                    if (cancelFlag?.get() == true) throw ImgCancelledException()
                    val header = rar.nextFileHeader() ?: break
                    val name = header.fileName.replace('\\', '/')
                    if (name.isBlank()) continue
                    if (!budget.allowEntry()) { ok = false; break }
                    val out = File(destDir, name)
                    if (!FileOps.isPathInside(destPath, out.canonicalPath)) { ok = false; break }
                    if (header.isDirectory) {
                        FileOps.noteCreatedDir(out, destPath, createdDirs)
                        out.mkdirs()
                        // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                        val dirCanon = try { out.canonicalPath } catch (e: Exception) { null }
                        if (dirCanon == null || !FileOps.isPathInside(destPath, dirCanon)) { ok = false; break }
                    } else {
                        val existed = out.exists()
                        FileOps.noteCreatedDir(out.parentFile, destPath, createdDirs)
                        out.parentFile?.mkdirs()
                        // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                        val parentCanon = try { out.parentFile?.canonicalPath } catch (e: Exception) { null }
                        if (parentCanon == null || !FileOps.isPathInside(destPath, parentCanon)) { ok = false; break }
                        // M1: cek ukuran deklarasi dulu; stream berhenti di
                        // tengah copy bila anggaran terlampaui.
                        if (header.unpSize >= 0L && header.unpSize > budget.remaining()) { ok = false; break }
                        if (!existed) written += out
                        val counting = CountingOutputStream(FileOutputStream(out), budget.remaining())
                        counting.use { fos -> rar.extractFile(header, fos) }
                        if (!budget.addBytes(counting.count)) { ok = false; break }
                    }
                    onEntry()
                }
            }
            if (!ok) cleanup()
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            false
        }
    }


    /** Daftar isi berkas ZIP/APK (nama + ukuran) untuk Inspeksi; null bila bukan ZIP valid
     *  atau jumlah entrinya melewati batas (C2 — jangan muat arsip raksasa ke memori). */
    fun listZipContents(file: File): List<Pair<String, Long>>? {
        return try {
            ZipFile(file).use { zip ->
                val result = mutableListOf<Pair<String, Long>>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (result.size >= FileOps.MAX_ARCHIVE_ENTRIES) return null
                    result += e.name to if (e.isDirectory) -1L else e.size
                }
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Daftar isi arsip (ZIP/7Z/TAR/RAR) untuk dibuka tanpa ekstrak; null bila gagal/tidak didukung. */
    /** Baca teks berkas dengan cap byte (M8). Null bila berkas hilang,
     *  melebihi [maxBytes], atau gagal dibaca — tanpa memuat isi utuh
     *  ke memori dulu. */
    fun readTextCapped(file: File, maxBytes: Long): String? {
        if (!file.isFile || file.length() > maxBytes) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Baca satu entri teks dari arsip ZIP ke memori (tanpa menulis ke disk);
     *  null bila bukan ZIP, entri tidak ada/direktori, terlalu besar, atau gagal.
     *  Arsip selain ZIP mengembalikan null — pemanggil memakai jalur cache. */
    fun readEntryText(file: File, entryName: String, maxBytes: Int = 1_000_000): String? {
        if (kindOf(file.name) != "zip") return null
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                if (entry.isDirectory) return null
                if (entry.size > maxBytes) return null
                zip.getInputStream(entry).use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return null
                        out.write(buf, 0, n)
                    }
                    String(out.toByteArray(), Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True bila entri teks arsip ini bisa ditulis kembali ke arsipnya
     *  (AT1): hanya berkas .zip biasa yang tidak terenkripsi. APK/OBB/
     *  JAR sengaja dikecualikan — menulis ulang akan merusak tanda
     *  tangannya; arsip terenkripsi tidak bisa dibaca java.util.zip. */
    fun canReplaceEntry(archive: File): Boolean {
        return archive.name.lowercase().endsWith(".zip") && !isZipEncrypted(archive)
    }

    /** Ganti isi satu entri teks di dalam arsip ZIP dengan [newText]
     *  (AT1). Arsip ditulis ulang ke berkas temp di folder yang sama,
     *  lalu ditukar secara aman (cadangan dulu, kembalikan bila gagal)
     *  — arsip asli tidak berubah bila ada kegagalan. Entri lain
     *  disalin apa adanya (nama, waktu, metode STORED/DEFLATED, dan
     *  CRC untuk STORED). False bila bukan .zip, terenkripsi, entri
     *  tidak ditemukan atau direktori, atau ada kegagalan I/O. */
    fun replaceZipEntry(archive: File, entryName: String, newText: String): Boolean {
        if (!canReplaceEntry(archive)) return false
        val newBytes = newText.toByteArray(Charsets.UTF_8)
        val parent = archive.parentFile ?: return false
        val tmp = File(parent, "${archive.name}.tmp-${System.nanoTime()}")
        val backup = File(parent, "${archive.name}.bak-${System.nanoTime()}")
        var found = false
        var count = 0
        try {
            ZipFile(archive).use { zip ->
                // L5: total byte rewrite dicap (2 GiB) — dulu entri
                // zip-bomb mengembang penuh ke temp selama rewrite.
                val counting = CountingOutputStream(BufferedOutputStream(FileOutputStream(tmp)), FileOps.MAX_EXTRACT_BYTES)
                ZipOutputStream(counting).use { zos ->
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) {
                        val entry = enumeration.nextElement()
                        count++
                        if (count > FileOps.MAX_ARCHIVE_ENTRIES) return false
                        val isTarget = entry.name == entryName
                        if (isTarget) {
                            if (entry.isDirectory) return false
                            found = true
                        }
                        val out = ZipEntry(entry.name)
                        if (isTarget) out.time = System.currentTimeMillis()
                        else if (entry.time > 0) out.time = entry.time
                        if (entry.method == ZipEntry.STORED) {
                            out.method = ZipEntry.STORED
                            if (isTarget) {
                                val crc = CRC32()
                                crc.update(newBytes)
                                out.size = newBytes.size.toLong()
                                out.crc = crc.value
                            } else {
                                out.size = entry.size
                                out.crc = entry.crc
                            }
                        }
                        zos.putNextEntry(out)
                        if (!entry.isDirectory) {
                            if (isTarget) {
                                zos.write(newBytes)
                            } else {
                                zip.getInputStream(entry).use { it.copyTo(zos, 64 * 1024) }
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
            if (!found) return false
            // Verifikasi ringan sebelum tukar: temp masih ZIP valid,
            // jumlah entri sama, dan entri target berisi teks baru.
            val ok = try {
                ZipFile(tmp).use { zip ->
                    val names = mutableListOf<String>()
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) names += enumeration.nextElement().name
                    names.size == count && names.count { it == entryName } == 1 &&
                        zip.getInputStream(zip.getEntry(entryName)).use { it.readBytes() }
                            .contentEquals(newBytes)
                }
            } catch (e: Exception) {
                false
            }
            if (!ok) return false
            if (!archive.renameTo(backup)) return false
            if (!tmp.renameTo(archive)) {
                backup.renameTo(archive)
                return false
            }
            backup.delete()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            tmp.delete()
            if (backup.exists() && !archive.exists()) backup.renameTo(archive)
        }
    }

    fun listContents(file: File): List<ArchiveItem>? {
        return try {
            when (kindOf(file.name)) {
                "zip" -> listZipContents(file)?.map { (name, size) ->
                    ArchiveItem(name, size < 0L, if (size < 0L) 0L else size)
                }
                "7z" -> list7z(file)
                "tar" -> listTar(file)
                "rar" -> listRar(file)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }



    private fun list7z(archive: File): List<ArchiveItem>? {
        val result = mutableListOf<ArchiveItem>()
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
            while (true) {
                val e = seven.nextEntry ?: break
                if (result.size >= FileOps.MAX_ARCHIVE_ENTRIES) return null
                result += ArchiveItem(
                    e.name.replace('\\', '/'),
                    e.isDirectory,
                    if (e.isDirectory) 0L else e.size
                )
            }
        }
        return result
    }

    private fun openTar(
        archive: File
    ): org.apache.commons.compress.archivers.tar.TarArchiveInputStream {
        // L29: stream mentah ditutup bila konstruktor wrapper melempar
        // (dulu bocor).
        val raw = BufferedInputStream(FileInputStream(archive))
        return try {
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(raw)
        } catch (e: Exception) {
            try { raw.close() } catch (_: Exception) { }
            throw e
        }
    }

    private fun listTar(archive: File): List<ArchiveItem>? {
        val result = mutableListOf<ArchiveItem>()
        openTar(archive).use { tis ->
            while (true) {
                val e = tis.nextEntry ?: break
                if (result.size >= FileOps.MAX_ARCHIVE_ENTRIES) return null
                result += ArchiveItem(e.name, e.isDirectory, if (e.isDirectory) 0L else e.size)
            }
        }
        return result
    }

    private fun listRar(archive: File): List<ArchiveItem>? {
        val result = mutableListOf<ArchiveItem>()
        com.github.junrar.Archive(archive).use { rar ->
            while (true) {
                val header = rar.nextFileHeader() ?: break
                val name = header.fileName.replace('\\', '/')
                if (name.isBlank()) continue
                if (result.size >= FileOps.MAX_ARCHIVE_ENTRIES) return null
                result += ArchiveItem(name, header.isDirectory, if (header.isDirectory) 0L else header.unpSize)
            }
        }
        return result
    }

    /** Nama folder hasil "Ekstrak Di Sini": nama arsip tanpa akhiran arsipnya. */
    fun archiveBaseName(name: String): String {
        val lower = name.lowercase()
        val suffixes = listOf(
            ".zip", ".7z", ".rar", ".tar",
            ".apk", ".apks", ".xapk", ".obb", ".jar"
        )
        for (suffix in suffixes) {
            if (lower.endsWith(suffix)) {
                val base = name.dropLast(suffix.length)
                return base.ifBlank { name }
            }
        }
        return name
    }

    /** Ekstrak arsip ke folder BARU bernama seperti arsip di dalam [destParent].
     *  r182: [onProgress] menerima (entri selesai, total entri) dan
     *  [cancelFlag] membatalkan lewat ImgCancelledException — diteruskan
     *  ke [extract] supaya ekstrak biasa juga berprogres + bisa dibatalkan. */
    fun extractToFolder(
        file: File,
        destParent: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val dir = File(destParent, FileOps.uniqueName(destParent, archiveBaseName(file.name)))
        if (!dir.mkdirs() && !dir.isDirectory) return false
        return extract(file, dir, onProgress, cancelFlag)
    }

    /** Ekstrak SATU entri [entryName] dari arsip ke [destDir]; berkas hasil atau null.
     *  H7: entri dibatasi [maxBytes] (default 2 GiB) — ukuran deklarasi
     *  diperiksa dulu, dan copy berhenti di tengah bila cap terlampaui;
     *  berkas sebagian dihapus. */
    fun extractSingle(file: File, entryName: String, destDir: File, maxBytes: Long = FileOps.MAX_EXTRACT_BYTES): File? {
        val base = entryName.substringAfterLast('/')
        if (base.isBlank()) return null
        val out = uniqueDest(destDir, base)
        val ok = try {
            when (kindOf(file.name)) {
                "zip" -> extractSingleZip(file, entryName, out, maxBytes)
                "7z" -> extractSingle7z(file, entryName, out, maxBytes)
                "tar" -> extractSingleTar(file, entryName, out, maxBytes)
                "rar" -> extractSingleRar(file, entryName, out, maxBytes)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
        return if (ok) out else {
            out.delete()
            null
        }
    }

    private fun extractSingleZip(file: File, entryName: String, out: File, maxBytes: Long): Boolean {
        ZipFile(file).use { zip ->
            val e = zip.getEntry(entryName) ?: return false
            if (e.isDirectory) return false
            // H7: tolak sebelum membaca bila ukuran deklarasi sudah
            // melewati cap; stream berhenti di tengah copy bila cap
            // terlampaui (dulu entri zip-bomb ditulis penuh lalu sukses).
            if (e.size >= 0L && e.size > maxBytes) return false
            zip.getInputStream(e).use { input ->
                val counting = CountingOutputStream(FileOutputStream(out), maxBytes)
                counting.use { input.copyTo(it, 64 * 1024) }
            }
        }
        return true
    }

    private fun extractSingle7z(archive: File, entryName: String, out: File, maxBytes: Long): Boolean {
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
            // L2: batasi jumlah entri yang dipindai — dulu tar/7z/rar
            // dengan jutaan entri sebelum target dipindai tanpa batas.
            var scanned = 0
            while (true) {
                val e = seven.nextEntry ?: break
                if (++scanned > FileOps.MAX_ARCHIVE_ENTRIES) return false
                if (e.isDirectory || e.name.replace('\\', '/') != entryName) continue
                if (e.size >= 0L && e.size > maxBytes) return false
                val counting = CountingOutputStream(FileOutputStream(out), maxBytes)
                counting.use { cos ->
                    val buffer = ByteArray(SEVENZ_READ_BUFFER_SIZE)
                    var remaining = e.size
                    while (remaining > 0) {
                        val read = seven.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        cos.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                return true
            }
        }
        return false
    }

    private fun extractSingleTar(archive: File, entryName: String, out: File, maxBytes: Long): Boolean {
        openTar(archive).use { tis ->
            // L2: batasi jumlah entri yang dipindai (lihat extractSingle7z).
            var scanned = 0
            while (true) {
                val e = tis.nextEntry ?: break
                if (++scanned > FileOps.MAX_ARCHIVE_ENTRIES) return false
                if (e.isDirectory || e.name != entryName) continue
                if (e.size >= 0L && e.size > maxBytes) return false
                val counting = CountingOutputStream(FileOutputStream(out), maxBytes)
                counting.use { cos -> tis.copyTo(cos, 64 * 1024) }
                return true
            }
        }
        return false
    }

    private fun extractSingleRar(archive: File, entryName: String, out: File, maxBytes: Long): Boolean {
        com.github.junrar.Archive(archive).use { rar ->
            // L2: batasi jumlah entri yang dipindai (lihat extractSingle7z).
            var scanned = 0
            while (true) {
                val header = rar.nextFileHeader() ?: break
                if (++scanned > FileOps.MAX_ARCHIVE_ENTRIES) return false
                if (header.isDirectory) continue
                if (header.fileName.replace('\\', '/') != entryName) continue
                if (header.unpSize >= 0L && header.unpSize > maxBytes) return false
                val counting = CountingOutputStream(FileOutputStream(out), maxBytes)
                counting.use { fos -> rar.extractFile(header, fos) }
                return true
            }
        }
        return false
    }

    private fun createEncryptedZip(sources: List<File>, destDir: File, baseName: String, password: String): File? {
        val clean = baseName.trim().trimEnd('.')
        if (clean.isBlank()) return null
        val dest = uniqueDest(destDir, "$clean.zip")
        // L4: hapus output sebagian bila gagal (dulu tertinggal).
        return try {
            val zipFile = net.lingala.zip4j.ZipFile(dest, password.toCharArray())
            // r183: params dibuat per berkas (zip4j tidak punya clone())
            // — DEFLATE level FASTEST, STORED untuk ekstensi tak
            // terkompresi (diukur: zip terenkripsi campuran 200 MB
            // 5864 -> 1106 ms). Struktur folder dipertahankan lewat
            // fileNameInZip + entri direktori eksplisit (diuji).
            fun paramsFor(store: Boolean) = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                compressionMethod = if (store) net.lingala.zip4j.model.enums.CompressionMethod.STORE
                    else net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
                compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
            }
            fun addEncrypted(src: File, zipName: String) {
                if (src.isDirectory) {
                    val dirParams = paramsFor(true).apply { fileNameInZip = "$zipName/" }
                    zipFile.addStream(java.io.ByteArrayInputStream(ByteArray(0)), dirParams)
                    src.listFiles()?.forEach { addEncrypted(it, "$zipName/${it.name}") }
                } else {
                    val p = paramsFor(FileOps.isIncompressibleEntry(src.name))
                    p.fileNameInZip = zipName
                    zipFile.addFile(src, p)
                }
            }
            for (src in sources) addEncrypted(src, src.name)
            dest
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    fun isZipEncrypted(file: File): Boolean = try {
        net.lingala.zip4j.ZipFile(file).isEncrypted
    } catch (e: Exception) {
        false
    }

    /** True bila arsip RAR dilindungi kata sandi (junrar); false bila gagal diperiksa. */
    fun isRarEncrypted(file: File): Boolean = try {
        com.github.junrar.Archive(file).use { it.isPasswordProtected() }
    } catch (e: Exception) {
        false
    }

    /** Ekstrak arsip RAR terenkripsi (junrar) dengan kata sandi; false bila sandi salah/gagal.
     *  M2: seperti unzip — lacak berkas tertulis (kecuali yang sudah ada),
     *  bersihkan bila gagal/batal, dan bisa dibatalkan lewat [cancelFlag];
     *  [onProgress] melaporkan (entri selesai, total entri). */
    fun extractEncryptedRar(
        file: File,
        destDir: File,
        password: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val destPath = destDir.canonicalPath
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        var ok = true
        // Total entri untuk progres (daftar header tanpa kata sandi);
        // 0 bila tidak bisa dibaca — bar tak tentu seperti extract().
        val total = try { listRar(file)?.size?.toLong() ?: 0L } catch (e: Exception) { 0L }
        var done = 0L
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        return try {
            com.github.junrar.Archive(file, password).use { rar ->
                while (true) {
                    if (cancelFlag?.get() == true) throw ImgCancelledException()
                    val header = rar.nextFileHeader() ?: break
                    val name = header.fileName.replace('\\', '/')
                    if (name.isBlank()) continue
                    if (!budget.allowEntry()) { ok = false; break }
                    val out = File(destDir, name)
                    if (!FileOps.isPathInside(destPath, out.canonicalPath)) { ok = false; break }
                    if (header.isDirectory) {
                        FileOps.noteCreatedDir(out, destPath, createdDirs)
                        out.mkdirs()
                        // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                        val dirCanon = try { out.canonicalPath } catch (e: Exception) { null }
                        if (dirCanon == null || !FileOps.isPathInside(destPath, dirCanon)) { ok = false; break }
                    } else {
                        val existed = out.exists()
                        FileOps.noteCreatedDir(out.parentFile, destPath, createdDirs)
                        out.parentFile?.mkdirs()
                        // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                        val parentCanon = try { out.parentFile?.canonicalPath } catch (e: Exception) { null }
                        if (parentCanon == null || !FileOps.isPathInside(destPath, parentCanon)) { ok = false; break }
                        if (header.unpSize >= 0L && header.unpSize > budget.remaining()) { ok = false; break }
                        if (!existed) written += out
                        val counting = CountingOutputStream(FileOutputStream(out), budget.remaining())
                        counting.use { fos -> rar.extractFile(header, fos) }
                        if (!budget.addBytes(counting.count)) { ok = false; break }
                    }
                    done++
                    onProgress(done, total)
                }
            }
            if (!ok) cleanup()
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /** Ekstrak arsip ZIP terenkripsi (zip4j) dengan kata sandi; false bila sandi salah/gagal.
     *  M2: seperti unzip — lacak berkas tertulis (kecuali yang sudah ada),
     *  bersihkan bila gagal/batal, dan bisa dibatalkan lewat [cancelFlag];
     *  [onProgress] melaporkan (entri selesai, total entri). */
    fun extractEncryptedZip(
        file: File,
        destDir: File,
        password: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        cancelFlag: AtomicBoolean? = null
    ): Boolean {
        val destPath = destDir.canonicalPath
        val budget = ExtractBudget()
        val written = mutableListOf<File>()
        // L7: direktori yang dibuat selama ekstraksi ini (dihapus bila gagal).
        val createdDirs = mutableListOf<File>()
        var ok = true
        // L7: bersihkan berkas + direktori yang dibuat selama ekstraksi ini.
        fun cleanup() {
            written.forEach { file -> file.delete() }
            FileOps.deleteCreatedDirs(createdDirs)
        }
        return try {
            val zipFile = net.lingala.zip4j.ZipFile(file, password.toCharArray())
            val total = zipFile.fileHeaders.size.toLong()
            var done = 0L
            for (header in zipFile.fileHeaders) {
                if (cancelFlag?.get() == true) throw ImgCancelledException()
                if (!budget.allowEntry()) { ok = false; break }
                val out = File(destDir, header.fileName)
                if (!FileOps.isPathInside(destPath, out.canonicalPath)) { ok = false; break }
                if (header.isDirectory) {
                    FileOps.noteCreatedDir(out, destPath, createdDirs)
                    out.mkdirs()
                    // L1: validasi ulang canonical SETELAH mkdirs (TOCTOU).
                    val dirCanon = try { out.canonicalPath } catch (e: Exception) { null }
                    if (dirCanon == null || !FileOps.isPathInside(destPath, dirCanon)) { ok = false; break }
                } else {
                    val existed = out.exists()
                    FileOps.noteCreatedDir(out.parentFile, destPath, createdDirs)
                    out.parentFile?.mkdirs()
                    // L1: validasi ulang canonical parent SETELAH mkdirs (TOCTOU).
                    val parentCanon = try { out.parentFile?.canonicalPath } catch (e: Exception) { null }
                    if (parentCanon == null || !FileOps.isPathInside(destPath, parentCanon)) { ok = false; break }
                    if (header.uncompressedSize >= 0L && header.uncompressedSize > budget.remaining()) { ok = false; break }
                    if (!existed) written += out
                    val counting = CountingOutputStream(FileOutputStream(out), budget.remaining())
                    zipFile.getInputStream(header).use { input ->
                        counting.use { cos -> input.copyTo(cos, ARCHIVE_COPY_BUFFER_SIZE) }
                    }
                    if (!budget.addBytes(counting.count)) { ok = false; break }
                }
                done++
                onProgress(done, total)
            }
            if (!ok) cleanup()
            ok
        } catch (e: ImgCancelledException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    private fun uniqueDest(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.indexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val rest = if (dot > 0) fileName.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$rest")
            i++
        }
        return candidate
    }
}
