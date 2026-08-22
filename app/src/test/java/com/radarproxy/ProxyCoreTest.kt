package com.radarproxy

import com.radarproxy.core.parser.ProxyParser
import com.radarproxy.core.telegram.TelegramLinkBuilder
import com.radarproxy.domain.*
import org.junit.Assert.*
import org.junit.Test

class ProxyCoreTest {
    private val parser = ProxyParser()
    @Test fun rawHttpsAndTgMtProtoDeduplicate() {
        val secret = "abcdef0123456789abcdef0123456789"
        val result = parser.parse("https://t.me/proxy?server=Example.com&port=443&secret=$secret\ntg://proxy?server=example.com&port=443&secret=$secret\nt.me/proxy?server=example.com&port=443&secret=$secret", "official")
        assertEquals(1, result.size)
    }
    @Test fun socksVariantsAreParsedAndSensitiveFieldsStayInModel() {
        val p = parser.parse("https://t.me/socks?server=example.com&port=1080&user=alice&pass=p%26x", "s").single()
        assertEquals(ProxyType.SOCKS5, p.type); assertEquals("alice", p.username); assertEquals("p&x", p.password)
    }
    @Test fun credentialsChangeStableId() {
        val one = parser.parse("tg://socks?server=x&port=1&user=u&pass=a", "s").single()
        val two = parser.parse("tg://socks?server=x&port=1&user=u&pass=b", "s").single()
        assertNotEquals(one.id, two.id)
    }
    @Test fun garbageAndBadPortAreIgnored() { assertTrue(parser.parse("hello\nhttps://t.me/proxy?server=x&port=no&secret=z", "s").isEmpty()) }
    @Test fun linkBuilderEncodesSpecialCredentials() {
        val p = ProxyEntry("1", ProxyType.SOCKS5, "example.com", 1080, username = "a b", password = "p&x", sourceId = "s", firstSeen = 0, lastSeen = 0)
        val url = TelegramLinkBuilder.buildUrl(p)
        assertTrue(url.contains("a%20b")); assertTrue(url.contains("p%26x"))
    }
}
