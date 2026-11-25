package id.sysfile.manager

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Satu berkas di Tempat Sampah: nama di dalam sampah + jalur asalnya. */
data class TrashItem(
    val trashName: String,
    val originalPath: String,
    val deletedAt: Long,
    val size: Long,
    val isDir: Boolean
) {
    val name: String get() = File(originalPath).name
    val file: File get() = File(TrashOps.trashDir(), trashName)
}

/**
 * Tempat Sampah: berkas yang "dihapus" dipindah ke folder .SysFileTrash di
 * akar penyimpanan internal, dicatat di index.json (jalur asal + waktu),
 * dan bisa dipulihkan atau dihapus permanen dari layar Tempat Sampah.
 */
object TrashOps {

    private val indexLock = Any()

    fun trashDir(): File = File(Environment.getExternalStorageDirectory(), FileOps.TRASH_DIR_NAME)

    private fun indexFile(dir: File): File = File(dir, "index.json")

    private fun readIndex(dir: File): MutableList<JSONObject> {
        val f = indexFile(dir)
        if (!f.isFile) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) out += arr.getJSONObject(i)
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun writeIndex(dir: File, items: List<JSONObject>) {
        val tmp = FileOps.tempFileIn(dir)
        try {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            val text = arr.toString()
            if (tmp != null) {
                tmp.writeText(text)
                if (!tmp.renameTo(indexFile(dir))) {
                    indexFile(dir).writeText(text)
                }
            } else {
                indexFile(dir).writeText(text)
            }
        } catch (e: Exception) {
            // Indeks gagal ditulis tidak boleh menggagalkan operasi berkasnya.
        } finally {
            if (tmp != null && tmp.exists()) runCatching { tmp.delete() }
        }
    }

    fun isInTrash(file: File): Boolean {
        val trashCanon = FileOps.canonicalOrNull(trashDir().absolutePath) ?: return false
        val fileCanon = FileOps.canonicalOrNull(file.absolutePath) ?: return false
        return FileOps.isPathInside(trashCanon, fileCanon)
    }

    private fun isTrashRoot(file: File): Boolean {
        val trashCanon = FileOps.canonicalOrNull(trashDir().absolutePath) ?: return false
        val fileCanon = FileOps.canonicalOrNull(file.absolutePath) ?: return false
        return trashCanon == fileCanon
    }

    /** Pindahkan berkas/folder ke Tempat Sampah; yang sudah di sampah dihapus permanen. */
    fun moveToTrash(file: File): Boolean {
        if (isTrashRoot(file)) return false
        if (isInTrash(file)) return FileOps.deleteRecursively(file)
        val dir = trashDir()
        if (!dir.mkdirs() && !dir.isDirectory) return false
        val trashName = FileOps.uniqueName(dir, file.name)
        val dest = File(dir, trashName)
        val isDir = file.isDirectory
        val size = if (isDir) -1L else file.length()
        val originalPath = file.absolutePath
        val moved = try { file.renameTo(dest) } catch (e: Exception) { false }
        val ok = if (moved) {
            true
        } else if (copyRecursive(file, dest)) {
            FileOps.deleteRecursively(file)
            true
        } else {
            FileOps.deleteRecursively(dest)
            false
        }
        if (!ok) return false
        synchronized(indexLock) {
            val items = readIndex(dir)
            items += JSONObject()
                .put("t", trashName)
                .put("o", originalPath)
                .put("d", System.currentTimeMillis())
                .put("s", size)
                .put("dir", isDir)
            writeIndex(dir, items)
        }
        return true
    }

    /** Benar bila item sampah harus disembunyikan: jalur asalnya di
     *  dalam folder terkunci (atau induknya) yang belum dibuka pada sesi ini (M13). */
    internal fun isHiddenLocked(item: TrashItem, locked: Set<String>, unlocked: Set<String>): Boolean {
        if (item.originalPath.isBlank() || locked.isEmpty()) return false
        if (FileOps.isLockedPath(item.originalPath, locked) &&
            !FileOps.isUnlockedPath(item.originalPath, unlocked)
        ) return true
        return item.isDir && FileOps.blockingLockedInside(item.originalPath, locked, unlocked) != null
    }

