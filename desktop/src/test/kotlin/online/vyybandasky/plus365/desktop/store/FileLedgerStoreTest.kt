package online.vyybandasky.plus365.desktop.store

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.store.openOrSeed
import online.vyybandasky.plus365.core.store.save

/**
 * The file-backed store, exercised against a real filesystem.
 *
 * The phone runs the same ten lines against its own private directory, so this
 * covers the part that the in-memory tests deliberately cannot.
 */
class FileLedgerStoreTest {

    private val dir: File = Files.createTempDirectory("plus365-store").toFile()
    private val file = File(dir, "ledger.json")
    private val store = FileLedgerStore(file)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun a_missing_file_reads_as_nothing_rather_than_throwing() {
        assertFalse(file.exists())
        assertNull(store.read())
    }

    @Test
    fun a_book_written_to_disk_comes_back_the_same() {
        val seeded = DevSeed.book()
        store.save(seeded)

        assertTrue(file.exists())
        val back = FileLedgerStore(file).openOrSeed { error("should not seed") }
        assertEquals(seeded.entries, back.entries)
        assertEquals(seeded.state().cashAtHandCents, back.state().cashAtHandCents)
    }

    @Test
    fun opening_an_empty_directory_lays_down_the_baseline() {
        assertFalse(file.exists())
        val opened = store.openOrSeed { DevSeed.book() }
        assertTrue(file.exists(), "opening must leave a file behind")
        assertEquals(opened.entries.size, FileLedgerStore(file).openOrSeed { error("no") }.entries.size)
    }

    @Test
    fun a_second_write_replaces_the_file_rather_than_appending_to_it() {
        store.save(DevSeed.book())
        val firstLength = file.length()
        store.save(DevSeed.book())

        assertEquals(firstLength, file.length(), "the book is rewritten whole, not appended")
    }

    @Test
    fun no_temporary_file_is_left_behind_after_a_write() {
        store.save(DevSeed.book())
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "found ${leftovers.map { it.name }}")
    }

    @Test
    fun a_corrupt_file_falls_back_to_the_seed_instead_of_refusing_to_open() {
        file.writeText("{ this is not json")
        val opened = store.openOrSeed { DevSeed.book() }
        assertEquals(DevSeed.book().entries.size, opened.entries.size)
    }

    @Test
    fun clear_removes_the_file() {
        store.save(DevSeed.book())
        assertTrue(file.exists())
        store.clear()
        assertFalse(file.exists())
        assertNull(store.read())
    }

    @Test
    fun the_stored_file_is_readable_by_a_person() {
        // If this app ever loses its way, the ledger should still be legible.
        store.save(DevSeed.book())
        val text = file.readText()
        assertTrue(text.contains("\n"), "should be pretty-printed, not one line")
        assertTrue(text.contains("kangiri"))
        assertTrue(text.contains("CONTRIBUTION"))
    }
}
