package online.vyybandasky.plus365.desktop.store

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import online.vyybandasky.plus365.core.store.LedgerStore

/**
 * The master copy on the laptop.
 *
 * Written whole, to a temporary file, then moved over the real one, so what is on
 * disk is always a complete book — never half of one.
 *
 * ## The window that used to exist
 *
 * `File.renameTo` refuses to replace an existing file on Windows, so the old
 * fallback was `delete()` then `renameTo()`. Between those two calls there is no
 * ledger at all: a crash, a power cut, or the process being killed right there
 * loses three people's entire money record, and the previous version has already
 * gone. Small window, total loss.
 *
 * `Files.move` with `ATOMIC_MOVE` replaces in one operation, so that window does
 * not exist. Where a filesystem cannot do it atomically it falls back to a
 * replacing move, which is still a single call rather than two.
 *
 * ## And a copy of the last one
 *
 * Atomicity protects against a *torn* write. It does nothing about a *wrong*
 * one — a bug in the encoder, or a book that was already wrong in memory,
 * overwrites the only copy with something well-formed and false.
 *
 * So the version being replaced is kept as `.bak` first. One generation, not a
 * history: the ledger is its own history, and this is only here to survive the
 * save that should not have happened.
 */
class FileLedgerStore(private val file: File) : LedgerStore {

    private val backup = File(file.parentFile, "${file.name}.bak")

    init {
        file.parentFile?.mkdirs()
    }

    override fun read(): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    /** The copy of the version before the current one, if there is one. */
    fun readBackup(): String? =
        if (backup.exists()) runCatching { backup.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)

        // Keep what is about to be replaced. Best-effort: failing to make a
        // backup is not a reason to refuse to save the ledger.
        if (file.exists()) {
            runCatching {
                Files.copy(
                    file.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        moveOver(tmp, file)
    }

    override fun clear() {
        file.delete()
        backup.delete()
    }

    private fun moveOver(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
