package com.winagent.bridge

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscovery(private val context: Context) {

    suspend fun discover(deviceId: String, deviceName: String, timeoutMs: Long = 10_000L): PairedPc? = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("wa-bridge-discovery")
        lock.setReferenceCounted(true)
        lock.acquire()

        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 1000

                val broadcastAddr = computeBroadcastAddress(wifi)
                val reqBytes = Messages.discoverReq(deviceId, deviceName)
                val out = DatagramPacket(reqBytes, reqBytes.size, broadcastAddr, Protocol.DISCOVERY_UDP_PORT)
                sock.send(out)

                val buf = ByteArray(4096)
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeoutMs) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        val pc = parseDiscoverResWithFallback(pkt)
                        if (pc != null) return@withContext pc
                    } catch (_: java.net.SocketTimeoutException) {
                        // keep waiting
                    }
                }
                return@withContext null
            }
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    private fun parseDiscoverResWithFallback(pkt: DatagramPacket): PairedPc? {
        val s = try { String(pkt.data, 0, pkt.length, Charsets.UTF_8) } catch (_: Throwable) { return null }
        return try {
            val o = JSONObject(s)
            if (o.optString(Protocol.F_TYPE) != Protocol.T_DISCOVER_RES) return null
            val ip = o.optString("ip").ifBlank { pkt.address?.hostAddress ?: "" }
            val port = o.optInt("port", Protocol.DEFAULT_PC_PORT)
            if (ip.isBlank()) return null
            val name = o.optString("pcName").ifBlank { null }
            val token = o.optString("token").ifBlank { null }
            PairedPc(ip = ip, port = port, name = name, token = token)
        } catch (_: Throwable) {
            null
        }
    }

    private fun computeBroadcastAddress(wifi: WifiManager): InetAddress {
        return try {
            val dhcp = wifi.dhcpInfo
            if (dhcp == null) return InetAddress.getByName("255.255.255.255")
            val ip = dhcp.ipAddress
            val mask = dhcp.netmask
            val bcast = (ip and mask) or mask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = ((bcast shr (k * 8)) and 0xFF).toByte()
            }
            InetAddress.getByAddress(quads)
        } catch (_: Throwable) {
            InetAddress.getByName("255.255.255.255")
        }
    }
}
