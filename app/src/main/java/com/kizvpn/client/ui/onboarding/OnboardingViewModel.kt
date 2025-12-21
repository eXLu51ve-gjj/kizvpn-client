package com.kizvpn.client.ui.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel для управления состоянием onboarding процесса
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "OnboardingViewModel"
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    /**
     * Переход к следующему шагу
     */
    fun nextStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep < 6) { // Максимум 6 шагов теперь
            Log.d(TAG, "Moving from step $currentStep to step ${currentStep + 1}")
            _uiState.value = _uiState.value.copy(currentStep = currentStep + 1)
        }
    }
    
    /**
     * Переход к предыдущему шагу
     */
    fun previousStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep > 1) { // Минимум 1 шаг
            Log.d(TAG, "Moving from step $currentStep to step ${currentStep - 1}")
            _uiState.value = _uiState.value.copy(currentStep = currentStep - 1)
        }
    }
    
    /**
     * Пропуск текущего шага
     */
    fun skipCurrentStep() {
        Log.d(TAG, "Skipping current step: ${_uiState.value.currentStep}")
        nextStep()
    }
    
    /**
     * Пропуск всего onboarding (переход сразу в главное меню)
     */
    fun skipAllOnboarding() {
        Log.d(TAG, "Skipping all onboarding steps")
        
        // Сохраняем минимальные настройки при пропуске
        val context = getApplication<Application>()
        val appPrefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        appPrefs.edit()
            .putString("selected_language", _uiState.value.selectedLanguage) // Сохраняем выбранный язык
            .putBoolean("onboarding_completed", true)
            .apply()
        
        // ЯВНО сохраняем все настройки как ВЫКЛЮЧЕННЫЕ при пропуске
        val vpnPrefs = context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
        vpnPrefs.edit()
            .putBoolean("notifications_enabled", false) // ВЫКЛЮЧЕНО
            .putBoolean("auto_connect", false) // ВЫКЛЮЧЕНО
            .putBoolean("security_enabled", false) // ВЫКЛЮЧЕНО
            .apply()
        
        Log.d(TAG, "All settings explicitly set to FALSE when skipping onboarding")
        
        _uiState.value = _uiState.value.copy(currentStep = 7) // Переходим к завершению
    }
    
    /**
     * Проверка возможности вернуться назад
     */
    fun canGoBack(): Boolean {
        return _uiState.value.currentStep > 1
    }
    
    /**
     * Установка выбранного языка
     */
    fun setLanguage(language: String) {
        Log.d(TAG, "Setting language: $language")
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
    }
    
    /**
     * Установка результата запроса разрешения на уведомления
     */
    fun setNotificationPermissionResult(granted: Boolean) {
        Log.d(TAG, "Notification permission result: $granted")
        _uiState.value = _uiState.value.copy(
            notificationPermissionGranted = granted,
            notificationPermissionRequested = true
        )
    }
    
    /**
     * Установка результата настройки биометрии
     */
    fun setBiometricResult(success: Boolean, errorMessage: String?) {
        Log.d(TAG, "Biometric setup result: success=$success, error=$errorMessage")
        _uiState.value = _uiState.value.copy(
            biometricEnabled = success,
            biometricSetupAttempted = true,
            biometricErrorMessage = errorMessage
        )
    }
    
    /**
     * Установка результата запроса разрешения на камеру
     */
    fun setCameraPermissionResult(granted: Boolean) {
        Log.d(TAG, "Camera permission result: $granted")
        _uiState.value = _uiState.value.copy(
            cameraPermissionGranted = granted,
            cameraPermissionRequested = true
        )
    }
    
    /**
     * Переключение автоподключения VPN
     */
    fun setAutoConnect(enabled: Boolean) {
        Log.d(TAG, "Auto-connect VPN: $enabled")
        _uiState.value = _uiState.value.copy(autoConnectEnabled = enabled)
    }
    
    /**
     * Переключение автоматического добавления конфигов
     */
    fun setAutoAddConfigs(enabled: Boolean) {
        Log.d(TAG, "Auto-add configs: $enabled")
        _uiState.value = _uiState.value.copy(autoAddConfigsEnabled = enabled)
        
        // Если включено - открываем настройки Android для ручного включения поддержки веб-адресов
        if (enabled) {
            openAppLinksSettings()
        }
    }
    
    /**
     * Открытие настроек App Links в Android
     */
    private fun openAppLinksSettings() {
        try {
            val context = getApplication<Application>()
            Log.d(TAG, "Opening Android App Links settings")
            
            // Создаем Intent для открытия настроек "Открывать по умолчанию"
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Проверяем, можем ли открыть настройки
            if (intent.resolveActivity(context.packageManager) != null) {
                Log.d(TAG, "Opening App Links settings for manual configuration")
                context.startActivity(intent)
            } else {
                Log.w(TAG, "App Links settings not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open App Links settings", e)
        }
    }
    
    /**
     * Сохранение всех настроек в SharedPreferences
     */
    fun saveSettings() {
        val context = getApplication<Application>()
        val state = _uiState.value
        
        Log.d(TAG, "Saving onboarding settings")
        
        // Сохраняем основные настройки приложения
        val appPrefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        appPrefs.edit()
            .putString("selected_language", state.selectedLanguage)
            .putBoolean("onboarding_completed", true)
            .apply()
        
        // Сохраняем настройки VPN
        val vpnPrefs = context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
        vpnPrefs.edit()
            .putBoolean("notifications_enabled", state.notificationPermissionGranted)
            .putBoolean("auto_connect", state.autoConnectEnabled) // Исправлено: было auto_connect_enabled
            .putBoolean("auto_add_configs", state.autoAddConfigsEnabled) // Новая настройка
            .apply()
        
        // Сохраняем настройки безопасности
        val securityPrefs = context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE) // Используем тот же файл
        securityPrefs.edit()
            .putBoolean("security_enabled", state.biometricEnabled) // Исправлено: было biometric_enabled
            .apply()
        
        Log.d(TAG, "Settings saved: language=${state.selectedLanguage}, " +
                "notifications=${state.notificationPermissionGranted}, " +
                "biometric=${state.biometricEnabled}, " +
                "autoConnect=${state.autoConnectEnabled}, " +
                "autoAddConfigs=${state.autoAddConfigsEnabled}, " +
                "camera=${state.cameraPermissionGranted}")
    }
}

/**
 * Состояние UI для onboarding
 */
data class OnboardingUiState(
    val currentStep: Int = 1, // Начинаем с 1 (первый шаг)
    val selectedLanguage: String = "ru", // По умолчанию русский
    val notificationPermissionGranted: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricSetupAttempted: Boolean = false,
    val biometricErrorMessage: String? = null,
    val cameraPermissionGranted: Boolean = false,
    val cameraPermissionRequested: Boolean = false,
    val autoConnectEnabled: Boolean = false,
    val autoAddConfigsEnabled: Boolean = false // По умолчанию ВЫКЛЮЧЕНО
)

/**
 * Шаги onboarding процесса
 */
enum class OnboardingStep {
    SETUP_WIZARD,        // Мастер настройки - Начать или Пропустить
    WELCOME_LANGUAGE,    // Приветствие + выбор языка
    NOTIFICATIONS,       // Настройка уведомлений
    BIOMETRIC_SECURITY,  // Настройка биометрической защиты
    CAMERA_PERMISSION,   // Разрешение камеры
    ADDITIONAL_SETTINGS  // Дополнительные настройки
}