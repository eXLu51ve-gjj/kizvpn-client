package com.kizvpn.client.api

import android.util.Log
import com.kizvpn.client.data.SubscriptionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * API клиент для взаимодействия с VPN сервером
 */
class VpnApiClient(
    // TODO: Replace with your server domain or IP for API access
    private val baseUrl: String = "http://YOUR_SERVER_IP:8081",  // Replace with your server IP
    private val subscriptionPort: Int = 2096,                     // Replace with your subscription port (default: 2096)
    private val telegramBotUrl: String? = null,
    private val onFailedConnection: ((String) -> Unit)? = null
) {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // Отключаем повторные попытки для быстрого ответа
        .build()
    
    /**
     * Проверка подписки пользователя по UUID
     * Пробует несколько методов:
     * 1. Telegram Bot API (если настроен)
     * 2. 3x-ui subscription API на порту 2096
     * 3. Стандартный API endpoint
     * @param uuid UUID пользователя из конфига
     * @return информация о подписке (дни и часы) или null если безлимит/ошибка
     */
    suspend fun checkSubscription(uuid: String): SubscriptionInfo? = withContext(Dispatchers.IO) {
        // Метод 1: Попытка через Telegram Bot API
        if (!telegramBotUrl.isNullOrBlank()) {
            try {
                val botResult = checkSubscriptionViaTelegramBot(uuid)
                if (botResult != null) {
                    Log.d("VpnApiClient", "Subscription received via Telegram Bot: ${botResult.format()}")
                    return@withContext botResult
                }
            } catch (e: Exception) {
                Log.w("VpnApiClient", "Telegram Bot API failed, trying other methods", e)
            }
        }
        
        // Метод 2: 3x-ui subscription API на порту 2096
        try {
            val subscriptionResult = checkSubscriptionVia3xUi(uuid)
            if (subscriptionResult != null) {
                Log.d("VpnApiClient", "Subscription received via 3x-ui API: ${subscriptionResult.format()}")
                return@withContext subscriptionResult
            }
        } catch (e: Exception) {
            Log.w("VpnApiClient", "3x-ui subscription API failed, trying standard API", e)
        }
        
        // Метод 3: Стандартный API endpoint на порту 8080
        // Пробуем разные возможные пути API
        val apiEndpoints = listOf(
            "$baseUrl/api/subscription?uuid=$uuid",
            "$baseUrl/api/user/subscription?uuid=$uuid",
            "$baseUrl/api/v1/subscription?uuid=$uuid",
            "$baseUrl/subscription?uuid=$uuid",
            "$baseUrl/panel/api/subscription?uuid=$uuid"
        )
        
        for (url in apiEndpoints) {
            try {
                Log.d("VpnApiClient", "=== Trying API endpoint: $url ===")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Connection", "close")
                    .addHeader("User-Agent", "KIZ-VPN-Client/1.0")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                Log.d("VpnApiClient", "Response code: ${response.code}")
                Log.d("VpnApiClient", "Response message: ${response.message}")
                
                // Проверяем статус перед обработкой body
                val responseCode = response.code
                var shouldContinue = false
                
                if (responseCode == 404) {
                    Log.d("VpnApiClient", "✗ Endpoint not found (404), trying next...")
                    shouldContinue = true
                } else if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.w("VpnApiClient", "✗ API request failed")
                    Log.w("VpnApiClient", "  Code: $responseCode")
                    Log.w("VpnApiClient", "  Message: ${response.message}")
                    Log.w("VpnApiClient", "  Error body: $errorBody")
                    
                    // Отслеживаем неудачные попытки подключения (401, 403, 429 - подозрительные коды)
                    if (responseCode == 401 || responseCode == 403 || responseCode == 429) {
                        // Пытаемся получить IP адрес из заголовков ответа или из URL
                        val clientIp = response.header("X-Real-IP") 
                            ?: response.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
                            ?: try {
                                // Пытаемся извлечь IP из baseUrl (это IP сервера, но для отслеживания можно использовать)
                                java.net.URL(baseUrl).host
                            } catch (e: Exception) {
                                null
                            }
                        
                        if (clientIp != null && clientIp.isNotBlank()) {
                            Log.w("VpnApiClient", "Tracking failed connection from IP: $clientIp (code: $responseCode)")
                            onFailedConnection?.invoke(clientIp)
                        }
                    }
                    
                    shouldContinue = true
                }
                
                if (shouldContinue) {
                    response.close()
                    continue
                }
                
                // Правильно закрываем response body
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        Log.d("VpnApiClient", "Response body length: ${body?.length ?: 0}")
                        Log.d("VpnApiClient", "Response body (first 500 chars): ${body?.take(500)}")
                        
                        if (body != null) {
                            try {
                                val json = JSONObject(body)
                                Log.d("VpnApiClient", "Parsed JSON: $json")
                                
                                // Если есть поле "unlimited" и оно true, возвращаем безлимит
                                if (json.optBoolean("unlimited", false)) {
                                    Log.d("VpnApiClient", "Subscription is unlimited")
                                    return@withContext SubscriptionInfo(unlimited = true)
                                }
                                
                                // Проверяем expired
                                val expired = json.optBoolean("expired", false)
                                
                                // Извлекаем дни и часы
                                val days = if (json.has("days")) json.getInt("days") else 
                                          if (json.has("remaining_days")) json.getInt("remaining_days") else 0
                                val hours = if (json.has("hours")) json.getInt("hours") else 0
                                
                                if (days > 0 || hours > 0) {
                                    Log.d("VpnApiClient", "✓ Found subscription: $days days, $hours hours")
                                    return@withContext SubscriptionInfo(
                                        days = days,
                                        hours = hours,
                                        expired = expired
                                    )
                                }
                                
                                // Если есть только дни (старый формат)
                                if (days > 0) {
                                    Log.d("VpnApiClient", "✓ Found subscription days: $days")
                                    return@withContext SubscriptionInfo(days = days, hours = 0)
                                }
                                
                                // Проверяем другие возможные поля
                                if (json.has("expire")) {
                                    try {
                                        val expireStr = json.getString("expire")
                                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                        val expireDate = dateFormat.parse(expireStr)
                                        expireDate?.let { date ->
                                            val now = System.currentTimeMillis()
                                            val days = ((date.time - now) / (1000 * 60 * 60 * 24)).toInt()
                                            if (days > 0) {
                                                Log.d("VpnApiClient", "✓ Calculated days from expire date: $days")
                                                return@withContext SubscriptionInfo.fromDays(days)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("VpnApiClient", "Failed to parse expire date", e)
                                    }
                                }
                                
                                Log.w("VpnApiClient", "✗ JSON response does not contain subscription days. Available keys: ${json.keys().asSequence().toList()}")
                            } catch (e: Exception) {
                                Log.e("VpnApiClient", "✗ Failed to parse JSON response: $body", e)
                                // Возможно, это не JSON, а конфиг или другой формат
                                val daysFromConfig = parseSubscriptionFromConfig(body)
                                if (daysFromConfig != null) {
                                    Log.d("VpnApiClient", "✓ Found subscription days in non-JSON response: $daysFromConfig")
                                    return@withContext SubscriptionInfo.fromDays(daysFromConfig)
                                } else {
                                    // Если не нашли, продолжаем поиск
                                }
                            }
                        } else {
                            Log.w("VpnApiClient", "✗ Response body is null")
                        }
                    }
                }
            } catch (e: java.net.SocketException) {
                Log.e("VpnApiClient", "✗ Socket error for $url - trying next endpoint", e)
                // Отслеживаем неудачные попытки подключения при сетевых ошибках
                try {
                    val serverIp = java.net.URL(baseUrl).host
                    if (serverIp.isNotBlank()) {
                        onFailedConnection?.invoke(serverIp)
                    }
                } catch (ex: Exception) {
                    // Игнорируем ошибки извлечения IP
                }
                continue
            } catch (e: java.io.IOException) {
                Log.e("VpnApiClient", "✗ IO error for $url - trying next endpoint: ${e.message}", e)
                // Отслеживаем неудачные попытки подключения при IO ошибках
                try {
                    val serverIp = java.net.URL(baseUrl).host
                    if (serverIp.isNotBlank()) {
                        onFailedConnection?.invoke(serverIp)
                    }
                } catch (ex: Exception) {
                    // Игнорируем ошибки извлечения IP
                }
                continue
            } catch (e: Exception) {
                Log.e("VpnApiClient", "✗ Failed to check subscription for $url - trying next endpoint", e)
                continue
            }
        }
        
        Log.w("VpnApiClient", "Subscription check failed, returning null")
        null
    }
    
    /**
     * Проверка доступности API сервера
     * @return true если API доступен, false если нет
     */
    suspend fun checkApiAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val healthUrl = "$baseUrl/health"
            Log.d("VpnApiClient", "Checking API availability: $healthUrl")
            
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .addHeader("Connection", "close")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val isAvailable = response.isSuccessful
            response.close()
            
            Log.d("VpnApiClient", "API availability: $isAvailable")
            return@withContext isAvailable
        } catch (e: Exception) {
            Log.w("VpnApiClient", "API not available: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Проверка подписки для WireGuard конфигов по комментарию/имени клиента
     * Используется когда UUID отсутствует в конфиге WireGuard
     * @param comment Комментарий/имя клиента из конфига WireGuard (например, "fsaad-1")
     * @return информация о подписке или null если не найдено
     */
    suspend fun checkSubscriptionByComment(comment: String): SubscriptionInfo? = withContext(Dispatchers.IO) {
        Log.d("VpnApiClient", "=== checkSubscriptionByComment called ===")
        Log.d("VpnApiClient", "Comment: $comment")
        Log.d("VpnApiClient", "baseUrl: $baseUrl")
        
        // Note: If the server is in local network,
        // API requests should be made WITHOUT VPN (or with configured Split Tunnel).
        // If the server is only accessible through VPN, VPN must be enabled.
        // Local IP is typically accessible without VPN in local network.
        
        if (comment.isBlank()) {
            Log.w("VpnApiClient", "Comment is blank, cannot check subscription")
            return@withContext null
        }
        
        Log.d("VpnApiClient", "Checking subscription by comment/name: $comment")
        Log.d("VpnApiClient", "baseUrl: $baseUrl")
        
        // URL-кодируем комментарий для безопасной передачи в URL
        val encodedComment = try {
            java.net.URLEncoder.encode(comment, "UTF-8")
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to encode comment: ${e.message}", e)
            comment
        }
        Log.d("VpnApiClient", "Encoded comment: $encodedComment")
        
        // Пробуем разные варианты API endpoints для проверки по комментарию/имени/приватному ключу
        // Если comment выглядит как приватный ключ WireGuard (длинный base64 с + и =), используем private_key
        val isPrivateKey = comment.length > 40 && comment.contains("+") && comment.contains("=")
        val paramName = if (isPrivateKey) "private_key" else "comment"
        
        val apiEndpoints = listOf(
            "$baseUrl/api/subscription?$paramName=$encodedComment",
            "$baseUrl/api/subscription?comment=$encodedComment",
            "$baseUrl/api/subscription?name=$encodedComment",
            "$baseUrl/api/subscription?client=$encodedComment",
            "$baseUrl/api/subscription?identifier=$encodedComment",
            "$baseUrl/api/wireguard/subscription?comment=$encodedComment",
            "$baseUrl/api/user/subscription?comment=$encodedComment",
            "$baseUrl/api/v1/subscription?comment=$encodedComment"
        )
        
        Log.d("VpnApiClient", "Will try ${apiEndpoints.size} endpoints")
        apiEndpoints.forEachIndexed { index, url ->
            Log.d("VpnApiClient", "  [$index] $url")
        }
        
        var attemptCount = 0
        for (url in apiEndpoints) {
            attemptCount++
            try {
                Log.d("VpnApiClient", "=== Attempt $attemptCount/${apiEndpoints.size}: Trying API endpoint (by comment) ===")
                Log.d("VpnApiClient", "URL: $url")
                Log.d("VpnApiClient", "Creating HTTP request...")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Connection", "close")
                    .addHeader("User-Agent", "KIZ-VPN-Client/1.0")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                Log.d("VpnApiClient", "Response code: ${response.code}")
                
                var shouldContinue = false
                
                response.use { resp ->
                    if (resp.code == 404) {
                        Log.d("VpnApiClient", "Endpoint not found (404), trying next...")
                        shouldContinue = true
                    } else if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            Log.d("VpnApiClient", "API response (by comment): ${body.take(200)}")
                            
                            try {
                                val json = JSONObject(body)
                                
                                // Проверяем unlimited
                                if (json.optBoolean("unlimited", false)) {
                                    Log.d("VpnApiClient", "Subscription is unlimited (by comment)")
                                    return@withContext SubscriptionInfo(unlimited = true)
                                }
                                
                                // Проверяем expired
                                val expired = json.optBoolean("expired", false)
                                if (expired) {
                                    Log.d("VpnApiClient", "Subscription is expired (by comment)")
                                    return@withContext SubscriptionInfo(expired = true)
                                }
                                
                                // Пытаемся извлечь дни и часы
                                // Проверяем разные варианты полей: days, remaining_days
                                val days = if (json.has("days")) {
                                    json.getInt("days")
                                } else if (json.has("remaining_days")) {
                                    json.getInt("remaining_days")
                                } else {
                                    -1
                                }
                                
                                val hours = json.optInt("hours", -1)
                                
                                // Логируем все доступные поля для отладки
                                Log.d("VpnApiClient", "JSON keys: ${json.keys().asSequence().toList()}")
                                Log.d("VpnApiClient", "Days: $days, Hours: $hours, Expired: $expired")
                                
                                if (days >= 0 && hours >= 0) {
                                    Log.d("VpnApiClient", "✓ Subscription found (by comment): $days days, $hours hours")
                                    return@withContext SubscriptionInfo(
                                        days = days,
                                        hours = hours,
                                        unlimited = false,
                                        expired = expired
                                    )
                                } else if (days >= 0) {
                                    Log.d("VpnApiClient", "✓ Subscription found (by comment): $days days (hours not available)")
                                    return@withContext SubscriptionInfo(
                                        days = days,
                                        hours = if (hours >= 0) hours else 0,
                                        unlimited = false,
                                        expired = expired
                                    )
                                } else if (hours >= 0) {
                                    // Если есть только часы (маловероятно, но возможно)
                                    Log.d("VpnApiClient", "✓ Subscription found (by comment): $hours hours (days not available)")
                                    return@withContext SubscriptionInfo(
                                        days = 0,
                                        hours = hours,
                                        unlimited = false,
                                        expired = expired
                                    )
                                } else {
                                    // Не удалось извлечь дни и часы
                                    Log.w("VpnApiClient", "✗ Could not extract days/hours from response. Full JSON: $json")
                                }
                            } catch (e: Exception) {
                                Log.w("VpnApiClient", "Failed to parse JSON response (by comment)", e)
                            }
                        } else {
                            // Body is null
                            Log.w("VpnApiClient", "Response body is null")
                        }
                    } else {
                        Log.w("VpnApiClient", "API request failed (by comment): ${resp.code} ${resp.message}")
                    }
                }
                
                if (shouldContinue) {
                    continue
                }
            } catch (e: java.io.IOException) {
                // Обработка сетевых ошибок (включая "unexpected end of stream")
                val errorMessage = e.message ?: "Unknown IO error"
                Log.w("VpnApiClient", "=== IO Error for endpoint: $url ===")
                Log.w("VpnApiClient", "Error: $errorMessage")
                
                // Если это "unexpected end of stream", это может означать:
                // 1. Сервер закрыл соединение преждевременно
                // 2. API не запущен или не отвечает
                // 3. Проблемы с сетью
                if (errorMessage.contains("unexpected end of stream") || 
                    errorMessage.contains("EOFException")) {
                    Log.w("VpnApiClient", "Server closed connection prematurely. Possible causes:")
                    Log.w("VpnApiClient", "  1. API server is not running on port 8081")
                    Log.w("VpnApiClient", "  2. API server is crashing/erroring")
                    Log.w("VpnApiClient", "  3. Network connectivity issues")
                    Log.w("VpnApiClient", "  4. Firewall blocking connection")
                }
                
                // Продолжаем попытки с другими endpoints
                continue
            } catch (e: java.net.SocketException) {
                Log.w("VpnApiClient", "=== Socket Error for endpoint: $url ===")
                Log.w("VpnApiClient", "Error: ${e.message}")
                Log.w("VpnApiClient", "Server may be unreachable or port is closed")
                continue
            } catch (e: java.net.SocketTimeoutException) {
                Log.w("VpnApiClient", "=== Timeout Error for endpoint: $url ===")
                Log.w("VpnApiClient", "Error: ${e.message}")
                Log.w("VpnApiClient", "Server is not responding within timeout period")
                continue
            } catch (e: Exception) {
                Log.e("VpnApiClient", "=== ERROR: Failed to check subscription by comment for $url ===")
                Log.e("VpnApiClient", "Exception type: ${e.javaClass.simpleName}")
                Log.e("VpnApiClient", "Exception message: ${e.message}")
                Log.e("VpnApiClient", "Stack trace:", e)
                continue
            }
        }
        
        Log.w("VpnApiClient", "=== Subscription check by comment completed ===")
        Log.w("VpnApiClient", "All ${apiEndpoints.size} endpoints tried, no subscription found")
        Log.w("VpnApiClient", "Returning null")
        null
    }
    
    /**
     * Проверка подписки через Telegram Bot
     * Пробует HTTP API, если доступен, иначе использует SSH для получения информации
     */
    private suspend fun checkSubscriptionViaTelegramBot(uuid: String): SubscriptionInfo? {
        // Метод 1: Попытка через HTTP API (если настроен)
        if (!telegramBotUrl.isNullOrBlank()) {
            try {
                val endpoints = listOf(
                    "$telegramBotUrl/api/subscription?uuid=$uuid",
                    "$telegramBotUrl/subscription?uuid=$uuid",
                    "$telegramBotUrl/api/user/subscription?uuid=$uuid"
                )
                
                for (url in endpoints) {
                    try {
                        Log.d("VpnApiClient", "Trying Telegram Bot HTTP API: $url")
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("Connection", "close")
                            .build()
                        
                        val response = httpClient.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                try {
                                    val json = JSONObject(body)
                                    if (json.optBoolean("unlimited", false)) {
                                        return SubscriptionInfo(unlimited = true)
                                    }
                                    val expired = json.optBoolean("expired", false)
                                    val days = if (json.has("days")) json.getInt("days") else 
                                              if (json.has("remaining_days")) json.getInt("remaining_days") else 0
                                    val hours = if (json.has("hours")) json.getInt("hours") else 0
                                    if (days > 0 || hours > 0) {
                                        return SubscriptionInfo(days = days, hours = hours, expired = expired)
                                    }
                                    if (days > 0) {
                                        return SubscriptionInfo.fromDays(days)
                                    }
                                } catch (e: Exception) {
                                    Log.w("VpnApiClient", "Failed to parse Telegram Bot HTTP response", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("VpnApiClient", "Telegram Bot HTTP endpoint failed: $url", e)
                    }
                }
            } catch (e: Exception) {
                Log.w("VpnApiClient", "Telegram Bot HTTP API check failed", e)
            }
        }
        
        // Метод 2: Попытка через SSH (если бот на сервере 10.10.10.120)
        // Это будет реализовано через SshClient в MainActivity
        // Здесь возвращаем null, чтобы попробовать другие методы
        return null
    }
    
    /**
     * Проверка подписки через 3x-ui subscription API на порту 2096
     * 3x-ui subscription API может возвращать конфиг с информацией о подписке
     */
    private suspend fun checkSubscriptionVia3xUi(uuid: String): SubscriptionInfo? {
        try {
            // 3x-ui subscription API usually uses format /sub/TOKEN or /json/TOKEN
            val endpoints = listOf(
                "http://LOCAL_SERVER_IP:$subscriptionPort/sub/$uuid",
                "http://LOCAL_SERVER_IP:$subscriptionPort/json/$uuid",
                "http://LOCAL_SERVER_IP:$subscriptionPort/api/subscription?uuid=$uuid"
            )
            
            for (url in endpoints) {
                try {
                    Log.d("VpnApiClient", "Trying 3x-ui subscription API: $url")
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Connection", "close")
                        .addHeader("User-Agent", "KIZ-VPN-Client/1.0")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    
                    // Правильно закрываем response body
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                            Log.d("VpnApiClient", "3x-ui subscription API response (length: ${body.length}, first 200 chars: ${body.take(200)})")
                            
                            // Пробуем распарсить как JSON
                            try {
                                val json = JSONObject(body)
                                Log.d("VpnApiClient", "3x-ui returned JSON: $json")
                                
                                // Проверяем unlimited
                                if (json.optBoolean("unlimited", false)) {
                                    Log.d("VpnApiClient", "Subscription is unlimited")
                                    return SubscriptionInfo(unlimited = true)
                                }
                                
                                // Проверяем expired
                                val expired = json.optBoolean("expired", false)
                                
                                // Извлекаем дни и часы
                                val days = if (json.has("days")) json.getInt("days") else 
                                          if (json.has("remaining_days")) json.getInt("remaining_days") else 0
                                val hours = if (json.has("hours")) json.getInt("hours") else 0
                                
                                if (days > 0 || hours > 0) {
                                    Log.d("VpnApiClient", "✓ Found subscription: $days days, $hours hours")
                                    return SubscriptionInfo(
                                        days = days,
                                        hours = hours,
                                        expired = expired
                                    )
                                }
                                
                                // Если есть только дни (старый формат)
                                if (days > 0) {
                                    Log.d("VpnApiClient", "✓ Found subscription days: $days")
                                    return SubscriptionInfo(days = days, hours = 0)
                                }
                                
                                // Проверяем другие возможные поля
                                if (json.has("expire")) {
                                    try {
                                        val expireStr = json.getString("expire")
                                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                        val expireDate = dateFormat.parse(expireStr)
                                        expireDate?.let { date ->
                                            val now = System.currentTimeMillis()
                                            val days = ((date.time - now) / (1000 * 60 * 60 * 24)).toInt()
                                            if (days > 0) {
                                                Log.d("VpnApiClient", "Calculated days from expire date: $days")
                                                return SubscriptionInfo(days = days, hours = 0)
                                            } else {
                                                // Дни истекли или некорректны
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("VpnApiClient", "Failed to parse expire date from JSON", e)
                                    }
                                } else {
                                    // Поле expire отсутствует
                                }
                            } catch (e: Exception) {
                                // Если не JSON, возможно это конфиг VPN (может быть зашифрован или в другом формате)
                                Log.d("VpnApiClient", "3x-ui returned non-JSON response (likely VPN config or encrypted), trying to parse...")
                                
                                // Проверяем, что body не null
                                if (body != null) {
                                    // Пробуем декодировать base64 (если конфиг закодирован)
                                    var configToParse: String = body
                                    try {
                                        val decoded = android.util.Base64.decode(body, android.util.Base64.DEFAULT)
                                        configToParse = String(decoded)
                                        Log.d("VpnApiClient", "Decoded base64 config")
                                    } catch (e: Exception) {
                                        // Не base64, используем как есть
                                        Log.d("VpnApiClient", "Response is not base64, using as-is")
                                    }
                                    
                                    // Парсим конфиг для поиска информации о подписке
                                    val daysFromConfig = parseSubscriptionFromConfig(configToParse)
                                    if (daysFromConfig != null) {
                                        Log.d("VpnApiClient", "Found subscription days in config: $daysFromConfig")
                                        return SubscriptionInfo.fromDays(daysFromConfig)
                                    } else {
                                        Log.w("VpnApiClient", "Could not extract subscription days from config")
                                    }
                                } else {
                                    Log.w("VpnApiClient", "Body is null, cannot parse config")
                                }
                            }
                        } else {
                            Log.w("VpnApiClient", "3x-ui subscription API returned empty body")
                        }
                    } else if (resp.code == 400) {
                        // 400 означает, что endpoint существует, но формат неправильный
                        // Возможно, нужно использовать токен подписки вместо UUID
                        Log.d("VpnApiClient", "3x-ui subscription endpoint exists but format is wrong (400). " +
                                "Note: Subscription API on port 2096 usually requires subscription token, not UUID")
                    } else {
                        Log.w("VpnApiClient", "3x-ui subscription API returned status: ${resp.code}")
                    }
                }
                } catch (e: Exception) {
                    Log.d("VpnApiClient", "3x-ui subscription endpoint failed: $url", e)
                }
            }
        } catch (e: Exception) {
            Log.w("VpnApiClient", "3x-ui subscription API check failed", e)
        }
        
        return null
    }
    
    /**
     * Парсинг информации о подписке из конфига VPN
     * 3x-ui встраивает информацию о подписке в комментарии конфига, когда включена опция
     * "Показать информацию об использовании"
     * Форматы: "Осталось дней: 7", "Remaining days: 7", "days: 7", "expire: 2024-01-01" и т.д.
     */
    private fun parseSubscriptionFromConfig(config: String): Int? {
        try {
            Log.d("VpnApiClient", "Parsing subscription info from config (length: ${config.length})")
            
            // Ищем паттерны в разных форматах (русский и английский)
            val patterns = listOf(
                // Русские форматы
                Regex("""осталось\s+дней[:\s=]+(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""остаток[:\s=]+(\d+)\s+дней""", RegexOption.IGNORE_CASE),
                Regex("""дней[:\s=]+(\d+)""", RegexOption.IGNORE_CASE),
                // Английские форматы
                Regex("""remaining\s+days[:\s=]+(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""days[:\s=]+(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""subscription[:\s=]+(\d+)""", RegexOption.IGNORE_CASE),
                // Форматы с датой окончания (вычисляем дни до окончания)
                Regex("""expire[:\s=]+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE),
                Regex("""expiry[:\s=]+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE),
                // Форматы в комментариях VLESS (после #)
                Regex("""#.*?(\d+)\s*дней""", RegexOption.IGNORE_CASE),
                Regex("""#.*?(\d+)\s*days""", RegexOption.IGNORE_CASE)
            )
            
            for (pattern in patterns) {
                val match = pattern.find(config)
                if (match != null) {
                    val value = match.groupValues[1]
                    
                    // Если это дата, вычисляем дни до окончания
                    if (value.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                        try {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            val expireDate = dateFormat.parse(value)
                            expireDate?.let { date ->
                                val now = System.currentTimeMillis()
                                val days = ((date.time - now) / (1000 * 60 * 60 * 24)).toInt()
                                if (days > 0) {
                                    Log.d("VpnApiClient", "Found subscription expire date: $value, days remaining: $days")
                                    return days
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("VpnApiClient", "Failed to parse expire date: $value", e)
                        }
                    } else {
                        // Это число дней
                        val days = value.toIntOrNull()
                        if (days != null && days > 0) {
                            Log.d("VpnApiClient", "Found subscription days in config: $days (pattern: ${pattern.pattern})")
                            return days
                        }
                    }
                }
            }
            
            // Если не нашли в паттернах, ищем в комментариях после # в VLESS конфиге
            val commentMatch = Regex("""#(.+?)(?:\s|$)""").find(config)
            if (commentMatch != null) {
                val comment = commentMatch.groupValues[1]
                Log.d("VpnApiClient", "Found comment in config: $comment")
                
                // Ищем числа в комментарии
                val numberMatch = Regex("""(\d+)""").find(comment)
                if (numberMatch != null) {
                    val number = numberMatch.groupValues[1].toIntOrNull()
                    // Если число разумное (от 1 до 3650 дней = 10 лет), считаем это днями
                    if (number != null && number in 1..3650) {
                        Log.d("VpnApiClient", "Found potential subscription days in comment: $number")
                        return number
                    }
                }
            }
            
            Log.d("VpnApiClient", "No subscription days found in config")
        } catch (e: Exception) {
            Log.w("VpnApiClient", "Failed to parse subscription from config", e)
        }
        
        return null
    }
    
    /**
     * Активация ключа
     * @param key ключ для активации
     * @return количество дней подписки или null если ошибка
     */
    suspend fun activateKey(key: String): Int? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/activate"
            val jsonBody = JSONObject().apply {
                put("key", key)
            }.toString()
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        
                        if (json.optBoolean("unlimited", false)) {
                            return@withContext null
                        }
                        
                        if (json.has("days")) {
                            return@withContext json.getInt("days")
                        }
                        
                        if (json.has("remaining_days")) {
                            return@withContext json.getInt("remaining_days")
                        }
                    } catch (e: Exception) {
                        Log.e("VpnApiClient", "Failed to parse activation response", e)
                    }
                }
            } else {
                Log.w("VpnApiClient", "Activation failed with code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to activate key", e)
        }
        
        null
    }
    
    /**
     * Блокировка IP адреса
     * @param ipAddress IP адрес для блокировки
     * @return true если успешно заблокирован, false если ошибка
     */
    suspend fun blockIpAddress(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/block-ip"
            Log.d("VpnApiClient", "Blocking IP $ipAddress via: $url")
            val jsonBody = JSONObject().apply {
                put("ip", ipAddress)
            }.toString()
            Log.d("VpnApiClient", "Request body: $jsonBody")
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Connection", "close")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            Log.d("VpnApiClient", "Block IP response code: ${response.code}, message: ${response.message}")
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                Log.d("VpnApiClient", "Block IP response body: $body")
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        val success = json.optBoolean("success", false)
                        Log.d("VpnApiClient", "IP $ipAddress blocked: $success")
                        return@withContext success
                    } catch (e: Exception) {
                        Log.e("VpnApiClient", "Failed to parse block IP response: $body", e)
                    }
                }
                // Если ответ успешный, но нет JSON, считаем что блокировка прошла
                Log.d("VpnApiClient", "IP $ipAddress blocked (successful response without JSON)")
                return@withContext true
            } else {
                val errorBody = response.body?.string()
                Log.w("VpnApiClient", "Block IP failed with code: ${response.code}, message: ${response.message}, body: $errorBody")
            }
        } catch (e: java.net.SocketException) {
            Log.e("VpnApiClient", "Socket error blocking IP $ipAddress - server may be unreachable", e)
        } catch (e: java.io.IOException) {
            Log.e("VpnApiClient", "IO error blocking IP $ipAddress - connection issue: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to block IP $ipAddress", e)
        }
        
        Log.w("VpnApiClient", "Failed to block IP $ipAddress")
        false
    }
    
    /**
     * Получение списка заблокированных IP
     * @return список заблокированных IP адресов
     */
    suspend fun getBlockedIps(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/blocked-ips"
            Log.d("VpnApiClient", "Getting blocked IPs from: $url")
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Connection", "close")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                Log.d("VpnApiClient", "Blocked IPs response body: $body")
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        if (json.has("ips")) {
                            val ipsArray = json.getJSONArray("ips")
                            val ipsList = mutableListOf<String>()
                            for (i in 0 until ipsArray.length()) {
                                ipsList.add(ipsArray.getString(i))
                            }
                            Log.d("VpnApiClient", "Parsed ${ipsList.size} blocked IPs: $ipsList")
                            return@withContext ipsList
                        } else {
                            Log.w("VpnApiClient", "Response JSON doesn't have 'ips' field: $body")
                        }
                    } catch (e: Exception) {
                        Log.e("VpnApiClient", "Failed to parse blocked IPs response: $body", e)
                    }
                } else {
                    Log.w("VpnApiClient", "Response body is null")
                }
            } else {
                Log.w("VpnApiClient", "Get blocked IPs failed with code: ${response.code}, message: ${response.message}")
            }
        } catch (e: java.net.SocketException) {
            Log.e("VpnApiClient", "Socket error getting blocked IPs - server may be unreachable", e)
        } catch (e: java.io.IOException) {
            Log.e("VpnApiClient", "IO error getting blocked IPs - connection issue: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to get blocked IPs", e)
        }
        
        Log.d("VpnApiClient", "Returning empty list of blocked IPs")
        emptyList()
    }
    
    /**
     * Разблокировка IP адреса
     * @param ipAddress IP адрес для разблокировки
     * @return true если успешно разблокирован, false если ошибка
     */
    suspend fun unblockIpAddress(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/unblock-ip"
            val jsonBody = JSONObject().apply {
                put("ip", ipAddress)
            }.toString()
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        val json = JSONObject(body)
                        val success = json.optBoolean("success", false)
                        Log.d("VpnApiClient", "IP $ipAddress unblocked: $success")
                        return@withContext success
                    } catch (e: Exception) {
                        Log.e("VpnApiClient", "Failed to parse unblock IP response", e)
                    }
                }
                // Если ответ успешный, но нет JSON, считаем что разблокировка прошла
                return@withContext true
            } else {
                Log.w("VpnApiClient", "Unblock IP failed with code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to unblock IP", e)
        }
        
        false
    }
}

