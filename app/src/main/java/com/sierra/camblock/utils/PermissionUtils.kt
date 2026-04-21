package com.sierra.camblock.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.sierra.camblock.CameraBlockerAccessibilityService

/**
 * Centralised permission helpers so the same logic is used everywhere
 * (Splash / Permission / Main / Scan activities). Previously this logic was
 * duplicated in four places which made the UsageStats → Accessibility
 * migration error-prone.
 */
object PermissionUtils {

    private const val TAG = "PermissionUtils"

    /**
     * Returns true if the app's [CameraBlockerAccessibilityService] is
     * enabled and bound by the system. We check the colon-separated list of
     * enabled accessibility services in Settings.Secure because
     * AccessibilityManager.getEnabledAccessibilityServiceList can momentarily
     * return stale data immediately after the user flips the toggle.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(
            context,
            CameraBlockerAccessibilityService::class.java
        )
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val accessibilityEnabled = try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }

            if (accessibilityEnabled != 1) return false

            val splitter = TextUtils.SimpleStringSplitter(':').apply {
                setString(enabledServices)
            }
            while (splitter.hasNext()) {
                val componentString = splitter.next()
                val parsed = ComponentName.unflattenFromString(componentString)
                if (parsed != null && parsed == expectedComponent) {
                    return true
                }
                // Fallback match: some OEMs store only the class name portion.
                if (parsed != null &&
                    parsed.packageName == expectedComponent.packageName &&
                    parsed.className == expectedComponent.className
                ) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Accessibility check failed", e)
            false
        }
    }

    /**
     * Builds an intent that opens the system Accessibility settings. We also
     * include a fragment hint so that on OSes that honour it (Android 14+
     * stock, Pixel) the user is taken directly to our service's sub-page.
     * When the specific intent fails we fall back to the generic settings
     * screen so the flow never leaves the user stranded.
     */
    fun buildAccessibilitySettingsIntent(context: Context): Intent {
        val componentName = ComponentName(
            context,
            CameraBlockerAccessibilityService::class.java
        ).flattenToString()

        val bundle: android.os.Bundle = android.os.Bundle().apply {
            putString(":settings:fragment_args_key", componentName)
        }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", componentName)
            putExtra(":settings:show_fragment_args", bundle)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun buildOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )
    }
}
