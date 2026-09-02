package com.radarproxy

import com.radarproxy.core.parser.ProxyParser
import com.radarproxy.core.telegram.TelegramLinkBuilder
import com.radarproxy.domain.*
import org.junit.Assert.*
import org.junit.Test

class RadarProxyCoreTest {
    private val parser = ProxyParser()

    @Test fun rawTelegramSchemesDeduplicate() {
        val secret = "abcdef0123456789abcdef0123456789"
        val result = parser.parse("https://t.me/proxy?server=Example.com&port=443&secret=$secret\ntg://proxy?server=example.com&port=443&secret=$secret\nt.me/proxy?server=example.com&port=443&secret=$secret", "official")
        assertEquals(1, result.size)
    }

    @Test fun allTelegramSubscriptionSchemesNormalizeToOneProxy() {
        val secret = "abcdef0123456789abcdef0123456789"
        val links = listOf(
            "http://t.me/proxy?server=example.com&port=443&secret=$secret",
            "https://telegram.me/proxy?server=example.com&port=443&secret=$secret",
            "telegram.me/proxy?server=example.com&port=443&secret=$secret",
            "tg://proxy?server=example.com&port=443&secret=$secret"
        ).joinToString("\n")
        assertEquals(1, parser.parse(links, "source").size)
    }

    @Test fun urlEncodedBase64PlusIsNotTurnedIntoSpace() {
        val proxy = parser.parse("https://t.me/proxy?server=example.com&port=443&secret=%2B%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7%2B%2Fv7w%3D%3D", "source").single()
        assertEquals(ProxyType.MT_PROTO, proxy.type)
    }

    @Test fun base64MtProtoSecretIsParsed() {
        val proxy = parser.parse("tg://proxy?server=example.com&port=443&secret=MDEyMzQ1Njc4OWFiY2RlZg==", "source").single()
        assertEquals(ProxyType.MT_PROTO, proxy.type)
        val liveShape = parser.parse("tg://proxy?server=example.com&port=443&secret=3rbz3hntxabj6l5bqipx2_s=", "source").single()
        assertEquals(ProxyType.MT_PROTO, liveShape.type)
    }

    @Test fun hexAndBase64SecretsShareCanonicalIdentity() {
        val hex = parser.parse("tg://proxy?server=example.com&port=443&secret=30313233343536373839616263646566", "source").single()
        val base64 = parser.parse("tg://proxy?server=example.com&port=443&secret=MDEyMzQ1Njc4OWFiY2RlZg==", "source").single()
        assertEquals(hex.id, base64.id)
    }

    @Test fun rawSocksLineIsParsed() {
        val proxy = parser.parse("https://t.me/socks?server=example.com&port=1080&user=alice&pass=p%26x", "source").single()
        assertEquals(ProxyType.SOCKS5, proxy.type)
        assertEquals("alice", proxy.username)
        assertEquals("p&x", proxy.password)
    }

    @Test fun invalidLinesAreIgnored() {
        assertTrue(parser.parse("hello\nhttps://t.me/proxy?server=x&port=no&secret=z", "source").isEmpty())
        assertTrue(parser.parse("https://t.me/proxy?server=x&port=443&secret=not-a-valid-secret", "source").isEmpty())
    }

    @Test fun unauthenticatedSocks5LinkIsParsed() {
        val proxy = parser.parse("tg://socks?server=example.com&port=1080", "source").single()
        assertEquals(ProxyType.SOCKS5, proxy.type)
        assertNull(proxy.username)
        assertNull(proxy.password)
    }

    @Test fun credentialChangesChangeStableId() {
        val a = parser.parse("tg://socks?server=x&port=1&user=u&pass=a", "s").single()
        val b = parser.parse("tg://socks?server=x&port=1&user=u&pass=b", "s").single()
        assertNotEquals(a.id, b.id)
    }

    @Test fun linkBuilderEncodesSensitiveCharacters() {
        val p = ProxyEntry("1", ProxyType.SOCKS5, "example.com", 1080, username = "a b", password = "p&x", sourceId = "s", firstSeen = 0, lastSeen = 0)
        val url = TelegramLinkBuilder.buildUrl(p)
        assertTrue(url.startsWith("tg://socks?"))
        assertTrue(url.contains("a%20b"))
        assertTrue(url.contains("p%26x"))
    }
}
