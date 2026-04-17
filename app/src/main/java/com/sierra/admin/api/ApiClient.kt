package com.camshield.admin.api

import android.content.Context
import com.sierra.admin.Constants
import com.sierra.admin.auth.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject

class ApiClient(context: Context) {
    val tokenManager = TokenManager(context)

    private val client = HttpClient(Android) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
            requestTimeoutMillis = 15_000
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                }
            )
        }
        defaultRequest {
            url(Constants.BASE_URL)
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
    }

    suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        requiresAuth: Boolean = true
    ): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val response = client.request {
                url(path)
                this.method = HttpMethod.parse(method)
                if (requiresAuth) {
                    tokenManager.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                if (body != null) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(body.toString())
                }
            }

            val code = response.status.value
            val text = response.bodyAsText()
            val json = text.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            Pair(code, json)
        } catch (e: Exception) {
            val errJson = JSONObject().put("message", e.localizedMessage ?: "Network error")
            Pair(-1, errJson)
        }
    }

    suspend fun get(path: String, auth: Boolean = true) =
        request("GET", path, requiresAuth = auth)

    suspend fun post(path: String, body: JSONObject, auth: Boolean = true) =
        request("POST", path, body, auth)

    suspend fun put(path: String, body: JSONObject) =
        request("PUT", path, body)

    suspend fun delete(path: String) =
        request("DELETE", path)
}
