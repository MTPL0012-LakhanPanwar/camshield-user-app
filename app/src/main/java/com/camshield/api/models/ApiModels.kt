package com.camshield.api.models

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
    val address: String? = null
)
