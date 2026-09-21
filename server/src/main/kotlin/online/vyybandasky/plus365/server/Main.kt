package online.vyybandasky.plus365.server

import java.io.File
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.store.encodeBook
import online.vyybandasky.plus365.core.store.open

fun main(args: Array<String>) {
    val port = args
        .firstOrNull { it.startsWith("--port=") }
        ?.substringAfter("=")
        ?.toIntOrNull()
        ?: DEFAULT_PORT

    val host = args
        .firstOrNull { it.startsWith("--host=") }
        ?.substringAfter("=")
        ?: DEFAULT_HOST

    val dataDir = File(
        System.getProperty("user.home"),
        ".365plus-server",
    )

    val store = ServerFileLedgerStore(
        File(dataDir, "ledger.json"),
    )

    val opened = store.open {
        DevSeed.book()
    }

    val hub = SyncHub(
        object : ServerLedger {
            private var current: LedgerBook = opened.book

            override fun read(): LedgerBook = current

            override fun write(book: LedgerBook): Boolean =
                runCatching {
                    store.write(encodeBook(book))
                    current = book
                    true
                }.getOrDefault(false)
        },
    )

    val pairingCode = ServerPairingCode.loadOrCreate(dataDir)

    startServer(
        port = port,
        host = host,
        hub = hub,
        pairingCode = pairingCode,
    )

    println("365+ server running on $host:$port")
    println("Data: ${File(dataDir, "ledger.json").absolutePath}")
    println("Pairing code: $pairingCode")

    Thread.currentThread().join()
}
