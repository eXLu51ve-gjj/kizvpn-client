package com.kizvpn.client.util

import android.util.Log

/**
 * Единая система логирования для всего приложения
 * Использует теги и уровни для лучшей организации логов
 */
object Logger {
    private const val DEFAULT_TAG = "KizVpnClient"
    
    // Теги для разных модулей
    object Tag {
        const val MAIN = "MainActivity"
        const val VPN_SERVICE = "KizVpnService"
        const val API = "VpnApiClient"
        const val CONFIG = "ConfigParser"
        const val UI = "UI"
        const val SUBSCRIPTION = "Subscription"
        const val NETWORK = "Network"
    }
    
    /**
     * Уровень логирования (DEBUG в development, WARN в production)
     */
    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    private var minLevel = Level.DEBUG
    
    /**
     * Установить минимальный уровень логирования
     */
    fun setMinLevel(level: Level) {
        minLevel = level
    }
    
    // === VPN Connection ===
    fun vpnConnecting(tag: String = Tag.MAIN, message: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "🔌 VPN: $message")
        }
    }
    
    fun vpnConnected(tag: String = Tag.MAIN, message: String = "Connected successfully") {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "✅ VPN: $message")
        }
    }
    
    fun vpnDisconnecting(tag: String = Tag.MAIN, message: String = "Disconnecting...") {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "🔄 VPN: $message")
        }
    }
    
    fun vpnDisconnected(tag: String = Tag.MAIN, message: String = "Disconnected") {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "❌ VPN: $message")
        }
    }
    
    fun vpnError(tag: String = Tag.MAIN, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, "❌ VPN Error: $message", throwable)
        }
    }
    
    // === Subscription ===
    fun subscriptionInfo(tag: String = Tag.SUBSCRIPTION, message: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "📋 Subscription: $message")
        }
    }
    
    fun subscriptionError(tag: String = Tag.SUBSCRIPTION, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, "❌ Subscription Error: $message", throwable)
        }
    }
    
    // === API ===
    fun apiRequest(tag: String = Tag.API, message: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, "🌐 API Request: $message")
        }
    }
    
    fun apiResponse(tag: String = Tag.API, message: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, "📥 API Response: $message")
        }
    }
    
    fun apiError(tag: String = Tag.API, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, "❌ API Error: $message", throwable)
        }
    }
    
    // === Config ===
    fun configParsed(tag: String = Tag.CONFIG, message: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, "⚙️ Config: $message")
        }
    }
    
    fun configError(tag: String = Tag.CONFIG, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, "❌ Config Error: $message", throwable)
        }
    }
    
    // === Key Activation ===
    fun keyActivating(tag: String = Tag.MAIN, message: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "🔑 Activating key: $message")
        }
    }
    
    fun keyActivated(tag: String = Tag.MAIN, message: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, "✅ Key activated: $message")
        }
    }
    
    fun keyActivationError(tag: String = Tag.MAIN, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, "❌ Key activation error: $message", throwable)
        }
    }
    
    // === General ===
    fun debug(tag: String = DEFAULT_TAG, message: String) {
        if (shouldLog(Level.DEBUG)) {
            Log.d(tag, message)
        }
    }
    
    fun info(tag: String = DEFAULT_TAG, message: String) {
        if (shouldLog(Level.INFO)) {
            Log.i(tag, message)
        }
    }
    
    fun warn(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.WARN)) {
            Log.w(tag, message, throwable)
        }
    }
    
    fun error(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (shouldLog(Level.ERROR)) {
            Log.e(tag, message, throwable)
        }
    }
    
    private fun shouldLog(level: Level): Boolean {
        return level.ordinal >= minLevel.ordinal
    }
}

