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

    // r203: vold memasang OTG di namespace mount global; su biasa berjalan
    // di namespace aplikasi sehingga isi volume tampak kosong (titik mount
    // sendiri terlihat — itulah kenapa pemindai r201 menemukan nama volume
    // tapi isinya tak terbaca). Bila su mendukung --mount-master, semua
    // perintah root dijalankan di namespace global.
    @Volatile
    private var mountMaster: Boolean = false
    @Volatile
    private var mountMasterProbed: Boolean = false

    /** Jalankan perintah lewat su; daftar baris keluaran, atau null bila gagal/timeout. */
    private fun runSu(script: String, timeoutSec: Long): List<String>? {
        val argv = if (mountMaster) listOf("su", "--mount-master", "-c", script)
        else listOf("su", "-c", script)
        return runSuCmd(argv, timeoutSec)
    }

    private fun runSuCmd(argv: List<String>, timeoutSec: Long): List<String>? {
        return try {
            val proc = ProcessBuilder(argv)
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

    /** L28: kunci untuk check-and-run [ensureRoot] — dulu dua pemanggil
     *  bersamaan bisa lolos dari cek state bersamaan dan menampilkan
     *  prompt su dua kali. */
    private val rootLock = Any()

    /** Cek/minta akses root (memicu prompt Magisk bila belum diputuskan). Hasil di-cache per sesi. */
    fun ensureRoot(): Boolean = synchronized(rootLock) {
        if (state == UNKNOWN) {
            val granted = runSu("id", 20)?.any { it.contains("uid=0") } == true
            state = if (granted) GRANTED else DENIED
        }
        if (state == GRANTED && !mountMasterProbed) {
            // r203: sekali per sesi — bila su tak mendukung --mount-master,
            // perintah gagal (exit != 0) dan mountMaster tetap false, jadi
            // perilaku root yang sudah berjalan tidak berubah.
            mountMasterProbed = true
            mountMaster = runSuCmd(listOf("su", "--mount-master", "-c", "id"), 10)
                ?.any { it.contains("uid=0") } == true
        }
        state == GRANTED
    }

    /** r204: kembalikan status root ke UNKNOWN agar percobaan berikutnya
     *  (mis. sakelar dihidupkan lagi) benar-benar mencoba ulang su —
     *  DENIED yang menempel tidak boleh mengunci root seumur proses. */
    fun resetRootState() = synchronized(rootLock) {
        state = UNKNOWN
        mountMaster = false
        mountMasterProbed = false
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

    // L15: record stat berformat length-prefixed (T|size|mtime|len|path)
    // sehingga nama file yang mengandung newline tidak lagi menyamar
    // sebagai record baru — dulu parser berbasis Regex per baris membuat
    // record hantu (baris lanjutan nama yang kebetulan cocok pola
    // "D|angka|angka|" dianggap record baru dengan nama/saat salah).
    internal fun groupStatLines(lines: List<String>): List<String> {
        val bytes = (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
        val result = mutableListOf<String>()
        var i = 0
        while (i < bytes.size) {
            // Header: T|size|mtime|len — 4 pemisah '|' pertama dari i.
            // Path bisa mengandung '|' dan newline, jadi batas path
            // ditentukan len, BUKAN newline berikutnya.
            var p1 = -1
            var p2 = -1
            var p3 = -1
            var p4 = -1
            var j = i
            var corrupt = false
            while (j < bytes.size) {
                val b = bytes[j]
                if (b == '\n'.code.toByte()) {
                    // Newline sebelum 4 pemisah ketemu: header rusak.
                    corrupt = true
                    break
                }
                if (b == '|'.code.toByte()) {
                    if (p1 < 0) p1 = j else if (p2 < 0) p2 = j else if (p3 < 0) p3 = j else if (p4 < 0) {
                        p4 = j
                        break
                    }
                }
                j++
            }
            if (corrupt || p4 < 0) {
                // Lewati baris ini, lanjut dari baris berikut.
                i = if (corrupt) j + 1 else bytes.size
                continue
            }
            val kind = bytes[i].toInt().toChar()
            val size = String(bytes, p1 + 1, p2 - p1 - 1, Charsets.UTF_8).toLongOrNull()
            val mtime = String(bytes, p2 + 1, p3 - p2 - 1, Charsets.UTF_8).toLongOrNull()
            // wc -c mengeluarkan spasi di depan angka — trim dulu.
            val len = String(bytes, p3 + 1, p4 - p3 - 1, Charsets.UTF_8).trim().toIntOrNull()
            if (kind != 'D' && kind != 'F' && kind != 'O' || size == null || mtime == null || len == null || len < 0) {
                i = p4 + 1
                continue
            }
            val pathStart = p4 + 1
            val pathEnd = pathStart + len
            if (pathEnd > bytes.size) break
            val path = String(bytes, pathStart, len, Charsets.UTF_8)
            result += "$kind|$size|$mtime|$path"
            // Setelah path ada satu '\n' pemisah.
            i = if (pathEnd < bytes.size && bytes[pathEnd] == '\n'.code.toByte()) pathEnd + 1 else pathEnd
        }
        return result
    }

    private fun listDirRaw(dir: File, showHidden: Boolean, sort: SortKey, ascending: Boolean): List<FEntry>? {
        val q = quote(dir.absolutePath)
        val script = "for f in $q/* $q/.[!.]*; do " +
            "[ -e \"\$f\" ] || [ -L \"\$f\" ] || continue; " +
            "if [ -d \"\$f\" ]; then t=D; elif [ -f \"\$f\" ]; then t=F; else t=O; fi; " +
            "s=\$(stat -c %s \"\$f\" 2>/dev/null); m=\$(stat -c %Y \"\$f\" 2>/dev/null); " +
            "n=\$(printf '%s' \"\$f\" | wc -c); " +
            "printf '%s|%s|%s|%s|%s\\n' \"\$t\" \"\${s:-0}\" \"\${m:-0}\" \"\$n\" \"\$f\"; done"
        val lines = runSu(script, 30) ?: return null
        val entries = mutableListOf<FEntry>()
        for (line in groupStatLines(lines)) {
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

    /** r207: ukuran volume lewat `df` root — total + tersedia (kolom
     *  1K-blocks; Available = StatFs.availableBytes yang dipakai
     *  storageRoots). StatFs/File.totalSpace tak bisa membaca
     *  /mnt/media_rw: hanya root yang masuk namespace mount-nya (r203).
     *  Null bila root tak aktif / df gagal / tak terurai — pemanggil
     *  memakai 0L/0L (volume tetap jadi baris kecil). */
    fun volumeSize(path: String): Pair<Long, Long>? {
        if (state != GRANTED) return null
        val lines = runSu("df " + quote(path), 10) ?: return null
        return parseDfOutput(lines)
    }

    /** Urai keluaran `df <path>` (toybox, satuan 1K-blocks):
     *  baris data pertama yang terurai jadi (total, tersedia) dalam
     *  byte; null bila tak ada baris data yang valid. Murni agar bisa
     *  diuji unit tanpa su. */
    internal fun parseDfOutput(lines: List<String>): Pair<Long, Long>? {
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 4) continue
            val total = parts[1].toLongOrNull() ?: continue
            val free = parts[3].toLongOrNull() ?: continue
            if (total <= 0 || free < 0 || free > total) continue
            return total * 1024L to free * 1024L
        }
        return null
    }

    /** Salin berkas yang tak terbaca langsung ke cache aplikasi lewat root; null bila gagal. */
    fun stageToCache(file: File, cacheDir: File): File? {
        if (state != GRANTED) return null
        val dir = File(cacheDir, "rootstage").apply { mkdirs() }
        // M15: nama unik per staging + evict basi — dulu nama polos
        // (dua berkas beda folder saling menimpa) dan tidak pernah
        // dibersihkan.
        FileOps.evictStaleCache(dir)
        val dest = File(dir, System.nanoTime().toString() + "-" + file.name)
        val cmd = "cp ${quote(file.absolutePath)} ${quote(dest.absolutePath)} && chmod 644 ${quote(dest.absolutePath)}"
        val ok = runSu(cmd, 30) != null
        return if (ok && dest.exists()) dest else null
    }

    /**
     * Tulis teks ke berkas target lewat shell root (su).
     * Berguna untuk berkas di /system, /vendor, /data, Magisk module, atau berkas sistem lainnya.
     * Menggunakan file cache sementara dan cat/cp agar kepemilikan dan permission berkas asli tetap terjaga.
     */
    fun writeFile(target: File, text: String, cacheDir: File): Boolean {
        if (!ensureRoot()) return false
        val dir = File(cacheDir, "rootsave").apply { mkdirs() }
        FileOps.evictStaleCache(dir)
        val temp = File(dir, "save-" + System.nanoTime() + ".tmp")
        return try {
            temp.writeText(text, Charsets.UTF_8)
            val qSrc = quote(temp.absolutePath)
            val qDst = quote(target.absolutePath)
            val parent = target.parentFile
            val qParent = if (parent != null) quote(parent.absolutePath) else null

            val script = buildString {
                if (qParent != null) {
                    append("mkdir -p $qParent && ")
                }
                append("cat $qSrc > $qDst 2>/dev/null || ")
                append("(mount -o remount,rw / 2>/dev/null; mount -o remount,rw /system 2>/dev/null; cat $qSrc > $qDst 2>/dev/null || cp -f $qSrc $qDst) && ")
                append("chmod 644 $qDst")
            }
            val res = runSu(script, 30)
            res != null
        } catch (e: Exception) {
            false
        } finally {
            try { temp.delete() } catch (_: Exception) {}
        }
    }
}
