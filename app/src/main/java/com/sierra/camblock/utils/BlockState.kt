package com.sierra.camblock.utils

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide, thread-safe state shared between:
 *   - [com.sierra.camblock.CameraBlockerAccessibilityService] — updates
 *     [isCameraAppForeground] the instant a WINDOW_STATE_CHANGED event fires.
 *   - [com.sierra.camblock.CameraBlockerService] — reads the state to decide
 *     whether the blocking overlay should be visible and also updates
 *     [isCameraInUse] from the CameraManager availability callback.
 *
 * Using atomic primitives plus a single listener callback avoids the 200 ms
 * polling loop of the previous UsageStats-based implementation and gives us
 * near-zero latency between the OS signalling a window change and the
 * overlay being rendered.
 */
object BlockState {

    /** Last known foreground package reported by the Accessibility Service. */
    private val foregroundPackageRef = AtomicReference<String>("")

    /** True if the current foreground app matches [CameraApps.isCameraApp]. */
    private val cameraAppForegroundRef = AtomicBoolean(false)

    /** True while *any* app holds the camera hardware (CameraManager callback). */
    private val cameraInUseRef = AtomicBoolean(false)

    /**
     * Sticky "user has not acknowledged the block yet" flag.
     *
     * We need this because our blocking strategy is to kick the user back to
     * Home via `performGlobalAction(GLOBAL_ACTION_HOME)`. The instant that
     * happens, the foreground package becomes the launcher and the camera
     * hardware is released — which would otherwise cause [shouldBlock] to
     * flip back to false and the overlay would never render (or would
     * render for a single frame).
     *
     * This flag stays `true` until [acknowledge] is called (i.e. the user
     * tapped "I Understand") so the overlay remains visible on top of the
     * launcher until they explicitly dismiss it.
     */
    private val needsAcknowledgementRef = AtomicBoolean(false)

    /**
     * Single listener invoked on every state transition so the overlay
     * service can react immediately without polling. Use `set` from the
     * service's [android.app.Service.onCreate] and clear it in
     * `onDestroy` to avoid leaking the service reference.
     */
    @Volatile
    var onStateChanged: (() -> Unit)? = null

    var foregroundPackage: String
        get() = foregroundPackageRef.get() ?: ""
        set(value) {
            val next = value
            val prev = foregroundPackageRef.getAndSet(next)
            if (prev != next) notifyChanged()
        }

    var isCameraAppForeground: Boolean
        get() = cameraAppForegroundRef.get()
        set(value) {
            if (cameraAppForegroundRef.getAndSet(value) != value) notifyChanged()
        }

    var isCameraInUse: Boolean
        get() = cameraInUseRef.get()
        set(value) {
            if (cameraInUseRef.getAndSet(value) != value) notifyChanged()
        }

    val needsAcknowledgement: Boolean
        get() = needsAcknowledgementRef.get()

    /**
     * Optional SharedPreferences-backed persistence sink for
     * [needsAcknowledgement]. When set, transitions of the sticky flag
     * are mirrored into prefs so they survive a full process kill
     * (e.g. "clear all" from recents on Samsung OneUI).
     *
     * Set once at service startup and cleared on destroy; see
     * [CameraBlockerService.onCreate] and
     * [CameraBlockerAccessibilityService.onServiceConnected].
     */
    @Volatile
    var persistentAckSetter: ((Boolean) -> Unit)? = null

    /**
     * Marks the overlay as requiring explicit user dismissal. Idempotent —
     * calling it repeatedly will not trigger duplicate listener notifications.
     */
    fun requireAcknowledgement() {
        if (!needsAcknowledgementRef.getAndSet(true)) {
            persistentAckSetter?.invoke(true)
            notifyChanged()
        }
    }

    /** Called from the overlay's "I Understand" click handler. */
    fun acknowledge() {
        if (needsAcknowledgementRef.getAndSet(false)) {
            persistentAckSetter?.invoke(false)
            notifyChanged()
        }
    }

    /**
     * Seed the in-memory sticky flag from a previously persisted value.
     * Called by the accessibility / blocker service during `onCreate`
     * so that a freshly-restarted process (after "clear all") can
     * immediately paint the overlay without waiting for a camera event.
     */
    fun restoreAcknowledgementFromPersistence(persisted: Boolean) {
        needsAcknowledgementRef.set(persisted)
        if (persisted) notifyChanged()
    }

    /**
     * True when the overlay should be visible. The sticky
     * [needsAcknowledgement] flag is the primary driver — the two "live"
     * signals are kept in the OR only as a safety net in case a listener
     * fires before the sticky flag has been set.
     */
    fun shouldBlock(): Boolean =
        needsAcknowledgementRef.get() ||
                cameraAppForegroundRef.get() ||
                cameraInUseRef.get()

    fun reset() {
        foregroundPackageRef.set("")
        cameraAppForegroundRef.set(false)
        cameraInUseRef.set(false)
        needsAcknowledgementRef.set(false)
    }

    private fun notifyChanged() {
        // Copy into a local so the callback can't be nulled mid-invocation.
        onStateChanged?.invoke()
    }
}
