package online.vyybandasky.plus365.desktop.store

import java.io.File
import online.vyybandasky.plus365.core.store.LedgerStore

/**
 * The master copy on the laptop. Same shape as the phone's, same reasoning:
 * whole-file write via a temporary and a rename, so the file is never half a
 * ledger.
 */
class FileLedgerStore(private val file: File) : LedgerStore {

    init {
        file.parentFile?.mkdirs()
    }

    override fun read(): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    override fun clear() {
        file.delete()
    }
}
