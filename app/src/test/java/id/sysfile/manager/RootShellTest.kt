package id.sysfile.manager

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the root shell output parsing (E1). No su needed. */
class RootShellTest {

    @Test
    fun groupStatLines_normalLinesPassThrough() {
        val lines = listOf(
            "D|0|1700000000|/storage",
            "F|123|1700000001|/storage/a.txt",
            "F|456|1700000002|/storage/b.txt"
        )
        assertEquals(lines, RootShell.groupStatLines(lines))
    }

    @Test
    fun groupStatLines_pipeInNameIsKept() {
        // '|' di nama aman: jalur terakhir, split limit=4.
        val lines = listOf("F|10|1700000000|/storage/a|b.txt")
        assertEquals(lines, RootShell.groupStatLines(lines))
    }

    @Test
    fun groupStatLines_newlineInNameIsJoined() {
        val lines = listOf(
            "F|10|1700000000|/storage/nama",
            "dengan baris baru.txt",
            "F|20|1700000001|/storage/lain.txt"
        )
        assertEquals(
            listOf(
                "F|10|1700000000|/storage/nama\ndengan baris baru.txt",
                "F|20|1700000001|/storage/lain.txt"
            ),
            RootShell.groupStatLines(lines)
        )
    }

    @Test
    fun groupStatLines_strayLinesBeforeFirstRecordIgnored() {
        val lines = listOf("sampah", "D|0|1700000000|/storage")
        assertEquals(listOf("D|0|1700000000|/storage"), RootShell.groupStatLines(lines))
    }
}
