package com.radarproxy.core.parser

import com.radarproxy.domain.ProxyEntry
import com.radarproxy.domain.ProxyIdentity
import com.radarproxy.domain.ProxyType
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ProxyParser {
    private val linkPattern = Regex("(?i)(?:(?:https?://)?(?:t\\.me|telegram\\.me)/(?:proxy|socks)|tg://(?:proxy|socks))\\?[^\\s<>\\\"']+")

    fun parse(text: String, sourceId: String, now: Long = System.currentTimeMillis()): List<ProxyEntry> {
        val unique = LinkedHashMap<String, ProxyEntry>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@forEach
            val candidates = linkPattern.findAll(line).map { clean(it.value) }.toList().ifEmpty { listOf(clean(line)) }
            candidates.forEach { candidate ->
                parseOne(candidate, sourceId, now)?.let { unique.putIfAbsent(it.id, it) }
            }
        }
        return unique.values.toList()
    }

    private fun clean(value: String): String = value.trim()
        .trimEnd(',', ';', ')', ']', '}', '>', '،', '؛')
        .replace("&amp;", "&", ignoreCase = true)
        .let {
            when {
                it.startsWith("t.me/", true) || it.startsWith("telegram.me/", true) -> "https://$it"
                it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("tg://", true) -> it
                else -> it
            }
        }

    private fun parseOne(value: String, sourceId: String, now: Long): ProxyEntry? = runCatching {
        val uri = URI(value)
        val endpoint = uri.path.trim('/').lowercase().ifBlank { uri.host?.lowercase().orEmpty() }
        val type = when (endpoint) {
            "proxy" -> ProxyType.MT_PROTO
            "socks" -> ProxyType.SOCKS5
            else -> return null
        }
        val params = uri.rawQuery.orEmpty().split('&').asSequence().mapNotNull { pair ->
            val split = pair.indexOf('=')
            if (split <= 0) null else decode(pair.substring(0, split)).lowercase() to decode(pair.substring(split + 1))
        }.toMap()
        val server = params["server"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = params["port"]?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val secret = params["secret"]?.trim()?.takeIf { it.isNotEmpty() }
        val username = params["user"]?.trim()?.takeIf { it.isNotEmpty() }
        val password = params["pass"]?.takeIf { it.isNotEmpty() }
        if (type == ProxyType.MT_PROTO && (secret == null || !isValidSecret(secret))) return null
        if (type == ProxyType.SOCKS5 && ((username == null) != (password == null))) return null
        ProxyEntry(
            ProxyIdentity.stableId(type, server, port, secret, username, password),
            type, server, port, secret, username, password, sourceId, now, now
        )
    }.getOrNull()

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun isValidSecret(value: String): Boolean {
        val clean = value.trim().removePrefix("0x").replace(" ", "")
        if (clean.matches(Regex("(?i)[0-9a-f]{32}([0-9a-f]{2})?"))) return true
        return runCatching {
            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
            val raw = runCatching { java.util.Base64.getUrlDecoder().decode(padded) }
                .getOrElse { java.util.Base64.getDecoder().decode(padded) }
            raw.size == 16 || raw.size == 17
        }.getOrDefault(false)
    }
}
