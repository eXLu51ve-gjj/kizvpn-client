package com.kizvpn.client.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kizvpn.client.R
import com.kizvpn.client.ui.onboarding.steps.SetupWizardStep
import com.kizvpn.client.ui.onboarding.steps.WelcomeLanguageStep
import com.kizvpn.client.ui.onboarding.steps.NotificationsStep
import com.kizvpn.client.ui.onboarding.steps.BiometricSecurityStep
import com.kizvpn.client.ui.onboarding.steps.CameraPermissionStep
import com.kizvpn.client.ui.onboarding.steps.AdditionalSettingsStep
import com.kizvpn.client.ui.onboarding.components.CameraPermissionDialog

/**
 * Основной экран onboarding с модальным окном
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onLanguageSelected: (String) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onSkipStep: () -> Unit,
    onSkipAll: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onEnableBiometric: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onToggleAutoConnect: (Boolean) -> Unit,
    onToggleAutoAddConfigs: (Boolean) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Фоновое изображение
        Image(
            painter = painterResource(id = R.drawable.welcome_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Модальное окно в самом низу
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp), // Отступ только снизу
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent // Полностью прозрачное
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Контент текущего шага с анимацией
                    AnimatedContent(
                        targetState = uiState.currentStep,
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth }, // Справа
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ),
                                initialContentExit = slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth }, // Влево
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            )
                        },
                        label = "onboarding_step_transition"
                    ) { step ->
                        when (step) {
                            1 -> SetupWizardStep(
                                onStart = onNextStep,
                                onSkipAll = onSkipAll
                            )
                            2 -> WelcomeLanguageStep(
                                selectedLanguage = uiState.selectedLanguage,
                                onLanguageSelected = onLanguageSelected,
                                onNext = onNextStep
                            )
                            3 -> NotificationsStep(
                                isPermissionGranted = uiState.notificationPermissionGranted,
                                onRequestPermission = onRequestNotificationPermission,
                                onNext = onNextStep,
                                onPrevious = onPreviousStep,
                                onSkip = onSkipStep
                            )
                            4 -> BiometricSecurityStep(
                                isEnabled = uiState.biometricEnabled,
                                errorMessage = uiState.biometricErrorMessage,
                                onEnable = onEnableBiometric,
                                onNext = onNextStep,
                                onPrevious = onPreviousStep,
                                onSkip = onSkipStep
                            )
                            5 -> CameraPermissionStep(
                                isPermissionGranted = uiState.cameraPermissionGranted,
                                onRequestPermission = onRequestCameraPermission,
                                onNext = onNextStep,
                                onPrevious = onPreviousStep,
                                onSkip = onSkipStep
                            )
                            6 -> AdditionalSettingsStep(
                                autoConnectEnabled = uiState.autoConnectEnabled,
                                autoAddConfigsEnabled = uiState.autoAddConfigsEnabled,
                                cameraPermissionGranted = uiState.cameraPermissionGranted,
                                onToggleAutoConnect = onToggleAutoConnect,
                                onToggleAutoAddConfigs = onToggleAutoAddConfigs,
                                onRequestCameraPermission = onRequestCameraPermission,
                                onFinish = onFinishOnboarding,
                                onPrevious = onPreviousStep
                            )
                        }
                    }
                    
                    // Индикатор прогресса (кружочки) внизу - показываем только если не первый шаг
                    if (uiState.currentStep > 1) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        StepIndicator(
                            currentStep = uiState.currentStep - 1, // Вычитаем 1, так как первый шаг не считается
                            totalSteps = 5
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val stepNumber = index + 1
            val isActive = stepNumber <= currentStep
            val isCurrent = stepNumber == currentStep
            
            Box(
                modifier = Modifier
                    .size(6.dp) // Уменьшили с 8dp до 6dp
                    .background(
                        color = when {
                            isCurrent -> Color(0xFF9C27B0) // Фиолетовый для текущего
                            isActive -> Color(0xFF9C27B0).copy(alpha = 0.6f) // Полупрозрачный фиолетовый для пройденных
                            else -> Color(0xFFFF6B35).copy(alpha = 0.5f) // Оранжевый для будущих
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}