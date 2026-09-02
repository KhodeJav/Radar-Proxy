package com.radarproxy.domain

import java.security.MessageDigest

enum class ProxyType { MT_PROTO, SOCKS5 }
enum class PingStatus { UNTESTED, TESTING, ONLINE, SLOW, TIMEOUT, PROTOCOL_ERROR, INVALID_PROXY, FAILED }
enum class SortMode { DEFAULT, FASTEST, SLOWEST, NEWEST, OLDEST }
enum class AppLanguage { ENGLISH, PERSIAN }
enum class AppTheme { LIGHT, DARK, AURA, DISCORD }

data class ProxyEntry(
    val id: String, val type: ProxyType, val server: String, val port: Int,
    val secret: String? = null, val username: String? = null, val password: String? = null,
    val sourceId: String, val firstSeen: Long, val lastSeen: Long,
    val pingMs: Long? = null, val pingStatus: PingStatus = PingStatus.UNTESTED,
    val lastPingTime: Long? = null
)

object ProxyIdentity {
    fun stableId(type: ProxyType, server: String, port: Int, secret: String?, username: String?, password: String?): String {
        val material = listOf(type.name.lowercase(), server.trim().lowercase(), port.toString(), normalizeSecret(secret), username.orEmpty().trim(), password.orEmpty().trim()).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSecret(value: String?): String {
        val clean = value.orEmpty().trim().removePrefix("0x").replace(" ", "")
        val hex = runCatching {
            if (!clean.matches(Regex("(?i)[0-9a-f]{32}([0-9a-f]{2})?"))) return@runCatching null
            val raw = ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            raw.copyOfRange(if (raw.size == 17) 1 else 0, raw.size).joinToString("") { "%02x".format(it) }
        }.getOrNull()
        if (hex != null) return hex
        val base64 = runCatching {
            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
            val raw = runCatching { java.util.Base64.getUrlDecoder().decode(padded) }
                .getOrElse { java.util.Base64.getDecoder().decode(padded) }
            require(raw.size == 16 || raw.size == 17)
            raw.copyOfRange(if (raw.size == 17) 1 else 0, raw.size).joinToString("") { "%02x".format(it) }
        }.getOrNull()
        return base64 ?: clean.lowercase()
    }
}
