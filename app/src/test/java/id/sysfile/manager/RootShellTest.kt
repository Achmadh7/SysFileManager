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

    @Test
    fun parseDfOutput_validDf() {
        // toybox df: Filesystem 1K-blocks Used Available Use% Mounted on.
        val lines = listOf(
            "Filesystem           1K-blocks      Used Available Use% Mounted on",
            "/dev/block/vold/public:179,65  30408704  12345678  18063026  41% /mnt/media_rw/BED9-9EEF"
        )
        assertEquals(Pair(31138512896L, 18496538624L), RootShell.parseDfOutput(lines))
    }

    @Test
    fun parseDfOutput_noDataLineIsNull() {
        assertEquals(null, RootShell.parseDfOutput(emptyList()))
        assertEquals(
            null,
            RootShell.parseDfOutput(listOf("Filesystem 1K-blocks Used Available Use% Mounted on"))
        )
        assertEquals(null, RootShell.parseDfOutput(listOf("sampah tanpa angka")))
    }

    @Test
    fun groupStatBytes_crlfInFilenamePreserved() {
        val path1 = "/tmp/a\r\nb"
        val path2 = "/tmp/c"
        val raw = (
            "F|4|10|${path1.toByteArray(Charsets.UTF_8).size}|$path1\n" +
                "F|5|11|${path2.toByteArray(Charsets.UTF_8).size}|$path2\n"
            ).toByteArray(Charsets.UTF_8)

        assertEquals(
            listOf("F|4|10|$path1", "F|5|11|$path2"),
            RootShell.groupStatBytes(raw)
        )
    }

    @Test
    fun isProtectedRootPath_blocksMountsButAllowsChildren() {
        org.junit.Assert.assertTrue(RootShell.isProtectedRootPath("/"))
        org.junit.Assert.assertTrue(RootShell.isProtectedRootPath(""))
        org.junit.Assert.assertTrue(RootShell.isProtectedRootPath("/system/"))
        org.junit.Assert.assertTrue(RootShell.isProtectedRootPath("/data"))
        org.junit.Assert.assertTrue(RootShell.isProtectedRootPath("/storage/emulated/0"))
        org.junit.Assert.assertFalse(RootShell.isProtectedRootPath("/data/local/tmp/foo"))
        org.junit.Assert.assertFalse(RootShell.isProtectedRootPath("/system/app/Demo.apk"))
    }

    @Test
    fun rootMutations_failClosedWithoutGrantedState() {
        RootShell.resetRootState()
        val a = java.io.File("/data/local/tmp/a")
        val b = java.io.File("/data/local/tmp/b")
        org.junit.Assert.assertFalse(RootShell.exists(a))
        org.junit.Assert.assertFalse(RootShell.mkdir(a))
        org.junit.Assert.assertFalse(RootShell.createFile(a))
        org.junit.Assert.assertFalse(RootShell.rename(a, b))
        org.junit.Assert.assertFalse(RootShell.copy(a, b))
        org.junit.Assert.assertFalse(RootShell.move(a, b))
        org.junit.Assert.assertFalse(RootShell.delete(a))
    }
}
