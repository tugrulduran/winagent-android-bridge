package com.winagent.bridge

import org.json.JSONObject

object Protocol {
    const val VERSION = 1
    const val DISCOVERY_UDP_PORT = 45151
    // We keep the same numeric default as earlier, but events are pushed over TCP.
    const val DEFAULT_PC_PORT = 45152

    // JSON field names
    const val F_TYPE = "t"

    const val T_DISCOVER_REQ = "discover_req"
    const val T_DISCOVER_RES = "discover_res"
    // Event channel sends a full "state" packet. We keep only the discovery types here.
}

object Messages {
    fun discoverReq(deviceId: String, deviceName: String): ByteArray {
        val o = JSONObject()
        o.put(Protocol.F_TYPE, Protocol.T_DISCOVER_REQ)
        o.put("v", Protocol.VERSION)
        o.put("deviceId", deviceId)
        o.put("deviceName", deviceName)
        // Hint: PC side can reply to source IP:port
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Full state packet required by the PC plugin:
     * {
     *   "notifications": [...],
     *   "missedCalls": [...],
     *   "phoneRinging": true/false
     * }
     */
    fun statePacket(snapshot: BridgeSnapshot): ByteArray {
        val o = JSONObject()

        o.put("phoneRinging", snapshot.phoneRinging)

        if (snapshot.phoneRinging && !snapshot.callFrom.isNullOrBlank()) {
            o.put("callFrom", snapshot.callFrom)
        }

        val missed = org.json.JSONArray()
        for (c in snapshot.missedCalls) {
            val mc = JSONObject()
            mc.put("id", c.id)
            if (!c.number.isNullOrBlank()) mc.put("number", c.number)
            if (!c.name.isNullOrBlank()) mc.put("name", c.name)
            mc.put("dateMs", c.dateMs)
            mc.put("durationSec", c.durationSec)
            mc.put("isNew", c.isNew)
            missed.put(mc)
        }
        o.put("missedCalls", missed)

        val notifs = org.json.JSONArray()
        for (n in snapshot.notifications) {
            val nn = JSONObject()
            // Unique notification id (later you can act on it from dashboard)
            nn.put("id", n.id)
            nn.put("package", n.packageName)
            nn.put("postTime", n.postTime)
            n.title?.let { nn.put("title", it) }
            n.text?.let { nn.put("text", it) }
            n.androidId?.let { nn.put("androidId", it) }
            notifs.put(nn)
        }
        o.put("notifications", notifs)

        return o.toString().toByteArray(Charsets.UTF_8)
    }

    fun tryParseDiscoverRes(bytes: ByteArray, len: Int): PairedPc? {
        return try {
            val s = String(bytes, 0, len, Charsets.UTF_8)
            val o = JSONObject(s)
            if (o.optString(Protocol.F_TYPE) != Protocol.T_DISCOVER_RES) return null
            val ip = o.optString("ip")
            val port = o.optInt("port", Protocol.DEFAULT_PC_PORT)
            if (ip.isBlank()) return null
            val name = o.optString("pcName").ifBlank { null }
            val token = o.optString("token").ifBlank { null }
            PairedPc(ip = ip, port = port, name = name, token = token)
        } catch (_: Throwable) {
            null
        }
    }
}