    /** Entri indeks yang berkasnya masih ada, terbaru dulu; entri yang
     *  berkasnya hilang dibersihkan dari indeks. */
    private fun indexedItems(dir: File): List<TrashItem> = synchronized(indexLock) {
        val items = readIndex(dir)
        val result = mutableListOf<TrashItem>()
        var changed = false
        for (obj in items) {
            val trashName = obj.optString("t")
            if (trashName.isBlank() || !File(dir, trashName).exists()) {
                changed = true
                continue
            }
            result += TrashItem(
                trashName = trashName,
                originalPath = obj.optString("o"),
                deletedAt = obj.optLong("d"),
                size = obj.optLong("s", -1L),
                isDir = obj.optBoolean("dir")
            )
        }
        if (changed) {
            writeIndex(dir, items.filter { it.optString("t").isNotBlank() && File(dir, it.optString("t")).exists() })
        }
        result.sortedByDescending { it.deletedAt }
    }

    /** Semua isi Tempat Sampah, terbaru dulu. M13: item yang jalur
     *  asalnya di dalam folder terkunci yang belum dibuka sesi ini
     *  disembunyikan (tetap tercatat di indeks, hanya tidak tampil). */
    fun listTrash(locked: Set<String> = emptySet(), unlocked: Set<String> = emptySet()): List<TrashItem> {
        val dir = trashDir()
        if (!dir.isDirectory) return emptyList()
        return indexedItems(dir).filterNot { isHiddenLocked(it, locked, unlocked) }
    }

    /** Jumlah item sampah yang disembunyikan karena folder terkunci
     *  (M13). Hanya angka — tanpa nama/jalur — untuk catatan di layar. */
    fun countHiddenLocked(locked: Set<String>, unlocked: Set<String>): Int {
        val dir = trashDir()
        if (!dir.isDirectory) return 0
        return indexedItems(dir).count { isHiddenLocked(it, locked, unlocked) }
    }

    /** Kembalikan berkas ke jalur asalnya (nama dibuat unik bila sudah ada pengganti). */
    fun restore(item: TrashItem): Boolean {
        val src = item.file
        if (!src.exists()) return false
        val parent = File(item.originalPath).parentFile ?: return false
        if (!parent.mkdirs() && !parent.isDirectory) return false
        val dest = File(parent, FileOps.uniqueName(parent, File(item.originalPath).name))
        val moved = try { src.renameTo(dest) } catch (e: Exception) { false }
        val ok = if (moved) {
            true
        } else if (copyRecursive(src, dest)) {
            FileOps.deleteRecursively(src)
            true
        } else {
            FileOps.deleteRecursively(dest)
            false
        }
        if (ok) removeFromIndex(item.trashName)
        return ok
    }

    /** Hapus satu isi sampah secara permanen. */
    fun deleteForever(item: TrashItem): Boolean {
        val ok = FileOps.deleteRecursively(item.file)
        if (ok || !item.file.exists()) removeFromIndex(item.trashName)
        return ok
    }

    /** Kosongkan seluruh Tempat Sampah secara permanen. M13: item dari
     *  folder terkunci yang belum dibuka sesi ini TIDAK ikut terhapus
     *  (dan tetap tercatat di indeks) — menghapusnya tanpa biometrik
     *  sama saja melewati kunci. */
    fun emptyTrash(locked: Set<String> = emptySet(), unlocked: Set<String> = emptySet()): Boolean = synchronized(indexLock) {
        val dir = trashDir()
        if (!dir.isDirectory) return true
        val keep = indexedItems(dir)
            .filter { isHiddenLocked(it, locked, unlocked) }
            .map { it.trashName }.toSet()
        var ok = true
        dir.listFiles()?.forEach { child ->
            if (child.name != "index.json" && child.name !in keep && !FileOps.deleteRecursively(child)) ok = false
        }
        writeIndex(dir, readIndex(dir).filter { it.optString("t") in keep })
        ok
    }

    private fun removeFromIndex(trashName: String) = synchronized(indexLock) {
        val dir = trashDir()
        writeIndex(dir, readIndex(dir).filter { it.optString("t") != trashName })
    }

    private fun copyRecursive(src: File, dest: File): Boolean {
        return try {
            if (src.isDirectory) {
                if (!dest.mkdirs() && !dest.isDirectory) return false
                val children = src.listFiles() ?: return false
                for (child in children) {
                    if (!copyRecursive(child, File(dest, child.name))) return false
                }
                true
            } else {
                FileInputStream(src).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
