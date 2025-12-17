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
import com.kizvpn.client.util.Logger
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
    // VpnApiClient - универсальный клиент для парсинга Subscription URL
    // Работает с любыми VPN серверами без привязки к конкретным API
    private val apiClient = VpnApiClient()
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
    
    // Флаг для предотвращения множественных одновременных операций подключения/отключения
    private val isVpnOperationInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Флаг для предотвращения автоподключения после ручного отключения
    private var wasManuallyDisconnected = false
    
    // Job для обновления статистики (чтобы можно было отменить)
    private var statsUpdateJob: kotlinx.coroutines.Job? = null
    
    // Job для периодического обновления подписки при включенном VPN (раз в час)
    private var subscriptionUpdateJob: kotlinx.coroutines.Job? = null
    private var lastSubscriptionCheckTime: Long = 0 // Время последней проверки подписки
    
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
        
        // Периодическое обновление подписки отключено - проверка только при включении VPN
        
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
        // Проверяем статус VPN при возврате в приложение (только если не было ручного отключения)
        // После ручного отключения не проверяем статус сразу, чтобы избежать переподключения
        if (!wasManuallyDisconnected) {
            checkVpnStatus()
        } else {
            // Сбрасываем флаг через некоторое время, чтобы автоподключение снова заработало при следующем запуске
            lifecycleScope.launch {
                delay(5000) // Ждем 5 секунд после ручного отключения
                wasManuallyDisconnected = false
            }
        }
        
        // Начинаем проверку подключения и обновление статистики, если VPN подключен
        lifecycleScope.launch {
            viewModel.connectionStatus.collect { status ->
                if (status.isConnected) {
                    if (statsUpdateJob?.isActive != true) {
                        statsUpdateJob = startStatsUpdate()
                    }
                    // Запускаем периодическое обновление подписки (раз в час) только если VPN подключен
                    if (subscriptionUpdateJob?.isActive != true) {
                        startSubscriptionUpdateJob()
                    }
                } else {
                    statsUpdateJob?.cancel()
                    statsUpdateJob = null
                    subscriptionUpdateJob?.cancel()
                    subscriptionUpdateJob = null
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
        statsUpdateJob = null
        subscriptionUpdateJob?.cancel()
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
            Logger.vpnError(Logger.Tag.MAIN, "Config is blank")
            showNotification("Вставьте конфиг в настройках VPN")
            return
        }
        
        val parsedConfig = configParser.parseConfig(configString)
        if (parsedConfig == null) {
            Logger.configError(Logger.Tag.MAIN, "Invalid config format")
            showNotification("Неверный формат конфига")
            return
        }
        
        // Проверяем поддержку протокола
        if (parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.VLESS && 
            parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
            Logger.configError(Logger.Tag.MAIN, "Unsupported protocol: ${parsedConfig.protocol}")
            showNotification("Протокол ${parsedConfig.protocol} не поддерживается. Используйте VLESS или WireGuard", 4000)
            return
        }
        
        // Проверяем разрешение VPN
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Logger.info(Logger.Tag.MAIN, "VPN permission not granted, requesting...")
            vpnPermissionLauncher.launch(intent)
        } else {
            Logger.vpnConnecting(Logger.Tag.MAIN, "VPN permission granted, starting connection...")
            startVpnConnection(configString)
        }
    }
    
    /**
     * Показать уведомление пользователю (универсальный метод)
     */
    private fun showNotification(message: String, duration: Long = 8000) {
        configNotificationState.value = message
        lifecycleScope.launch {
            delay(duration)
            configNotificationState.value = null
        }
    }
    
    /**
     * Запуск VPN соединения
     */
    private fun startVpnConnection(configString: String) {
        // Защита от множественных одновременных вызовов
        if (!isVpnOperationInProgress.compareAndSet(false, true)) {
            Log.d("MainActivity", "startVpnConnection: Operation already in progress, ignoring")
            return
        }
        
        Logger.vpnConnecting(Logger.Tag.MAIN, "Starting VPN connection...")
        
        // Обновляем состояние СИНХРОННО ПЕРЕД запуском асинхронной операции
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
                
                Logger.info(Logger.Tag.MAIN, "Starting VPN service with config length: ${configString.length}")
                startForegroundService(serviceIntent)
                
                // Ждем установления соединения (увеличено до 3 секунд для надежности)
                delay(3000)
                
                // Проверяем реальный статус VPN сервиса
                val isServiceRunning = checkVpnServiceRunning()
                if (isServiceRunning) {
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
                    
                    // Освобождаем флаг после успешного подключения
                    isVpnOperationInProgress.set(false)
                    
                    // Сбрасываем флаг ручного отключения после успешного подключения
                    wasManuallyDisconnected = false
                    
                    Logger.vpnConnected(Logger.Tag.MAIN, "VPN connected successfully")
                    
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
                    
                    // Проверяем подписку после успешного подключения, чтобы показать уведомление
                    // Устанавливаем флаг для показа уведомления о подписке
                    shouldShowSubscriptionNotification = true
                    lifecycleScope.launch {
                        // Небольшая задержка, чтобы уведомление о подключении успело показаться
                        delay(1500)
                        checkSubscriptionFromConfig(configString)
                    }
                } else {
                    Logger.vpnError(Logger.Tag.MAIN, "VPN service not running after start")
                    viewModel.updateConnectionStatus(
                        viewModel.connectionStatus.value.copy(
                            isConnected = false,
                            isConnecting = false
                        )
                    )
                    showNotification("Ошибка: VPN сервис не запустился", 4000)
                    // Освобождаем флаг при ошибке
                    isVpnOperationInProgress.set(false)
                }
                
            } catch (e: Exception) {
                Logger.vpnError(Logger.Tag.MAIN, "Error starting VPN: ${e.message}", e)
                viewModel.updateConnectionStatus(
                    viewModel.connectionStatus.value.copy(
                        isConnected = false,
                        isConnecting = false
                    )
                )
                showNotification("Ошибка подключения: ${e.message ?: "неизвестная ошибка"}", 4000)
                // Освобождаем флаг при ошибке
                isVpnOperationInProgress.set(false)
            }
        }
    }
    
    /**
     * Проверка, запущен ли VPN сервис
     */
    private fun checkVpnServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            runningServices.any { service ->
                service.service.className == KizVpnService::class.java.name
            }
        } catch (e: Exception) {
            Logger.error(Logger.Tag.MAIN, "Error checking VPN service status", e)
            false
        }
    }
    
    /**
     * Отключение от VPN
     */
    private fun disconnectFromVpn() {
        Logger.vpnDisconnecting(Logger.Tag.MAIN, "Disconnecting VPN...")
        
        // Устанавливаем флаг ручного отключения, чтобы предотвратить автоподключение
        wasManuallyDisconnected = true
        
        // Отключение имеет приоритет - всегда разрешаем отключение
        // Но защищаем от множественных одновременных вызовов
        if (!isVpnOperationInProgress.compareAndSet(false, true)) {
            Logger.warn(Logger.Tag.MAIN, "disconnectFromVpn: Disconnect already in progress, ignoring duplicate call")
            return
        }
        
        lifecycleScope.launch {
            try {
                // СНАЧАЛА отправляем команду STOP сервису
                val serviceIntent = Intent(this@MainActivity, KizVpnService::class.java).apply {
                    action = "com.kizvpn.client.STOP"
                }
                startService(serviceIntent)
                
                Logger.vpnDisconnected(Logger.Tag.MAIN, "VPN stop command sent to service")
                
                // Ждем немного, чтобы сервис успел обработать команду
                delay(500)
                
                // Проверяем, что сервис действительно остановился
                val isServiceRunning = checkVpnServiceRunning()
                if (isServiceRunning) {
                    Logger.warn(Logger.Tag.MAIN, "VPN service still running after stop command, forcing stop")
                    // Принудительно останавливаем сервис
                    stopService(serviceIntent)
                    delay(300)
                }
                
                // ТОЛЬКО ПОСЛЕ фактической остановки обновляем UI состояние
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
                
                // Останавливаем обновление статистики
                statsUpdateJob?.cancel()
                statsUpdateJob = null
                
                // Останавливаем периодическое обновление подписки
                subscriptionUpdateJob?.cancel()
                subscriptionUpdateJob = null
                
                // Не проверяем подписку при отключении VPN - проверка только при включении
                showNotification("VPN отключен", 2000)
                
                Logger.vpnDisconnected(Logger.Tag.MAIN, "VPN disconnected successfully")
                
            } catch (e: Exception) {
                Logger.vpnError(Logger.Tag.MAIN, "Error disconnecting VPN: ${e.message}", e)
                // Даже при ошибке обновляем состояние
                viewModel.updateConnectionStatus(
                    ConnectionStatus(
                        isConnected = false,
                        isConnecting = false
                    )
                )
                showNotification("Ошибка отключения: ${e.message ?: "неизвестная ошибка"}", 4000)
            } finally {
                // Освобождаем флаг после завершения операции
                isVpnOperationInProgress.set(false)
            }
        }
    }
    
    // Добавляем функцию vpnDisconnecting для Logger (если её нет)
    // Используем существующий метод Logger
    
    /**
     * Проверка статуса VPN сервиса
     */
    private fun checkVpnStatus() {
        lifecycleScope.launch {
            try {
                // Если VPN был вручную отключен, не обновляем статус (чтобы избежать переподключения)
                if (wasManuallyDisconnected) {
                    Logger.debug(Logger.Tag.MAIN, "checkVpnStatus: VPN был вручную отключен, пропускаем проверку статуса")
                    return@launch
                }
                
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
                    Logger.debug(Logger.Tag.MAIN, "VPN статус: отключен (сервис не запущен)")
                    return@launch
                }
                
                // Сервис запущен - проверяем, есть ли активный VPN интерфейс
                // Если prepare() возвращает null, VPN уже активен
                val vpnIntent = VpnService.prepare(this@MainActivity)
                
                if (vpnIntent == null) {
                    // VPN активен - обновляем статус только если он еще не установлен как подключенный
                    val currentStatus = viewModel.connectionStatus.value
                    if (!currentStatus.isConnected) {
                        viewModel.updateConnectionStatus(
                            viewModel.connectionStatus.value.copy(
                                isConnected = true,
                                isConnecting = false
                            )
                        )
                        Logger.debug(Logger.Tag.MAIN, "VPN статус: подключен (VpnService.prepare() вернул null)")
                    }
                } else {
                    // Сервис запущен, но VPN интерфейс еще не создан - возможно идет подключение или отключение
                    // Не обновляем статус, чтобы не перезаписать состояние отключения
                    Logger.debug(Logger.Tag.MAIN, "VPN сервис запущен, но интерфейс не создан (prepare() вернул intent)")
                }
            } catch (e: Exception) {
                Logger.error(Logger.Tag.MAIN, "Ошибка при проверке статуса VPN: ${e.message}", e)
                // В случае ошибки не обновляем статус, чтобы не перезаписать текущее состояние
            }
        }
    }
    
    /**
     * Проверка автоподключения при запуске приложения
     */
    private fun checkAutoConnect() {
        lifecycleScope.launch {
            try {
                // Если VPN был вручную отключен, не включаем автоподключение
                if (wasManuallyDisconnected) {
                    Log.d("MainActivity", "Автоподключение пропущено: VPN был вручную отключен")
                    return@launch
                }
                
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
            // Не проверяем подписку при загрузке - проверка только при включении VPN
            Log.d("MainActivity", "Loaded saved config: ${savedConfig.take(50)}... (type: ${getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE).getString("active_config_type", "unknown")})")
        }
    }
    
    /**
     * Проверка подписки на основе конфига
     * Сначала проверяет сохраненный Subscription URL для этого конфига
     * Если Subscription URL есть - всегда использует его для получения актуальных данных
     */
    private fun checkSubscriptionFromConfig(config: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsedConfig = configParser.parseConfig(config)
                Log.d("MainActivity", "Parsed config: uuid=${parsedConfig?.uuid}, protocol=${parsedConfig?.protocol}, name=${parsedConfig?.name}")
                
                // Обновляем время последней проверки
                lastSubscriptionCheckTime = System.currentTimeMillis()
                
                // ПЕРВЫМ ДЕЛОМ проверяем, есть ли сохраненный Subscription URL для этого конфига
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val configKey = parsedConfig?.uuid ?: parsedConfig?.name
                
                if (configKey != null) {
                    val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                    if (!savedSubscriptionUrl.isNullOrBlank()) {
                        Log.d("MainActivity", "checkSubscriptionFromConfig: Found saved subscription URL for config, checking for fresh data...")
                        // Всегда проверяем Subscription URL для получения актуальных данных
                        withContext(Dispatchers.Main) {
                            checkSubscriptionFromUrl(savedSubscriptionUrl)
                        }
                        return@launch // Выходим, так как Subscription URL уже проверили
                    } else {
                        Log.d("MainActivity", "checkSubscriptionFromConfig: No saved subscription URL found for config key: $configKey")
                    }
                }
                
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
                    Log.d("MainActivity", "=== UUID found: $uuidToCheck ===")
                    Log.w("MainActivity", "API subscription check removed - use Subscription URL instead")
                    // API проверка подписки удалена - используйте Subscription URL для проверки подписки
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
                            Log.d("MainActivity", "=== WireGuard comment found: $comment ===")
                            Log.w("MainActivity", "API subscription check by comment removed - use Subscription URL instead")
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
                                Log.d("MainActivity", "=== WireGuard private key found ===")
                                Log.w("MainActivity", "API subscription check by private key removed - use Subscription URL instead")
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
    
    // Флаг для отслеживания, нужно ли показывать уведомление о подписке (только при подключении VPN)
    private var shouldShowSubscriptionNotification = false
    
    /**
     * Сохранение информации о подписке для конкретного конфига
     */
    private fun saveSubscriptionInfoForConfig(info: SubscriptionInfo, configKey: String) {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("subscription_days_$configKey", info.days)
            .putInt("subscription_hours_$configKey", info.hours)
            .putBoolean("subscription_unlimited_$configKey", info.unlimited)
            .putBoolean("subscription_expired_$configKey", info.expired)
        
        // Сохраняем новые поля
        if (info.expiryDate != null) {
            editor.putLong("subscription_expiry_date_$configKey", info.expiryDate)
        } else {
            editor.remove("subscription_expiry_date_$configKey")
        }
        
        if (info.totalTraffic != null) {
            editor.putLong("subscription_total_traffic_$configKey", info.totalTraffic)
        } else {
            editor.remove("subscription_total_traffic_$configKey")
        }
        
        if (info.usedTraffic != null) {
            editor.putLong("subscription_used_traffic_$configKey", info.usedTraffic)
        } else {
            editor.remove("subscription_used_traffic_$configKey")
        }
        
        if (info.remainingTraffic != null) {
            editor.putLong("subscription_remaining_traffic_$configKey", info.remainingTraffic)
        } else {
            editor.remove("subscription_remaining_traffic_$configKey")
        }
        
        if (info.configsCount != null) {
            editor.putInt("subscription_configs_count_$configKey", info.configsCount)
        } else {
            editor.remove("subscription_configs_count_$configKey")
        }
        
        editor.apply()
        Log.d("MainActivity", "✓ Subscription info saved for config $configKey: ${info.format()}")
    }
    
    /**
     * Сохранение информации о подписке
     */
    private fun saveSubscriptionInfo(info: SubscriptionInfo, shouldShowNotification: Boolean = false) {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("subscription_days", info.days)
            .putInt("subscription_hours", info.hours)
            .putBoolean("subscription_unlimited", info.unlimited)
            .putBoolean("subscription_expired", info.expired)
        
        // Сохраняем новые поля
        if (info.expiryDate != null) {
            editor.putLong("subscription_expiry_date", info.expiryDate)
        } else {
            editor.remove("subscription_expiry_date")
        }
        
        if (info.totalTraffic != null) {
            editor.putLong("subscription_total_traffic", info.totalTraffic)
        } else {
            editor.remove("subscription_total_traffic")
        }
        
        if (info.usedTraffic != null) {
            editor.putLong("subscription_used_traffic", info.usedTraffic)
        } else {
            editor.remove("subscription_used_traffic")
        }
        
        if (info.remainingTraffic != null) {
            editor.putLong("subscription_remaining_traffic", info.remainingTraffic)
        } else {
            editor.remove("subscription_remaining_traffic")
        }
        
        editor.apply()
        subscriptionInfo = info
        subscriptionInfoState.value = info
        Log.d("MainActivity", "✓ Subscription info saved: ${info.format()}")
        
        // Показываем уведомление о подписке при сохранении
        // Проверяем безлимитность: unlimited = true ИЛИ expiryDate = 0L или очень маленькое значение
        val isUnlimitedSubscription = info.unlimited || 
                (info.expiryDate != null && (info.expiryDate == 0L || info.expiryDate < 1000000000L))
        
        // Формируем текст с информацией о времени подписки
        val subscriptionText: String = if (isUnlimitedSubscription) {
            "Безлимит"
        } else {
            // Формируем многострочный текст: заголовок по центру, затем дни/часы и трафик
            val header = "Подписка осталась:"
            
            // Проверяем, есть ли данные о подписке
            when {
                info.expiryDate != null && info.expiryDate > 0 -> {
                    val now = System.currentTimeMillis()
                    val timeUntilExpiry = info.expiryDate - now
                    if (timeUntilExpiry > 0) {
                        val days = (timeUntilExpiry / (1000 * 60 * 60 * 24)).toInt()
                        val hours = ((timeUntilExpiry % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
                        
                        // Формируем дни и часы
                        val details = StringBuilder()
                        val daysText = when {
                            days == 1 -> "день"
                            days in 2..4 -> "дня"
                            else -> "дней"
                        }
                        val hoursText = when {
                            hours == 1 -> "час"
                            hours in 2..4 -> "часа"
                            else -> "часов"
                        }
                        
                        when {
                            days > 0 && hours > 0 -> details.append("$days $daysText $hours $hoursText")
                            days > 0 -> details.append("$days $daysText")
                            hours > 0 -> details.append("$hours $hoursText")
                            else -> details.append("Активна")
                        }
                        
                        // Добавляем трафик
                        val trafficText = info.formatTrafficForNotification()
                        if (trafficText != null) {
                            details.append(" • $trafficText")
                        }
                        
                        "$header\n${details.toString()}"
                    } else {
                        // Подписка истекла
                        val trafficText = info.formatTrafficForNotification()
                        if (trafficText != null) {
                            "$header\nИстекла • $trafficText"
                        } else {
                            "$header\nИстекла"
                        }
                    }
                }
                info.days > 0 || info.hours > 0 -> {
                    val days = info.days
                    val hours = info.hours
                    
                    // Формируем дни и часы
                    val details = StringBuilder()
                    val daysText = when {
                        days == 1 -> "день"
                        days in 2..4 -> "дня"
                        else -> "дней"
                    }
                    val hoursText = when {
                        hours == 1 -> "час"
                        hours in 2..4 -> "часа"
                        else -> "часов"
                    }
                    
                    when {
                        days > 0 && hours > 0 -> details.append("$days $daysText $hours $hoursText")
                        days > 0 -> details.append("$days $daysText")
                        hours > 0 -> details.append("$hours $hoursText")
                        else -> details.append("Активна")
                    }
                    
                    // Добавляем трафик
                    val trafficText = info.formatTrafficForNotification()
                    if (trafficText != null) {
                        details.append(" • $trafficText")
                    }
                    
                    "$header\n${details.toString()}"
                }
                else -> {
                    val trafficText = info.formatTrafficForNotification()
                    if (trafficText != null) {
                        "$header\n${info.format()} • $trafficText"
                    } else {
                        "$header\n${info.format()}"
                    }
                }
            }
        }
        // Показываем уведомление только если явно указано или при подключении VPN
        if (shouldShowNotification || shouldShowSubscriptionNotification) {
            showNotification(subscriptionText, 8000)
            shouldShowSubscriptionNotification = false // Сбрасываем флаг после показа
        }
        
        // Проверяем и показываем уведомления о приближающемся окончании подписки
        // НЕ показываем предупреждения для безлимитной подписки (чтобы не перезаписать уведомление "Безлимит")
        if (!isUnlimitedSubscription) {
            checkSubscriptionExpiryNotifications(info)
        }
        
        // Обновляем уведомление VPN сервиса, если VPN подключен
        if (viewModel.connectionStatus.value.isConnected) {
            try {
                val serviceIntent = Intent(this, KizVpnService::class.java)
                val binder = bindService(serviceIntent, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val vpnService = (service as? KizVpnService.KizVpnBinder)?.getService()
                        
                        // Вычисляем актуальные дни/часы от текущего времени, если есть дата окончания
                        var actualDays = info.days
                        var actualHours = info.hours
                        
                        if (info.expiryDate != null) {
                            val now = System.currentTimeMillis()
                            val timeUntilExpiry = info.expiryDate - now
                            
                            if (timeUntilExpiry > 0) {
                                actualDays = (timeUntilExpiry / (1000 * 60 * 60 * 24)).toInt()
                                actualHours = ((timeUntilExpiry % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
                            } else {
                                actualDays = 0
                                actualHours = 0
                            }
                        }
                        
                        vpnService?.setSubscriptionInfo(actualDays, actualHours)
                        unbindService(this)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }, Context.BIND_AUTO_CREATE)
                Log.d("MainActivity", "Updating VPN notification with subscription info: ${info.days} days, ${info.hours} hours")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to update VPN notification", e)
            }
        }
    }
    
    /**
     * Проверка и показ уведомлений о приближающемся окончании подписки
     */
    private fun checkSubscriptionExpiryNotifications(info: SubscriptionInfo) {
        // Проверяем, является ли подписка безлимитной
        val isUnlimitedSubscription = info.unlimited || 
                (info.expiryDate != null && (info.expiryDate == 0L || info.expiryDate < 1000000000L))
        
        // Если подписка безлимитная или истекла, не показываем уведомления
        if (isUnlimitedSubscription || info.expired) {
            return
        }
        
        // Проверяем дату окончания
        val expiryDate = info.expiryDate
        if (expiryDate != null && expiryDate > 0) {
            val now = System.currentTimeMillis()
            val timeUntilExpiry = expiryDate - now
            
            // Если подписка уже истекла
            if (timeUntilExpiry <= 0) {
                showNotification("⚠️ Подписка истекла", 5000)
                return
            }
            
            // Вычисляем дни и часы до окончания
            val daysUntilExpiry = (timeUntilExpiry / (1000 * 60 * 60 * 24)).toInt()
            val hoursUntilExpiry = (timeUntilExpiry / (1000 * 60 * 60)).toInt()
            
            // Показываем уведомления в зависимости от оставшегося времени
            when {
                daysUntilExpiry == 0 && hoursUntilExpiry <= 1 -> {
                    // Осталось менее 1 часа
                    showNotification("⚠️ Подписка истекает менее чем через 1 час", 8000)
                }
                daysUntilExpiry == 0 && hoursUntilExpiry <= 6 -> {
                    // Осталось менее 6 часов в последний день
                    val hoursText = when(hoursUntilExpiry) {
                        1 -> "час"
                        in 2..4 -> "часа"
                        else -> "часов"
                    }
                    showNotification("⚠️ Подписка истекает через $hoursUntilExpiry $hoursText", 8000)
                }
                daysUntilExpiry == 1 -> {
                    // Остался 1 день
                    showNotification("⚠️ Подписка истекает завтра (${info.formatExpiryDate()})", 8000)
                }
                daysUntilExpiry <= 3 -> {
                    // Осталось 2-3 дня
                    showNotification("⚠️ Подписка истекает через $daysUntilExpiry ${when(daysUntilExpiry) { 2, 3, 4 -> "дня" else -> "дней" }} (${info.formatExpiryDate()})", 8000)
                }
            }
        } else if (info.days > 0) {
            // Если нет даты, но есть дни - используем дни
            when {
                info.days == 1 -> {
                    showNotification("⚠️ Подписка истекает завтра", 8000)
                }
                info.days <= 3 -> {
                    showNotification("⚠️ Подписка истекает через $info.days ${when(info.days) { 2, 3, 4 -> "дня" else -> "дней" }}", 8000)
                }
            }
        }
    }
    
    /**
     * Загрузка сохраненной информации о подписке
     */
    private fun loadSubscriptionInfo() {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        
        // Проверяем, есть ли сохраненный конфиг - если нет, очищаем информацию о подписке
        val savedConfig = getSavedConfig()
        if (savedConfig.isNullOrBlank()) {
            // Нет сохраненного конфига - очищаем информацию о подписке
            subscriptionInfo = null
            subscriptionInfoState.value = null
            Log.d("MainActivity", "No saved config found, clearing subscription info")
            // Очищаем сохраненные данные о подписке
            prefs.edit()
                .remove("subscription_days")
                .remove("subscription_hours")
                .remove("subscription_unlimited")
                .remove("subscription_expired")
                .remove("subscription_expiry_date")
                .remove("subscription_total_traffic")
                .remove("subscription_used_traffic")
                .remove("subscription_remaining_traffic")
                .apply()
            return
        }
        
        if (prefs.contains("subscription_days") || prefs.contains("subscription_hours")) {
            val days = prefs.getInt("subscription_days", 0)
            val hours = prefs.getInt("subscription_hours", 0)
            val unlimited = prefs.getBoolean("subscription_unlimited", false)
            val expired = prefs.getBoolean("subscription_expired", false)
            
            // Загружаем новые поля
            val expiryDate = if (prefs.contains("subscription_expiry_date")) {
                prefs.getLong("subscription_expiry_date", -1).takeIf { it > 0 }
            } else null
            
            val totalTraffic = if (prefs.contains("subscription_total_traffic")) {
                prefs.getLong("subscription_total_traffic", -1).takeIf { it >= 0 }
            } else null
            
            val usedTraffic = if (prefs.contains("subscription_used_traffic")) {
                prefs.getLong("subscription_used_traffic", -1).takeIf { it >= 0 }
            } else null
            
            val remainingTraffic = if (prefs.contains("subscription_remaining_traffic")) {
                prefs.getLong("subscription_remaining_traffic", -1).takeIf { it >= 0 }
            } else null
            
            subscriptionInfo = SubscriptionInfo(
                days = days,
                hours = hours,
                unlimited = unlimited,
                expired = expired,
                expiryDate = expiryDate,
                totalTraffic = totalTraffic,
                usedTraffic = usedTraffic,
                remainingTraffic = remainingTraffic
            )
            subscriptionInfoState.value = subscriptionInfo
            Log.d("MainActivity", "Loaded subscription info: ${subscriptionInfo?.format()}")
        } else {
            subscriptionInfo = null
            subscriptionInfoState.value = null
            Log.d("MainActivity", "No subscription info found in preferences")
        }
    }
    
    /**
     * Проверка подписки из Subscription URL
     */
    private fun checkSubscriptionFromUrl(subscriptionUrl: String) {
        Log.d("MainActivity", "=== Checking subscription from URL ===")
        Log.d("MainActivity", "URL: $subscriptionUrl")
        
        // Если VPN был вручную отключен, не проверяем подписку (чтобы избежать автоподключения)
        if (wasManuallyDisconnected) {
            Logger.debug(Logger.Tag.MAIN, "checkSubscriptionFromUrl: VPN был вручную отключен, пропускаем проверку")
            return
        }
        
        // Валидация URL - проверяем, что это действительно валидный URL
        val trimmedUrl = subscriptionUrl.trim()
        if (trimmedUrl.isBlank()) {
            Log.w("MainActivity", "Subscription URL is empty")
            Toast.makeText(this, "Введите Subscription URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            Log.w("MainActivity", "Invalid subscription URL format: $trimmedUrl")
            Toast.makeText(this, "Некорректный формат URL. URL должен начинаться с http:// или https://", Toast.LENGTH_LONG).show()
            return
        }
        
        // Проверяем, что это не случайно переданное имя класса
        if (trimmedUrl.equals("VpnApiClient", ignoreCase = true) || 
            trimmedUrl.equals("apiClient", ignoreCase = true)) {
            Log.w("MainActivity", "Invalid subscription URL: looks like class name instead of URL")
            Toast.makeText(this, "Некорректный URL. Пожалуйста, введите полный Subscription URL (например, https://host.kizvpn.ru/sub/...)", Toast.LENGTH_LONG).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            var errorMessage: String? = null
            
            try {
                val subscriptionInfo = apiClient.getSubscriptionInfoFromUrl(trimmedUrl)
                
                withContext(Dispatchers.Main) {
                    if (subscriptionInfo != null) {
                        Logger.subscriptionInfo(Logger.Tag.MAIN, "Subscription info received from URL: ${subscriptionInfo.format()}")
                        // НЕ вызываем saveSubscriptionInfo здесь - вызовем после обновления с количеством конфигов
                        // Уведомление покажем только один раз в saveSubscriptionInfo
                        
                        // Попытка извлечь и сохранить конфиг из Subscription URL
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // Делаем еще один запрос для получения конфигов
                                val configResponse = apiClient.getSubscriptionConfigs(trimmedUrl)
                                withContext(Dispatchers.Main) {
                                    // Сохраняем количество конфигов и сам список конфигов
                                    if (!configResponse.isNullOrEmpty()) {
                                        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                                        val editor = prefs.edit()
                                        
                                        // Сохраняем количество конфигов
                                        editor.putInt("subscription_configs_count", configResponse.size)
                                        
                                        // Сохраняем список всех конфигов из подписки в JSON формате
                                        val configsArray = org.json.JSONArray()
                                        configResponse.forEach { config ->
                                            val parsed = configParser.parseConfig(config)
                                            if (parsed != null) {
                                                val configObject = org.json.JSONObject()
                                                configObject.put("config", config)
                                                configObject.put("name", parsed.name ?: when (parsed.protocol) {
                                                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "VLESS"
                                                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "WireGuard"
                                                    else -> "Конфиг"
                                                })
                                                configObject.put("protocol", parsed.protocol.name)
                                                configObject.put("server", parsed.server)
                                                configObject.put("port", parsed.port)
                                                configsArray.put(configObject)
                                            } else {
                                                // Если не удалось распарсить, все равно сохраняем
                                                val configObject = org.json.JSONObject()
                                                configObject.put("config", config)
                                                configObject.put("name", "Конфиг")
                                                configObject.put("protocol", "UNKNOWN")
                                                configsArray.put(configObject)
                                            }
                                        }
                                        editor.putString("subscription_configs_list", configsArray.toString())
                                        
                                        // Сохраняем Subscription URL в список subscription_urls_list
                                        val subscriptionsListJson = prefs.getString("subscription_urls_list", "[]")
                                        val subscriptionsList = org.json.JSONArray(subscriptionsListJson)
                                        
                                        // Проверяем, есть ли уже этот Subscription URL
                                        var existingIndex = -1
                                        for (i in 0 until subscriptionsList.length()) {
                                            val item = subscriptionsList.getJSONObject(i)
                                            if (item.getString("url") == trimmedUrl) {
                                                existingIndex = i
                                                break
                                            }
                                        }
                                        
                                        // Создаем объект Subscription
                                        val subscriptionObject = org.json.JSONObject()
                                        subscriptionObject.put("url", trimmedUrl)
                                        subscriptionObject.put("configs_count", configResponse.size)
                                        subscriptionObject.put("configs", configsArray)
                                        
                                        // Сохраняем информацию о подписке в объекте
                                        subscriptionObject.put("days", subscriptionInfo.days)
                                        subscriptionObject.put("hours", subscriptionInfo.hours)
                                        subscriptionObject.put("unlimited", subscriptionInfo.unlimited)
                                        subscriptionObject.put("expired", subscriptionInfo.expired)
                                        if (subscriptionInfo.expiryDate != null) {
                                            subscriptionObject.put("expiry_date", subscriptionInfo.expiryDate)
                                        }
                                        if (subscriptionInfo.totalTraffic != null) {
                                            subscriptionObject.put("total_traffic", subscriptionInfo.totalTraffic)
                                        }
                                        if (subscriptionInfo.usedTraffic != null) {
                                            subscriptionObject.put("used_traffic", subscriptionInfo.usedTraffic)
                                        }
                                        if (subscriptionInfo.remainingTraffic != null) {
                                            subscriptionObject.put("remaining_traffic", subscriptionInfo.remainingTraffic)
                                        }
                                        
                                        if (existingIndex >= 0) {
                                            // Обновляем существующий
                                            subscriptionsList.put(existingIndex, subscriptionObject)
                                            Log.d("MainActivity", "Updated existing subscription URL in list")
                                        } else {
                                            // Добавляем новый
                                            subscriptionsList.put(subscriptionObject)
                                            Log.d("MainActivity", "Added new subscription URL to list")
                                        }
                                        
                                        editor.putString("subscription_urls_list", subscriptionsList.toString())
                                        
                                        // Также сохраняем Subscription URL для всех конфигов из этого URL (для обратной совместимости)
                                        configResponse.forEach { config ->
                                            val parsed = configParser.parseConfig(config)
                                            if (parsed != null) {
                                                val configKey = parsed.uuid ?: parsed.name ?: "default"
                                                val configKeyPrefs = "subscription_url_$configKey"
                                                editor.putString(configKeyPrefs, trimmedUrl)
                                                Log.d("MainActivity", "Saved subscription URL for config: $configKey")
                                            }
                                        }
                                        
                                        editor.apply()
                                        Logger.info(Logger.Tag.MAIN, "Found ${configResponse.size} config(s) in subscription URL, saved count and list")
                                    }
                                    
                                    if (!configResponse.isNullOrEmpty()) {
                                        Logger.info(Logger.Tag.MAIN, "Found ${configResponse.size} config(s) in subscription URL")
                                        
                                        // Сохраняем ВСЕ конфиги из Subscription URL в соответствующие списки
                                        configResponse.forEach { config ->
                                            saveConfig(config)
                                        }
                                        
                                        // Сохраняем первый конфиг как активный (можно будет добавить выбор)
                                        val firstConfig = configResponse.first()
                                        val parsedConfig = configParser.parseConfig(firstConfig)
                                        
                                        if (parsedConfig != null) {
                                            Logger.configParsed(Logger.Tag.MAIN, "Parsed config from subscription, protocol: ${parsedConfig.protocol}")
                                            
                                            viewModel.setVpnConfig(firstConfig)
                                            
                                            // Сохраняем подписку с количеством конфигов
                                            val updatedInfo = subscriptionInfo.copy(configsCount = configResponse.size)
                                            
                                            // Сохраняем информацию о подписке для всех конфигов из Subscription URL
                                            configResponse.forEach { config ->
                                                val parsed = configParser.parseConfig(config)
                                                if (parsed != null) {
                                                    val configKey = parsed.uuid ?: parsed.name ?: "default"
                                                    saveSubscriptionInfoForConfig(updatedInfo, configKey)
                                                }
                                            }
                                            
                                            // Также сохраняем общую информацию о подписке
                                            saveSubscriptionInfo(updatedInfo, shouldShowNotification = shouldShowSubscriptionNotification)
                                            
                                            // НЕ подключаемся автоматически при проверке подписки через Subscription URL
                                            // Автоподключение происходит только при явном нажатии кнопки подключения
                                            Logger.info(Logger.Tag.MAIN, "Subscription URL checked, configs loaded. Waiting for user to connect manually.")
                                        } else {
                                            Logger.configError(Logger.Tag.MAIN, "Failed to parse config from subscription URL")
                                            // Сохраняем подписку
                                            saveSubscriptionInfo(subscriptionInfo, shouldShowNotification = shouldShowSubscriptionNotification)
                                            // Дополнительное уведомление об ошибке
                                            showNotification("⚠️ Не удалось загрузить конфиг", 5000)
                                        }
                                    } else {
                                        Logger.warn(Logger.Tag.MAIN, "No configs found in subscription URL response")
                                        // Сохраняем подписку
                                        saveSubscriptionInfo(subscriptionInfo, shouldShowNotification = shouldShowSubscriptionNotification)
                                        // Уведомление "Конфиги не найдены" удалено - не показываем его, так как это может быть нормально
                                    }
                                }
                            } catch (e: Exception) {
                                // Логируем ошибку, но не показываем пользователю, так как информация о подписке уже получена
                                Logger.warn(Logger.Tag.MAIN, "Failed to extract configs from subscription URL (subscription info already received): ${e.message}")
                                Logger.debug(Logger.Tag.MAIN, "Error details: ${e.javaClass.simpleName} - ${e.message}")
                                withContext(Dispatchers.Main) {
                                    // Сохраняем подписку даже если конфиги не удалось загрузить из-за сетевой ошибки
                                    // Информация о подписке уже получена из заголовков, поэтому просто сохраняем её
                                    saveSubscriptionInfo(subscriptionInfo, shouldShowNotification = shouldShowSubscriptionNotification)
                                }
                            }
                        }
                    } else {
                        Log.w("MainActivity", "✗ Failed to get subscription info from URL")
                        // Пробуем извлечь UUID и проверить через стандартный API
                        Log.d("MainActivity", "Trying to extract UUID from subscription URL and check via API...")
                        
                        // Пытаемся извлечь UUID из URL (последняя часть после /sub/)
                        try {
                            val urlParts = trimmedUrl.split("/sub/")
                            if (urlParts.size > 1) {
                                val token = urlParts[1].trim()
                                Log.d("MainActivity", "Extracted token from URL: ${token.take(20)}...")
                                
                                // API проверка подписки по UUID удалена - используйте Subscription URL
                                if (token.matches(Regex("""[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}""", RegexOption.IGNORE_CASE))) {
                                    Log.w("MainActivity", "Token looks like UUID, but API subscription check removed - use Subscription URL instead")
                                } else {
                                    // Token не UUID - ничего не делаем
                                }
                            } else {
                                // URL не содержит /sub/ - ничего не делаем
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Failed to extract UUID from URL", e)
                        }
                        
                        // Уведомление об ошибке удалено по запросу пользователя
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                errorMessage = "Ошибка сети: не удалось подключиться к серверу"
                Log.e("MainActivity", "Network error checking subscription from URL", e)
            } catch (e: java.net.SocketTimeoutException) {
                errorMessage = "Таймаут соединения. Проверьте подключение к интернету"
                Log.e("MainActivity", "Timeout checking subscription from URL", e)
            } catch (e: java.io.IOException) {
                errorMessage = "Ошибка ввода/вывода: ${e.message}"
                Log.e("MainActivity", "IO error checking subscription from URL", e)
            } catch (e: Exception) {
                errorMessage = "Ошибка: ${e.message ?: "Неизвестная ошибка"}"
                Log.e("MainActivity", "Error checking subscription from URL", e)
            }
            
            if (errorMessage != null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        errorMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Периодическое обновление подписки при включенном VPN (раз в час)
     */
    private fun startSubscriptionUpdateJob() {
        // Отменяем предыдущий job, если есть
        subscriptionUpdateJob?.cancel()
        
        subscriptionUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (viewModel.connectionStatus.value.isConnected) {
                    // Ждем 1 час (3600000 миллисекунд)
                    delay(60 * 60 * 1000L)
                    
                    // Проверяем, что VPN все еще подключен
                    if (!viewModel.connectionStatus.value.isConnected) {
                        break
                    }
                    
                    // Проверяем подписку, если есть сохраненный конфиг
                    val savedConfig = getSavedConfig()
                    if (!savedConfig.isNullOrBlank()) {
                        val now = System.currentTimeMillis()
                        // Проверяем, что прошло хотя бы 50 минут с последней проверки (защита от множественных проверок)
                        if (now - lastSubscriptionCheckTime >= 50 * 60 * 1000L) {
                            Log.d("MainActivity", "Периодическое обновление подписки (раз в час)")
                            lastSubscriptionCheckTime = now
                            checkSubscriptionFromConfig(savedConfig)
                        } else {
                            Log.d("MainActivity", "Пропуск периодического обновления: недавно проверяли подписку")
                        }
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
                // При ошибке ждем еще 30 минут перед следующей попыткой
                delay(30 * 60 * 1000L)
            }
        }
        
        Log.d("MainActivity", "Запущено периодическое обновление подписки (раз в час при включенном VPN)")
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
                    
                    // API проверка подписки и активация удалены - используйте Subscription URL
                    Log.w("MainActivity", "API subscription check and activation removed - use Subscription URL instead")
                    Log.d("MainActivity", "UUID: ${parsedConfig.uuid}")
                    callback(null)
                } else {
                    // API активация ключей удалена - используйте Subscription URL
                    Log.w("MainActivity", "API key activation removed - use Subscription URL instead")
                    callback(null)
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
        
        // Автоматическая проверка подписки при изменении конфига отключена
        // Проверка только при включении VPN
        
        AppNavHost(
            onConnectClick = {
                // Сбрасываем флаг ручного отключения при попытке подключения
                wasManuallyDisconnected = false
                
                // onConnectClick используется ТОЛЬКО для подключения
                // HomeScreen уже проверяет isConnected и вызывает правильный callback
                // (onDisconnectClick если подключен, onConnectClick если отключен)
                
                // Защита от множественных быстрых нажатий - проверяем актуальное состояние
                val currentStatus = viewModel.connectionStatus.value
                if (currentStatus.isConnected || currentStatus.isConnecting || isVpnOperationInProgress.get()) {
                    Log.d("MainActivity", "onConnectClick: VPN already connected/connecting or operation in progress, ignoring")
                    return@AppNavHost
                }
                
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
                        
                        // Устанавливаем флаг для показа уведомления при подключении
                        shouldShowSubscriptionNotification = true
                        // НЕ проверяем подписку перед подключением - это может вызвать автоподключение
                        // Подписка проверяется автоматически после подключения (если нужно)
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
            },
            onDisconnectClick = {
                // Проверяем актуальное состояние - если VPN уже отключен, ничего не делаем
                val disconnectStatus = viewModel.connectionStatus.value
                if (!disconnectStatus.isConnected && !disconnectStatus.isConnecting) {
                    Logger.debug(Logger.Tag.MAIN, "onDisconnectClick: VPN already disconnected, ignoring")
                    return@AppNavHost
                }
                
                // Отключение имеет приоритет - вызываем disconnectFromVpn
                // Он сам управляет флагом isVpnOperationInProgress
                Logger.info(Logger.Tag.MAIN, "onDisconnectClick: Disconnecting VPN...")
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
                Log.d("MainActivity", "onConfigChange: получен конфиг, длина=${config.length}")
                currentConfigInput = config
                viewModel.setVpnConfig(config)
                
                // Проверяем, является ли это Subscription URL
                if (config.startsWith("http://") || config.startsWith("https://")) {
                    Log.d("MainActivity", "onConfigChange: обнаружен Subscription URL")
                    checkSubscriptionFromUrl(config)
                } else {
                    // Это обычный конфиг - парсим и сохраняем
                    val parsedConfig = configParser.parseConfig(config.trim())
                    if (parsedConfig != null) {
                        Log.d("MainActivity", "onConfigChange: конфиг успешно распарсен, protocol=${parsedConfig.protocol}")
                        // Сохраняем конфиг в список
                        saveConfig(config.trim())
                        
                        // Устанавливаем конфиг как текущий и обновляем UI
                        val configType = when (parsedConfig.protocol) {
                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                            else -> "vless"
                        }
                        
                        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                        when (parsedConfig.protocol) {
                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                                prefs.edit()
                                    .putString("saved_config", config.trim())
                                    .putString("active_config_type", configType)
                                    .apply()
                            }
                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                                prefs.edit()
                                    .putString("saved_wireguard_config", config.trim())
                                    .putString("active_config_type", configType)
                                    .apply()
                            }
                            else -> {
                                prefs.edit().putString("active_config_type", configType).apply()
                            }
                        }
                        
                        // Обновляем UI - триггерим перерисовку списка конфигов
                        lifecycleScope.launch {
                            delay(300)
                            // UI обновится автоматически при следующем compose recomposition
                        }
                    } else {
                        Log.w("MainActivity", "onConfigChange: не удалось распарсить конфиг")
                    }
                }
            },
            viewModel = viewModel,
            onActivateKey = { key, callback ->
                activateKey(key, callback)
            },
            onSubscriptionUrlCheck = { url ->
                checkSubscriptionFromUrl(url)
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
            onDeleteConfig = { configToDelete, protocol ->
                // Удаление конфига из списка
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val configListKey = when (protocol) {
                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "saved_vless_configs_list"
                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "saved_wireguard_configs_list"
                    else -> "saved_vless_configs_list"
                }
                
                try {
                    val existingListJson = prefs.getString(configListKey, "[]")
                    val configList = org.json.JSONArray(existingListJson)
                    
                    // Находим и удаляем конфиг
                    var indexToRemove = -1
                    for (i in 0 until configList.length()) {
                        val item = configList.getJSONObject(i)
                        if (item.getString("config") == configToDelete) {
                            indexToRemove = i
                            break
                        }
                    }
                    
                    if (indexToRemove >= 0) {
                        configList.remove(indexToRemove)
                        prefs.edit()
                            .putString(configListKey, configList.toString())
                            .commit()
                        
                        // Если удаленный конфиг был активным, сбрасываем активный конфиг
                        val activeConfigType = prefs.getString("active_config_type", null)
                        val activeConfig = when (activeConfigType) {
                            "vless" -> prefs.getString("saved_config", null)
                            "wireguard" -> prefs.getString("saved_wireguard_config", null)
                            else -> null
                        }
                        
                        // Удаляем Subscription URL для этого конфига
                        val parsedConfig = configParser.parseConfig(configToDelete)
                        if (parsedConfig != null) {
                            val configKey = parsedConfig.uuid ?: parsedConfig.name ?: "default"
                            val subscriptionUrlKey = "subscription_url_$configKey"
                            prefs.edit().remove(subscriptionUrlKey).apply()
                            Log.d("MainActivity", "onDeleteConfig: Удален subscription_url_$configKey")
                        }
                        
                        if (activeConfig == configToDelete) {
                            // Отключаем VPN если подключен
                            disconnectFromVpn()
                            
                            // Удаляем активный конфиг
                            prefs.edit()
                                .remove(when (protocol) {
                                    com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "saved_config"
                                    com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "saved_wireguard_config"
                                    else -> "saved_config"
                                })
                                .putString("active_config_type", null)
                                .commit()
                            
                            currentConfigInput = ""
                            viewModel.setVpnConfig("")
                        }
                        
                        Log.d("MainActivity", "onDeleteConfig: Конфиг удален, осталось: ${configList.length()}")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Ошибка при удалении конфига", e)
                }
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
                
                // Если конфиг успешно распарсен, сохраняем его в список конфигов через saveConfig
                // Это добавит конфиг в saved_vless_configs_list или saved_wireguard_configs_list
                if (parsedConfig != null) {
                    saveConfig(config)
                    Log.d("MainActivity", "onSelectConfig: Конфиг сохранен через saveConfig в список конфигов")
                } else {
                    // Если парсинг не удался, просто сохраняем как активный (для обратной совместимости)
                    val prefsFallback = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                    val configType = when (protocolToUse) {
                        com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                        com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                        else -> "vless"
                    }
                    
                    when (protocolToUse) {
                        com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> {
                            prefsFallback.edit()
                                .putString("saved_config", config)
                                .putString("active_config_type", configType)
                                .commit()
                            Log.d("MainActivity", "onSelectConfig: Сохранен Vless конфиг в saved_config (парсинг не удался)")
                        }
                        com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                            prefsFallback.edit()
                                .putString("saved_wireguard_config", config)
                                .putString("active_config_type", configType)
                                .commit()
                            Log.d("MainActivity", "onSelectConfig: Сохранен WireGuard конфиг в saved_wireguard_config (парсинг не удался)")
                        }
                        else -> {
                            prefsFallback.edit().putString("active_config_type", configType).commit()
                        }
                    }
                }
                
                // Устанавливаем конфиг как текущий
                currentConfigInput = config
                viewModel.setVpnConfig(config)
                
                // Проверяем подписку при изменении конфига
                if (config.isNotBlank()) {
                    val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                    // Пытаемся найти сохраненный Subscription URL для этого конфига
                    val parsedConfigForSubscription = configParser.parseConfig(config)
                    val configKey = parsedConfigForSubscription?.uuid ?: parsedConfigForSubscription?.name
                    
                    var savedSubscriptionUrl: String? = null
                    if (configKey != null) {
                        savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                        Log.d("MainActivity", "onSelectConfig: Looking for subscription URL for config key: $configKey")
                    }
                    
                    if (!savedSubscriptionUrl.isNullOrBlank()) {
                        // Если есть сохраненный Subscription URL - проверяем его
                        Log.d("MainActivity", "onSelectConfig: Found saved subscription URL for config, checking...")
                        checkSubscriptionFromUrl(savedSubscriptionUrl)
                    } else {
                        // Если нет Subscription URL - проверяем конфиг напрямую (старый способ)
                        Log.d("MainActivity", "onSelectConfig: No saved subscription URL found for config key: $configKey")
                        Log.d("MainActivity", "onSelectConfig: Checking config directly (old method)...")
                        checkSubscriptionFromConfig(config)
                    }
                }
            },
        )
    }
    
}







