package com.sierra.admin.modal

// ─── Auth ──────────────────────────────────────────────────────────────────────
data class AdminData(
    val id: String = "",
    val username: String = "",
    val role: String? = null,
    val createdAt: String? = null
)

data class AuthResponse(
    val token: String,
    val admin: AdminData
)

// ─── Facility ──────────────────────────────────────────────────────────────────
data class Coordinates(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class LocationData(
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val coordinates: Coordinates? = null
)

data class FacilityData(
    val id: String = "",
    val facilityId: String? = null,
    val name: String = "",
    val description: String? = null,
    val location: LocationData? = null,
    val notificationEmails: List<String> = emptyList(),
    val timezone: String? = null,
    val status: String = "active",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val activeQRCodes: List<QRData> = emptyList()
)

data class QRData(
    val id: String = "",
    val name: String = "",
    val value: String = "",
    val type: String = "",
    val action: String = "",
    val status: String = "",
    val validFrom: String? = null,
    val validUntil: String? = null,
    val generatedForDate: String? = null,
    val token: String = "",
    val url: String = "",
    val imagePath: String? = null,
    val imageUrl: String? = null,
    val qrCodeId: String? = null
)

data class QRPair(
    val entry: QRData? = null,
    val exit: QRData? = null
)

data class FacilityCreateResponse(
    val facility: FacilityData,
    val qrs: QRPair?
)

// ─── Device / Enrollment ───────────────────────────────────────────────────────
data class DeviceInfo(
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val model: String = "",
    val status: String = "",
    val manufacturer: String = "",
    val osVersion: String = ""
)

data class ActiveDeviceItem(
    val id: String = "",
    val device: DeviceInfo = DeviceInfo(),
    val visitorId: String = "",
    val status: String = "",
    val lastActivity: String? = null,
    val pushToken: String? = null,
    val lastEnrollment: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val currentFacility: FacilityData? = null
)

data class EnrollmentDetail(
    val enrollmentId: String = "",
    val device: DeviceInfo = DeviceInfo(),
    val facility: FacilityData = FacilityData(),
    val entryQRCode: QRData? = null,
    val enrolledAt: String = ""
)

data class ForceExitResponse(
    val action: String = "",
    val enrollmentId: String = "",
    val pushSent: Boolean = false,
    val restoreToken: String? = null
)

// ─── Pagination ────────────────────────────────────────────────────────────────
data class PaginatedData<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1
)

// ─── Result Wrapper ────────────────────────────────────────────────────────────
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
