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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    subscriptionInfo: SubscriptionInfo? = null,
    onLogout: () -> Unit = {},
    context: Context? = null
) {
    var useAutoConnect by remember { mutableStateOf(false) }
    var useNotifications by remember { mutableStateOf(true) }
    
    // Отображаем актуальное значение подписки
    var currentSubscriptionInfo by remember { mutableStateOf(subscriptionInfo) }
    
    // Обновляем при изменении subscriptionInfo
    LaunchedEffect(subscriptionInfo) {
        currentSubscriptionInfo = subscriptionInfo
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = { 
                Text(
                    "Настройки",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Подписка
            SettingsSection(title = "Подписка") {
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
                        Column {
                            val info = currentSubscriptionInfo
                            Text(
                                text = info?.format() ?: "Не активирован",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (info != null && !info.expired && (info.days > 0 || info.hours > 0 || info.unlimited)) NeonPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Подключение
            SettingsSection(title = "Подключение") {
                SettingsSwitch(
                    title = "Автоподключение",
                    description = "Автоматически подключаться при запуске",
                    checked = useAutoConnect,
                    onCheckedChange = { useAutoConnect = it }
                )
            }

            // Уведомления
            SettingsSection(title = "Уведомления") {
                SettingsSwitch(
                    title = "Уведомления",
                    description = "Показывать уведомления о статусе VPN",
                    checked = useNotifications,
                    onCheckedChange = { useNotifications = it }
                )
            }
            
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

            // Выход
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusDisconnected
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Выйти", color = Color.White)
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

