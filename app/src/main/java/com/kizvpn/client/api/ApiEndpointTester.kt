package com.kizvpn.client.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Утилита для проверки доступных API endpoints на сервере
 */
class ApiEndpointTester(private val baseUrl: String = "http://LOCAL_SERVER_IP:LOCAL_PORT") {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    
    /**
     * Проверка доступности endpoint
     */
    suspend fun testEndpoint(path: String, method: String = "GET"): EndpointResult = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl$path"
            val requestBuilder = Request.Builder().url(url)
            
            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val emptyBody = "".toRequestBody("application/json; charset=utf-8".toMediaType())
                    requestBuilder.post(emptyBody)
                }
                else -> requestBuilder.get()
            }
            
            val request = requestBuilder
                .addHeader("Connection", "close")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            val body = response.body?.string()
            EndpointResult(
                path = path,
                method = method,
                statusCode = response.code,
                isAvailable = response.code != 404 && response.code < 500,
                responseBody = body?.take(500) // Первые 500 символов для анализа
            )
        } catch (e: Exception) {
            EndpointResult(
                path = path,
                method = method,
                statusCode = -1,
                isAvailable = false,
                error = e.message
            )
        }
    }
    
    /**
     * Проверка всех возможных endpoints
     */
    suspend fun testAllEndpoints(): List<EndpointResult> = withContext(Dispatchers.IO) {
        val endpointsToTest = listOf(
            // Root endpoints (проверяем сначала)
            "/" to "GET",
            "/api" to "GET",
            "/api/" to "GET",
            "/xui" to "GET",
            "/xui/" to "GET",
            "/panel" to "GET",
            "/panel/" to "GET",
            "/v1" to "GET",
            "/v1/" to "GET",
            "/health" to "GET",
            "/status" to "GET",
            "/docs" to "GET",
            "/swagger" to "GET",
            
            // X-UI / 3x-ui API endpoints (обычно требуют токен)
            "/api/inbound" to "GET",
            "/api/inbound/list" to "GET",
            "/api/inbound/get" to "GET",
            "/api/user" to "GET",
            "/api/user/list" to "GET",
            "/api/user/get" to "GET",
            "/api/subscription" to "GET",
            "/api/subscription/get" to "GET",
            "/api/block" to "POST",
            "/api/unblock" to "POST",
            
            // Subscription endpoints
            "/api/subscription" to "GET",
            "/api/v1/subscription" to "GET",
            "/v1/subscription" to "GET",
            "/subscription" to "GET",
            "/api/user/subscription" to "GET",
            "/user/subscription" to "GET",
            
            // Activate endpoints
            "/api/activate" to "POST",
            "/api/v1/activate" to "POST",
            "/v1/activate" to "POST",
            "/activate" to "POST",
            "/api/key/activate" to "POST",
            "/key/activate" to "POST",
            
            // Block IP endpoints
            "/api/block-ip" to "POST",
            "/api/v1/block-ip" to "POST",
            "/v1/block-ip" to "POST",
            "/block-ip" to "POST",
            "/api/ip/block" to "POST",
            "/ip/block" to "POST",
            
            // Get blocked IPs endpoints
            "/api/blocked-ips" to "GET",
            "/api/v1/blocked-ips" to "GET",
            "/v1/blocked-ips" to "GET",
            "/blocked-ips" to "GET",
            "/api/ips/blocked" to "GET",
            "/ips/blocked" to "GET",
            "/api/ip/blocked" to "GET",
            
            // Unblock IP endpoints
            "/api/unblock-ip" to "POST",
            "/api/v1/unblock-ip" to "POST",
            "/v1/unblock-ip" to "POST",
            "/unblock-ip" to "POST",
            "/api/ip/unblock" to "POST"
        )
        
        endpointsToTest.map { (path, method) ->
            testEndpoint(path, method)
        }
    }
    
    data class EndpointResult(
        val path: String,
        val method: String,
        val statusCode: Int,
        val isAvailable: Boolean,
        val responseBody: String? = null,
        val error: String? = null
    ) {
        override fun toString(): String {
            return when {
                isAvailable -> "✅ $method $path -> $statusCode"
                error != null -> "❌ $method $path -> ERROR: $error"
                else -> "❌ $method $path -> $statusCode (Not Found)"
            }
        }
    }
}

