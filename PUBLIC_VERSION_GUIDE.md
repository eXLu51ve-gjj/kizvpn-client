# 📢 Инструкция по созданию публичной версии

## 🎯 Цель
Подготовить версию проекта для публичного GitHub репозитория с заменой всех чувствительных данных на placeholder'ы.

---

## 📋 СПИСОК ФАЙЛОВ ДЛЯ ИЗМЕНЕНИЯ

### ✅ Файлы с чувствительными данными (ОБЯЗАТЕЛЬНО изменить):

#### 1. **MainActivity.kt**
**Путь:** `app/src/main/java/com/kizvpn/client/MainActivity.kt`

**Строки 68-71:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
private val apiClient = VpnApiClient(
    baseUrl = "http://95.46.177.19:8081",  // Внешний IP VPN сервера
    subscriptionPort = 2096,
    telegramBotUrl = null
)

// ИЗМЕНИТЬ НА (публичная):
private val apiClient = VpnApiClient(
    baseUrl = "http://YOUR_SERVER_IP:YOUR_API_PORT",  // Replace with your server IP and port
    subscriptionPort = YOUR_SUBSCRIPTION_PORT,          // Replace with your subscription port
    telegramBotUrl = null  // Optional: URL of your Telegram Bot API
)
```

---

#### 2. **VpnApiClient.kt**
**Путь:** `app/src/main/java/com/kizvpn/client/api/VpnApiClient.kt`

**Строки 19-21:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
class VpnApiClient(
    private val baseUrl: String = "http://kizvpn.ru:8081",
    private val subscriptionPort: Int = 2096,
    private val telegramBotUrl: String? = null
)

// ИЗМЕНИТЬ НА (публичная):
class VpnApiClient(
    private val baseUrl: String = "http://YOUR_SERVER_IP:YOUR_API_PORT",
    private val subscriptionPort: Int = YOUR_SUBSCRIPTION_PORT,
    private val telegramBotUrl: String? = null
)
```

**Строки 547-549** (внутри метода):
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
val urls = listOf(
    "http://10.10.10.110:$subscriptionPort/sub/$uuid",
    "http://10.10.10.110:$subscriptionPort/json/$uuid",
    "http://10.10.10.110:$subscriptionPort/api/subscription?uuid=$uuid"
)

// ИЗМЕНИТЬ НА (публичная):
val urls = listOf(
    "http://LOCAL_SERVER_IP:$subscriptionPort/sub/$uuid",
    "http://LOCAL_SERVER_IP:$subscriptionPort/json/$uuid",
    "http://LOCAL_SERVER_IP:$subscriptionPort/api/subscription?uuid=$uuid"
)
```

**Строки 281-284** (комментарии):
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
// Примечание: Если сервер 10.10.10.110 находится в локальной сети,
// то доступ к нему обычно возможен и без VPN.
// IP 10.10.10.110 - это локальный IP, обычно доступен без VPN в локальной сети.

// ИЗМЕНИТЬ НА (публичная):
// Note: If the server is in local network,
// access to it is usually possible without VPN.
// Local IP is typically accessible without VPN in local network.
```

---

#### 3. **ApiEndpointTester.kt**
**Путь:** `app/src/main/java/com/kizvpn/client/api/ApiEndpointTester.kt`

