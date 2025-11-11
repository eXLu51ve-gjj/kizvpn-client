# 📦 Инструкция по созданию GitHub Release

## 🎯 Что такое GitHub Release

GitHub Release - это способ опубликовать готовую версию приложения с:
- Версионированием (v1.0.0, v1.1.0 и т.д.)
- Файлами для скачивания (APK, исходники)
- Описанием изменений (Changelog)
- Автоматическими ссылками

---

## 📋 ШАГ ЗА ШАГОМ

### 1️⃣ **Подготовить APK файл**

**Текущее расположение:**
```
C:\Users\[username]\Desktop\KIZ_VPN.apk
Размер: ~161 MB
```

**Рекомендуется переименовать для версионирования:**
```
KIZ_VPN_v1.0.0.apk
```

---

### 2️⃣ **Создать репозиторий на GitHub**

1. Зайти на https://github.com
2. Нажать **New repository**
3. Заполнить:
   - **Repository name:** `kiz-vpn-client`
   - **Description:** Modern Android VPN client with VLESS and WireGuard
   - **Public/Private:** Выбрать Public
   - ✅ Add README (не нужно, у нас уже есть)
   - ✅ Choose license: GPL-3.0
4. Нажать **Create repository**

---

### 3️⃣ **Загрузить код в репозиторий**

#### Вариант A: Через командную строку (рекомендуется)

```bash
# 1. Перейти в папку проекта
cd E:\CursorAi_project\vpn_client_kiz_public

# 2. Инициализировать Git
git init

# 3. Добавить все файлы
git add .

# 4. Создать коммит
git commit -m "Initial commit: KIZ VPN v1.0.0"

# 5. Добавить remote репозиторий (заменить на свой URL)
git remote add origin https://github.com/your-username/kiz-vpn-client.git

# 6. Загрузить код
git branch -M main
git push -u origin main
```

#### Вариант B: Через GitHub Desktop

1. Открыть GitHub Desktop
2. File → Add Local Repository
3. Выбрать папку `vpn_client_kiz_public`
4. Commit to main
5. Publish repository

---

### 4️⃣ **Создать Release**

1. **Перейти в репозиторий на GitHub**
   ```
   https://github.com/your-username/kiz-vpn-client
   ```

2. **Нажать на вкладку "Releases"** (справа на странице)

3. **Нажать "Create a new release"**

4. **Заполнить форму:**

   **Tag version:**
   ```
   v1.0.0
   ```
   
   **Release title:**
   ```
   KIZ VPN v1.0.0 - Initial Release
   ```
   
   **Description (Changelog):**
   ```markdown
   ## 🎉 First Release
   
   ### ✨ Features
   - 🔒 VLESS Protocol support (Reality, WebSocket, TLS/XTLS)
   - 🛡️ WireGuard Protocol support
   - ⚡ Quick Settings Tile for quick VPN toggle
   - 📊 Real-time traffic statistics
   - 📅 Subscription management
   - 📷 QR code scanner for easy config import
   - 🔄 Auto-connect on app launch
   - 📜 Connection history
   - 🌓 Beautiful dark theme UI with Jetpack Compose
   
   ### 📱 Requirements
   - Android 8.0+ (API 26)
   - ~161 MB storage space
   
   ### 📥 Installation
   1. Download `KIZ_VPN.apk` below
   2. Enable "Install from Unknown Sources" in Settings
   3. Install the APK
   4. Grant VPN permission when prompted
   
   ### ⚠️ Note
   This is an unsigned debug build. You may see a warning from Google Play Protect.
   
   ### 🐛 Known Issues
   - None yet
   
   ### 📞 Support
   - Email: nml5222600@mail.ru
   - Telegram: @eXLu51ve
   ```

5. **Загрузить APK файл:**
   - Прокрутить вниз до секции **"Attach binaries"**
   - Нажать **"Choose files"** или перетащить файл
   - Выбрать `KIZ_VPN.apk` с рабочего стола
   - Дождаться загрузки (может занять время, файл ~161MB)

6. **Опции:**
   - ✅ **Set as the latest release** - отметить
   - ✅ **Create a discussion for this release** - по желанию

