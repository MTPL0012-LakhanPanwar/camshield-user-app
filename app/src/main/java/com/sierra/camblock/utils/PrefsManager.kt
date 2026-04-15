package com.sierra.camblock.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "camera_lock_prefs"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_ENTRY_TOKEN = "entry_token"
    }

    var isLocked: Boolean
        get() = prefs.getBoolean(KEY_IS_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOCKED, value).apply()

    var entryTime : Long
        get() = prefs.getLong("entry_time", 0)
        set(value) = prefs.edit().putLong("entry_time", value).apply()

    var isOverlayPermit : Boolean
        get() = prefs.getBoolean("overlay_permit",false)
        set(value) = prefs.edit().putBoolean("overlay_permit",value).apply()

    var isUsageStatPermit : Boolean
        get() = prefs.getBoolean("usage_stat_permit",false)
        set(value) = prefs.edit().putBoolean("usage_stat_permit",value).apply()

    var entryToken: String?
        get() = prefs.getString(KEY_ENTRY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ENTRY_TOKEN, value).apply()

    var isXiaomiSetupDone: Boolean
        get() = prefs.getBoolean("xiaomi_setup_done", false)
        set(value) = prefs.edit().putBoolean("xiaomi_setup_done", value).apply()
        
    fun clear() {
        prefs.edit().clear().apply()
    }
}
