package id.sysfile.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tes murni M13: penyembunyian item sampah dari folder terkunci.
 *  listTrash() sendiri butuh Environment Android, jadi hanya helper
 *  murninya yang diuji di JVM. */
class TrashOpsTest {

    private val locked = setOf("/storage/emulated/0/Private")

    @Test
    fun hiddenWhenOriginalInsideLockedFolder() {
        val item = TrashItem("a.jpg", "/storage/emulated/0/Private/a.jpg", 0L, 10L, false)
        assertTrue(TrashOps.isHiddenLocked(item, locked, emptySet()))
    }

    @Test
    fun visibleWhenFolderUnlockedThisSession() {
        val item = TrashItem("a.jpg", "/storage/emulated/0/Private/a.jpg", 0L, 10L, false)
        assertFalse(TrashOps.isHiddenLocked(item, locked, setOf("/storage/emulated/0/Private")))
    }

    @Test
    fun visibleWhenOriginalOutsideLockedFolder() {
        val item = TrashItem("b.jpg", "/storage/emulated/0/Download/b.jpg", 0L, 10L, false)
        assertFalse(TrashOps.isHiddenLocked(item, locked, emptySet()))
    }

    @Test
    fun visibleWhenOriginalPathBlank() {
        val item = TrashItem("c.jpg", "", 0L, 10L, false)
        assertFalse(TrashOps.isHiddenLocked(item, locked, emptySet()))
    }
}
