package com.kizvpn.client.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.kizvpn.client.config.ConfigParser
import com.kizvpn.client.ui.models.ConnectionStatus
import com.kizvpn.client.ui.models.Server
import com.kizvpn.client.vpn.KizVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs: SharedPreferences = application.getSharedPreferences("KizVpnPrefs", android.content.Context.MODE_PRIVATE)
    
    // Состояние VPN
    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // Конфиг VPN
    private val _vpnConfig = MutableStateFlow<String?>(null)
    val vpnConfig: StateFlow<String?> = _vpnConfig.asStateFlow()
    
    // Тема (светлая/темная)
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    // Данные для графика трафика (последние 60 точек)
    private val _trafficData = MutableStateFlow<List<Float>>(List(60) { 0f })
    val trafficData: StateFlow<List<Float>> = _trafficData.asStateFlow()
    
    // Текущая скорость трафика (для графика)
    private var currentTrafficSpeed = 0f
    
    // Текущая скорость для статистики (KB/s)
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    // Данные для нового графика (YCharts)
    private val _downloadChartData = MutableStateFlow<List<co.yml.charts.common.model.Point>>(emptyList())
    val downloadChartData: StateFlow<List<co.yml.charts.common.model.Point>> = _downloadChartData.asStateFlow()
    
    private val _uploadChartData = MutableStateFlow<List<co.yml.charts.common.model.Point>>(emptyList())
    val uploadChartData: StateFlow<List<co.yml.charts.common.model.Point>> = _uploadChartData.asStateFlow()
    
    private val _currentDownloadSpeed = MutableStateFlow(0f)
    val currentDownloadSpeed: StateFlow<Float> = _currentDownloadSpeed.asStateFlow()
    
    private val _currentUploadSpeed = MutableStateFlow(0f)
    val currentUploadSpeed: StateFlow<Float> = _currentUploadSpeed.asStateFlow()
    
    private var chartTimeCounter = 0f
    
    // Время начала подключения
    private var connectionStartTime: Long? = null
    
    // Информация о подписке
    private val _subscriptionInfo = MutableStateFlow<com.kizvpn.client.data.SubscriptionInfo?>(null)
    val subscriptionInfo: StateFlow<com.kizvpn.client.data.SubscriptionInfo?> = _subscriptionInfo.asStateFlow()
    
    init {
        // Загружаем сохраненный конфиг
        loadSavedConfig()
        
        // Инициализируем график с начальными данными (для демонстрации)
        initializeTrafficGraph()
        initializeNetworkChart()
        
        // Начинаем обновление данных графика
        startTrafficGraphUpdate()
        startNetworkChartUpdate()
    }
    
    /**
     * Инициализация данных для нового графика
     */
    private fun initializeNetworkChart() {
        val initialDownload = List(10) { i ->
            co.yml.charts.common.model.Point(i.toFloat(), (i * 100).toFloat())
        }
        val initialUpload = List(10) { i ->
            co.yml.charts.common.model.Point(i.toFloat(), (i * 50).toFloat())
        }
        _downloadChartData.value = initialDownload
        _uploadChartData.value = initialUpload
        chartTimeCounter = 10f
    }
    
    /**
     * Обновление данных графика в реальном времени
     */
    private fun startNetworkChartUpdate() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Обновление каждую секунду
                
                if (_connectionStatus.value.isConnected) {
                    // Генерация реалистичных данных сети
                    val downloadSpeed = (100 + Math.random() * 900).toFloat()
                    val uploadSpeed = (50 + Math.random() * 300).toFloat()
                    
                    _currentDownloadSpeed.value = downloadSpeed
                    _currentUploadSpeed.value = uploadSpeed
                    
                    // Добавление новых точек
                    val newDownloadData = _downloadChartData.value.toMutableList()
                    val newUploadData = _uploadChartData.value.toMutableList()
                    
                    newDownloadData.add(co.yml.charts.common.model.Point(chartTimeCounter, downloadSpeed))
                    newUploadData.add(co.yml.charts.common.model.Point(chartTimeCounter, uploadSpeed))
                    
                    // Удаление старых точек (сохраняем последние 15)
                    if (newDownloadData.size > 15) {
                        newDownloadData.removeAt(0)
                        newUploadData.removeAt(0)
                    }
                    
                    _downloadChartData.value = newDownloadData
                    _uploadChartData.value = newUploadData
                    
                    chartTimeCounter++
                } else {
                    // Когда не подключено, сбрасываем данные
                    _currentDownloadSpeed.value = 0f
                    _currentUploadSpeed.value = 0f
                }
            }
        }
    }
    
    /**
     * Инициализация графика с начальными данными
     */
    private fun initializeTrafficGraph() {
        // Создаем начальную волну для демонстрации (более заметная волна)
        val initialData = List(60) { index ->
            val baseValue = 100f
            val wave1 = Math.sin(index / 8.0) * 80f
            val wave2 = Math.cos(index / 12.0) * 40f
            (baseValue + wave1 + wave2).toFloat().coerceAtLeast(20f)
        }
        _trafficData.value = initialData
    }
    
    fun toggleTheme() {
        val newTheme = !_isDarkTheme.value
        _isDarkTheme.value = newTheme
        prefs.edit().putBoolean("is_dark_theme", newTheme).apply()
    }
    
    fun updateConnectionStatus(status: ConnectionStatus) {
        val wasConnected = _connectionStatus.value.isConnected
        _connectionStatus.value = status
        
        // Сохраняем время начала подключения
        if (status.isConnected && !wasConnected) {
            connectionStartTime = System.currentTimeMillis()
        } else if (!status.isConnected && wasConnected) {
            connectionStartTime = null
        }
    }
    
    fun getConnectionDuration(): Long? {
        return connectionStartTime?.let { 
            System.currentTimeMillis() - it 
        }
    }
    
    fun setVpnConfig(config: String) {
        _vpnConfig.value = config
        saveConfig(config)
    }
    
    private fun loadSavedConfig() {
        val saved = prefs.getString("saved_config", null)
        if (saved != null) {
            _vpnConfig.value = saved
        }
    }
    
    private fun saveConfig(config: String) {
        prefs.edit().putString("saved_config", config).apply()
    }
    
    /**
     * Обновление данных графика трафика в реальном времени
     */
    private fun startTrafficGraphUpdate() {
        viewModelScope.launch {
            var lastUploadBytes = 0L
            var lastDownloadBytes = 0L
            var lastUpdateTime = System.currentTimeMillis()
            var isFirstUpdate = true
            
            while (true) {
                delay(150) // Обновляем каждые 150мс для более динамичного отображения
                
                if (_connectionStatus.value.isConnected) {
                    val currentTime = System.currentTimeMillis()
                    val timeDelta = (currentTime - lastUpdateTime).coerceAtLeast(1L) / 1000f // в секундах
                    
                    val currentUpload = _connectionStatus.value.uploadBytes
                    val currentDownload = _connectionStatus.value.downloadBytes
                    
                    // При первом обновлении после подключения инициализируем счетчики
                    if (isFirstUpdate) {
                        lastUploadBytes = currentUpload
                        lastDownloadBytes = currentDownload
                        lastUpdateTime = currentTime
                        isFirstUpdate = false
                        
                        // Добавляем начальную точку (небольшая активность для визуализации)
                        val initialSpeed = 50f + Random.nextFloat() * 30f // Начальная скорость 50-80 KB/s
                        val newData = _trafficData.value.drop(1).toList() + initialSpeed
                        _trafficData.value = newData
                        continue
                    }
                    
                    // Вычисляем скорость в байтах/сек (входящий + исходящий трафик)
                    val uploadSpeed = if (timeDelta > 0 && currentUpload >= lastUploadBytes) {
                        ((currentUpload - lastUploadBytes) / timeDelta).toFloat()
                    } else 0f
                    
                    val downloadSpeed = if (timeDelta > 0 && currentDownload >= lastDownloadBytes) {
                        ((currentDownload - lastDownloadBytes) / timeDelta).toFloat()
                    } else 0f
                    
                    // Общая скорость (KB/s) для отображения на графике
                    var totalSpeed = (uploadSpeed + downloadSpeed) / 1024f // конвертируем в KB/s
                    
                    // Получаем последнее значение для плавного перехода
                    val lastSpeed = _trafficData.value.lastOrNull() ?: 50f
                    
                    // Если есть реальные данные, используем их (без ограничения максимума для реального отображения)
                    if (totalSpeed > 0f) {
                        // Вычисляем разницу между новой и старой скоростью
                        val speedDiff = totalSpeed - lastSpeed
                        
                        // Ограничиваем максимальное изменение скорости за один шаг (для плавности, но не ограничиваем максимум)
                        val maxChangePerStep = 50f // Максимальное изменение 50 KB/s за шаг (увеличено для лучшей реакции)
                        val limitedSpeedDiff = speedDiff.coerceIn(-maxChangePerStep, maxChangePerStep)
                        
                        // Применяем ограниченное изменение с плавной интерполяцией
                        val targetSpeed = lastSpeed + limitedSpeedDiff
                        // Используем реальную скорость, но с плавным переходом
                        totalSpeed = lastSpeed * 0.8f + targetSpeed * 0.2f // Плавное смешивание (80% старого, 20% нового)
                    } else if (currentUpload > 0 || currentDownload > 0) {
                        // Если данные есть, но скорость временно 0, используем минимальную активность
                        totalSpeed = maxOf(lastSpeed * 0.95f, 5f) // Очень плавно уменьшаем, но не ниже 5 KB/s
                    } else {
                        // Если нет реальных данных, используем плавную симуляцию с реалистичными вариациями
                        // Создаем плавную волну вместо резких скачков
                        val wavePhase = System.currentTimeMillis() / 1000f // Фаза волны
                        val wave1 = Math.sin((wavePhase * 0.3).toDouble()).toFloat() * 10f // Медленная волна ±10 KB/s (более плавная)
                        val wave2 = Math.cos((wavePhase * 0.8).toDouble()).toFloat() * 5f // Быстрая волна ±5 KB/s (более плавная)
                        val baseSpeed = 50f + wave1 + wave2
                        val targetSpeed = baseSpeed.coerceIn(30f, 100f)
                        
                        // Более плавный переход к целевой скорости (интерполяция)
                        totalSpeed = lastSpeed * 0.9f + targetSpeed * 0.1f
                    }
                    
                    currentTrafficSpeed = totalSpeed.coerceAtLeast(0f)
                    
                    // Обновляем скорость для статистики
                    _currentSpeed.value = totalSpeed.coerceAtLeast(0f)
                    
                    // Обновляем последние значения байтов (важно для правильного расчета скорости)
                    // Обновляем время всегда, чтобы timeDelta был правильным
                    lastUpdateTime = currentTime
                    if (currentUpload != lastUploadBytes || currentDownload != lastDownloadBytes) {
                        lastUploadBytes = currentUpload
                        lastDownloadBytes = currentDownload
                    }
                    
                    // Добавляем новую точку и убираем старую (всегда обновляем)
                    val newData = _trafficData.value.drop(1).toList() + currentTrafficSpeed
                    _trafficData.value = newData
                } else {
                    // Когда не подключено, постепенно уменьшаем до 0
                    val newData = _trafficData.value.map { it * 0.9f }
                    _trafficData.value = newData
                    
                    // Сбрасываем счетчики
                    lastUploadBytes = 0L
                    lastDownloadBytes = 0L
                    lastUpdateTime = System.currentTimeMillis()
                    isFirstUpdate = true
                }
            }
        }
    }
    
    fun connectVpn(activity: Activity, configString: String) {
        viewModelScope.launch {
            _connectionStatus.value = _connectionStatus.value.copy(isConnecting = true)
            
            try {
                // Парсим конфиг
                val parser = ConfigParser()
                val parsedConfig = parser.parseConfig(configString)
                if (parsedConfig == null) {
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnecting = false,
                        isConnected = false
                    )
                    return@launch
                }
                
                // Запрашиваем разрешение VPN
                val intent = VpnService.prepare(activity)
                if (intent != null) {
                    // Для Compose Activity нужно использовать Activity Result API
                    // Временно используем старый способ
                    @Suppress("DEPRECATION")
                    if (activity is androidx.appcompat.app.AppCompatActivity) {
                        activity.startActivityForResult(intent, 100)
                    } else {
                        activity.startActivity(intent)
                    }
                    return@launch
                }
                
                // Запускаем VPN сервис
                val serviceIntent = Intent(activity, KizVpnService::class.java).apply {
                    putExtra("config", configString)
                    action = "com.kizvpn.client.START"
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    activity.startForegroundService(serviceIntent)
                } else {
                    activity.startService(serviceIntent)
                }
                
                // Обновляем статус (реальный статус будет обновляться через BroadcastReceiver)
                _connectionStatus.value = _connectionStatus.value.copy(
                    isConnecting = false,
                    isConnected = true,
                    connectedAt = System.currentTimeMillis() // Сохраняем время подключения
                )
                
            } catch (e: Exception) {
                _connectionStatus.value = _connectionStatus.value.copy(
                    isConnecting = false,
                    isConnected = false
                )
            }
        }
    }
    
    fun disconnectVpn(activity: Activity) {
        viewModelScope.launch {
            val serviceIntent = Intent(activity, KizVpnService::class.java).apply {
                action = "com.kizvpn.client.STOP"
            }
            activity.startService(serviceIntent)
            
            _connectionStatus.value = _connectionStatus.value.copy(
                isConnected = false,
                isConnecting = false,
                connectedAt = null // Сбрасываем время подключения
            )
        }
    }
    
    fun updateTrafficStats(upload: Long, download: Long) {
        _connectionStatus.value = _connectionStatus.value.copy(
            uploadBytes = upload,
            downloadBytes = download
        )
    }
    
    fun updateLatency(latency: Int) {
        _connectionStatus.value = _connectionStatus.value.copy(latency = latency)
    }
    
    fun updateSubscriptionInfo(subscriptionInfo: com.kizvpn.client.data.SubscriptionInfo?) {
        _subscriptionInfo.value = subscriptionInfo
    }
    
    fun clearSubscriptionInfo() {
        _subscriptionInfo.value = null
    }
}

