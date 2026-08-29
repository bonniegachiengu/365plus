package online.vyybandasky.plus365.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.desktop.store.FileLedgerStore

/**
 * The ledger file is three people's money. Losing it is not a bug, it is the
 * end of the record.
 *
 * These are about the file, not the book: that a save replaces the previous
 * version in one operation, and that the version it replaced is still there
 * afterwards.
 */
class DurableWriteTest {

    private fun tempStore(): Pair<FileLedgerStore, File> {
        val dir = Files.createTempDirectory("plus365-write").toFile()
        val f = File(dir, "ledger.json")
        return FileLedgerStore(f) to f
    }

    @Test
    fun `a save replaces the file`() {
        val (store, f) = tempStore()
        store.write("first")
        assertEquals("first", store.read())
        store.write("second")
        assertEquals("second", store.read())
        assertTrue(f.exists())
    }

    @Test
    fun `the version being replaced is kept`() {
        val (store, _) = tempStore()
        store.write("first")
        assertNull(store.readBackup(), "there was nothing to replace yet")
        store.write("second")
        assertEquals("first", store.readBackup(), "the previous ledger is gone")
        store.write("third")
        assertEquals("second", store.readBackup(), "the backup is one generation, not the first ever")
    }

    @Test
    fun `no temporary file is left behind`() {
        val (store, f) = tempStore()
        store.write("first")
        store.write("second")
        assertTrue(
            !File(f.parentFile, "${f.name}.tmp").exists(),
            "a leftover .tmp means the move did not happen",
        )
    }

    @Test
    fun `clearing takes the backup with it`() {
        val (store, _) = tempStore()
        store.write("first")
        store.write("second")
        store.clear()
        assertNull(store.read())
        assertNull(store.readBackup(), "a cleared ledger that leaves a copy behind is not cleared")
    }

    @Test
    fun `a save into a directory that does not exist yet works`() {
        val dir = Files.createTempDirectory("plus365-write").toFile()
        val store = FileLedgerStore(File(File(dir, "nested"), "ledger.json"))
        store.write("first")
        assertEquals("first", store.read())
    }
}
