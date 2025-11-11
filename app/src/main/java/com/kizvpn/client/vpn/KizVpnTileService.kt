package com.kizvpn.client.vpn

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.kizvpn.client.R

/**
 * Quick Settings Tile для быстрого включения/выключения VPN
 */
class KizVpnTileService : TileService() {
    
    private val TAG = "KizVpnTileService"
    private var vpnService: KizVpnService? = null
    private var isBound = false
    
    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received VPN state broadcast")
            updateTileState()
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as? KizVpnService.KizVpnBinder
            vpnService = binder?.getService()
            isBound = true
            updateTileState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            vpnService = null
            isBound = false
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile started listening")
        
        // Регистрируем BroadcastReceiver для обновления плитки
        val filter = IntentFilter("com.kizvpn.client.VPN_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnStateReceiver, filter)
        }
        
        // Пытаемся подключиться к сервису
        try {
            val intent = Intent(this, KizVpnService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error binding to service", e)
        }
        
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile stopped listening")
        
        // Разрегистрируем BroadcastReceiver
        try {
            unregisterReceiver(vpnStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        if (isBound) {
            try {
                unbindService(serviceConnection)
                isBound = false
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
        }
    }
    
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked")
        
        // Проверяем статус VPN
        val isVpnActive = isVpnActive()
        Log.d(TAG, "Current VPN status: ${if (isVpnActive) "Active" else "Inactive"}")
        
        if (isVpnActive) {
            // VPN активен — отключаем
            disconnectVpn()
        } else {
            // VPN неактивен — включаем
            connectVpn()
        }
        
        // Обновляем состояние плитки с небольшой задержкой
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTileState()
        }, 500)
    }
    
    /**
     * Проверка активности VPN
     */
    private fun isVpnActive(): Boolean {
        // Проверяем через SharedPreferences
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        
        Log.d(TAG, "isVpnActive from prefs: $isConnected")
        return isConnected
    }
    
    /**
     * Подключение VPN
     */
    private fun connectVpn() {
        Log.d(TAG, "Attempting to connect VPN from tile")
        
        try {
            // Получаем сохранённый конфиг
            val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
            val activeConfigType = prefs.getString("active_config_type", null)
            val config = when (activeConfigType) {
                "vless" -> prefs.getString("saved_config", null)
                "wireguard" -> prefs.getString("saved_wireguard_config", null)
                else -> null
            }
            
            if (config.isNullOrBlank()) {
                Log.w(TAG, "No saved config found, opening app")
                // Открываем приложение, если нет конфига
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
            
            // Запускаем VPN сервис с правильным action
            val intent = Intent(this, KizVpnService::class.java).apply {
                action = "com.kizvpn.client.START"
                putExtra("config", config)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Log.d(TAG, "VPN connection initiated from tile with config: ${config.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting VPN from tile", e)
        }
    }
    
    /**
     * Отключение VPN
     */
    private fun disconnectVpn() {
        Log.d(TAG, "Attempting to disconnect VPN from tile")
        
        try {
            val intent = Intent(this, KizVpnService::class.java).apply {
                action = "com.kizvpn.client.STOP"
            }
            startService(intent)
            
            Log.d(TAG, "VPN disconnection initiated from tile")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting VPN from tile", e)
        }
    }
    
    /**
     * Обновление состояния плитки
     */
    private fun updateTileState() {
        val tile = qsTile ?: return
        
        val isActive = isVpnActive()
        Log.d(TAG, "Updating tile state: ${if (isActive) "Active" else "Inactive"}")
        
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "KIZ VPN"
        tile.contentDescription = if (isActive) "VPN подключен" else "VPN отключен"
        
        // Устанавливаем иконку
        tile.icon = Icon.createWithResource(this, R.drawable.kiz_vpn_mono)
        
        tile.updateTile()
    }
}

