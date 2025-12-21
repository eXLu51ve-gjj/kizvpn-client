package com.kizvpn.client.ui.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.kizvpn.client.ui.onboarding.components.CameraPermissionDialog
import com.kizvpn.client.ui.onboarding.components.ModernToggleSwitch

@Composable
fun AdditionalSettingsStep(
    autoConnectEnabled: Boolean,
    autoAddConfigsEnabled: Boolean = false, // Изменено: по умолчанию ВЫКЛЮЧЕНО
    cameraPermissionGranted: Boolean, // Оставляем для совместимости, но не используем
    onToggleAutoConnect: (Boolean) -> Unit,
    onToggleAutoAddConfigs: (Boolean) -> Unit = {}, // Новый callback
    onRequestCameraPermission: () -> Unit, // Оставляем для совместимости, но не используем
    onFinish: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок (оранжевый)
        Text(
            text = LocalizationHelper.getString(context, R.string.additional_settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B35), // Оранжевый цвет
            textAlign = TextAlign.Center
        )
        
        // Описание (оранжевый)
        Text(
            text = LocalizationHelper.getString(context, R.string.additional_settings_description),
            fontSize = 16.sp,
            color = Color(0xFFFF6B35), // Оранжевый цвет
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Настройка автоподключения
        SettingRow(
            icon = R.drawable.ic_settings_auto_connect,
            title = LocalizationHelper.getString(context, R.string.auto_connect_title),
            description = LocalizationHelper.getString(context, R.string.auto_connect_description),
            isEnabled = autoConnectEnabled,
            onToggle = onToggleAutoConnect
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Настройка автоматического добавления конфигов
        AutoAddConfigsSettingRow(
            icon = R.drawable.suburl,
            title = LocalizationHelper.getString(context, R.string.auto_add_configs_title),
            description = LocalizationHelper.getString(context, R.string.auto_add_configs_description),
            instruction = LocalizationHelper.getString(context, R.string.app_links_instruction),
            isEnabled = autoAddConfigsEnabled,
            onToggle = onToggleAutoAddConfigs
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки внизу бок о бок
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
            
            // Кнопка "Завершить настройку"
            Button(
                onClick = onFinish,
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
                    text = LocalizationHelper.getString(context, R.string.finish_setup),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: Int,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Иконка (белая)
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        
        // Текст
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Современный красивый переключатель
        ModernToggleSwitch(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun AutoAddConfigsSettingRow(
    icon: Int,
    title: String,
    description: String,
    instruction: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Иконка (белая)
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        
        // Текст
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            
            // Основное описание
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 2
            )
            
            // Инструкция оранжевым цветом (компактно)
            Text(
                text = instruction,
                fontSize = 11.sp,
                color = Color(0xFFFF6B35), // Оранжевый цвет
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2
            )
        }
        
        // Современный красивый переключатель
        ModernToggleSwitch(
            checked = isEnabled,
            onCheckedChange = onToggle
        )
    }
}