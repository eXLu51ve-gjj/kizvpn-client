package com.kizvpn.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Context
import android.util.Log
import com.kizvpn.client.api.ApiEndpointTester
import com.kizvpn.client.ui.theme.BackgroundDark
import com.kizvpn.client.ui.theme.NeonPrimary
import com.kizvpn.client.ui.theme.StatusDisconnected
import com.kizvpn.client.ui.theme.TextPrimary
import com.kizvpn.client.ui.theme.TextSecondary
import com.kizvpn.client.ui.theme.CardDark
import com.kizvpn.client.data.SubscriptionInfo
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    subscriptionInfo: SubscriptionInfo? = null,
    onLogout: () -> Unit = {},
    context: Context? = null,
    onSubscriptionUrlCheck: ((String) -> Unit)? = null  // Callback для проверки subscription URL
) {
    // Загружаем количество конфигов из SharedPreferences
    val configsCount = remember {
        if (context != null) {
            val prefs = context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
            prefs.getInt("subscription_configs_count", 0)
        } else 0
    }
    // Загружаем сохраненные настройки
    var useAutoConnect by remember {
        mutableStateOf(
            if (context != null) {
                val prefs = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                prefs.getBoolean("auto_connect", false)
            } else false
        )
    }
    var useNotifications by remember {
        mutableStateOf(
            if (context != null) {
                val prefs = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                prefs.getBoolean("notifications_enabled", true) // По умолчанию включены
            } else true
        )
    }
    val scope = rememberCoroutineScope()
    
    // Отображаем актуальное значение подписки
    var currentSubscriptionInfo by remember { mutableStateOf(subscriptionInfo) }
    
    // Состояние для subscription URL
    var subscriptionUrl by remember { mutableStateOf("") }
    var isCheckingSubscriptionUrl by remember { mutableStateOf(false) }
    var subscriptionUrlError by remember { mutableStateOf<String?>(null) }
    var showSubscriptionUrlInput by remember { mutableStateOf(false) }
    
    // Обновляем при изменении subscriptionInfo (включая обновления после проверки URL)
    LaunchedEffect(subscriptionInfo) {
        currentSubscriptionInfo = subscriptionInfo
        // Если subscriptionInfo обновился и мы проверяли URL, сбрасываем состояние загрузки
        if (subscriptionInfo != null && isCheckingSubscriptionUrl) {
            isCheckingSubscriptionUrl = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        var showSubscriptionUrlDialog by remember { mutableStateOf(false) }
        
        TopAppBar(
            title = { 
                Text(
                    "Настройки",
                    color = TextPrimary
                ) 
            },
            navigationIcon = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary
                        )
                    }
                    TextButton(
                        onClick = { showSubscriptionUrlDialog = true },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    ) {
                        Text(
                            text = "Sub",
                            color = NeonPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )
        
        // Диалог для ввода Subscription URL
        if (showSubscriptionUrlDialog) {
            var dialogSubscriptionUrl by remember { mutableStateOf("") }
            var dialogError by remember { mutableStateOf<String?>(null) }
            var isChecking by remember { mutableStateOf(false) }
            
            AlertDialog(
                onDismissRequest = { 
                    showSubscriptionUrlDialog = false
                    dialogSubscriptionUrl = ""
                    dialogError = null
                },
                title = {
                    Text(
                        "Проверить Subscription URL",
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = dialogSubscriptionUrl,
                            onValueChange = { 
                                dialogSubscriptionUrl = it
                                dialogError = null
                            },
                            label = { Text("Subscription URL", color = TextSecondary) },
                            placeholder = { Text("https://host.kizvpn.ru/sub/...", color = TextSecondary.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonPrimary,
                                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                focusedLabelColor = TextSecondary,
                                unfocusedLabelColor = TextSecondary
                            ),
                            enabled = !isChecking,
                            singleLine = true
                        )
                        
                        if (dialogError != null) {
                            Text(
                                text = dialogError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusDisconnected
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (dialogSubscriptionUrl.isNotBlank()) {
                                isChecking = true
                                dialogError = null
                                onSubscriptionUrlCheck?.invoke(dialogSubscriptionUrl.trim())
                                // Закрываем диалог через небольшую задержку
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    isChecking = false
                                    showSubscriptionUrlDialog = false
                                    dialogSubscriptionUrl = ""
                                }
                            } else {
                                dialogError = "Введите Subscription URL"
                            }
                        },
                        enabled = !isChecking && dialogSubscriptionUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPrimary
                        )
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверка...", color = Color.White)
                        } else {
                            Text("Проверить", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showSubscriptionUrlDialog = false
                            dialogSubscriptionUrl = ""
                            dialogError = null
                        }
                    ) {
                        Text("Отмена", color = TextSecondary)
                    }
                },
                containerColor = CardDark,
                titleContentColor = TextPrimary,
                textContentColor = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Подписка и Настройка VPN теперь в главном меню
            // Оставляем только "О приложении"

            
            // О приложении
            SettingsSection(title = "О приложении") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "KIZ VPN",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Версия 2.2.1",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Назад
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusDisconnected
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Назад", color = Color.White)
            }
        }
    }
}

@Composable
private fun TestApiEndpointsButton() {
    var isTesting by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ApiEndpointTester.EndpointResult>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val tester = remember { ApiEndpointTester() }
    
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
            Button(
                onClick = {
                    if (!isTesting) {
                        isTesting = true
                        testResults = emptyList()
                        scope.launch {
                            val results = tester.testAllEndpoints()
                            testResults = results
                            isTesting = false
                            
                            // Логируем результаты
                            Log.d("SettingsScreen", "=== API Endpoints Test Results ===")
                            results.forEach { result ->
                                Log.d("SettingsScreen", result.toString())
                            }
                            Log.d("SettingsScreen", "=== End of Results ===")
                        }
                    }
                },
                enabled = !isTesting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверка...", color = Color.White)
                } else {
                    Text("Проверить API endpoints", color = Color.White)
                }
            }
            
            if (testResults.isNotEmpty()) {
                val available = testResults.count { it.isAvailable }
                val total = testResults.size
                Text(
                    text = "Найдено: $available из $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available > 0) NeonPrimary else TextSecondary
                )
                
                // Показываем только доступные endpoints
                val availableEndpoints = testResults.filter { it.isAvailable }
                if (availableEndpoints.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableEndpoints.forEach { result ->
                            Column(
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${result.method} ${result.path} (${result.statusCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonPrimary
                                )
                                // Показываем начало ответа для анализа
                                if (!result.responseBody.isNullOrBlank()) {
                                    val preview = result.responseBody.take(100)
                                    Text(
                                        text = "Ответ: $preview${if (result.responseBody.length > 100) "..." else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Доступные endpoints не найдены",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NeonPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CardDark
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonPrimary,
                    checkedTrackColor = NeonPrimary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

