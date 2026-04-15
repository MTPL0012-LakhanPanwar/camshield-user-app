package com.sierra.camblock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver
 *
 * This receiver handles device administrator callbacks:
 * - When device admin is enabled
 * - When device admin is disabled
 * - When password changes
 * - etc.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "DeviceAdminReceiver"
    }

    /**
     * Called when device admin is successfully enabled
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin Enabled")

        Toast.makeText(
            context,
            "Device admin enabled successfully",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Called when device admin is disabled
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin Disabled")

        Toast.makeText(
            context,
            "Device admin disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Called when user attempts to disable device admin
     * Return the message to show to the user
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "Device Admin Disable Requested")
        return "This will unlock the camera and unenroll this device from the facility."
    }

    /**
     * Called when password changes
     */
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Password Changed")
    }

    /**
     * Called when password failed
     */
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Password Failed")
    }

    /**
     * Called when password succeeded
     */
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Password Succeeded")
    }
}