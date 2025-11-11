package com.kizvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ConnectionHistoryEntry(
    val timestamp: Long,
    val action: String, // "connected" или "disconnected"
    val server: String? = null,
    val duration: Long? = null // Для отключения - длительность подключения в миллисекундах
)

class ConnectionHistoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
    private val key = "connection_history"
    private val maxEntries = 100 // Максимум записей в истории
    
    /**
     * Добавить запись в историю
     */
    fun addEntry(entry: ConnectionHistoryEntry) {
        try {
            val history = getHistory().toMutableList()
            history.add(0, entry) // Добавляем в начало
            
            // Ограничиваем количество записей
            if (history.size > maxEntries) {
                history.removeAt(history.size - 1)
            }
            
            // Сохраняем обратно
            val jsonArray = JSONArray()
            history.forEach { item ->
                val jsonObject = JSONObject().apply {
                    put("timestamp", item.timestamp)
                    put("action", item.action)
                    if (item.server != null) put("server", item.server)
                    if (item.duration != null) put("duration", item.duration)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString(key, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Получить всю историю
     */
    fun getHistory(): List<ConnectionHistoryEntry> {
        return try {
            val jsonString = prefs.getString(key, null) ?: return emptyList()
            val jsonArray = JSONArray(jsonString)
            val history = mutableListOf<ConnectionHistoryEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                history.add(
                    ConnectionHistoryEntry(
                        timestamp = jsonObject.getLong("timestamp"),
                        action = jsonObject.getString("action"),
                        server = jsonObject.optString("server").takeIf { !it.isNullOrEmpty() },
                        duration = if (jsonObject.has("duration")) jsonObject.getLong("duration") else null
                    )
                )
            }
            
            history
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Очистить историю
     */
    fun clearHistory() {
        prefs.edit().remove(key).apply()
    }
    
    /**
     * Получить последнее подключение
     */
    fun getLastConnection(): ConnectionHistoryEntry? {
        return getHistory().firstOrNull { it.action == "connected" }
    }
}

