package com.kizvpn.client.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.kizvpn.client.R
import com.kizvpn.client.util.localizedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class для информации о приложении
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

/**
 * Модальное окно маршрутизации приложений
 */
@Composable
fun RoutingModal(
    showRouting: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val zenterFontFamily = getJuraFontFamily()
    
    // Создаем состояние анимации для модального окна
    val animationState = remember { MenuItemAnimationState() }
    
    // Управление видимостью overlay
    var isVisible by remember { mutableStateOf(false) }
    
    // Анимация фона
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showRouting) 0.7f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "routing_background_alpha"
    )
    
    // Получаем список приложений
    var appsList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    
    // Загружаем список приложений при показе модального окна
    LaunchedEffect(showRouting) {
        if (showRouting) {
            val apps = mutableListOf<AppInfo>()
            val pm = context.packageManager
            
            try {
                // Используем getInstalledApplications для получения всех установленных приложений
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                installedApps.forEach { appInfo ->
                    try {
                        val packageName = appInfo.packageName
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        
                        // Показываем все приложения с непустым названием
                        if (appName.isNotBlank() && appName != packageName) {
                            val icon = try {
                                pm.getApplicationIcon(appInfo)
                            } catch (e: Exception) {
                                null
                            }
                            
                            apps.add(AppInfo(
                                packageName = packageName,
                                appName = appName,
                                icon = icon
                            ))
                        }
                    } catch (e: Exception) {
                        // Игнорируем ошибки загрузки отдельного приложения
                    }
                }
            } catch (e: Exception) {
                // Если getInstalledApplications не работает, пробуем через Intent
                try {
                    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                    mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    
                    val seenPackages = mutableSetOf<String>()
                    
                    resolveInfos.forEach { resolveInfo ->
                        try {
                            val packageName = resolveInfo.activityInfo.packageName
                            
                            if (!seenPackages.contains(packageName)) {
                                seenPackages.add(packageName)
                                val appInfo = pm.getApplicationInfo(packageName, 0)
                                val appName = pm.getApplicationLabel(appInfo).toString()
                                
                                if (appName.isNotBlank()) {
                                    val icon = try {
                                        pm.getApplicationIcon(appInfo)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    
                                    apps.add(AppInfo(
                                        packageName = packageName,
                                        appName = appName,
                                        icon = icon
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            // Игнорируем ошибки
                        }
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("RoutingModal", "Error loading apps", e2)
                }
            }
            
            // Сортируем по имени
            appsList = apps.sortedBy { it.appName }
        }
    }
    
    // Поиск по приложениям
    var searchText by remember { mutableStateOf("") }
    val filteredAppsList = remember(appsList, searchText) {
        if (searchText.isBlank()) {
            appsList
        } else {
            val searchLower = searchText.lowercase()
            appsList.filter { app ->
                app.appName.lowercase().contains(searchLower) ||
                app.packageName.lowercase().contains(searchLower)
            }
        }
    }
    
    // Загружаем выбранные приложения из SharedPreferences
    val prefs = remember { context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE) }
    val selectedAppsJson = remember { prefs.getString("routing_selected_apps", "[]") ?: "[]" }
    var selectedAppsSet by remember {
        val set = mutableSetOf<String>()
        try {
            val jsonArray = JSONArray(selectedAppsJson)
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            // Если ошибка парсинга, используем пустой список
        }
        mutableStateOf(set)
    }
    
    // Функция для сохранения выбранных приложений
    fun saveSelectedApps() {
        val jsonArray = JSONArray()
        selectedAppsSet.forEach { packageName: String ->
            jsonArray.put(packageName)
        }
        prefs.edit()
            .putString("routing_selected_apps", jsonArray.toString())
            .apply()
    }
    
    // Загружаем состояние переключателя "routing_enabled"
    var routingEnabled by remember { 
        mutableStateOf(prefs.getBoolean("routing_enabled", false)) 
    }
    
    // Синхронизируем состояние всех приложений с общим переключателем
    var isUpdatingFromToggle by remember { mutableStateOf(false) }
    
    LaunchedEffect(routingEnabled) {
        if (isUpdatingFromToggle && appsList.isNotEmpty()) {
            isUpdatingFromToggle = false
            if (routingEnabled) {
                // Если включено - выбираем все приложения
                val allPackageNames = appsList.map { it.packageName }.toSet()
                selectedAppsSet = allPackageNames.toMutableSet()
                saveSelectedApps()
            } else {
                // Если выключено - очищаем список
                selectedAppsSet = mutableSetOf()
                saveSelectedApps()
            }
        }
    }
    
    // Управление анимациями
    LaunchedEffect(showRouting) {
        if (showRouting) {
            isVisible = true
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
        } else {
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
    
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    
    // Показываем overlay только когда нужно
    if (isVisible || showRouting) {
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
        
        // Модальное окно маршрутизации - поверх всех элементов
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Заголовок с переключателем
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Название "Маршрутизация" слева
                            Text(
                                text = localizedString(R.string.routing),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = zenterFontFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF5B9BD5),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Переключатель справа
                            Box(
                                modifier = Modifier
                                    .height(43.dp)
                                    .width(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Фоновое изображение
                                Image(
                                    painter = painterResource(id = R.drawable.not_all),
                                    contentDescription = "Routing toggle",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(43.dp)
                                        .padding(horizontal = 4.dp)
                                        .zIndex(0f)
                                )
                                
                                // Оранжевая подсветка для выбранного состояния
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .zIndex(1f)
                                ) {
                                    // Подсветка левой части (OFF)
                                    if (!routingEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.5f)
                                                .fillMaxHeight()
                                                .background(
                                                    Color(0xFFFFA500).copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                                )
                                        )
                                    }
                                    
                                    // Подсветка правой части (ON)
                                    if (routingEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.5f)
                                                .fillMaxHeight()
                                                .align(Alignment.CenterEnd)
                                                .background(
                                                    Color(0xFFFFA500).copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                                )
                                        )
                                    }
                                }
                                
                                // Кликабельные области (поверх всего)
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(2f)
                                ) {
                                    // Левая часть (OFF)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                isUpdatingFromToggle = true
                                                routingEnabled = false
                                                prefs.edit().putBoolean("routing_enabled", false).apply()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                    )
                                    
                                    // Правая часть (ON)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                isUpdatingFromToggle = true
                                                routingEnabled = true
                                                prefs.edit().putBoolean("routing_enabled", true).apply()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                    )
                                }
                            }
                        }
                        
                        // Поле поиска
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = context.getString(R.string.search_apps),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = zenterFontFamily
                                    ),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = zenterFontFamily,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF5B9BD5),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        
                        // Список приложений
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = filteredAppsList,
                                key = { it.packageName }
                            ) { app ->
                                AppRoutingItem(
                                    app = app,
                                    isSelected = selectedAppsSet.contains(app.packageName),
                                    onToggle = {
                                        val newSet = selectedAppsSet.toMutableSet()
                                        if (newSet.contains(app.packageName)) {
                                            newSet.remove(app.packageName)
                                        } else {
                                            newSet.add(app.packageName)
                                        }
                                        selectedAppsSet = newSet
                                        
                                        // Обновляем общий переключатель: включен, если выбраны все приложения
                                        val allSelected = newSet.size == appsList.size && appsList.isNotEmpty()
                                        if (routingEnabled != allSelected) {
                                            routingEnabled = allSelected
                                            prefs.edit().putBoolean("routing_enabled", allSelected).apply()
                                        }
                                        
                                        saveSelectedApps()
                                    },
                                    zenterFontFamily = zenterFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Кнопка "Закрыть" — вне модального окна (как в AboutModal)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10000f),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (showRouting) {
                CloseButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

/**
 * Элемент списка приложения с переключателем
 */
@Composable
fun AppRoutingItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    zenterFontFamily: FontFamily
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
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
            // Иконка и название приложения
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Иконка приложения
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.icon != null) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    setImageDrawable(app.icon)
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        // Fallback: первая буква, если иконка недоступна
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color(0xFF5B9BD5).copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.appName.take(1).uppercase(),
                                color = Color(0xFF5B9BD5),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Название приложения
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = zenterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
            
            // Переключатель ON/OFF (используем SettingsSwitchCard без описания)
            Box(
                modifier = Modifier
                    .height(43.dp)
                    .width(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Фоновое изображение
                Image(
                    painter = painterResource(id = R.drawable.off_on),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(43.dp)
                        .padding(horizontal = 4.dp)
                        .zIndex(0f)
                )
                
                // Оранжевая подсветка для выбранного состояния
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .zIndex(1f)
                ) {
                    // Подсветка левой части (OFF)
                    if (!isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFFFFA500).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                )
                        )
                    }
                    
                    // Подсветка правой части (ON)
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                                .background(
                                    Color(0xFFFFA500).copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                )
                        )
                    }
                }
                
                // Кликабельные области (поверх всего)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                ) {
                    // Левая часть (OFF)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onToggle() }
                    )
                    
                    // Правая часть (ON)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onToggle() }
                    )
                }
            }
        }
    }
}

