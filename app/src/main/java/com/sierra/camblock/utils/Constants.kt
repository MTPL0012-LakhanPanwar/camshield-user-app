package com.sierra.camblock.utils

object Constants {
    
    // ==================== API Configuration ====================
    
    // ==================== API Endpoints ====================

    // IMPORTANT: Change this to your backend server IP address
    // For emulator: use 10.0.2.2 (this points to localhost on your computer)
    // For physical device: use your computer's IP address (e.g., 192.168.1.100)
    const val BASE_URL = "https://5415-103-180-38-170.ngrok-free.app/api/" // Updated to match backend port 5000

    const val ENDPOINT_VALIDATE_QR = "enrollments/validate-qr"
    const val ENDPOINT_SCAN_ENTRY = "enrollments/scan-entry"
    const val ENDPOINT_SCAN_EXIT = "enrollments/scan-exit"
    const val ENDPOINT_STATUS = "enrollments/status/{deviceId}"
    
    const val ENDPOINT_FORCE_EXIT_REQUEST = "force-exit/request"

    
    
    // ==================== SharedPreferences Keys ====================
    
    const val PREFS_NAME = "CameraLockPrefs"
    const val KEY_DEVICE_ID = "device_id"
    const val KEY_IS_ENROLLED = "is_enrolled"
    const val KEY_ENROLLMENT_ID = "enrollment_id"
    const val KEY_FACILITY_NAME = "facility_name"
    const val KEY_ENROLLED_AT = "enrolled_at"
    
    
    // ==================== QR Code Configuration ====================
    
    const val QR_SCAN_REQUEST_CODE = 100
    
    
    // ==================== Device Admin ====================
    
    const val DEVICE_ADMIN_REQUEST_CODE = 200
    
    
    // ==================== Network Configuration ====================
    
    const val NETWORK_TIMEOUT = 30L // seconds
    const val RETRY_DELAY = 2000L // milliseconds
    
    
    // ==================== App Info ====================
    
    const val APP_VERSION = "1.0.0"
    const val PLATFORM = "android"
    
    
    // ==================== Error Messages ====================
    
    const val ERROR_NO_INTERNET = "No internet connection. Please check your network."
    const val ERROR_SERVER_UNREACHABLE = "Cannot reach server. Please check backend is running."
    const val ERROR_INVALID_QR = "Invalid or expired QR code."
    const val ERROR_DEVICE_ADMIN_REQUIRED = "Device admin permission is required to lock camera."
    const val ERROR_ALREADY_ENROLLED = "This device is already enrolled."
    const val ERROR_NOT_ENROLLED = "This device is not enrolled."
    const val ERROR_CAMERA_PERMISSION = "Camera permission is required to scan QR codes."
    
    
    // ==================== Success Messages ====================
    
    const val SUCCESS_CAMERA_LOCKED = "Camera locked successfully! You can now visit the facility."
    const val SUCCESS_CAMERA_UNLOCKED = "Camera unlocked! Thank you for visiting."
    const val SUCCESS_DEVICE_ADMIN_ENABLED = "Device admin enabled. Locking camera..."
    
    
    // ==================== Info Messages ====================
    
    const val INFO_SCAN_ENTRY = "Scan the Entry QR code at the facility entrance"
    const val INFO_SCAN_EXIT = "Scan the Exit QR code before leaving"
    const val INFO_CAMERA_LOCKED = "Camera is currently locked"
    const val INFO_CAMERA_UNLOCKED = "Camera is unlocked"
}
