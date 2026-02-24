package com.winagent.bridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var sendEnabled: Boolean = false

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Actions.ACTION_REFRESH_NOTIFICATIONS) return
            val forceSend = intent.getBooleanExtra(Actions.EXTRA_FORCE_SEND, false)
            scope.launch {
                reloadActiveNotifications()
                if (forceSend) {
                    EventDispatcher.sendStateAsync()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            AppGlobals.settings.sendNotificationsFlow.collect { sendEnabled = it }
        }

        // Listen for explicit refresh requests from the UI.
        val filter = IntentFilter(Actions.ACTION_REFRESH_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(refreshReceiver, filter)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(refreshReceiver) } catch (_: Throwable) {}
        scope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!sendEnabled) return
        scope.launch {
            reloadActiveNotifications()
            EventDispatcher.sendStateAsync()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!sendEnabled) return
        BridgeState.upsertNotification(toItem(sbn))
        EventDispatcher.sendStateAsync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!sendEnabled) return
        BridgeState.removeNotification(sbn.key)
        EventDispatcher.sendStateAsync()
    }

    private suspend fun reloadActiveNotifications() = withContext(Dispatchers.Default) {
        val list = try { activeNotifications } catch (_: Throwable) { null }
        if (list == null) {
            BridgeState.replaceAllNotifications(emptyList())
            return@withContext
        }

        val items = list.map { toItem(it) }
        BridgeState.replaceAllNotifications(items)
    }

    private fun toItem(sbn: StatusBarNotification): BridgeState.NotificationItem {
        val n = sbn.notification
        val extras = n.extras

        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        return BridgeState.NotificationItem(
            id = sbn.key, // unique id
            packageName = sbn.packageName,
            postTime = sbn.postTime,
            title = title,
            text = text,
            androidId = sbn.id
        )
    }
}
