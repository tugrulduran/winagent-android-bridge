package com.winagent.bridge

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sends a single JSON packet over TCP, then closes the socket.
 *
 * This matches the requirement:
 * - No continuous scanning / no long-lived connection.
 * - On interrupt-like events we push one full "state" packet.
 */
object TcpSender {

    @Throws(Throwable::class)
    fun sendOnce(ip: String, port: Int, jsonUtf8: ByteArray, connectTimeoutMs: Int = 1500, writeTimeoutMs: Int = 2000) {
        Socket().use { sock ->
            sock.tcpNoDelay = true
            sock.soTimeout = writeTimeoutMs
            sock.connect(InetSocketAddress(ip, port), connectTimeoutMs)
            val w = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
            w.write(String(jsonUtf8, Charsets.UTF_8))
            w.write("\n") // newline-delimited JSON
            w.flush()
        }
    }
}
