package com.radarproxy.core.telegram

import android.net.Uri
import com.radarproxy.domain.ProxyEntry
import com.radarproxy.domain.ProxyType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TelegramLinkBuilder {
    fun build(proxy: ProxyEntry): Uri = Uri.parse(buildUrl(proxy))
    fun buildUrl(proxy: ProxyEntry): String {
        fun e(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return when (proxy.type) {
            ProxyType.MT_PROTO -> "tg://proxy?server=${e(proxy.server)}&port=${proxy.port}&secret=${e(requireNotNull(proxy.secret))}"
            ProxyType.SOCKS5 -> "tg://socks?server=${e(proxy.server)}&port=${proxy.port}&user=${e(proxy.username.orEmpty())}&pass=${e(proxy.password.orEmpty())}"
        }
    }
}
