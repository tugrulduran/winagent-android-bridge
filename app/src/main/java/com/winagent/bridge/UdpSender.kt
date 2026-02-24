package com.winagent.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object UdpSender {
    suspend fun send(ip: String, port: Int, payload: ByteArray) = withContext(Dispatchers.IO) {
        val addr = InetAddress.getByName(ip)
        DatagramSocket().use { sock ->
            val pkt = DatagramPacket(payload, payload.size, addr, port)
            sock.send(pkt)
        }
    }
}
