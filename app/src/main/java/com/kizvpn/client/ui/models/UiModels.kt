package com.kizvpn.client.ui.models

data class Server(
    val id: String,
    val country: String,
    val city: String? = null,
    val pingMs: Int,
    val loadPercent: Int,
    val flagEmoji: String = "🌍",
    val isSelected: Boolean = false
)

data class ConnectionStatus(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val server: Server? = null,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val latency: Int = 0,
    val connectedAt: Long? = null // Время подключения (timestamp)
)

