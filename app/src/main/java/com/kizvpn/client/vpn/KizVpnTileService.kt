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
import androidx.core.app.NotificationCompat

/**
 * Quick Settings Tile для быстрого включения/выключения VPN
 */
class KizVpnTileService : TileService() {
    
    private val TAG = "KizVpnTileService"
    private var vpnService: KizVpnService? = null
    private var isBound = false
    
    /**
     * Проверяет, активен ли именно наш VPN (KIZ VPN)
     */
    private val isOurVpnActive: Boolean
        get() {
            val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("is_connected", false)
        }
    
    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.kizvpn.client.VPN_STATE_CHANGED" -> {
                    Log.d(TAG, "Received VPN state broadcast")
                    updateTileState()
                }
                "com.kizvpn.client.VPN_CONFLICT" -> {
                    Log.d(TAG, "Received VPN conflict broadcast")
                    showVpnConflictNotification()
                }
            }
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
        
        // Регистрируем BroadcastReceiver для обновления плитки и обработки конфликтов VPN
        val filter = IntentFilter().apply {
            addAction("com.kizvpn.client.VPN_STATE_CHANGED")
            addAction("com.kizvpn.client.VPN_CONFLICT")
        }
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
            // Проверяем разрешение VPN
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                Log.w(TAG, "VPN permission not granted, opening app")
                // Открываем приложение для получения разрешения VPN
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
            
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
            
            // Проверяем, не активен ли уже другой VPN
            // Если prepare() возвращает null, но наш VPN не подключен, 
            // мы все равно попытаемся подключиться - KizVpnService обработает конфликт
            if (!isOurVpnActive) {
                Log.d(TAG, "Our VPN is not active, attempting to connect")
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
            showErrorNotification("Ошибка подключения VPN: ${e.message}")
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
    
    /**
     * Показать уведомление о конфликте с другим VPN
     */
    private fun showVpnConflictNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Создаем канал уведомлений если нужно
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "kiz_vpn_alerts",
                "KIZ VPN Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        // Создаем intent для открытия приложения
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(this, "kiz_vpn_alerts")
            .setSmallIcon(R.drawable.kiz_vpn_mono)
            .setContentTitle("KIZ VPN")
            .setContentText("Другой VPN активен. Отключите его и попробуйте снова.")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText("Обнаружен активный VPN-клиент. Для подключения KIZ VPN необходимо сначала отключить другой VPN в настройках системы."))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(1001, notification)
        Log.d(TAG, "VPN conflict notification shown")
    }
    
    /**
     * Показать уведомление об ошибке
     */
    private fun showErrorNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Создаем канал уведомлений если нужно
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "kiz_vpn_alerts",
                "KIZ VPN Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        // Создаем intent для открытия приложения
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(this, "kiz_vpn_alerts")
            .setSmallIcon(R.drawable.kiz_vpn_mono)
            .setContentTitle("KIZ VPN - Ошибка")
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(1002, notification)
        Log.d(TAG, "Error notification shown: $message")
    }
}

