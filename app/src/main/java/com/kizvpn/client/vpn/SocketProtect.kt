package com.kizvpn.client.vpn

/**
 * Интерфейс для защиты сокетов от VPN
 * Импортирован из XiVPN
 */
interface SocketProtect {
    fun protectFd(fd: Int)
}

