package id.sysfile.manager

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Akses direktori root "/" model gabungan:
 * - tanpa root: penjelajahan biasa sebatas yang diizinkan Android (mode terbatas);
 * - dengan izin superuser (Magisk/su): daftar isi dibaca lewat shell root sehingga
 *   seluruh pohon berkas terbuka, dan berkas yang tak terbaca langsung dapat disalin
 *   ke cache aplikasi untuk dibuka.
 * Semua fungsi di sini memblokir — panggil dari dispatcher IO.
 */
object RootShell {
    const val UNKNOWN = 0
    const val GRANTED = 1
    const val DENIED = -1

    @Volatile
    var state: Int = UNKNOWN
        private set

    private fun quote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    /** Jalankan perintah lewat su; daftar baris keluaran, atau null bila gagal/timeout. */
    private fun runSu(script: String, timeoutSec: Long): List<String>? {
        return try {
            val proc = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
            val reader = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { lines.add(it) }
                } catch (e: Exception) {
                    // abaikan: proses mungkin dimatikan paksa
                }
            }
            reader.isDaemon = true
            reader.start()
            if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            reader.join(2000)
            if (proc.exitValue() != 0) null else lines
        } catch (e: Exception) {
            null
        }
    }

    /** Cek/minta akses root (memicu prompt Magisk bila belum diputuskan). Hasil di-cache per sesi. */
    fun ensureRoot(): Boolean {
        if (state != UNKNOWN) return state == GRANTED
        val granted = runSu("id", 20)?.any { it.contains("uid=0") } == true
        state = if (granted) GRANTED else DENIED
        return granted
    }

    /** Shell root dipakai untuk jalur di luar penyimpanan bersama. */
    fun shouldUseShell(path: String): Boolean =
        state == GRANTED && path != "/storage" && !path.startsWith("/storage/") && !path.startsWith("/sdcard")

    /**
     * Padanan jalur media nyata untuk jalur emulasi: /storage/emulated/0
     * adalah tampilan FUSE; sumber aslinya /data/media/0. Khusus Android/obb
     * berupa bind mount per-namespace — dari namespace shell root, jalur
     * emulasinya tampak KOSONG padahal isinya ada, sedangkan jalur media
     * selalu terbaca root. Null bila bukan jalur emulasi internal.
     */
    private fun mediaDirOf(dir: File): File? {
        val p = dir.absolutePath
        val prefix = "/storage/emulated/0"
        return if (p == prefix || p.startsWith("$prefix/")) {
            File("/data/media/0" + p.removePrefix(prefix))
        } else {
            null
        }
    }

    /**
     * Daftar isi satu folder lewat shell root; null bila gagal. Urutan sama
     * seperti FileOps.listDir. Bila hasil jalur emulasi kosong/null, dicoba
     * ulang lewat jalur media (/data/media/0) — kasus Android/obb di atas.
     */
    fun listDir(dir: File, showHidden: Boolean, sort: SortKey, ascending: Boolean): List<FEntry>? {
        val primary = listDirRaw(dir, showHidden, sort, ascending)
        if (primary != null && primary.isNotEmpty()) return primary
        val media = mediaDirOf(dir) ?: return primary
        val viaMedia = listDirRaw(media, showHidden, sort, ascending)
        return if (viaMedia != null && viaMedia.isNotEmpty()) viaMedia else primary
    }

    private fun listDirRaw(dir: File, showHidden: Boolean, sort: SortKey, ascending: Boolean): List<FEntry>? {
        val q = quote(dir.absolutePath)
        val script = "for f in $q/* $q/.[!.]*; do " +
            "[ -e \"\$f\" ] || [ -L \"\$f\" ] || continue; " +
            "if [ -d \"\$f\" ]; then t=D; elif [ -f \"\$f\" ]; then t=F; else t=O; fi; " +
            "s=\$(stat -c %s \"\$f\" 2>/dev/null); m=\$(stat -c %Y \"\$f\" 2>/dev/null); " +
            "echo \"\$t|\${s:-0}|\${m:-0}|\$f\"; done"
        val lines = runSu(script, 30) ?: return null
        val entries = mutableListOf<FEntry>()
        for (line in lines) {
            val parts = line.split("|", limit = 4)
            if (parts.size < 4) continue
            val path = parts[3]
            val name = File(path).name
            if (!showHidden && name.startsWith(".")) continue
            val isDir = parts[0] == "D"
            entries += FEntry(
                path = path,
                name = name,
                isDir = isDir,
                size = if (isDir) 0L else parts[1].toLongOrNull() ?: 0L,
                modified = (parts[2].toLongOrNull() ?: 0L) * 1000L,
                category = if (isDir) Category.OTHER else FileOps.categoryOf(name)
            )
        }
        val comparator: Comparator<FEntry> = when (sort) {
            SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortKey.DATE -> compareBy { it.modified }
            SortKey.SIZE -> compareBy { it.size }
        }
        val sorted = entries.sortedWith(comparator)
        val ordered = if (ascending) sorted else sorted.reversed()
        return ordered.filter { it.isDir } + ordered.filter { !it.isDir }
    }

    /** Jumlah isi langsung sebuah folder lewat shell root; null bila gagal. */
    fun childCount(dir: File): Int? {
        val direct = childCountRaw(dir)
        if (direct != null && direct > 0) return direct
        val media = mediaDirOf(dir) ?: return direct
        val viaMedia = childCountRaw(media)
        return if (viaMedia != null && viaMedia > 0) viaMedia else direct
    }

    private fun childCountRaw(dir: File): Int? {
        if (state != GRANTED) return null
        val q = quote(dir.absolutePath)
        val script = "n=0; for f in $q/* $q/.[!.]*; do " +
            "[ -e \"\$f\" ] || [ -L \"\$f\" ] || continue; n=\$((n+1)); done; echo \$n"
        val lines = runSu(script, 20) ?: return null
        return lines.firstOrNull()?.trim()?.toIntOrNull()
    }

    /** Salin berkas yang tak terbaca langsung ke cache aplikasi lewat root; null bila gagal. */
    fun stageToCache(file: File, cacheDir: File): File? {
        if (state != GRANTED) return null
        val dir = File(cacheDir, "rootstage").apply { mkdirs() }
        val dest = File(dir, file.name)
        val cmd = "cp ${quote(file.absolutePath)} ${quote(dest.absolutePath)} && chmod 644 ${quote(dest.absolutePath)}"
        val ok = runSu(cmd, 30) != null
        return if (ok && dest.exists()) dest else null
    }
}
