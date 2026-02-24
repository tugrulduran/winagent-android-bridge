package com.winagent.bridge

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "wa_bridge_settings")

data class PairedPc(
    val ip: String,
    val port: Int,
    val name: String? = null,
    val token: String? = null
)

/**
 * Tiny settings repository backed by DataStore Preferences.
 * (Deliberately simple so you can add more tabs/features later.)
 */
class SettingsRepo(private val context: Context) {

    private object Keys {
        val sendLiveCalls = booleanPreferencesKey("send_live_calls")
        val sendMissedCalls = booleanPreferencesKey("send_missed_calls")
        val sendNotifications = booleanPreferencesKey("send_notifications")

        val pairedIp = stringPreferencesKey("paired_ip")
        val pairedPort = intPreferencesKey("paired_port")
        val pairedName = stringPreferencesKey("paired_name")
        val pairedToken = stringPreferencesKey("paired_token")

        val deviceId = stringPreferencesKey("device_id")
        val lastMissedCallId = longPreferencesKey("last_missed_call_id")
    }

    val sendLiveCallsFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.sendLiveCalls] ?: false }
    val sendMissedCallsFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.sendMissedCalls] ?: false }
    val sendNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.sendNotifications] ?: false }

    val pairedPcFlow: Flow<PairedPc?> = context.dataStore.data.map { prefs ->
        val ip = prefs[Keys.pairedIp] ?: return@map null
        val port = prefs[Keys.pairedPort] ?: Protocol.DEFAULT_PC_PORT
        val name = prefs[Keys.pairedName]
        val token = prefs[Keys.pairedToken]
        PairedPc(ip = ip, port = port, name = name, token = token)
    }

    val deviceIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.deviceId] ?: ""
    }

    suspend fun ensureDeviceId(): String {
        val existing = context.dataStore.data.map { it[Keys.deviceId] ?: "" }.firstOrNull() ?: ""
        if (existing.isNotBlank()) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.deviceId] = id }
        return id
    }

    suspend fun setSendLiveCalls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.sendLiveCalls] = enabled }
    }

    suspend fun setSendMissedCalls(enabled: Boolean) {
        context.dataStore.edit { it[Keys.sendMissedCalls] = enabled }
    }

    suspend fun setSendNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.sendNotifications] = enabled }
    }

    suspend fun setPairedPc(pc: PairedPc?) {
        context.dataStore.edit { prefs ->
            if (pc == null) {
                prefs.remove(Keys.pairedIp)
                prefs.remove(Keys.pairedPort)
                prefs.remove(Keys.pairedName)
                prefs.remove(Keys.pairedToken)
            } else {
                prefs[Keys.pairedIp] = pc.ip
                prefs[Keys.pairedPort] = pc.port
                pc.name?.let { prefs[Keys.pairedName] = it } ?: prefs.remove(Keys.pairedName)
                pc.token?.let { prefs[Keys.pairedToken] = it } ?: prefs.remove(Keys.pairedToken)
            }
        }
    }

    suspend fun getPairedPc(): PairedPc? {
        val prefs = context.dataStore.data.firstOrNull() ?: return null
        val ip = prefs[Keys.pairedIp] ?: return null
        val port = prefs[Keys.pairedPort] ?: Protocol.DEFAULT_PC_PORT
        val name = prefs[Keys.pairedName]
        val token = prefs[Keys.pairedToken]
        return PairedPc(ip = ip, port = port, name = name, token = token)
    }

    suspend fun getSendLiveCalls(): Boolean = context.dataStore.data.firstOrNull()?.get(Keys.sendLiveCalls) ?: false
    suspend fun getSendMissedCalls(): Boolean = context.dataStore.data.firstOrNull()?.get(Keys.sendMissedCalls) ?: false
    suspend fun getSendNotifications(): Boolean = context.dataStore.data.firstOrNull()?.get(Keys.sendNotifications) ?: false

    suspend fun getLastMissedCallId(): Long = context.dataStore.data.firstOrNull()?.get(Keys.lastMissedCallId) ?: 0L
    suspend fun setLastMissedCallId(value: Long) {
        context.dataStore.edit { it[Keys.lastMissedCallId] = value }
    }
}
