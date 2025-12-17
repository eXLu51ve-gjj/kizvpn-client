# Исправление проблемы 16 KB Alignment

## ⚠️ Предупреждение

Android Studio показывает предупреждение:
```
APK app-debug.apk is not compatible with 16 KB devices.
Some libraries have LOAD segments not aligned at 16 KB boundaries:
• lib/arm64-v8a/libxivpn.so
```

## 📅 Дедлайн

**1 ноября 2025** - Google Play начнет требовать поддержку 16 KB для всех новых приложений и обновлений, нацеленных на Android 15+.

## 🔧 Решение

Библиотеку `libxivpn.so` нужно пересобрать с флагом выравнивания 16 KB.

### Вариант 1: Пересборка библиотеки (Рекомендуется)

1. **Клонируйте репозиторий libxivpn:**
   ```bash
   cd E:\CursorAi_project
   git clone https://github.com/Exclude0122/libxivpn
   cd libxivpn
   ```

2. **Установите Android NDK:**
   - Откройте Android Studio
   - File → Settings → Appearance & Behavior → System Settings → Android SDK
   - Вкладка "SDK Tools"
   - Установите "NDK (Side by side)" версии r28 или выше

3. **Обновите скрипты сборки:**
   
   Найдите файлы сборки (CMakeLists.txt, build.gradle, или скрипты сборки) и добавьте флаг линковщика:
   
   ```cmake
   # Для CMake
   set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=0x4000")
   ```
   
   Или для Android.mk:
   ```makefile
   LOCAL_LDFLAGS += -Wl,-z,max-page-size=0x4000
   ```

4. **Пересоберите библиотеку:**
   ```bash
   # Следуйте инструкциям в репозитории libxivpn
   # Обычно это что-то вроде:
   ./build.sh
   # или
   ndk-build
   ```

5. **Замените библиотеки в проекте:**
   ```bash
   # Скопируйте пересобранные .so файлы
   cp libxivpn_arm64.so ../vpn_client_kiz_public/app/src/main/jniLibs/arm64-v8a/libxivpn.so
   cp libxivpn_x86_64.so ../vpn_client_kiz_public/app/src/main/jniLibs/x86_64/libxivpn.so
   ```

### Вариант 2: Обратиться к автору библиотеки

1. Откройте issue в репозитории: https://github.com/Exclude0122/libxivpn
2. Попросите автора добавить поддержку 16 KB alignment
3. После обновления библиотеки - замените файлы в проекте

### Вариант 3: Временно игнорировать (Только до ноября 2025)

Если приложение не планируется публиковать в Play Store в ближайшее время:

1. Закройте предупреждение в Android Studio
2. Отметьте в календаре вернуться к этой проблеме перед публикацией
3. Продолжайте разработку - приложение работает корректно

## ✅ Проверка после исправления

1. Соберите release APK:
   ```bash
   ./gradlew assembleRelease
   ```

2. Откройте APK Analyzer:
   - Build → Analyze APK...
   - Выберите `app-release.apk`
   - Проверьте раздел `lib`
   - Предупреждения о 16 KB alignment должны исчезнуть

## 📚 Полезные ссылки

- [Android 16 KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)
- [Google Play Requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [libxivpn Repository](https://github.com/Exclude0122/libxivpn)

## ⚠️ Важно

**Это нужно исправить перед публикацией в Google Play Store**, если планируется публикация после 1 ноября 2025 или если приложение нацелено на Android 15+.

