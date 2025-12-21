package com.kizvpn.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.graphics.Typeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kizvpn.client.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import org.json.JSONArray
import org.json.JSONObject
import com.kizvpn.client.util.localizedString

/**
 * Кнопка меню с выпадающим окном и анимацией вращения
 */
@Composable
fun BottomMenuButton(
    onClick: () -> Unit,
    isMenuOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Анимация вращения иконки меню
    val rotation by animateFloatAsState(
        targetValue = if (isMenuOpen) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "menu_icon_rotation"
    )

    // Анимация масштаба при нажатии
    val scale by animateFloatAsState(
        targetValue = if (isMenuOpen) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "menu_icon_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .graphicsLayer {
                rotationZ = rotation
            },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp)
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = localizedString(R.string.menu_text),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Функция для получения шрифта Jura
 */
@Composable
fun getJuraFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember {
        try {
            // Попытка загрузить через R.font (предпочтительный способ)
            try {
                FontFamily(Font(R.font.jura, FontWeight.Normal))
            } catch (e: Exception) {
                // Fallback: поиск через resources
                val fontResId = context.resources.getIdentifier("jura", "font", context.packageName)
                if (fontResId != 0) {
                    FontFamily(Font(fontResId, FontWeight.Normal))
                } else {
                    // Последняя попытка через системный шрифт
                    val typeface = Typeface.create("Jura", Typeface.NORMAL)
                    if (typeface != null && typeface != Typeface.DEFAULT) {
                        FontFamily(typeface)
                    } else {
                        FontFamily.Default
                    }
                }
            }
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
}

/**
 * Состояние анимации для одного элемента меню
 */
class MenuItemAnimationState {
    val offset = Animatable(1f)
    val opacity = Animatable(0f)
    val scale = Animatable(0.8f)
    val rotation = Animatable(-10f)
}

/**
 * Выпадающее меню с улучшенной анимацией
 */
@Composable
fun BottomMenuDropdown(
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onScanQRCode: () -> Unit = {},
    onShowStatistics: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onActivateKey: () -> Unit = {},
    onShowWireGuard: () -> Unit = {},
    onShowVpnConfig: () -> Unit = {}, // Новая функция для настройки VPN
    onShowNetworkChart: () -> Unit = {}, // Новая функция для графика сети
    buttonPosition: androidx.compose.ui.geometry.Offset? = null,
    subscriptionInfo: com.kizvpn.client.data.SubscriptionInfo? = null,
    isVpnConnected: Boolean = false,
    onUpdateSubscriptionInfo: (com.kizvpn.client.data.SubscriptionInfo) -> Unit = {},
    viewModel: com.kizvpn.client.ui.viewmodel.VpnViewModel? = null,
    biometricAuthManager: com.kizvpn.client.security.BiometricAuthManager? = null // Добавляем BiometricAuthManager
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Состояние для пинга
    var currentPing by remember { mutableStateOf<Int?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    
    // Функция для получения пинга текущего сервера
    fun pingCurrentServer() {
        if (isPinging || !isVpnConnected) {
            android.util.Log.d("BottomMenuDropdown", "Ping skipped: isPinging=$isPinging, isVpnConnected=$isVpnConnected")
            return
        }
        
        coroutineScope.launch {
            isPinging = true
            android.util.Log.d("BottomMenuDropdown", "Starting ping...")
            try {
                // Получаем текущий конфиг используя ту же логику что и getSavedConfig
                val prefs = context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE)
                val activeConfigType = prefs.getString("active_config_type", null)
                android.util.Log.d("BottomMenuDropdown", "Active config type: $activeConfigType")
                
                val vlessConfig = prefs.getString("saved_config", null)
                val wireGuardConfig = prefs.getString("saved_wireguard_config", null)
                
                val currentConfig = when (activeConfigType) {
                    "wireguard" -> {
                        if (!wireGuardConfig.isNullOrBlank()) {
                            android.util.Log.d("BottomMenuDropdown", "Using WireGuard config")
                            wireGuardConfig
                        } else {
                            android.util.Log.w("BottomMenuDropdown", "WireGuard config is null but activeConfigType = wireguard")
                            null
                        }
                    }
                    "vless", null -> {
                        if (!vlessConfig.isNullOrBlank()) {
                            android.util.Log.d("BottomMenuDropdown", "Using VLESS config")
                            vlessConfig
                        } else if (!wireGuardConfig.isNullOrBlank()) {
                            android.util.Log.d("BottomMenuDropdown", "No VLESS config, using WireGuard as fallback")
                            wireGuardConfig
                        } else {
                            android.util.Log.w("BottomMenuDropdown", "No configs available")
                            null
                        }
                    }
                    else -> {
                        android.util.Log.w("BottomMenuDropdown", "Unknown activeConfigType: $activeConfigType")
                        vlessConfig ?: wireGuardConfig
                    }
                }
                
                android.util.Log.d("BottomMenuDropdown", "Current config length: ${currentConfig?.length ?: 0}")
                
                if (currentConfig != null) {
                    // Парсим конфиг для получения хоста и порта
                    val configParser = com.kizvpn.client.config.ConfigParser()
                    val parsedConfig = configParser.parseConfig(currentConfig)
                    
                    android.util.Log.d("BottomMenuDropdown", "Parsed config - server: ${parsedConfig?.server}, port: ${parsedConfig?.port}, protocol: ${parsedConfig?.protocol}")
                    android.util.Log.d("BottomMenuDropdown", "Config preview: ${currentConfig.take(100)}...")
                    
                    // Для VLESS используем server, для WireGuard тоже server (из endpoint)
                    val serverHost = parsedConfig?.server
                    val serverPort = parsedConfig?.port
                    
                    if (serverHost != null && serverPort != null) {
                        android.util.Log.d("BottomMenuDropdown", "Pinging $serverHost:$serverPort")
                        val ping = com.kizvpn.client.util.PingUtil.pingServer(
                            serverHost, 
                            serverPort
                        )
                        android.util.Log.d("BottomMenuDropdown", "Ping result: $ping ms")
                        currentPing = ping
                        
                        // Обновляем пинг в VPN сервисе для уведомления
                        if (ping != null && ping > 0) {
                            updateVpnServicePing(context, ping)
                        }
                    } else {
                        android.util.Log.w("BottomMenuDropdown", "No server or port in parsed config - server: $serverHost, port: $serverPort")
                        
                        // Fallback: попробуем извлечь хост и порт вручную для простых случаев
                        try {
                            var fallbackHost: String? = null
                            var fallbackPort: Int? = null
                            
                            if (currentConfig.startsWith("vless://")) {
                                // Простой парсинг VLESS: vless://uuid@host:port?...
                                val hostPortRegex = Regex("@([^:]+):(\\d+)")
                                val match = hostPortRegex.find(currentConfig)
                                if (match != null) {
                                    fallbackHost = match.groupValues[1]
                                    fallbackPort = match.groupValues[2].toIntOrNull()
                                }
                            } else if (currentConfig.contains("Endpoint")) {
                                // Простой парсинг WireGuard: Endpoint = host:port
                                val endpointRegex = Regex("Endpoint\\s*=\\s*([^:]+):(\\d+)")
                                val match = endpointRegex.find(currentConfig)
                                if (match != null) {
                                    fallbackHost = match.groupValues[1]
                                    fallbackPort = match.groupValues[2].toIntOrNull()
                                }
                            }
                            
                            if (fallbackHost != null && fallbackPort != null) {
                                android.util.Log.d("BottomMenuDropdown", "Using fallback parsing - pinging $fallbackHost:$fallbackPort")
                                val ping = com.kizvpn.client.util.PingUtil.pingServer(
                                    fallbackHost, 
                                    fallbackPort
                                )
                                android.util.Log.d("BottomMenuDropdown", "Fallback ping result: $ping ms")
                                currentPing = ping
                                
                                // Обновляем пинг в VPN сервисе для уведомления
                                if (ping != null && ping > 0) {
                                    updateVpnServicePing(context, ping)
                                }
                            } else {
                                android.util.Log.w("BottomMenuDropdown", "Fallback parsing also failed")
                                currentPing = null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BottomMenuDropdown", "Fallback parsing error", e)
                            currentPing = null
                        }
                    }
                } else {
                    android.util.Log.w("BottomMenuDropdown", "No current config found")
                    currentPing = null
                }
            } catch (e: Exception) {
                android.util.Log.e("BottomMenuDropdown", "Error pinging server", e)
                currentPing = null
            } finally {
                isPinging = false
                android.util.Log.d("BottomMenuDropdown", "Ping finished, result: $currentPing")
            }
        }
    }
    
    // Периодический пинг при подключенном VPN
    LaunchedEffect(isVpnConnected, showMenu) {
        if (isVpnConnected && showMenu) {
            // Пингуем сразу при открытии меню
            pingCurrentServer()
            
            // Затем пингуем каждые 10 секунд
            while (isVpnConnected && showMenu) {
                delay(10000) // 10 секунд
                pingCurrentServer()
            }
        }
    }
    
    // Создаем состояние для хранения актуальной информации о подписке
    // Всегда используем информацию из ViewModel, если она доступна, иначе используем переданную
    val viewModelSubscriptionInfo by viewModel?.subscriptionInfo?.collectAsState() ?: remember { mutableStateOf(null) }
    var effectiveSubscriptionInfo by remember {
        mutableStateOf(viewModelSubscriptionInfo ?: subscriptionInfo)
    }
    
    // Обновляем информацию при изменении состояния VPN или при открытии меню
    LaunchedEffect(subscriptionInfo, showMenu, viewModelSubscriptionInfo) {
        // Всегда используем информацию из ViewModel как приоритетную
        // НЕ зависим от состояния VPN - подписка должна быть постоянной
        effectiveSubscriptionInfo = viewModelSubscriptionInfo ?: subscriptionInfo
    }

    // Создаем состояния анимации для каждого элемента (теперь 8 элементов)
    val animationStates = remember {
        List(8) { MenuItemAnimationState() }
    }

    // Задержки для анимации (в миллисекундах)
    val animationDelays = listOf(0, 60, 120, 180, 240, 300, 360, 420)

    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }

    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showMenu) 0.4f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "menu_background_alpha"
    )
    
    // Управление анимациями
    LaunchedEffect(showMenu) {
    if (showMenu) {
            isVisible = true
            // Анимация открытия (снизу вверх)
            animationStates.forEachIndexed { index, state ->
                coroutineScope.launch {
                    delay(animationDelays[index].toLong())
                    // Параллельный запуск всех анимаций для плавности
                    launch {
                        state.offset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        } else {
            // Анимация закрытия (сверху вниз)
            animationStates.reversed().forEachIndexed { reversedIndex, state ->
                coroutineScope.launch {
                    delay(animationDelays[reversedIndex].toLong())
                    // Параллельный запуск всех анимаций
                    launch {
                        state.offset.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 0.8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 10f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
            // Ждем завершения всех анимаций перед скрытием
            delay((animationDelays.last() + 300).toLong())
            isVisible = false
        }
    }

    // InteractionSource для фона (чтобы убрать визуальные эффекты нажатия)
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showMenu) {
        // Затемняющий фон - разделяем фон и область нажатия для избежания визуальных эффектов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f)
        ) {
            // Фон с фиксированным цветом
            Box(
                modifier = Modifier
                    .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            // Прозрачная область для нажатия без визуальных эффектов
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null, // Убираем ripple-эффект
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                    )
            )
        }

        // Меню
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.6f) // немного шире, как просили
                    .padding(end = 0.dp, top = 16.dp, bottom = 100.dp) // без правого отступа – капсулы прижаты к краю
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Пункты меню (текст + иконка)
                val menuItems = listOf(
                    Triple(localizedString(R.string.menu_subscription), R.drawable.ic_menu_subscription) {
                        onShowVpnConfig(); onDismiss()
                    },
                    Triple(localizedString(R.string.show_chart), R.drawable.ic_menu_chart) {
                        onShowNetworkChart(); onDismiss()
                    },
                    Triple(localizedString(R.string.menu_statistics), R.drawable.ic_menu_statistics) {
                        onShowStatistics(); onDismiss()
                    },
                    Triple(localizedString(R.string.menu_history), R.drawable.ic_menu_history) {
                        onShowHistory(); onDismiss()
                    },
                    Triple(localizedString(R.string.menu_settings), R.drawable.ic_menu_settings) {
                        onOpenSettings(); onDismiss()
                    },
                    Triple(localizedString(R.string.menu_about), R.drawable.ic_menu_about) {
                        onShowAbout(); onDismiss()
                    }
                )

                menuItems.forEachIndexed { index, (text, iconResId, action) ->
                    // Для первого пункта (Подписка) показываем дополнительную информацию когда есть данные о подписке
                    if (index == 0 && effectiveSubscriptionInfo != null) {
                        AnimatedMenuItemWithSubscription(
                            text = text,
                            animationState = animationStates[7 - index],
                            iconResId = iconResId,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                action()
                            },
                            zenterFontFamily = zenterFontFamily,
                            subscriptionInfo = effectiveSubscriptionInfo!!, // Явно указываем, что значение не null
                            currentPing = currentPing,
                            isPinging = isPinging
                        )
                    } else {
                        AnimatedMenuItem(
                            text = text,
                            animationState = animationStates[7 - index],
                            iconResId = iconResId,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                action()
                            },
                            zenterFontFamily = zenterFontFamily
                        )
                    }
                }
            }
        }
    }
}

