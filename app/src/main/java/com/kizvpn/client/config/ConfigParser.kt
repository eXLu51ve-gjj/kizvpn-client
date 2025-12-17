package com.kizvpn.client.config

import android.util.Base64
import com.google.gson.Gson

/**
 * Парсер конфигов VPN (VLESS, WireGuard)
 */
class ConfigParser {
    
    data class ParsedConfig(
        val protocol: Protocol,
        val server: String,
        val port: Int,
        val uuid: String? = null,
        val privateKey: String? = null,
        val publicKey: String? = null,
        val preSharedKey: String? = null, // Для WireGuard: PreSharedKey из [Peer]
        val dns: String? = null,
        val allowedIPs: String? = null, // Для WireGuard: маршруты из [Peer]
        val address: String? = null, // Для WireGuard: локальный адрес из [Interface]
        val endpoint: String? = null,
        val rawConfig: String,
        val name: String? = null, // Имя конфига из фрагмента после #
        val comment: String? = null, // Примечание/комментарий из конфига (для WireGuard)
        // VLESS stream settings
        val network: String? = null, // tcp, ws, grpc, etc.
        val security: String? = null, // none, tls, reality
        val path: String? = null,
        val host: String? = null,
        val sni: String? = null,
        val alpn: String? = null,
        val allowInsecure: Boolean = false,
        // Reality settings
        val pbk: String? = null, // public key
        val fp: String? = null, // fingerprint
        val sid: String? = null, // short ID
        val spx: String? = null, // spiderX
        // VLESS flow
        val flow: String? = null // xtls-rprx-vision, etc.
    )
    
    enum class Protocol {
        VLESS, WIREGUARD, VMESS, UNKNOWN
    }
    
    /**
     * Парсит конфиг и определяет его тип
     */
    fun parseConfig(config: String): ParsedConfig? {
        val trimmedConfig = config.trim()
        
        return when {
            trimmedConfig.startsWith("vless://") -> parseVless(trimmedConfig)
            trimmedConfig.startsWith("wireguard://") -> parseWireGuard(trimmedConfig)
            trimmedConfig.startsWith("vmess://") -> parseVmess(trimmedConfig)
            trimmedConfig.startsWith("[Interface]") -> parseWireGuardNative(trimmedConfig)
            else -> null
        }
    }
    
