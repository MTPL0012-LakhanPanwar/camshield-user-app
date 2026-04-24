package com.sierra.camblock.api.models

import com.google.gson.annotations.SerializedName

// ==================== Request Models ====================

data class ValidateQRRequest(
    @SerializedName("token")
    val token: String
)

data class ScanEntryRequest(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("deviceId")
    val deviceId: String,
    
    @SerializedName("deviceInfo")
    val deviceInfo: DeviceInfo,
    
    @SerializedName("visitorInfo")
    val visitorInfo: VisitorInfo? = null
)

data class ScanExitRequest(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("deviceId")
    val deviceId: String
)

data class DeviceInfo(
    @SerializedName("manufacturer")
    val manufacturer: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("osVersion")
    val osVersion: String,
    
    @SerializedName("platform")
    val platform: String = "android",
    
    @SerializedName("appVersion")
    val appVersion: String,
    
    @SerializedName("deviceName")
    val deviceName: String? = null
)

data class VisitorInfo(
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("phone")
    val phone: String? = null,
    
    @SerializedName("purpose")
    val purpose: String? = null,
    
    @SerializedName("company")
    val company: String? = null
)

// ==================== Response Models ====================

data class ApiResponse<T>(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

data class ValidateQRResponse(
    @SerializedName("qrCodeId")
    val qrCodeId: String,
    
    @SerializedName("action")
    val action: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("facility")
    val facility: FacilityInfo,
    
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

data class EnrollmentResponse(
    val enrollmentId: String?,
    val action: String?,
    val facilityName: String?,
    val visitorId: String? // Added since it's in your JSON
)
data class EnrollmentStatusResponse(
    @SerializedName("isEnrolled")
    val isEnrolled: Boolean,
    
    @SerializedName("enrollmentId")
    val enrollmentId: String? = null,
    
    @SerializedName("facilityName")
    val facilityName: String? = null,
    
    @SerializedName("enrolledAt")
    val enrolledAt: String? = null
)

data class FacilityInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("address")
    val address: String? = null,

    @SerializedName("location")
    val location:String = ""
)
// Add these data classes to the end of ApiModels.kt

data class ForceExitRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("reason")
    val reason: String? = null
)

data class ForceExitResponse(
    val status: String,
    val message: String,
    val data: ForceExitData?
)

// 3. The actual Data object inside the response
data class ForceExitData(
    val requestId: String,
    val status: String,
    val requestedAt: String
)

// Check Request Status Response
data class ForceExitStatusResponse(
    val status: String,
    val message: String,
    val data: ForceExitStatusData?
)

data class ForceExitStatusData(
    val hasRequest: Boolean,
    val requestId: String? = null,
    val status: String? = null, // pending, approved, denied, completed
    val requestedAt: String? = null,
    val approvedAt: String? = null,
    val deniedAt: String? = null,
    val completedAt: String? = null,
    val reason: String? = null,
    val adminNotes: String? = null,
    val facility: FacilityInfo? = null
)

// Complete Force Exit Request (for push notification handling)
data class CompleteForceExitRequest(
    @SerializedName("token")
    val token: String, // restore_token_from_notification
    
    @SerializedName("deviceId")
    val deviceId: String // device_identifier
)

// Complete Force Exit Response
data class CompleteForceExitResponse(
    val status: String,
    val message: String,
    val data: CompleteForceExitData?
)

data class CompleteForceExitData(
    val action: String, // "UNLOCK_CAMERA"
    val unlockSuccess: Boolean
)
