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
import androidx.fragment.app.FragmentActivity
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
import com.kizvpn.client.security.BiometricAuthManager
import com.kizvpn.client.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.net.TrafficStats
import android.app.ActivityManager
import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    
    private val viewModel: VpnViewModel by viewModels()
    private val configParser = ConfigParser()
    // VpnApiClient - универсальный клиент для парсинга Subscription URL
    // Работает с любыми VPN серверами без привязки к конкретным API
    private val apiClient = VpnApiClient()
    private lateinit var connectionHistoryManager: com.kizvpn.client.data.ConnectionHistoryManager
    private var connectionStartTime: Long? = null // Время начала подключения для расчета длительности
    
    // Биометрическая аутентификация
    private lateinit var biometricAuthManager: BiometricAuthManager
    private var isAppAuthenticated = false // Флаг аутентификации
    
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
        
        // Инициализируем биометрическую аутентификацию
        biometricAuthManager = BiometricAuthManager(this)
        
        // Мигрируем неправильно сохраненные конфиги (если WireGuard конфиг в saved_config, переносим его)
        migrateIncorrectlySavedConfigs()
        migrateOldConfigsToLists() // Мигрируем старые конфиги в новый формат списков
        
        // Загружаем сохраненный конфиг
        loadSavedConfig()
        
        // Загружаем сохраненную информацию о подписке
        val savedConfig = getSavedConfig()
        if (!savedConfig.isNullOrBlank()) {
            // Есть сохраненный конфиг - загружаем информацию о подписке
            val savedSubscriptionInfo = loadSubscriptionInfo()
            if (savedSubscriptionInfo != null) {
                this.subscriptionInfo = savedSubscriptionInfo
                this.subscriptionInfoState.value = savedSubscriptionInfo
                viewModel.updateSubscriptionInfo(savedSubscriptionInfo)
                Log.d("MainActivity", "Loaded subscription info on startup: ${savedSubscriptionInfo.format()}")
            } else {
                Log.d("MainActivity", "No saved subscription info found for existing config")
            }
        } else {
            // Нет конфига - очищаем старые данные о подписке (исправляем проблему с 363 днями)
            Log.d("MainActivity", "No config found on startup, clearing old subscription data")
            val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("subscription_days")
                .remove("subscription_hours")
                .remove("subscription_unlimited")
                .remove("subscription_expired")
                .remove("subscription_expiry_date")
                .remove("subscription_total_traffic")
                .remove("subscription_used_traffic")
                .remove("subscription_remaining_traffic")
                .remove("subscription_configs_count")
                .apply()
            
            // Также очищаем состояние в UI
            this.subscriptionInfo = null
            this.subscriptionInfoState.value = null
            viewModel.updateSubscriptionInfo(null)
        }
        
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
            checkBiometricAuthenticationIfEnabled()
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
        
        // Проверяем биометрическую аутентификацию при возврате в приложение
        val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
        val securityEnabled = prefs.getBoolean("security_enabled", false)
        
        if (securityEnabled && !isAppAuthenticated && biometricAuthManager.isBiometricAvailable()) {
            Log.d("MainActivity", "Требуется повторная биометрическая аутентификация")
            
            biometricAuthManager.authenticate(
                activity = this,
                title = getString(R.string.biometric_title),
                subtitle = getString(R.string.biometric_subtitle),
                onSuccess = {
                    Log.d("MainActivity", "Повторная биометрическая аутентификация успешна")
                    isAppAuthenticated = true
                    continueOnResume()
                },
                onError = { error ->
                    Log.e("MainActivity", "Ошибка повторной биометрической аутентификации: $error")
                    Toast.makeText(this, getString(R.string.authentication_error, error), Toast.LENGTH_LONG).show()
                    finish()
                },
                onCancel = {
                    Log.d("MainActivity", "Повторная биометрическая аутентификация отменена")
                    Toast.makeText(this, getString(R.string.authentication_cancelled), Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        } else {
            continueOnResume()
        }
    }
    
    private fun continueOnResume() {
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
        
        // Сбрасываем флаг аутентификации при уходе из приложения
        val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
        val securityEnabled = prefs.getBoolean("security_enabled", false)
        if (securityEnabled) {
            isAppAuthenticated = false
        }
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
        
        // ВАЖНО: Всегда используем текущий активный конфиг, а не переданный configString
        val currentActiveConfig = getSavedConfig()
        if (currentActiveConfig.isNullOrBlank()) {
            Log.e("MainActivity", "startVpnConnection: No active config found!")
            viewModel.updateConnectionStatus(
                viewModel.connectionStatus.value.copy(
                    isConnected = false,
                    isConnecting = false
                )
            )
            showNotification("Ошибка: нет активного конфига", 4000)
            isVpnOperationInProgress.set(false)
            return
        }
        
        Log.d("MainActivity", "startVpnConnection: Using current active config (length: ${currentActiveConfig.length})")
        
        // Обновляем состояние СИНХРОННО ПЕРЕД запуском асинхронной операции
        viewModel.updateConnectionStatus(
            viewModel.connectionStatus.value.copy(isConnecting = true)
        )
        
        lifecycleScope.launch {
            try {
                // Запускаем VPN сервис с текущим активным конфигом
                val serviceIntent = Intent(this@MainActivity, KizVpnService::class.java).apply {
                    putExtra("config", currentActiveConfig)
                    action = "com.kizvpn.client.START"
                }
                
                Logger.info(Logger.Tag.MAIN, "Starting VPN service with current active config length: ${currentActiveConfig.length}")
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
                        // Сначала обновляем подписку из subscription_blocks (если конфиг там есть)
                        refreshActiveSubscriptionFromBlocks(configString)
                        
                        // Обновляем информацию о подписке при подключении VPN (только обновляем, не очищаем)
                        // ВАЖНО: Используем getSavedConfig() чтобы получить текущий активный конфиг
                        val currentActiveConfig = getSavedConfig()
                        Log.d("MainActivity", "VPN connected: getting current active config...")
                        Log.d("MainActivity", "VPN connected: currentActiveConfig length = ${currentActiveConfig?.length ?: 0}")
                        
                        if (!currentActiveConfig.isNullOrBlank()) {
                            val parsedConfig = configParser.parseConfig(currentActiveConfig)
                            val configKey = parsedConfig?.uuid ?: parsedConfig?.name ?: parsedConfig?.comment
                            
                            Log.d("MainActivity", "VPN connected: checking subscription for active config")
                            Log.d("MainActivity", "VPN connected: configKey = $configKey")
                            
                            if (configKey != null) {
                                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                                val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                                Log.d("MainActivity", "VPN connected: savedSubscriptionUrl = ${savedSubscriptionUrl?.take(50)}...")
                                
                                if (!savedSubscriptionUrl.isNullOrBlank()) {
                                    // Сначала загружаем сохраненную информацию о подписке для немедленного отображения
                                    Log.d("MainActivity", "VPN connected: loading saved subscription info first...")
                                    val savedSubscriptionInfo = loadSubscriptionInfo()
                                    if (savedSubscriptionInfo != null) {
                                        Log.d("MainActivity", "VPN connected: ✓ Loaded saved subscription info, displaying immediately")
                                        this@MainActivity.subscriptionInfo = savedSubscriptionInfo
                                        this@MainActivity.subscriptionInfoState.value = savedSubscriptionInfo
                                        viewModel.updateSubscriptionInfo(savedSubscriptionInfo)
                                    } else {
                                        Log.d("MainActivity", "VPN connected: No saved subscription info found")
                                    }
                                    
                                    // Затем обновляем информацию с сервера для получения актуальных данных
                                    Log.d("MainActivity", "VPN connected: updating subscription URL info from server for fresh data")
                                    checkSubscriptionFromUrl(savedSubscriptionUrl, forceUpdate = true)
                                } else {
                                    Log.d("MainActivity", "VPN connected: standalone config, keeping existing subscription info (not clearing)")
                                    // Для standalone конфигов НЕ очищаем информацию о подписке
                                    // Подписка должна быть постоянной как в шторке уведомлений
                                    // VPN подключение только обновляет данные, но не очищает их
                                }
                            } else {
                                Log.d("MainActivity", "VPN connected: no config key found, keeping existing subscription info")
                                // Нет ключа конфига - НЕ очищаем информацию о подписке
                                // Сохраняем принцип постоянства как в шторке уведомлений
                            }
                        } else {
                            Log.w("MainActivity", "VPN connected: no active config found, keeping existing subscription info")
                            // Нет активного конфига - НЕ очищаем информацию о подписке
                            // Подписка должна оставаться видимой независимо от состояния VPN
                        }
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
     * Проверка биометрической аутентификации при запуске приложения
     */
    private fun checkBiometricAuthenticationIfEnabled() {
        val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
        val securityEnabled = prefs.getBoolean("security_enabled", false)
        
        if (securityEnabled && biometricAuthManager.isBiometricAvailable()) {
            Log.d("MainActivity", "Биометрическая аутентификация включена, запрашиваем аутентификацию...")
            
            biometricAuthManager.authenticate(
                activity = this,
                title = getString(R.string.biometric_title),
                subtitle = getString(R.string.biometric_subtitle_app),
                onSuccess = {
                    Log.d("MainActivity", "Биометрическая аутентификация успешна")
                    isAppAuthenticated = true
                    checkAutoConnect()
                },
                onError = { error ->
                    Log.e("MainActivity", "Ошибка биометрической аутентификации: $error")
                    Toast.makeText(this, getString(R.string.authentication_error, error), Toast.LENGTH_LONG).show()
                    // Закрываем приложение при ошибке аутентификации
                    finish()
                },
                onCancel = {
                    Log.d("MainActivity", "Биометрическая аутентификация отменена")
                    Toast.makeText(this, getString(R.string.authentication_cancelled), Toast.LENGTH_SHORT).show()
                    // Закрываем приложение при отмене аутентификации
                    finish()
                }
            )
        } else {
            // Биометрия отключена или недоступна - продолжаем обычную загрузку
            isAppAuthenticated = true
            checkAutoConnect()
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
                delay(1000) // Уменьшено до 1 секунды для более частого обновления скорости
                
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
                            
                            // Обновляем скорость в VPN сервисе для уведомления
                            updateVpnServiceSpeed(downloadBytes, uploadBytes)
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
     * Обновление скорости в VPN сервисе для уведомления
     */
    private fun updateVpnServiceSpeed(downloadBytes: Long, uploadBytes: Long) {
        try {
            val serviceIntent = Intent(this, KizVpnService::class.java)
            bindService(serviceIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as? KizVpnService.KizVpnBinder
                    
                    // Вычисляем скорость (примерно) - используем данные из ViewModel
                    val currentSpeed = viewModel.currentSpeed.value // KB/s
                    val downloadSpeed = (currentSpeed * 0.7 * 1024).toLong() // 70% на загрузку, конвертируем в байты/сек
                    val uploadSpeed = (currentSpeed * 0.3 * 1024).toLong() // 30% на отдачу, конвертируем в байты/сек
                    
                    Log.d("MainActivity", "Updating VPN service speed: download=${downloadSpeed}B/s, upload=${uploadSpeed}B/s (total=${currentSpeed}KB/s)")
                    binder?.updateSpeed(downloadSpeed.coerceAtLeast(0), uploadSpeed.coerceAtLeast(0))
                    unbindService(this)
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating VPN service speed", e)
        }
    }
    
    /**
     * Обновление пинга в VPN сервисе для уведомления
     */
    private fun updateVpnServicePing(ping: Int) {
        try {
            val serviceIntent = Intent(this, KizVpnService::class.java)
            bindService(serviceIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as? KizVpnService.KizVpnBinder
                    binder?.updatePing(ping)
                    unbindService(this)
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating VPN service ping", e)
        }
    }
    
    /**
     * Полная очистка информации о подписке
     */
    private fun clearAllSubscriptionInfo() {
        Log.d("MainActivity", "clearAllSubscriptionInfo: Очищаем всю информацию о подписке")
        
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("subscription_days")
            .remove("subscription_hours")
            .remove("subscription_unlimited")
            .remove("subscription_expired")
            .remove("subscription_expiry_date")
            .remove("subscription_total_traffic")
            .remove("subscription_used_traffic")
            .remove("subscription_remaining_traffic")
            .remove("subscription_configs_count")
            .apply()
        
        // Очищаем состояние в UI
        this.subscriptionInfo = null
        this.subscriptionInfoState.value = null
        viewModel.updateSubscriptionInfo(null)
        
        Log.d("MainActivity", "clearAllSubscriptionInfo: ✓ Информация о подписке очищена")
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
            
            // Проверяем, является ли это standalone конфигом (не из subscription URL)
            val configKey = parsedConfig.uuid ?: parsedConfig.name ?: "default"
            val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
            
            if (savedSubscriptionUrl.isNullOrBlank()) {
                // Это standalone конфиг - очищаем информацию о подписке
                Log.d("MainActivity", "saveConfig: Standalone конфиг, очищаем подписку")
                clearAllSubscriptionInfo()
            } else {
                Log.d("MainActivity", "saveConfig: Конфиг из subscription URL, сохраняем подписку")
            }
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
            
            // Проверяем, является ли это standalone конфигом (не из subscription URL)
            val configKey = parsedConfig.uuid ?: parsedConfig.name ?: "default"
            val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
            
            if (savedSubscriptionUrl.isNullOrBlank()) {
                // Это standalone конфиг - очищаем информацию о подписке
                Log.d("MainActivity", "saveConfig (fallback): Standalone конфиг, очищаем подписку")
                clearAllSubscriptionInfo()
            } else {
                Log.d("MainActivity", "saveConfig (fallback): Конфиг из subscription URL, сохраняем подписку")
            }
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
     * Проверка подписки для конфига
     * Логика:
     * 1. Если у конфига есть subscription URL - обновляем информацию
     * 2. Если это отдельный ключ - очищаем информацию только при смене типа конфига
     * 3. При переключении VPN информация сохраняется
     */
    private fun checkSubscriptionFromConfig(config: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsedConfig = configParser.parseConfig(config)
                Log.d("MainActivity", "checkSubscriptionFromConfig: uuid=${parsedConfig?.uuid}, protocol=${parsedConfig?.protocol}, name=${parsedConfig?.name}")
                
                // Обновляем время последней проверки
                lastSubscriptionCheckTime = System.currentTimeMillis()
                
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val configKey = parsedConfig?.uuid ?: parsedConfig?.name ?: parsedConfig?.comment
                
                if (configKey != null) {
                    val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                    if (!savedSubscriptionUrl.isNullOrBlank()) {
                        Log.d("MainActivity", "checkSubscriptionFromConfig: Config has subscription URL, updating info...")
                        // Конфиг из subscription URL - всегда обновляем информацию
                        withContext(Dispatchers.Main) {
                            checkSubscriptionFromUrl(savedSubscriptionUrl)
                        }
                        return@launch
                    } else {
                        Log.d("MainActivity", "checkSubscriptionFromConfig: Config is standalone (no subscription URL)")
                        // Это отдельный ключ - очищаем информацию только если она от subscription
                        val currentInfo = viewModel.subscriptionInfo.value
                        if (currentInfo != null) {
                            // Проверяем, есть ли другие subscription URLs
                            val hasOtherSubscriptions = prefs.all.keys.any { it.startsWith("subscription_url_") }
                            if (hasOtherSubscriptions) {
                                Log.d("MainActivity", "checkSubscriptionFromConfig: Clearing subscription info for standalone config")
                                withContext(Dispatchers.Main) {
                                    viewModel.clearSubscriptionInfo()
                                }
                            }
                        }
                    }
                } else {
                    Log.d("MainActivity", "checkSubscriptionFromConfig: No config key found")
                    // Конфиг без ключа - это точно отдельный ключ
                    val currentInfo = viewModel.subscriptionInfo.value
                    if (currentInfo != null) {
                        // Проверяем, есть ли subscription URLs
                        val hasSubscriptions = prefs.all.keys.any { it.startsWith("subscription_url_") }
                        if (hasSubscriptions) {
                            Log.d("MainActivity", "checkSubscriptionFromConfig: Clearing subscription info for config without key")
                            withContext(Dispatchers.Main) {
                                viewModel.clearSubscriptionInfo()
                            }
                        }
                    }
                }
                
                Log.d("MainActivity", "checkSubscriptionFromConfig: Finished processing config")
            } catch (e: Exception) {
                Log.e("MainActivity", "checkSubscriptionFromConfig: Error processing config", e)
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
     * Сохранение информации о подписке (упрощенная версия как в VPN сервисе)
     */
    private fun saveSubscriptionInfo(info: SubscriptionInfo, shouldShowNotification: Boolean = false) {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("subscription_days", info.days)
            .putInt("subscription_hours", info.hours)
            .putBoolean("subscription_unlimited", info.unlimited)
            .putBoolean("subscription_expired", info.expired)
        
        // Сохраняем дополнительные поля
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
        
        if (info.configsCount != null) {
            editor.putInt("subscription_configs_count", info.configsCount)
        } else {
            editor.remove("subscription_configs_count")
        }
        
        editor.apply()
        
        // Обновляем состояние в MainActivity и ViewModel
        this.subscriptionInfo = info
        this.subscriptionInfoState.value = info
        viewModel.updateSubscriptionInfo(info)
        
        Log.d("MainActivity", "✓ Subscription info saved: ${info.format()}")
        
        // Показываем уведомление только если явно указано или при подключении VPN
        if (shouldShowNotification || shouldShowSubscriptionNotification) {
            val subscriptionText = info.format()
            showNotification(subscriptionText, 800)
            shouldShowSubscriptionNotification = false // Сбрасываем флаг после показа
        }
        
        // Обновляем уведомление VPN сервиса с новой информацией о подписке
        try {
            val serviceIntent = Intent(this, KizVpnService::class.java)
            bindService(serviceIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    // Сервис обновит уведомление автоматически при следующем обновлении
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
            Log.d("MainActivity", "Updating VPN notification with subscription info: ${info.days} days, ${info.hours} hours")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to update VPN notification", e)
        }
        
        // Проверяем уведомления о приближающемся окончании подписки
        checkSubscriptionExpiryNotifications(info)
    }
    
    /**
     * Загрузка информации о подписке (упрощенная версия)
     */
    private fun loadSubscriptionInfo(): SubscriptionInfo? {
        val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
        
        // Проверяем, есть ли сохраненная информация
        if (!prefs.contains("subscription_days")) {
            Log.d("MainActivity", "No saved subscription info found")
            return null
        }
        
        val days = prefs.getInt("subscription_days", 0)
        val hours = prefs.getInt("subscription_hours", 0)
        val unlimited = prefs.getBoolean("subscription_unlimited", false)
        val expired = prefs.getBoolean("subscription_expired", false)
        
        val expiryDate = if (prefs.contains("subscription_expiry_date")) {
            prefs.getLong("subscription_expiry_date", 0L)
        } else null
        
        val totalTraffic = if (prefs.contains("subscription_total_traffic")) {
            prefs.getLong("subscription_total_traffic", 0L)
        } else null
        
        val usedTraffic = if (prefs.contains("subscription_used_traffic")) {
            prefs.getLong("subscription_used_traffic", 0L)
        } else null
        
        val remainingTraffic = if (prefs.contains("subscription_remaining_traffic")) {
            prefs.getLong("subscription_remaining_traffic", 0L)
        } else null
        
        val configsCount = if (prefs.contains("subscription_configs_count")) {
            prefs.getInt("subscription_configs_count", 1)
        } else null
        
        val subscriptionInfo = SubscriptionInfo(
            days = days,
            hours = hours,
            unlimited = unlimited,
            expired = expired,
            expiryDate = expiryDate,
            totalTraffic = totalTraffic,
            usedTraffic = usedTraffic,
            remainingTraffic = remainingTraffic,
            configsCount = configsCount
        )
        
        Log.d("MainActivity", "✓ Loaded subscription info: ${subscriptionInfo.format()}")
        return subscriptionInfo
    }
    
    /**
     * Обновление подписки активного конфига из subscription_blocks
     */
    private fun refreshActiveSubscriptionFromBlocks(activeConfig: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
                val subscriptionsArray = org.json.JSONArray(subscriptionsJson)
                
                // Ищем подписку с активным конфигом
                for (i in 0 until subscriptionsArray.length()) {
                    val subObj = subscriptionsArray.getJSONObject(i)
                    val configsArray = subObj.optJSONArray("configs") ?: continue
                    
                    // Проверяем есть ли активный конфиг в этой подписке
                    var foundActiveConfig = false
                    for (j in 0 until configsArray.length()) {
                        if (configsArray.getString(j) == activeConfig) {
                            foundActiveConfig = true
                            break
                        }
                    }
                    
                    if (foundActiveConfig) {
                        // Нашли подписку с активным конфигом
                        val subscriptionUrl = subObj.optString("url", null)
                        if (!subscriptionUrl.isNullOrBlank()) {
                            Log.d("MainActivity", "refreshActiveSubscriptionFromBlocks: Found subscription URL: $subscriptionUrl")
                            
                            // Обновляем информацию через API
                            val subscriptionInfo = apiClient.getSubscriptionInfoFromUrl(subscriptionUrl)
                            val configs = apiClient.getSubscriptionConfigs(subscriptionUrl)
                            
                            if (subscriptionInfo != null && configs != null) {
                                withContext(Dispatchers.Main) {
                                    // Обновляем данные в subscription_blocks
                                    subObj.put("name", "Subscription (${configs.size})")
                                    
                                    subscriptionInfo.formatUsedTraffic()?.let { 
                                        subObj.put("usedTraffic", it) 
                                    }
                                    subscriptionInfo.formatTotalTraffic()?.let { 
                                        subObj.put("totalTraffic", it) 
                                    }
                                    subscriptionInfo.usedTraffic?.let { 
                                        subObj.put("usedTrafficBytes", it) 
                                    }
                                    subscriptionInfo.totalTraffic?.let { 
                                        subObj.put("totalTrafficBytes", it) 
                                    }
                                    val daysText = subscriptionInfo.format()
                                    if (daysText != "Не активирован") {
                                        subObj.put("daysRemaining", daysText)
                                    }
                                    
                                    val configsArrayNew = org.json.JSONArray()
                                    configs.forEach { configsArrayNew.put(it) }
                                    subObj.put("configs", configsArrayNew)
                                    
                                    // Сохраняем обновлённые данные
                                    prefs.edit()
                                        .putString("subscription_blocks", subscriptionsArray.toString())
                                        .apply()
                                    
                                    // Сохраняем в глобальные ключи для уведомления
                                    val editor = prefs.edit()
                                    editor.putInt("subscription_days", subscriptionInfo.days)
                                    editor.putInt("subscription_hours", subscriptionInfo.hours)
                                    subscriptionInfo.usedTraffic?.let { 
                                        editor.putLong("subscription_used_traffic", it) 
                                    }
                                    subscriptionInfo.totalTraffic?.let { 
                                        editor.putLong("subscription_total_traffic", it) 
                                    }
                                    editor.apply()
                                    
                                    // Обновляем уведомление через сервис
                                    if (viewModel.connectionStatus.value.isConnected) {
                                        try {
                                            val serviceIntent = Intent(this@MainActivity, KizVpnService::class.java)
                                            bindService(serviceIntent, object : ServiceConnection {
                                                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                                    val binder = service as? KizVpnService.KizVpnBinder
                                                    binder?.getService()?.setSubscriptionInfo(subscriptionInfo.days, subscriptionInfo.hours)
                                                    binder?.refreshSubscriptionInfo()
                                                    unbindService(this)
                                                }
                                                override fun onServiceDisconnected(name: ComponentName?) {}
                                            }, Context.BIND_AUTO_CREATE)
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error updating VPN service notification", e)
                                        }
                                    }
                                    
                                    Log.d("MainActivity", "✓ Active subscription refreshed: ${subscriptionInfo.format()}")
                                }
                                return@launch
                            }
                        }
                    }
                }
                Log.d("MainActivity", "refreshActiveSubscriptionFromBlocks: Active config not found in subscription_blocks")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error refreshing active subscription from blocks", e)
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
     * Проверка подписки из Subscription URL
     */
    private fun checkSubscriptionFromUrl(subscriptionUrl: String, forceUpdate: Boolean = false) {
        Log.d("MainActivity", "=== Checking subscription from URL ===")
        Log.d("MainActivity", "URL: $subscriptionUrl")
        Log.d("MainActivity", "Force update: $forceUpdate")
        
        // Если VPN был вручную отключен, не проверяем подписку (чтобы избежать автоподключения)
        // Исключение: если это принудительное обновление при смене конфига
        if (wasManuallyDisconnected && !forceUpdate) {
            Logger.debug(Logger.Tag.MAIN, "checkSubscriptionFromUrl: VPN был вручную отключен, пропускаем проверку (forceUpdate=$forceUpdate)")
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
                                                    saveSubscriptionInfo(updatedInfo)
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
                            
                            // Обновляем только subscription URL конфиги, не очищаем информацию для отдельных ключей
                            val parsedConfig = configParser.parseConfig(savedConfig)
                            val configKey = parsedConfig?.uuid ?: parsedConfig?.name ?: parsedConfig?.comment
                            
                            if (configKey != null) {
                                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                                val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                                if (!savedSubscriptionUrl.isNullOrBlank()) {
                                    Log.d("MainActivity", "Периодическое обновление: обновляем subscription URL")
                                    checkSubscriptionFromUrl(savedSubscriptionUrl)
                                } else {
                                    Log.d("MainActivity", "Периодическое обновление: пропуск для отдельного ключа")
                                }
                            } else {
                                Log.d("MainActivity", "Периодическое обновление: пропуск для конфига без ключа")
                            }
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
                
                // Защита от множественных быстрых нажатий
                val currentStatus = viewModel.connectionStatus.value
                if (currentStatus.isConnected || currentStatus.isConnecting || isVpnOperationInProgress.get()) {
                    Log.d("MainActivity", "onConnectClick: VPN already connected/connecting or operation in progress, ignoring")
                    return@AppNavHost
                }
                
                // Проверяем, есть ли активный конфиг
                val config = getSavedConfig()
                Log.d("MainActivity", "onConnectClick: Checking active config...")
                
                if (!config.isNullOrBlank()) {
                    Log.d("MainActivity", "onConnectClick: Active config found, connecting...")
                    
                    // Устанавливаем флаг для показа уведомления при подключении
                    shouldShowSubscriptionNotification = true
                    
                    // Подключаемся (startVpnConnection сам получит текущий активный конфиг)
                    connectToVpn(config)
                } else {
                    Log.w("MainActivity", "onConnectClick: No active config found!")
                    // Показываем уведомление
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
                
                // Очищаем информацию о подписке для отдельных конфигов
                Log.d("MainActivity", "onSaveWireGuardConfig: Очищаем подписку для standalone WireGuard конфига")
                clearAllSubscriptionInfo()
                
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
                        
                        // Проверяем, остались ли еще конфиги
                        val vlessListJson = prefs.getString("saved_vless_configs_list", "[]")
                        val wireGuardListJson = prefs.getString("saved_wireguard_configs_list", "[]")
                        val vlessConfigs = org.json.JSONArray(vlessListJson)
                        val wireGuardConfigs = org.json.JSONArray(wireGuardListJson)
                        val totalConfigs = vlessConfigs.length() + wireGuardConfigs.length()
                        
                        Log.d("MainActivity", "onDeleteConfig: Всего конфигов осталось: $totalConfigs")
                        
                        // Если конфигов не осталось, очищаем информацию о подписке
                        if (totalConfigs == 0) {
                            Log.d("MainActivity", "onDeleteConfig: Все конфиги удалены, очищаем подписку")
                            clearAllSubscriptionInfo()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Ошибка при удалении конфига", e)
                }
            },
            onSelectConfig = { config, protocol ->
                // При выборе конфига из списка, устанавливаем его как текущий
                Log.d("MainActivity", "onSelectConfig: protocol = $protocol, config = ${config.take(100)}...")
                
                // Проверяем, подключен ли VPN
                val wasConnected = connectionStatus.isConnected
                Log.d("MainActivity", "onSelectConfig: VPN was connected = $wasConnected")
                
                // Парсим конфиг
                val parsedConfig = configParser.parseConfig(config)
                val actualProtocol = parsedConfig?.protocol
                Log.d("MainActivity", "onSelectConfig: parsedProtocol = $actualProtocol")
                
                // Используем протокол из парсера, если он доступен
                val protocolToUse = actualProtocol ?: protocol
                Log.d("MainActivity", "onSelectConfig: protocolToUse = $protocolToUse")
                
                // ВАЖНО: Сохраняем конфиг как активный ПЕРЕД любыми другими операциями
                if (parsedConfig != null) {
                    saveConfig(config)
                    Log.d("MainActivity", "onSelectConfig: ✓ Конфиг сохранен через saveConfig")
                } else {
                    // Fallback: сохраняем напрямую
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
                            Log.d("MainActivity", "onSelectConfig: ✓ Vless конфиг сохранен в saved_config")
                        }
                        com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> {
                            prefsFallback.edit()
                                .putString("saved_wireguard_config", config)
                                .putString("active_config_type", configType)
                                .commit()
                            Log.d("MainActivity", "onSelectConfig: ✓ WireGuard конфиг сохранен в saved_wireguard_config")
                        }
                        else -> {
                            prefsFallback.edit().putString("active_config_type", configType).commit()
                        }
                    }
                }
                
                // Устанавливаем конфиг в ViewModel
                currentConfigInput = config
                viewModel.setVpnConfig(config)
                Log.d("MainActivity", "onSelectConfig: ✓ Конфиг установлен в ViewModel")
                
                // Загружаем информацию о подписке для выбранного конфига (только обновляем, не очищаем)
                val prefs = getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val configKey = parsedConfig?.uuid ?: parsedConfig?.name
                
                if (configKey != null) {
                    val savedSubscriptionUrl = prefs.getString("subscription_url_$configKey", null)
                    
                    if (!savedSubscriptionUrl.isNullOrBlank()) {
                        // Это конфиг из subscription URL - загружаем сохраненную информацию
                        Log.d("MainActivity", "onSelectConfig: Конфиг из subscription URL, загружаем информацию...")
                        val savedSubscriptionInfo = loadSubscriptionInfo()
                        if (savedSubscriptionInfo != null) {
                            Log.d("MainActivity", "onSelectConfig: ✓ Загружена информация о подписке")
                            this@MainActivity.subscriptionInfo = savedSubscriptionInfo
                            this@MainActivity.subscriptionInfoState.value = savedSubscriptionInfo
                            viewModel.updateSubscriptionInfo(savedSubscriptionInfo)
                        } else {
                            Log.d("MainActivity", "onSelectConfig: Нет сохраненной информации о подписке для этого конфига")
                            // НЕ очищаем существующую информацию - сохраняем принцип постоянства
                        }
                    } else {
                        // Это standalone конфиг - очищаем информацию о подписке
                        Log.d("MainActivity", "onSelectConfig: Standalone конфиг, очищаем информацию о подписке")
                        clearAllSubscriptionInfo()
                    }
                } else {
                    Log.d("MainActivity", "onSelectConfig: Нет config key, очищаем информацию о подписке")
                    clearAllSubscriptionInfo()
                }
                
                // Если VPN был подключен - отключаем его
                // Пользователь должен вручную включить VPN для подключения к новому конфигу
                if (wasConnected) {
                    Log.d("MainActivity", "onSelectConfig: VPN был подключен, отключаем...")
                    disconnectFromVpn()
                } else {
                    Log.d("MainActivity", "onSelectConfig: VPN не был подключен, конфиг готов к использованию")
                }
            },
            onShowNetworkChart = { },
            isVpnConnected = connectionStatus.isConnected,
            onUpdateSubscriptionInfo = { newSubscriptionInfo ->
                subscriptionInfoState.value = newSubscriptionInfo
                this.subscriptionInfo = newSubscriptionInfo
            }
        )
    }
    
}







