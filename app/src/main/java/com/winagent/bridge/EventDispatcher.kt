package com.winagent.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Central place to push state packets to the paired PC.
 *
 * Requirement:
 * - Discovery is UDP.
 * - Data channel can be TCP; we send a single JSON packet per event.
 */
object EventDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sends the full packet:
     * { notifications:[...], missedCalls:[...], phoneRinging:true/false }
     */
    fun sendStateAsync() {
        scope.launch {
            val settings = AppGlobals.settings
            val pc = settings.getPairedPc() ?: return@launch
            val snapshot = BridgeState.snapshot()
            val bytes = Messages.statePacket(snapshot)
            try {
                TcpSender.sendOnce(pc.ip, pc.port, bytes)
            } catch (_: Throwable) {
                // ignore network errors
            }
        }
    }
}