/**
 * Модальное окно "О приложении" в стиле белого диалога с закругленными углами
 */
@Composable
fun AboutModal(
    showAbout: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Получаем версию приложения
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "2.2.2"
        } catch (e: Exception) {
            "2.2.2"
        }
    }
    
    // Управление видимостью overlay (держим пока анимация не завершится)
    var isOverlayVisible by remember { mutableStateOf(false) }
    
    // Управление видимостью для AnimatedVisibility (с задержкой для анимации входа)
    var isContentVisible by remember { mutableStateOf(false) }
    
    // Анимация фона (как у капсул)
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showAbout) 0.4f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "about_background_alpha"
    )
    
    // Управление видимостью для правильной анимации входа и выхода
    LaunchedEffect(showAbout) {
        if (showAbout) {
            // Сначала показываем overlay
            isOverlayVisible = true
            // Небольшая задержка, чтобы AnimatedVisibility начал с visible=false и анимировал вход
            delay(50)
            isContentVisible = true
        } else {
            // Сначала скрываем контент (с анимацией)
            isContentVisible = false
            // Ждем завершения анимации закрытия, потом скрываем overlay
            delay(450)
            isOverlayVisible = false
        }
    }
    
    // Функции для открытия Email и Telegram
    val openEmail = {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("nml5222600@mail.ru"))
                putExtra(Intent.EXTRA_SUBJECT, "")
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_email)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    val openTelegram = {
        try {
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/eXLu51ve"))
            context.startActivity(telegramIntent)
        } catch (e: Exception) {
            // Если не удалось открыть Telegram, пытаемся через браузер
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/eXLu51ve"))
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isOverlayVisible || showAbout) {
        // Затемняющий фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                    )
            )
        }
        
        // Модальное окно "О приложении" - поверх всех элементов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1002f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp), // Как у "Подписки"
            contentAlignment = Alignment.Center
        ) {
            // Анимация выезда справа в центр (как у капсул)
            AnimatedVisibility(
                visible = isContentVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // Убираем визуальный эффект нажатия
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f) // Фон как у капсул
                    ),
                    shape = MaterialTheme.shapes.large, // Закругленные углы
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp), // Компактнее: было 24.dp
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Компактнее: было 16.dp
                    ) {
                        // Заголовок "KIZ VPN" (мягкий синий) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "KIZ VPN",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 20.sp, // Компактнее: было 24.sp
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color(0xFF5B9BD5),
                            textAlign = TextAlign.Center
                        )
                        
                        // Подзаголовок с легкой обводкой
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 14.sp, // Компактнее: было 16.sp
                                fontWeight = FontWeight.Medium,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Описание с тенью
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.app_description),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 12.sp, // Компактнее: было 14.sp
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // "Что мы предлагаем" (оранжевый) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.what_we_offer),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 16.sp, // Компактнее: было 18.sp
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            color = Color(0xFFFF9500),
                            textAlign = TextAlign.Center
                        )
                        
                        // Список преимуществ с тенями
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.Text(
                                text = localizedString(R.string.privacy_protection),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp, // Компактнее: было 14.sp
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = localizedString(R.string.high_speed),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp, // Компактнее: было 14.sp
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = localizedString(R.string.completely_free),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp, // Компактнее: было 14.sp
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = localizedString(R.string.easy_to_use),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp, // Компактнее: было 14.sp
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // "Важно" (оранжевый) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.important),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 16.sp, // Компактнее: было 18.sp
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            color = Color(0xFFFF9500),
                            textAlign = TextAlign.Center
                        )
                        
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.beta_warning),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 14.sp,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // "Связь с разработчиком" (оранжевый) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = localizedString(R.string.contact_developer),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 16.sp, // Компактнее: было 18.sp
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            color = Color(0xFFFF9500),
                            textAlign = TextAlign.Center
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Кликабельный Email с иконкой
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            openEmail()
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = Color(0xFF5B9BD5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(
                                    text = "nml5222600@mail.ru",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 12.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFF5B9BD5),
                                    textAlign = TextAlign.Center
                                )
                            }
                            // Кликабельный Telegram с иконкой
                            Row(
                                modifier = Modifier
                                    .clickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            openTelegram()
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Telegram",
                                    tint = Color(0xFF5B9BD5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(
                                    text = "@eXLu51ve",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 12.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFF5B9BD5),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Версия сборки и ник разработчика в одной строке
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Версия сборки (слева) с легкой обводкой
                            androidx.compose.material3.Text(
                                text = localizedString(R.string.version_text).format(versionName),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            
                            // Ник разработчика (красный, справа) с легкой обводкой
                            androidx.compose.material3.Text(
                                text = "eXLu51ve",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.4f),
                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                        blurRadius = 2f
                                    )
                                ),
                                color = Color(0xFFFF3B30) // Красный цвет
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            }
        }
    }
}

/**
 * Модальное окно активации ключа
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivateKeyModal(
    showActivateKey: Boolean,
    onDismiss: () -> Unit,
    onActivateKey: (String, (Int?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    var keyInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Загружаем сохраненный конфиг и его имя
    val prefs = remember { context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE) }
    var savedConfig by remember { mutableStateOf(prefs.getString("saved_config", null)) }
    var savedConfigName by remember { mutableStateOf(prefs.getString("saved_config_name", null)) }
    var configActivated by remember { mutableStateOf(prefs.getBoolean("config_activated", false)) }
    
    // Создаем состояние анимации для модального окна
    val animationState = remember { MenuItemAnimationState() }
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showActivateKey) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "activate_key_background_alpha"
    )
    
    // Управление анимациями
    LaunchedEffect(showActivateKey) {
        if (showActivateKey) {
            isVisible = true
            // Анимация открытия (медленное появление из центра с масштабированием)
            coroutineScope.launch {
                animationState.scale.snapTo(0.8f)
                animationState.opacity.snapTo(0f)
                
                launch {
                    animationState.scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
            }
        } else {
            // Анимация закрытия (плавное затухание)
            coroutineScope.launch {
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animationState.scale.animateTo(
                        targetValue = 0.9f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
                delay(300)
                isVisible = false
            }
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showActivateKey) {
        // Затемняющий фон
        Box(
                        modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                            .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                                onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                }
                            )
            )
        }
        
        // Модальное окно активации ключа - поверх всех элементов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(9999f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .alpha(animationState.opacity.value)
                    .scale(animationState.scale.value)
            ) {
                Card(
                        modifier = Modifier
                            .fillMaxWidth()
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2E) // Темно-серый фон
                    ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Заголовок
                        androidx.compose.material3.Text(
                            text = context.getString(R.string.paste_vless_key),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color(0xFF5B9BD5),
                            textAlign = TextAlign.Center
                        )
                        
                        // Показываем сохраненный конфиг, если он есть
                        if (savedConfig != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (configActivated) Color(0xFF5B9BD5).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f)
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
                                        androidx.compose.material3.Text(
                                            savedConfigName ?: "Сохраненный конфиг",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Default,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color.Black.copy(alpha = 0.3f),
                                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                                    blurRadius = 2f
                                                )
                                            ),
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                        if (savedConfig != null) {
                                            androidx.compose.material3.Text(
                                                savedConfig!!.take(50) + if (savedConfig!!.length > 50) "..." else "",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Default,
                                                    fontSize = 12.sp,
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = Color.Black.copy(alpha = 0.3f),
                                                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                                        blurRadius = 2f
                                                    )
                                                ),
                                                color = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    if (configActivated) {
                                        androidx.compose.material3.Icon(
                                            androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = "Активирован",
                                            tint = Color(0xFF5B9BD5),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
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
                            label = { 
                                androidx.compose.material3.Text(
                                    "Ключ активации", 
                                    color = Color.White.copy(alpha = 0.7f)
                                ) 
                            },
                            placeholder = { 
                                androidx.compose.material3.Text(
                                    "Введите ключ...", 
                                    color = Color.White.copy(alpha = 0.5f)
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF5B9BD5),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                focusedBorderColor = Color(0xFF5B9BD5),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = Color(0xFF5B9BD5)
                            ),
                            enabled = !isActivating,
                            singleLine = false
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
                                        // Обновляем состояние активации
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
                                containerColor = Color(0xFF5B9BD5)
                            )
                        ) {
                            if (isActivating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(context.getString(R.string.activating), color = Color.White)
                            } else {
                                androidx.compose.material3.Text(context.getString(R.string.activate), color = Color.White)
                            }
                        }
                        
                        // Сообщение об ошибке
                        if (errorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFFFF3B30),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Сообщение об успехе
                        if (successMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF5B9BD5).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    successMessage!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFF5B9BD5),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Кнопка "Закрыть"
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B2323).copy(alpha = 0.9f)
                            )
                        ) {
                            androidx.compose.material3.Text(localizedString(R.string.close), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Модальное окно для WireGuard конфига
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WireGuardModal(
    showWireGuard: Boolean,
    onDismiss: () -> Unit,
    onSaveConfig: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configParser = remember { com.kizvpn.client.config.ConfigParser() }
    
    var configInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Загружаем сохраненный WireGuard конфиг
    val prefs = remember { context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE) }
    var savedWireGuardConfig by remember { mutableStateOf(prefs.getString("saved_wireguard_config", null)) }
    
    // Создаем состояние анимации для модального окна
    val animationState = remember { MenuItemAnimationState() }
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showWireGuard) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "wireguard_background_alpha"
    )
    
    // Управление анимациями
    LaunchedEffect(showWireGuard) {
        if (showWireGuard) {
            isVisible = true
            coroutineScope.launch {
                animationState.scale.snapTo(0.8f)
                animationState.opacity.snapTo(0f)
                
                launch {
                    animationState.scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
            }
        } else {
            coroutineScope.launch {
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animationState.scale.animateTo(
                        targetValue = 0.9f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
                delay(300)
                isVisible = false
            }
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showWireGuard) {
        // Затемняющий фон
        Box(
                        modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                            .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                                onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                }
                            )
            )
        }
        
        // Модальное окно WireGuard - поверх всех элементов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(9999f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f)
                    .alpha(animationState.opacity.value)
                    .scale(animationState.scale.value)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2E) // Темно-серый фон
                    ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Заголовок
                        androidx.compose.material3.Text(
                            text = "WireGuard конфиг",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color(0xFF5B9BD5),
                            textAlign = TextAlign.Center
                        )
                        
                        // Инструкция
                        androidx.compose.material3.Text(
                            text = context.getString(R.string.paste_wireguard_config),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 14.sp,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        
                        // Показываем сохраненный WireGuard конфиг, если он есть
                        if (savedWireGuardConfig != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF5B9BD5).copy(alpha = 0.2f)
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
                                        androidx.compose.material3.Text(
                                            "WireGuard конфиг сохранен",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Default,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color.Black.copy(alpha = 0.3f),
                                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                                    blurRadius = 2f
                                                )
                                            ),
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                        androidx.compose.material3.Text(
                                            savedWireGuardConfig!!.take(50) + if (savedWireGuardConfig!!.length > 50) "..." else "",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Default,
                                                fontSize = 12.sp,
                                                shadow = androidx.compose.ui.graphics.Shadow(
                                                    color = Color.Black.copy(alpha = 0.3f),
                                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                                    blurRadius = 2f
                                                )
                                            ),
                                            color = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    androidx.compose.material3.Icon(
                                        androidx.compose.material.icons.Icons.Default.CheckCircle,
                                        contentDescription = "Сохранено",
                                        tint = Color(0xFF5B9BD5),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        
                        // Поле ввода конфига
                        OutlinedTextField(
                            value = configInput,
                            onValueChange = { 
                                configInput = it
                                errorMessage = null
                                successMessage = null
                            },
                            label = { 
                                androidx.compose.material3.Text(
                                    "WireGuard конфиг", 
                                    color = Color.White.copy(alpha = 0.7f)
                                ) 
                            },
                            placeholder = { 
                                androidx.compose.material3.Text(
                                    "Вставьте конфиг WireGuard...", 
                                    color = Color.White.copy(alpha = 0.5f)
                                ) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF5B9BD5),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                focusedBorderColor = Color(0xFF5B9BD5),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = Color(0xFF5B9BD5)
                            ),
                            enabled = !isSaving,
                            singleLine = false,
                            maxLines = 10
                        )
                        
                        // Кнопка сохранения
                        Button(
                            onClick = {
                                if (configInput.isBlank()) {
                                    errorMessage = "Введите WireGuard конфиг"
                                    return@Button
                                }
                                
                                // Проверяем, является ли это WireGuard конфигом
                                val parsedConfig = configParser.parseConfig(configInput.trim())
                                if (parsedConfig == null || parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                                    errorMessage = "Это не является валидным WireGuard конфигом"
                                    return@Button
                                }
                                
                                isSaving = true
                                errorMessage = null
                                successMessage = null
                                
                                // Сохраняем конфиг
                                prefs.edit().putString("saved_wireguard_config", configInput.trim()).apply()
                                savedWireGuardConfig = configInput.trim()
                                
                                // Вызываем callback для сохранения в основное приложение
                                onSaveConfig(configInput.trim())
                                
                                isSaving = false
                                successMessage = "WireGuard конфиг успешно сохранен!"
                                configInput = ""
                            },
                            enabled = !isSaving && configInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5B9BD5)
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(context.getString(R.string.saving), color = Color.White)
                            } else {
                                androidx.compose.material3.Text(context.getString(R.string.save), color = Color.White)
                            }
                        }
                        
                        // Сообщение об ошибке
                        if (errorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFFFF3B30),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Сообщение об успехе
                        if (successMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF5B9BD5).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    successMessage!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 14.sp,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                            blurRadius = 2f
                                        )
                                    ),
                                    color = Color(0xFF5B9BD5),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Кнопка "Закрыть"
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B2323).copy(alpha = 0.9f)
                            )
                        ) {
                            androidx.compose.material3.Text(localizedString(R.string.close), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Универсальный компонент для анимированных карточек в модальных окнах
 */
