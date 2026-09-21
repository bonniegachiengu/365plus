package online.vyybandasky.plus365.server

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

    val runtime = ServerRuntime(
        dataDir = defaultServerDataDir(),
        port = port,
        host = host,
    )

    runtime.start()

    println("365+ server running on $host:$port")
    println("Data: ${runtime.ledgerFile().absolutePath}")
    println("Pairing code: ${runtime.pairingCode}")

    Thread.currentThread().join()
}
