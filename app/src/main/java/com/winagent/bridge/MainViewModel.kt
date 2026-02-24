package com.winagent.bridge

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.winagent.bridge.telephony.CallLogReader

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepo(app.applicationContext)

    val sendLiveCalls: StateFlow<Boolean> = repo.sendLiveCallsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val sendMissedCalls: StateFlow<Boolean> = repo.sendMissedCallsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val sendNotifications: StateFlow<Boolean> = repo.sendNotificationsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pairedPc: StateFlow<PairedPc?> = repo.pairedPcFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var pairingStatus: String by mutableStateOf("Not paired")
        private set
    fun refreshPairingStatus() {
        val pc = pairedPc.value
        pairingStatus = if (pc == null) "Not paired" else "Paired: ${pc.name ?: pc.ip}:${pc.port}"
    }

    fun isNotificationListenerEnabled(): Boolean {
        val ctx = getApplication<Application>().applicationContext
        return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    fun hasPermission(p: String): Boolean {
        val ctx = getApplication<Application>().applicationContext
        return ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    }

    fun setSendLiveCalls(enabled: Boolean) {
        viewModelScope.launch { repo.setSendLiveCalls(enabled) }
    }

    fun setSendMissedCalls(enabled: Boolean) {
        viewModelScope.launch { repo.setSendMissedCalls(enabled) }
    }

    fun setSendNotifications(enabled: Boolean) {
        viewModelScope.launch { repo.setSendNotifications(enabled) }
    }

    fun clearPair() {
        viewModelScope.launch { repo.setPairedPc(null) }
    }

    fun startDiscovery(timeoutMs: Long = 30_000L) {
        viewModelScope.launch {
            pairingStatus = "Scanning LAN…"
            val ctx = getApplication<Application>().applicationContext
            val deviceId = repo.ensureDeviceId()
            val name = android.os.Build.MODEL ?: "Android"
            val found = UdpDiscovery(ctx).discover(deviceId, name, timeoutMs = timeoutMs)
            if (found == null) {
                pairingStatus = "No PC found (is WinAgent broadcasting/responding?)"
            } else {
                repo.setPairedPc(found)
                pairingStatus = "Paired: ${found.name ?: found.ip}:${found.port}"
            }
        }
    }

    /**
     * Test helper: re-read current missed calls and active notifications, package them
     * and push a single full state packet.
     */
    fun triggerSendNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext

            // Refresh missed calls best-effort
            if (hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
                val calls = CallLogReader.queryMissedCalls(ctx, limit = 20, onlyNew = true)
                BridgeState.setMissedCalls(
                    calls.map {
                        BridgeState.MissedCallItem(
                            id = it.id,
                            number = it.number,
                            name = it.cachedName,
                            dateMs = it.dateMs,
                            durationSec = it.durationSec,
                            isNew = it.isNew
                        )
                    }
                )
            }

            // Ask the NotificationListenerService to refresh active notifications and send.
            // If the service is not enabled, we still send whatever we have in memory.
            val intent = Intent(Actions.ACTION_REFRESH_NOTIFICATIONS)
                .setPackage(ctx.packageName)
                .putExtra(Actions.EXTRA_FORCE_SEND, true)
            try {
                ctx.sendBroadcast(intent)
            } catch (_: Throwable) {
                // ignore
            }

            if (!isNotificationListenerEnabled()) {
                EventDispatcher.sendStateAsync()
            }
        }
    }

}