@Composable
fun AnimatedModalCard(
    animationState: MenuItemAnimationState,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (animationState.offset.value * 250).dp)
            .alpha(animationState.opacity.value)
            .scale(animationState.scale.value)
            .graphicsLayer {
                rotationZ = animationState.rotation.value
            }
    ) {
        content()
    }
}

/**
 * Улучшенный компонент пункта меню с множественными анимациями
 */
@Composable
fun AnimatedMenuItem(
    text: String,
    animationState: MenuItemAnimationState,
    onClick: () -> Unit,
    zenterFontFamily: FontFamily,
    iconResId: Int? = null
) {
    // Анимация нажатия
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (animationState.offset.value * 250).dp)
            .alpha(animationState.opacity.value)
            .scale(animationState.scale.value * pressScale)
            .graphicsLayer {
                rotationZ = animationState.rotation.value
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp), // как в настройках
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconResId != null) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp), // Увеличено с 22dp до 26dp
                    colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                )
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClick = onClick,
                        onClickLabel = text
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f)
                    )

                    // Декоративный индикатор (оранжевая точка)
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = Color(0xFFFF9500).copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.small
                            )
                    )
                }
            }
        }
    }
}

/**
 * Пункт меню с анимацией и информацией о подписке
 */
@Composable
fun AnimatedMenuItemWithSubscription(
    text: String,
    animationState: MenuItemAnimationState,
    onClick: () -> Unit,
    zenterFontFamily: FontFamily,
    iconResId: Int? = null,
    subscriptionInfo: com.kizvpn.client.data.SubscriptionInfo,
    currentPing: Int? = null,
    isPinging: Boolean = false
) {
    // Анимация нажатия
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = (animationState.offset.value * 250).dp)
            .alpha(animationState.opacity.value)
            .scale(animationState.scale.value * pressScale)
            .graphicsLayer {
                rotationZ = animationState.rotation.value
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconResId != null) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp), // Увеличено с 22dp до 26dp
                    colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                )
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClick = onClick,
                        onClickLabel = text
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 18.dp)
                ) {
                    // Первая строка: название и точка
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.2.sp
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                            
                            // Пинг бейдж (динамический)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isPinging -> Color(0xFFFF9500) // Оранжевый при пинге
                                        currentPing == null -> Color(0xFFFF3B30) // Красный если нет пинга
                                        currentPing!! < 100 -> Color(0xFF34C759) // Зеленый для хорошего пинга
                                        currentPing!! < 300 -> Color(0xFFFF9500) // Оранжевый для среднего пинга
                                        else -> Color(0xFFFF3B30) // Красный для плохого пинга
                                    }
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                            ) {
                                Text(
                                    text = when {
                                        isPinging -> "..."
                                        currentPing != null -> "${currentPing}ms"
                                        else -> "N/A"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.dp)
                                )
                            }
                        }

                        // Оранжевая точка
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = Color(0xFFFF9500).copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                    
                    // Вторая строка: дни/часы и трафик (компактно и с цветами)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Компактное время с цветом
                        val timeText = subscriptionInfo.formatTimeCompact()
                        val timeColor = when {
                            subscriptionInfo.expired -> Color(0xFFFF3B30) // Красный для истекших
                            subscriptionInfo.unlimited -> Color(0xFF34C759) // Зеленый для безлимита
                            (subscriptionInfo.days ?: 0) <= 3 -> Color(0xFFFF9500) // Оранжевый если меньше 3 дней
                            else -> Color(0xFF34C759) // Зеленый для нормального времени
                        }
                        
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = timeColor
                        )
                        
                        // Трафик (показываем всегда, если есть информация об использованном трафике)
                        val shouldShowTraffic = subscriptionInfo.usedTraffic != null
                        if (shouldShowTraffic) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp
                                ),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            
                            val usedText = subscriptionInfo.formatUsedTrafficCompact() ?: "0 B"
                            val totalText = subscriptionInfo.formatTotalTrafficCompact() ?: "∞"
                            
                            // Цвет трафика в зависимости от использования
                            val trafficProgress = if (subscriptionInfo.hasTrafficLimit() && subscriptionInfo.totalTraffic != null && subscriptionInfo.totalTraffic > 0) {
                                (subscriptionInfo.usedTraffic?.toFloat() ?: 0f) / subscriptionInfo.totalTraffic.toFloat()
                            } else {
                                0f
                            }
                            
                            val trafficColor = when {
                                !subscriptionInfo.hasTrafficLimit() -> Color(0xFF34C759) // Зеленый для безлимита
                                trafficProgress > 0.9f -> Color(0xFFFF3B30) // Красный если больше 90%
                                trafficProgress > 0.8f -> Color(0xFFFF9500) // Оранжевый если больше 80%
                                else -> Color(0xFF007AFF) // Синий для нормального использования
                            }
                            
                            Text(
                                text = "$usedText / $totalText",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = trafficColor
                            )
                        }
                    }
                    
                    // Полоса трафика (если есть ограничение по трафику)
                    if (subscriptionInfo.hasTrafficLimit()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val usedTraffic = subscriptionInfo.usedTraffic ?: 0L
                        val totalTraffic = subscriptionInfo.totalTraffic ?: 1L
                        val progress = if (totalTraffic > 0) {
                            (usedTraffic.toFloat() / totalTraffic.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        
                        // Полоса прогресса
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(
                                        color = if (progress > 0.8f) Color(0xFFFF3B30) else Color(0xFF34C759),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Модальное окно статистики в стиле "Liquid Glass"
 */
@Composable
fun StatisticsModal(
    showStatistics: Boolean,
    onDismiss: () -> Unit,
    onBackToMenu: () -> Unit, // Callback для возврата в главное меню
    connectionStatus: com.kizvpn.client.ui.models.ConnectionStatus,
    viewModel: com.kizvpn.client.ui.viewmodel.VpnViewModel? = null
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    
    // Получаем данные статистики
    val currentSpeed by viewModel?.currentSpeed?.collectAsState() ?: remember { mutableStateOf(0f) }
    val connectionDuration = remember { mutableStateOf<Long?>(null) }
    
    // Обновляем время подключения каждую секунду
    LaunchedEffect(connectionStatus.isConnected) {
        while (connectionStatus.isConnected) {
            connectionDuration.value = viewModel?.getConnectionDuration()
            delay(1000)
        }
        connectionDuration.value = null
    }
    
    // Форматируем время
    val formattedDuration = connectionDuration.value?.let { duration ->
        val hours = TimeUnit.MILLISECONDS.toHours(duration)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        when {
            hours > 0 -> "${hours}ч ${minutes}м ${seconds}с"
            minutes > 0 -> "${minutes}м ${seconds}с"
            else -> "${seconds}с"
        }
    } ?: "Не подключено"
    
    // Форматируем скорость
    val formattedSpeed = when {
        currentSpeed >= 1024 -> String.format("%.2f MB/s", currentSpeed / 1024f)
        currentSpeed > 0 -> String.format("%.0f KB/s", currentSpeed)
        else -> "0 KB/s"
    }
    
    // Форматируем трафик
    val downloadBytes = connectionStatus.downloadBytes
    val uploadBytes = connectionStatus.uploadBytes
    val formattedDownload = when {
        downloadBytes >= 1073741824L -> String.format("%.2f GB", downloadBytes / 1073741824.0)
        downloadBytes >= 1048576L -> String.format("%.2f MB", downloadBytes / 1048576.0)
        downloadBytes >= 1024L -> String.format("%.2f KB", downloadBytes / 1024.0)
        else -> "$downloadBytes B"
    }
    val formattedUpload = when {
        uploadBytes >= 1073741824L -> String.format("%.2f GB", uploadBytes / 1073741824.0)
        uploadBytes >= 1048576L -> String.format("%.2f MB", uploadBytes / 1048576.0)
        uploadBytes >= 1024L -> String.format("%.2f KB", uploadBytes / 1024.0)
        else -> "$uploadBytes B"
    }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showStatistics) 0.4f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "statistics_background_alpha"
    )
    
    // InteractionSource для фона
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    
    // Создаем состояния анимации для каждого элемента (5 элементов: заголовок, время подключения, скорость, трафик, выход)
    val animationStates = remember {
        List(5) { MenuItemAnimationState() }
    }
    
    // Задержки для анимации (как в основной менюшке)
    val animationDelays = listOf(0, 60, 120, 180, 240)
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Управление анимациями (как в основной менюшке)
    LaunchedEffect(showStatistics) {
        if (showStatistics) {
            isVisible = true
            // Анимация открытия (снизу вверх)
            animationStates.forEachIndexed { index, state ->
                coroutineScope.launch {
                    delay(animationDelays[index].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        } else {
            // Анимация закрытия (сверху вниз)
            animationStates.reversed().forEachIndexed { reversedIndex, state ->
                coroutineScope.launch {
                    delay(animationDelays[reversedIndex].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 0.8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 10f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
            delay((animationDelays.last() + 300).toLong())
            isVisible = false
        }
    }
    
    // Показываем overlay только когда нужно
    if (isVisible || showStatistics) {
        // Затемняющий фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            // Фон с фиксированным цветом
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            // Прозрачная область для нажатия без визуальных эффектов
            // При нажатии на фон возвращаемся в главное меню
            Box(
                modifier = Modifier
                    .fillMaxSize()
                            .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                                onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBackToMenu() // Возвращаемся в главное меню
                        }
                    )
            )
        }
        
        // Модальное окно статистики
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1002f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(end = 16.dp, top = 16.dp, bottom = 80.dp), // Увеличили padding снизу для видимости кнопки
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Заголовок "Статистика" (индекс 4 - самый верхний)
                AnimatedModalCard(animationState = animationStates[4]) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = localizedString(R.string.statistics_title),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF5B9BD5) // Мягкий синий цвет
                            )
                            
                            // Декоративный индикатор (фиолетовая точка)
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
                
                // Время подключения (индекс 3)
                AnimatedModalCard(animationState = animationStates[3]) {
                    StatisticsCard(
                        title = "Время подключения",
                        value = formattedDuration,
                        zenterFontFamily = zenterFontFamily
                    )
                }
                
                // Скорость (индекс 2)
                AnimatedModalCard(animationState = animationStates[2]) {
                    StatisticsCard(
                        title = "Скорость",
                        value = formattedSpeed,
                        zenterFontFamily = zenterFontFamily
                    )
                }
                
                // Трафик (индекс 1)
                AnimatedModalCard(animationState = animationStates[1]) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                    ) {
                        Text(
                                    text = localizedString(R.string.traffic_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF5B9BD5).copy(alpha = 0.8f) // Мягкий синий цвет
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = localizedString(R.string.incoming_traffic).format(formattedDownload),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 13.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = localizedString(R.string.outgoing_traffic).format(formattedUpload),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 13.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                            
                            // Декоративный индикатор (фиолетовая точка)
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
                
                // Кнопка "Назад" (индекс 0 - самый нижний, красная)
                AnimatedModalCard(animationState = animationStates[0]) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBackToMenu()
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF8B2323).copy(alpha = 0.9f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedString(R.string.menu_exit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Анимированная карточка статистики
 */
@Composable
fun AnimatedStatisticsCard(
    isVisible: Boolean,
    delay: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 500,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

/**
 * Карточка статистики
 */
@Composable
fun StatisticsCard(
    title: String,
    value: String,
    zenterFontFamily: FontFamily
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = zenterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF5B9BD5).copy(alpha = 0.8f) // Мягкий синий цвет для названий
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = zenterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF5B9BD5) // Мягкий синий цвет для значений
                )
            }
            
            // Декоративный индикатор (фиолетовая точка как в главном меню)
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * Модальное окно настроек в стиле "Liquid Glass"
 */
@Composable
fun SettingsModal(
    showSettings: Boolean,
    onDismiss: () -> Unit,
    onBackToMenu: () -> Unit,
    subscriptionInfo: com.kizvpn.client.data.SubscriptionInfo? = null,
    onLogout: () -> Unit = {},
    onAutoConnectChange: (Boolean) -> Unit = {},
    onNotificationsChange: (Boolean) -> Unit = {},
    initialAutoConnect: Boolean = false,
    initialNotifications: Boolean = true,
    onOpenRouting: () -> Unit = {},
    biometricAuthManager: com.kizvpn.client.security.BiometricAuthManager? = null // Добавляем BiometricAuthManager
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Получаем локализованные строки заранее
    val appSecurityTitle = localizedString(R.string.app_security)
    val biometricSubtitle = localizedString(R.string.biometric_subtitle_app)
    
    // Загружаем настройки из SharedPreferences
    var useAutoConnect by remember { 
        mutableStateOf(
            context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("auto_connect", initialAutoConnect)
        )
    }
    var useNotifications by remember { 
        mutableStateOf(
            context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("notifications_enabled", initialNotifications)
        )
    }
    // Состояние безопасности
    var securityEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("security_enabled", false)
        )
    }
    // Состояние маршрутизации: включена, если выбран хотя бы один app в RoutingModal
    var routingEnabled by remember { mutableStateOf(false) }
    // Текущий язык интерфейса
    var currentLanguage by remember {
        mutableStateOf(
            context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                .getString("language", "ru") ?: "ru"
        )
    }
    
    // Сохраняем настройки при изменении
    LaunchedEffect(useAutoConnect) {
        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_connect", useAutoConnect)
            .apply()
        onAutoConnectChange(useAutoConnect)
    }
    
    LaunchedEffect(useNotifications) {
        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications_enabled", useNotifications)
            .apply()
        onNotificationsChange(useNotifications)
    }
    LaunchedEffect(securityEnabled) {
        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("security_enabled", securityEnabled)
            .apply()
    }
    // Обновляем состояние маршрутизации при каждом открытии окна настроек
    LaunchedEffect(showSettings) {
        if (showSettings) {
            routingEnabled = try {
                val prefs = context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                val json = prefs.getString("routing_selected_apps", "[]")
                val arr = org.json.JSONArray(json)
                arr.length() > 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    // Создаем состояния анимации для каждого элемента (8 элементов: заголовок, подписка, язык, автоподключение, уведомления, маршрутизация, безопасность, назад)
    val animationStates = remember {
        List(8) { MenuItemAnimationState() }
    }
    
    // Задержки для анимации (как в основной менюшке)
    val animationDelays = listOf(0, 60, 120, 180, 240, 300, 360, 420)
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showSettings) 0.4f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "settings_background_alpha"
    )
    
    // Управление анимациями (как в основной менюшке)
    LaunchedEffect(showSettings) {
        if (showSettings) {
            isVisible = true
            // Анимация открытия (снизу вверх)
            animationStates.forEachIndexed { index, state ->
                coroutineScope.launch {
                    delay(animationDelays[index].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        } else {
            // Анимация закрытия (сверху вниз)
            animationStates.reversed().forEachIndexed { reversedIndex, state ->
                coroutineScope.launch {
                    delay(animationDelays[reversedIndex].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 0.8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 10f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
            delay((animationDelays.last() + 300).toLong())
            isVisible = false
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showSettings) {
        // Затемняющий фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                            .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                                onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBackToMenu()
                        }
                    )
            )
        }
        
        // Модальное окно настроек
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1002f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(end = 16.dp, top = 16.dp, bottom = 80.dp) // Увеличили padding снизу для видимости кнопки
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Заголовок (индекс 7 - самый верхний)
                AnimatedModalCard(animationState = animationStates[7]) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = context.getString(R.string.settings_title),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF5B9BD5) // Мягкий синий цвет
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
                
                // Язык (индекс 5) — капсула на всю ширину, иконка вынесена наружу
                AnimatedModalCard(animationState = animationStates[5]) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Капсула
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.75f)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = localizedString(R.string.language),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF5B9BD5).copy(alpha = 0.8f)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ru_eng),
                                        contentDescription = "Language toggle",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .padding(horizontal = 4.dp)
                                            .zIndex(0f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .zIndex(1f)
                                    ) {
                                        if (currentLanguage != "en") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.5f)
                                                    .fillMaxHeight()
                                                    .background(
                                                        Color(0xFFFFA500).copy(alpha = 0.4f),
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                                            topStart = 8.dp,
                                                            bottomStart = 8.dp
                                                        )
                                                    )
                                            )
                                        }
                                        if (currentLanguage == "en") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.5f)
                                                    .fillMaxHeight()
                                                    .align(Alignment.CenterEnd)
                                                    .background(
                                                        Color(0xFFFFA500).copy(alpha = 0.4f),
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                                            topEnd = 8.dp,
                                                            bottomEnd = 8.dp
                                                        )
                                                    )
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .zIndex(2f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    if (currentLanguage != "ru") {
                                                        currentLanguage = "ru"
                                                        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putString("language", "ru")
                                                            .apply()
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    if (currentLanguage != "en") {
                                                        currentLanguage = "en"
                                                        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putString("language", "en")
                                                            .apply()
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        // Иконка вынесена за левый край капсулы
                        Image(
                            painter = painterResource(id = R.drawable.ic_settings_language),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .size(26.dp),
                            colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                        )
                        // Фиолетовая точка справа
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                // Автоподключение (индекс 4)
                AnimatedModalCard(animationState = animationStates[4]) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsSwitchCard(
                            title = localizedString(R.string.auto_connect_title),
                            description = "",
                            checked = useAutoConnect,
                            onCheckedChange = { newValue ->
                                useAutoConnect = newValue
                                // Сохраняем в SharedPreferences
                                context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("auto_connect", newValue)
                                    .apply()
                            },
                            zenterFontFamily = zenterFontFamily,
                            iconResId = R.drawable.off_on
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_settings_auto_connect),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .size(26.dp),
                            colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                        )
                    }
                }
                
                // Уведомления (индекс 3)
                AnimatedModalCard(animationState = animationStates[3]) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsSwitchCard(
                            title = localizedString(R.string.notifications),
                            description = "",
                            checked = useNotifications,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    // Если включаем уведомления, запрашиваем системное разрешение
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                            // Запрашиваем разрешение через MainActivity
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("BottomMenuButton", "Failed to open notification settings", e)
                                            }
                                        }
                                    }
                                }
                                
                                useNotifications = newValue
                                // Сохраняем в SharedPreferences
                                context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("notifications_enabled", newValue)
                                    .apply()
                            },
                            zenterFontFamily = zenterFontFamily,
                            iconResId = R.drawable.off_on
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_settings_notifications),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .size(26.dp),
                            colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                        )
                    }
                }
                
                // Маршрутизация (индекс 2)
                AnimatedModalCard(animationState = animationStates[2]) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenRouting()
                            }
                    ) {
                        SettingsSwitchCard(
                            title = localizedString(R.string.routing),
                            description = "",
                            checked = routingEnabled,
                            onCheckedChange = { /* read-only индикатор */ },
                            zenterFontFamily = zenterFontFamily,
                            iconResId = R.drawable.off_on,
                            interactive = false
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_settings_routing),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .size(26.dp),
                            colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                        )
                    }
                }

                // Безопасность (индекс 1)
                AnimatedModalCard(animationState = animationStates[1]) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsSwitchCard(
                            title = localizedString(R.string.security),
                            description = "",
                            checked = securityEnabled,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    // Если включаем безопасность, запрашиваем биометрическую аутентификацию
                                    if (biometricAuthManager != null && biometricAuthManager.isBiometricAvailable()) {
                                        android.util.Log.d("BottomMenuButton", "Requesting biometric authentication for security")
                                        
                                        // Получаем Activity из context
                                        val activity = context as? androidx.fragment.app.FragmentActivity
                                        if (activity != null) {
                                            biometricAuthManager.authenticate(
                                                activity = activity,
                                                title = appSecurityTitle,
                                                subtitle = biometricSubtitle,
                                                onSuccess = {
                                                    android.util.Log.d("BottomMenuButton", "Biometric authentication successful - enabling security")
                                                    securityEnabled = true
                                                    // Сохраняем в SharedPreferences
                                                    context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                                        .edit()
                                                        .putBoolean("security_enabled", true)
                                                        .apply()
                                                },
                                                onError = { error ->
                                                    android.util.Log.e("BottomMenuButton", "Biometric authentication failed: $error")
                                                    // Не включаем безопасность при ошибке
                                                    securityEnabled = false
                                                },
                                                onCancel = {
                                                    android.util.Log.d("BottomMenuButton", "Biometric authentication cancelled")
                                                    // Не включаем безопасность при отмене
                                                    securityEnabled = false
                                                }
                                            )
                                        } else {
                                            android.util.Log.e("BottomMenuButton", "Activity not found for biometric authentication")
                                            securityEnabled = newValue
                                            context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                                .edit()
                                                .putBoolean("security_enabled", newValue)
                                                .apply()
                                        }
                                    } else {
                                        android.util.Log.w("BottomMenuButton", "Biometric authentication not available")
                                        // Если биометрия недоступна, просто включаем настройку
                                        securityEnabled = newValue
                                        context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("security_enabled", newValue)
                                            .apply()
                                    }
                                } else {
                                    // Если выключаем безопасность, просто сохраняем
                                    securityEnabled = newValue
                                    context.getSharedPreferences("vpn_settings", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("security_enabled", newValue)
                                        .apply()
                                }
                            },
                            zenterFontFamily = zenterFontFamily,
                            iconResId = R.drawable.off_on
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_settings_security),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-26).dp)
                                .size(26.dp),
                            colorFilter = ColorFilter.tint(Color.White) // Изменено с оранжевого на белый
                        )
                    }
                }
                
                // Назад (красная кнопка)
                AnimatedModalCard(animationState = animationStates[0]) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLogout()
                                    onBackToMenu()
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF8B2323).copy(alpha = 0.9f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedString(R.string.menu_exit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = Color.White.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    zenterFontFamily: FontFamily,
    iconResId: Int? = null,
    interactive: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = zenterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF5B9BD5).copy(alpha = 0.8f)
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // OFF/ON индикатор
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconResId != null) {
                        Image(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp) // Увеличено с 32dp до 36dp
                                .padding(horizontal = 4.dp)
                                .zIndex(0f),
                            colorFilter = ColorFilter.tint(Color.White) // Добавлен белый цвет
                        )
                    }

                    // Оранжевая подсветка выбранной части
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .zIndex(1f)
                    ) {
                        if (!checked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .fillMaxHeight()
                                    .background(
                                        Color(0xFFFFA500).copy(alpha = 0.4f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                            topStart = 8.dp,
                                            bottomStart = 8.dp
                                        )
                                    )
                            )
                        }
                        if (checked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .fillMaxHeight()
                                    .align(Alignment.CenterEnd)
                                    .background(
                                        Color(0xFFFFA500).copy(alpha = 0.4f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                            topEnd = 8.dp,
                                            bottomEnd = 8.dp
                                        )
                                    )
                            )
                        }
                    }

                    if (interactive) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (checked) {
                                            onCheckedChange(false)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (!checked) {
                                            onCheckedChange(true)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Фиолетовая точка справа (как в старом дизайне)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * Модальное окно истории подключений в стиле "Liquid Glass"
 */
@Composable
fun HistoryModal(
    showHistory: Boolean,
    onDismiss: () -> Unit,
    onBackToMenu: () -> Unit,
    context: android.content.Context? = null
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    val ctx = context ?: LocalContext.current
    
    val historyManager = remember { com.kizvpn.client.data.ConnectionHistoryManager(ctx) }
    var history by remember { mutableStateOf<List<com.kizvpn.client.data.ConnectionHistoryEntry>>(emptyList()) }
    
    // Загружаем историю при открытии
    LaunchedEffect(showHistory) {
        if (showHistory) {
            history = historyManager.getHistory()
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Фиксированное количество состояний анимации (заголовок + выход + несколько для истории)
    // Для истории используем одно состояние, так как LazyColumn сам управляет отображением
    val animationStates = remember {
        List(3) { MenuItemAnimationState() } // Заголовок, список истории, выход
    }
    
    // Задержки для анимации (как в основной менюшке)
    val animationDelays = listOf(0, 60, 120)
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showHistory) 0.4f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "history_background_alpha"
    )
    
    // Управление анимациями (как в основной менюшке)
    LaunchedEffect(showHistory) {
        if (showHistory) {
            isVisible = true
            // Анимация открытия (снизу вверх)
            animationStates.forEachIndexed { index, state ->
                coroutineScope.launch {
                    delay(animationDelays[index].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        } else {
            // Анимация закрытия (сверху вниз)
            animationStates.reversed().forEachIndexed { reversedIndex, state ->
                coroutineScope.launch {
                    delay(animationDelays[reversedIndex].toLong())
                    launch {
                        state.offset.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.opacity.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        state.scale.animateTo(
                            targetValue = 0.8f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        state.rotation.animateTo(
                            targetValue = 10f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
            delay((animationDelays.last() + 300).toLong())
            isVisible = false
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showHistory) {
        // Затемняющий фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                            .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                                onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBackToMenu()
                        }
                    )
            )
        }
        
        // Модальное окно истории
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1002f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(end = 16.dp, top = 16.dp, bottom = 80.dp), // Увеличили padding снизу для видимости кнопки
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Заголовок (индекс 2 - самый верхний)
                AnimatedModalCard(animationState = animationStates[2]) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.75f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = localizedString(R.string.connection_history),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF5B9BD5) // Мягкий синий цвет
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
                
                // Прокручиваемый список истории
                if (history.isEmpty()) {
                    // Пустое сообщение (индекс 1)
                    AnimatedModalCard(animationState = animationStates[1]) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.75f)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localizedString(R.string.history_empty),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 13.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            shape = MaterialTheme.shapes.small
                                        )
                                )
                            }
                        }
                    }
                } else {
                    // Используем LazyColumn для прокрутки большого количества элементов
                    // Используем одно состояние анимации для всего списка (индекс 1)
                    AnimatedModalCard(animationState = animationStates[1]) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            items(history.size) { index ->
                                val entry = history[index]
                                HistoryItemCard(entry = entry, zenterFontFamily = zenterFontFamily)
                            }
                        }
                    }
                }
                
                // Кнопка "Назад" (индекс 0 - самый нижний, красная)
                AnimatedModalCard(animationState = animationStates[0]) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onBackToMenu()
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF8B2323).copy(alpha = 0.9f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedString(R.string.menu_exit),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = Color.White.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    entry: com.kizvpn.client.data.ConnectionHistoryEntry,
    zenterFontFamily: FontFamily
) {
    val context = LocalContext.current
    val dateFormat = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()) }
    val date = dateFormat.format(java.util.Date(entry.timestamp))
    
    val actionText = when (entry.action) {
        "connected" -> "Подключено"
        "disconnected" -> "Отключено"
        else -> entry.action
    }
    
    val actionColor = when (entry.action) {
        "connected" -> Color(0xFF00FF88)
        "disconnected" -> Color(0xFFD32F2F)
        else -> Color.White.copy(alpha = 0.7f)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = actionColor
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                if (entry.server != null) {
                    Text(
                        text = context.getString(R.string.server_label, entry.server),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                if (entry.duration != null) {
                    val durationSeconds = entry.duration / 1000
                    val hours = durationSeconds / 3600
                    val minutes = (durationSeconds % 3600) / 60
                    val seconds = durationSeconds % 60
                    
                    val durationText = when {
                        hours > 0 -> "${hours}ч ${minutes}м ${seconds}с"
                        minutes > 0 -> "${minutes}м ${seconds}с"
                        else -> "${seconds}с"
                    }
                    
                    Text(
                        text = context.getString(R.string.duration_label, durationText),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = zenterFontFamily,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Декоративный индикатор (фиолетовая точка)
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * Модальное окно для настройки VPN (объединяет Vless и WireGuard)
 */
// Data class для конфига с пингом (для VpnConfigModal)
data class VpnConfigItem(
    val id: String,
    val config: String,
    val name: String,
    val host: String,
    val port: Int = 443,
    val protocol: String, // "VLESS" или "WireGuard"
    val subscriptionId: String?, // null если это отдельный конфиг
    val isActive: Boolean,
    var ping: Int? = null
)

// Data class для блока подписки (для VpnConfigModal)
data class VpnSubscriptionBlock(
    val id: String,
    val url: String,
    val name: String,
    val usedTraffic: String?,
    val totalTraffic: String?,
    val usedTrafficBytes: Long? = null,  // Для шкалы трафика
    val totalTrafficBytes: Long? = null, // Для шкалы трафика
    val daysRemaining: String?,
    val configs: List<VpnConfigItem>,
    var isExpanded: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnConfigModal(
    showVpnConfig: Boolean,
    onDismiss: () -> Unit,
    onActivateKey: (String, (Int?) -> Unit) -> Unit,
    onSaveWireGuardConfig: (String) -> Unit = {},
    onSelectConfig: (String, com.kizvpn.client.config.ConfigParser.Protocol) -> Unit = { _, _ -> },
    onDisconnectClick: () -> Unit = {},
    onShowConfigNotification: (String) -> Unit = {},
    onUpdateSubscriptionInfo: (com.kizvpn.client.data.SubscriptionInfo) -> Unit = {} // Callback для обновления информации о подписке в ViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configParser = remember { com.kizvpn.client.config.ConfigParser() }
    val vpnApiClient = remember { com.kizvpn.client.api.VpnApiClient() }
    
    // SharedPreferences
    val prefs = remember { context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE) }
    
    // Состояния
    var subscriptionBlocks by remember { mutableStateOf<List<VpnSubscriptionBlock>>(emptyList()) }
    var standaloneConfigs by remember { mutableStateOf<List<VpnConfigItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshingBlockId by remember { mutableStateOf<String?>(null) } // Какой блок сейчас обновляется
    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) } // Показать QR сканер
    var showQrCodeDialog by remember { mutableStateOf<String?>(null) } // Показать QR-код для шаринга
    var addDialogInput by remember { mutableStateOf("") }
    var addDialogError by remember { mutableStateOf<String?>(null) }
    var pingResults by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var pingChecked by remember { mutableStateOf<Set<String>>(emptySet()) } // Отслеживаем проверенные конфиги
    
    // Функция для извлечения хоста из конфига
    fun extractHost(configString: String): String {
        return try {
            val parsed = configParser.parseConfig(configString)
            if (parsed?.address != null) {
                parsed.address
            } else {
                // Fallback: пробуем извлечь хост вручную для VLESS
                if (configString.startsWith("vless://")) {
                    val withoutProtocol = configString.removePrefix("vless://")
                    val atIndex = withoutProtocol.indexOf('@')
                    if (atIndex != -1) {
                        val hostPart = withoutProtocol.substring(atIndex + 1)
                        val colonIndex = hostPart.indexOf(':')
                        if (colonIndex != -1) {
                            return hostPart.substring(0, colonIndex)
                        }
                    }
                }
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    // Функция для извлечения порта из конфига
    fun extractPort(configString: String): Int {
        return try {
            val parsed = configParser.parseConfig(configString)
            parsed?.port ?: 443
        } catch (e: Exception) {
            443
        }
    }
    
    // Функция для извлечения имени из конфига
    fun extractName(configString: String): String {
        return try {
            // Сначала пробуем через парсер
            val parsed = configParser.parseConfig(configString)
            if (parsed?.name != null && parsed.name.isNotBlank()) {
                return parsed.name
            }
            
            // Fallback: для VLESS конфигов имя после #
            if (configString.startsWith("vless://")) {
                val hashIndex = configString.lastIndexOf('#')
                if (hashIndex != -1) {
                    val name = java.net.URLDecoder.decode(configString.substring(hashIndex + 1), "UTF-8")
                    if (name.isNotBlank()) {
                        return name
                    }
                }
            }
            // Для WireGuard ищем комментарий
            if (configString.contains("[Interface]")) {
                val lines = configString.split("\n")
                for (line in lines) {
                    if (line.trim().startsWith("#")) {
                        val name = line.trim().removePrefix("#").trim()
                        if (name.isNotBlank()) {
                            return name
                        }
                    }
                }
            }
            "Config"
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "Error extracting name: ${e.message}")
            "Config"
        }
    }
    
    // Функция для определения протокола
    fun getProtocol(configString: String): String {
        return when {
            configString.trim().startsWith("vless://") -> "VLESS"
            configString.contains("[Interface]") && configString.contains("PrivateKey") -> "WireGuard"
            else -> "Unknown"
        }
    }
    
    // Функция для загрузки всех данных
    // runPings = true - запускать проверку пингов (при первом открытии)
    // runPings = false - не запускать пинги (при обновлении одного блока)
    fun loadAllData(runPings: Boolean = true) {
        coroutineScope.launch {
            isLoading = true
            
            val activeConfig = prefs.getString("saved_config", null)
            val activeConfigType = prefs.getString("active_config_type", null)
            
            // Загружаем subscription блоки
            val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
            val subscriptionsArray = try { org.json.JSONArray(subscriptionsJson) } catch (e: Exception) { org.json.JSONArray() }
            
            val loadedBlocks = mutableListOf<VpnSubscriptionBlock>()
            
            for (i in 0 until subscriptionsArray.length()) {
                try {
                    val subObj = subscriptionsArray.getJSONObject(i)
                    val subId = subObj.getString("id")
                    val subUrl = subObj.getString("url")
                    val subName = subObj.optString("name", context.getString(R.string.subscription_single))
                    val usedTraffic = subObj.optString("usedTraffic", null)
                    val totalTraffic = subObj.optString("totalTraffic", null)
                    val usedTrafficBytes = if (subObj.has("usedTrafficBytes")) subObj.optLong("usedTrafficBytes", 0) else null
                    val totalTrafficBytes = if (subObj.has("totalTrafficBytes")) subObj.optLong("totalTrafficBytes", 0) else null
                    val daysRemaining = subObj.optString("daysRemaining", null)
                    val configsArray = subObj.optJSONArray("configs") ?: org.json.JSONArray()
                    
                    val configItems = mutableListOf<VpnConfigItem>()
                    for (j in 0 until configsArray.length()) {
                        val configStr = configsArray.getString(j)
                        val configId = "${subId}_$j"
                        val isActive = configStr == activeConfig
                        
                        configItems.add(VpnConfigItem(
                            id = configId,
                            config = configStr,
                            name = extractName(configStr),
                            host = extractHost(configStr),
                            port = extractPort(configStr),
                            protocol = getProtocol(configStr),
                            subscriptionId = subId,
                            isActive = isActive
                        ))
                    }
                    
                    loadedBlocks.add(VpnSubscriptionBlock(
                        id = subId,
                        url = subUrl,
                        name = subName,
                        usedTraffic = usedTraffic,
                        totalTraffic = totalTraffic,
                        usedTrafficBytes = usedTrafficBytes,
                        totalTrafficBytes = totalTrafficBytes,
                        daysRemaining = daysRemaining,
                        configs = configItems,
                        isExpanded = true
                    ))
                } catch (e: Exception) {
                    Log.e("VpnConfigModal", "Error loading subscription block", e)
                }
            }
            
            subscriptionBlocks = loadedBlocks
            
            // Загружаем отдельные конфиги (не из subscriptions)
            val standaloneJson = prefs.getString("standalone_configs", "[]")
            val standaloneArray = try { org.json.JSONArray(standaloneJson) } catch (e: Exception) { org.json.JSONArray() }
            
            val loadedStandalone = mutableListOf<VpnConfigItem>()
            for (i in 0 until standaloneArray.length()) {
                try {
                    val configStr = standaloneArray.getString(i)
                    val isActive = configStr == activeConfig
                    
                    loadedStandalone.add(VpnConfigItem(
                        id = "standalone_$i",
                        config = configStr,
                        name = extractName(configStr),
                        host = extractHost(configStr),
                        port = extractPort(configStr),
                        protocol = getProtocol(configStr),
                        subscriptionId = null,
                        isActive = isActive
                    ))
                } catch (e: Exception) {
                    Log.e("VpnConfigModal", "Error loading standalone config", e)
                }
            }
            
            standaloneConfigs = loadedStandalone
            isLoading = false
            
            // Запускаем пинг для всех конфигов (только при первом открытии или если runPings = true)
            if (runPings) {
                val allConfigs = loadedBlocks.flatMap { it.configs } + loadedStandalone
                Log.d("VpnConfigModal", "Запуск проверки пингов для ${allConfigs.size} конфигов")
                allConfigs.forEach { config ->
                    if (config.host != "unknown" && config.host.isNotBlank()) {
                        launch {
                            try {
                                Log.d("VpnConfigModal", "Пинг ${config.host}:${config.port}")
                                val pingTime = com.kizvpn.client.util.PingUtil.pingServer(config.host, config.port)
                                Log.d("VpnConfigModal", "Пинг ${config.host} = ${pingTime}ms")
                                pingResults = pingResults + (config.id to pingTime)
                                pingChecked = pingChecked + config.id
                            } catch (e: Exception) {
                                Log.e("VpnConfigModal", "Ошибка пинга ${config.host}: ${e.message}")
                                pingResults = pingResults + (config.id to -1) // -1 означает ошибку
                                pingChecked = pingChecked + config.id
                            }
                        }
                    } else {
                        // Для неизвестных хостов сразу отмечаем как проверенные с ошибкой
                        pingResults = pingResults + (config.id to -1)
                        pingChecked = pingChecked + config.id
                    }
                }
            }
        }
    }
    
    // Функция для добавления subscription URL или отдельного ключа
    fun addConfig(input: String) {
        coroutineScope.launch {
            addDialogError = null
            val trimmedInput = input.trim()
            
            // Проверяем, это URL подписки или отдельный конфиг
            if (trimmedInput.startsWith("http://") || trimmedInput.startsWith("https://")) {
                // Это Subscription URL
                isLoading = true
                try {
                    // Получаем информацию о подписке
                    val subscriptionInfo = vpnApiClient.getSubscriptionInfoFromUrl(trimmedInput)
                    val configs = vpnApiClient.getSubscriptionConfigs(trimmedInput) ?: emptyList()
                    
                    // Добавляем подписку даже если конфигов нет (например, истекшая подписка)
                    // Создаем новый блок подписки
                    val subId = System.currentTimeMillis().toString()
                    val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
                    val subscriptionsArray = try { org.json.JSONArray(subscriptionsJson) } catch (e: Exception) { org.json.JSONArray() }
                    
                    val newSubObj = org.json.JSONObject()
                    newSubObj.put("id", subId)
                    newSubObj.put("url", trimmedInput)
                    newSubObj.put("name", "Subscription (${configs.size})")
                        
                    if (subscriptionInfo != null) {
                        subscriptionInfo.formatUsedTraffic()?.let { newSubObj.put("usedTraffic", it) }
                        subscriptionInfo.formatTotalTraffic()?.let { newSubObj.put("totalTraffic", it) }
                        // Сохраняем числовые значения для шкалы трафика
                        subscriptionInfo.usedTraffic?.let { newSubObj.put("usedTrafficBytes", it) }
                        subscriptionInfo.totalTraffic?.let { newSubObj.put("totalTrafficBytes", it) }
                        val daysText = subscriptionInfo.format()
                        if (daysText != context.getString(R.string.not_activated)) {
                            newSubObj.put("daysRemaining", daysText)
                        }
                    }
                        
                    val configsArray = org.json.JSONArray()
                    configs.forEach { configsArray.put(it) }
                    newSubObj.put("configs", configsArray)
                    
                    subscriptionsArray.put(newSubObj)
                    prefs.edit().putString("subscription_blocks", subscriptionsArray.toString()).apply()
                    
                    showAddDialog = false
                    addDialogInput = ""
                    loadAllData(runPings = configs.isNotEmpty()) // Запускаем пинги только если есть конфиги
                    
                    if (configs.isNotEmpty()) {
                        onShowConfigNotification("Подписка добавлена: ${configs.size} конфигов")
                    } else {
                        // Подписка добавлена, но конфигов нет (возможно истекла)
                        val statusText = if (subscriptionInfo?.expired == true) context.getString(R.string.expired_status) else context.getString(R.string.no_configs)
                        onShowConfigNotification(context.getString(R.string.subscription_added_status, statusText))
                    }
                } catch (e: Exception) {
                    Log.e("VpnConfigModal", "Error adding subscription", e)
                    addDialogError = "Ошибка: ${e.message}"
                }
                isLoading = false
            } else if (trimmedInput.startsWith("vless://") || trimmedInput.contains("[Interface]")) {
                // Это отдельный конфиг (VLESS или WireGuard)
                val standaloneJson = prefs.getString("standalone_configs", "[]")
                val standaloneArray = try { org.json.JSONArray(standaloneJson) } catch (e: Exception) { org.json.JSONArray() }
                
                // Проверяем, нет ли уже такого конфига
                var exists = false
                for (i in 0 until standaloneArray.length()) {
                    if (standaloneArray.getString(i) == trimmedInput) {
                        exists = true
                        break
                    }
                }
                
                if (!exists) {
                    standaloneArray.put(trimmedInput)
                    prefs.edit().putString("standalone_configs", standaloneArray.toString()).apply()
                    
                    showAddDialog = false
                    addDialogInput = ""
                    loadAllData(runPings = true) // Запускаем пинг для нового конфига
                    
                    val protocol = getProtocol(trimmedInput)
                    onShowConfigNotification("$protocol конфиг добавлен")
                } else {
                    addDialogError = "Этот конфиг уже добавлен"
                }
            } else {
                addDialogError = "Неверный формат. Введите Subscription URL, VLESS ключ или WireGuard конфиг"
            }
        }
    }
    
    // Функция для удаления конфига
    fun deleteConfig(configItem: VpnConfigItem) {
        // Если удаляем активный конфиг - очищаем его
        val currentActiveConfig = prefs.getString("saved_config", null)
        if (configItem.config == currentActiveConfig) {
            prefs.edit()
                .remove("saved_config")
                .remove("saved_config_name")
                .remove("active_config_type")
                .apply()
            Log.d("VpnConfigModal", "Удалён активный конфиг, очищаем настройки")
        }
        
        if (configItem.subscriptionId != null) {
            // Удаляем из subscription блока
            val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
            val subscriptionsArray = try { org.json.JSONArray(subscriptionsJson) } catch (e: Exception) { org.json.JSONArray() }
            
            for (i in 0 until subscriptionsArray.length()) {
                val subObj = subscriptionsArray.getJSONObject(i)
                if (subObj.getString("id") == configItem.subscriptionId) {
                    val configsArray = subObj.optJSONArray("configs") ?: org.json.JSONArray()
                    val newConfigsArray = org.json.JSONArray()
                    for (j in 0 until configsArray.length()) {
                        if (configsArray.getString(j) != configItem.config) {
                            newConfigsArray.put(configsArray.getString(j))
                        }
                    }
                    subObj.put("configs", newConfigsArray)
                    subObj.put("name", "Subscription (${newConfigsArray.length()})")
                    break
                }
            }
            
            prefs.edit().putString("subscription_blocks", subscriptionsArray.toString()).apply()
        } else {
            // Удаляем из standalone
            val standaloneJson = prefs.getString("standalone_configs", "[]")
            val standaloneArray = try { org.json.JSONArray(standaloneJson) } catch (e: Exception) { org.json.JSONArray() }
            
            val newArray = org.json.JSONArray()
            for (i in 0 until standaloneArray.length()) {
                if (standaloneArray.getString(i) != configItem.config) {
                    newArray.put(standaloneArray.getString(i))
                }
            }
            
            prefs.edit().putString("standalone_configs", newArray.toString()).apply()
        }
        
        loadAllData(runPings = false) // Не перезапускаем пинги при удалении
    }
    
    // Функция для удаления всего subscription блока
    fun deleteSubscriptionBlock(blockId: String) {
        val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
        val subscriptionsArray = try { org.json.JSONArray(subscriptionsJson) } catch (e: Exception) { org.json.JSONArray() }
        
        // Проверяем, есть ли активный конфиг в этом блоке
        val currentActiveConfig = prefs.getString("saved_config", null)
        var shouldClearActive = false
        
        val newArray = org.json.JSONArray()
        for (i in 0 until subscriptionsArray.length()) {
            val subObj = subscriptionsArray.getJSONObject(i)
            if (subObj.getString("id") != blockId) {
                newArray.put(subObj)
            } else {
                // Проверяем, содержит ли удаляемый блок активный конфиг
                val configsArray = subObj.optJSONArray("configs") ?: org.json.JSONArray()
                for (j in 0 until configsArray.length()) {
                    if (configsArray.getString(j) == currentActiveConfig) {
                        shouldClearActive = true
                        break
                    }
                }
            }
        }
        
        // Если активный конфиг был в удалённом блоке - очищаем
        if (shouldClearActive) {
            prefs.edit()
                .remove("saved_config")
                .remove("saved_config_name")
                .remove("active_config_type")
                .putString("subscription_blocks", newArray.toString())
                .apply()
            Log.d("VpnConfigModal", "Удалён блок с активным конфигом, очищаем настройки")
        } else {
            prefs.edit().putString("subscription_blocks", newArray.toString()).apply()
        }
        
        loadAllData(runPings = false) // Не перезапускаем пинги при удалении
    }
    
    // Функция для обновления subscription
    fun refreshSubscription(blockId: String, url: String) {
        coroutineScope.launch {
            refreshingBlockId = blockId // Отмечаем какой блок обновляется
            try {
                val subscriptionInfo = vpnApiClient.getSubscriptionInfoFromUrl(url)
                val configs = vpnApiClient.getSubscriptionConfigs(url)
                
                if (configs != null) {
                    val subscriptionsJson = prefs.getString("subscription_blocks", "[]")
                    val subscriptionsArray = try { org.json.JSONArray(subscriptionsJson) } catch (e: Exception) { org.json.JSONArray() }
                    
                    for (i in 0 until subscriptionsArray.length()) {
                        val subObj = subscriptionsArray.getJSONObject(i)
                        if (subObj.getString("id") == blockId) {
                            subObj.put("name", "Subscription (${configs.size})")
                            
                            if (subscriptionInfo != null) {
                                subscriptionInfo.formatUsedTraffic()?.let { subObj.put("usedTraffic", it) }
                                subscriptionInfo.formatTotalTraffic()?.let { subObj.put("totalTraffic", it) }
                                // Сохраняем также числовые значения для шкалы
                                subscriptionInfo.usedTraffic?.let { subObj.put("usedTrafficBytes", it) }
                                subscriptionInfo.totalTraffic?.let { subObj.put("totalTrafficBytes", it) }
                                val daysText = subscriptionInfo.format()
                                if (daysText != context.getString(R.string.not_activated)) {
                                    subObj.put("daysRemaining", daysText)
                                }
                            }
                            
                            val configsArray = org.json.JSONArray()
                            configs.forEach { configsArray.put(it) }
                            subObj.put("configs", configsArray)
                            break
                        }
                    }
                    
                    prefs.edit().putString("subscription_blocks", subscriptionsArray.toString()).apply()
                    
                    // Загружаем данные без пингов
                    loadAllData(runPings = false)
                    
                    // Обновляем пинги только для конфигов в этом блоке
                    val updatedBlock = subscriptionBlocks.find { it.id == blockId }
                    updatedBlock?.configs?.forEach { config ->
                        if (config.host != "unknown" && config.host.isNotBlank()) {
                            launch {
                                try {
                                    val pingTime = com.kizvpn.client.util.PingUtil.pingServer(config.host, config.port)
                                    pingResults = pingResults + (config.id to pingTime)
                                    pingChecked = pingChecked + config.id
                                } catch (e: Exception) {
                                    pingResults = pingResults + (config.id to -1)
                                    pingChecked = pingChecked + config.id
                                }
                            }
                        }
                    }
                    
                    onShowConfigNotification("Подписка обновлена")
                    
                    // Обновляем информацию о подписке в основном ViewModel
                    if (subscriptionInfo != null) {
                        onUpdateSubscriptionInfo(subscriptionInfo)
                    }
                }
            } catch (e: Exception) {
                Log.e("VpnConfigModal", "Error refreshing subscription", e)
                onShowConfigNotification("Ошибка обновления")
            }
            refreshingBlockId = null
        }
    }
    
    // Функция для выбора конфига
    fun selectConfig(configItem: VpnConfigItem) {
        prefs.edit()
            .putString("saved_config", configItem.config)
            .putString("saved_config_name", configItem.name)
            .putString("active_config_type", if (configItem.protocol == "VLESS") "vless" else "wireguard")
            .apply()

        onSelectConfig(configItem.config,
            if (configItem.protocol == "VLESS")
                com.kizvpn.client.config.ConfigParser.Protocol.VLESS
            else
                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD
        )

        loadAllData(runPings = false) // Не перезапускаем пинги при выборе конфига
        onShowConfigNotification("Выбран: ${configItem.name}")
        
        // Если конфиг принадлежит подписке, обновляем информацию о подписке
        if (configItem.subscriptionId != null) {
            val block = subscriptionBlocks.find { it.id == configItem.subscriptionId }
            if (block != null) {
                refreshSubscription(block.id, block.url)
            }
        }
    }
    
    // Функция для шаринга конфига
    fun shareConfig(configItem: VpnConfigItem) {
        try {
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, configItem.config)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться конфигом")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "Error sharing config", e)
        }
    }
    
    // Функция для шаринга конфига через QR-код
    fun shareConfigQr(configItem: VpnConfigItem) {
        try {
            // Генерируем QR-код и показываем диалог
            showQrCodeDialog = configItem.config
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "Error sharing config QR", e)
        }
    }
    
    // Функция для шаринга URL подписки
    fun shareSubscriptionUrl(url: String) {
        try {
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться подпиской")
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "Error sharing subscription URL", e)
        }
    }
    
    // Функция для шаринга URL подписки через QR-код
    fun shareSubscriptionUrlQr(url: String) {
        try {
            showQrCodeDialog = url
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "Error sharing subscription QR", e)
        }
    }
    
    // Загружаем данные при открытии
    LaunchedEffect(showVpnConfig) {
        if (showVpnConfig) {
            // Сбрасываем пинги при открытии, чтобы проверить заново
            pingResults = emptyMap()
            pingChecked = emptySet()
            loadAllData(runPings = true) // Запускаем пинги при открытии окна
        }
    }
    
    // Анимация
    val animationState = remember { MenuItemAnimationState() }
    var isVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(false) }
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showVpnConfig) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "vpn_config_background_alpha"
    )
    
    LaunchedEffect(showVpnConfig) {
        if (showVpnConfig) {
            isVisible = true
            // Небольшая задержка для плавной анимации
            delay(50)
            isContentVisible = true
        } else {
            // Сначала скрываем контент
            isContentVisible = false
            // Ждем завершения анимации, потом скрываем overlay
            delay(450)
            isVisible = false
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    if (isVisible || showVpnConfig) {
        // Затемняющий фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1001f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = backgroundInteractionSource,
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                    )
            )
        }
        
        // Модальное окно
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10000f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Анимация выезда справа в центр (как у других модальных окон)
            AnimatedVisibility(
                visible = isContentVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F24)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .padding(bottom = 60.dp) // Место для кнопки закрытия
                    ) {
                        // Заголовок с кнопками добавления
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp), // Добавлен отступ сверху
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedString(R.string.subscriptions_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Кнопки добавления с подписями слева - стиль как у CloseButton
                            // TODO: Локализация - "QR-код" -> "QR-code", "Конфиг" -> "conf", "Пинг всех" -> "Ping all"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Пинг всех кнопка с эффектами
                                Card(
                                    onClick = {
                                        // Пинг всех конфигов
                                        coroutineScope.launch {
                                            val allConfigs = subscriptionBlocks.flatMap { it.configs } + standaloneConfigs
                                            allConfigs.forEach { config ->
                                                if (config.host != "unknown" && config.host.isNotBlank()) {
                                                    launch {
                                                        try {
                                                            val pingTime = com.kizvpn.client.util.PingUtil.pingServer(config.host, config.port)
                                                            pingResults = pingResults + (config.id to pingTime)
                                                            pingChecked = pingChecked + config.id
                                                        } catch (e: Exception) {
                                                            pingResults = pingResults + (config.id to null)
                                                            pingChecked = pingChecked + config.id
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(12.dp),
                                            clip = false
                                        )
                                        .graphicsLayer {
                                            translationY = -2f // Легкий подъем
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Иконка
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.kizvpn.client.R.drawable.ic_ping_all),
                                            contentDescription = localizedString(R.string.ping_all),
                                            tint = Color(0xFF4FC3F7),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        // Текст
                                        Text(
                                            text = localizedString(R.string.ping_all), // EN: "Ping all"
                                            color = Color(0xFFFF9800),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // QR-код кнопка с эффектами
                                Card(
                                    onClick = { showQrScanner = true },
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(12.dp),
                                            clip = false
                                        )
                                        .graphicsLayer {
                                            translationY = -2f // Легкий подъем
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Иконка
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.kizvpn.client.R.drawable.ic_camera_qr),
                                            contentDescription = "Сканировать QR",
                                            tint = Color(0xFF4FC3F7),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        // Текст
                                        Text(
                                            text = localizedString(R.string.qr_code), // EN: "QR-code"
                                            color = Color(0xFFFF9800),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Конфиг кнопка с эффектами
                                Card(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(12.dp),
                                            clip = false
                                        )
                                        .graphicsLayer {
                                            translationY = -2f // Легкий подъем
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Иконка
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.kizvpn.client.R.drawable.ic_add_key),
                                            contentDescription = "Добавить",
                                            tint = Color(0xFF4FC3F7),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        // Текст
                                        Text(
                                            text = localizedString(R.string.config_label), // EN: "Config"
                                            color = Color(0xFFFF9800),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Контент - список subscription блоков и отдельных конфигов
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Заголовок Subscription
                            if (subscriptionBlocks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = localizedString(R.string.subscription_single),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            // Subscription блоки
                            items(subscriptionBlocks.size) { index ->
                                val block = subscriptionBlocks[index]
                                SubscriptionBlockCard(
                                    block = block,
                                    pingResults = pingResults,
                                    isRefreshing = refreshingBlockId == block.id,
                                    onRefresh = { refreshSubscription(block.id, block.url) },
                                    onDelete = { deleteSubscriptionBlock(block.id) },
                                    onConfigSelect = { selectConfig(it) },
                                    onConfigShare = { shareConfig(it) },
                                    onConfigShareQr = { shareConfigQr(it) },
                                    onConfigDelete = { deleteConfig(it) },
                                    onToggleExpand = {
                                        subscriptionBlocks = subscriptionBlocks.toMutableList().also {
                                            it[index] = block.copy(isExpanded = !block.isExpanded)
                                        }
                                    },
                                    onShareSubscription = { shareSubscriptionUrl(block.url) },
                                    onShareSubscriptionQr = { shareSubscriptionUrlQr(block.url) }
                                )
                            }
                            
                            // Отдельные конфиги
                            if (standaloneConfigs.isNotEmpty()) {
                                item {
                                    Text(
                                        text = localizedString(R.string.separate_configs),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                
                                items(standaloneConfigs.size) { index ->
                                    val config = standaloneConfigs[index]
                                    ConfigItemCard(
                                        config = config,
                                        ping = pingResults[config.id],
                                        onSelect = { selectConfig(config) },
                                        onShare = { shareConfig(config) },
                                        onShareQr = { shareConfigQr(config) },
                                        onDelete = { deleteConfig(config) }
                                    )
                                }
                            }
                            
                            // Пустое состояние
                            if (subscriptionBlocks.isEmpty() && standaloneConfigs.isEmpty() && !isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = context.getString(R.string.no_subscriptions),
                                                color = Color.White.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = context.getString(R.string.click_to_add),
                                                color = Color.White.copy(alpha = 0.3f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Индикатор загрузки
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF4FC3F7))
                        }
                    }
                    
                    // Кнопка закрытия
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        CloseButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
        
        // QR сканер - показываем поверх всего как Dialog
        if (showQrScanner) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showQrScanner = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    com.kizvpn.client.ui.screens.QRCodeScreen(
                        onBack = { showQrScanner = false },
                        onConfigScanned = { scannedConfig ->
                            showQrScanner = false
                            // Добавляем отсканированный конфиг
                            addConfig(scannedConfig)
                        }
                    )
                }
            }
        }
        
        // Диалог для показа QR-кода
        showQrCodeDialog?.let { qrContent ->
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            
            Dialog(
                onDismissRequest = { showQrCodeDialog = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                // Клик вне QR закрывает диалог
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showQrCodeDialog = null },
                    contentAlignment = Alignment.Center
                ) {
                    // QR-код - клик копирует в буфер
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .shadow(16.dp, RoundedCornerShape(12.dp))
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(qrContent))
                                android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridSize = 25
                            val cellSize = size.width / gridSize
                            val hash = qrContent.hashCode()
                            
                            fun drawPositionMarker(startX: Float, startY: Float) {
                                for (i in 0 until 7) {
                                    drawRect(Color.Black, Offset(startX + i * cellSize, startY), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                    drawRect(Color.Black, Offset(startX + i * cellSize, startY + 6 * cellSize), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                    drawRect(Color.Black, Offset(startX, startY + i * cellSize), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                    drawRect(Color.Black, Offset(startX + 6 * cellSize, startY + i * cellSize), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                }
                                for (i in 2 until 5) {
                                    for (j in 2 until 5) {
                                        drawRect(Color.Black, Offset(startX + i * cellSize, startY + j * cellSize), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                    }
                                }
                            }
                            
                            drawPositionMarker(0f, 0f)
                            drawPositionMarker((gridSize - 7) * cellSize, 0f)
                            drawPositionMarker(0f, (gridSize - 7) * cellSize)
                            
                            var seed = hash
                            for (y in 0 until gridSize) {
                                for (x in 0 until gridSize) {
                                    if ((x < 8 && y < 8) || (x >= gridSize - 8 && y < 8) || (x < 8 && y >= gridSize - 8)) continue
                                    seed = (seed * 1103515245 + 12345) and 0x7fffffff
                                    if (seed % 3 == 0) {
                                        drawRect(Color.Black, Offset(x * cellSize, y * cellSize), androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Диалог добавления
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = Color(0xFF2C2C2E),
                title = {
                    Text(
                        text = context.getString(R.string.add_subscription),
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            text = context.getString(R.string.enter_subscription_url),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = addDialogInput,
                            onValueChange = { 
                                addDialogInput = it
                                addDialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("URL или ключ...", color = Color.White.copy(alpha = 0.3f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4FC3F7),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            ),
                            maxLines = 5
                        )
                        if (addDialogError != null) {
                            Text(
                                text = addDialogError!!,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { addConfig(addDialogInput) },
                        enabled = addDialogInput.isNotBlank()
                    ) {
                        Text(context.getString(R.string.add), color = Color(0xFF4FC3F7))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(context.getString(R.string.cancel), color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }
        } // Закрытие AnimatedVisibility
    } // Закрытие Box
}

/**
 * Карточка блока подписки
 */
@Composable
fun SubscriptionBlockCard(
    block: VpnSubscriptionBlock,
    pingResults: Map<String, Int?>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onConfigSelect: (VpnConfigItem) -> Unit,
    onConfigShare: (VpnConfigItem) -> Unit,
    onConfigShareQr: (VpnConfigItem) -> Unit,
    onConfigDelete: (VpnConfigItem) -> Unit,
    onToggleExpand: () -> Unit,
    onShareSubscription: () -> Unit,
    onShareSubscriptionQr: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false
            )
            .graphicsLayer {
                translationY = -2f
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2E)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок блока
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Иконка обновления (или индикатор загрузки)
                    IconButton(
                        onClick = { if (!isRefreshing) onRefresh() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF4FC3F7),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Иконка свернуть/развернуть
                    Icon(
                        imageVector = if (block.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Column {
                        // Количество ключей X (цифра красная)
                        Row {
                            Text(
                                text = localizedString(R.string.config_count) + " ",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${block.configs.size}",
                                color = Color(0xFFE57373), // Красный цвет для цифры
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        // Информация о днях и трафике
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Сначала дни
                            if (block.daysRemaining != null) {
                                Text(
                                    text = block.daysRemaining,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // Потом трафик: показываем использованный, а если есть лимит - добавляем максимум
                            if (block.usedTraffic != null) {
                                val trafficText = if (block.totalTraffic != null) {
                                    "${block.usedTraffic} / ${block.totalTraffic}"
                                } else {
                                    "${block.usedTraffic} (∞)" // Безлимит
                                }
                                Text(
                                    text = trafficText,
                                    color = Color(0xFF4FC3F7),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                Row {
                    // Кнопка QR-код для подписки
                    IconButton(
                        onClick = onShareSubscriptionQr,
                        modifier = Modifier.size(32.dp)
                    ) {
                        // Красивая иконка QR-код (4 квадрата с точками)
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val qrColor = Color(0xFF4FC3F7)
                            val s = size.width
                            val gap = s * 0.08f
                            val blockSize = (s - gap) / 2
                            val cornerRadius = s * 0.08f
                            val dotSize = blockSize * 0.35f
                            
                            // Левый верхний квадрат с точкой
                            drawRoundRect(qrColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                            drawRect(Color(0xFF2C2C2E), Offset(blockSize * 0.25f, blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                            drawRect(qrColor, Offset((blockSize - dotSize) / 2, (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                            
                            // Правый верхний квадрат с точкой
                            drawRoundRect(qrColor, Offset(blockSize + gap, 0f), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                            drawRect(Color(0xFF2C2C2E), Offset(blockSize + gap + blockSize * 0.25f, blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                            drawRect(qrColor, Offset(blockSize + gap + (blockSize - dotSize) / 2, (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                            
                            // Левый нижний квадрат с точкой
                            drawRoundRect(qrColor, Offset(0f, blockSize + gap), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                            drawRect(Color(0xFF2C2C2E), Offset(blockSize * 0.25f, blockSize + gap + blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                            drawRect(qrColor, Offset((blockSize - dotSize) / 2, blockSize + gap + (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                            
                            // Правый нижний - маленькие квадратики
                            val smallBlock = blockSize * 0.4f
                            val smallGap = blockSize * 0.1f
                            val startX = blockSize + gap
                            val startY = blockSize + gap
                            drawRect(qrColor, Offset(startX, startY), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                            drawRect(qrColor, Offset(startX + smallBlock + smallGap, startY), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                            drawRect(qrColor, Offset(startX + smallBlock + smallGap, startY + smallBlock + smallGap), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                        }
                    }
                    
                    // Кнопка поделиться подпиской
                    IconButton(
                        onClick = onShareSubscription,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = Color(0xFF4FC3F7),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Кнопка удаления блока
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Шкала трафика (если есть данные)
            if (block.usedTrafficBytes != null && block.totalTrafficBytes != null && block.totalTrafficBytes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = (block.usedTrafficBytes.toFloat() / block.totalTrafficBytes.toFloat()).coerceIn(0f, 1f)
                val progressColor = when {
                    progress < 0.7f -> Color(0xFF4CAF50) // Зеленый
                    progress < 0.9f -> Color(0xFFFFC107) // Желтый
                    else -> Color(0xFFF44336) // Красный
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0xFF3A3A3E), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(progressColor, RoundedCornerShape(2.dp))
                    )
                }
            }
            
            // Список конфигов (если развернуто)
            if (block.isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                block.configs.forEach { config ->
                    ConfigItemCard(
                        config = config,
                        ping = pingResults[config.id],
                        onSelect = { onConfigSelect(config) },
                        onShare = { onConfigShare(config) },
                        onShareQr = { onConfigShareQr(config) },
                        onDelete = { onConfigDelete(config) },
                        isInSubscription = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Карточка отдельного конфига
 */
@Composable
fun ConfigItemCard(
    config: VpnConfigItem,
    ping: Int?,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onShareQr: () -> Unit,
    onDelete: () -> Unit,
    isInSubscription: Boolean = false
) {
    val backgroundColor = if (config.isActive) Color(0xFF1A3A1A) else Color(0xFF252528)
    
    // Цвет бейджа протокола - цветной только для активных ключей
    val protocolColor = if (config.isActive) {
        when (config.protocol) {
            "VLESS" -> Color(0xFF4FC3F7) // Голубой для VLESS
            "WireGuard" -> Color(0xFF81C784) // Зелёный для WireGuard
            else -> Color(0xFF9E9E9E) // Серый для других
        }
    } else {
        Color(0xFF353538) // Тёмный фон для неактивных (похож на карточку)
    }
    
    // Цвет текста протокола
    val protocolTextColor = if (config.isActive) Color.White else Color.White.copy(alpha = 0.7f)
    
    // Внешний Row: прямоугольник протокола + карточка ключа
    // Используем IntrinsicSize.Min чтобы прямоугольник был такой же высоты как карточка
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Уменьшил расстояние
    ) {
        // Прямоугольник протокола ОТДЕЛЬНО от карточки - кликабельный для выбора ключа
        Box(
            modifier = Modifier
                .width(36.dp) // Компактная ширина
                .fillMaxHeight() // Заполняем высоту родителя (равную карточке)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(10.dp),
                    clip = false
                )
                .graphicsLayer {
                    translationY = -2f
                }
                .background(
                    color = protocolColor,
                    shape = RoundedCornerShape(10.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0x20FFFFFF),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { onSelect() }, // Клик для выбора ключа
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true) // Разрешаем тексту выходить за границы до поворота
                    .graphicsLayer { rotationZ = -90f }
            ) {
                Text(
                    text = config.protocol.uppercase(),
                    color = protocolTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1
                )
            }
        }
        
        // Карточка ключа
        Card(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    clip = false
                )
                .graphicsLayer {
                    translationY = -2f
                }
                .clickable { onSelect() },
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
        ) {
            // Пинг - определяем заранее для использования в layout
            val pingColor = when {
                ping == null -> Color(0xFF607D8B) // Серый - загрузка
                ping < 0 -> Color(0xFF9E9E9E) // Темно-серый - недоступен
                ping < 100 -> Color(0xFF4CAF50) // Зеленый - отлично
                ping < 300 -> Color(0xFFFFC107) // Желтый - нормально  
                else -> Color(0xFFF44336) // Красный - плохо
            }
            val pingText = when {
                ping == null -> "..."
                ping < 0 -> "N/A"
                else -> "$ping ms"
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Верхняя строка: Название ключа слева, кнопки справа
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Имя конфига - прижат к верхнему левому углу
                    Text(
                        text = config.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Кнопки справа
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Кнопка QR-код
                        IconButton(
                            onClick = onShareQr,
                            modifier = Modifier.size(28.dp)
                        ) {
                            // Красивая иконка QR-код (4 квадрата с точками)
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val qrColor = Color(0xFF4FC3F7)
                                val s = size.width
                                val gap = s * 0.08f
                                val blockSize = (s - gap) / 2
                                val cornerRadius = s * 0.08f
                                val dotSize = blockSize * 0.35f
                                
                                // Левый верхний квадрат с точкой
                                drawRoundRect(qrColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                                drawRect(Color(0xFF252528), Offset(blockSize * 0.25f, blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                                drawRect(qrColor, Offset((blockSize - dotSize) / 2, (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                                
                                // Правый верхний квадрат с точкой
                                drawRoundRect(qrColor, Offset(blockSize + gap, 0f), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                                drawRect(Color(0xFF252528), Offset(blockSize + gap + blockSize * 0.25f, blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                                drawRect(qrColor, Offset(blockSize + gap + (blockSize - dotSize) / 2, (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                                
                                // Левый нижний квадрат с точкой
                                drawRoundRect(qrColor, Offset(0f, blockSize + gap), androidx.compose.ui.geometry.Size(blockSize, blockSize), androidx.compose.ui.geometry.CornerRadius(cornerRadius))
                                drawRect(Color(0xFF252528), Offset(blockSize * 0.25f, blockSize + gap + blockSize * 0.25f), androidx.compose.ui.geometry.Size(blockSize * 0.5f, blockSize * 0.5f))
                                drawRect(qrColor, Offset((blockSize - dotSize) / 2, blockSize + gap + (blockSize - dotSize) / 2), androidx.compose.ui.geometry.Size(dotSize, dotSize))
                                
                                // Правый нижний - маленькие квадратики
                                val smallBlock = blockSize * 0.4f
                                val smallGap = blockSize * 0.1f
                                val startX = blockSize + gap
                                val startY = blockSize + gap
                                drawRect(qrColor, Offset(startX, startY), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                                drawRect(qrColor, Offset(startX + smallBlock + smallGap, startY), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                                drawRect(qrColor, Offset(startX + smallBlock + smallGap, startY + smallBlock + smallGap), androidx.compose.ui.geometry.Size(smallBlock, smallBlock))
                            }
                        }
                    
                        // Кнопка шаринга
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Поделиться",
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        // Кнопка удаления
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Нижняя строка: Хост слева, Пинг справа (прижат к Edit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Хост - прижат к нижнему левому углу
                    Text(
                        text = config.host,
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Пинг - прижат справа (под кнопками)
                    Box(
                        modifier = Modifier
                            .background(
                                color = pingColor,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = pingText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Обновление пинга в VPN сервисе для уведомления
 */
private fun updateVpnServicePing(context: Context, ping: Int) {
    try {
        val serviceIntent = Intent(context, com.kizvpn.client.vpn.KizVpnService::class.java)
        context.bindService(serviceIntent, object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as? com.kizvpn.client.vpn.KizVpnService.KizVpnBinder
                binder?.updatePing(ping)
                context.unbindService(this)
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
        }, Context.BIND_AUTO_CREATE)
    } catch (e: Exception) {
        android.util.Log.e("BottomMenuButton", "Error updating VPN service ping", e)
    }
}