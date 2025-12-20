package com.kizvpn.client.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

object PingUtil {
    private const val TAG = "PingUtil"
    private const val PING_TIMEOUT_MS = 3000 // 3 секунды таймаут
    
    /**
     * Проверяет пинг до сервера (TCP соединение)
     * @param host IP адрес или домен
     * @param port Порт для проверки
     * @return Пинг в миллисекундах, или null если не удалось подключиться
     */
    suspend fun pingServer(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting ping to $host:$port with timeout ${PING_TIMEOUT_MS}ms")
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            
            // Добавляем дополнительную проверку хоста
            if (host.isBlank()) {
                Log.w(TAG, "Host is blank")
                return@withContext null
            }
            
            if (port <= 0 || port > 65535) {
                Log.w(TAG, "Invalid port: $port")
                return@withContext null
            }
            
            socket.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS)
            socket.close()
            
            val ping = (System.currentTimeMillis() - startTime).toInt()
            Log.d(TAG, "Ping to $host:$port = ${ping}ms")
            ping
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Ping timeout to $host:$port after ${PING_TIMEOUT_MS}ms")
            null
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Unknown host: $host")
            null
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "Connection refused to $host:$port")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Ping failed to $host:$port: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
    
    /**
     * Форматирует пинг для отображения
     */
    fun formatPing(ping: Int?): String {
        return when {
            ping == null -> "N/A"
            ping < 1000 -> "$ping ms"
            else -> "${ping / 1000}s"
        }
    }
}




























