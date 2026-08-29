package online.vyybandasky.plus365.store

import java.io.File
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
 * ## No `java.nio.file` here
 *
 * The laptop's store uses `Files.move` with `ATOMIC_MOVE`, because Windows'
 * `File.renameTo` refuses to replace an existing file and the delete-then-rename
 * fallback leaves a window with no ledger in it at all.
 *
 * Android has neither the problem nor room for that cure. `java.nio.file` needs
 * API 26 and this app's `minSdk` is 24, so the same code here compiles cleanly
 * and then throws at the moment of saving on an Android 7 phone — the one moment
 * where failing costs somebody an entry. Lint caught it; the unit tests could
 * not, because they run on a desktop JVM where the class exists.
 *
 * It does not need it either. `rename(2)` on a POSIX filesystem replaces the
 * destination atomically, which is the guarantee the laptop had to reach for NIO
 * to obtain. The delete-then-rename path below is a last resort for a filesystem
 * that refuses a replacing rename, and on Android it should never be reached.
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
    override fun readBackup(): String? =
        if (backup.exists()) runCatching { backup.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)

        // Best-effort: failing to make a backup is not a reason to refuse to
        // save the ledger. copyTo is Kotlin stdlib and has no API floor.
        if (file.exists()) {
            runCatching { file.copyTo(backup, overwrite = true) }
        }

        if (!tmp.renameTo(file)) {
            // Only reachable on a filesystem that will not replace on rename.
            // There is a moment in here with no ledger on disk, which is why it
            // is the fallback and not the path.
            file.delete()
            tmp.renameTo(file)
        }
    }

    override fun clear() {
        file.delete()
        backup.delete()
    }
}
