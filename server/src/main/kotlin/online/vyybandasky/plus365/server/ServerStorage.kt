package online.vyybandasky.plus365.server

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.random.Random
import online.vyybandasky.plus365.core.store.LedgerStore

class ServerFileLedgerStore(
    private val file: File,
) : LedgerStore {

    private val backup = File(file.parentFile, "${file.name}.bak")

    init {
        file.parentFile?.mkdirs()
    }

    override fun read(): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    override fun readBackup(): String? =
        if (backup.exists()) runCatching { backup.readText() }.getOrNull() else null

    override fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)

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
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    override fun clear() {
        file.delete()
        backup.delete()
    }
}

object ServerPairingCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun loadOrCreate(dir: File): String {
        val file = File(dir, "pairing-code.txt")
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrBlank()) return existing

        val made = (1..6)
            .map { ALPHABET[Random.nextInt(ALPHABET.length)] }
            .joinToString("")

        runCatching {
            dir.mkdirs()
            file.writeText(made)
        }

        return made
    }
}
