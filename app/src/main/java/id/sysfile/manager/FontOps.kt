package id.sysfile.manager

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/**
 * Parser tabel nama font TrueType/OpenType: nama keluarga (nameID 1)
 * + gaya (nameID 2), dibaca langsung dari berkas tanpa dependensi baru.
 * Semua kesalahan (bukan font, tabel nama hilang/rusak) -> null.
 * Dipakai pratinjau font (.ttf); format OTTO (.otf) memakai tabel nama
 * yang sama sehingga ikut terbaca.
 */
object FontOps {
    fun familyName(file: File): String? = nameRecord(file, 1)
    fun styleName(file: File): String? = nameRecord(file, 2)

    private fun nameRecord(file: File, nameId: Int): String? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 12) return null
                raf.seek(4)
                val numTables = raf.readU16()
                if (numTables <= 0 || numTables > 100) return null
                var nameOffset = -1L
                var nameLength = -1L
                for (i in 0 until numTables) {
                    val rec = 12L + i * 16L
                    if (rec + 16 > raf.length()) return null
                    if (raf.readTagAt(rec) == "name") {
                        nameOffset = raf.readU32At(rec + 8)
                        nameLength = raf.readU32At(rec + 12)
                        break
                    }
                }
                if (nameOffset < 0 || nameLength < 6 || nameOffset + nameLength > raf.length()) return null
                raf.seek(nameOffset)
                val format = raf.readU16()
                if (format != 0 && format != 1) return null
                val count = raf.readU16()
                val stringOffset = raf.readU16()
                if (count <= 0 || count > 500) return null
                var fallback: String? = null
                for (i in 0 until count) {
                    val r = nameOffset + 6 + i * 12L
                    if (r + 12 > nameOffset + nameLength) break
                    raf.seek(r)
                    val platform = raf.readU16()
                    val encoding = raf.readU16()
                    raf.readU16() // languageID
                    val id = raf.readU16()
                    val len = raf.readU16()
                    val off = raf.readU16()
                    if (id != nameId || len <= 0) continue
                    val pos = nameOffset + stringOffset + off
                    if (pos < 0 || pos + len > nameOffset + nameLength) continue
                    val bytes = ByteArray(len)
                    raf.seek(pos)
                    raf.readFully(bytes)
                    val text = when (platform) {
                        3, 0 -> bytes.decodeToString(Charsets.UTF_16BE)
                        1 -> bytes.decodeToString(Charsets.ISO_8859_1)
                        else -> continue
                    }.trim()
                    if (text.isEmpty()) continue
                    if (platform == 3 && encoding == 1) return text
                    if (fallback == null) fallback = text
                }
                return fallback
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun RandomAccessFile.readU16(): Int {
        val hi = read()
        val lo = read()
        if (hi < 0 || lo < 0) throw EOFException()
        return (hi shl 8) or lo
    }

    private fun RandomAccessFile.readU32At(off: Long): Long {
        seek(off)
        var v = 0L
        repeat(4) {
            val b = read()
            if (b < 0) throw EOFException()
            v = (v shl 8) or b.toLong()
        }
        return v
    }

    private fun RandomAccessFile.readTagAt(off: Long): String {
        val bytes = ByteArray(4)
        seek(off)
        readFully(bytes)
        return bytes.decodeToString()
    }
}
