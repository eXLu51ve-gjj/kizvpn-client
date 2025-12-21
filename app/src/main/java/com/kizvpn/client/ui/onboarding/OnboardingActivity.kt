package com.kizvpn.client.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.kizvpn.client.MainActivity
import com.kizvpn.client.security.BiometricAuthManager
import com.kizvpn.client.ui.theme.KizVpnTheme
import com.kizvpn.client.util.LocalizationHelper

/**
 * Activity для первоначальной настройки приложения (onboarding)
 * Показывается только при первом запуске приложения
 */
class OnboardingActivity : FragmentActivity() {
    
    private val TAG = "OnboardingActivity"
    private val viewModel: OnboardingViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[OnboardingViewModel::class.java]
    }
    private lateinit var biometricAuthManager: BiometricAuthManager
    
    // Launcher для запроса разрешения на уведомления
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Notification permission result: $isGranted")
        viewModel.setNotificationPermissionResult(isGranted)
    }
    
    // Launcher для запроса разрешения на камеру
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Camera permission result: $isGranted")
        viewModel.setCameraPermissionResult(isGranted)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "OnboardingActivity created")
        
        // Инициализируем BiometricAuthManager
        biometricAuthManager = BiometricAuthManager(this)
        
        setContent {
            KizVpnTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    
                    OnboardingScreen(
                        uiState = uiState,
                        onLanguageSelected = { language ->
                            Log.d(TAG, "Language selected: $language")
                            // Сохраняем язык в SharedPreferences
                            val prefs = getSharedPreferences("vpn_settings", MODE_PRIVATE)
                            prefs.edit().putString("language", language).apply()
                            viewModel.setLanguage(language)
                            
                            // НЕ перезапускаем Activity - это нарушает анимацию
                            // Язык применится при следующем запуске приложения
                        },
                        onNextStep = {
                            viewModel.nextStep()
                        },
                        onPreviousStep = {
                            viewModel.previousStep()
                        },
                        onSkipStep = {
                            viewModel.skipCurrentStep()
                        },
                        onSkipAll = {
                            viewModel.skipAllOnboarding()
                            // Переходим в основное приложение БЕЗ вызова finishOnboarding
                            // чтобы избежать перезаписи настроек через saveSettings()
                            val intent = Intent(this@OnboardingActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        },
                        onRequestNotificationPermission = {
                            requestNotificationPermission()
                        },
                        onEnableBiometric = {
                            enableBiometricAuth()
                        },
                        onRequestCameraPermission = {
                            requestCameraPermission()
                        },
                        onToggleAutoConnect = { enabled ->
                            viewModel.setAutoConnect(enabled)
                        },
                        onToggleAutoAddConfigs = { enabled ->
                            viewModel.setAutoAddConfigs(enabled)
                        },
                        onFinishOnboarding = {
                            finishOnboarding()
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Запрос разрешения на уведомления (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                    viewModel.setNotificationPermissionResult(true)
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // На Android < 13 разрешение не требуется
            Log.d(TAG, "Notification permission not required on this Android version")
            viewModel.setNotificationPermissionResult(true)
        }
    }
    
    /**
     * Включение биометрической аутентификации
     */
    private fun enableBiometricAuth() {
        Log.d(TAG, "Enabling biometric authentication")
        
        if (!biometricAuthManager.isBiometricAvailable()) {
            Log.w(TAG, "Biometric authentication not available")
            viewModel.setBiometricResult(false, "Биометрическая аутентификация недоступна на этом устройстве")
            return
        }
        
        // Тестируем биометрическую аутентификацию
        biometricAuthManager.authenticate(
            activity = this,
            title = "Настройка биометрической защиты",
            subtitle = "Подтвердите настройку отпечатком пальца или Face ID",
            onSuccess = {
                Log.d(TAG, "Biometric authentication test successful")
                viewModel.setBiometricResult(true, null)
            },
            onError = { error ->
                Log.e(TAG, "Biometric authentication test failed: $error")
                viewModel.setBiometricResult(false, error)
            },
            onCancel = {
                Log.d(TAG, "Biometric authentication test cancelled")
                viewModel.setBiometricResult(false, "Настройка отменена")
            }
        )
    }
    
    /**
     * Запрос разрешения на камеру для QR-кодов
     */
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
                viewModel.setCameraPermissionResult(true)
            }
            else -> {
                Log.d(TAG, "Requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * Завершение onboarding и переход в основное приложение
     */
    private fun finishOnboarding() {
        Log.d(TAG, "Finishing onboarding")
        
        // Сохраняем все настройки
        viewModel.saveSettings()
        
        // Отмечаем что onboarding завершен
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("onboarding_completed", true)
            .apply()
        
        // Переходим в основное приложение
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        // Обрабатываем кнопку "Назад"
        if (viewModel.canGoBack()) {
            viewModel.previousStep()
        } else {
            // На первом экране - выходим из приложения
            super.onBackPressed()
        }
    }
}