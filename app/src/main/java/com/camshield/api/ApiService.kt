package com.camshield.api

import com.camshield.api.models.*
import com.camshield.utils.Constants
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
}
