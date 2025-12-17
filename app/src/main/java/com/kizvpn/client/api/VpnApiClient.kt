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
 * Клиент для парсинга Subscription URL и извлечения конфигов
 * Универсальный - работает с любыми VPN серверами без привязки к конкретным API
 */
class VpnApiClient {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // Отключаем повторные попытки для быстрого ответа
        .build()
    
    // Старые методы API (checkSubscription, checkSubscriptionByComment, activateKey, checkApiAvailability) удалены
    // Клиент работает только с парсингом Subscription URL, без зависимостей от серверов
    // Все методы с API вызовами к серверам удалены - теперь работаем только с парсингом Subscription URL
    
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
    
    // Старые методы API удалены (activateKey, blockIpAddress, unblockIpAddress, getBlockedIps)
    // Они использовали baseUrl и больше не нужны
    
    /**
     * Получение информации о подписке из Subscription URL
     * Subscription URL обычно имеет формат: https://host.kizvpn.ru/sub/TOKEN
     * где TOKEN - это base64-encoded строка с данными пользователя
     * 
     * @param subscriptionUrl Полный URL подписки (например, https://host.kizvpn.ru/sub/dXNlcl8...)
     * @return Информация о подписке или null если ошибка
     */
    suspend fun getSubscriptionInfoFromUrl(subscriptionUrl: String): SubscriptionInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d("VpnApiClient", "=== Получение информации о подписке из Subscription URL ===")
            Log.d("VpnApiClient", "URL: $subscriptionUrl")
            
            // Убираем пробелы и переносы строк
            val cleanUrl = subscriptionUrl.trim()
            
