# 🔍 Как проверить логи для диагностики

## Через командную строку (ADB):

```bash
# Очистить логи
adb logcat -c

# Запустить приложение и подключиться к VLESS

# Посмотреть ошибки VPN сервиса:
adb logcat | grep KizVpnService

# Посмотреть ошибки MainActivity:
adb logcat | grep MainActivity

# Посмотреть все ошибки:
adb logcat *:E

# Посмотреть ошибки парсинга конфига:
adb logcat | grep ConfigParser
```

## Через Android Studio:

```
1. Подключить устройство
2. Открыть Logcat (внизу экрана)
3. Фильтр: "KizVpnService"
4. Попробовать подключиться к VLESS
5. Посмотреть ошибки (красные строки)
```

## Что искать в логах:

### Для VLESS:
```
"Failed to parse config"
"UUID is required"
"Error building xray config"
"startLibxi() returned false"
"libxivpn exit unexpectedly"
"Error connecting VPN"
```

### Для API (можно игнорировать):
```
"Failed to check subscription" - НОРМАЛЬНО
"Connection refused" - НОРМАЛЬНО (нет сервера)
"UnknownHostException: YOUR_SERVER_IP" - НОРМАЛЬНО
```

