package com.sierra.camblock.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sierra.camblock.DeviceAdminReceiver

/**
 * Device Admin Manager
 *
 * Manages all device administrator operations:
 * - Check if device admin is active
 * - Request device admin permission
 * - Remove device admin
 * - Lock/unlock camera
 */
class DeviceAdminManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceAdminManager"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName: ComponentName =
        ComponentName(context, DeviceAdminReceiver::class.java)


    /**
     * Check if device admin is currently active
     */
    fun isDeviceAdminActive(): Boolean {
        val isActive = devicePolicyManager.isAdminActive(componentName)
        Log.d(TAG, "Device admin active: $isActive")
        return isActive
    }


    /**
     * Request device admin permission
     * This will show a system dialog to the user
     */
    fun requestDeviceAdminPermission(): Intent {
        Log.d(TAG, "Requesting device admin permission")

        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "This app requires device administrator permission to lock your camera " +
                    "while you're visiting the facility. The camera will be automatically " +
                    "unlocked when you scan the exit QR code."
        )

        return intent
    }


    /**
     * Remove device admin
     * Note: This can only be done if the app is currently a device admin
     */
    fun removeDeviceAdmin(): Boolean {
        return try {
            if (isDeviceAdminActive()) {
                Log.d(TAG, "Removing device admin")
                devicePolicyManager.removeActiveAdmin(componentName)
                Log.d(TAG, "Device admin removed successfully")
                true
            } else {
                Log.d(TAG, "Device admin is not active")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing device admin", e)
            false
        }
    }


    /**
     * Lock camera
     * Requires device admin to be active
     */
    fun lockCamera(): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.e(TAG, "Cannot lock camera: Device admin not active")
                // Do not show Toast here, handled by UI/Logic
                return false
            }

            Log.d(TAG, "Locking camera")
            devicePolicyManager.setCameraDisabled(componentName, true)

            // Verify camera is locked
            val isLocked = isCameraLocked()
            Log.d(TAG, "Camera lock status after action: $isLocked")
            return isLocked
        } catch (e: Exception) {
            // Log error but don't show Toast - fallback service will handle enforcement
            Log.e(TAG, "Error locking camera (Legacy Method): ${e.message}")
            false
        }
    }

    /**
     * Unlock camera
     */
    fun unlockCamera(): Boolean {
        return try {
            if (!isDeviceAdminActive()) {
                Log.w(TAG, "Device admin not active, assuming camera is unlocked")
                return true
            }

            Log.d(TAG, "Unlocking camera")
            devicePolicyManager.setCameraDisabled(componentName, false)

            val isLocked = isCameraLocked()
            Log.d(TAG, "Camera lock status after unlock: $isLocked")
            return !isLocked
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking camera", e)
            false
        }
    }

    /**
     * Check if camera is locked
     */
    fun isCameraLocked(): Boolean {
        return try {
            if (!isDeviceAdminActive()) return false
            devicePolicyManager.getCameraDisabled(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera status", e)
            false
        }
    }
}

