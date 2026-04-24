package com.sierra.admin.modal


// ─── Force Exit Request Models ───────────────────────────────────────────────────
import com.google.gson.annotations.SerializedName
import java.io.Serializable


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
) : java.io.Serializable

data class LocationData(
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val coordinates: Coordinates? = null
) : java.io.Serializable

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
) : java.io.Serializable

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
) : java.io.Serializable

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

data class ForceExitRequestItem(
    @SerializedName("_id")
    val id: String = "",

    @SerializedName("requestId")
    val requestId: String = "",

    @SerializedName("deviceId")
    val device: DeviceDetails = DeviceDetails(),

    @SerializedName("facilityId")
    val facility: FacilityDataa = FacilityDataa(),

    @SerializedName("visitorId")
    val visitorId: String? = null,

    @SerializedName("status")
    val status: String = "", // pending, approved, denied, completed

    @SerializedName("reason")
    val reason: String? = null,

    @SerializedName("pushNotificationSent")
    val pushNotificationSent: Boolean = false,

    @SerializedName("requestedAt")
    val requestedAt: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null,

    @SerializedName("__v")
    val version: Int = 0
) : Serializable

// The "deviceId" field in your JSON is an object, not just a string/info class
data class DeviceDetails(
    @SerializedName("_id") val id: String = "",
    @SerializedName("deviceId") val hardwareId: String = "",
    @SerializedName("visitorId") val visitorId: String = "",
    @SerializedName("deviceInfo") val deviceInfo: DeviceHardwareInfo = DeviceHardwareInfo()
) : Serializable

data class DeviceHardwareInfo(
    @SerializedName("manufacturer") val manufacturer: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("osVersion") val osVersion: String = "",
    @SerializedName("platform") val platform: String = "",
    @SerializedName("deviceName") val deviceName: String = ""
) : Serializable

data class FacilityDataa(
    @SerializedName("_id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("location") val location: LocationDataa = LocationDataa()
) : Serializable

data class LocationDataa(
    @SerializedName("address") val address: String = "",
    @SerializedName("city") val city: String = "",
    @SerializedName("state") val state: String = "",
    @SerializedName("country") val country: String = ""
) : Serializable
data class ForceExitApproveResponse(
    val requestId: String = "",
    val status: String = "", // approved
    val approvedAt: String? = null,
    val pushNotificationSent: Boolean = false
)

data class ForceExitDenyResponse(
    val requestId: String = "",
    val status: String = "", // denied
    val deniedAt: String? = null
)

// ─── Result Wrapper ────────────────────────────────────────────────────────────
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
