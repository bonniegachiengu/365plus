package online.vyybandasky.plus365.server

import org.postgresql.ds.PGSimpleDataSource

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

    val usePostgres = args.any { it == "--postgres" }

    val runtime = if (usePostgres) {
        val dataSource = PGSimpleDataSource().apply {
            setServerNames(arrayOf(System.getenv("PGHOST") ?: "localhost"))
            setPortNumbers(
                intArrayOf(
                    System.getenv("PGPORT")?.toIntOrNull() ?: 5432,
                ),
            )
            databaseName = System.getenv("PGDATABASE") ?: "365plus"
            user = System.getenv("PGUSER")
                ?: error("PGUSER is required when --postgres is used")
            password = System.getenv("PGPASSWORD")
                ?: error("PGPASSWORD is required when --postgres is used")
        }

        ServerRuntime(
            dataDir = defaultServerDataDir(),
            port = port,
            host = host,
            store = PostgresLedgerStore(dataSource),
        )
    } else {
        ServerRuntime(
            dataDir = defaultServerDataDir(),
            port = port,
            host = host,
        )
    }

    runtime.start()

    println("365+ server running on $host:$port")
    println("Persistence: ${if (usePostgres) "PostgreSQL" else "file"}")
    println("Pairing code: ${runtime.pairingCode}")

    Thread.currentThread().join()
}
