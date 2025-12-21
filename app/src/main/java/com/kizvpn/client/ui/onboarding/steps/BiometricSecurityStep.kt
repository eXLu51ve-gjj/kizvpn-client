package com.kizvpn.client.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizvpn.client.R
import com.kizvpn.client.util.LocalizationHelper

@Composable
fun BiometricSecurityStep(
    isEnabled: Boolean,
    errorMessage: String?,
    onEnable: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit // Оставляем параметр для совместимости, но не используем
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Иконка безопасности (белая)
        Image(
            painter = painterResource(id = R.drawable.ic_settings_security),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        
        // Заголовок
        Text(
            text = LocalizationHelper.getString(context, R.string.biometric_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = LocalizationHelper.getString(context, R.string.enable_biometric_question),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        // Статус защиты
        if (isEnabled) {
            Text(
                text = LocalizationHelper.getString(context, R.string.biometric_enabled),
                fontSize = 14.sp,
                color = Color.Green,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Сообщение об ошибке (если есть)
        errorMessage?.let { error ->
            Text(
                text = error,
                fontSize = 14.sp,
                color = Color.Red,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Показываем кнопки в зависимости от состояния
        if (!isEnabled && errorMessage == null) {
            // Если биометрия не включена и нет ошибки - показываем кнопки выбора
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка "Включить"
                Button(
                    onClick = onEnable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = LocalizationHelper.getString(context, R.string.enable),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Кнопка "Пропустить"
                OutlinedButton(
                    onClick = onNext, // Пропустить = переход на следующий шаг
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = LocalizationHelper.getString(context, R.string.skip),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // В любом другом случае (биометрия включена ИЛИ есть ошибка) - показываем навигационные кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка "Назад"
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = LocalizationHelper.getString(context, R.string.back),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Кнопка "Продолжить" - ВСЕГДА доступна
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = LocalizationHelper.getString(context, R.string.continue_text),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}