package com.sierra.camblock.utils

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.sierra.camblock.CamShield
import com.sierra.camblock.R

/**
 * Process-wide owner of the blocking overlay View + its WindowManager
 * attachment.
 *
 * Why a singleton instead of per-service state?  Samsung OneUI's
 * "clear all from recents" gesture kills our foreground service
 * (`CameraBlockerService`) and the app process along with it, but the
 * accessibility service (`CameraBlockerAccessibilityService`) is
 * restarted by the system much sooner — typically hundreds of
 * milliseconds before the foreground service comes back. During that
 * gap the user can launch the camera app and see a live preview.
 *
 * This controller lets whichever service is currently alive paint the
 * overlay directly. It relies only on an [android.content.Context]
 * plus the `SYSTEM_ALERT_WINDOW` permission (granted at setup time),
 * both of which are available to both services equally.
 *
 * Threading model: every mutating call must happen on the main thread.
 * Callers that already run on the main thread (accessibility callbacks,
 * main-thread Handler posts from the camera callback) invoke directly;
 * everything else must route through [Handler(Looper.getMainLooper())]
 * first. All internal state is read/written on the main thread only,
 * so no volatile / atomic guards are needed.
 */
object OverlayController {

    private const val TAG = "OverlayCtrl"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isAttached: Boolean = false

    /**
     * Pre-inflate the overlay View and cache the WindowManager reference
     * using the given [context]. Idempotent — safe to call from both
     * services' lifecycle callbacks. Must run on the main thread.
     *
     * Inflation (measured 20–60 ms on a cold JIT) is moved off the
     * block hot path by calling this at service startup. Once the view
     * is cached, [show] only needs to do `addView` (typically <2 ms).
     */
    fun initialize(context: Context) {
        if (overlayView != null && windowManager != null) return
        val app = context.applicationContext
        try {
            if (windowManager == null) {
                windowManager = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            }
            if (overlayView == null) {
                val inflater = app.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val view = inflater.inflate(R.layout.activity_blocked, null)
                view.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
                    Log.d(TAG, "User acknowledged block")
                    BlockState.acknowledge()
                    hide()
                    try {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        app.startActivity(home)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch home after dismiss", e)
                    }
                }
                overlayView = view
            }
        } catch (e: Exception) {
            Log.e(TAG, "initialize failed", e)
        }
    }

    /**
     * Attach the overlay if it isn't already visible. Returns true if
     * the overlay is now on-screen (regardless of whether this call or
     * a prior one actually performed the attach).
     *
     * Handles the OneUI desync case where the framework silently
     * detaches our overlay during configuration changes or memory
     * pressure: checks [View.isAttachedToWindow] against our bookkeeping
     * and re-attaches if they disagree.
     *
     * Safe to call from any thread; non-main-thread calls are routed
     * to the main thread via [Handler.post].
     */
    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context) }
            return isAttached
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted")
            return false
        }
        initialize(context)
        val view = overlayView ?: return false
        val wm = windowManager ?: return false

        // Desync detection: if we *think* it's attached but the view
        // is not actually in the window manager (OneUI edge case),
        // correct the flag and re-attach.
        if (isAttached && !view.isAttachedToWindow) {
            Log.w(TAG, "Overlay flagged attached but View.isAttachedToWindow=false — re-attaching")
            isAttached = false
        }
        if (isAttached) return true

        try {
            view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            wm.addView(view, buildLayoutParams())
            isAttached = true
            Log.d(TAG, "Overlay SHOWN")
            return true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "addView reported already attached — syncing state", e)
            isAttached = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            return false
        }
    }

    /**
     * Detach the overlay from the window manager if it is currently
     * attached. Safe to call from any thread; always idempotent.
     */
    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        if (!isAttached) return
        val view = overlayView
        val wm = windowManager
        try {
            if (view != null && wm != null) wm.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        } finally {
            isAttached = false
            Log.d(TAG, "Overlay HIDDEN")
        }
    }

    /** True when the overlay is attached to the window manager. */
    fun isCurrentlyShowing(): Boolean {
        val view = overlayView ?: return false
        return isAttached && view.isAttachedToWindow
    }

    /**
     * Aligns the overlay's visible state with the current shared
     * [BlockState] + session gates. Runs on the main thread only.
     *
     * This is invoked from:
     *   1. The service-level watchdog (every 150 ms).
     *   2. The state-change listener subscribed to [BlockState].
     *   3. Service onStartCommand + onServiceConnected, so a process
     *      freshly restarted after "clear all" immediately restores
     *      the overlay if the user was mid-block.
     */
    fun enforce(context: Context, isLocked: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { enforce(context, isLocked) }
            return
        }
        if (!isLocked) {
            hide()
            return
        }
        if (CamShield.isOwnAppInForeground) {
            hide()
            return
        }
        if (BlockState.shouldBlock()) {
            show(context)
        } else {
            hide()
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.CENTER
        return params
    }
}
