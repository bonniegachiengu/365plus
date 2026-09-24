package online.vyybandasky.plus365.server

import java.security.SecureRandom
import java.util.Base64

class DeviceSessions {
    private val random = SecureRandom()
    private val sessions = mutableSetOf<String>()

    @Synchronized
    fun issue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
            .also { sessions += it }
    }

    @Synchronized
    fun contains(token: String): Boolean = token in sessions
}