**Строка 15:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
class ApiEndpointTester(private val baseUrl: String = "http://10.10.10.110:8080") {

// ИЗМЕНИТЬ НА (публичная):
class ApiEndpointTester(private val baseUrl: String = "http://LOCAL_SERVER_IP:LOCAL_PORT") {
```

---

#### 4. **AndroidManifest.xml**
**Путь:** `app/src/main/AndroidManifest.xml`

**Строки 65-68:**
```xml
<!-- ТЕКУЩАЯ ВЕРСИЯ (приватная): -->
<data
    android:scheme="https"
    android:host="kizvpn.app"
    android:pathPrefix="/connect" />

<!-- ИЗМЕНИТЬ НА (публичная): -->
<data
    android:scheme="https"
    android:host="your-domain.com"
    android:pathPrefix="/connect" />
```

**Причина:** Безопасность - чтобы кто-то не купил домен и не использовал для фишинга

---

#### 5. **MainActivity.kt** (deep link проверка)
**Путь:** `app/src/main/java/com/kizvpn/client/MainActivity.kt`

**Строка 272:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
data.scheme == "https" && data.host == "kizvpn.app" -> {
    // https://kizvpn.app/connect?vless=...

// ИЗМЕНИТЬ НА (публичная):
data.scheme == "https" && data.host == "your-domain.com" -> {
    // https://your-domain.com/connect?vless=...
```

---

#### 6. **BottomMenuButton.kt** (контакты)
**Путь:** `app/src/main/java/com/kizvpn/client/ui/components/BottomMenuButton.kt`

**Строка 456:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
putExtra(Intent.EXTRA_EMAIL, arrayOf("kiz_vpn@internet.ru"))

// ИЗМЕНИТЬ НА (публичная):
putExtra(Intent.EXTRA_EMAIL, arrayOf("your-email@example.com"))
```

**Строки 467, 472:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
Uri.parse("https://t.me/KIZ_VPN_helper")

// ИЗМЕНИТЬ НА (публичная):
Uri.parse("https://t.me/your_telegram")
```

**Строка 739:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
text = "Email: kiz_vpn@internet.ru"

// ИЗМЕНИТЬ НА (публичная):
text = "Email: your-email@example.com"
```

**Строка 760:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ (приватная):
text = "Telegram: @KIZ_VPN_helper"

// ИЗМЕНИТЬ НА (публичная):
text = "Telegram: @your_telegram"
```

---

### ✅ Комментарии и документация (РЕКОМЕНДУЕТСЯ изменить):

#### 7. **KizVpnService.java**
**Путь:** `app/src/main/java/com/kizvpn/client/vpn/KizVpnService.java`

**Строки 68-69:**
```java
// ТЕКУЩАЯ ВЕРСИЯ:
/**
 * Упрощенный VPN сервис на основе XiVPNService
 * Использует libxivpn для маршрутизации через TUN интерфейс
 */

// ИЗМЕНИТЬ НА (более нейтрально):
/**
 * VPN Service for routing traffic through TUN interface
 * Based on VPN core architecture with native library integration
 */
```

**Логи** (можно оставить как есть, но при желании изменить):
```java
// Примеры (строки 241, 470, 527, 603 и т.д.):
"starting libxivpn" → "starting VPN core"
"libxivpn path" → "native library path"
"libxivpn.so not found" → "VPN library not found"
Log.d("libxivpn", line) → Log.d("VPNCore", line)
```

---

#### 8. **SocketProtect.kt**
**Путь:** `app/src/main/java/com/kizvpn/client/vpn/SocketProtect.kt`

**Строка 5:**
```kotlin
// ТЕКУЩАЯ ВЕРСИЯ:
/**
 * Импортирован из XiVPN
 */

// ИЗМЕНИТЬ НА:
/**
 * Socket protection interface for VPN Service
 */
```

---

### ✅ Файлы для удаления (НЕ ВКЛЮЧАТЬ в публичный репозиторий):

```
❌ PRIVATE_RESTORE_GUIDE.md          ← ПРИВАТНАЯ документация
❌ .idea/                              ← Настройки IDE
❌ local.properties                    ← Локальные настройки
❌ *.keystore                          ← Ключи подписи
❌ app/google-services.json            ← Firebase (если есть)
❌ Все .md файлы из корня КРОМЕ:
   ✅ README.md (создать новый)
   ✅ FEATURES_LIST.md
   ✅ CREDITS.md
   ✅ LICENSE
```

---

## 📝 СОЗДАТЬ НОВЫЕ ФАЙЛЫ

### 1. **README.md** (для публичного репозитория)

```markdown
# 🚀 KIZ VPN - Android VPN Client

Modern Android VPN client with support for VLESS and WireGuard protocols.

## ✨ Features

- 🔒 VLESS & WireGuard support
- 📱 Quick Settings Tile
- 📊 Real-time traffic statistics
- 🎨 Modern dark UI
- 📅 Subscription management
- 📷 QR code scanner
- 🔄 Auto-connect
- 📜 Connection history

## 🛠️ Technologies

- Kotlin + Jetpack Compose
- VPN core based on XiVPN (GPL-3.0)
- Material3 design
- MVVM architecture

## 📦 Setup

### Requirements:
- Android Studio Arctic Fox or later
- JDK 11 or higher
- Android SDK (min API 26)

### Configuration:

1. Clone the repository
2. Open in Android Studio
3. Configure your server settings in:
   - `MainActivity.kt` - set your server IP and ports
   - `VpnApiClient.kt` - set API endpoints

```kotlin
private val apiClient = VpnApiClient(
    baseUrl = "http://YOUR_SERVER_IP:YOUR_API_PORT",
    subscriptionPort = YOUR_SUBSCRIPTION_PORT
)
```

4. Build: `Build → Build Bundle(s) / APK(s) → Build APK(s)`

## 📖 Documentation

- [Features List](FEATURES_LIST.md) - Complete list of features
- [Credits](CREDITS.md) - Acknowledgments and licensing

## 📜 License

GPL-3.0 (due to XiVPN core usage)

## 🙏 Credits

VPN core based on [XiVPN](https://github.com/Exclude0122/XiVPN) by Exclude0122

## 📧 Contact

[Your contact info]
```

---

### 2. **LICENSE** (файл лицензии)

Скопировать текст GPL-3.0 лицензии:
```
Создать файл LICENSE с полным текстом GPL-3.0
Можно скопировать с: https://www.gnu.org/licenses/gpl-3.0.txt
```

---

### 3. **.gitignore** (обязательно!)

```gitignore
# Built application files
*.apk
*.aar
*.ap_
*.aab

# Files for the ART/Dalvik VM
*.dex

# Java class files
*.class

# Generated files
bin/
gen/
out/
build/
*/build/

# Gradle files
.gradle/
gradle-app.setting
!gradle-wrapper.jar
!gradle-wrapper.properties

# Local configuration file
local.properties

# IntelliJ
*.iml
.idea/
*.ipr
*.iws
.idea_modules/

# Keystore files
*.jks
*.keystore

# External native build folder
.externalNativeBuild
.cxx/

# Google Services (e.g. APIs or Firebase)
google-services.json

# Android Profiling
*.hprof

# Sensitive/Private files
PRIVATE_RESTORE_GUIDE.md
.env
*.key
*.pem
```

---

## 🔄 ПОРЯДОК СОЗДАНИЯ ПУБЛИЧНОЙ ВЕРСИИ

### Вариант A: Ручное копирование (безопаснее)

```bash
# 1. Создать новую папку для публичной версии
mkdir E:\CursorAi_project\vpn_client_kiz_public
cd E:\CursorAi_project\vpn_client_kiz_public

# 2. Скопировать проект (НЕ включая .git)
# Скопировать вручную все папки КРОМЕ:
#   - .idea/
#   - build/
#   - app/build/
#   - .gradle/
#   - local.properties

# 3. Изменить чувствительные данные вручную
#    (см. список выше)

# 4. Создать новые файлы:
#    - README.md
#    - LICENSE
#    - .gitignore

# 5. Удалить приватные файлы:
#    - PRIVATE_RESTORE_GUIDE.md
#    - Все ненужные .md из корня

# 6. Инициализировать Git
git init
git add .
git commit -m "Initial commit: KIZ VPN public version"

# 7. Подключить к GitHub
git remote add origin https://github.com/ваш-username/kiz-vpn-public.git
git push -u origin main
```

---

### Вариант B: Автоматизированный (сложнее, но быстрее)

Можно создать скрипт PowerShell для автоматической замены:

```powershell
# prepare_public.ps1
$source = "E:\CursorAi_project\vpn_client_kiz"
$destination = "E:\CursorAi_project\vpn_client_kiz_public"

# 1. Копировать проект
robocopy $source $destination /E /XD .idea build .gradle

# 2. Заменить чувствительные данные
# (требуется PowerShell скрипт для замены в файлах)

# 3. Удалить приватные файлы
Remove-Item "$destination\PRIVATE_RESTORE_GUIDE.md"
```

---

## ✅ ЧЕКЛИСТ ПЕРЕД ПУБЛИКАЦИЕЙ

Проверить перед push в GitHub:

- [ ] Все IP адреса заменены на placeholder'ы
- [ ] Все порты заменены
- [ ] Нет UUID ключей
- [ ] Нет токенов ботов
- [ ] Нет паролей
- [ ] PRIVATE_RESTORE_GUIDE.md удален
- [ ] README.md создан
- [ ] LICENSE создан
- [ ] .gitignore создан
- [ ] FEATURES_LIST.md на месте
- [ ] CREDITS.md на месте
- [ ] Проект собирается без ошибок
- [ ] Все комментарии проверены

---

## 🔍 БЫСТРАЯ ПРОВЕРКА

### Поиск IP адресов:
```bash
# В PowerShell (в корне проекта):
Get-ChildItem -Recurse -Include *.kt,*.java | 
  Select-String "\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}" |
  Where-Object { $_.Line -notmatch "0\.0\.0\.0|8\.8\.8\.8|10\.0\.0\." }
```

### Поиск портов:
```bash
Get-ChildItem -Recurse -Include *.kt,*.java |
  Select-String ":2096|:8081|:2053"
```

---

## 📞 ЕСЛИ ЧТО-ТО ПОШЛО НЕ ТАК

1. **Случайно запушили приватные данные:**
   ```bash
   # НЕ ПАНИКОВАТЬ!
   # 1. Удалить репозиторий на GitHub
   # 2. Создать заново
   # 3. Перепроверить все файлы
   # 4. Заново запушить
   ```

2. **Проект не собирается после изменений:**
   ```
   Проверить:
   - Синтаксис Kotlin/Java
   - Не удалены ли нужные строки
   - Gradle sync прошел
   ```

---

**Дата создания:** 11.11.2025  
**Статус:** Готово к использованию

