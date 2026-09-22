package online.vyybandasky.plus365.server

import java.io.File
import online.vyybandasky.plus365.core.DevSeed
import online.vyybandasky.plus365.core.book.LedgerBook
import online.vyybandasky.plus365.core.store.LedgerStore
import online.vyybandasky.plus365.core.store.encodeBook
import online.vyybandasky.plus365.core.store.open

class ServerRuntime(
    val dataDir: File,
    val port: Int = DEFAULT_PORT,
    val host: String = DEFAULT_HOST,
    private val store: LedgerStore = ServerFileLedgerStore(
        File(dataDir, "ledger.json"),
    ),
) {
    private val ledgerFile = File(dataDir, "ledger.json")

    private val opened = store.open {
        DevSeed.book()
    }

    private val hub = SyncHub(
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

    val pairingCode: String = ServerPairingCode.loadOrCreate(dataDir)

    val server = buildServer(
        port = port,
        host = host,
        hub = hub,
        pairingCode = pairingCode,
    )

    fun start() {
        server.start(wait = false)
    }

    fun stop(
        gracePeriodMillis: Long = 100,
        timeoutMillis: Long = 1_000,
    ) {
        server.stop(gracePeriodMillis, timeoutMillis)
    }

    fun ledgerFile(): File = ledgerFile
}

fun defaultServerDataDir(): File =
    File(
        System.getProperty("user.home"),
        ".365plus-server",
    )