7. **Нажать "Publish release"** 🎉

---

### 5️⃣ **Проверить ссылки**

После создания Release, проверьте что ссылки работают:

**Ссылка на последний релиз:**
```
https://github.com/your-username/kiz-vpn-client/releases/latest
```

**Прямая ссылка на APK:**
```
https://github.com/your-username/kiz-vpn-client/releases/latest/download/KIZ_VPN.apk
```

**Ссылка на конкретную версию:**
```
https://github.com/your-username/kiz-vpn-client/releases/tag/v1.0.0
```

---

### 6️⃣ **Обновить README**

После создания релиза, обновите ссылки в README.md:

```markdown
# Заменить:
https://github.com/your-username/kiz-vpn-client

# На реальный URL:
https://github.com/ваш-настоящий-username/kiz-vpn-client
```

---

## 🔄 **Создание новых версий (в будущем)**

### Когда выпускать новую версию:

**Patch (1.0.0 → 1.0.1):**
- Исправление багов
- Мелкие улучшения

**Minor (1.0.0 → 1.1.0):**
- Новые функции
- Улучшения UI
- Не ломающие изменения

**Major (1.0.0 → 2.0.0):**
- Крупные изменения
- Переработка архитектуры
- Ломающие изменения

### Процесс создания новой версии:

1. **Обновить код**
2. **Собрать новый APK**
3. **Обновить версию в `build.gradle.kts`:**
   ```kotlin
   versionCode = 2
   versionName = "1.0.1"
   ```
4. **Commit и push изменения**
5. **Создать новый Release** (повторить шаги 4-6)
6. **В changelog указать что изменилось**

---

## 📊 **Статистика загрузок**

После создания Release, GitHub автоматически покажет:
- Количество скачиваний APK
- Количество просмотров страницы релиза
- Популярные версии

Посмотреть статистику:
```
https://github.com/your-username/kiz-vpn-client/releases
```

---

## 💡 **Советы**

### Хорошие практики:

1. **Используйте семантическое версионирование** (SemVer):
   ```
   v1.0.0, v1.0.1, v1.1.0, v2.0.0
   ```

2. **Пишите подробный Changelog:**
   - Что добавлено (✨ Added)
   - Что изменено (🔄 Changed)
   - Что исправлено (🐛 Fixed)
   - Что удалено (❌ Removed)

3. **Загружайте несколько файлов:**
   - APK для установки
   - Source code (автоматически)
   - Changelog.md (опционально)

4. **Создавайте Pre-release для бета версий:**
   - Отметить ✅ "Set as a pre-release"
   - Использовать версии типа `v1.0.0-beta.1`

---

## 🔐 **Подписание APK (опционально)**

Для production версии рекомендуется подписать APK:

1. **Создать keystore:**
   ```bash
   keytool -genkey -v -keystore kiz-vpn.keystore \
     -alias kiz_vpn -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Подписать APK:**
   ```bash
   jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
     -keystore kiz-vpn.keystore app-release-unsigned.apk kiz_vpn
   ```

3. **Оптимизировать APK:**
   ```bash
   zipalign -v 4 app-release-unsigned.apk KIZ_VPN_v1.0.0_signed.apk
   ```

**Важно:** Храните keystore в безопасном месте! Без него вы не сможете обновлять приложение.

---

## ❓ **Частые вопросы**

### Q: Можно ли удалить старые релизы?
A: Да, но не рекомендуется. Лучше пометить как deprecated.

### Q: Максимальный размер файла?
A: 2 GB для GitHub Release

### Q: Можно ли редактировать релиз после публикации?
A: Да, можно изменить описание и добавить/удалить файлы.

### Q: Что делать если APK не загружается?
A: Проверьте размер файла и интернет соединение. Попробуйте через браузер.

---

## 📞 **Помощь**

Если возникли проблемы:
- 📧 Email: nml5222600@mail.ru
- 💬 Telegram: @eXLu51ve
- 📖 GitHub Docs: https://docs.github.com/en/repositories/releasing-projects-on-github

---

**Удачи с первым релизом!** 🚀

