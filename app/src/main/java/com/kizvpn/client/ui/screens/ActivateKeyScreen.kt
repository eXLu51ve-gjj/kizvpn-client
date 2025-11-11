package com.kizvpn.client.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kizvpn.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivateKeyScreen(
    onBack: () -> Unit,
    onActivateKey: (String, (Int?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Загружаем сохраненный конфиг и его имя
    val prefs = remember { context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE) }
    var savedConfig by remember { mutableStateOf(prefs.getString("saved_config", null)) }
    var savedConfigName by remember { mutableStateOf(prefs.getString("saved_config_name", null)) }
    var configActivated by remember { mutableStateOf(prefs.getBoolean("config_activated", false)) }
    
    // Обновляем состояние после активации
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            savedConfig = prefs.getString("saved_config", null)
            savedConfigName = prefs.getString("saved_config_name", null)
            configActivated = prefs.getBoolean("config_activated", false)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Активировать ключ",
                    color = TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Показываем сохраненный конфиг, если он есть
            if (savedConfig != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (configActivated) NeonPrimary.copy(alpha = 0.2f) else CardDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                savedConfigName ?: "Сохраненный конфиг",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            if (savedConfig != null) {
                                Text(
                                    savedConfig!!.take(50) + if (savedConfig!!.length > 50) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (configActivated) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Активирован",
                                tint = NeonPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Инструкция
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardDark
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Вставьте ключ Vless",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Вставьте ключ Vless в поле ниже для получения подписки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            
            // Поле ввода ключа
            OutlinedTextField(
                value = keyInput,
                onValueChange = { 
                    keyInput = it
                    errorMessage = null
                    successMessage = null
                },
                label = { Text("Ключ активации", color = TextSecondary) },
                placeholder = { Text("Введите ключ...", color = TextSecondary.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = NeonPrimary,
                    unfocusedLabelColor = TextSecondary,
                    focusedBorderColor = NeonPrimary,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                    cursorColor = NeonPrimary
                ),
                enabled = !isActivating,
                singleLine = true
            )
            
            // Кнопка активации
            Button(
                onClick = {
                    if (keyInput.isBlank()) {
                        errorMessage = "Введите ключ активации"
                        return@Button
                    }
                    
                    isActivating = true
                    errorMessage = null
                    successMessage = null
                    
                    onActivateKey(keyInput) { days ->
                        isActivating = false
                        if (days != null) {
                            successMessage = "Ключ активирован! Подписка: $days дней"
                            keyInput = ""
                            // Обновляем состояние активации сразу
                            savedConfig = prefs.getString("saved_config", null)
                            savedConfigName = prefs.getString("saved_config_name", null)
                            configActivated = prefs.getBoolean("config_activated", false)
                        } else {
                            errorMessage = "Не удалось активировать ключ. Проверьте правильность ключа."
                        }
                    }
                },
                enabled = !isActivating && keyInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPrimary
                )
            ) {
                if (isActivating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Активация...", color = Color.White)
                } else {
                    Text("Активировать", color = Color.White)
                }
            }
            
            // Сообщение об ошибке
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusDisconnected.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusDisconnected,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Сообщение об успехе
            if (successMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = NeonPrimary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        successMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonPrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

