package online.vyybandasky.plus365.store

import java.io.File
import online.vyybandasky.plus365.core.store.LedgerStore

/**
 * The phone's copy of the book: one JSON file in the app's private storage.
 *
 * Written whole rather than appended to. The log is small — a few hundred
 * entries over years — and rewriting it means a crash mid-write can never leave
 * half an entry behind. The write goes to a temporary file first and is renamed
 * over the real one, so the file on disk is always a complete book or the
 * previous complete book, never something in between.
 */
class FileLedgerStore(private val file: File) : LedgerStore {

    override fun read(): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse a rename onto an existing file.
            file.delete()
            tmp.renameTo(file)
        }
    }

    override fun clear() {
        file.delete()
    }
}
