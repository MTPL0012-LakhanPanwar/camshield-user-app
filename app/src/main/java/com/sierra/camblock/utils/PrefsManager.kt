package com.sierra.camblock.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "camera_lock_prefs"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_ENTRY_TOKEN = "entry_token"
        private const val KEY_ACTIVE_VISITOR_ID = "active_visitor_id"
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

    /**
     * Legacy flag kept only for backward-compat with older installs that
     * may have persisted it. Nothing writes to it anymore — call sites have
     * migrated to [isAccessibilityPermit]. Safe to remove after a few
     * release cycles.
     */
    @Deprecated("Usage Stats has been replaced by the Accessibility Service")
    var isUsageStatPermit : Boolean
        get() = prefs.getBoolean("usage_stat_permit",false)
        set(value) = prefs.edit().putBoolean("usage_stat_permit",value).apply()

    /** User has been guided through granting the Accessibility permission. */
    var isAccessibilityPermit : Boolean
        get() = prefs.getBoolean("accessibility_permit", false)
        set(value) = prefs.edit().putBoolean("accessibility_permit", value).apply()

    var entryToken: String?
        get() = prefs.getString(KEY_ENTRY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ENTRY_TOKEN, value).apply()

    var activeVisitorId: String
        get() = prefs.getString(KEY_ACTIVE_VISITOR_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_VISITOR_ID, value).apply()

    var isXiaomiSetupDone: Boolean
        get() = prefs.getBoolean("xiaomi_setup_done", false)
        set(value) = prefs.edit().putBoolean("xiaomi_setup_done", value).apply()

    /**
     * Persisted mirror of [BlockState.needsAcknowledgement].
     *
     * Why persist it?  On Samsung / OneUI, swiping "clear all" from
     * recents kills the entire app process — including the
     * `CameraBlockerService` foreground service AND the in-memory
     * `BlockState` AtomicBooleans. After the system restarts our
     * accessibility service it must know whether the user was already
     * inside an un-acknowledged block so the overlay can be painted
     * immediately on process restart, before the user has any chance
     * to open the camera app during the 1-3 s service-startup window.
     *
     * Only written on [BlockState.requireAcknowledgement] /
     * [BlockState.acknowledge] transitions so SharedPreferences I/O is
     * trivial (two writes per block cycle).
     */
    var needsAcknowledgement: Boolean
        get() = prefs.getBoolean("needs_acknowledgement", false)
        set(value) = prefs.edit().putBoolean("needs_acknowledgement", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
