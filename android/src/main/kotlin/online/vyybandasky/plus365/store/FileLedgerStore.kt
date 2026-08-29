package online.vyybandasky.plus365.store

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import online.vyybandasky.plus365.core.store.LedgerStore

/**
 * The phone's copy of the book: one JSON file in the app's private storage.
 *
 * Written whole rather than appended to. The log is small — a few hundred entries
 * over years — and rewriting it means a crash mid-write can never leave half an
 * entry behind. The write goes to a temporary file and is moved over the real
 * one, so what is on disk is always a complete book or the previous complete
 * book, never something in between.
 *
 * ## The window that used to exist
 *
 * `File.renameTo` refuses to replace an existing file on some filesystems, so
 * the old fallback was `delete()` then `renameTo()`. Between those two calls
 * there is no ledger at all: a crash, a dead battery, or Android killing the
 * process right there loses the whole record, and the previous version has
 * already gone. Small window, total loss — and a phone being killed while
 * backgrounded is not a rare event.
 *
 * `Files.move` with `ATOMIC_MOVE` replaces in one operation.
 *
 * ## And a copy of the last one
 *
 * Atomicity protects against a torn write, not a wrong one. A bug in the encoder
 * overwrites the only copy with something well-formed and false, so the version
 * being replaced is kept as `.bak` first. One generation: the ledger is its own
 * history, and this is only here to survive the save that should not have
 * happened.
 */
class FileLedgerStore(private val file: File) : LedgerStore {

    private val backup = File(file.parentFile, "${file.name}.bak")

    override fun read(): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    /** The copy of the version before the current one, if there is one. */
    fun readBackup(): String? =
        if (backup.exists()) runCatching { backup.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)

        // Best-effort: failing to make a backup is not a reason to refuse to
        // save the ledger.
        if (file.exists()) {
            runCatching {
                Files.copy(
                    file.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun clear() {
        file.delete()
        backup.delete()
    }
}
