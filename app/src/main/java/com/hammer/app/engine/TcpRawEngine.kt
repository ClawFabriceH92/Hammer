package com.hammer.app.engine

import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom

object TcpRawEngine {

    fun execute(config: RunConfig): RequestOutcome {
        val (host, port) = hostAndPort(config.target)
        val packet = buildPacket(config.tcpPacketSizeBytes, config.tcpPacketPattern)

        val startNanos = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), config.timeoutMillis.toInt())
                socket.soTimeout = config.timeoutMillis.toInt()
                socket.getOutputStream().apply {
                    write(packet)
                    flush()
                }
            }
            RequestOutcome(true, elapsedMillis(startNanos), ErrorCategory.NONE)
        } catch (e: SocketTimeoutException) {
            RequestOutcome(false, elapsedMillis(startNanos), ErrorCategory.TIMEOUT)
        } catch (e: ConnectException) {
            RequestOutcome(false, elapsedMillis(startNanos), ErrorCategory.CONNECTION_REFUSED)
        } catch (e: IOException) {
            val category = if (e.message?.contains("reset", ignoreCase = true) == true) {
                ErrorCategory.RESET
            } else {
                ErrorCategory.OTHER
            }
            RequestOutcome(false, elapsedMillis(startNanos), category)
        }
    }

    private fun hostAndPort(target: TargetConfig): Pair<String, Int> = when (target) {
        is TargetConfig.LocalIp -> target.ip to target.port
        is TargetConfig.Website -> target.host to target.port
    }

    private fun buildPacket(sizeBytes: Int, pattern: PacketPattern): ByteArray {
        val clamped = sizeBytes.coerceIn(64, 65536)
        return when (pattern) {
            PacketPattern.ZERO -> ByteArray(clamped)
            PacketPattern.RANDOM -> ByteArray(clamped).also { SecureRandom().nextBytes(it) }
            PacketPattern.TEXT -> {
                val text = "HAMMER-TCP-RAW-STRESS-TEST-PACKET-"
                ByteArray(clamped) { text[it % text.length].code.toByte() }
            }
        }
    }

    private fun elapsedMillis(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000
}
