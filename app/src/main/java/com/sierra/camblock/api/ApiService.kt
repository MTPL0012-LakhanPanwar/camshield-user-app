package com.sierra.camblock.api

import com.sierra.camblock.api.models.*
import com.sierra.camblock.utils.Constants
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    /**
     * Validate QR code token
     * Endpoint: POST /api/enrollments/validate-qr
     */
    @POST(Constants.ENDPOINT_VALIDATE_QR)
    suspend fun validateQR(
        @Body request: ValidateQRRequest
    ): Response<ApiResponse<ValidateQRResponse>>
    
    
    /**
     * Scan entry QR code (Lock camera)
     * Endpoint: POST /enrollments/scan-entry
     */
    @POST(Constants.ENDPOINT_SCAN_ENTRY)
    suspend fun scanEntry(
        @Body request: ScanEntryRequest
    ): Response<ApiResponse<EnrollmentResponse>>
    
    
    /**
     * Scan exit QR code (Unlock camera)
     * Endpoint: POST /enrollments/scan-exit
     */
    @POST(Constants.ENDPOINT_SCAN_EXIT)
    suspend fun scanExit(
        @Body request: ScanExitRequest
    ): Response<ApiResponse<EnrollmentResponse>>
    
    
    /**
     * Get enrollment status
     * Endpoint: GET /api/enrollments/status/:deviceId
     */
    @GET(Constants.ENDPOINT_STATUS)
    suspend fun getEnrollmentStatus(
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<EnrollmentStatusResponse>>

    /**
     * Create Force Exit Request
     * Endpoint: POST /api/force-exit/request
     * Request body requires deviceId and optional reason
     */
    @POST(Constants.ENDPOINT_FORCE_EXIT_REQUEST)
    suspend fun createForceExitRequest(
        @Body request: ForceExitRequest
    ): Response<ApiResponse<ForceExitResponse>>

    /**
     * Check Request Status
     * Endpoint: GET /api/force-exit/status/:deviceId
     * Returns the current status of the force exit request for a device
     */
    @GET("/api/force-exit/status/{deviceId}")
    suspend fun checkRequestStatus(
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<ForceExitStatusResponse>>

    /**
     * Complete Force Exit (Handle Push Notification)
     * Endpoint: POST /api/force-exit/complete
     * Called when user taps the push notification to restore permissions
     */
    @POST("/api/force-exit/complete")
    suspend fun completeForceExit(
        @Body request: CompleteForceExitRequest
    ): Response<ApiResponse<CompleteForceExitResponse>>


}
