package com.kizvpn.client.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.tooling.preview.Preview
import com.kizvpn.client.R
import com.kizvpn.client.ui.components.AboutModal
import com.kizvpn.client.ui.components.VpnConfigModal
import com.kizvpn.client.ui.components.BottomMenuButton
import com.kizvpn.client.ui.components.BottomMenuDropdown
import com.kizvpn.client.ui.components.HistoryModal
import com.kizvpn.client.ui.components.SettingsModal
import com.kizvpn.client.ui.components.StatisticsModal
import com.kizvpn.client.ui.components.TrafficGraph
import com.kizvpn.client.ui.components.VideoBackground
import com.kizvpn.client.ui.components.VideoButton
import com.kizvpn.client.ui.components.NetworkStatsModal
import com.kizvpn.client.ui.components.RoutingModal
import com.kizvpn.client.ui.models.ConnectionStatus
import com.kizvpn.client.ui.theme.BackgroundDark
import com.kizvpn.client.ui.theme.KizVpnTheme
import com.kizvpn.client.ui.theme.NeonAccent
import com.kizvpn.client.ui.theme.NeonPrimary
import com.kizvpn.client.ui.theme.StatusConnected
import com.kizvpn.client.ui.theme.StatusConnecting
import com.kizvpn.client.ui.theme.CardDark
import com.kizvpn.client.ui.theme.TextPrimary
import com.kizvpn.client.ui.theme.TextSecondary
import com.kizvpn.client.data.SubscriptionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    connectionStatus: ConnectionStatus = ConnectionStatus(),
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    trafficData: List<Float> = emptyList(),
    vpnConfig: String? = null,
    onConfigChange: (String) -> Unit = {},
    onScanQRCode: () -> Unit = {},
    onShowStatistics: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onActivateKey: (String, (Int?) -> Unit) -> Unit = { _, _ -> },
    onSaveWireGuardConfig: (String) -> Unit = {},
    onSelectConfig: (String, com.kizvpn.client.config.ConfigParser.Protocol) -> Unit = { _, _ -> },
    subscriptionInfo: SubscriptionInfo? = null,
    viewModel: com.kizvpn.client.ui.viewmodel.VpnViewModel? = null,
    configNotification: String? = null,
    onShowConfigNotification: (String) -> Unit = {} // Callback для показа уведомления сверху экрана
) {
    val isConnected = connectionStatus.isConnected
    val isConnecting = connectionStatus.isConnecting
    
    val connectColor by animateColorAsState(
        targetValue = when {
            isConnected -> StatusConnected
            isConnecting -> StatusConnecting
            else -> NeonPrimary
        }
    )
    
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isConnecting -> 1.1f // Увеличиваем при подключении
            isConnected -> 1.05f // Немного увеличиваем когда подключено
            else -> 1f // Обычный размер
        }
    )

    val backgroundColor = BackgroundDark // Всегда темная тема
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(1000),
        label = "traffic_graph"
    )
    
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showStatisticsModal by remember { mutableStateOf(false) } // Модальное окно статистики
    var showSettingsModal by remember { mutableStateOf(false) } // Модальное окно настроек
    var showHistoryModal by remember { mutableStateOf(false) } // Модальное окно истории
    var showAboutModal by remember { mutableStateOf(false) } // Модальное окно "О приложении"
    var showVpnConfigModal by remember { mutableStateOf(false) } // Модальное окно настройки VPN
    var showNetworkChartModal by remember { mutableStateOf(false) } // Модальное окно графика сети
    var showRoutingModal by remember { mutableStateOf(false) } // Модальное окно маршрутизации
    
    // Уведомление о подписке
    var showSubscriptionNotification by remember { mutableStateOf(false) }
    var subscriptionNotificationText by remember { mutableStateOf("") }
    
    // Уведомление о подключении/отключении (только сверху)
    var showConnectionNotification by remember { mutableStateOf(false) }
    var connectionNotificationText by remember { mutableStateOf("") }
    var previousConnectionState by remember { mutableStateOf<Boolean?>(null) }
    
    // Уведомление о конфигах
    var showConfigNotification by remember { mutableStateOf(false) }
    var configNotificationText by remember { mutableStateOf("") }
    
    // Показываем уведомление при изменении подписки
    LaunchedEffect(subscriptionInfo) {
        subscriptionInfo?.let { info ->
            if (!info.expired && (info.days > 0 || info.hours > 0 || info.unlimited)) {
                subscriptionNotificationText = "Подписка: ${info.format()}"
                showSubscriptionNotification = true
                delay(3000) // Показываем 3 секунды
                showSubscriptionNotification = false
            }
        }
    }
    
    // Показываем уведомление при изменении статуса подключения (только сверху)
    LaunchedEffect(isConnected) {
        if (previousConnectionState != null && previousConnectionState != isConnected) {
            connectionNotificationText = if (isConnected) "Подключено" else "Отключено"
            showConnectionNotification = true
            delay(3000) // Показываем 3 секунды
            showConnectionNotification = false
        }
        previousConnectionState = isConnected
    }
    
    // Показываем уведомление о конфигах
    LaunchedEffect(configNotification) {
        if (configNotification != null && configNotification.isNotBlank()) {
            configNotificationText = configNotification!!
            showConfigNotification = true
            delay(3000) // Показываем 3 секунды
            showConfigNotification = false
            configNotificationText = ""
        } else {
            // Если configNotification стал null или пустым, сразу скрываем уведомление
            showConfigNotification = false
            configNotificationText = ""
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Основной контент с blur эффектом (только контент, не меню)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showMenuDropdown) {
                        Modifier.blur(15.dp) // Размытие всего контента при открытом меню
                    } else {
                        Modifier
                    }
                )
        ) {
            // Видео фон (воспроизводится только когда VPN подключен)
            VideoBackground(
                modifier = Modifier.fillMaxSize(),
                isPlaying = isConnected // Воспроизводится только когда VPN подключен
            )
            Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars), // Отступ снизу для системной панели навигации
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Отступ сверху (с отступом для статус-бара)
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(16.dp)
            )
            
            // График трафика удален (будет добавлен позже с новым дизайном)
            
            Spacer(modifier = Modifier.weight(1f))
        }
        }
        
        // Центральная кнопка VPN - на одном уровне с боковыми кнопками
        VideoButton(
            onClick = {
                if (isConnected) {
                    onDisconnectClick()
                } else {
                    onConnectClick()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 50.dp) // Смещаем кнопку вниз
                .padding(bottom = 8.dp) // Минимальный отступ
                .windowInsetsPadding(WindowInsets.navigationBars)
                .scale(buttonScale) // Анимация масштаба при подключении
                .zIndex(1004f), // Выше меню и blur эффекта
            videoResId = if (isConnected) R.raw.kiz_vpnon else R.raw.kiz_vpnof,
            size = 230.dp, // Визуальный размер кнопки (уменьшен с 250.dp)
            clickableSize = 120.dp // Круглая зона нажатия (уменьшена еще больше)
        )
        
        // Левая кнопка - видео в левом нижнем углу
        VideoButton(
            onClick = { /* TODO: Добавить функцию */ },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp) // Уменьшили отступы, чтобы были ближе к углам
                .windowInsetsPadding(WindowInsets.navigationBars)
                .zIndex(1004f), // Увеличили zIndex, чтобы быть выше меню и blur эффекта
            videoResId = R.raw.kiz_vpnbutton777,
            size = 80.dp // Уменьшили размер боковых кнопок
        )
        
        // Правая кнопка - видео в правом нижнем углу (всегда видна, даже когда меню открыто)
        // Не открываем меню, если открыты модальные окна
        VideoButton(
            onClick = { 
                if (!showStatisticsModal && !showSettingsModal && !showHistoryModal && !showAboutModal && !showVpnConfigModal && !showRoutingModal) {
                    showMenuDropdown = !showMenuDropdown
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp) // Уменьшили отступы, чтобы были ближе к углам
                .windowInsetsPadding(WindowInsets.navigationBars)
                .zIndex(1004f), // Увеличили zIndex, чтобы быть выше меню и blur эффекта
            videoResId = R.raw.kiz_vpnbutton777,
            size = 80.dp // Уменьшили размер боковых кнопок
        )
        
        // Уведомление о подключении/отключении вверху экрана
        AnimatedVisibility(
            visible = showConnectionNotification,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1003f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f) // Стиль "Liquid Glass" как у меню
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Иконка логотипа
                    Image(
                        painter = painterResource(id = R.drawable.kiz_vpntop),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Текст уведомления (белый как в меню)
                    Text(
                        text = connectionNotificationText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }
        }
        
        // Уведомление о конфигах вверху экрана (стиль "Liquid Glass" как у меню)
        AnimatedVisibility(
            visible = showConfigNotification && !showConnectionNotification && !showSubscriptionNotification,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10000f) // Выше модального окна (9999f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f) // Стиль "Liquid Glass" как у меню
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Иконка логотипа
                    Image(
                        painter = painterResource(id = R.drawable.kiz_vpntop),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Текст уведомления (оранжевый цвет)
                    Text(
                        text = configNotificationText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFFF9500) // Оранжевый цвет
                    )
                }
            }
        }
        
        // Уведомление о подписке вверху экрана (стиль "Liquid Glass" как у меню)
        AnimatedVisibility(
            visible = showSubscriptionNotification && !showConnectionNotification && !showConfigNotification,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1002f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f) // Стиль "Liquid Glass" как у меню
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Иконка логотипа
                    Image(
                        painter = painterResource(id = R.drawable.kiz_vpntop),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Текст уведомления (белый как в меню)
                    Text(
                        text = subscriptionNotificationText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }
        }
        
        // Модальное окно статистики
        StatisticsModal(
            showStatistics = showStatisticsModal,
            onDismiss = { showStatisticsModal = false },
            onBackToMenu = {
                showStatisticsModal = false
                showMenuDropdown = true
            },
            connectionStatus = connectionStatus,
            viewModel = viewModel
        )
        
        // Модальное окно настроек
        SettingsModal(
            showSettings = showSettingsModal,
            onDismiss = { showSettingsModal = false },
            onBackToMenu = {
                showSettingsModal = false
                showMenuDropdown = true
            },
            subscriptionInfo = subscriptionInfo,
            onLogout = {
                // TODO: Добавить логику выхода
            },
            onOpenRouting = {
                showSettingsModal = false
                showRoutingModal = true
            }
        )
        
        // Модальное окно истории
        HistoryModal(
            showHistory = showHistoryModal,
            onDismiss = { showHistoryModal = false },
            onBackToMenu = {
                showHistoryModal = false
                showMenuDropdown = true
            }
        )
        
        // Модальное окно "О приложении"
        AboutModal(
            showAbout = showAboutModal,
            onDismiss = { 
                showAboutModal = false
                showMenuDropdown = true // Открываем меню при закрытии
            }
        )
        
        // Модальное окно активации ключа
        // Модальное окно настройки VPN (объединяет Vless и WireGuard)
        VpnConfigModal(
            showVpnConfig = showVpnConfigModal,
            onDismiss = { 
                showVpnConfigModal = false
                showMenuDropdown = true // Открываем меню при закрытии
            },
            onActivateKey = { key, callback -> onActivateKey(key, callback) },
            onSaveWireGuardConfig = { config -> onSaveWireGuardConfig(config) },
            onSelectConfig = { selectedConfig, selectedProtocol ->
                // При выборе конфига из списка, устанавливаем его как текущий
                onConfigChange(selectedConfig)
                // Вызываем callback для сохранения типа активного конфига
                onSelectConfig(selectedConfig, selectedProtocol)
            },
            onDisconnectClick = {
                // Отключаем VPN если он подключен
                if (isConnected || isConnecting) {
                    onDisconnectClick()
                }
            },
            onShowConfigNotification = { message ->
                // Показываем уведомление сверху экрана
                onShowConfigNotification(message)
            }
        )
        
        // Выпадающее меню - overlay поверх всего экрана (но ниже кнопок)
        // Не показываем главное меню, если открыты модальные окна
        BottomMenuDropdown(
            showMenu = showMenuDropdown && !showStatisticsModal && !showSettingsModal && !showHistoryModal && !showAboutModal && !showVpnConfigModal && !showNetworkChartModal && !showRoutingModal,
            onDismiss = { showMenuDropdown = false },
            onOpenSettings = {
                showMenuDropdown = false
                showSettingsModal = true
            },
            onScanQRCode = {
                onScanQRCode()
                showMenuDropdown = false
            },
            onShowStatistics = {
                showMenuDropdown = false
                showStatisticsModal = true
            },
            onShowHistory = {
                showMenuDropdown = false
                showHistoryModal = true
            },
            onShowAbout = {
                showMenuDropdown = false
                showAboutModal = true
            },
            onShowVpnConfig = {
                showVpnConfigModal = true
                showMenuDropdown = false
            },
            onShowNetworkChart = {
                showNetworkChartModal = true
                showMenuDropdown = false
            }
        )

        // Модальное окно маршрутизации
        RoutingModal(
            showRouting = showRoutingModal,
            onDismiss = { 
                showRoutingModal = false
                showMenuDropdown = true // Открываем меню при закрытии
            }
        )
        
        // Модальное окно графика сети
        if (viewModel != null) {
            val downloadChartData by viewModel.downloadChartData.collectAsState()
            val uploadChartData by viewModel.uploadChartData.collectAsState()
            val currentDownloadSpeed by viewModel.currentDownloadSpeed.collectAsState()
            val currentUploadSpeed by viewModel.currentUploadSpeed.collectAsState()
            
            // Форматируем общий трафик
            val totalDownloadedMB = (connectionStatus.downloadBytes / (1024f * 1024f))
            val totalUploadedMB = (connectionStatus.uploadBytes / (1024f * 1024f))
            
            val totalDownloadedStr = if (totalDownloadedMB >= 1000) {
                "%.1f GB".format(totalDownloadedMB / 1024f)
            } else {
                "%.1f MB".format(totalDownloadedMB)
            }
            
            val totalUploadedStr = if (totalUploadedMB >= 1000) {
                "%.1f GB".format(totalUploadedMB / 1024f)
            } else {
                "%.1f MB".format(totalUploadedMB)
            }
            
            // Вычисляем время сессии с автообновлением (используем ту же логику, что в StatisticsModal)
            var sessionTimeStr by remember { mutableStateOf("00:00") }
            
            LaunchedEffect(connectionStatus.isConnected, showNetworkChartModal) {
                while (connectionStatus.isConnected && showNetworkChartModal) {
                    val duration = viewModel.getConnectionDuration()
                    if (duration != null) {
                        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(duration)
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(duration) % 60
                        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                        sessionTimeStr = when {
                            hours > 0 -> "%dч %dм %dс".format(hours, minutes, seconds)
                            minutes > 0 -> "%dм %dс".format(minutes, seconds)
                            else -> "%dс".format(seconds)
                        }
                    }
                    delay(1000) // Обновляем каждую секунду
                }
                if (!connectionStatus.isConnected) {
                    sessionTimeStr = "00:00"
                }
            }
            
            NetworkStatsModal(
                showStats = showNetworkChartModal,
                onDismiss = { showNetworkChartModal = false },
                downloadData = downloadChartData,
                uploadData = uploadChartData,
                currentDownloadSpeed = currentDownloadSpeed,
                currentUploadSpeed = currentUploadSpeed,
                totalDownloaded = totalDownloadedStr,
                totalUploaded = totalUploadedStr,
                sessionTime = sessionTimeStr
            )
        }
    }
}

@Preview(showBackground = true, name = "Home Screen")
@Composable
private fun HomeScreenPreview() {
    KizVpnTheme(darkTheme = true) {
        HomeScreen(
            onOpenServers = {},
            onOpenSettings = {},
            connectionStatus = ConnectionStatus(isConnected = false, isConnecting = false),
            onConnectClick = {},
            onDisconnectClick = {},
            trafficData = List(60) { (Math.random() * 200 + 50).toFloat() },
            vpnConfig = null,
            onConfigChange = {},
            onScanQRCode = {},
            onShowStatistics = {},
            onShowHistory = {},
            onShowAbout = {},
            onActivateKey = { _, _ -> },
            subscriptionInfo = null
        )
    }
}
