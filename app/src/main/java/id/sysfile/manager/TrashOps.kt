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
        try {
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            indexFile(dir).writeText(arr.toString())
        } catch (e: Exception) {
            // Indeks gagal ditulis tidak boleh menggagalkan operasi berkasnya.
        }
    }

    fun isInTrash(file: File): Boolean {
        val dir = trashDir().absolutePath + "/"
        return file.absolutePath.startsWith(dir)
    }

    /** Pindahkan berkas/folder ke Tempat Sampah; yang sudah di sampah dihapus permanen. */
    fun moveToTrash(file: File): Boolean {
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
        val items = readIndex(dir)
        items += JSONObject()
            .put("t", trashName)
            .put("o", originalPath)
            .put("d", System.currentTimeMillis())
            .put("s", size)
            .put("dir", isDir)
        writeIndex(dir, items)
        return true
    }

    /** Semua isi Tempat Sampah, terbaru dulu; entri indeks yang berkasnya hilang dibersihkan. */
    fun listTrash(): List<TrashItem> {
        val dir = trashDir()
        if (!dir.isDirectory) return emptyList()
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
        return result.sortedByDescending { it.deletedAt }
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

    /** Kosongkan seluruh Tempat Sampah secara permanen. */
    fun emptyTrash(): Boolean {
        val dir = trashDir()
        if (!dir.isDirectory) return true
        var ok = true
        dir.listFiles()?.forEach { child ->
            if (child.name != "index.json" && !FileOps.deleteRecursively(child)) ok = false
        }
        writeIndex(dir, emptyList())
        return ok
    }

    private fun removeFromIndex(trashName: String) {
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
