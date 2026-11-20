package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the root shell output parsing (L15: record stat
 *  length-prefixed "T|size|mtime|len|path"). No su needed. */
class RootShellTest {

    @Test
    fun groupStatLines_normalLinesPassThrough() {
        val lines = listOf(
            "D|0|1700000000|8|/storage",
            "F|123|1700000001|14|/storage/a.txt",
            "F|456|1700000002|14|/storage/b.txt"
        )
        assertEquals(
            listOf(
                "D|0|1700000000|/storage",
                "F|123|1700000001|/storage/a.txt",
                "F|456|1700000002|/storage/b.txt"
            ),
            RootShell.groupStatLines(lines)
        )
    }

    @Test
    fun groupStatLines_pipeInNameIsKept() {
        // '|' di nama aman: panjang byte menentukan batas path.
        val lines = listOf("F|10|1700000000|16|/storage/a|b.txt")
        assertEquals(listOf("F|10|1700000000|/storage/a|b.txt"), RootShell.groupStatLines(lines))
    }

    @Test
    fun groupStatLines_newlineInNameIsNotAGhostRecord() {
        // Nama "/storage/a\nD|5|6|/fake" (22 byte) tiba sebagai dua baris;
        // baris kedua menyerupai record tapi harus jadi bagian path,
        // bukan record hantu.
        val lines = listOf(
            "F|10|1700000000|22|/storage/a",
            "D|5|6|/fake",
            "F|20|1700000001|10|/storage/b"
        )
        assertEquals(
            listOf(
                "F|10|1700000000|/storage/a\nD|5|6|/fake",
                "F|20|1700000001|/storage/b"
            ),
            RootShell.groupStatLines(lines)
        )
    }

    @Test
    fun groupStatLines_filenameEndingWithNewline() {
        // Nama "/storage/x\n" (11 byte): newline di ujung nama + satu
        // newline pemisah record.
        val lines = listOf(
            "F|10|1700000000|11|/storage/x",
            "",
            "F|20|1700000001|10|/storage/b"
        )
        assertEquals(
            listOf(
                "F|10|1700000000|/storage/x\n",
                "F|20|1700000001|/storage/b"
            ),
            RootShell.groupStatLines(lines)
        )
    }

    @Test
    fun groupStatLines_strayLinesBeforeFirstRecordIgnored() {
        val lines = listOf("sampah", "D|0|1700000000|8|/storage")
        assertEquals(listOf("D|0|1700000000|/storage"), RootShell.groupStatLines(lines))
    }

    @Test
    fun groupStatLines_corruptTailKeepsEarlierRecords() {
        val lines = listOf(
            "F|10|1700000000|10|/storage/a",
            "sampah tanpa pipe"
        )
        assertEquals(listOf("F|10|1700000000|/storage/a"), RootShell.groupStatLines(lines))
    }
}
