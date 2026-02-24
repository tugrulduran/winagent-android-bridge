package com.winagent.bridge

import android.content.Context

/** Simple global holder to access settings from Services easily. */
object AppGlobals {
    @Volatile private var appContext: Context? = null
    @Volatile private var settingsRepo: SettingsRepo? = null

    val context: Context
        get() = appContext ?: error("AppGlobals not initialized")

    val settings: SettingsRepo
        get() = settingsRepo ?: error("SettingsRepo not initialized")

    fun init(ctx: Context) {
        val c = ctx.applicationContext
        appContext = c
        settingsRepo = SettingsRepo(c)
    }
}
