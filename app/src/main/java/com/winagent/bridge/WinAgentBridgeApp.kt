package com.winagent.bridge

import android.app.Application

class WinAgentBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGlobals.init(this)
    }
}
