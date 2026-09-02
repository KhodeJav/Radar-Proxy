package com.radarproxy

import com.radarproxy.core.ping.ProxyPingEngine
import com.radarproxy.domain.ProxyEntry
import com.radarproxy.domain.ProxyType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ProxyPingEngineTest {
    @Test
    fun socks5RequiresTelegramResPqResponse() = runBlocking {
        val server = ServerSocket(0)
        val serverError = AtomicReference<Throwable?>(null)
        val worker = thread(start = true, name = "fake-telegram-socks") {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    check(input.readExact(4)[0].toInt() == 5)
                    output.write(byteArrayOf(5, 0))
                    output.flush()

                    check(input.readExact(10)[1].toInt() == 1)
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 1, 187.toByte()))
                    output.flush()

                    check(input.read() == 0xef)
                    val units = input.read()
                    check(units > 0)
                    check(input.readExact(units * 4).size == 40)

                    val response = ByteArray(40)
                    ByteBuffer.wrap(response, 8, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(1L)
                    ByteBuffer.wrap(response, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(4)
                    ByteBuffer.wrap(response, 20, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x05162463)
                    output.write(10)
                    output.write(response)
                    output.flush()
                }
            } catch (error: Throwable) {
                serverError.set(error)
            }
        }

        val proxy = ProxyEntry("local", ProxyType.SOCKS5, "127.0.0.1", server.localPort, sourceId = "test", firstSeen = 0, lastSeen = 0)
        val result = ProxyPingEngine().test(proxy, 1_000)
        worker.join(2_000)
        server.close()
        serverError.get()?.let { throw it }
        assertTrue("Expected Telegram resPQ handshake success: $result", result.isSuccess)
    }

    private fun java.io.InputStream.readExact(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            check(count > 0)
            offset += count
        }
        return bytes
    }
}
