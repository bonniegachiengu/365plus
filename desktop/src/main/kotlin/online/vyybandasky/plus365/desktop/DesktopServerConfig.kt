package online.vyybandasky.plus365.desktop

const val DEFAULT_SERVER_URL = "http://127.0.0.1:8443"

data class DesktopServerConfig(
    val serverUrl: String,
    val pairingCode: String,
) {
    companion object {
        fun fromEnvironment(): DesktopServerConfig? =
            fromValues(
                serverUrl = System.getenv("365PLUS_SERVER_URL"),
                pairingCode = System.getenv("365PLUS_PAIRING_CODE"),
            )

        fun fromValues(
            serverUrl: String?,
            pairingCode: String?,
        ): DesktopServerConfig? {
            val code = pairingCode
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            val url = serverUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_SERVER_URL

            return DesktopServerConfig(
                serverUrl = url,
                pairingCode = code,
            )
        }
    }
}
