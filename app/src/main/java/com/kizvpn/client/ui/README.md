# Compose UI для KIZ_VPN

## 📁 Структура файлов

```
ui/
├── theme/
│   ├── Color.kt          # Неоновая цветовая палитра
│   └── Theme.kt         # Material3 темная тема
├── screens/
│   ├── HomeScreen.kt    # Главный экран с кнопкой Connect
│   ├── ServersScreen.kt # Список серверов
│   └── SettingsScreen.kt # Настройки
├── navigation/
│   └── NavGraph.kt      # Навигация между экранами
├── models/
│   └── UiModels.kt      # Модели данных
├── viewmodel/
│   └── VpnViewModel.kt  # ViewModel для управления VPN
└── MainActivityCompose.kt # Activity для Compose UI
```

## 🚀 Быстрый старт

### 1. Обновите MainActivity

Откройте `MainActivity.kt` и замените на Compose версию:

```kotlin
package com.kizvpn.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kizvpn.client.ui.navigation.AppNavHost
import com.kizvpn.client.ui.theme.KizVpnTheme
import com.kizvpn.client.ui.theme.BackgroundDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KizVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
```

### 2. Синхронизируйте Gradle

В Android Studio нажмите "Sync Now" когда появится запрос.

### 3. Запустите приложение

Приложение должно отобразить новый Compose UI с темной неоновой темой!

## 🎨 Особенности дизайна

- **Темная неоновая тема** - фиолетовый и бирюзовый акценты
- **Большая круглая кнопка Connect** с анимацией
- **Список серверов** с индикаторами ping и нагрузки
- **Экран настроек** с переключателями

## 🔧 Интеграция с VPN

Для подключения реального VPN функционала:

1. Добавьте `VpnViewModel` в `HomeScreen`
2. Подключите к существующему `KizVpnService`
3. Используйте BroadcastReceiver для обновления статуса

Подробнее смотрите в `COMPOSE_UI_INTEGRATION.md`.