    /**
     * Парсит VLESS конфиг
     * Формат: vless://uuid@server:port?type=ws&security=tls&path=/path&host=example.com#name
     */
    private fun parseVless(config: String): ParsedConfig? {
        try {
            val withoutProtocol = config.removePrefix("vless://")
            val parts = withoutProtocol.split("#")
            val mainPart = parts[0]
            val name = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else null
            
            val uuidAndServer = mainPart.split("@")
            if (uuidAndServer.size != 2) return null
            
            val uuid = uuidAndServer[0]
            val serverAndParams = uuidAndServer[1].split("?")
            val serverPort = serverAndParams[0].split(":")
            
            if (serverPort.size != 2) return null
            
            val server = serverPort[0]
            val port = serverPort[1].toIntOrNull() ?: return null
            
            // Парсим параметры
            var network: String? = "tcp" // По умолчанию
            var security: String? = "tls" // По умолчанию
            var path: String? = null
            var host: String? = null
            var sni: String? = null
            var alpn: String? = null
            var allowInsecure = false
            var pbk: String? = null // Reality public key
            var fp: String? = null // Reality fingerprint
            var sid: String? = null // Reality short ID
            var spx: String? = null // Reality spiderX
            var flow: String? = null // VLESS flow
            
            if (serverAndParams.size > 1) {
                val paramsString = serverAndParams[1]
                val params = paramsString.split("&")
                
                for (param in params) {
                    if (param.isBlank()) continue
                    
                    val keyValue = param.split("=", limit = 2)
                    if (keyValue.size >= 1) {
                        val key = keyValue[0]
                        // Значение может отсутствовать (пустой параметр) или быть закодированным
                        val value = if (keyValue.size >= 2 && keyValue[1].isNotBlank()) {
                            try {
                                java.net.URLDecoder.decode(keyValue[1], "UTF-8")
                            } catch (e: Exception) {
                                keyValue[1] // Если декодирование не удалось, используем как есть
                            }
                        } else {
                            "" // Пустое значение
                        }
                        
                        when (key.lowercase()) {
                            "type" -> network = value
                            "security" -> security = value
                            "path" -> path = value
                            "host" -> host = value
                            "sni" -> sni = value
                            "alpn" -> alpn = value
                            "allowinsecure", "allowInsecure" -> allowInsecure = value.lowercase() == "true" || value == "1"
                            "pbk" -> pbk = value
                            "fp" -> fp = value
                            "sid" -> sid = value
                            "spx" -> spx = value
                            "flow" -> flow = value
                        }
                    }
                }
            }
            
            return ParsedConfig(
                protocol = Protocol.VLESS,
                server = server,
                port = port,
                uuid = uuid,
                rawConfig = config,
                name = name,
                network = network,
                security = security,
                path = path,
                host = host,
                sni = sni,
                alpn = alpn,
                allowInsecure = allowInsecure,
                pbk = pbk,
                fp = fp,
                sid = sid,
                spx = spx,
                flow = flow
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Парсит WireGuard конфиг из URL
     * Формат: wireguard://base64?name
     */
    private fun parseWireGuard(config: String): ParsedConfig? {
        try {
            val withoutProtocol = config.removePrefix("wireguard://")
            val parts = withoutProtocol.split("?")
            val base64Config = parts[0]
            
            val decoded = String(Base64.decode(base64Config, Base64.URL_SAFE))
            return parseWireGuardNative(decoded)
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Парсит нативный WireGuard конфиг
     */
    private fun parseWireGuardNative(config: String): ParsedConfig? {
        try {
            var privateKey: String? = null
            var publicKey: String? = null
            var dns: String? = null
            var address: String? = null // Address из [Interface] секции
            var allowedIPs: String? = null // AllowedIPs из [Peer] секции
            var endpoint: String? = null
            var preSharedKey: String? = null
            var server: String? = null
            var port: Int? = null
            var comment: String? = null // Примечание/комментарий
            
            val lines = config.lines()
            var inInterface = true
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                // Функция для проверки, является ли текст валидным комментарием
                fun isValidComment(text: String?): Boolean {
                    if (text.isNullOrBlank()) return false
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return false
                    // Игнорируем -1, числа, IP адреса
                    if (trimmed == "-1") return false
                    if (trimmed.matches(Regex("^-?\\d+$"))) return false // Числа (включая отрицательные)
                    if (trimmed.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) return false // IP адреса
                    return true
                }
                
                // Извлекаем комментарии/примечания (строки, начинающиеся с #)
                // Но игнорируем строки, которые являются частью параметров (например, DNS = 1.1.1.1)
                if (trimmed.startsWith("#")) {
                    val commentText = trimmed.substring(1).trim()
                    // Игнорируем комментарии, которые выглядят как числа (включая -1), IP адреса или пустые
                    // (это может быть часть параметра, а не реальный комментарий)
                    if (isValidComment(commentText)) {
                        // Для WireGuard конфигов берем первый валидный комментарий как основной идентификатор
                        // Обычно это имя клиента (например, "fsaad-1")
                        if (comment == null) {
                            comment = commentText
                        } else {
                            // Если комментарий уже есть, добавляем новый через пробел
                            // Но для подписки обычно используется первый комментарий
                            comment = "$comment $commentText"
                        }
                    }
                    continue
                }
                
                // Также проверяем комментарии в конце строк (например, "PrivateKey = xxx # comment")
                // Но только если это не часть значения параметра (например, не "DNS = 1.1.1.1")
                var lineToProcess = trimmed
                val inlineCommentIndex = trimmed.indexOf("#")
                if (inlineCommentIndex > 0) {
                    // Проверяем, что # не является частью значения параметра
                    val beforeHash = trimmed.substring(0, inlineCommentIndex).trim()
                    val afterHash = trimmed.substring(inlineCommentIndex + 1).trim()
                    
                    // Если перед # есть = и значение выглядит как параметр, это не комментарий
                    if (beforeHash.contains("=")) {
                        val value = beforeHash.substringAfter("=").trim()
                        // Если значение не пустое и не выглядит как число/IP, это может быть комментарий
                        if (value.isNotEmpty() && !value.matches(Regex("^\\d+$")) && !value.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                            // Используем только часть до # для парсинга параметра
                            lineToProcess = beforeHash
                            // Извлекаем комментарий только если он валидный
                            if (isValidComment(afterHash)) {
                                comment = if (comment == null) afterHash else "$comment $afterHash"
                            }
                        }
                    } else {
                        // Если нет =, это просто комментарий в начале строки, уже обработан выше
                    }
                }
                
                if (lineToProcess == "[Peer]") {
                    inInterface = false
                    continue
                }
                
                if (inInterface) {
                    when {
                        lineToProcess.startsWith("PrivateKey", ignoreCase = true) -> {
                            privateKey = lineToProcess.substringAfter("=", "").trim()
                        }
                        lineToProcess.startsWith("DNS", ignoreCase = true) -> {
                            dns = lineToProcess.substringAfter("=", "").trim()
                        }
                        lineToProcess.startsWith("Address", ignoreCase = true) -> {
                            address = lineToProcess.substringAfter("=", "").trim()
                        }
                    }
                } else {
                    when {
                        lineToProcess.startsWith("PublicKey", ignoreCase = true) -> {
                            publicKey = lineToProcess.substringAfter("=", "").trim()
                        }
                        lineToProcess.startsWith("Endpoint", ignoreCase = true) -> {
                            endpoint = lineToProcess.substringAfter("=", "").trim()
                            val endpointParts = endpoint.split(":")
                            if (endpointParts.size >= 2) {
                                server = endpointParts[0]
                                port = endpointParts[1].toIntOrNull()
                            }
                        }
                        lineToProcess.startsWith("AllowedIPs", ignoreCase = true) -> {
                            allowedIPs = lineToProcess.substringAfter("=", "").trim()
                        }
                        lineToProcess.startsWith("PresharedKey", ignoreCase = true) || 
                        lineToProcess.startsWith("PreSharedKey", ignoreCase = true) -> {
                            preSharedKey = lineToProcess.substringAfter("=", "").trim()
                        }
                    }
                }
            }
            
            if (server == null || port == null || privateKey == null) {
                return null
            }
            
            // Если address не найден, но есть allowedIPs, используем первый адрес из allowedIPs
            // Но обычно address и allowedIPs - это разные вещи:
            // address - локальный адрес клиента (например, 10.0.0.2/32)
            // allowedIPs - маршруты (например, 0.0.0.0/0)
            // Для парсинга используем address как локальный адрес
            
            return ParsedConfig(
                protocol = Protocol.WIREGUARD,
                server = server,
                port = port,
                privateKey = privateKey,
                publicKey = publicKey,
                preSharedKey = preSharedKey,
                dns = dns,
                allowedIPs = allowedIPs ?: "0.0.0.0/0,::/0", // Маршруты из [Peer]
                address = address, // Локальный адрес из [Interface]
                endpoint = endpoint,
                rawConfig = config,
                comment = comment // Примечание/комментарий из конфига
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Парсит VMess конфиг
     */
    private fun parseVmess(config: String): ParsedConfig? {
        try {
            val withoutProtocol = config.removePrefix("vmess://")
            val decoded = String(Base64.decode(withoutProtocol, Base64.DEFAULT))
            
            val gson = Gson()
            val vmessConfig = gson.fromJson(decoded, VmessConfig::class.java)
            
            return ParsedConfig(
                protocol = Protocol.VMESS,
                server = vmessConfig.add ?: "",
                port = vmessConfig.port ?: 443,
                uuid = vmessConfig.id,
                rawConfig = config
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private data class VmessConfig(
        val add: String?,
        val port: Int?,
        val id: String?,
        val aid: String?,
        val net: String?,
        val type: String?,
        val tls: String?
    )
}

