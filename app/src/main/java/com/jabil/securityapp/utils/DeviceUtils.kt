package com.jabil.securityapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.jabil.securityapp.api.models.DeviceInfo
import java.util.*

/**
 * Device Utilities
 * Helper functions for getting device information
 */
object DeviceUtils {
    
    /**
     * Generate or retrieve unique device ID
     */
    fun getDeviceId(context: Context): String {
        val prefs = PreferencesManager.getInstance(context)
        
        // Check if device ID already exists
        var deviceId = prefs.getDeviceId()
        
        if (deviceId.isEmpty()) {
            // Generate new device ID
            deviceId = try {
                // Try to get Android ID (unique per device)
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: UUID.randomUUID().toString()
            } catch (e: Exception) {
                // Fallback to random UUID
                UUID.randomUUID().toString()
            }
            
            // Save device ID
            prefs.setDeviceId(deviceId)
        }
        
        return deviceId
    }


    fun isTargetedXiaomiVersion(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        if (manufacturer != "xiaomi" && manufacturer != "redmi" && manufacturer != "poco") return false

        val miuiName = getSystemProperty("ro.miui.ui.version.name") // e.g., "V14" or "OS1.0"
        val androidVersion = android.os.Build.VERSION.SDK_INT

        // MIUI 14 Check
        val isMiui14 = miuiName.contains("V14", ignoreCase = true)

        val isHyperOS_V1 = miuiName.contains("V816")

        val isHyperOS_V2 = miuiName.startsWith("OS")

        val isModernAndroid = (androidVersion >= 34)

        // HyperOS (Android 14 is SDK 34, Android 15 is SDK 35)
        // HyperOS name typically starts with "OS"
        return isMiui14 || (isModernAndroid && (isHyperOS_V1 || isHyperOS_V2))
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            get.invoke(c, key) as String
        } catch (e: Exception) { "" }
    }
    
    /**
     * Get complete device information
     */
    fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            platform = Constants.PLATFORM,
            appVersion = Constants.APP_VERSION,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
    
    
    /**
     * Get device description for display
     */
    fun getDeviceDescription(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
    //Check internet availability
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            // Ethernet for emulators/TVs
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}


/**
 * Preferences Manager
 * Manages SharedPreferences for storing app data
 */
class PreferencesManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var instance: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    
    // ==================== Device ID ====================
    
    fun getDeviceId(): String {
        return prefs.getString(Constants.KEY_DEVICE_ID, "") ?: ""
    }
    
    fun setDeviceId(deviceId: String) {
        prefs.edit().putString(Constants.KEY_DEVICE_ID, deviceId).apply()
    }
    
    
    // ==================== Enrollment Status ====================
    
    fun isEnrolled(): Boolean {
        return prefs.getBoolean(Constants.KEY_IS_ENROLLED, false)
    }
    
    fun setEnrolled(enrolled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_IS_ENROLLED, enrolled).apply()
    }
    
    
    // ==================== Enrollment Details ====================
    
    fun getEnrollmentId(): String {
        return prefs.getString(Constants.KEY_ENROLLMENT_ID, "") ?: ""
    }
    
    fun setEnrollmentId(enrollmentId: String) {
        prefs.edit().putString(Constants.KEY_ENROLLMENT_ID, enrollmentId).apply()
    }
    
    fun getFacilityName(): String {
        return prefs.getString(Constants.KEY_FACILITY_NAME, "") ?: ""
    }
    
    fun setFacilityName(facilityName: String) {
        prefs.edit().putString(Constants.KEY_FACILITY_NAME, facilityName).apply()
    }
    
    fun getEnrolledAt(): String {
        return prefs.getString(Constants.KEY_ENROLLED_AT, "") ?: ""
    }
    
    fun setEnrolledAt(enrolledAt: String) {
        prefs.edit().putString(Constants.KEY_ENROLLED_AT, enrolledAt).apply()
    }
    
    
    // ==================== Clear Data ====================
    
    fun clearEnrollmentData() {
        prefs.edit().apply {
            putBoolean(Constants.KEY_IS_ENROLLED, false)
            putString(Constants.KEY_ENROLLMENT_ID, "")
            putString(Constants.KEY_FACILITY_NAME, "")
            putString(Constants.KEY_ENROLLED_AT, "")
            apply()
        }
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
