package id.sysfile.manager

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
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

/** Satu entri di dalam arsip, untuk layar Isi Arsip (dibuka tanpa ekstrak). */
data class ArchiveItem(val name: String, val isDir: Boolean, val size: Long)

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

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "wmv")
    private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "amr")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "epub")
    private val APP_EXT = setOf("apk", "xapk", "apks", "aab")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

    fun extOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

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
        val ext = extOf(file.name)
        return ext in setOf("txt", "md", "log", "json", "xml", "csv", "html", "htm", "kt", "java", "py", "js", "css", "ini", "cfg", "yaml", "yml", "properties", "sh")
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
                val kind = if (rootPath.lowercase().contains("usb")) "usb" else "sd"
                addRoot(rootPath, kind)
            }
        } catch (e: Exception) {
            // Volume eksternal tidak tersedia; internal tetap ditampilkan.
        }
        return roots
    }

    /** Ukuran rekursif sebuah file/folder. */
    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        file.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
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

    private fun copyOne(src: File, destDir: File): Boolean {
        val dest = File(destDir, uniqueName(destDir, src.name))
        return try {
            if (src.isDirectory) {
                if (!dest.mkdirs()) return false
                src.listFiles()?.forEach { child ->
                    val childDest = File(dest, child.name)
                    copyRaw(child, childDest)
                }
                true
            } else {
                copyRaw(src, dest)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun copyRaw(src: File, dest: File): Boolean {
        return try {
            if (src.isDirectory) {
                dest.mkdirs()
                src.listFiles()?.forEach { copyRaw(it, File(dest, it.name)) }
            } else {
                dest.parentFile?.mkdirs()
                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
                }
                dest.setLastModified(src.lastModified())
            }
            true
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
            }
        }
        return ok
    }

    fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
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

    /** Pencarian rekursif berdasarkan nama, dibatasi jumlah hasil. */
    fun search(root: File, query: String, showHidden: Boolean, limit: Int = 500): List<FEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty() || !root.isDirectory) return emptyList()
        val results = mutableListOf<FEntry>()
        root.walkTopDown()
            .onEnter { dir -> showHidden || !dir.name.startsWith(".") || dir == root }
            .forEach { f ->
                if (results.size >= limit) return results
                if (f == root) return@forEach
                if (!showHidden && f.name.startsWith(".")) return@forEach
                if (f.name.lowercase().contains(q)) results += entryOf(f)
            }
        return results
    }

    /** File terbaru lintas volume: pindai terbatas, hasil diurutkan dari yang paling baru diubah. */
    fun recentFiles(roots: List<File>, showHidden: Boolean, limit: Int = 12, cap: Int = 8000): List<FEntry> {
        val found = mutableListOf<FEntry>()
        var visited = 0
        for (root in roots) {
            if (!root.isDirectory || visited >= cap) continue
            root.walkTopDown()
                .onEnter { dir -> dir.canRead() && (showHidden || !dir.name.startsWith(".") || dir == root) }
                .forEach { f ->
                    if (visited >= cap) return@forEach
                    if (f.isFile) {
                        visited++
                        if (showHidden || !f.name.startsWith(".")) found += entryOf(f)
                    }
                }
        }
        return found.sortedByDescending { it.modified }.take(limit)
    }

    /** Semua berkas satu kategori lintas volume (untuk menu kategori di Beranda). */
    fun categoryFiles(roots: List<File>, category: Category, showHidden: Boolean, cap: Int = 20000, limit: Int = 1000): List<FEntry> {
        val found = mutableListOf<FEntry>()
        var visited = 0
        for (root in roots) {
            if (!root.isDirectory || visited >= cap) continue
            root.walkTopDown()
                .onEnter { dir -> dir.canRead() && (showHidden || !dir.name.startsWith(".") || dir == root) }
                .forEach { f ->
                    if (visited >= cap) return@forEach
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
        val entry = ZipEntry(entryName)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(zos, 64 * 1024) }
        zos.closeEntry()
    }

    /** Ekstrak berkas ZIP ke folder tujuan (dengan penjaga zip-slip). */
    fun unzip(zip: File, destDir: File): Boolean {
        return try {
            val destCanonical = destDir.canonicalPath
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(destCanonical)) {
                        return false
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out, 64 * 1024) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Pindai satu volume: ukuran per kategori + file terbesar. */
    fun analyze(root: File): AnalysisResult {
        val sizes = mutableMapOf<Category, Long>()
        val counts = mutableMapOf<Category, Int>()
        val largest = mutableListOf<FEntry>()
        var total = 0
        root.walkTopDown()
            .onEnter { dir -> dir.canRead() }
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
}

/** Mesin arsip tambahan: TAR & tarball (Commons Compress), 7Z ekstrak, ZIP terenkripsi (zip4j). */
object ArchiveOps {

    /** Jenis arsip dari nama berkas, mis. "tar.gz"; null bila bukan yang didukung. */
    fun kindOf(name: String): String? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tar.gz"
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> "tar.bz2"
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> "tar.xz"
            n.endsWith(".tar") -> "tar"
            n.endsWith(".zip") -> "zip"
            n.endsWith(".7z") -> "7z"
            n.endsWith(".rar") -> "rar"
            n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".aab") -> "zip"
            n.endsWith(".gz") -> "gz"
            n.endsWith(".bz2") -> "bz2"
            n.endsWith(".xz") -> "xz"
            else -> null
        }
    }

    /** Ekstrak arsip apa pun yang didukung ke [destDir]; false bila gagal/tidak didukung. */
    fun extract(file: File, destDir: File): Boolean {
        return try {
            when (kindOf(file.name)) {
                "zip" -> FileOps.unzip(file, destDir)
                "7z" -> extract7z(file, destDir)
                "tar" -> extractTar(file, destDir, "tar")
                "tar.gz" -> extractTar(file, destDir, "tar.gz")
                "tar.bz2" -> extractTar(file, destDir, "tar.bz2")
                "tar.xz" -> extractTar(file, destDir, "tar.xz")
                "rar" -> extractRar(file, destDir)
                "gz" -> decompressSingle(file, destDir, "gz")
                "bz2" -> decompressSingle(file, destDir, "bz2")
                "xz" -> decompressSingle(file, destDir, "xz")
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Buat arsip [kind] ("zip"/"tar"/"tar.gz"/"tar.bz2"/"tar.xz"); kata sandi hanya untuk ZIP. */
    fun create(sources: List<File>, destDir: File, baseName: String, kind: String, password: String?): File? {
        return try {
            when (kind) {
                "zip" -> if (password.isNullOrBlank()) FileOps.zipFiles(sources, destDir, baseName)
                         else createEncryptedZip(sources, destDir, baseName, password)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> createTar(sources, destDir, baseName, kind)
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
        val raw = BufferedOutputStream(FileOutputStream(dest))
        val compressed = when (kind) {
            "tar.gz" -> org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(raw)
            "tar.bz2" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream(raw)
            "tar.xz" -> org.apache.commons.compress.compressors.xz.XZCompressorOutputStream(raw)
            else -> raw
        }
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(compressed).use { tos ->
            tos.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX)
            for (src in sources) addToTar(tos, src, src.name)
        }
        return dest
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
            FileInputStream(file).use { it.copyTo(tos) }
            tos.closeArchiveEntry()
        }
    }

    private fun extractTar(archive: File, destDir: File, kind: String): Boolean {
        val raw = BufferedInputStream(FileInputStream(archive))
        val decompressed = when (kind) {
            "tar.gz" -> org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(raw)
            "tar.bz2" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(raw)
            "tar.xz" -> org.apache.commons.compress.compressors.xz.XZCompressorInputStream(raw)
            else -> raw
        }
        val destPath = destDir.canonicalPath
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream(decompressed).use { tis ->
            while (true) {
                val entry = tis.nextEntry ?: break
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(destPath)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { tis.copyTo(it) }
                }
            }
        }
        return true
    }

    private fun extract7z(archive: File, destDir: File): Boolean {
        val destPath = destDir.canonicalPath
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
            while (true) {
                val entry = seven.nextEntry ?: break
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(destPath)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        val buffer = ByteArray(8192)
                        var remaining = entry.size
                        while (remaining > 0) {
                            val read = seven.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (read <= 0) break
                            fos.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                }
            }
        }
        return true
    }

    /** Ekstrak arsip RAR (junrar) dengan penjaga path-slip; RAR berenkripsi tidak didukung. */
    private fun extractRar(archive: File, destDir: File): Boolean {
        val destPath = destDir.canonicalPath
        com.github.junrar.Archive(archive).use { rar ->
            while (true) {
                val header = rar.nextFileHeader() ?: break
                val name = header.fileName.replace('\\', '/')
                if (name.isBlank()) continue
                val out = File(destDir, name)
                if (!out.canonicalPath.startsWith(destPath)) continue
                if (header.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos -> rar.extractFile(header, fos) }
                }
            }
        }
        return true
    }

    /** Dekompresi aliran berkas tunggal (.gz/.bz2/.xz) menjadi satu berkas tanpa ekstensi kompresi. */
    private fun decompressSingle(file: File, destDir: File, kind: String): Boolean {
        val stripped = file.name.dropLast(kind.length + 1)
        val outName = if (stripped.isBlank()) file.name + ".out" else stripped
        val out = uniqueDest(destDir, outName)
        return try {
            val raw = BufferedInputStream(FileInputStream(file))
            val stream = when (kind) {
                "gz" -> org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(raw)
                "bz2" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(raw)
                "xz" -> org.apache.commons.compress.compressors.xz.XZCompressorInputStream(raw)
                else -> raw
            }
            stream.use { input -> FileOutputStream(out).use { input.copyTo(it, 64 * 1024) } }
            true
        } catch (e: Exception) {
            out.delete()
            false
        }
    }

    /** Daftar isi berkas ZIP/APK (nama + ukuran) untuk Inspeksi; null bila bukan ZIP valid. */
    fun listZipContents(file: File): List<Pair<String, Long>>? {
        return try {
            ZipFile(file).use { zip ->
                val result = mutableListOf<Pair<String, Long>>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    result += e.name to if (e.isDirectory) -1L else e.size
                }
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Daftar isi arsip (ZIP/7Z/TAR/RAR) untuk dibuka tanpa ekstrak; null bila gagal/tidak didukung. */
    fun listContents(file: File): List<ArchiveItem>? {
        return try {
            when (val kind = kindOf(file.name)) {
                "zip" -> listZipContents(file)?.map { (name, size) ->
                    ArchiveItem(name, size < 0L, if (size < 0L) 0L else size)
                }
                "7z" -> list7z(file)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> listTar(file, kind!!)
                "rar" -> listRar(file)
                "gz", "bz2", "xz" -> listSingle(file, kind)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Isi berkas aliran tunggal (gz/bz2/xz): tepat satu berkas hasil dekompresi. */
    private fun listSingle(file: File, kind: String): List<ArchiveItem> {
        val stripped = file.name.dropLast(kind.length + 1)
        val inner = if (stripped.isBlank()) file.name + ".out" else stripped
        // Ukuran hasil hanya diketahui untuk GZIP (trailer ISIZE); bz2/xz = 0 (disembunyikan UI).
        val size = if (kind == "gz") gzipSize(file) else 0L
        return listOf(ArchiveItem(inner, false, size))
    }

    /** Ukuran hasil dekompresi dari trailer GZIP (ISIZE, 4 byte terakhir); 0 bila tak terbaca. */
    private fun gzipSize(file: File): Long {
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 18) return 0L
                raf.seek(raf.length() - 4)
                val b = ByteArray(4)
                raf.readFully(b)
                (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun list7z(archive: File): List<ArchiveItem> {
        val result = mutableListOf<ArchiveItem>()
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
            while (true) {
                val e = seven.nextEntry ?: break
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
        archive: File,
        kind: String
    ): org.apache.commons.compress.archivers.tar.TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(archive))
        val decompressed = when (kind) {
            "tar.gz" -> org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(raw)
            "tar.bz2" -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(raw)
            "tar.xz" -> org.apache.commons.compress.compressors.xz.XZCompressorInputStream(raw)
            else -> raw
        }
        return org.apache.commons.compress.archivers.tar.TarArchiveInputStream(decompressed)
    }

    private fun listTar(archive: File, kind: String): List<ArchiveItem> {
        val result = mutableListOf<ArchiveItem>()
        openTar(archive, kind).use { tis ->
            while (true) {
                val e = tis.nextEntry ?: break
                result += ArchiveItem(e.name, e.isDirectory, if (e.isDirectory) 0L else e.size)
            }
        }
        return result
    }

    private fun listRar(archive: File): List<ArchiveItem> {
        val result = mutableListOf<ArchiveItem>()
        com.github.junrar.Archive(archive).use { rar ->
            while (true) {
                val header = rar.nextFileHeader() ?: break
                val name = header.fileName.replace('\\', '/')
                if (name.isBlank()) continue
                result += ArchiveItem(name, header.isDirectory, if (header.isDirectory) 0L else header.unpSize)
            }
        }
        return result
    }

    /** Ekstrak SATU entri [entryName] dari arsip ke [destDir]; berkas hasil atau null. */
    fun extractSingle(file: File, entryName: String, destDir: File): File? {
        val base = entryName.substringAfterLast('/')
        if (base.isBlank()) return null
        val out = uniqueDest(destDir, base)
        val ok = try {
            when (val kind = kindOf(file.name)) {
                "zip" -> extractSingleZip(file, entryName, out)
                "7z" -> extractSingle7z(file, entryName, out)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> extractSingleTar(file, kind!!, entryName, out)
                "rar" -> extractSingleRar(file, entryName, out)
                // Aliran tunggal: dekompresi ke nama hasil yang sama dengan 'out'.
                "gz", "bz2", "xz" -> decompressSingle(file, destDir, kind)
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

    private fun extractSingleZip(file: File, entryName: String, out: File): Boolean {
        ZipFile(file).use { zip ->
            val e = zip.getEntry(entryName) ?: return false
            if (e.isDirectory) return false
            zip.getInputStream(e).use { input ->
                FileOutputStream(out).use { input.copyTo(it, 64 * 1024) }
            }
        }
        return true
    }

    private fun extractSingle7z(archive: File, entryName: String, out: File): Boolean {
        org.apache.commons.compress.archivers.sevenz.SevenZFile(archive).use { seven ->
            while (true) {
                val e = seven.nextEntry ?: break
                if (e.isDirectory || e.name.replace('\\', '/') != entryName) continue
                FileOutputStream(out).use { fos ->
                    val buffer = ByteArray(8192)
                    var remaining = e.size
                    while (remaining > 0) {
                        val read = seven.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                return true
            }
        }
        return false
    }

    private fun extractSingleTar(archive: File, kind: String, entryName: String, out: File): Boolean {
        openTar(archive, kind).use { tis ->
            while (true) {
                val e = tis.nextEntry ?: break
                if (e.isDirectory || e.name != entryName) continue
                FileOutputStream(out).use { fos -> tis.copyTo(fos, 64 * 1024) }
                return true
            }
        }
        return false
    }

    private fun extractSingleRar(archive: File, entryName: String, out: File): Boolean {
        com.github.junrar.Archive(archive).use { rar ->
            while (true) {
                val header = rar.nextFileHeader() ?: break
                if (header.isDirectory) continue
                if (header.fileName.replace('\\', '/') != entryName) continue
                FileOutputStream(out).use { fos -> rar.extractFile(header, fos) }
                return true
            }
        }
        return false
    }

    private fun createEncryptedZip(sources: List<File>, destDir: File, baseName: String, password: String): File? {
        val clean = baseName.trim().trimEnd('.')
        if (clean.isBlank()) return null
        val dest = uniqueDest(destDir, "$clean.zip")
        val zipFile = net.lingala.zip4j.ZipFile(dest, password.toCharArray())
        val params = net.lingala.zip4j.model.ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
        }
        for (src in sources) {
            if (src.isDirectory) zipFile.addFolder(src, params) else zipFile.addFile(src, params)
        }
        return dest
    }

    fun isZipEncrypted(file: File): Boolean = try {
        net.lingala.zip4j.ZipFile(file).isEncrypted
    } catch (e: Exception) {
        false
    }

    fun extractEncryptedZip(file: File, destDir: File, password: String): Boolean {
        return try {
            val zipFile = net.lingala.zip4j.ZipFile(file, password.toCharArray())
            val destPath = destDir.canonicalPath
            for (header in zipFile.fileHeaders) {
                val out = File(destDir, header.fileName)
                if (!out.canonicalPath.startsWith(destPath)) continue
                if (header.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zipFile.getInputStream(header).use { input ->
                        FileOutputStream(out).use { input.copyTo(it) }
                    }
                }
            }
            true
        } catch (e: Exception) {
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
