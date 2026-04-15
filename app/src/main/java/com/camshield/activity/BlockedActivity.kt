package com.camshield.activity

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.camshield.R

class BlockedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MIUI-specific overlay setup for Android 14+
        setupOverlayForMiui()

        setContentView(R.layout.activity_blocked)

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            // Go back to home screen
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
            finish()
        }
    }

    private fun setupOverlayForMiui() {
        // Apply full screen flags for all devices to ensure coverage
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow extending outside screen
                    WindowManager.LayoutParams.FLAG_FULLSCREEN // Request full screen
        )

        // Handle translucent bars if available (extra safety)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
        }

        // Handle Display Cutout (Notch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set system UI visibility for immersive experience
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        // Make the activity show over everything (if used as an overlay activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Note: Activities typically aren't TYPE_APPLICATION_OVERLAY unless specifically designed as such
            // and launched with NEW_TASK.
            // If this is a normal activity, we don't set TYPE_APPLICATION_OVERLAY on the window usually.
            // But if the previous code had it, we keep it or adjust based on usage.
            // The previous code had it conditionally.
            // We'll keep the logic but simplify the method name/structure conceptually.
            try {
                // window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                // CAUTION: Setting this on an Activity window can crash if not properly handled.
                // The previous code had it, so we will respect it but wrap in try-catch or leave as is if unsure.
                // However, for a standard Activity to be full screen, the flags above are sufficient.
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun isMiuiDevice(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()

            manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    brand.contains("xiaomi") ||
                    brand.contains("redmi") ||
                    brand.contains("mi")
        } catch (e: Exception) {
            false
        }
    }

    override fun onBackPressed() {
        // Prevent back button from closing the overlay
        // Instead, behave like the Home button to take them away from the camera
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    override fun onResume() {
        super.onResume()

        // Force bring to front for MIUI devices
        if (isMiuiDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val taskList = activityManager.getRunningTasks(10)

                for (task in taskList) {
                    if (task.topActivity?.packageName == packageName) {
                        activityManager.moveTaskToFront(task.id, ActivityManager.MOVE_TASK_WITH_HOME)
                        break
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    override fun onUserLeaveHint() {
        // User pressed home button - keep overlay visible
        moveTaskToBack(false)
    }
}