package com.kizvpn.client

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.kizvpn.client.data.SubscriptionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.kizvpn.client.api.VpnApiClient
import com.kizvpn.client.config.ConfigParser
import com.kizvpn.client.ui.models.ConnectionStatus
import com.kizvpn.client.ui.models.Server
import com.kizvpn.client.ui.navigation.AppNavHost
import com.kizvpn.client.ui.theme.KizVpnTheme
import androidx.compose.material3.MaterialTheme
import com.kizvpn.client.ui.viewmodel.VpnViewModel
import com.kizvpn.client.vpn.KizVpnService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.net.TrafficStats
import android.app.ActivityManager
import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    
    private val viewModel: VpnViewModel by viewModels()
    private val configParser = ConfigParser()
    // VpnApiClient with 3x-ui subscription API and Telegram Bot API support
    // TODO: Replace with your actual server settings
    // subscriptionPort: subscription port from 3x-ui settings (typically 2096)
    // telegramBotUrl: null - URL of Telegram Bot API (optional)
    private val apiClient = VpnApiClient(
        baseUrl = "http://YOUR_SERVER_IP:8081",  // Replace with your VPN server IP and API port
        subscriptionPort = 2096,                  // Replace with your subscription port (default: 2096)
        telegramBotUrl = null  // TODO: Configure Telegram Bot API URL if your bot provides HTTP API
    )
    private lateinit var connectionHistoryManager: com.kizvpn.client.data.ConnectionHistoryManager
    private var connectionStartTime: Long? = null // Время начала подключения для расчета длительности
    
    // HTTP client для проверки подключения (с ограничением соединений)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES)) // Ограничиваем пул соединений
        .build()
    
    // Флаг для предотвращения множественных одновременных проверок (используем AtomicBoolean для thread-safety)
    private val isCheckingConnection = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Job для обновления статистики (чтобы можно было отменить)
    private var statsUpdateJob: kotlinx.coroutines.Job? = null
    
    // Job для периодического обновления подписки (каждые 12 часов, как в настройках 3x-ui)
    private var subscriptionUpdateJob: kotlinx.coroutines.Job? = null
    
    // Subscription data
    private var subscriptionInfo: com.kizvpn.client.data.SubscriptionInfo? = null
    private val subscriptionInfoState = mutableStateOf<com.kizvpn.client.data.SubscriptionInfo?>(null)
    
    // Config notification state
    private val configNotificationState = mutableStateOf<String?>(null)
    
    // VPN permission launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Разрешение получено, подключаемся
            val config = getSavedConfig()
            if (!config.isNullOrEmpty()) {
                connectToVpn(config)
            }
        }
    }
    
    // Notification permission launcher (для Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
            // Если разрешение не дано, открываем настройки уведомлений
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to open notification settings", e)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Включаем edge-to-edge режим (приложение на весь экран)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Убеждаемся, что статус-бар всегда виден и активен
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.apply {
            // Показываем статус-бар
            show(WindowInsetsCompat.Type.statusBars())
            // Устанавливаем поведение по умолчанию - статус-бар всегда виден
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        
        // Устанавливаем флаги окна для полноэкранного режима, но оставляем статус-бар видимым
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // Инициализируем менеджер истории подключений
        connectionHistoryManager = com.kizvpn.client.data.ConnectionHistoryManager(this)
        
        // Мигрируем неправильно сохраненные конфиги (если WireGuard конфиг в saved_config, переносим его)
        migrateIncorrectlySavedConfigs()
        migrateOldConfigsToLists() // Мигрируем старые конфиги в новый формат списков
        
        // Загружаем сохраненный конфиг
        loadSavedConfig()
        
        // Загружаем сохраненную информацию о подписке
        loadSubscriptionInfo()
        
        // Запускаем периодическое обновление подписки (каждые 12 часов, как в настройках 3x-ui)
        startSubscriptionUpdateJob()
        
        // Запрашиваем разрешение на уведомления (для Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Обработка deep link при запуске
        handleIntent(intent)
        
        // Явно инициализируем состояние как отключенное при первом запуске
        viewModel.updateConnectionStatus(
            ConnectionStatus(isConnected = false, isConnecting = false)
        )
        
        // Проверяем статус VPN сервиса (асинхронно)
        checkVpnStatus()
        
        // Проверяем автоподключение (после проверки статуса VPN)
        lifecycleScope.launch {
            delay(500) // Даем время на проверку статуса VPN
            checkAutoConnect()
        }
        
        setContent {
            // Всегда используем темную тему
            // Обновляем цвет статус-бара для темной темы (белые иконки)
            SideEffect {
                windowInsetsController?.apply {
                    isAppearanceLightStatusBars = false // Белые иконки для темной темы
                }
            }
            
            KizVpnTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHostComposable()
                }
            }
        }
        
    }
    
    override fun onResume() {
        super.onResume()
        // Проверяем статус VPN при возврате в приложение
        checkVpnStatus()
        
        // Начинаем проверку подключения и обновление статистики, если VPN подключен
        lifecycleScope.launch {
            viewModel.connectionStatus.collect { status ->
                if (status.isConnected && statsUpdateJob?.isActive != true) {
                    statsUpdateJob = startStatsUpdate()
                } else if (!status.isConnected) {
                    statsUpdateJob?.cancel()
                    statsUpdateJob = null
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Останавливаем обновления статистики при уходе из приложения
        // Обновление подписки продолжает работать в фоне
        statsUpdateJob?.cancel()
        statsUpdateJob = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем все фоновые задачи
        statsUpdateJob?.cancel()
        subscriptionUpdateJob?.cancel()
        statsUpdateJob = null
        subscriptionUpdateJob = null
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            handleIntent(intent)
        }
    }
    
    /**
     * Обработка deep link из Telegram бота
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        val data = intent.data
        if (data != null) {
            var configUrl: String? = null
            
            when {
                data.scheme == "kizvpn" -> {
                    // kizvpn://vless://...
                    configUrl = data.toString().replace("kizvpn://", "")
                }
                data.scheme == "https" && data.host == "your-domain.com" -> {
                    // https://your-domain.com/connect?vless=...
                    configUrl = intent.getStringExtra("vless") ?: data.getQueryParameter("vless")
                    if (configUrl == null && data.path?.contains("/connect") == true) {
                        configUrl = data.fragment
                    }
                }
            }
            
            if (!configUrl.isNullOrBlank()) {
                // Сохраняем конфиг
                saveConfig(configUrl)
                viewModel.setVpnConfig(configUrl)
                
                // Парсим и проверяем
                val parsed = configParser.parseConfig(configUrl)
                if (parsed != null) {
                    Toast.makeText(this, "Конфиг получен из Telegram", Toast.LENGTH_SHORT).show()
                    // Проверяем подписку
                    checkSubscriptionFromConfig(configUrl)
                    // Автоматически подключаемся
                    connectToVpn(configUrl)
                } else {
                    // Показываем уведомление вверху экрана
                    configNotificationState.value = "Неверный формат конфига"
                    lifecycleScope.launch {
                        delay(3000)
                        configNotificationState.value = null
                    }
                }
            }
        }
    }
    
    /**
     * Подключение к VPN
     */
    private fun connectToVpn(configString: String) {
        // Проверка на пустой конфиг
        if (configString.isBlank()) {
            Log.w("MainActivity", "connectToVpn: config is blank")
            configNotificationState.value = "Вставьте конфиг в настройках VPN"
            lifecycleScope.launch {
                delay(3000)
                configNotificationState.value = null
            }
            return
        }
        
        val parsedConfig = configParser.parseConfig(configString)
        if (parsedConfig == null) {
            // Показываем уведомление вверху экрана
            configNotificationState.value = "Неверный формат конфига"
            lifecycleScope.launch {
                delay(3000)
                configNotificationState.value = null
            }
            return
        }
        
        // Проверяем поддержку протокола
        if (parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.VLESS && 
            parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
            // Показываем уведомление вверху экрана
            configNotificationState.value = "Протокол ${parsedConfig.protocol} не поддерживается. Используйте VLESS или WireGuard"
            lifecycleScope.launch {
                delay(4000)
                configNotificationState.value = null
            }
            Log.w("MainActivity", "Protocol ${parsedConfig.protocol} is not supported. Use VLESS or WireGuard config instead.")
            return
        }
        
        // Проверяем разрешение VPN
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.d("MainActivity", "VPN permission not granted, requesting...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Log.d("MainActivity", "VPN permission already granted, starting connection...")
            startVpnConnection(configString)
        }
    }
    
    /**
     * Запуск VPN соединения
     */
    private fun startVpnConnection(configString: String) {
        viewModel.updateConnectionStatus(
            viewModel.connectionStatus.value.copy(isConnecting = true)
        )
        
        lifecycleScope.launch {
            try {
                // Запускаем VPN сервис
                val serviceIntent = Intent(this@MainActivity, KizVpnService::class.java).apply {
                    putExtra("config", configString)
                    action = "com.kizvpn.client.START"
                }
                
                startForegroundService(serviceIntent)
                
                // Ждем установления соединения
                delay(2000)
                
                // Обновляем статус
                val server = Server(
                    id = "current",
                    country = "Unknown",
                    pingMs = 0,
                    loadPercent = 0,
                    isSelected = true
                )
                
                viewModel.updateConnectionStatus(
                    ConnectionStatus(
                        isConnected = true,
                        isConnecting = false,
                        server = server
                    )
                )
                
                // Сохраняем в историю
                connectionStartTime = System.currentTimeMillis()
                val serverName = server.city?.let { "${server.country}, $it" } ?: server.country
                connectionHistoryManager.addEntry(
                    com.kizvpn.client.data.ConnectionHistoryEntry(
                        timestamp = connectionStartTime!!,
                        action = "connected",
                        server = serverName
                    )
                )
                
                // Начинаем обновление статистики
                startStatsUpdate()
                
                // После успешного подключения пробуем обновить подписку (если конфиг содержит UUID/комментарий)
                checkSubscriptionFromConfig(configString)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting VPN", e)
                viewModel.updateConnectionStatus(
                    viewModel.connectionStatus.value.copy(
                        isConnected = false,
                        isConnecting = false
                    )
                )
                // Показываем уведомление об ошибке подключения
                val message = "Ошибка подключения: ${e.message ?: "неизвестная ошибка"}"
                configNotificationState.value = message
                lifecycleScope.launch {
                    delay(4000)
                    configNotificationState.value = null
                }
            }
        }
    }
    
    /**
     * Отключение от VPN
     */
    private fun disconnectFromVpn() {
        lifecycleScope.launch {
            try {
                val serviceIntent = Intent(this@MainActivity, KizVpnService::class.java).apply {
                    action = "com.kizvpn.client.STOP"
                }
                startService(serviceIntent)
                
                viewModel.updateConnectionStatus(
                    ConnectionStatus(
                        isConnected = false,
                        isConnecting = false
                    )
                )
                
                // Сохраняем отключение в историю
                val duration = connectionStartTime?.let { System.currentTimeMillis() - it }
                connectionHistoryManager.addEntry(
                    com.kizvpn.client.data.ConnectionHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        action = "disconnected",
                        duration = duration
                    )
                )
                connectionStartTime = null
                
                // После отключения VPN проверяем подписку через SSH (если еще не проверена)
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val savedConfig = prefs.getString("saved_config", null)
                if (savedConfig != null && subscriptionInfo == null) {
                    Log.d("MainActivity", "VPN disconnected, checking subscription via SSH...")
                    checkSubscriptionFromConfig(savedConfig)
                }
                
            } catch (e: Exception) {
                val message = "Ошибка отключения: ${e.message ?: "неизвестная ошибка"}"
                configNotificationState.value = message
                lifecycleScope.launch {
                    delay(4000)
                    configNotificationState.value = null
                }
            }
        }
    }
    
    /**
     * Проверка статуса VPN сервиса
     */
    private fun checkVpnStatus() {
        lifecycleScope.launch {
            try {
                // Сначала проверяем, запущен ли наш VPN сервис
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                
                val isServiceRunning = runningServices.any { service ->
                    service.service.className == KizVpnService::class.java.name
                }
                
                if (!isServiceRunning) {
                    // Сервис не запущен - VPN точно отключен
                    viewModel.updateConnectionStatus(
                        viewModel.connectionStatus.value.copy(
                            isConnected = false,
                            isConnecting = false
                        )
                    )
                    Log.d("MainActivity", "VPN статус: отключен (сервис не запущен)")
                    return@launch
                }
                
                // Сервис запущен - проверяем, есть ли активный VPN интерфейс
                // Если prepare() возвращает null, VPN уже активен
                val vpnIntent = VpnService.prepare(this@MainActivity)
                
                if (vpnIntent == null) {
                    // VPN активен - обновляем статус
                    viewModel.updateConnectionStatus(
                        viewModel.connectionStatus.value.copy(
                            isConnected = true,
                            isConnecting = false
                        )
                    )
                    Log.d("MainActivity", "VPN статус: подключен (VpnService.prepare() вернул null)")
                } else {
                        // Сервис запущен, но VPN интерфейс еще не создан - возможно идет подключение
                        viewModel.updateConnectionStatus(
                            viewModel.connectionStatus.value.copy(
                                isConnected = false, // Не считаем подключенным, пока нет интерфейса
                                isConnecting = true
                            )
                        )
                        Log.d("MainActivity", "VPN сервис запущен, но интерфейс не создан")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка при проверке статуса VPN: ${e.message}", e)
                // В случае ошибки считаем, что VPN отключен
                        viewModel.updateConnectionStatus(
                            viewModel.connectionStatus.value.copy(
                                isConnected = false,
                                isConnecting = false
                            )
                        )
            }
        }
    }
    
    /**
     * Проверка автоподключения при запуске приложения
     */
    private fun checkAutoConnect() {
        lifecycleScope.launch {
            try {
                val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                val autoConnect = prefs.getBoolean("auto_connect", false)
                
                if (autoConnect) {
                    Log.d("MainActivity", "Автоподключение включено, проверяю статус VPN...")
                    delay(1000) // Даем время на проверку статуса VPN
                    
                    val currentStatus = viewModel.connectionStatus.value
                    val savedConfig = getSavedConfig()
                    
                    // Подключаемся только если VPN не подключен и есть сохраненный конфиг
                    if (!currentStatus.isConnected && !currentStatus.isConnecting && savedConfig != null) {
                        Log.d("MainActivity", "Автоматическое подключение к VPN...")
                        connectToVpn(savedConfig)
                    } else {
                        Log.d("MainActivity", "Автоподключение пропущено: connected=${currentStatus.isConnected}, connecting=${currentStatus.isConnecting}, hasConfig=${savedConfig != null}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка при проверке автоподключения: ${e.message}", e)
            }
        }
    }
    
    /**
     * Обновление статистики трафика
     */
    private fun startStatsUpdate(): kotlinx.coroutines.Job {
        return lifecycleScope.launch {
            var downloadBytes = 0L
            var uploadBytes = 0L
            var lastCheckTime = 0L
            var updateCount = 0
            var isFirstUpdate = true
            
            while (viewModel.connectionStatus.value.isConnected) {
                delay(2000) // Увеличено с 1 до 2 секунд для уменьшения нагрузки
                
                try {
                    // Получаем реальную статистику через TrafficStats
                    // Это простой способ получить статистику трафика для текущего приложения
                    try {
                        val uid = android.os.Process.myUid()
                        val currentRxBytes = TrafficStats.getUidRxBytes(uid)
                        val currentTxBytes = TrafficStats.getUidTxBytes(uid)
                        
                        // TrafficStats возвращает -1 если статистика недоступна
                        // TrafficStats возвращает накопленные байты с момента загрузки системы
                        if (currentRxBytes >= 0 && currentTxBytes >= 0) {
                            if (isFirstUpdate) {
                                // Первое обновление - сохраняем начальные значения (это накопленные байты)
                                downloadBytes = currentRxBytes
                                uploadBytes = currentTxBytes
                                isFirstUpdate = false
                            } else {
                                // Вычисляем разницу от предыдущих значений (реальная скорость)
                                val prevRx = viewModel.connectionStatus.value.downloadBytes
                                val prevTx = viewModel.connectionStatus.value.uploadBytes
                                
                                if (currentRxBytes >= prevRx && currentTxBytes >= prevTx) {
                                    // Значения увеличились - используем новые накопленные значения
                                    // ViewModel сам вычислит скорость из разницы
                                    downloadBytes = currentRxBytes
                                    uploadBytes = currentTxBytes
                                } else {
                                    // Значения могли сброситься - это нормально, используем новые значения
                                    downloadBytes = currentRxBytes
                                    uploadBytes = currentTxBytes
                                }
                            }
                            
                            // Всегда обновляем статистику при каждом обновлении
                            // ViewModel вычислит скорость из разницы байтов
                            viewModel.updateTrafficStats(uploadBytes, downloadBytes)
                        } else {
                            // Если TrafficStats недоступен, используем симуляцию
                            throw Exception("TrafficStats недоступен")
                        }
                    } catch (e: Exception) {
                        // Если не удалось получить реальную статистику, используем минимальную симуляцию
                        Log.w("MainActivity", "Не удалось получить статистику трафика: ${e.message}")
                        
                        if (isFirstUpdate) {
                            downloadBytes = 1000L
                            uploadBytes = 500L
                            isFirstUpdate = false
                            viewModel.updateTrafficStats(uploadBytes, downloadBytes)
                        } else {
                            // Минимальная симуляция для демонстрации
                            val currentDownload = viewModel.connectionStatus.value.downloadBytes
                            val currentUpload = viewModel.connectionStatus.value.uploadBytes
                            
                            if (currentDownload == 0L && currentUpload == 0L) {
                                downloadBytes += (50..200).random().toLong()
                                uploadBytes += (20..100).random().toLong()
                            } else {
                                downloadBytes = currentDownload
                                uploadBytes = currentUpload
                            }
                            
                            updateCount++
                            if (updateCount % 2 == 0) {
                                viewModel.updateTrafficStats(uploadBytes, downloadBytes)
                            }
                        }
                    }
                    
                    // Проверка подключения каждые 60 секунд (увеличено для уменьшения нагрузки)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastCheckTime > 60000) {
                        lastCheckTime = currentTime
                        checkConnection()
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e("MainActivity", "Out of memory in startStatsUpdate", e)
                    // Пропускаем обновление, если не хватает памяти
                    delay(5000) // Ждем 5 секунд перед следующей попыткой
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in startStatsUpdate", e)
                }
            }
        }
    }
    
    /**
     * Проверка подключения через HTTP запрос
     */
    private fun checkConnection() {
        // Предотвращаем множественные одновременные проверки (thread-safe)
        if (!isCheckingConnection.compareAndSet(false, true)) {
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.ipify.org?format=text")
                    .build()
                
                // Выполняем запрос и закрываем ресурсы правильно
                val response = httpClient.newCall(request).execute()
                try {
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            try {
                                val ipAddress = body.string().trim()
                                if (ipAddress.isNotEmpty()) {
                                    Log.d("MainActivity", "VPN IP: $ipAddress")
                                    // Можно обновить UI с IP адресом
                                }
                            } finally {
                                body.close()
                            }
                        }
                    }
                } finally {
                    response.close()
                }
            } catch (e: OutOfMemoryError) {
                // Обрабатываем OutOfMemoryError отдельно
                Log.e("MainActivity", "Out of memory during connection check", e)
                // Пропускаем проверку, если не хватает памяти
            } catch (e: java.net.SocketTimeoutException) {
                // Таймаут - это нормально, если VPN не может подключиться к внешнему сервису
                // Не логируем как ошибку, чтобы не засорять логи
                // VPN работает, но внешние проверки могут таймаутить
            } catch (e: java.net.UnknownHostException) {
                // Не можем резолвить хост - это нормально при работе VPN
                // Не логируем как ошибку, чтобы не засорять логи
            } catch (e: Exception) {
                // Логируем только реальные ошибки
                Log.e("MainActivity", "Connection check failed", e)
            } finally {
                isCheckingConnection.set(false)
            }
        }
    }
    
    /**
     * Сохранение конфига в список (добавляет, не перезаписывает)
     */
    private fun saveConfig(config: String) {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val parsedConfig = configParser.parseConfig(config)
        
        if (parsedConfig == null) {
            Log.w("MainActivity", "saveConfig: Не удалось распарсить конфиг, пропускаем сохранение")
            return
        }
        
        // Определяем тип конфига
        val configType = when (parsedConfig.protocol) {
            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
            else -> "vless" // По умолчанию Vless
        }
        
        Log.d("MainActivity", "saveConfig: protocol=${parsedConfig.protocol}, configType=$configType")
        
        try {
            // Загружаем существующий список конфигов
            val configListKey = when (parsedConfig.protocol) {
                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "saved_vless_configs_list"
                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "saved_wireguard_configs_list"
                else -> "saved_vless_configs_list"
            }
            
            val existingListJson = prefs.getString(configListKey, "[]")
            val configList = org.json.JSONArray(existingListJson)
            
            // Проверяем, есть ли уже такой конфиг в списке
            var configExists = false
            for (i in 0 until configList.length()) {
                val item = configList.getJSONObject(i)
                if (item.getString("config") == config) {
                    configExists = true
                    break
                }
            }
            
            // Если конфиг еще не существует, добавляем его
            if (!configExists) {
                val configObject = org.json.JSONObject()
                configObject.put("config", config)
                configObject.put("name", parsedConfig.name ?: when (parsedConfig.protocol) {
                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "Vless конфиг"
                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "WireGuard конфиг"
                    else -> "Конфиг"
                })
                configObject.put("addedAt", System.currentTimeMillis())
                
                configList.put(configObject)
                Log.d("MainActivity", "saveConfig: Добавлен новый конфиг в список (всего: ${configList.length()})")
            } else {
                Log.d("MainActivity", "saveConfig: Конфиг уже существует в списке, пропускаем")
            }
            
            // Сохраняем обновленный список
            val editor = prefs.edit()
            editor.putString(configListKey, configList.toString())
            
            // Также сохраняем последний добавленный конфиг для обратной совместимости
            when (parsedConfig.protocol) {
                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                    editor.putString("saved_config", config)
                    if (parsedConfig.name != null) {
                        editor.putString("saved_config_name", parsedConfig.name)
                    }
                }
                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                    editor.putString("saved_wireguard_config", config)
                }
                else -> {
                    editor.putString("saved_config", config)
                }
            }
            
            editor.putString("active_config_type", configType)
            editor.commit()
            
            Log.d("MainActivity", "saveConfig: Конфиг добавлен в список, active_config_type = $configType")
        } catch (e: Exception) {
            Log.e("MainActivity", "saveConfig: Ошибка при сохранении конфига в список", e)
            // Fallback: сохраняем как раньше (один конфиг)
            val editor = prefs.edit()
            when (parsedConfig.protocol) {
                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                    editor.putString("saved_config", config)
                    if (parsedConfig.name != null) {
                        editor.putString("saved_config_name", parsedConfig.name)
                    }
                }
                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                    editor.putString("saved_wireguard_config", config)
                }
                else -> {
                    editor.putString("saved_config", config)
                }
            }
            editor.putString("active_config_type", configType)
            editor.commit()
        }
    }
    
    /**
     * Миграция старых конфигов в новый формат списков
     * Переносит конфиги из старых ключей (saved_config, saved_wireguard_config) в списки
     */
    private fun migrateOldConfigsToLists() {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        
        try {
            // Мигрируем Vless конфиг
            val vlessConfig = prefs.getString("saved_config", null)
            val vlessName = prefs.getString("saved_config_name", null)
            if (!vlessConfig.isNullOrBlank()) {
                val parsedConfig = configParser.parseConfig(vlessConfig)
                if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.VLESS) {
                    val listJson = prefs.getString("saved_vless_configs_list", "[]")
                    val configList = org.json.JSONArray(listJson)
                    
                    // Проверяем, есть ли уже этот конфиг в списке
                    var exists = false
                    for (i in 0 until configList.length()) {
                        val item = configList.getJSONObject(i)
                        if (item.getString("config") == vlessConfig) {
                            exists = true
                            break
                        }
                    }
                    
                    if (!exists) {
                        val configObject = org.json.JSONObject()
                        configObject.put("config", vlessConfig)
                        configObject.put("name", vlessName ?: "Vless конфиг")
                        configObject.put("addedAt", System.currentTimeMillis())
                        configList.put(configObject)
                        prefs.edit().putString("saved_vless_configs_list", configList.toString()).commit()
                        Log.d("MainActivity", "migrateOldConfigsToLists: Мигрирован Vless конфиг в список")
                    }
                }
            }
            
            // Мигрируем WireGuard конфиг
            val wireGuardConfig = prefs.getString("saved_wireguard_config", null)
            if (!wireGuardConfig.isNullOrBlank()) {
                val parsedConfig = configParser.parseConfig(wireGuardConfig)
                if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                    val listJson = prefs.getString("saved_wireguard_configs_list", "[]")
                    val configList = org.json.JSONArray(listJson)
                    
                    // Проверяем, есть ли уже этот конфиг в списке
                    var exists = false
                    for (i in 0 until configList.length()) {
                        val item = configList.getJSONObject(i)
                        if (item.getString("config") == wireGuardConfig) {
                            exists = true
                            break
                        }
                    }
                    
                    if (!exists) {
                        val configObject = org.json.JSONObject()
                        configObject.put("config", wireGuardConfig)
                        configObject.put("name", "WireGuard конфиг")
                        configObject.put("addedAt", System.currentTimeMillis())
                        configList.put(configObject)
                        prefs.edit().putString("saved_wireguard_configs_list", configList.toString()).commit()
                        Log.d("MainActivity", "migrateOldConfigsToLists: Мигрирован WireGuard конфиг в список")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "migrateOldConfigsToLists: Ошибка при миграции", e)
        }
    }
    
    /**
     * Миграция неправильно сохраненных конфигов
     * Если WireGuard конфиг был сохранен в saved_config, переносим его в saved_wireguard_config
     * ВАЖНО: НЕ удаляем saved_config, если там уже есть правильный Vless конфиг
     */
    private fun migrateIncorrectlySavedConfigs() {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val savedConfig = prefs.getString("saved_config", null)
        val savedWireGuardConfig = prefs.getString("saved_wireguard_config", null)
        
        if (!savedConfig.isNullOrBlank()) {
            val parsedConfig = configParser.parseConfig(savedConfig)
            if (parsedConfig != null && parsedConfig.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                Log.w("MainActivity", "migrateIncorrectlySavedConfigs: Обнаружен WireGuard конфиг в saved_config, переносим в saved_wireguard_config")
                
                // Переносим WireGuard конфиг в правильное место только если там еще нет WireGuard конфига
                if (savedWireGuardConfig.isNullOrBlank()) {
                    prefs.edit()
                        .putString("saved_wireguard_config", savedConfig)
                        .putString("active_config_type", "wireguard")
                        .commit()
                    Log.d("MainActivity", "migrateIncorrectlySavedConfigs: WireGuard конфиг перенесен в saved_wireguard_config")
                    // НЕ удаляем saved_config - он может содержать правильный Vless конфиг, который был сохранен позже
                } else {
                    // Если WireGuard конфиг уже есть в правильном месте, проверяем, не является ли saved_config правильным Vless конфигом
                    // Если нет - удаляем только дубликат WireGuard
                    val savedConfigParsed = configParser.parseConfig(savedConfig)
                    if (savedConfigParsed != null && savedConfigParsed.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                        // Это действительно дубликат WireGuard - удаляем его
                        prefs.edit()
                            .remove("saved_config")
                            .remove("saved_config_name")
                            .putString("active_config_type", "wireguard")
                            .commit()
                        Log.d("MainActivity", "migrateIncorrectlySavedConfigs: WireGuard конфиг уже существует, удален дубликат из saved_config")
                    } else {
                        // saved_config содержит правильный Vless конфиг - не удаляем его
                        Log.d("MainActivity", "migrateIncorrectlySavedConfigs: saved_config содержит правильный конфиг (${savedConfigParsed?.protocol}), не удаляем")
                    }
                }
            }
        }
    }
    
    /**
     * Загрузка сохраненного конфига
     */
    private fun loadSavedConfig() {
        // Используем getSavedConfig(), который правильно определяет активный конфиг
        val savedConfig = getSavedConfig()
        if (savedConfig != null) {
            viewModel.setVpnConfig(savedConfig)
            // Проверяем подписку при загрузке конфига
            checkSubscriptionFromConfig(savedConfig)
            Log.d("MainActivity", "Loaded saved config: ${savedConfig.take(50)}... (type: ${getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE).getString("active_config_type", "unknown")})")
        }
    }
    
    /**
     * Проверка подписки на основе конфига
     * Пробует несколько методов: API, SSH через бота, 3x-ui
     */
    private fun checkSubscriptionFromConfig(config: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsedConfig = configParser.parseConfig(config)
                Log.d("MainActivity", "Parsed config: uuid=${parsedConfig?.uuid}, protocol=${parsedConfig?.protocol}, name=${parsedConfig?.name}")
                
                // Для WireGuard конфигов пытаемся извлечь UUID из имени конфига или примечания
                var uuidToCheck: String? = parsedConfig?.uuid
                
                if (parsedConfig != null && uuidToCheck.isNullOrBlank() && parsedConfig.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                    // Для WireGuard конфигов пытаемся найти UUID в имени конфига или примечании
                    // В 3x-ui WireGuard конфиги могут иметь имена вида "Email user30@vpn.local"
                    // или содержать UUID в имени/примечании
                    val name = parsedConfig.name
                    val comment = parsedConfig.comment // Примечание из конфига
                    
                    // Логируем полную информацию о конфиге для отладки
                    Log.d("MainActivity", "=== WireGuard Config Analysis ===")
                    Log.d("MainActivity", "Config name: $name")
                    Log.d("MainActivity", "Config comment: $comment")
                    Log.d("MainActivity", "Full config length: ${parsedConfig.rawConfig.length} chars")
                    Log.d("MainActivity", "Full config content:\n${parsedConfig.rawConfig}")
                    Log.d("MainActivity", "Config preview (first 500 chars): ${parsedConfig.rawConfig.take(500)}")
                    
                    // Функция для поиска UUID в тексте (поддерживает формат с дефисами и без)
                    fun findUuidInText(text: String?): String? {
                        if (text.isNullOrBlank()) return null
                        // Стандартный формат UUID с дефисами: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                        val uuidPatternWithDashes = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", RegexOption.IGNORE_CASE)
                        val uuidMatchWithDashes = uuidPatternWithDashes.find(text)
                        if (uuidMatchWithDashes != null) {
                            return uuidMatchWithDashes.value
                        }
                        // Формат UUID без дефисов: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (32 hex символа)
                        val uuidPatternWithoutDashes = Regex("[0-9a-fA-F]{32}", RegexOption.IGNORE_CASE)
                        val uuidMatchWithoutDashes = uuidPatternWithoutDashes.find(text)
                        if (uuidMatchWithoutDashes != null) {
                            val uuidWithoutDashes = uuidMatchWithoutDashes.value
                            // Преобразуем в формат с дефисами для единообразия
                            return "${uuidWithoutDashes.substring(0, 8)}-${uuidWithoutDashes.substring(8, 12)}-${uuidWithoutDashes.substring(12, 16)}-${uuidWithoutDashes.substring(16, 20)}-${uuidWithoutDashes.substring(20, 32)}"
                        }
                        return null
                    }
                    
                    // Сохраняем комментарий для возможного использования как идентификатор
                    var commentToUse: String? = null
                    
                    // Сначала проверяем примечание (комментарий) из конфига
                    // ConfigParser теперь фильтрует -1 и числовые значения, но на всякий случай проверяем еще раз
                    if (!comment.isNullOrBlank() && comment.trim() != "-1" && !comment.trim().matches(Regex("^-?\\d+$"))) {
                        Log.d("MainActivity", "WireGuard config comment: $comment, trying to extract UUID or email")
                        commentToUse = comment.trim() // Сохраняем комментарий для возможного использования
                        
                        val uuidFromComment = findUuidInText(comment)
                        if (uuidFromComment != null) {
                            uuidToCheck = uuidFromComment
                            Log.d("MainActivity", "Found UUID in WireGuard config comment: $uuidToCheck")
                        } else {
                            // Пытаемся найти email в примечании
                            val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                            val emailMatch = emailPattern.find(comment)
                            if (emailMatch != null) {
                                val email = emailMatch.value
                                Log.d("MainActivity", "Found email in WireGuard config comment: $email")
                                commentToUse = email // Используем email как идентификатор
                                // TODO: Поиск UUID по email через API
                            } else {
                                // Комментарий может быть идентификатором клиента в 3x-ui (например, "fsaad-1")
                                Log.d("MainActivity", "Comment is not UUID or email, but may be a client identifier: $comment")
                                Log.d("MainActivity", "Will try to use comment as identifier for subscription check")
                            }
                        }
                    } else if (!comment.isNullOrBlank()) {
                        Log.d("MainActivity", "WireGuard config comment filtered out (invalid): $comment")
                    }
                    
                    // Также проверяем весь конфиг (rawConfig) на наличие UUID
                    if (uuidToCheck.isNullOrBlank()) {
                        Log.d("MainActivity", "WireGuard config comment is empty or invalid, checking rawConfig for UUID")
                        // Логируем больше конфига для отладки (первые 1000 символов)
                        val configPreview = parsedConfig.rawConfig.take(1000)
                        Log.d("MainActivity", "WireGuard config preview (first 1000 chars): $configPreview")
                        val uuidFromRawConfig = findUuidInText(parsedConfig.rawConfig)
                        if (uuidFromRawConfig != null) {
                            uuidToCheck = uuidFromRawConfig
                            Log.d("MainActivity", "Found UUID in WireGuard rawConfig: $uuidToCheck")
                        } else {
                            Log.d("MainActivity", "No UUID found in WireGuard rawConfig")
                        }
                    }
                    
                    // Если UUID не найден в примечании, проверяем имя конфига
                    if (uuidToCheck.isNullOrBlank() && !name.isNullOrBlank()) {
                        Log.d("MainActivity", "WireGuard config name: $name, trying to extract UUID or email")
                        val uuidFromName = findUuidInText(name)
                        if (uuidFromName != null) {
                            uuidToCheck = uuidFromName
                            Log.d("MainActivity", "Found UUID in WireGuard config name: $uuidToCheck")
                        } else {
                            // Пытаемся найти email в имени
                            val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                            val emailMatch = emailPattern.find(name)
                            if (emailMatch != null) {
                                val email = emailMatch.value
                                Log.d("MainActivity", "Found email in WireGuard config name: $email")
                            }
                        }
                    }
                    
                    // Также проверяем сохраненное имя конфига из SharedPreferences
                    if (uuidToCheck.isNullOrBlank()) {
                        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                        val savedWireGuardConfigName = prefs.getString("saved_wireguard_config_name", null)
                        Log.d("MainActivity", "Checking SharedPreferences for saved WireGuard config name...")
                        Log.d("MainActivity", "saved_wireguard_config_name: $savedWireGuardConfigName")
                        
                        // Проверяем также список сохраненных конфигов
                        val savedWireGuardConfigsList = prefs.getString("saved_wireguard_configs_list", null)
                        Log.d("MainActivity", "saved_wireguard_configs_list: ${savedWireGuardConfigsList?.take(500)}")
                        
                        if (!savedWireGuardConfigName.isNullOrBlank()) {
                            Log.d("MainActivity", "Checking saved WireGuard config name: $savedWireGuardConfigName")
                            val uuidFromSaved = findUuidInText(savedWireGuardConfigName)
                            if (uuidFromSaved != null) {
                                uuidToCheck = uuidFromSaved
                                Log.d("MainActivity", "Found UUID in saved WireGuard config name: $uuidToCheck")
                            } else {
                                // Проверяем на email в сохраненном имени
                                val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                                val emailMatch = emailPattern.find(savedWireGuardConfigName)
                                if (emailMatch != null) {
                                    val email = emailMatch.value
                                    Log.d("MainActivity", "Found email in saved WireGuard config name: $email")
                                    // TODO: Поиск UUID по email через API
                                }
                            }
                        } else {
                            Log.d("MainActivity", "No saved WireGuard config name found in SharedPreferences")
                        }
                        
                        Log.d("MainActivity", "=== End WireGuard Config Analysis ===")
                        Log.d("MainActivity", "Summary: uuidToCheck=$uuidToCheck, commentToUse=$commentToUse")
                    }
                }
                
                // Логируем результат поиска UUID
                Log.d("MainActivity", "=== UUID Search Result ===")
                Log.d("MainActivity", "uuidToCheck: ${uuidToCheck ?: "null"}")
                Log.d("MainActivity", "parsedConfig.protocol: ${parsedConfig?.protocol}")
                Log.d("MainActivity", "=== End UUID Search Result ===")
                
                if (parsedConfig != null && !uuidToCheck.isNullOrBlank()) {
                    Log.d("MainActivity", "=== Checking subscription by UUID (for ${parsedConfig.protocol}) ===")
                    Log.d("MainActivity", "UUID: $uuidToCheck")
                    
                    // Проверка через API
                    var subscriptionInfo = apiClient.checkSubscription(uuidToCheck)
                    
                    Log.d("MainActivity", "=== SUBSCRIPTION CHECK RESULT ===")
                    Log.d("MainActivity", "Subscription info received: $subscriptionInfo")
                    Log.d("MainActivity", "Current subscriptionInfo: $subscriptionInfo")
                    lifecycleScope.launch(Dispatchers.Main) {
                        // Обновляем подписку только если получили валидные данные
                        // При ошибке (null) не очищаем уже сохраненные данные
                        if (subscriptionInfo != null) {
                            Log.d("MainActivity", "Saving subscription info: $subscriptionInfo")
                            saveSubscriptionInfo(subscriptionInfo)
                            Log.d("MainActivity", "✓ Subscription info updated: ${subscriptionInfo.format()}")
                            Log.d("MainActivity", "  subscriptionInfoState.value: ${subscriptionInfoState.value?.format()}")
                            Log.d("MainActivity", "  subscriptionInfo: ${this@MainActivity.subscriptionInfo?.format()}")
                        } else {
                            Log.w("MainActivity", "✗ Subscription check returned null")
                            Log.w("MainActivity", "Keeping existing subscription info: ${this@MainActivity.subscriptionInfo?.format()}")
                            Log.w("MainActivity", "  subscriptionInfoState.value: ${subscriptionInfoState.value?.format()}")
                        }
                        Log.d("MainActivity", "=== END SUBSCRIPTION CHECK ===")
                    }
                } else {
                    Log.d("MainActivity", "=== No UUID found, checking alternative methods ===")
                    Log.d("MainActivity", "parsedConfig: ${if (parsedConfig != null) "exists" else "null"}")
                    if (parsedConfig != null) {
                        Log.d("MainActivity", "parsedConfig.protocol: ${parsedConfig.protocol}")
                        Log.d("MainActivity", "parsedConfig.comment: ${parsedConfig.comment}")
                        Log.d("MainActivity", "parsedConfig.name: ${parsedConfig.name}")
                    }
                    
                    // Если UUID не найден, но есть комментарий для WireGuard конфигов,
                    // пытаемся проверить подписку по комментарию/имени клиента
                    if (parsedConfig != null && parsedConfig.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                        val comment = parsedConfig.comment
                        Log.d("MainActivity", "=== WireGuard config detected, checking comment ===")
                        Log.d("MainActivity", "Comment value: $comment")
                        Log.d("MainActivity", "Comment isNullOrBlank: ${comment.isNullOrBlank()}")
                        if (!comment.isNullOrBlank()) {
                            Log.d("MainActivity", "Comment trimmed: ${comment.trim()}")
                            Log.d("MainActivity", "Comment != '-1': ${comment.trim() != "-1"}")
                            Log.d("MainActivity", "Comment is numeric: ${comment.trim().matches(Regex("^-?\\d+$"))}")
                        }
                        
                        if (!comment.isNullOrBlank() && comment.trim() != "-1" && !comment.trim().matches(Regex("^-?\\d+$"))) {
                            Log.d("MainActivity", "=== Checking subscription by comment (for WireGuard) ===")
                            Log.d("MainActivity", "Comment/Name: $comment")
                            Log.d("MainActivity", "Comment trimmed: ${comment.trim()}")
                            Log.d("MainActivity", "Note: This is a separate check from UUID-based subscription (Vless)")
                            Log.d("MainActivity", "Will call apiClient.checkSubscriptionByComment(\"${comment.trim()}\")")
                            
                            // Пытаемся проверить подписку по комментарию
                            // Мы уже в lifecycleScope.launch(Dispatchers.IO), поэтому используем withContext
                            try {
                                Log.d("MainActivity", "Calling apiClient.checkSubscriptionByComment(\"${comment.trim()}\")...")
                                val subscriptionInfo = apiClient.checkSubscriptionByComment(comment.trim())
                                Log.d("MainActivity", "API call completed. Result: ${subscriptionInfo?.format() ?: "null"}")
                                
                                withContext(Dispatchers.Main) {
                                    if (subscriptionInfo != null) {
                                        Log.d("MainActivity", "=== SUBSCRIPTION CHECK RESULT (WireGuard by comment) ===")
                                        Log.d("MainActivity", "✓ Subscription found by comment: ${subscriptionInfo.format()}")
                                        Log.d("MainActivity", "Saving subscription info for WireGuard config...")
                                        saveSubscriptionInfo(subscriptionInfo)
                                        Log.d("MainActivity", "  subscriptionInfoState.value: ${subscriptionInfoState.value?.format()}")
                                        Log.d("MainActivity", "  subscriptionInfo: ${this@MainActivity.subscriptionInfo?.format()}")
                                        Log.d("MainActivity", "=== END SUBSCRIPTION CHECK (WireGuard) ===")
                                    } else {
                                        Log.w("MainActivity", "=== SUBSCRIPTION CHECK RESULT (WireGuard by comment) ===")
                                        Log.w("MainActivity", "✗ Subscription not found by comment: $comment")
                                        Log.w("MainActivity", "WireGuard config: UUID not found in comment, rawConfig, name, or saved config name")
                                        Log.w("MainActivity", "Cannot check subscription without UUID. Config may need UUID in comment or name.")
                                        Log.w("MainActivity", "Searched locations: comment=$comment, name=${parsedConfig.name}")
                                        Log.w("MainActivity", "=== END SUBSCRIPTION CHECK (WireGuard) ===")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "=== ERROR: Failed to check subscription by comment ===", e)
                                Log.e("MainActivity", "Exception type: ${e.javaClass.simpleName}")
                                Log.e("MainActivity", "Exception message: ${e.message}")
                                withContext(Dispatchers.Main) {
                                    Log.w("MainActivity", "WireGuard config: UUID not found, subscription check by comment failed")
                                    Log.w("MainActivity", "Searched locations: comment=$comment, name=${parsedConfig.name}")
                                }
                            }
                        } else {
                            Log.w("MainActivity", "=== Comment validation failed ===")
                            Log.w("MainActivity", "Comment isNullOrBlank: ${comment.isNullOrBlank()}")
                            if (!comment.isNullOrBlank()) {
                                Log.w("MainActivity", "Comment trimmed: ${comment.trim()}")
                                Log.w("MainActivity", "Comment == '-1': ${comment.trim() == "-1"}")
                                Log.w("MainActivity", "Comment is numeric: ${comment.trim().matches(Regex("^-?\\d+$"))}")
                            }
                            
                            // Fallback: попробуем использовать приватный ключ для проверки подписки
                            val privateKey = parsedConfig.privateKey
                            if (!privateKey.isNullOrBlank()) {
                                Log.d("MainActivity", "=== Trying subscription check by private key (WireGuard fallback) ===")
                                Log.d("MainActivity", "PrivateKey: ${privateKey.take(20)}...")
                                
                                try {
                                    val subscriptionInfo = apiClient.checkSubscriptionByComment(privateKey)
                                    Log.d("MainActivity", "API call completed. Result: ${subscriptionInfo?.format() ?: "null"}")
                                    
                                    withContext(Dispatchers.Main) {
                                        if (subscriptionInfo != null) {
                                            Log.d("MainActivity", "=== SUBSCRIPTION CHECK RESULT (WireGuard by private key) ===")
                                            Log.d("MainActivity", "✓ Subscription found by private key: ${subscriptionInfo.format()}")
                                            saveSubscriptionInfo(subscriptionInfo)
                                            Log.d("MainActivity", "=== END SUBSCRIPTION CHECK (WireGuard) ===")
                                        } else {
                                            Log.w("MainActivity", "=== SUBSCRIPTION CHECK RESULT (WireGuard by private key) ===")
                                            Log.w("MainActivity", "✗ Subscription not found by private key")
                                            Log.w("MainActivity", "WireGuard config: UUID not found in comment, rawConfig, name, or saved config name")
                                            Log.w("MainActivity", "Cannot check subscription without UUID. Config may need UUID in comment or name.")
                                            Log.w("MainActivity", "Searched locations: comment=$comment, name=${parsedConfig.name}, privateKey=${privateKey.take(20)}...")
                                            Log.w("MainActivity", "=== END SUBSCRIPTION CHECK (WireGuard) ===")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "=== ERROR: Failed to check subscription by private key ===", e)
                                    Log.e("MainActivity", "Exception type: ${e.javaClass.simpleName}")
                                    Log.e("MainActivity", "Exception message: ${e.message}")
                                    withContext(Dispatchers.Main) {
                                        Log.w("MainActivity", "WireGuard config: UUID not found, subscription check by private key failed")
                                        Log.w("MainActivity", "Searched locations: comment=$comment, name=${parsedConfig.name}, privateKey=${privateKey.take(20)}...")
                                    }
                                }
                            } else {
                                Log.w("MainActivity", "WireGuard config: UUID not found in comment, rawConfig, name, or saved config name")
                                Log.w("MainActivity", "Cannot check subscription without UUID. Config may need UUID in comment or name.")
                                Log.w("MainActivity", "Searched locations: comment=$comment, name=${parsedConfig.name}")
                            }
                        }
                    } else {
                        Log.w("MainActivity", "=== Not a WireGuard config or parsedConfig is null ===")
                        Log.w("MainActivity", "parsedConfig: ${if (parsedConfig != null) "exists, protocol=${parsedConfig.protocol}" else "null"}")
                        if (parsedConfig != null && parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                            Log.w("MainActivity", "This is ${parsedConfig.protocol} config, not WireGuard")
                        }
                    Log.w("MainActivity", "Config has no UUID, cannot check subscription")
                    }
                    Log.d("MainActivity", "=== End alternative methods check ===")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to check subscription, keeping existing subscription info: ${subscriptionInfo?.format()}", e)
            }
        }
    }
    
    /**
     * Получение сохраненного конфига
     */
    private fun getSavedConfig(): String? {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        // Проверяем, какой конфиг активен (последний выбранный)
        val activeConfigType = prefs.getString("active_config_type", null) // "vless" или "wireguard"
        
        val vlessConfig = prefs.getString("saved_config", null)
        val wireGuardConfig = prefs.getString("saved_wireguard_config", null)
        
        Log.d("MainActivity", "getSavedConfig(): activeConfigType = $activeConfigType, vless exists = ${!vlessConfig.isNullOrBlank()}, wireguard exists = ${!wireGuardConfig.isNullOrBlank()}")
        
        // СТРОГАЯ проверка activeConfigType - если он установлен, используем его
        if (activeConfigType == "wireguard") {
            // Если активен WireGuard, возвращаем его конфиг
            if (!wireGuardConfig.isNullOrBlank()) {
                Log.d("MainActivity", "getSavedConfig(): Returning WireGuard config (activeConfigType = wireguard)")
                return wireGuardConfig
            } else {
                Log.w("MainActivity", "getSavedConfig(): WireGuard config is null or blank, but activeConfigType = wireguard!")
                // Если WireGuard конфига нет, но activeConfigType = wireguard, не возвращаем Vless!
                // Это ошибка состояния - пользователь выбрал WireGuard, но конфига нет
                return null
            }
        }
        
        // Если activeConfigType == "vless" или null, возвращаем Vless конфиг
        if (activeConfigType == null || activeConfigType == "vless") {
            if (!vlessConfig.isNullOrBlank()) {
                Log.d("MainActivity", "getSavedConfig(): Returning Vless config (activeConfigType = $activeConfigType)")
                return vlessConfig
            } else {
                Log.w("MainActivity", "getSavedConfig(): Vless config is null or blank, activeConfigType = $activeConfigType")
                // Если Vless конфига нет, но activeConfigType = vless или null, возвращаем WireGuard как запасной вариант
                if (!wireGuardConfig.isNullOrBlank()) {
                    Log.d("MainActivity", "getSavedConfig(): No Vless config, returning WireGuard as fallback")
                    return wireGuardConfig
                }
            }
        }
        
        Log.w("MainActivity", "getSavedConfig(): No saved configs found")
        return null
    }
    
    /**
     * Сохранение информации о подписке
     */
    private fun saveSubscriptionInfo(info: SubscriptionInfo) {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("subscription_days", info.days)
            .putInt("subscription_hours", info.hours)
            .putBoolean("subscription_unlimited", info.unlimited)
            .putBoolean("subscription_expired", info.expired)
            .apply()
        subscriptionInfo = info
        subscriptionInfoState.value = info
        Log.d("MainActivity", "✓ Subscription info saved: ${info.format()}")
        
        // Обновляем уведомление VPN сервиса, если VPN подключен
        if (viewModel.connectionStatus.value.isConnected) {
            try {
                val serviceIntent = Intent(this, KizVpnService::class.java)
                val binder = bindService(serviceIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val vpnService = (service as? KizVpnService.KizVpnBinder)?.getService()
                        vpnService?.setSubscriptionInfo(info.days, info.hours)
                        unbindService(this)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }, Context.BIND_AUTO_CREATE)
                Log.d("MainActivity", "Updating VPN notification with subscription info")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to update VPN notification", e)
            }
        }
    }
    
    /**
     * Загрузка сохраненной информации о подписке
     */
    private fun loadSubscriptionInfo() {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        if (prefs.contains("subscription_days") || prefs.contains("subscription_hours")) {
            val days = prefs.getInt("subscription_days", 0)
            val hours = prefs.getInt("subscription_hours", 0)
            val unlimited = prefs.getBoolean("subscription_unlimited", false)
            val expired = prefs.getBoolean("subscription_expired", false)
            subscriptionInfo = SubscriptionInfo(days, hours, unlimited, expired)
            subscriptionInfoState.value = subscriptionInfo
            Log.d("MainActivity", "Loaded subscription info: ${subscriptionInfo?.format()}")
        } else {
            subscriptionInfo = null
            subscriptionInfoState.value = null
            Log.d("MainActivity", "No subscription info found in preferences")
        }
    }
    
    /**
     * Запуск периодического обновления подписки (каждые 12 часов, как в настройках 3x-ui)
     */
    private fun startSubscriptionUpdateJob() {
        // Отменяем предыдущий job, если есть
        subscriptionUpdateJob?.cancel()
        
        subscriptionUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    // Ждем 12 часов (43200000 миллисекунд)
                    delay(12 * 60 * 60 * 1000L)
                    
                    // Проверяем подписку, если есть сохраненный конфиг
                    val savedConfig = getSavedConfig()
                    if (!savedConfig.isNullOrBlank()) {
                        Log.d("MainActivity", "Периодическое обновление подписки (каждые 12 часов)")
                        checkSubscriptionFromConfig(savedConfig)
                    } else {
                        Log.d("MainActivity", "Пропуск периодического обновления: нет сохраненного конфига")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Корректная отмена корутины - просто выходим
                Log.d("MainActivity", "Периодическое обновление подписки отменено")
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка при периодическом обновлении подписки", e)
                // При ошибке ждем еще час перед следующей попыткой
                delay(60 * 60 * 1000L)
            }
        }
        
        Log.d("MainActivity", "Запущено периодическое обновление подписки (каждые 12 часов)")
    }
    
    /**
     * Активация ключа через API
     */
    private fun activateKey(key: String, callback: (Int?) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Общий таймаут на всю активацию - 10 секунд
            try {
                withTimeout(10000) { // 10 секунд максимум на всю активацию
                Log.d("MainActivity", "Activating key/config: ${key.take(50)}...")
                
                // Проверяем, является ли введенный текст конфигом
                val parsedConfig = configParser.parseConfig(key.trim())
                
                if (parsedConfig != null && parsedConfig.uuid != null) {
                    // Это конфиг - сохраняем его и проверяем подписку по UUID
                    Log.d("MainActivity", "Detected config with UUID: ${parsedConfig.uuid}, protocol: ${parsedConfig.protocol}")
                    saveConfig(key.trim()) // saveConfig уже устанавливает active_config_type
                    
                    // Сохраняем флаг активации конфига
                    val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("config_activated", false).apply()
                    
                    // Проверяем подписку по UUID из конфига
                    Log.d("MainActivity", "Checking subscription for UUID: ${parsedConfig.uuid}")
                    val subscriptionInfo = apiClient.checkSubscription(parsedConfig.uuid)
                    Log.d("MainActivity", "Subscription check completed, result: ${subscriptionInfo?.format() ?: "null"}")
                    
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (subscriptionInfo != null && !subscriptionInfo.expired && (subscriptionInfo.days > 0 || subscriptionInfo.hours > 0 || subscriptionInfo.unlimited)) {
                            Log.d("MainActivity", "Subscription found: ${subscriptionInfo.format()}")
                            saveSubscriptionInfo(subscriptionInfo)
                            // Помечаем конфиг как активированный и устанавливаем тип активного конфига
                            val configType = when (parsedConfig.protocol) {
                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                                else -> "vless"
                            }
                            prefs.edit()
                                .putBoolean("config_activated", true)
                                .putString("active_config_type", configType)
                                .commit() // Используем commit() для синхронного сохранения
                            val days = subscriptionInfo.days
                            callback(days)
                        } else {
                            Log.w("MainActivity", "Subscription not found or expired for UUID: ${parsedConfig.uuid}")
                            // Пробуем активировать UUID как ключ
                            try {
                                val days = apiClient.activateKey(parsedConfig.uuid)
                                if (days != null && days > 0) {
                                    saveSubscriptionInfo(SubscriptionInfo.fromDays(days))
                                    // Помечаем конфиг как активированный и устанавливаем тип активного конфига
                                    val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                                    val configType = when (parsedConfig.protocol) {
                                        com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                                        com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                                        else -> "vless"
                                    }
                                    prefs.edit()
                                        .putBoolean("config_activated", true)
                                        .putString("active_config_type", configType)
                                        .commit() // Используем commit() для синхронного сохранения
                                    callback(days)
                                } else {
                                    callback(null)
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to activate UUID as key", e)
                                callback(null)
                            }
                        }
                    }
                } else {
                    // Это не конфиг - пробуем как ключ активации
                    Log.d("MainActivity", "Treating input as activation key")
                val days = apiClient.activateKey(key)
                Log.d("MainActivity", "Activation response: days=$days")
                lifecycleScope.launch(Dispatchers.Main) {
                    if (days != null && days > 0) {
                            Log.d("MainActivity", "Saving subscription info: $days days")
                            saveSubscriptionInfo(SubscriptionInfo.fromDays(days))
                        // Также проверяем подписку из конфига, если он есть
                        val savedConfig = getSavedConfig()
                        if (!savedConfig.isNullOrBlank()) {
                            Log.d("MainActivity", "Rechecking subscription from saved config")
                            checkSubscriptionFromConfig(savedConfig)
                        }
                        callback(days)
                    } else {
                        Log.w("MainActivity", "Activation returned null or 0 days")
                        callback(null)
                        }
                    }
                }
                } // конец withTimeout
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("MainActivity", "Activation timeout (10s)")
                lifecycleScope.launch(Dispatchers.Main) {
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to activate key", e)
                lifecycleScope.launch(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Вставка конфига из буфера обмена
     */
    fun pasteFromClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text.toString()
            return text
        } else {
            Toast.makeText(this, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            return null
        }
    }
    
    /**
     * Compose функция для навигации с интеграцией VPN
     */
    @Composable
    private fun AppNavHostComposable() {
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val vpnConfig by viewModel.vpnConfig.collectAsState()
        val isDarkTheme by viewModel.isDarkTheme.collectAsState()
        val trafficData by viewModel.trafficData.collectAsState()
        
        var currentConfigInput by remember { mutableStateOf(vpnConfig ?: "") }
        val subscriptionInfo by subscriptionInfoState
        val configNotification by configNotificationState
        
        // Автоматическая проверка подписки при изменении конфига
        LaunchedEffect(vpnConfig) {
            vpnConfig?.let { config ->
                if (config.isNotBlank()) {
                    checkSubscriptionFromConfig(config)
                }
            }
        }
        
        AppNavHost(
            onConnectClick = {
                if (connectionStatus.isConnected) {
                    disconnectFromVpn()
                } else {
                    // ВСЕГДА используем getSavedConfig(), который правильно учитывает active_config_type
                    // Это гарантирует, что используется правильный конфиг (Vless или WireGuard)
                    val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                    val activeConfigType = prefs.getString("active_config_type", null)
                    Log.d("MainActivity", "onConnectClick: active_config_type = $activeConfigType")
                    
                    val config = getSavedConfig()
                    
                    Log.d("MainActivity", "onConnectClick: Checking if config exists...")
                    if (!config.isNullOrBlank()) {
                        Log.d("MainActivity", "onConnectClick: Config found, length=${config.length}")
                        // Проверяем тип конфига
                        val parsedConfig = configParser.parseConfig(config)
                        val actualConfigType = when (parsedConfig?.protocol) {
                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                            else -> "unknown"
                        }
                        Log.d("MainActivity", "onConnectClick: Connecting with config type = $activeConfigType, actual protocol = $actualConfigType, config = ${config.take(100)}...")
                        
                        // Если activeConfigType не совпадает с actualConfigType, это проблема!
                        if (actualConfigType != "unknown" && activeConfigType != actualConfigType) {
                            Log.e("MainActivity", "🚨 КРИТИЧЕСКАЯ ОШИБКА в onConnectClick: activeConfigType = $activeConfigType, но actualConfigType = $actualConfigType")
                            Log.e("MainActivity", "🚨 Исправляем activeConfigType на $actualConfigType")
                            // Исправляем activeConfigType
                            prefs.edit().putString("active_config_type", actualConfigType).commit()
                            
                            // Также сохраняем конфиг в правильное место
                            // ВАЖНО: НЕ удаляем конфиги другого типа - они должны сохраняться независимо
                            when (parsedConfig?.protocol) {
                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                                    prefs.edit()
                                        .putString("saved_config", config)
                                        .commit()
                                    Log.d("MainActivity", "onConnectClick: Сохранен Vless конфиг в saved_config")
                                }
                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                                    prefs.edit()
                                        .putString("saved_wireguard_config", config)
                                        .commit()
                                    Log.d("MainActivity", "onConnectClick: Сохранен WireGuard конфиг в saved_wireguard_config")
                                }
                                else -> {}
                            }
                        }
                        
                        // Обновляем currentConfigInput и ViewModel
                        currentConfigInput = config
                        viewModel.setVpnConfig(config)
                        
                        // Проверяем подписку перед подключением
                        checkSubscriptionFromConfig(config)
                        connectToVpn(config)
                    } else {
                        Log.w("MainActivity", "onConnectClick: No config found! active_config_type = $activeConfigType")
                        // Показываем уведомление вверху экрана
                        configNotificationState.value = "Вставьте конфиг"
                        lifecycleScope.launch {
                            delay(3000)
                            configNotificationState.value = null
                        }
                    }
                }
            },
            onDisconnectClick = {
                disconnectFromVpn()
            },
            connectionStatus = connectionStatus,
            subscriptionInfo = subscriptionInfo,
            configNotification = configNotification,
            onShowConfigNotification = { message ->
                // Устанавливаем уведомление
                configNotificationState.value = message
                // Автоматически скрываем через 3 секунды
                lifecycleScope.launch {
                    delay(3000)
                    configNotificationState.value = null
                }
            },
            trafficData = trafficData,
            vpnConfig = currentConfigInput,
            onConfigChange = { config ->
                currentConfigInput = config
                viewModel.setVpnConfig(config)
                // Проверяем подписку при изменении конфига
                if (config.isNotBlank()) {
                    checkSubscriptionFromConfig(config)
                }
            },
            viewModel = viewModel,
            onActivateKey = { key, callback ->
                activateKey(key, callback)
            },
            onSaveWireGuardConfig = { config ->
                // Сохраняем WireGuard конфиг отдельно
                // ВАЖНО: НЕ удаляем Vless конфиг - они должны сохраняться независимо
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("saved_wireguard_config", config)
                    .putString("active_config_type", "wireguard") // Устанавливаем WireGuard как активный
                    .commit() // Используем commit() для синхронного сохранения
                Log.d("MainActivity", "onSaveWireGuardConfig: Saved WireGuard config, set active_config_type = wireguard")
                currentConfigInput = config
                viewModel.setVpnConfig(config)
            },
            onSelectConfig = { config, protocol ->
                // При выборе конфига из списка, устанавливаем его как текущий
                Log.d("MainActivity", "onSelectConfig: protocol = $protocol, config = ${config.take(100)}...")
                
                // Парсим конфиг еще раз для проверки
                val parsedConfig = configParser.parseConfig(config)
                val actualProtocol = parsedConfig?.protocol
                Log.d("MainActivity", "onSelectConfig: parsedProtocol = $actualProtocol")
                
                // Если протокол не совпадает, это критическая ошибка!
                if (actualProtocol != null && actualProtocol != protocol) {
                    Log.e("MainActivity", "🚨 КРИТИЧЕСКАЯ ОШИБКА в onSelectConfig: передан protocol = $protocol, но parsedProtocol = $actualProtocol")
                    Log.e("MainActivity", "🚨 Используем actualProtocol = $actualProtocol (из парсера)")
                }
                
                // Используем протокол из парсера, если он доступен, иначе используем переданный
                val protocolToUse = actualProtocol ?: protocol
                Log.d("MainActivity", "onSelectConfig: protocolToUse = $protocolToUse")
                
                // Сохраняем тип активного конфига ПЕРВЫМ ДЕЛОМ СИНХРОННО
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val configType = when (protocolToUse) {
                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                    else -> "vless"
                }
                
                // Также сохраняем сам конфиг в правильное место
                // ВАЖНО: НЕ удаляем конфиги другого типа - они должны сохраняться независимо
                when (protocolToUse) {
                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                        prefs.edit()
                            .putString("saved_config", config)
                            .putString("active_config_type", configType)
                            .commit()
                        Log.d("MainActivity", "onSelectConfig: Сохранен Vless конфиг в saved_config")
                    }
                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                        prefs.edit()
                            .putString("saved_wireguard_config", config)
                            .putString("active_config_type", configType)
                            .commit()
                        Log.d("MainActivity", "onSelectConfig: Сохранен WireGuard конфиг в saved_wireguard_config")
                    }
                    else -> {
                        prefs.edit().putString("active_config_type", configType).commit()
                    }
                }
                
                Log.d("MainActivity", "onSelectConfig: Set active_config_type = $configType (committed)")
                
                // Проверяем, что active_config_type действительно сохранился
                val savedConfigType = prefs.getString("active_config_type", null)
                Log.d("MainActivity", "onSelectConfig: Verified active_config_type = $savedConfigType")
                
                // Устанавливаем конфиг как текущий
                currentConfigInput = config
                viewModel.setVpnConfig(config)
                
                // Проверяем подписку при изменении конфига
                if (config.isNotBlank()) {
                    checkSubscriptionFromConfig(config)
                }
            },
        )
    }
    
}







