package com.sierra.admin.api

import android.content.Context
import com.camshield.admin.api.ApiClient
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.AdminData
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.AuthResponse
import com.sierra.admin.modal.Coordinates
import com.sierra.admin.modal.DeviceInfo
import com.sierra.admin.modal.EnrollmentDetail
import com.sierra.admin.modal.ExitRequest
import com.sierra.admin.modal.ExitRequestListResponse
import com.sierra.admin.modal.ExitRequestResponse
import com.sierra.admin.modal.ExitRequestStatusResponse
import com.sierra.admin.modal.ApproveExitRequestRequest
import com.sierra.admin.modal.DenyExitRequestRequest
import com.sierra.admin.modal.FacilityCreateResponse
import com.sierra.admin.modal.FacilityData
import com.sierra.admin.modal.LocationData
import com.sierra.admin.modal.PaginatedData
import com.sierra.admin.modal.QRData
import com.sierra.admin.modal.QRPair
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ApiService(context: Context) {
    private val client = ApiClient(context)
    val tokenManager = client.tokenManager

    private fun extractMessage(json: JSONObject?): String =
        json?.optString("message")?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred"

    private fun encode(q: String) = URLEncoder.encode(q, "UTF-8")

    // ─── Auth ──────────────────────────────────────────────────────────────────
    suspend fun login(username: String, password: String): ApiResult<AuthResponse> {
        val body = JSONObject().put("username", username).put("password", password)
        val (code, json) = client.post("/api/auth/admin/login", body, auth = false)
        return if (code == 200 && json != null) {
            val data = json.optJSONObject("data") ?: return ApiResult.Error(extractMessage(json), code)
            val token = data.optString("token").takeIf { it.isNotBlank() } ?: return ApiResult.Error("Missing token", code)
            val admin = data.optJSONObject("admin")?.let { parseAdmin(it) } ?: AdminData()
            tokenManager.saveToken(token)
            tokenManager.saveAdmin(admin.id, admin.username)
            ApiResult.Success(AuthResponse(token, admin))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun register(username: String, password: String): ApiResult<AuthResponse> {
        val body = JSONObject().put("username", username).put("password", password)
        val (code, json) = client.post("/api/auth/admin/register", body, auth = false)
        return if (code == 201 && json != null) {
            val data = json.optJSONObject("data") ?: return ApiResult.Error(extractMessage(json), code)
            val token = data.optString("token").takeIf { it.isNotBlank() } ?: return ApiResult.Error("Missing token", code)
            val admin = data.optJSONObject("admin")?.let { parseAdmin(it) } ?: AdminData()
            tokenManager.saveToken(token)
            tokenManager.saveAdmin(admin.id, admin.username)
            ApiResult.Success(AuthResponse(token, admin))
        } else ApiResult.Error(extractMessage(json), code)
    }

    // ─── Admins ────────────────────────────────────────────────────────────────
    suspend fun getAdmins(page: Int = 1, limit: Int = 20, q: String = ""): ApiResult<PaginatedData<AdminData>> {
        val (code, json) = client.get("/api/admin/admins?page=$page&limit=$limit&q=${encode(q)}")
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            val arr = data.getJSONArray("items")
            ApiResult.Success(PaginatedData(
                items = (0 until arr.length()).map { parseAdmin(arr.getJSONObject(it)) },
                page = data.optInt("page", page),
                limit = data.optInt("limit", limit),
                total = data.optInt("total", 0),
                totalPages = data.optInt("totalPages", 1)
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun getAdminDetail(idOrUsername: String): ApiResult<AdminData> {
        val (code, json) = client.get("/api/admin/admins/$idOrUsername")
        return if (code == 200 && json != null) {
            ApiResult.Success(parseAdmin(json.getJSONObject("data")))
        } else ApiResult.Error(extractMessage(json), code)
    }

    // ─── Facilities ────────────────────────────────────────────────────────────
    suspend fun createFacility(
        name: String, description: String,
        address: String, city: String, state: String, country: String,
        emails: List<String>, timezone: String, status: String
    ): ApiResult<FacilityCreateResponse> {
        val body = buildFacilityBody(name, description, address, city, state, country, emails, timezone, status)
        val (code, json) = client.post("/api/admin/facilities", body)
        return if (code == 201 && json != null) {
            val data = json.getJSONObject("data")
            ApiResult.Success(FacilityCreateResponse(
                facility = parseFacility(data.getJSONObject("facility")),
                qrs = data.optJSONObject("qrs")?.let { parseQRPair(it) }
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun getFacilities(page: Int = 1, limit: Int = 10, status: String = "", q: String = ""): ApiResult<PaginatedData<FacilityData>> {
        var path = "/api/admin/facilities?page=$page&limit=$limit"
        if (status.isNotBlank()) path += "&status=${encode(status)}"
        if (q.isNotBlank()) path += "&q=${encode(q)}"
        val (code, json) = client.get(path)
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            val arr = data.getJSONArray("items")
            ApiResult.Success(PaginatedData(
                items = (0 until arr.length()).map { parseFacility(arr.getJSONObject(it)) },
                page = data.optInt("page", page),
                limit = data.optInt("limit", limit),
                total = data.optInt("total", 0),
                totalPages = data.optInt("totalPages", 1)
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun getFacilityDetail(id: String): ApiResult<FacilityData> {
        val (code, json) = client.get("/api/admin/facilities/$id")
        return if (code == 200 && json != null) {
            ApiResult.Success(parseFacility(json.getJSONObject("data")))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun updateFacility(
        id: String, name: String, description: String,
        address: String, city: String, state: String, country: String,
        emails: List<String>, timezone: String, status: String
    ): ApiResult<FacilityData> {
        val body = buildFacilityBody(name, description, address, city, state, country, emails, timezone, status)
        val (code, json) = client.put("/api/admin/facilities/$id", body)
        return if (code == 200 && json != null) {
            ApiResult.Success(parseFacility(json.getJSONObject("data")))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun deleteFacility(id: String): ApiResult<Unit> { // Change return type to Unit
        val (code, json) = client.delete("/api/admin/facilities/$id")
        return if (code == 200) {
            // We pass Unit.VALUE (which is just 'Unit') into the Success wrapper
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error(extractMessage(json), code)
        }
    }

    // ─── Devices ───────────────────────────────────────────────────────────────
    suspend fun getActiveDevices(page: Int = 1, limit: Int = 10, q: String = ""): ApiResult<PaginatedData<ActiveDeviceItem>> {
        val (code, json) = client.get("/api/admin/devices/active?page=$page&limit=$limit&q=${encode(q)}")
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            val arr = data.getJSONArray("items")
            ApiResult.Success(PaginatedData(
                items = (0 until arr.length()).map { parseActiveDevice(arr.getJSONObject(it)) },
                page = data.optInt("page", page),
                limit = data.optInt("limit", limit),
                total = data.optInt("total", 0),
                totalPages = data.optInt("totalPages", 1)
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun getActiveEnrollment(deviceId: String): ApiResult<EnrollmentDetail> {
        val (code, json) = client.get("/api/admin/devices/$deviceId/active-enrollment")
        return if (code == 200 && json != null) {
            ApiResult.Success(parseEnrollment(json.getJSONObject("data")))
        } else ApiResult.Error(extractMessage(json), code)
    }

    // ─── Exit Requests ─────────────────────────────────────────────────────────
    suspend fun getForceExitRequests(
        page: Int = 1,
        limit: Int = 20,
        status: String = "",
        facilityId: String = "",
        search: String = ""
    ): ApiResult<ExitRequestListResponse> {
        var path = "/api/admin/force-exit-requests?page=$page&limit=$limit"
        if (status.isNotBlank()) path += "&status=${encode(status)}"
        if (facilityId.isNotBlank()) path += "&facilityId=${encode(facilityId)}"
        if (search.isNotBlank()) path += "&search=${encode(search)}"
        
        val (code, json) = client.get(path)
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            val arr = data.getJSONArray("items")
            ApiResult.Success(ExitRequestListResponse(
                items = (0 until arr.length()).map { parseExitRequest(arr.getJSONObject(it)) },
                page = data.optInt("page", page),
                limit = data.optInt("limit", limit),
                total = data.optInt("total", 0),
                totalPages = data.optInt("totalPages", 1),
                counts = data.optJSONObject("counts")?.let { parseStatusCounts(it) } ?: StatusCounts(0, 0, 0, 0)
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun getForceExitRequest(requestId: String): ApiResult<ExitRequestStatusResponse> {
        val (code, json) = client.get("/api/admin/force-exit-requests/$requestId")
        return if (code == 200 && json != null) {
            ApiResult.Success(parseExitRequestStatus(json.getJSONObject("data")))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun approveForceExitRequest(requestId: String, adminNotes: String): ApiResult<ExitRequestResponse> {
        val body = JSONObject().put("adminNotes", adminNotes)
        val (code, json) = client.post("/api/admin/force-exit-requests/$requestId/approve", body)
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            ApiResult.Success(ExitRequestResponse(
                requestId = data.optString("requestId"),
                status = data.optString("status"),
                processedAt = data.optString("processedAt").takeIf { it.isNotBlank() },
                pushNotificationSent = data.optBoolean("pushNotificationSent"),
                pushSent = data.optBoolean("pushSent"),
                firebasePushSent = data.optBoolean("firebasePushSent"),
                pushService = data.optString("pushService").takeIf { it.isNotBlank() },
                device = data.optJSONObject("device")?.let { parseDevicePushInfo(it) } ?: DevicePushInfo("", false),
                restoreToken = data.optString("restoreToken").takeIf { it.isNotBlank() }
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    suspend fun denyForceExitRequest(requestId: String, adminNotes: String): ApiResult<ExitRequestResponse> {
        val body = JSONObject().put("adminNotes", adminNotes)
        val (code, json) = client.post("/api/admin/force-exit-requests/$requestId/deny", body)
        return if (code == 200 && json != null) {
            val data = json.getJSONObject("data")
            ApiResult.Success(ExitRequestResponse(
                requestId = data.optString("requestId"),
                status = data.optString("status"),
                processedAt = data.optString("processedAt").takeIf { it.isNotBlank() },
                pushNotificationSent = data.optBoolean("pushNotificationSent"),
                pushSent = data.optBoolean("pushSent"),
                firebasePushSent = data.optBoolean("firebasePushSent"),
                pushService = data.optString("pushService").takeIf { it.isNotBlank() },
                device = data.optJSONObject("device")?.let { parseDevicePushInfo(it) } ?: DevicePushInfo("", false),
                restoreToken = data.optString("restoreToken").takeIf { it.isNotBlank() }
            ))
        } else ApiResult.Error(extractMessage(json), code)
    }

    // ─── Parsers ───────────────────────────────────────────────────────────────
    private fun parseAdmin(obj: JSONObject) = AdminData(
        id = obj.optString("id").ifBlank { obj.optString("_id") },
        username = obj.optString("username"),
        role = obj.optString("role")
            .takeIf { it.isNotBlank() }
            ?: obj.optString("type").takeIf { it.isNotBlank() },
        createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() }
    )

    private fun parseFacility(obj: JSONObject) = FacilityData(
        id = obj.optString("id").ifBlank { obj.optString("_id") },
        facilityId = obj.optString("facilityId").takeIf { it.isNotBlank() },
        name = obj.optString("name"),
        description = obj.optString("description").takeIf { it.isNotBlank() },
        status = obj.optString("status", "active"),
        timezone = obj.optString("timezone").takeIf { it.isNotBlank() },
        notificationEmails = obj.optJSONArray("notificationEmails")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        location = obj.optJSONObject("location")?.let { loc ->
            LocationData(
                address = loc.optString("address"),
                city = loc.optString("city"),
                state = loc.optString("state"),
                country = loc.optString("country"),
                coordinates = loc.optJSONObject("coordinates")?.let { c ->
                    Coordinates(c.optDouble("latitude", 0.0), c.optDouble("longitude", 0.0))
                }
            )
        },
        createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() },
        updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() },
        activeQRCodes = obj.optJSONArray("activeQRCodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { parseQR(it) }
            }
        } ?: emptyList()
    )

    suspend fun getFacilityQRCodes(facilityId: String): ApiResult<QRPair> {
        val (code, json) = client.get("/api/admin/facilities/$facilityId/qr-codes")
        return if (code == 200 && json != null) {
            val data = json.optJSONObject("data") ?: return ApiResult.Error("Invalid response", code)
            ApiResult.Success(parseQRPair(data))
        } else ApiResult.Error(extractMessage(json), code)
    }

    private fun parseQRPair(obj: JSONObject) = QRPair(
        entry = obj.optJSONObject("entry")?.let { parseQR(it, "entry") },
        exit = obj.optJSONObject("exit")?.let { parseQR(it, "exit") }
    )

    private fun parseQR(obj: JSONObject, defaultType: String? = null): QRData {
        val type = obj.optString("type").ifBlank { defaultType ?: "" }
        val qrCodeId = obj.optString("qrCodeId")
            .ifBlank { obj.optString("id") }
            .ifBlank { obj.optString("_id") }
        val url = obj.optString("url")
        val token = obj.optString("token")
        val value = obj.optString("value")
            .takeIf { it.isNotBlank() }
            ?: url.takeIf { it.isNotBlank() }
            ?: token.takeIf { it.isNotBlank() }
            ?: qrCodeId
            ?: ""

        val displayName = obj.optString("name")
            .takeIf { it.isNotBlank() }
            ?: qrCodeId.takeIf { it.isNotBlank() }
            ?: if (type.isNotBlank()) "${type.replaceFirstChar { it.uppercase() }} QR Code" else ""

        return QRData(
            id = obj.optString("id").ifBlank { obj.optString("_id") }.ifBlank { qrCodeId },
            name = displayName,
            value = value,
            type = type,
            action = obj.optString("action"),
            status = obj.optString("status"),
            validFrom = obj.optString("validFrom").takeIf { it.isNotBlank() },
            validUntil = obj.optString("validUntil").takeIf { it.isNotBlank() },
            generatedForDate = obj.optString("generatedForDate").takeIf { it.isNotBlank() },
            token = token,
            url = url,
            imagePath = obj.optString("imagePath").takeIf { it.isNotBlank() },
            imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
            qrCodeId = qrCodeId.takeIf { it.isNotBlank() }
        )
    }

    private fun parseDeviceInfo(obj: JSONObject) = DeviceInfo(
        deviceId = obj.optString("deviceId")
            .ifBlank { obj.optString("id") }
            .ifBlank { obj.optString("_id") },
        deviceName = obj.optString("deviceName").ifBlank { obj.optString("name") },
        platform = obj.optString("platform"),
        model = obj.optString("model"),
        status = obj.optString("status"),
        manufacturer = obj.optString("manufacturer"),
        osVersion = obj.optString("osVersion")
    )

    private fun parseActiveDevice(obj: JSONObject): ActiveDeviceItem {
        val deviceInfo = obj.optJSONObject("deviceInfo")
            ?.let { parseDeviceInfo(it) }
            ?: obj.optJSONObject("device")?.let { parseDeviceInfo(it) }
            ?: parseDeviceInfo(obj)

        val explicitDeviceId = obj.optString("deviceId")
        val mergedDevice = if (deviceInfo.deviceId.isBlank() && explicitDeviceId.isNotBlank()) {
            deviceInfo.copy(deviceId = explicitDeviceId)
        } else deviceInfo

        val id = obj.optString("id")
            .ifBlank { obj.optString("_id") }
            .ifBlank { mergedDevice.deviceId }

        val status = obj.optString("status").ifBlank { mergedDevice.status }

        return ActiveDeviceItem(
            id = id,
            device = mergedDevice.copy(status = status),
            visitorId = obj.optString("visitorId"),
            status = status,
            lastActivity = obj.optString("lastActivity").takeIf { it.isNotBlank() },
            pushToken = obj.optString("pushToken").takeIf { it.isNotBlank() },
            lastEnrollment = obj.optString("lastEnrollment").takeIf { it.isNotBlank() },
            createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() },
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() },
            currentFacility = obj.optJSONObject("currentFacility")?.let { parseFacility(it) }
        )
    }

    private fun parseEnrollment(obj: JSONObject) = EnrollmentDetail(
        enrollmentId = obj.optString("enrollmentId"),
        device = obj.optJSONObject("device")?.let { parseDeviceInfo(it) } ?: parseDeviceInfo(obj),
        facility = obj.optJSONObject("facility")?.let { parseFacility(it) } ?: FacilityData(),
        entryQRCode = obj.optJSONObject("entryQRCode")?.let { parseQR(it, "entry") },
        enrolledAt = obj.optString("enrolledAt")
    )

    private fun buildFacilityBody(
        name: String, description: String,
        address: String, city: String, state: String, country: String,
        emails: List<String>, timezone: String, status: String
    ): JSONObject = JSONObject().apply {
        put("name", name)
        if (description.isNotBlank()) put("description", description)
        put("location", JSONObject().apply {
            put("address", address)
            put("city", city)
            put("state", state)
            put("country", country)
        })
        put("notificationEmails", JSONArray(emails.filter { it.isNotBlank() }))
        put("timezone", timezone)
        put("status", status)
    }

    // ─── Exit Request Parsers ─────────────────────────────────────────────────────
    private fun parseExitRequest(obj: JSONObject): ExitRequest {
        return ExitRequest(
            requestId = obj.optString("requestId"),
            status = obj.optString("status"),
            reason = obj.optString("reason"),
            customReason = obj.optString("customReason").takeIf { it.isNotBlank() },
            requestedAt = obj.optString("requestedAt"),
            processedAt = obj.optString("processedAt").takeIf { it.isNotBlank() },
            adminNotes = obj.optString("adminNotes").takeIf { it.isNotBlank() },
            deviceId = obj.optJSONObject("deviceId")?.let { parseDeviceInfoExt(it) } ?: parseDeviceInfoExt(JSONObject()),
            enrollmentId = obj.optJSONObject("enrollmentId")?.let { parseEnrollmentInfo(it) } ?: EnrollmentInfo("", "", null),
            facilityId = obj.optJSONObject("facilityId")?.let { parseFacilityInfo(it) } ?: FacilityInfo("", "", null),
            processedBy = obj.optJSONObject("processedBy")?.let { parseAdminInfo(it) },
            pushNotificationSent = obj.optBoolean("pushNotificationSent")
        )
    }

    private fun parseExitRequestStatus(obj: JSONObject): ExitRequestStatusResponse {
        return ExitRequestStatusResponse(
            requestId = obj.optString("requestId"),
            status = obj.optString("status"),
            reason = obj.optString("reason"),
            customReason = obj.optString("customReason").takeIf { it.isNotBlank() },
            requestedAt = obj.optString("requestedAt"),
            processedAt = obj.optString("processedAt").takeIf { it.isNotBlank() },
            completedAt = obj.optString("completedAt").takeIf { it.isNotBlank() },
            adminNotes = obj.optString("adminNotes").takeIf { it.isNotBlank() },
            device = obj.optJSONObject("device")?.let { parseDeviceInfoExt(it) } ?: parseDeviceInfoExt(JSONObject()),
            facility = obj.optJSONObject("facility")?.let { parseFacilityInfo(it) } ?: FacilityInfo("", "", null),
            processedBy = obj.optJSONObject("processedBy")?.let { parseAdminInfo(it) }
        )
    }

    private fun parseDeviceInfoExt(obj: JSONObject) = com.sierra.admin.modal.DeviceInfo(
        deviceId = obj.optString("deviceId"),
        visitorId = obj.optString("visitorId"),
        deviceInfo = com.sierra.admin.modal.DeviceSpecs(
            manufacturer = obj.optJSONObject("deviceInfo")?.optString("manufacturer") ?: obj.optString("manufacturer"),
            model = obj.optJSONObject("deviceInfo")?.optString("model") ?: obj.optString("model"),
            platform = obj.optJSONObject("deviceInfo")?.optString("platform") ?: obj.optString("platform", "android")
        ),
        pushToken = obj.optString("pushToken").takeIf { it.isNotBlank() }
    )

    private fun parseEnrollmentInfo(obj: JSONObject) = EnrollmentInfo(
        enrollmentId = obj.optString("enrollmentId"),
        enrolledAt = obj.optString("enrolledAt"),
        facilityId = obj.optString("facilityId").takeIf { it.isNotBlank() }
    )

    private fun parseFacilityInfo(obj: JSONObject) = FacilityInfo(
        name = obj.optString("name"),
        facilityId = obj.optString("facilityId"),
        address = obj.optString("address").takeIf { it.isNotBlank() }
    )

    private fun parseAdminInfo(obj: JSONObject) = AdminInfo(
        name = obj.optString("name"),
        id = obj.optString("id").takeIf { it.isNotBlank() }
    )

    private fun parseStatusCounts(obj: JSONObject) = StatusCounts(
        pending = obj.optInt("pending", 0),
        approved = obj.optInt("approved", 0),
        denied = obj.optInt("denied", 0),
        completed = obj.optInt("completed", 0)
    )

    private fun parseDevicePushInfo(obj: JSONObject) = DevicePushInfo(
        deviceId = obj.optString("deviceId"),
        hasPushToken = obj.optBoolean("hasPushToken")
    )
}
