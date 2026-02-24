package com.winagent.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.winagent.bridge.telephony.CallLogReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Minimal-power call monitoring:
 * - No foreground service.
 * - System wakes us up only on call state broadcasts.
 *
 * Requirement:
 * - No polling.
 * - On interrupt-like call state events we update in-memory state and
 *   push a full packet via [EventDispatcher].
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action ?: return
        if (action != "android.intent.action.PHONE_STATE" &&
            action != "android.telephony.action.PHONE_STATE_CHANGED") {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        val repo = AppGlobals.settings
        val sendLive = repo.getSendLiveCalls()
        val sendMissed = repo.getSendMissedCalls()

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallStateMemory.onRinging()

                if (!sendLive) return
                if (!has(ctx, android.Manifest.permission.READ_PHONE_STATE)) return

                // Best-effort incoming number; may be null on some Android versions/OEMs.
                val from = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                BridgeState.setRinging(true, from)
                EventDispatcher.sendStateAsync()
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallStateMemory.onOffhook()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasRinging = CallStateMemory.onIdleAndWasRinging()
                val missed = CallStateMemory.wasMissedCall()

                // If we were in ringing state, we should clear phoneRinging.
                if (wasRinging && sendLive) {
                    BridgeState.setRinging(false, null)
                }

                if (missed && sendMissed) {
                    if (has(ctx, android.Manifest.permission.READ_CALL_LOG)) {
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
                }

                // Send on any relevant event:
                // - ringing ended (wasRinging)
                // - missed call event
                if ((wasRinging && sendLive) || (missed && sendMissed)) {
                    EventDispatcher.sendStateAsync()
                }
            }
        }
    }

    private fun has(ctx: Context, perm: String): Boolean {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    private object CallStateMemory {
        @Volatile private var lastState: String? = null
        @Volatile private var sawOffhook: Boolean = false
        @Volatile private var lastWasRinging: Boolean = false
        @Volatile private var lastWasMissed: Boolean = false

        fun onRinging() {
            lastState = TelephonyManager.EXTRA_STATE_RINGING
            sawOffhook = false
            lastWasRinging = true
            lastWasMissed = false
        }

        fun onOffhook() {
            lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            sawOffhook = true
        }

        fun onIdleAndWasRinging(): Boolean {
            val wasRinging = lastState == TelephonyManager.EXTRA_STATE_RINGING || lastWasRinging
            lastWasRinging = false
            // capture missed decision before reset
            lastWasMissed = wasRinging && !sawOffhook
            lastState = TelephonyManager.EXTRA_STATE_IDLE
            sawOffhook = false
            return wasRinging
        }

        /** True if the previous call sequence was a missed call. */
        fun wasMissedCall(): Boolean = lastWasMissed
    }
}