            // Проверяем, что это валидный URL
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                Log.w("VpnApiClient", "Invalid subscription URL format (must start with http:// or https://)")
                return@withContext null
            }
            
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .addHeader("Connection", "close")
                .addHeader("User-Agent", "KIZ-VPN-Client/1.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        Log.d("VpnApiClient", "Subscription URL response received (length: ${body.length})")
                        
                        // Subscription URL может вернуть:
                        // 1. JSON с информацией о подписке
                        // 2. Список конфигов с информацией в заголовках/комментариях
                        // 3. Просто конфиги
                        
                        // Сначала проверяем заголовки ответа
                        val headers = resp.headers
                        // Логируем все заголовки для отладки
                        Log.d("VpnApiClient", "Response headers:")
                        headers.names().forEach { headerName ->
                            Log.d("VpnApiClient", "  $headerName: ${headers.get(headerName)}")
                        }
                        
                        val subscriptionInfoFromHeaders = parseSubscriptionFromHeaders(headers)
                        if (subscriptionInfoFromHeaders != null) {
                            Log.d("VpnApiClient", "✓ Subscription info found in response headers: ${subscriptionInfoFromHeaders.format()}")
                            return@withContext subscriptionInfoFromHeaders
                        } else {
                            Log.d("VpnApiClient", "No subscription info in response headers, trying other methods")
                        }
                        
                        // Пробуем распарсить как JSON
                        try {
                            val json = JSONObject(body)
                            Log.d("VpnApiClient", "Subscription URL returned JSON: ${json.keys().asSequence().toList()}")
                            
                            val subscriptionInfo = parseSubscriptionFromJson(json)
                            if (subscriptionInfo != null) {
                                Log.d("VpnApiClient", "✓ Subscription info parsed from JSON: ${subscriptionInfo.format()}")
                                return@withContext subscriptionInfo
                            }
                        } catch (e: Exception) {
                            // Не JSON, возможно это список конфигов
                            Log.d("VpnApiClient", "Response is not JSON, trying to parse as config list")
                        }
                        
                        // Пробуем найти информацию в теле ответа (конфиги)
                        val subscriptionInfoFromBody = parseSubscriptionFromConfigList(body)
                        if (subscriptionInfoFromBody != null) {
                            Log.d("VpnApiClient", "✓ Subscription info found in config list")
                            return@withContext subscriptionInfoFromBody
                        }
                        
                        // Переменная для хранения информации об истекшей подписке (если timestamp показал истекшую)
                        var expiredSubscriptionInfo: SubscriptionInfo? = null
                        
                        // Если не удалось извлечь из конфига, пробуем извлечь username из самого URL
                        // Subscription URL обычно имеет формат: https://host.kizvpn.ru/sub/USERNAME или /sub/BASE64_TOKEN
                        try {
                            val urlParts = cleanUrl.split("/sub/")
                            if (urlParts.size > 1) {
                                val tokenOrUsername = urlParts[1].trim().split("?")[0].split("#")[0] // Убираем параметры и якоря
                                Log.d("VpnApiClient", "Extracted token/username from URL: ${tokenOrUsername.take(20)}...")
                                
                                // Пробуем декодировать как base64 (если это base64-encoded username)
                                try {
                                    val decoded = android.util.Base64.decode(tokenOrUsername, android.util.Base64.DEFAULT)
                                    val decodedString = String(decoded)
                                    Log.d("VpnApiClient", "Decoded from base64: $decodedString")
                                    
                                    // Извлекаем username и timestamp из токена (формат: "username,timestamp")
                                    // В decodedString могут быть лишние символы после timestamp, поэтому нужно извлекать только цифры
                                    var username = decodedString.trim()
                                    var expiryTimestamp: Long? = null
                                    var isUnlimited = false
                                    
                                    Log.d("VpnApiClient", "Full decoded string length: ${decodedString.length}, first 100 chars: ${decodedString.take(100)}")
                                    
                                    if (decodedString.contains(",")) {
                                        val parts = decodedString.split(",")
                                        username = parts[0].trim()
                                        
                                        // Вторая часть может быть timestamp окончания подписки
                                        // Извлекаем только цифры из второй части (на случай если там есть лишние символы)
                                        val timestampPart = parts[1].trim()
                                        Log.d("VpnApiClient", "Timestamp part from token: ${timestampPart.take(50)}")
                                        
                                        // Извлекаем только цифры из начала строки (до первого нецифрового символа)
                                        val timestampStr = timestampPart.takeWhile { it.isDigit() }
                                        Log.d("VpnApiClient", "Extracted timestamp digits: $timestampStr")
                                        
                                        if (timestampStr.isNotBlank()) {
                                            expiryTimestamp = timestampStr.toLongOrNull()?.let { ts ->
                                                Log.d("VpnApiClient", "Parsed timestamp value: $ts")
                                                
                                                // Проверяем на безлимит (0, -1)
                                                if (ts == 0L || ts == -1L) {
                                                    // Это безлимитная подписка
                                                    Log.d("VpnApiClient", "Timestamp indicates unlimited subscription (ts=$ts)")
                                                    isUnlimited = true
                                                    null // Не устанавливаем expiryDate для безлимита
                                                } else {
                                                    // Определяем, это миллисекунды или секунды
                                                    // Разделительная точка: 946684800000 (2000-01-01 в миллисекундах)
                                                    // Если меньше - это секунды, иначе миллисекунды
                                                    val ms: Long
                                                    if (ts < 946684800000) {
                                                        // Это секунды (значение меньше 2000 года в миллисекундах)
                                                        ms = ts * 1000
                                                        Log.d("VpnApiClient", "Timestamp is in seconds: $ts, converting to milliseconds: $ms")
                                                    } else {
                                                        // Это миллисекунды
                                                        ms = ts
                                                        Log.d("VpnApiClient", "Timestamp is in milliseconds: $ts")
                                                    }
                                                    
                                                    // Проверяем, что дата в разумном диапазоне (2000-2200 год)
                                                    // 2000-01-01 в миллисекундах = 946684800000
                                                    // 2200-01-01 в миллисекундах = 7258118400000
                                                    val year2000Ms = 946684800000L
                                                    val year2200Ms = 7258118400000L
                                                    
                                                    if (ms < year2000Ms || ms > year2200Ms) {
                                                        Log.w("VpnApiClient", "Timestamp $ms (${java.util.Date(ms)}) is outside valid range (2000-2200), treating as unlimited")
                                                        isUnlimited = true
                                                        null
                                                    } else {
                                                        // Проверяем, если дата окончания в очень далеком будущем (> 2100 год),
                                                        // считаем это безлимитной подпиской
                                                        val year2100Ms = 4102444800000L // 2100-01-01 в миллисекундах
                                                        if (ms > year2100Ms) {
                                                            Log.d("VpnApiClient", "Timestamp points to year > 2100, treating as unlimited")
                                                            isUnlimited = true
                                                            null
                                                        } else {
                                                            ms
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // Если это безлимитная подписка
                                            if (isUnlimited) {
                                                val subscriptionInfo = SubscriptionInfo(
                                                    days = 0,
                                                    hours = 0,
                                                    unlimited = true,
                                                    expired = false,
                                                    expiryDate = null,
                                                    totalTraffic = null,
                                                    usedTraffic = null,
                                                    remainingTraffic = null
                                                )
                                                Log.d("VpnApiClient", "✓ Subscription info extracted from token (UNLIMITED): ${subscriptionInfo.format()}")
                                                return@withContext subscriptionInfo
                                            }
                                            
                                            if (expiryTimestamp != null && !isUnlimited) {
                                                Log.d("VpnApiClient", "Found expiry timestamp in token: $expiryTimestamp (${java.util.Date(expiryTimestamp)})")
                                                // Вычисляем дни до окончания
                                                val now = System.currentTimeMillis()
                                                val daysUntilExpiry = ((expiryTimestamp - now) / (1000 * 60 * 60 * 24)).toInt()
                                                val isExpired = expiryTimestamp < now
                                                
                                                // Дополнительная проверка: если дата окончания в очень далеком будущем (> 50 лет),
                                                // считаем это безлимитной подпиской
                                                val fiftyYearsFromNow = now + (50L * 365 * 24 * 60 * 60 * 1000)
                                                if (expiryTimestamp > fiftyYearsFromNow) {
                                                    Log.d("VpnApiClient", "Expiry date is more than 50 years in future, treating as unlimited")
                                                    val subscriptionInfo = SubscriptionInfo(
                                                        days = 0,
                                                        hours = 0,
                                                        unlimited = true,
                                                        expired = false,
                                                        expiryDate = null,
                                                        totalTraffic = null,
                                                        usedTraffic = null,
                                                        remainingTraffic = null
                                                    )
                                                    Log.d("VpnApiClient", "✓ Subscription info extracted from token (UNLIMITED): ${subscriptionInfo.format()}")
                                                    return@withContext subscriptionInfo
                                                }
                                                
                                                // Вычисляем, на сколько времени истекла подписка (в часах)
                                                val hoursExpired = ((now - expiryTimestamp) / (1000 * 60 * 60)).toInt()
                                                val daysExpired = hoursExpired / 24
                                                
                                                Log.d("VpnApiClient", "Days until expiry: $daysUntilExpiry, expired: $isExpired, hours expired: $hoursExpired")
                                                
                                                if (!isExpired) {
                                                    // Подписка еще активна по timestamp - показываем количество дней
                                                    val subscriptionInfo = SubscriptionInfo(
                                                        days = if (daysUntilExpiry > 0) daysUntilExpiry else 0,
                                                        hours = 0,
                                                        unlimited = false,
                                                        expired = false,
                                                        expiryDate = expiryTimestamp,
                                                        totalTraffic = null,
                                                        usedTraffic = null,
                                                        remainingTraffic = null
                                                    )
                                                    Log.d("VpnApiClient", "✓ Subscription info extracted from token (ACTIVE): ${subscriptionInfo.format()}, expiry date: ${java.util.Date(expiryTimestamp)}")
                                                    return@withContext subscriptionInfo
                                                } else {
                                                    // Подписка истекла по timestamp
                                                    // Сохраняем информацию об истекшей подписке для возврата в конце, если API не сработал
                                                    expiredSubscriptionInfo = SubscriptionInfo(
                                                        days = 0,
                                                        hours = 0,
                                                        unlimited = false,
                                                        expired = true,
                                                        expiryDate = expiryTimestamp,
                                                        totalTraffic = null,
                                                        usedTraffic = null,
                                                        remainingTraffic = null
                                                    )
                                                    
                                                    // ВАЖНО: Если подписка истекла совсем недавно (менее 1 часа), 
                                                    // возможно это устаревший timestamp в URL
                                                    // НЕ возвращаем результат сразу - пусть код попробует API
                                                    // Если API не сработал, вернём истекший статус в конце
                                                    if (hoursExpired < 1) { // Менее 1 часа
                                                        Log.w("VpnApiClient", "⚠ Subscription expired very recently ($hoursExpired hours ago). Timestamp might be outdated. Will try API first.")
                                                        Log.d("VpnApiClient", "⚠ Expiry date from token: ${java.util.Date(expiryTimestamp)}")
                                                    } else {
                                                        // Истекла более часа назад - скорее всего это реально истекшая подписка
                                                        Log.d("VpnApiClient", "⚠ Subscription expired $hoursExpired hours ago. Will try API first, but likely expired.")
                                                    }
                                                }
                                            } else {
                                                Log.w("VpnApiClient", "Failed to parse valid timestamp from token part: $timestampPart")
                                            }
                                        } else {
                                            // Нет timestamp в токене - считаем безлимитной подпиской
                                            Log.d("VpnApiClient", "No timestamp found in token, treating as unlimited subscription")
                                            isUnlimited = true
                                        }
                                    } else {
                                        // Нет запятой - только username, считаем безлимитной подпиской
                                        Log.d("VpnApiClient", "No comma found in token, treating as unlimited subscription")
                                        isUnlimited = true
                                    }
                                    
                                    // Если это безлимитная подписка (обрабатываем случай когда нет timestamp)
                                    if (isUnlimited && expiryTimestamp == null) {
                                        val subscriptionInfo = SubscriptionInfo(
                                            days = 0,
                                            hours = 0,
                                            unlimited = true,
                                            expired = false,
                                            expiryDate = null,
                                            totalTraffic = null,
                                            usedTraffic = null,
                                            remainingTraffic = null
                                        )
                                        Log.d("VpnApiClient", "✓ Subscription info extracted from token (UNLIMITED): ${subscriptionInfo.format()}")
                                        return@withContext subscriptionInfo
                                    }
                                    
                                    Log.d("VpnApiClient", "Extracted username from decoded token: $username")
                                } catch (e: Exception) {
                                    // Не base64, пробуем как обычный username или UUID
                                    Log.d("VpnApiClient", "Token is not base64, skipping...")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("VpnApiClient", "Failed to extract username/token from URL", e)
                        }
                        
                        // Если дошли сюда, значит API не вернул результат
                        // Если была сохранена информация об истекшей подписке, проверяем, не является ли это ошибкой timestamp
                        if (expiredSubscriptionInfo != null) {
                            // Если конфиги успешно извлекаются, возможно timestamp в URL устарел или подписка безлимитна
                            // В этом случае показываем подписку как активную с датой из timestamp
                            // (даже если она технически в прошлом, конфиги работают - значит подписка активна)
                            val configs = try {
                                val tempConfigs = getSubscriptionConfigs(cleanUrl)
                                if (!tempConfigs.isNullOrEmpty()) {
                                    Log.d("VpnApiClient", "✓ Configs successfully extracted (${tempConfigs.size} configs). Timestamp might be outdated, treating subscription as ACTIVE.")
                                    
                                    // Проверяем, не является ли это безлимитной подпиской (expiryDate = 0 или очень маленькое значение)
                                    val expiryDateValue = expiredSubscriptionInfo.expiryDate
                                    val isUnlimited = expiryDateValue == null || 
                                                     expiryDateValue == 0L ||
                                                     expiryDateValue < 1000000000L // < 2001 год
                                    
                                    // Если конфиги успешно извлекаются, значит подписка работает
                                    // Показываем её как активную с датой из timestamp
                                    SubscriptionInfo(
                                        days = if (isUnlimited) 0 else (expiryDateValue?.let { expiryDate ->
                                            val now = System.currentTimeMillis()
                                            val daysUntil = ((expiryDate - now) / (1000 * 60 * 60 * 24)).toInt()
                                            if (daysUntil > 0) daysUntil else {
                                                // Если дата в прошлом, но конфиги работают, показываем как неограниченную или с датой из timestamp
                                                val daysSinceExpiry = ((now - expiryDate) / (1000 * 60 * 60 * 24)).toInt()
                                                if (daysSinceExpiry <= 10) {
                                                    // Истекла недавно (менее 10 дней), но конфиги работают - возможно timestamp устарел
                                                    // Показываем дату из timestamp, но не как истекшую
                                                    0 // Будем показывать только дату
                                                } else 0
                                            }
                                        } ?: 0),
                                        hours = 0,
                                        unlimited = isUnlimited,
                                        expired = false, // Не показываем как истекшую, если конфиги работают
                                        expiryDate = if (isUnlimited) 0L else expiryDateValue,
                                        totalTraffic = null,
                                        usedTraffic = null,
                                        remainingTraffic = null,
                                        configsCount = tempConfigs.size // Количество извлеченных конфигов
                                    )
                                } else null
                            } catch (e: Exception) {
                                Log.w("VpnApiClient", "Failed to extract configs for verification", e)
                                null
                            }
                            
                            if (configs != null) {
                                // Если дата в прошлом, но конфиги работают - добавляем предупреждение в лог
                                val now = System.currentTimeMillis()
                                val isDateInPast = expiredSubscriptionInfo.expiryDate != null && 
                                                  expiredSubscriptionInfo.expiryDate!! < now
                                
                                if (isDateInPast) {
                                    Log.w("VpnApiClient", "⚠ Timestamp in URL is outdated (${java.util.Date(expiredSubscriptionInfo.expiryDate!!)}), but configs are working. Subscription is ACTIVE, but expiry date may be incorrect.")
                                    Log.d("VpnApiClient", "✓ Subscription info (ACTIVE - configs working, but expiry date from URL may be outdated): ${configs.format()}")
                                } else {
                                    Log.d("VpnApiClient", "✓ Subscription info (ACTIVE - configs working): ${configs.format()}")
                                }
                                return@withContext configs
                            }
                            
                            // Если конфиги не извлекаются, возвращаем истекшую подписку
                            Log.d("VpnApiClient", "✓ Subscription info extracted from token (EXPIRED - API unavailable): ${expiredSubscriptionInfo.format()}")
                            return@withContext expiredSubscriptionInfo
                        }
                        
                        Log.w("VpnApiClient", "Could not extract subscription info from subscription URL response")
                        Log.d("VpnApiClient", "Response body preview (first 500 chars): ${body.take(500)}")
                    } else {
                        Log.w("VpnApiClient", "Subscription URL response body is null")
                    }
                } else {
                    val errorBody = resp.body?.string() ?: "No error body"
                    Log.w("VpnApiClient", "Subscription URL returned status: ${resp.code} ${resp.message}")
                    Log.d("VpnApiClient", "Error response body: $errorBody")
                    
                    // Если это 404, возможно URL неверный
                    when {
                        resp.code == 404 -> {
                            Log.w("VpnApiClient", "Subscription URL not found (404). URL might be incorrect or expired.")
                        }
                        resp.code >= 500 -> {
                            Log.w("VpnApiClient", "Server error (${resp.code}). Server might be temporarily unavailable.")
                        }
                        else -> {
                            Log.w("VpnApiClient", "Subscription URL returned error status: ${resp.code}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Failed to get subscription info from URL: ${e.message}", e)
        }
        
        null
    }
    
    /**
     * Извлечение конфигов из Subscription URL
     * @param subscriptionUrl URL подписки
     * @return Список конфигов или null если ошибка
     */
    suspend fun getSubscriptionConfigs(subscriptionUrl: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = subscriptionUrl.trim()
            
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .addHeader("Connection", "close")
                .addHeader("User-Agent", "KIZ-VPN-Client/1.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        Log.d("VpnApiClient", "Extracting configs from subscription URL response (length: ${body.length})")
                        
                        // Subscription URL может вернуть base64-encoded список конфигов
                        try {
                            // Пробуем декодировать base64
                            val decoded = android.util.Base64.decode(body.trim(), android.util.Base64.DEFAULT)
                            val decodedString = String(decoded)
                            Log.d("VpnApiClient", "Decoded base64 config list, length: ${decodedString.length}")
                            
                            // Разделяем конфиги по переносам строк
                            val configs = decodedString.split("\n", "\r\n", "\r")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && (it.startsWith("vless://") || it.startsWith("vmess://") || it.startsWith("ss://") || it.contains("PrivateKey") || it.contains("publicKey")) }
                            
                            if (configs.isNotEmpty()) {
                                Log.d("VpnApiClient", "Extracted ${configs.size} config(s) from subscription URL")
                                return@withContext configs
                            }
                        } catch (e: Exception) {
                            Log.w("VpnApiClient", "Failed to decode base64, trying as plain text", e)
                        }
                        
                        // Если не base64, пробуем как обычный текст с конфигами
                        val configs = body.split("\n", "\r\n", "\r")
                            .map { it.trim() }
                            .filter { it.isNotBlank() && (it.startsWith("vless://") || it.startsWith("vmess://") || it.startsWith("ss://") || it.contains("PrivateKey") || it.contains("publicKey")) }
                        
                        if (configs.isNotEmpty()) {
                            Log.d("VpnApiClient", "Extracted ${configs.size} config(s) from subscription URL (plain text)")
                            return@withContext configs
                        }
                        
                        Log.w("VpnApiClient", "No valid configs found in subscription URL response")
                    } else {
                        Log.w("VpnApiClient", "Subscription URL response body is null")
                    }
                } else {
                    Log.w("VpnApiClient", "Failed to get subscription configs: ${resp.code} ${resp.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VpnApiClient", "Error extracting configs from subscription URL", e)
        }
        
        return@withContext null
    }
    
    /**
     * Парсинг стандартного заголовка subscription-userinfo (формат Shadowrocket/Clash)
     * Формат: upload=0; download=29788976; total=0; expire=1766648256
     */
    private fun parseSubscriptionUserinfoHeader(headerValue: String): SubscriptionInfo? {
        try {
            Log.d("VpnApiClient", "Parsing subscription-userinfo: $headerValue")
            
            var upload: Long? = null
            var download: Long? = null
            var total: Long? = null
            var expire: Long? = null
            
            // Разбиваем по ; и парсим каждую пару ключ=значение
            val parts = headerValue.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("=")) {
                    val keyValue = trimmed.split("=", limit = 2)
                    if (keyValue.size == 2) {
                        val key = keyValue[0].trim()
                        val value = keyValue[1].trim()
                        
                        when (key.lowercase()) {
                            "upload" -> upload = value.toLongOrNull()
                            "download" -> download = value.toLongOrNull()
                            "total" -> total = value.toLongOrNull()
                            "expire" -> expire = value.toLongOrNull()
                        }
                    }
                }
            }
            
            // Если есть expire, это главная информация
            if (expire != null) {
                val expireMs = expire * 1000 // Конвертируем секунды в миллисекунды
                val now = System.currentTimeMillis()
                
                // Проверяем, является ли подписка безлимитной
                // expire = 0 или очень большое значение (> 2100 год) означает безлимит
                val isUnlimitedTime = expire == 0L || expireMs == 0L || expireMs > 4102444800000L // > 2100
                val expired = !isUnlimitedTime && expireMs < now
                
                val daysUntilExpiry = if (isUnlimitedTime) {
                    0 // Безлимит
                } else if (!expired) {
                    ((expireMs - now) / (1000 * 60 * 60 * 24)).toInt()
                } else {
                    -((now - expireMs) / (1000 * 60 * 60 * 24)).toInt()
                }
                
                val hoursUntilExpiry = if (isUnlimitedTime) {
                    0 // Безлимит
                } else if (!expired) {
                    (((expireMs - now) % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
                } else {
                    -(((now - expireMs) % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
                }
                
                // Вычисляем использованный и оставшийся трафик
                val usedTraffic = if (upload != null && download != null) {
                    upload + download
                } else {
                    download ?: upload
                }
                
                val remainingTraffic = if (total != null && total > 0 && usedTraffic != null) {
                    (total - usedTraffic).coerceAtLeast(0)
                } else {
                    null
                }
                
                // unlimited = true ТОЛЬКО если: безлимит по времени (expire = 0 или > 2100)
                // БЕЗЛИМИТ ПО ТРАФИКУ (total = 0) НЕ означает безлимит по времени!
                val unlimited = isUnlimitedTime
                
                // Если безлимит, устанавливаем expiryDate = 0 для правильного отображения
                val finalExpiryDate = if (isUnlimitedTime) 0L else expireMs
                
                Log.d("VpnApiClient", "✓ Parsed subscription-userinfo: expire=${if (isUnlimitedTime) "UNLIMITED" else java.util.Date(expireMs)}, unlimited=$unlimited, used=${usedTraffic?.let { "${it / 1024 / 1024 / 1024} GB" } ?: "unknown"}, total=${total?.let { if (it == 0L) "∞" else "${it / 1024 / 1024 / 1024} GB" } ?: "unknown"}")
                
                return SubscriptionInfo(
                    days = daysUntilExpiry.coerceAtLeast(0),
                    hours = hoursUntilExpiry.coerceAtLeast(0),
                    unlimited = unlimited,
                    expired = expired,
                    expiryDate = finalExpiryDate,
                    totalTraffic = if (total != null && total > 0) total else null,
                    usedTraffic = usedTraffic,
                    remainingTraffic = remainingTraffic
                )
            }
        } catch (e: Exception) {
            Log.w("VpnApiClient", "Failed to parse subscription-userinfo header: $headerValue", e)
        }
        return null
    }
    
    /**
     * Парсинг информации о подписке из заголовков HTTP ответа
     */
    private fun parseSubscriptionFromHeaders(headers: okhttp3.Headers): SubscriptionInfo? {
        try {
            // Парсим стандартный заголовок subscription-userinfo (формат Shadowrocket/Clash)
            // Формат: upload=0; download=29788976; total=0; expire=1766648256
            val subscriptionUserinfo = headers.get("subscription-userinfo")
            if (subscriptionUserinfo != null) {
                Log.d("VpnApiClient", "Found subscription-userinfo header: $subscriptionUserinfo")
                return parseSubscriptionUserinfoHeader(subscriptionUserinfo)
            }
            
            val subscriptionDays = headers.get("X-Subscription-Days")?.toIntOrNull() ?:
                                  headers.get("Subscription-Days")?.toIntOrNull() ?:
                                  headers.get("Sub-Days")?.toIntOrNull()
            val subscriptionHours = headers.get("X-Subscription-Hours")?.toIntOrNull() ?:
                                   headers.get("Subscription-Hours")?.toIntOrNull() ?:
                                   headers.get("Sub-Hours")?.toIntOrNull()
            val unlimited = headers.get("X-Subscription-Unlimited")?.toBoolean() ?: 
                           headers.get("Subscription-Unlimited")?.toBoolean() ?: false
            val expired = headers.get("X-Subscription-Expired")?.toBoolean() ?: 
                         headers.get("Subscription-Expired")?.toBoolean() ?: false
            val expiryDateStr = headers.get("X-Subscription-Expiry-Date") ?:
                               headers.get("Subscription-Expiry-Date") ?:
                               headers.get("Sub-Expiry-Date") ?:
                               headers.get("X-Subscription-Expires") ?:
                               headers.get("Subscription-Expires")
            val totalTraffic = headers.get("X-Subscription-Total-Traffic")?.toLongOrNull() ?:
                              headers.get("Subscription-Total-Traffic")?.toLongOrNull()
            val usedTraffic = headers.get("X-Subscription-Used-Traffic")?.toLongOrNull() ?:
                             headers.get("Subscription-Used-Traffic")?.toLongOrNull()
            val remainingTraffic = headers.get("X-Subscription-Remaining-Traffic")?.toLongOrNull() ?:
                                  headers.get("Subscription-Remaining-Traffic")?.toLongOrNull()
            
            val expiryDate = expiryDateStr?.let { dateStr ->
                try {
                    // Пробуем разные форматы дат
                    val formats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd",
                        "dd.MM.yyyy HH:mm:ss",
                        "dd.MM.yyyy"
                    )
                    
                    for (format in formats) {
                        try {
                            val dateFormat = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
                            val parsed = dateFormat.parse(dateStr)
                            if (parsed != null) {
                                Log.d("VpnApiClient", "Parsed expiry date from header: $dateStr -> ${java.util.Date(parsed.time)}")
                                return@let parsed.time
                            }
                        } catch (e: Exception) {
                            // Пробуем следующий формат
                        }
                    }
                    
                    // Если это timestamp (число)
                    dateStr.toLongOrNull()?.let { ts ->
                        val ms = if (ts < 946684800000L) ts * 1000 else ts // Конвертируем секунды в миллисекунды если нужно
                        Log.d("VpnApiClient", "Parsed expiry date as timestamp from header: $dateStr -> ${java.util.Date(ms)}")
                        return@let ms
                    }
                    
                    null
                } catch (e: Exception) {
                    Log.w("VpnApiClient", "Failed to parse expiry date from header: $dateStr", e)
                    null
                }
            }
            
            if (subscriptionDays != null || subscriptionHours != null || unlimited) {
                return SubscriptionInfo(
                    days = subscriptionDays ?: 0,
                    hours = subscriptionHours ?: 0,
                    unlimited = unlimited,
                    expired = expired,
                    expiryDate = expiryDate,
                    totalTraffic = totalTraffic,
                    usedTraffic = usedTraffic,
                    remainingTraffic = remainingTraffic
                )
            }
        } catch (e: Exception) {
            Log.w("VpnApiClient", "Failed to parse subscription from headers", e)
        }
        return null
    }
    
    /**
     * Парсинг информации о подписке из JSON
     */
    private fun parseSubscriptionFromJson(json: JSONObject): SubscriptionInfo? {
        try {
            val unlimited = json.optBoolean("unlimited", false)
            val expired = json.optBoolean("expired", false)
            val days = json.optInt("days", -1).takeIf { it >= 0 } ?: json.optInt("remaining_days", -1).takeIf { it >= 0 } ?: 0
            val hours = json.optInt("hours", 0)
            
            // Парсим дату окончания
            val expiryDate = json.optString("expiry_date", "").takeIf { it.isNotBlank() }?.let { dateStr ->
                try {
                    val formats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd"
                    )
                    for (format in formats) {
                        try {
                            val dateFormat = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
                            return@let dateFormat.parse(dateStr)?.time
                        } catch (e: Exception) {
                            // Пробуем следующий формат
                        }
                    }
                    null
                } catch (e: Exception) {
                    null
                }
            }
            
            // Парсим трафик
            val totalTraffic = json.optLong("total_traffic", -1).takeIf { it >= 0 }
                ?: json.optLong("traffic_limit", -1).takeIf { it >= 0 }
            
            val usedTraffic = json.optLong("used_traffic", -1).takeIf { it >= 0 }
                ?: json.optLong("traffic_used", -1).takeIf { it >= 0 }
            
            val remainingTraffic = json.optLong("remaining_traffic", -1).takeIf { it >= 0 }
                ?: (totalTraffic?.let { total -> usedTraffic?.let { used -> total - used } })
            
            return SubscriptionInfo(
                days = days,
                hours = hours,
                unlimited = unlimited,
                expired = expired,
                expiryDate = expiryDate,
                totalTraffic = totalTraffic,
                usedTraffic = usedTraffic,
                remainingTraffic = remainingTraffic
            )
        } catch (e: Exception) {
            Log.w("VpnApiClient", "Failed to parse subscription from JSON", e)
        }
        return null
    }
    
    /**
     * Парсинг информации о подписке из списка конфигов
     * Ищет информацию в комментариях конфигов или вычисляет из даты окончания
     */
    private fun parseSubscriptionFromConfigList(configList: String): SubscriptionInfo? {
        try {
            // Парсим каждый конфиг из списка (обычно разделены переносами строк или специальными символами)
            val configs = configList.split("\n\n", "\n---\n", "\r\n\r\n")
            
            var foundDays: Int? = null
            var foundHours: Int = 0
            var foundExpiryDate: Long? = null
            var foundTraffic: Long? = null
            var foundUsedTraffic: Long? = null
            
            for (config in configs) {
                if (config.isBlank()) continue
                
                // Пробуем найти информацию в конфиге
                val daysFromConfig = parseSubscriptionFromConfig(config)
                if (daysFromConfig != null && foundDays == null) {
                    foundDays = daysFromConfig
                }
                
                // Ищем дату окончания
                val expiryPatterns = listOf(
                    Regex("""expire[:\s=]+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE),
                    Regex("""expiry[:\s=]+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE),
                    Regex("""expire[:\s=]+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
                )
                
                for (pattern in expiryPatterns) {
                    val match = pattern.find(config)
                    if (match != null) {
                        val dateStr = match.groupValues[1]
                        try {
                            val dateFormat = java.text.SimpleDateFormat(
                                if (dateStr.contains(" ")) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd",
                                java.util.Locale.getDefault()
                            )
                            val date = dateFormat.parse(dateStr)
                            if (date != null) {
                                foundExpiryDate = date.time
                                
                                // Вычисляем дни до окончания
                                val now = System.currentTimeMillis()
                                val days = ((date.time - now) / (1000 * 60 * 60 * 24)).toInt()
                                if (days > 0 && foundDays == null) {
                                    foundDays = days
                                }
                            }
                        } catch (e: Exception) {
                            // Игнорируем ошибки парсинга даты
                        }
                    }
                }
                
                // Ищем информацию о трафике
                val trafficPatterns = listOf(
                    Regex("""traffic[:\s=]+(\d+)\s*(GB|MB|KB|TB)""", RegexOption.IGNORE_CASE),
                    Regex("""total[:\s=]+(\d+)\s*(GB|MB|KB|TB)""", RegexOption.IGNORE_CASE)
                )
                
                for (pattern in trafficPatterns) {
                    val match = pattern.find(config)
                    if (match != null) {
                        val value = match.groupValues[1].toLongOrNull()
                        val unit = match.groupValues[2].uppercase()
                        if (value != null) {
                            val bytes = when (unit) {
                                "TB" -> value * 1024L * 1024L * 1024L * 1024L
                                "GB" -> value * 1024L * 1024L * 1024L
                                "MB" -> value * 1024L * 1024L
                                "KB" -> value * 1024L
                                else -> value
                            }
                            if (foundTraffic == null) {
                                foundTraffic = bytes
                            }
                        }
                    }
                }
            }
            
            if (foundDays != null || foundExpiryDate != null) {
                return SubscriptionInfo(
                    days = foundDays ?: 0,
                    hours = foundHours,
                    unlimited = false,
                    expired = foundExpiryDate?.let { it < System.currentTimeMillis() } ?: false,
                    expiryDate = foundExpiryDate,
                    totalTraffic = foundTraffic,
                    usedTraffic = foundUsedTraffic,
                    remainingTraffic = foundTraffic?.let { total -> foundUsedTraffic?.let { used -> total - used } }
                )
            }
        } catch (e: Exception) {
            Log.w("VpnApiClient", "Failed to parse subscription from config list", e)
        }
        return null
    }
    
    // extractUuidFromConfig удален - больше не используется, так как не делаем API запросы
    // Все методы с API вызовами к серверам удалены - теперь работаем только с парсингом Subscription URL
}

