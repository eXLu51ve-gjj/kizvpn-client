package com.kizvpn.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
            contentDescription = "Меню",
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
    buttonPosition: androidx.compose.ui.geometry.Offset? = null
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

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
            packageInfo.versionName ?: "2.2.1"
        } catch (e: Exception) {
            "2.2.1"
        }
    }
    
    // Создаем состояние анимации для модального окна
    val animationState = remember { MenuItemAnimationState() }
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showAbout) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "about_background_alpha"
    )
    
    // Управление анимациями
    LaunchedEffect(showAbout) {
        if (showAbout) {
            isVisible = true
            // Анимация открытия (медленное появление из центра с масштабированием)
            coroutineScope.launch {
                // Начинаем с маленького размера и невидимого
                animationState.scale.snapTo(0.8f)
                animationState.opacity.snapTo(0f)
                
                launch {
                    animationState.scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        } else {
            // Анимация закрытия (плавное затухание)
            coroutineScope.launch {
                launch {
                    animationState.opacity.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    animationState.scale.animateTo(
                        targetValue = 0.9f,
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                delay(300)
                isVisible = false
            }
        }
    }
    
    // Функции для открытия Email и Telegram
    val openEmail = {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("your-email@example.com"))
                putExtra(Intent.EXTRA_SUBJECT, "")
            }
            context.startActivity(Intent.createChooser(intent, "Открыть почту"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    val openTelegram = {
        try {
            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_telegram"))
            context.startActivity(telegramIntent)
        } catch (e: Exception) {
            // Если не удалось открыть Telegram, пытаемся через браузер
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_telegram"))
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showAbout) {
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
                .zIndex(9999f), // Очень высокий zIndex, чтобы быть поверх всего
            contentAlignment = Alignment.Center
        ) {
            // Используем простой Box без rotation для AboutModal
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
                    shape = MaterialTheme.shapes.large, // Закругленные углы
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
                        // Заголовок "KIZ VPN" (мягкий синий) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "KIZ VPN",
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
                        
                        // Подзаголовок с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "Ваша анонимность и безопасность в сети",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 16.sp,
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
                            text = "Простой и бесплатный VPN для защиты вашего подключения к интернету.\n\nОбеспечивает анонимность и безопасность, чтобы вы могли серфить в сети спокойно.",
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
                        
                        // "Что мы предлагаем" (оранжевый) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "Что мы предлагаем",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 18.sp,
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
                                text = "Защита приватности: Ваши данные под надёжной защитой.",
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
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = "Высокая скорость: Стабильное и быстрое соединение.",
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
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = "Полностью бесплатно: Никаких скрытых платежей.",
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
                                textAlign = TextAlign.Center
                            )
                            androidx.compose.material3.Text(
                                text = "Простота в использовании: Подключение одним нажатием.",
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
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // "Важно" (оранжевый) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "Важно",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 18.sp,
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
                            text = "Приложение находится на стадии тестирования!\n\nМы активно работаем над его улучшением.\n\nЕсли вы столкнётесь с ошибками или багами\nПожалуйста, сообщите нам — ваша помощь бесценна!",
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
                            text = "Связь с разработчиком",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 18.sp,
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
                            // Кликабельный Email с тенью
                            androidx.compose.material3.Text(
                                text = "Email: your-email@example.com",
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
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openEmail()
                                    }
                                )
                            )
                            // Кликабельный Telegram с тенью
                            androidx.compose.material3.Text(
                                text = "Telegram: @your_telegram",
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
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        openTelegram()
                                    }
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Версия сборки (внизу по центру) с легкой обводкой
                        androidx.compose.material3.Text(
                            text = "Версия $versionName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Default,
                                fontSize = 11.sp,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                        
                        // Ник разработчика (красный, справа) с легкой обводкой
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
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
                                color = Color(0xFFFF3B30)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(60.dp)) // Отступ для кнопки закрытия
                    }
                }
            }
            
            // Кнопка закрытия жестко закреплена к низу модального окна
            CloseButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
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
                            text = "Вставьте ключ Vless",
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
                                androidx.compose.material3.Text("Активация...", color = Color.White)
                            } else {
                                androidx.compose.material3.Text("Активировать", color = Color.White)
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
                            androidx.compose.material3.Text("Закрыть", color = Color.White)
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
                            text = "Вставьте WireGuard конфиг в поле ниже",
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
                                androidx.compose.material3.Text("Сохранение...", color = Color.White)
                            } else {
                                androidx.compose.material3.Text("Сохранить", color = Color.White)
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
                            androidx.compose.material3.Text("Закрыть", color = Color.White)
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
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(Color(0xFFFF9500))
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
                                text = "Статистика",
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
                                    text = "Трафик",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color(0xFF5B9BD5).copy(alpha = 0.8f) // Мягкий синий цвет
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Входящий: $formattedDownload",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily,
                                        fontSize = 13.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Исходящий: $formattedUpload",
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
    onOpenRouting: () -> Unit = {}
) {
    val zenterFontFamily = getJuraFontFamily()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
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
                                text = "Настройки",
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
                            colorFilter = ColorFilter.tint(Color(0xFFFF9500))
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
                            title = localizedString(R.string.auto_connect),
                            description = "",
                            checked = useAutoConnect,
                            onCheckedChange = { useAutoConnect = it },
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
                            colorFilter = ColorFilter.tint(Color(0xFFFF9500))
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
                            onCheckedChange = { useNotifications = it },
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
                            colorFilter = ColorFilter.tint(Color(0xFFFF9500))
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
                            colorFilter = ColorFilter.tint(Color(0xFFFF9500))
                        )
                    }
                }

                // Безопасность (индекс 1)
                AnimatedModalCard(animationState = animationStates[1]) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SettingsSwitchCard(
                            title = "Безопасность",
                            description = "",
                            checked = securityEnabled,
                            onCheckedChange = { securityEnabled = it },
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
                            colorFilter = ColorFilter.tint(Color(0xFFFF9500))
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
                                .height(32.dp)
                                .padding(horizontal = 4.dp)
                                .zIndex(0f)
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
                                text = "История подключений",
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
                                    text = "История подключений будет отображаться здесь",
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
                        text = "Сервер: ${entry.server}",
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
                        text = "Длительность: $durationText",
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnConfigModal(
    showVpnConfig: Boolean,
    onDismiss: () -> Unit,
    onActivateKey: (String, (Int?) -> Unit) -> Unit,
    onSaveWireGuardConfig: (String) -> Unit = {},
    onSelectConfig: (String, com.kizvpn.client.config.ConfigParser.Protocol) -> Unit = { _, _ -> }, // Callback для выбора конфига
    onDisconnectClick: () -> Unit = {}, // Callback для отключения VPN
    onShowConfigNotification: (String) -> Unit = {} // Callback для показа уведомления сверху экрана
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val configParser = remember { com.kizvpn.client.config.ConfigParser() }
    val zenterFontFamily = getJuraFontFamily()
    
    // Тип конфига (Vless или WireGuard)
    var configType by remember { mutableStateOf(0) } // 0 = Vless, 1 = WireGuard
    
    var vlessInput by remember { mutableStateOf("") }
    var wireGuardInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Загружаем сохраненные конфиги
    val prefs = remember { context.getSharedPreferences("KizVpnPrefs", Context.MODE_PRIVATE) }
    var savedVlessConfig by remember { mutableStateOf(prefs.getString("saved_config", null)) }
    var savedVlessConfigName by remember { mutableStateOf(prefs.getString("saved_config_name", null)) }
    var savedWireGuardConfig by remember { mutableStateOf(prefs.getString("saved_wireguard_config", null)) }
    var configActivated by remember { mutableStateOf(prefs.getBoolean("config_activated", false)) }
    
    // Список сохраненных конфигов
    data class SavedConfig(
        val config: String,
        val name: String?,
        val type: com.kizvpn.client.config.ConfigParser.Protocol,
        val isActive: Boolean
    )
    
    val savedConfigs = remember { mutableStateListOf<SavedConfig>() }
    
    // Выбранный конфиг (для кнопки "Выбрать конфиг")
    var selectedConfig by remember { mutableStateOf<SavedConfig?>(null) }
    
    // Версия списка конфигов для отслеживания изменений
    var configsListVersion by remember { mutableStateOf(0) }
    
    // Функция для загрузки конфигов (фильтрует по configType)
    fun loadConfigs() {
        savedConfigs.clear()
        val activeConfigType = prefs.getString("active_config_type", null)
        
        Log.d("VpnConfigModal", "loadConfigs(): configType = $configType, activeConfigType = $activeConfigType")
        
        // Мигрируем старые конфиги в новый формат списков (если нужно)
        // Это нужно делать только для текущей вкладки, чтобы не замедлять загрузку
        try {
            if (configType == 0) {
                // Миграция Vless конфигов
                val vlessConfig = prefs.getString("saved_config", null)
                val vlessName = prefs.getString("saved_config_name", null)
                if (!vlessConfig.isNullOrBlank()) {
                    val parsedConfig = configParser.parseConfig(vlessConfig)
                    if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.VLESS) {
                        val listJson = prefs.getString("saved_vless_configs_list", "[]")
                        val configList = org.json.JSONArray(listJson)
                        var exists = false
                        for (i in 0 until configList.length()) {
                            val item = configList.getJSONObject(i)
                            if (item.getString("config") == vlessConfig) {
                                exists = true
                                break
                            }
                        }
                        if (!exists) {
                            val configObject = org.json.JSONObject()
                            configObject.put("config", vlessConfig)
                            configObject.put("name", vlessName ?: "Vless конфиг")
                            configObject.put("addedAt", System.currentTimeMillis())
                            configList.put(configObject)
                            prefs.edit().putString("saved_vless_configs_list", configList.toString()).commit()
                            Log.d("VpnConfigModal", "loadConfigs(): Мигрирован Vless конфиг в список")
                        }
                    }
                }
            } else {
                // Миграция WireGuard конфигов
                val wireGuardConfig = prefs.getString("saved_wireguard_config", null)
                if (!wireGuardConfig.isNullOrBlank()) {
                    val parsedConfig = configParser.parseConfig(wireGuardConfig)
                    if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                        val listJson = prefs.getString("saved_wireguard_configs_list", "[]")
                        val configList = org.json.JSONArray(listJson)
                        var exists = false
                        for (i in 0 until configList.length()) {
                            val item = configList.getJSONObject(i)
                            if (item.getString("config") == wireGuardConfig) {
                                exists = true
                                break
                            }
                        }
                        if (!exists) {
                            val configObject = org.json.JSONObject()
                            configObject.put("config", wireGuardConfig)
                            configObject.put("name", "WireGuard конфиг")
                            configObject.put("addedAt", System.currentTimeMillis())
                            configList.put(configObject)
                            prefs.edit().putString("saved_wireguard_configs_list", configList.toString()).commit()
                            Log.d("VpnConfigModal", "loadConfigs(): Мигрирован WireGuard конфиг в список")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "loadConfigs(): Ошибка при миграции старых конфигов", e)
        }
        
        try {
            // Определяем, какой список конфигов загружать
            val configListKey = if (configType == 0) "saved_vless_configs_list" else "saved_wireguard_configs_list"
            val listJson = prefs.getString(configListKey, "[]")
            val configList = org.json.JSONArray(listJson)
            
            Log.d("VpnConfigModal", "loadConfigs(): Загружаем список из $configListKey, найдено конфигов: ${configList.length()}")
            
            // Загружаем все конфиги из списка
            for (i in 0 until configList.length()) {
                try {
                    val item = configList.getJSONObject(i)
                    val configString = item.getString("config")
                    val configName = item.optString("name", null)
                    
                    // Парсим конфиг, чтобы определить реальный протокол
                    val parsedConfig = configParser.parseConfig(configString)
                    if (parsedConfig != null) {
                        val expectedProtocol = if (configType == 0) 
                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS 
                        else 
                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD
                        
                        // Проверяем, что протокол соответствует ожидаемому
                        if (parsedConfig.protocol == expectedProtocol) {
                            // Определяем, активен ли этот конфиг
                            // Активным считается конфиг, который соответствует active_config_type и сохранен в старых ключах
                            val savedConfig = if (configType == 0) prefs.getString("saved_config", null) else prefs.getString("saved_wireguard_config", null)
                            val isActive = when (parsedConfig.protocol) {
                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> 
                                    activeConfigType == "vless" && configString == savedConfig
                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> 
                                    activeConfigType == "wireguard" && configString == savedConfig
                                else -> false
                            }
                            
                            savedConfigs.add(SavedConfig(
                                config = configString,
                                name = configName,
                                type = parsedConfig.protocol,
                                isActive = isActive
                            ))
                            Log.d("VpnConfigModal", "loadConfigs(): Добавлен конфиг: name=$configName, isActive=$isActive, config preview=${configString.take(50)}...")
                        } else {
                            Log.w("VpnConfigModal", "loadConfigs(): Пропускаем конфиг с неправильным протоколом: ${parsedConfig.protocol} (ожидается $expectedProtocol)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VpnConfigModal", "loadConfigs(): Ошибка при загрузке конфига из списка (index=$i)", e)
                }
            }
            
            // Если список пустой, пытаемся загрузить из старых ключей (для обратной совместимости)
            if (savedConfigs.isEmpty()) {
                Log.d("VpnConfigModal", "loadConfigs(): Список пуст, пытаемся загрузить из старых ключей")
                val vless = prefs.getString("saved_config", null)
                val vlessName = prefs.getString("saved_config_name", null)
                val wireGuard = prefs.getString("saved_wireguard_config", null)
                
                if (configType == 0 && !vless.isNullOrBlank()) {
                    val parsedConfig = configParser.parseConfig(vless)
                    if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.VLESS) {
                        val isActive = activeConfigType == "vless" || (activeConfigType == null && vless != null)
                        savedConfigs.add(SavedConfig(
                            config = vless,
                            name = vlessName ?: "Vless конфиг",
                            type = parsedConfig.protocol,
                            isActive = isActive
                        ))
                        Log.d("VpnConfigModal", "loadConfigs(): Добавлен Vless конфиг из старого ключа")
                    }
                } else if (configType == 1 && !wireGuard.isNullOrBlank()) {
                    val parsedConfig = configParser.parseConfig(wireGuard)
                    if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                        val isActive = activeConfigType == "wireguard"
                        savedConfigs.add(SavedConfig(
                            config = wireGuard,
                            name = "WireGuard конфиг",
                            type = parsedConfig.protocol,
                            isActive = isActive
                        ))
                        Log.d("VpnConfigModal", "loadConfigs(): Добавлен WireGuard конфиг из старого ключа")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("VpnConfigModal", "loadConfigs(): Ошибка при загрузке списка конфигов", e)
            // Fallback: загружаем из старых ключей
            val vless = prefs.getString("saved_config", null)
            val vlessName = prefs.getString("saved_config_name", null)
            val wireGuard = prefs.getString("saved_wireguard_config", null)
            
            if (configType == 0 && !vless.isNullOrBlank()) {
                val parsedConfig = configParser.parseConfig(vless)
                if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.VLESS) {
                    val isActive = activeConfigType == "vless" || (activeConfigType == null && vless != null)
                    savedConfigs.add(SavedConfig(
                        config = vless,
                        name = vlessName ?: "Vless конфиг",
                        type = parsedConfig.protocol,
                        isActive = isActive
                    ))
                }
            } else if (configType == 1 && !wireGuard.isNullOrBlank()) {
                val parsedConfig = configParser.parseConfig(wireGuard)
                if (parsedConfig?.protocol == com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                    val isActive = activeConfigType == "wireguard"
                    savedConfigs.add(SavedConfig(
                        config = wireGuard,
                        name = "WireGuard конфиг",
                        type = parsedConfig.protocol,
                        isActive = isActive
                    ))
                }
            }
        }
        
        // Если выбранный конфиг не установлен, выбираем активный конфиг по умолчанию
        if (selectedConfig == null) {
            selectedConfig = savedConfigs.find { it.isActive }
            Log.d("VpnConfigModal", "loadConfigs(): selectedConfig = ${selectedConfig?.name} (isActive = ${selectedConfig?.isActive})")
        }
        
        Log.d("VpnConfigModal", "loadConfigs(): Итого загружено конфигов: ${savedConfigs.size}")
    }
    
    // Сбрасываем состояние при закрытии модального окна
    LaunchedEffect(showVpnConfig) {
        if (!showVpnConfig) {
            // Сбрасываем состояние загрузки при закрытии
            isSaving = false
            errorMessage = null
            successMessage = null
            Log.d("VpnConfigModal", "Модальное окно закрыто, состояние сброшено")
        }
    }
    
    // Загружаем конфиги при открытии модального окна и при изменении типа
    LaunchedEffect(showVpnConfig, configType) {
        if (showVpnConfig) {
            selectedConfig = null // Сбрасываем выбор при открытии модального окна
            // Сбрасываем состояние загрузки при открытии
            isSaving = false
            errorMessage = null
            successMessage = null
            // Принудительно обновляем состояние
            vlessInput = ""
            wireGuardInput = ""
            loadConfigs()
            Log.d("VpnConfigModal", "LaunchedEffect: Загружены конфиги после открытия модального окна, configType=$configType, сохранено конфигов: ${savedConfigs.size}")
        }
    }
    
    // Отслеживаем изменения в SharedPreferences для автоматического обновления списка
    // Используем ключ для отслеживания изменений списков конфигов
    val vlessConfigsListKey = remember { "saved_vless_configs_list" }
    val wireGuardConfigsListKey = remember { "saved_wireguard_configs_list" }
    
    LaunchedEffect(configsListVersion) {
        if (showVpnConfig) {
            loadConfigs()
            Log.d("VpnConfigModal", "LaunchedEffect(configsListVersion): Перезагружены конфиги, версия=$configsListVersion")
        }
    }
    
    // Автоматически скрываем сообщения об успехе через 3 секунды
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(3000)
            successMessage = null
        }
    }
    
    // Автоматически скрываем сообщение об успешной активации через 3 секунды
    
    // Создаем состояние анимации для модального окна
    val animationState = remember { MenuItemAnimationState() }
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showVpnConfig) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "vpn_config_background_alpha"
    )
    
    // Управление анимациями
    LaunchedEffect(showVpnConfig) {
        if (showVpnConfig) {
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
        
        // Модальное окно VPN конфига - поверх всех элементов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10000f)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .alpha(animationState.opacity.value)
                    .scale(animationState.scale.value),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F24)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Заголовок в стиле NetworkStatsModal
                        Text(
                            text = "Подписки",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        // Переключатель типа конфига
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Кнопка Vless
                            Button(
                                onClick = { 
                                    configType = 0
                                    // Загружаем конфиги при переключении вкладки
                                    loadConfigs()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (configType == 0) Color(0xFF5B9BD5) else Color(0xFF5B9BD5).copy(alpha = 0.3f)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.material3.Text("Vless", color = Color.White)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Кнопка WireGuard
                            Button(
                                onClick = { 
                                    configType = 1
                                    // Загружаем конфиги при переключении вкладки
                                    loadConfigs()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (configType == 1) Color(0xFF5B9BD5) else Color(0xFF5B9BD5).copy(alpha = 0.3f)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                modifier = Modifier.weight(1f)
                            ) {
                                androidx.compose.material3.Text("WireGuard", color = Color.White)
                            }
                        }
                        
                        // Список сохраненных конфигов (показываем всегда, даже если пусто)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.5f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = if (savedConfigs.isEmpty()) 70.dp else 200.dp,
                                    max = if (savedConfigs.size <= 3) 300.dp else 400.dp
                                ) // Увеличиваем высоту карточки для отображения нескольких конфигов
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), // Уменьшаем padding
                                verticalArrangement = Arrangement.spacedBy(6.dp) // Уменьшаем расстояние
                            ) {
                                androidx.compose.material3.Text(
                                    "Сохраненные конфиги:",
                                    style = MaterialTheme.typography.titleSmall, // Уменьшаем размер шрифта
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontFamily = zenterFontFamily
                                )
                                
                                if (savedConfigs.isEmpty()) {
                                    // Показываем сообщение, если список пуст
                                    androidx.compose.material3.Text(
                                        "Нет сохраненных конфигов",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontFamily = zenterFontFamily,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 350.dp), // Увеличиваем высоту списка для отображения нескольких конфигов
                                        verticalArrangement = Arrangement.spacedBy(8.dp) // Увеличиваем расстояние между элементами
                                    ) {
                                        items(savedConfigs.size) { index ->
                                            val savedConfig = savedConfigs[index]
                                            Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Выбираем конфиг (но не активируем сразу)
                                                    // Парсим конфиг заново, чтобы определить реальный протокол
                                                    val parsedConfig = configParser.parseConfig(savedConfig.config)
                                                    if (parsedConfig != null) {
                                                        // Создаем обновленный SavedConfig с правильным протоколом
                                                        val correctedConfig = savedConfig.copy(type = parsedConfig.protocol)
                                                        selectedConfig = correctedConfig
                                                        Log.d("VpnConfigModal", "Selected config: name=${savedConfig.name}, original type=${savedConfig.type}, actual type=${parsedConfig.protocol}, config preview=${savedConfig.config.take(50)}...")
                                                        
                                                        // Если протокол не совпадает, предупреждаем
                                                        if (savedConfig.type != parsedConfig.protocol) {
                                                            Log.w("VpnConfigModal", "⚠️ PROTOCOL MISMATCH! Saved type=${savedConfig.type}, parsed type=${parsedConfig.protocol}")
                                                        }
                                                    } else {
                                                        Log.w("VpnConfigModal", "Failed to parse config: ${savedConfig.config.take(50)}...")
                                                        selectedConfig = savedConfig
                                                    }
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedConfig?.config == savedConfig.config) 
                                                    Color(0xFF5B9BD5).copy(alpha = 0.5f) // Выбранный конфиг более яркий
                                                else if (savedConfig.isActive) 
                                                    Color(0xFF5B9BD5).copy(alpha = 0.3f) // Активный конфиг
                                                else 
                                                    Color.White.copy(alpha = 0.1f) // Обычный конфиг
                                            ),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp) // Более прямоугольная форма
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                            .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                // Галочка слева для активного конфига
                                                if (savedConfig.isActive) {
                                                    androidx.compose.material3.Icon(
                                                        androidx.compose.material.icons.Icons.Default.CheckCircle,
                                                        contentDescription = "Активирован",
                                                        tint = Color(0xFF5B9BD5),
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .padding(end = 8.dp)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.width(32.dp))
                                                }
                                                
                                                // Текст конфига в центре
                                                Column(modifier = Modifier.weight(1f)) {
                                                    androidx.compose.material3.Text(
                                                        savedConfig.name ?: when (savedConfig.type) {
                                                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "Vless конфиг"
                                                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "WireGuard конфиг"
                                                            else -> "Конфиг"
                                                        },
                            style = MaterialTheme.typography.bodyMedium,
                                                        color = Color.White.copy(alpha = 0.9f),
                                                        fontFamily = zenterFontFamily
                                                    )
                                                    androidx.compose.material3.Text(
                                                        when (savedConfig.type) {
                                                            com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "Vless"
                                                            com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "WireGuard"
                                                            else -> "Неизвестно"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF5B9BD5),
                                                        fontFamily = zenterFontFamily
                                                    )
                                                }
                                                
                                                // Кнопка удаления справа
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        // Удаляем конфиг из списка
                                                        try {
                                                            Log.d("VpnConfigModal", "Удаление конфига: name=${savedConfig.name}, type=${savedConfig.type}, isActive=${savedConfig.isActive}")
                                                            
                                                            val configListKey = when (savedConfig.type) {
                                                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "saved_vless_configs_list"
                                                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "saved_wireguard_configs_list"
                                                                else -> {
                                                                    Log.e("VpnConfigModal", "Неизвестный тип конфига: ${savedConfig.type}")
                                                                    return@IconButton
                                                                }
                                                            }
                                                            
                                                            val existingListJson = prefs.getString(configListKey, "[]")
                                                            val configList = JSONArray(existingListJson)
                                                            Log.d("VpnConfigModal", "Список конфигов до удаления: ${configList.length()}")
                                                            
                                                            // Находим и удаляем конфиг
                                                            var indexToRemove = -1
                                                            for (i in 0 until configList.length()) {
                                                                val item = configList.getJSONObject(i)
                                                                if (item.getString("config") == savedConfig.config) {
                                                                    indexToRemove = i
                                                                    break
                                                                }
                                                            }
                                                            
                                                            if (indexToRemove >= 0) {
                                                                configList.remove(indexToRemove)
                                                                Log.d("VpnConfigModal", "Конфиг удален из списка по индексу $indexToRemove")
                                                            } else {
                                                                Log.w("VpnConfigModal", "Конфиг не найден в списке для удаления")
                                                            }
                                                            
                                                            // Сохраняем обновленный список
                                                            prefs.edit()
                                                                .putString(configListKey, configList.toString())
                                                                .commit()
                                                            
                                                            Log.d("VpnConfigModal", "Список конфигов после удаления: ${configList.length()}")
                                                            
                                                            // Если удаленный конфиг был активным, сбрасываем активный конфиг и отключаем VPN
                                                            if (savedConfig.isActive) {
                                                                Log.d("VpnConfigModal", "Удаленный конфиг был активным, сбрасываем active_config_type и отключаем VPN")
                                                                
                                                                // Отключаем VPN если подключен
                                                                onDisconnectClick()
                                                                
                                                                prefs.edit()
                                                                    .putString("active_config_type", null)
                                                                    .remove(when (savedConfig.type) {
                                                                        com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "saved_config"
                                                                        com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "saved_wireguard_config"
                                                                        else -> ""
                                                                    })
                                                                    .commit()
                                                            }
                                                            
                                                            // Обновляем список
                                                            configsListVersion++
                                                            loadConfigs()
                                                            Log.d("VpnConfigModal", "Конфиг удален: ${savedConfig.name}, осталось конфигов: ${savedConfigs.size}")
                                                        } catch (e: Exception) {
                                                            Log.e("VpnConfigModal", "Ошибка при удалении конфига", e)
                                                        }
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        androidx.compose.material.icons.Icons.Default.Delete,
                                                        contentDescription = "Удалить",
                                                        tint = Color(0xFFFF3B30),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } // Конец Card с сохраненными конфигами
                        
                        // Spacer для визуального разделения между Card и кнопками
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Кнопка "Выбрать конфиг" между списком конфигов и полем ввода
                        // Показываем кнопку, если есть сохраненные конфиги в списке ИЛИ есть сохраненный конфиг напрямую
                        val hasSavedConfig = savedConfigs.isNotEmpty() || 
                            (configType == 0 && !savedVlessConfig.isNullOrBlank()) ||
                            (configType == 1 && !savedWireGuardConfig.isNullOrBlank())
                        
                        if (hasSavedConfig) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    
                                    // Используем выбранный конфиг или активный конфиг по умолчанию
                                    // Сохраняем значения в локальные переменные для smart cast
                                    val vlessConfig = savedVlessConfig
                                    val vlessName = savedVlessConfigName
                                    val wireGuardConfig = savedWireGuardConfig
                                    
                                    val configToUse = selectedConfig ?: savedConfigs.find { it.isActive } ?: run {
                                        // Если в списке нет конфига, используем сохраненный конфиг напрямую
                                        if (configType == 0 && !vlessConfig.isNullOrBlank()) {
                                            val parsed = configParser.parseConfig(vlessConfig)
                                            if (parsed != null) {
                                                SavedConfig(
                                                    config = vlessConfig,
                                                    name = vlessName,
                                                    type = parsed.protocol,
                                                    isActive = true
                                                )
                                            } else null
                                        } else if (configType == 1 && !wireGuardConfig.isNullOrBlank()) {
                                            val parsed = configParser.parseConfig(wireGuardConfig)
                                            if (parsed != null) {
                                                SavedConfig(
                                                    config = wireGuardConfig,
                                                    name = "WireGuard конфиг",
                                                    type = parsed.protocol,
                                                    isActive = true
                                                )
                                            } else null
                                        } else null
                                    }
                                    
                                    configToUse?.let { config ->
                                        Log.d("VpnConfigModal", "Выбрать конфиг: name=${config.name}, saved type=${config.type}, config preview=${config.config.take(100)}...")
                                        
                                        // Парсим конфиг заново, чтобы гарантировать правильное определение протокола
                                        val parsedConfig = configParser.parseConfig(config.config)
                                        if (parsedConfig != null) {
                                            // Используем протокол из парсера, а не из сохраненного типа
                                            val actualProtocol = parsedConfig.protocol
                                            val activeConfigType = when (actualProtocol) {
                                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                                                else -> "vless"
                                            }
                                            
                                            Log.d("VpnConfigModal", "✅ Парсинг успешен: actualProtocol = $actualProtocol, activeConfigType = $activeConfigType")
                                            
                                            // Если протокол не совпадает с сохраненным, это серьезная проблема!
                                            if (config.type != actualProtocol) {
                                                Log.e("VpnConfigModal", "🚨 КРИТИЧЕСКАЯ ОШИБКА: saved type=${config.type}, actual type=$actualProtocol")
                                                Log.e("VpnConfigModal", "🚨 Используем actualProtocol = $actualProtocol (из парсера)")
                                            }
                                            
                                            // Сохраняем синхронно с commit() чтобы гарантировать сохранение
                                            prefs.edit().putString("active_config_type", activeConfigType).commit()
                                            
                                            // Проверяем, что active_config_type действительно сохранился
                                            val savedActiveType = prefs.getString("active_config_type", null)
                                            Log.d("VpnConfigModal", "✅ active_config_type сохранен: $savedActiveType")
                                            
                                            // Вызываем callback для выбора конфига с ПРАВИЛЬНЫМ протоколом
                                            Log.d("VpnConfigModal", "Вызываем onSelectConfig с protocol = $actualProtocol")
                                            onSelectConfig(config.config, actualProtocol)
                                            
                                            // Обновляем список конфигов для отображения нового активного конфига
                                            loadConfigs()
                                        } else {
                                            Log.e("VpnConfigModal", "❌ Не удалось распарсить конфиг: ${config.config.take(100)}...")
                                            Log.e("VpnConfigModal", "Используем сохраненный тип как fallback: ${config.type}")
                                            
                                            // Если парсинг не удался, используем сохраненный тип (fallback)
                                            val activeConfigType = when (config.type) {
                                                com.kizvpn.client.config.ConfigParser.Protocol.VLESS -> "vless"
                                                com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD -> "wireguard"
                                                else -> "vless"
                                            }
                                            prefs.edit().putString("active_config_type", activeConfigType).commit()
                                            Log.d("VpnConfigModal", "Вызываем onSelectConfig с protocol = ${config.type} (fallback)")
                                            onSelectConfig(config.config, config.type)
                                            loadConfigs()
                                        }
                                    } ?: run {
                                        Log.w("VpnConfigModal", "❌ configToUse is null!")
                                    }
                                    
                                    // Отключаем VPN если он подключен
                                    onDisconnectClick()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5B9BD5).copy(alpha = 0.8f)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 2.dp
                                ),
                                enabled = selectedConfig != null || savedConfigs.any { it.isActive } || 
                                    (configType == 0 && !savedVlessConfig.isNullOrBlank()) ||
                                    (configType == 1 && !savedWireGuardConfig.isNullOrBlank())
                            ) {
                                androidx.compose.material3.Text("Выбрать конфиг", color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        
                        // Поле ввода конфига в зависимости от типа
                        // Всегда показываем поле ввода, чтобы можно было добавлять новые конфиги
                        if (configType == 0) {
                            // Vless конфиг
                            // Показываем поле ввода всегда для добавления новых конфигов
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Заголовок для поля ввода
                            androidx.compose.material3.Text(
                                "Добавить Vless конфиг:",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = zenterFontFamily,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            OutlinedTextField(
                                value = vlessInput,
                                onValueChange = { 
                                    vlessInput = it
                                    errorMessage = null
                                    successMessage = null
                                },
                                placeholder = { 
                                    androidx.compose.material3.Text(
                                        "Вставьте Vless конфиг...", 
                                        color = Color.White.copy(alpha = 0.5f)
                                    ) 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp, max = 80.dp), // Компактное поле ввода
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
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
                                maxLines = 3
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = {
                                    if (vlessInput.isBlank()) {
                                        errorMessage = "Введите Vless конфиг"
                                        return@Button
                                    }
                                    
                                    // Сохраняем конфиг в переменную перед началом
                                    val configToSave = vlessInput.trim()
                                    
                                    // Проверяем, является ли это VLESS конфигом
                                    val parsedConfig = configParser.parseConfig(configToSave)
                                    if (parsedConfig == null || parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.VLESS) {
                                        errorMessage = "Это не является валидным Vless конфигом"
                                        return@Button
                                    }
                                    
                                    isSaving = true
                                    errorMessage = null
                                    successMessage = null
                                    
                                    // Сохраняем конфиг в список сразу
                                    try {
                                        val configListKey = "saved_vless_configs_list"
                                        val existingListJson = prefs.getString(configListKey, "[]")
                                        val configList = JSONArray(existingListJson)
                                        
                                        // Проверяем, есть ли уже такой конфиг
                                        var configExists = false
                                        for (i in 0 until configList.length()) {
                                            val item = configList.getJSONObject(i)
                                            if (item.getString("config") == configToSave) {
                                                configExists = true
                                                break
                                            }
                                        }
                                        
                                        // Если конфиг еще не существует, добавляем его
                                        if (!configExists) {
                                            val configObject = JSONObject()
                                            configObject.put("config", configToSave)
                                            configObject.put("name", parsedConfig.name ?: "Vless конфиг")
                                            configObject.put("addedAt", System.currentTimeMillis())
                                            configList.put(configObject)
                                            Log.d("VpnConfigModal", "Vless: Добавлен новый конфиг в список (всего: ${configList.length()})")
                                        } else {
                                            Log.d("VpnConfigModal", "Vless: Конфиг уже существует в списке")
                                        }
                                        
                                        // Сохраняем обновленный список
                                        prefs.edit()
                                            .putString(configListKey, configList.toString())
                                            .putString("saved_config", configToSave)
                                            .putString("active_config_type", "vless")
                                            .commit()
                                        
                                        // Обновляем локальные переменные
                                        savedVlessConfig = prefs.getString("saved_config", null)
                                        savedVlessConfigName = prefs.getString("saved_config_name", null)
                                        
                                        isSaving = false
                                        onShowConfigNotification("Vless конфиг сохранен! Проверяем подписку...")
                                        vlessInput = ""
                                        // Принудительно обновляем список конфигов
                                        configsListVersion++
                                        loadConfigs()
                                        Log.d("VpnConfigModal", "Vless сохранен: обновлен список конфигов, всего: ${savedConfigs.size}")
                                        
                                        // Проверяем подписку в фоне (не блокируя UI)
                                        coroutineScope.launch {
                                            onActivateKey(configToSave) { days ->
                                                if (days != null) {
                                                    onShowConfigNotification("Подписка активна: $days дней")
                                                    Log.d("VpnConfigModal", "Подписка проверена: $days дней")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isSaving = false
                                        errorMessage = "Ошибка сохранения: ${e.message}"
                                        Log.e("VpnConfigModal", "Vless: Ошибка при сохранении конфига в список", e)
                                    }
                                },
                                enabled = !isSaving && vlessInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5B9BD5)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.Text("Сохранение...", color = Color.White)
                                } else {
                                    androidx.compose.material3.Text("Сохранить", color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        
                        if (configType == 1) {
                            // WireGuard конфиг
                            // Показываем поле ввода всегда для добавления новых конфигов
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Заголовок для поля ввода
                            androidx.compose.material3.Text(
                                "Добавить WireGuard конфиг:",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = zenterFontFamily,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            OutlinedTextField(
                                value = wireGuardInput,
                                onValueChange = { 
                                    wireGuardInput = it
                                    errorMessage = null
                                    successMessage = null
                                },
                                placeholder = { 
                                    androidx.compose.material3.Text(
                                        "Вставьте WireGuard конфиг...", 
                                        color = Color.White.copy(alpha = 0.5f)
                                    ) 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 100.dp), // Компактное поле ввода
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
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
                            
                            Button(
                                onClick = {
                                    if (wireGuardInput.isBlank()) {
                                        errorMessage = "Введите WireGuard конфиг"
                                        return@Button
                                    }
                                    
                                    // Проверяем, является ли это WireGuard конфигом
                                    val parsedConfig = configParser.parseConfig(wireGuardInput.trim())
                                    if (parsedConfig == null || parsedConfig.protocol != com.kizvpn.client.config.ConfigParser.Protocol.WIREGUARD) {
                                        errorMessage = "Это не является валидным WireGuard конфигом"
                                        return@Button
                                    }
                                    
                                    isSaving = true
                                    errorMessage = null
                                    successMessage = null
                                    
                                    // Сохраняем конфиг в переменную перед началом
                                    val configToSave = wireGuardInput.trim()
                                    
                                    // Сохраняем конфиг в список
                                    try {
                                        val configListKey = "saved_wireguard_configs_list"
                                        val existingListJson = prefs.getString(configListKey, "[]")
                                        val configList = org.json.JSONArray(existingListJson)
                                        
                                        // Проверяем, есть ли уже такой конфиг
                                        var configExists = false
                                        for (i in 0 until configList.length()) {
                                            val item = configList.getJSONObject(i)
                                            if (item.getString("config") == configToSave) {
                                                configExists = true
                                                break
                                            }
                                        }
                                        
                                        // Если конфиг еще не существует, добавляем его
                                        if (!configExists) {
                                            val configObject = org.json.JSONObject()
                                            configObject.put("config", configToSave)
                                            configObject.put("name", "WireGuard конфиг")
                                            configObject.put("addedAt", System.currentTimeMillis())
                                            configList.put(configObject)
                                            Log.d("VpnConfigModal", "WireGuard: Добавлен новый конфиг в список (всего: ${configList.length()})")
                                        } else {
                                            Log.d("VpnConfigModal", "WireGuard: Конфиг уже существует в списке")
                                        }
                                        
                                        // Сохраняем обновленный список
                                        prefs.edit()
                                            .putString(configListKey, configList.toString())
                                            .putString("saved_wireguard_config", configToSave)
                                            .putString("active_config_type", "wireguard")
                                            .commit()
                                        
                                        // Обновляем локальные переменные
                                        savedWireGuardConfig = prefs.getString("saved_wireguard_config", null)
                                        
                                        // Вызываем callback для сохранения в основное приложение
                                        onSaveWireGuardConfig(configToSave)
                                        
                                        isSaving = false
                                        onShowConfigNotification("WireGuard конфиг сохранен! Проверяем подписку...")
                                        wireGuardInput = ""
                                        // Принудительно обновляем список конфигов
                                        configsListVersion++
                                        loadConfigs()
                                        Log.d("VpnConfigModal", "WireGuard сохранен: обновлен список конфигов, всего: ${savedConfigs.size}")
                                        
                                        // Проверяем подписку в фоне (не блокируя UI)
                                        coroutineScope.launch {
                                            onActivateKey(configToSave) { days ->
                                                if (days != null) {
                                                    onShowConfigNotification("Подписка активна: $days дней")
                                                    Log.d("VpnConfigModal", "WireGuard подписка проверена: $days дней")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isSaving = false
                                        errorMessage = "Ошибка сохранения: ${e.message}"
                                        Log.e("VpnConfigModal", "WireGuard: Ошибка при сохранении конфига в список", e)
                                    }
                                },
                                enabled = !isSaving && wireGuardInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5B9BD5)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.Text("Сохранение...", color = Color.White)
                                } else {
                                    androidx.compose.material3.Text("Сохранить", color = Color.White)
                                }
                            }
                        }
                        
                        // Сообщение об ошибке
                        if (errorMessage != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.3f)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), // Более прямоугольная форма
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Text(
                                    successMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF5B9BD5),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Отступ под кнопку Закрыть
                        Spacer(modifier = Modifier.height(80.dp))
                    } // Column
                } // Box (внутренний)
            } // Card
            
            // Кнопка "Закрыть" как в NetworkStatsModal – снаружи контента
            CloseButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        } // Box (модальное окно)
    } // if (isVisible || showVpnConfig)
} // VpnConfigModal