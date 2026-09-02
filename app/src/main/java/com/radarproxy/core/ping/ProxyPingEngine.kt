package com.radarproxy.core.ping

import android.net.Network
import com.radarproxy.domain.ProxyEntry
import com.radarproxy.domain.ProxyType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ProxyPingEngine(private val networkProvider: ActiveNetworkProvider? = null) {
    suspend fun test(proxy: ProxyEntry, timeoutMs: Int = 10_000, network: Network? = null): Result<Long> = withContext(Dispatchers.IO) {
        val perAttemptTimeout = timeoutMs.coerceAtLeast(1)
        var lastFailure: PingFailure? = null
        repeat(2) { attempt ->
            try {
                // A fresh Network snapshot and a fresh socket are used for every attempt.
                val route = network ?: networkProvider?.current()
                if (networkProvider != null && route == null) {
                    throw PingFailure(FailureKind.TRANSIENT, "No validated active network")
                }
                val latency = when (proxy.type) {
                    ProxyType.SOCKS5 -> socks5(proxy, route, perAttemptTimeout)
                    ProxyType.MT_PROTO -> mtProto(proxy, route, perAttemptTimeout)
                }
                return@withContext Result.success(latency)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val failure = classify(error)
                lastFailure = failure
                if (attempt == 0 && failure.retryable) delay(150L) else return@withContext Result.failure(failure)
            }
        }
        Result.failure(lastFailure ?: PingFailure(FailureKind.PROTOCOL, "Telegram handshake failed"))
    }

    private fun socks5(p: ProxyEntry, network: Network?, timeoutMs: Int): Long {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var lastFailure: PingFailure? = null
        for (target in SOCKS_TARGETS) {
            try {
                return socks5Target(p, network, target, deadline)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val failure = classify(error)
                // A malformed/decrypted Telegram packet is a protocol result, not
                // a reason to retry the same proxy against another destination.
                if (failure.kind == FailureKind.PROTOCOL || failure.kind == FailureKind.INVALID) throw failure
                lastFailure = failure
                if (remainingMillis(deadline) <= 1) break
            }
        }
        throw lastFailure ?: PingFailure(FailureKind.TRANSIENT, "Telegram DC endpoints unavailable")
    }

    private fun socks5Target(p: ProxyEntry, network: Network?, target: TelegramTarget, deadline: Long): Long {
        val socket = openConnected(network, p.server, p.port, deadline)
        socket.use {
            val input = it.getInputStream()
            val output = it.getOutputStream()
            output.write(byteArrayOf(5, 2, 0, 2))
            output.flush()
            val greeting = input.readExact(2, it, deadline)
            check((greeting[0].toInt() and 255) == 5) { "Invalid SOCKS5 version" }
            when (greeting[1].toInt() and 255) {
                0 -> Unit
                2 -> {
                    val user = p.username.orEmpty().toByteArray(Charsets.UTF_8)
                    val pass = p.password.orEmpty().toByteArray(Charsets.UTF_8)
                    require(user.size < 256 && pass.size < 256) { "Invalid SOCKS5 credentials" }
                    output.write(byteArrayOf(1, user.size.toByte()))
                    output.write(user)
                    output.write(byteArrayOf(pass.size.toByte()))
                    output.write(pass)
                    output.flush()
                    val auth = input.readExact(2, it, deadline)
                    check((auth[1].toInt() and 255) == 0) { "SOCKS5 authentication failed" }
                }
                else -> throw PingFailure(FailureKind.PROTOCOL, "SOCKS5 authentication method rejected")
            }

            // These are current DC2 access points. Each fallback still performs
            // the full Telegram abridged handshake; none is a TCP-only check.
            output.write(byteArrayOf(5, 1, 0, 1, *target.ipBytes, 1, 187.toByte()))
            output.flush()
            val reply = input.readExact(4, it, deadline)
            check((reply[0].toInt() and 255) == 5) { "Invalid SOCKS5 CONNECT response" }
            check((reply[1].toInt() and 255) == 0) { "SOCKS5 CONNECT failed" }
            val addressLength = when (reply[3].toInt() and 255) {
                1 -> 4
                3 -> input.readExact(1, it, deadline)[0].toInt() and 255
                4 -> 16
                else -> throw PingFailure(FailureKind.PROTOCOL, "Invalid SOCKS5 address type")
            }
            input.readExact(addressLength + 2, it, deadline)

            val started = System.nanoTime()
            val payload = buildReqPq()
            check(payload.size % 4 == 0) { "Invalid Telegram probe alignment" }
            output.write(0xef)
            output.write(payload.size / 4)
            output.write(payload)
            output.flush()
            readAbridgedTelegramResponse(input, it, deadline)
            return (System.nanoTime() - started) / 1_000_000L
        }
    }

    private data class TelegramTarget(val ipBytes: ByteArray)

    private val SOCKS_TARGETS = listOf(
        TelegramTarget(byteArrayOf(149.toByte(), 154.toByte(), 167.toByte(), 51.toByte())),
        TelegramTarget(byteArrayOf(149.toByte(), 154.toByte(), 167.toByte(), 40.toByte())),
        TelegramTarget(byteArrayOf(149.toByte(), 154.toByte(), 167.toByte(), 50.toByte()))
    )

    /** Performs MTProxy obfuscation and accepts only a valid decrypted Telegram response. */
    private fun mtProto(p: ProxyEntry, network: Network?, timeoutMs: Int): Long {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        val encodedSecret = decodeSecret(p.secret ?: throw PingFailure(FailureKind.INVALID, "MTProto secret missing"))
        val secret = encodedSecret.bytes
        // Telegram/TDLib use plain intermediate transport for the classic 16-byte
        // secret and padded-intermediate for the 17-byte marker form.
        val init = generateInit(2, encodedSecret.padded)
        val reversed = init.clone().also { it.reverse() }
        val encKey = sha256(init.copyOfRange(8, 40) + secret)
        val decKey = sha256(reversed.copyOfRange(8, 40) + secret)
        val outputCipher = aes(Cipher.ENCRYPT_MODE, encKey, init.copyOfRange(40, 56))
        val inputCipher = aes(Cipher.DECRYPT_MODE, decKey, reversed.copyOfRange(40, 56))
        val encryptedInit = outputCipher.update(init.clone()) ?: throw PingFailure(FailureKind.PROTOCOL, "MTProto encryption failed")
        System.arraycopy(encryptedInit, 56, init, 56, 8)
        val encryptedProbe = outputCipher.update(frame(buildReqPq(), encodedSecret.padded)) ?: throw PingFailure(FailureKind.PROTOCOL, "MTProto probe encryption failed")

        val socket = openConnected(
            network,
            p.server ?: throw PingFailure(FailureKind.INVALID, "MTProto server missing"),
            p.port,
            deadline
        )
        socket.use {
            val started = System.nanoTime()
            it.getOutputStream().apply { write(init); write(encryptedProbe); flush() }
            readMtProtoResponse(it, inputCipher, deadline, encodedSecret.padded)
            return (System.nanoTime() - started) / 1_000_000L
        }
    }

    private fun generateInit(dcId: Int, padded: Boolean): ByteArray {
        val random = SecureRandom()
        val init = ByteArray(64)
        while (true) {
            random.nextBytes(init)
            if (init[0].toInt() and 0xff == 0xef) continue
            val first = init.copyOfRange(0, 4)
            val blocked = listOf("HEAD", "POST", " GET", "OPTI").map { it.toByteArray() }
            if (blocked.any { first.contentEquals(it) }) continue
            if (first.contentEquals(byteArrayOf(0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte()))) continue
            if (first.contentEquals(byteArrayOf(0xee.toByte(), 0xee.toByte(), 0xee.toByte(), 0xee.toByte()))) continue
            if (first.contentEquals(byteArrayOf(0x16, 0x03, 0x01, 0x02))) continue
            if (init.copyOfRange(4, 8).all { it == 0.toByte() }) continue
            break
        }
        repeat(4) { init[56 + it] = if (padded) 0xdd.toByte() else 0xee.toByte() }
        init[60] = (dcId and 0xff).toByte()
        init[61] = ((dcId shr 8) and 0xff).toByte()
        return init
    }

    private fun buildReqPq(): ByteArray {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val body = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0xbe7e8ef1.toInt()).put(nonce).array()
        val now = System.currentTimeMillis()
        val msgId = (((now / 1000L) shl 32) + ((now % 1000L) * 0x100000000L / 1000L)) and -4L
        return ByteBuffer.allocate(8 + 8 + 4 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(0L).putLong(msgId).putInt(body.size).put(body).array()
    }

    private fun readAbridgedTelegramResponse(input: InputStream, socket: Socket, deadline: Long) {
        while (true) {
            val first = input.readExact(1, socket, deadline)[0].toInt() and 0xff
            // A quick ACK is a valid Telegram transport response, not a protocol error.
            if (first and 0x80 != 0) {
                input.readExact(3, socket, deadline)
                continue
            }
            val payloadLength = if (first == 0x7f) {
                val lengthBytes = input.readExact(3, socket, deadline)
                (lengthBytes[0].toInt() and 0xff) or
                    ((lengthBytes[1].toInt() and 0xff) shl 8) or
                    ((lengthBytes[2].toInt() and 0xff) shl 16)
            } else {
                check(first in 1..126) { "Invalid Telegram abridged response frame" }
                first * 4
            }
            check(payloadLength in 4..(1024 * 1024) && payloadLength % 4 == 0) { "Invalid Telegram response length" }
            val payload = input.readExact(payloadLength, socket, deadline)
            validateRawTelegramPacket(payload, "Telegram abridged response")
            return
        }
    }

    private fun readMtProtoResponse(socket: Socket, cipher: Cipher, deadline: Long, padded: Boolean) {
        val input = socket.getInputStream()
        while (true) {
            val encryptedHeader = input.readExact(4, socket, deadline)
            val header = cipher.update(encryptedHeader) ?: throw PingFailure(FailureKind.PROTOCOL, "MTProto response decryption failed")
            val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
            if (length and Int.MIN_VALUE != 0) continue // transport quick ACK
            check(length in 4..(1024 * 1024) && (!padded || length <= 4 + 1024 * 1024)) { "Invalid MTProto response frame" }
            val encryptedBody = input.readExact(length, socket, deadline)
            val body = cipher.update(encryptedBody) ?: throw PingFailure(FailureKind.PROTOCOL, "MTProto response decryption failed")
            // A padded-intermediate quick ACK is not the Telegram response; keep reading.
            if (body.size in 8..16 && ByteBuffer.wrap(body, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int == -1) continue
            validateRawTelegramPacket(body, "MTProto response")
            return
        }
    }

    private fun validateRawTelegramPacket(packet: ByteArray, label: String) {
        // TDLib's no-auth ping accepts a successfully parsed raw MTProto packet;
        // it does not require the body constructor to be exactly resPQ.
        if (packet.size == 4) {
            val errorCode = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).int
            throw PingFailure(FailureKind.PROTOCOL, "$label transport error $errorCode")
        }
        check(packet.size >= 24) { "$label is too short" }
        check(packet.copyOfRange(0, 8).all { it == 0.toByte() }) { "$label has an auth key" }
        val messageId = ByteBuffer.wrap(packet, 8, 8).order(ByteOrder.LITTLE_ENDIAN).long
        check(messageId != 0L) { "$label has an invalid message id" }
        val bodyLength = ByteBuffer.wrap(packet, 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        check(bodyLength >= 4 && bodyLength <= packet.size - 20) { "$label has an invalid body length" }
        // The constructor at offset 20 is intentionally not restricted to resPQ:
        // rpc_error/bad_msg_notification are still genuine Telegram responses.
    }

    private data class EncodedSecret(val bytes: ByteArray, val padded: Boolean)

    private fun decodeSecret(value: String): EncodedSecret {
        val clean = value.trim().removePrefix("0x").replace(" ", "")
        val bytes = if (clean.matches(Regex("(?i)[0-9a-f]{32}([0-9a-f]{2})?"))) {
            ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } else {
            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
            runCatching { java.util.Base64.getUrlDecoder().decode(padded) }
                .getOrElse { java.util.Base64.getDecoder().decode(padded) }
        }
        require(bytes.size == 16 || bytes.size == 17) { "Invalid MTProto secret" }
        return EncodedSecret(if (bytes.size == 17) bytes.copyOfRange(1, 17) else bytes, padded = bytes.size == 17)
    }

    private fun frame(payload: ByteArray, padded: Boolean): ByteArray {
        require(payload.size % 4 == 0) { "Invalid MTProto payload alignment" }
        if (!padded) {
            return ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(payload.size).put(payload).array()
        }
        val padding = SecureRandom().nextInt(4) * 4
        return ByteBuffer.allocate(4 + payload.size + padding).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size + padding).put(payload).put(ByteArray(padding).also { SecureRandom().nextBytes(it) }).array()
    }

    private fun openConnected(network: Network?, host: String, port: Int, deadline: Long): Socket {
        val addresses = networkProvider?.addresses(network, host) ?: java.net.InetAddress.getAllByName(host)
        var last: IOException? = null
        for (address in addresses) {
            val socket = networkProvider?.open(network) ?: Socket()
            try {
                socket.soTimeout = remainingMillis(deadline)
                socket.connect(InetSocketAddress(address, port), remainingMillis(deadline))
                return socket
            } catch (error: IOException) {
                last = error
                runCatching { socket.close() }
            }
        }
        throw last ?: ConnectException("Unable to connect to proxy")
    }

    private fun classify(error: Throwable): PingFailure = when (error) {
        is PingFailure -> error
        is SocketTimeoutException -> PingFailure(FailureKind.TIMEOUT, "Telegram handshake timed out", error)
        is ConnectException, is NoRouteToHostException, is SocketException, is EOFException, is UnknownHostException, is IOException -> PingFailure(FailureKind.TRANSIENT, "Telegram connection interrupted", error)
        is IllegalArgumentException -> PingFailure(FailureKind.INVALID, error.message ?: "Invalid proxy", error)
        else -> PingFailure(FailureKind.PROTOCOL, error.message ?: "Telegram protocol error", error)
    }

    private fun aes(mode: Int, key: ByteArray, iv: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun remainingMillis(deadline: Long): Int = ((deadline - System.nanoTime()) / 1_000_000L).toInt().coerceAtLeast(1)
    private fun InputStream.readExact(size: Int, socket: Socket, deadline: Long): ByteArray {
        val output = ByteArray(size)
        var offset = 0
        while (offset < size) {
            socket.soTimeout = remainingMillis(deadline)
            val count = read(output, offset, size - offset)
            if (count < 0) throw EOFException("Telegram connection closed")
            offset += count
        }
        return output
    }

    enum class FailureKind { TIMEOUT, TRANSIENT, PROTOCOL, INVALID }
    class PingFailure(val kind: FailureKind, message: String, cause: Throwable? = null) : IOException(message, cause) {
        val retryable: Boolean get() = kind == FailureKind.TIMEOUT || kind == FailureKind.TRANSIENT
    }
}
