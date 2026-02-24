package com.winagent.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notification action receiver. Stops the foreground service.
 */
class QuitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == BridgeForegroundService.ACTION_STOP) {
            BridgeForegroundService.stop(context.applicationContext)
        }
    }
}
