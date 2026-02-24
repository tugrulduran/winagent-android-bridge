package com.winagent.bridge

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory state that is reflected to the PC.
 *
 * Requirement:
 * - App stays idle most of the time.
 * - On any interrupt-like event (ringing, missed call, notification change),
 *   we send a *full* packet:
 *     { notifications:[...], missedCalls:[...], phoneRinging:true/false }
 */
object BridgeState {

    @Volatile
    var phoneRinging: Boolean = false

    @Volatile
    var callFrom: String? = null

    // Keyed by notification unique id (StatusBarNotification.key)
    private val notifications = ConcurrentHashMap<String, NotificationItem>()

    @Volatile
    private var missedCalls: List<MissedCallItem> = emptyList()

    data class NotificationItem(
        val id: String,              // unique id (sbn.key)
        val packageName: String,
        val postTime: Long,
        val title: String? = null,
        val text: String? = null,
        val androidId: Int? = null    // legacy int id from the posting app
    )

    data class MissedCallItem(
        val id: Long,                // call log _ID
        val number: String? = null,
        val name: String? = null,
        val dateMs: Long,
        val durationSec: Long,
        val isNew: Boolean = true
    )

    fun setRinging(ringing: Boolean, from: String? = null) {
        phoneRinging = ringing
        callFrom = if (ringing) from else null
    }

    fun upsertNotification(item: NotificationItem) {
        notifications[item.id] = item
    }

    fun removeNotification(id: String) {
        notifications.remove(id)
    }

    fun replaceAllNotifications(items: List<NotificationItem>) {
        notifications.clear()
        for (it in items) notifications[it.id] = it
    }

    fun setMissedCalls(items: List<MissedCallItem>) {
        missedCalls = items
    }

    fun snapshot(): BridgeSnapshot {
        // Copy to stable snapshots for serialization.
        val notifs = notifications.values
            .sortedByDescending { it.postTime }
            .toList()
        val missed = missedCalls
        return BridgeSnapshot(
            phoneRinging = phoneRinging,
            callFrom = callFrom,
            missedCalls = missed,
            notifications = notifs
        )
    }
}

data class BridgeSnapshot(
    val phoneRinging: Boolean,
    val callFrom: String?,
    val missedCalls: List<BridgeState.MissedCallItem>,
    val notifications: List<BridgeState.NotificationItem>
)
