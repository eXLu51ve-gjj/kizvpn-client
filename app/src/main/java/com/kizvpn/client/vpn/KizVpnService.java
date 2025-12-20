package com.kizvpn.client.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kizvpn.client.MainActivity;
import com.kizvpn.client.R;
import com.kizvpn.client.config.ConfigParser;
import com.kizvpn.client.xrayconfig.Config;
import com.kizvpn.client.xrayconfig.Outbound;
import com.kizvpn.client.xrayconfig.VlessSettings;
import com.kizvpn.client.xrayconfig.VlessServerSettings;
import com.kizvpn.client.xrayconfig.VlessUser;
import com.kizvpn.client.xrayconfig.StreamSettings;
import com.kizvpn.client.xrayconfig.TLSSettings;
import com.kizvpn.client.xrayconfig.WireguardSettings;
import com.kizvpn.client.xrayconfig.WireguardPeer;
import com.kizvpn.client.xrayconfig.Routing;
import com.kizvpn.client.xrayconfig.RoutingRule;
import com.kizvpn.client.xrayconfig.XrayDNS;
import com.kizvpn.client.xrayconfig.DNSServer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Упрощенный VPN сервис на основе XiVPNService
 * Использует libxivpn для маршрутизации через TUN интерфейс
 */
public class KizVpnService extends VpnService implements SocketProtect {
    private final IBinder binder = new KizVpnBinder();
    private final String TAG = "KizVpnService";
    private final Set<VPNStateListener> listeners = new HashSet<>();
    private final Object vpnStateLock = new Object();
    
    private Process libxivpnProcess = null;
    private Thread teeThread = null;
    private Thread ipcThread = null;
    private OutputStream ipcWriter = null;
    private ParcelFileDescriptor fileDescriptor;
    private volatile VPNState vpnState = VPNState.DISCONNECTED;
    private Command commandBuffer = Command.NONE;
    private boolean mustLibxiStop = false;
    
    // Текущий конфиг (VLESS URL)
    private String currentConfigUrl = null;
    private ConfigParser.ParsedConfig parsedConfig = null;
    
    // Информация о подписке для отображения в уведомлении
    private int subscriptionDays = 0;
    private int subscriptionHours = 0;
    private long subscriptionUsedTraffic = -1;
    private long subscriptionTotalTraffic = -1;
    
    // Информация о пинге и скорости для уведомления
    private int currentPing = -1; // -1 означает нет данных
    private long currentDownloadSpeed = 0; // в байтах/сек
    private long currentUploadSpeed = 0; // в байтах/сек
    
    private static final String CHANNEL_ID = "kiz_vpn_service";
    private static final int NOTIFICATION_ID = 3; // Изменено для принудительного создания нового уведомления без largeIcon
    
    /**
     * Установить конфиг для VPN
     */
    public void setConfig(String configUrl) {
        synchronized (vpnStateLock) {
            this.currentConfigUrl = configUrl;
            ConfigParser parser = new ConfigParser();
            this.parsedConfig = parser.parseConfig(configUrl);
            if (parsedConfig == null) {
                Log.e(TAG, "Failed to parse config: " + configUrl);
            }
        }
    }
    
    /**
     * Установить информацию о подписке для отображения в уведомлении
     */
    public void setSubscriptionInfo(int days, int hours) {
        synchronized (vpnStateLock) {
            this.subscriptionDays = days;
            this.subscriptionHours = hours;
            // Обновляем уведомление, если VPN подключен
            if (vpnState == VPNState.CONNECTED) {
                updateNotification();
            }
        }
    }
    
    /**
     * Установить информацию о пинге для отображения в уведомлении
     */
    public void setPingInfo(int ping) {
        synchronized (vpnStateLock) {
            Log.d(TAG, "setPingInfo: " + ping + "ms");
            this.currentPing = ping;
            // Обновляем уведомление, если VPN подключен
            if (vpnState == VPNState.CONNECTED) {
                updateNotification();
            }
        }
    }
    
    /**
     * Установить информацию о скорости для отображения в уведомлении
     */
    public void setSpeedInfo(long downloadSpeed, long uploadSpeed) {
        synchronized (vpnStateLock) {
            Log.d(TAG, "setSpeedInfo: download=" + downloadSpeed + "B/s, upload=" + uploadSpeed + "B/s");
            this.currentDownloadSpeed = downloadSpeed;
            this.currentUploadSpeed = uploadSpeed;
            // Обновляем уведомление, если VPN подключен
            if (vpnState == VPNState.CONNECTED) {
                updateNotification();
            }
        }
    }
    
    /**
     * Загрузить информацию о подписке из SharedPreferences
     */
    private void loadSubscriptionInfo() {
        android.content.SharedPreferences prefs = getSharedPreferences("KizVpnPrefs", MODE_PRIVATE);
        if (prefs.contains("subscription_days") || prefs.contains("subscription_hours")) {
            this.subscriptionDays = prefs.getInt("subscription_days", 0);
            this.subscriptionHours = prefs.getInt("subscription_hours", 0);
            Log.i(TAG, "Loaded subscription info: " + subscriptionDays + " days, " + subscriptionHours + " hours");
        } else {
            Log.i(TAG, "No subscription info found in preferences");
        }
        
        // Загружаем данные о трафике
        if (prefs.contains("subscription_used_traffic")) {
            this.subscriptionUsedTraffic = prefs.getLong("subscription_used_traffic", -1);
        }
        if (prefs.contains("subscription_total_traffic")) {
            this.subscriptionTotalTraffic = prefs.getLong("subscription_total_traffic", -1);
        }
    }
    
    /**
     * Установить состояние
     */
    private void setStateRaw(VPNState newState) {
        Log.w(TAG, "state: " + newState.name());
        
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (listeners) {
                for (VPNStateListener listener : listeners) {
                    listener.onStateChanged(newState);
                }
            }
        });
        
        vpnState = newState;
        
        // Сохраняем статус в SharedPreferences для Quick Settings Tile
        getSharedPreferences("KizVpnPrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("is_connected", newState == VPNState.CONNECTED)
            .apply();
        
        // Отправляем broadcast для обновления Quick Settings Tile
        Intent tileIntent = new Intent("com.kizvpn.client.VPN_STATE_CHANGED");
        tileIntent.putExtra("is_connected", newState == VPNState.CONNECTED);
        sendBroadcast(tileIntent);
    }
    
    private void setState(VPNState newState) {
        synchronized (vpnStateLock) {
            setStateRaw(newState);
        }
    }
    
    /**
     * Отправить сообщение слушателям
     */
    private void sendMessage(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (listeners) {
                for (VPNStateListener listener : listeners) {
                    listener.onMessage(msg);
                }
            }
        });
    }
    
    private void updateCommand(Command command) {
        synchronized (vpnStateLock) {
            commandBuffer = command;
            vpnStateLock.notify();
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "on create");
        
        createNotificationChannel();
        
        // Основной поток обработки команд
        new Thread(() -> {
            Runnable work = () -> {};
            while (true) {
                work.run();
                work = () -> {};
                
                synchronized (vpnStateLock) {
                    while (commandBuffer == Command.NONE && !mustLibxiStop) {
                        try {
                            vpnStateLock.wait();
                        } catch (InterruptedException e) {
                            Log.wtf(TAG, "wait for new command", e);
                        }
                    }
                    
                    if (commandBuffer == Command.CONNECT) {
                        commandBuffer = Command.NONE;
                        
                        if (vpnState != VPNState.DISCONNECTED) {
                            Log.w(TAG, "Cannot connect: VPN state is " + vpnState);
                            continue;
                        }
                        
                        // Проверяем наличие конфига
                        if (parsedConfig == null || currentConfigUrl == null) {
                            Log.e(TAG, "No config set");
                            sendMessage("Error: No config set. Please set config first.");
                            continue;
                        }
                        
                        Log.i(TAG, "Starting VPN connection with config: " + currentConfigUrl.substring(0, Math.min(50, currentConfigUrl.length())) + "...");
                        setStateRaw(VPNState.ESTABLISHING_VPN);
                        work = () -> {
                            Log.i(TAG, "Executing VPN connection work");
                            if (!startVPN()) {
                                Log.e(TAG, "startVPN() returned false");
                                setState(VPNState.DISCONNECTED);
                                return;
                            }
                            
                            Log.i(TAG, "VPN interface created, starting libxivpn");
                            setState(VPNState.STARTING_LIBXI);
                            if (!startLibxi()) {
                                Log.e(TAG, "startLibxi() returned false");
                                stopVPN();
                                setState(VPNState.DISCONNECTED);
                                return;
                            }
                            
                            Log.i(TAG, "VPN connected successfully!");
                            setState(VPNState.CONNECTED);
                        };
                    } else if (commandBuffer == Command.DISCONNECT || mustLibxiStop) {
                        commandBuffer = Command.NONE;
                        mustLibxiStop = false;
                        
                        if (vpnState != VPNState.CONNECTED && vpnState != VPNState.STARTING_LIBXI) continue;
                        
                        setStateRaw(VPNState.STOPPING_LIBXI);
                        work = () -> {
                            stopLibxi();
                            
                            setState(VPNState.STOPPING_VPN);
                            stopVPN();
                            
                            setState(VPNState.DISCONNECTED);
                        };
                    }
                }
            }
        }).start();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Для foreground service всегда используем IMPORTANCE_LOW (минимальный уровень для foreground)
                // IMPORTANCE_NONE нельзя использовать для foreground service - это вызывает краш
                int importance = NotificationManager.IMPORTANCE_LOW;
                
                // Проверяем, существует ли канал
                NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
                if (existingChannel != null) {
                    Log.i(TAG, "Notification channel already exists: " + CHANNEL_ID);
                    // Если канал существует с другой важностью, удаляем и пересоздаем
                    if (existingChannel.getImportance() != importance) {
                        Log.i(TAG, "Channel has different importance (" + existingChannel.getImportance() + " vs " + importance + "), deleting and recreating...");
                        manager.deleteNotificationChannel(CHANNEL_ID);
                    } else {
                        Log.i(TAG, "Channel is already set to importance " + importance + ", using existing channel");
                        return; // Канал уже настроен правильно
                    }
                }
                
                // Создаем новый канал с правильными настройками
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "KIZ VPN Service",
                    importance // Всегда LOW для foreground service
                );
                channel.setDescription("VPN connection status");
                channel.setShowBadge(false); // Не показывать значок
                channel.enableLights(false); // Без световой индикации
                channel.enableVibration(false); // Без вибрации
                channel.setSound(null, null); // Без звука
                
                manager.createNotificationChannel(channel);
                Log.i(TAG, "New notification channel created: " + CHANNEL_ID + " with importance: " + channel.getImportance());
            }
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "on start command");
        
        if (intent == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        if (action != null) {
            if (action.equals("com.kizvpn.client.START")) {
                // Получаем конфиг из intent
                String configUrl = intent.getStringExtra("config");
                if (configUrl != null) {
                    setConfig(configUrl);
                }
                updateCommand(Command.CONNECT);
            } else if (action.equals("com.kizvpn.client.STOP")) {
                updateCommand(Command.DISCONNECT);
            }
        } else {
            // Если нет action, но есть конфиг, устанавливаем его
            String configUrl = intent.getStringExtra("config");
            if (configUrl != null) {
                setConfig(configUrl);
            }
        }
        
        return START_NOT_STICKY;
    }
    
    /**
     * Создать VPN интерфейс
     */
    private boolean startVPN() {
        Log.i(TAG, "=== startVPN() called - creating VPN interface ===");
        
        // Создаем/обновляем канал уведомлений в зависимости от настроек
        createNotificationChannel();
        
        // Загружаем информацию о подписке из SharedPreferences
        loadSubscriptionInfo();
        
        // Создаем уведомление
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Log.i(TAG, "Creating notification with icon resource ID: " + R.drawable.ic_notification_kiz_vpn);
        
        // Сначала удаляем старое уведомление, если оно есть
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.i(TAG, "Old notification cancelled");
        }
        
        // Используем монохромную версию логотипа для smallIcon
        int iconResId = R.drawable.kiz_vpn_mono;
        Log.i(TAG, "Using mono logo icon resource ID: " + iconResId);
        
        // Проверяем настройку уведомлений
        android.content.SharedPreferences prefs = getSharedPreferences("vpn_settings", MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        
        // Формируем компактный текст для уведомления (в одну строку) - объединяем время, трафик, пинг и скорость
        StringBuilder textBuilder = new StringBuilder();
        
        // Добавляем информацию о времени подписки
        if (notificationsEnabled && (subscriptionDays > 0 || subscriptionHours > 0)) {
            if (subscriptionDays > 0) {
                int months = subscriptionDays / 30;
                int remainingDays = subscriptionDays % 30;
                if (months > 0 && remainingDays > 0) {
                    textBuilder.append(String.format("%dм %dд", months, remainingDays));
                } else if (months > 0) {
                    textBuilder.append(String.format("%d мес", months));
                } else {
                    textBuilder.append(String.format("%dд", subscriptionDays));
                    if (subscriptionHours > 0) {
                        textBuilder.append(String.format(" %dч", subscriptionHours));
                    }
                }
            } else {
                textBuilder.append(String.format("%d ч", subscriptionHours));
            }
        }
        
        // Добавляем информацию о трафике
        String trafficText = formatTrafficForNotification();
        if (trafficText != null) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(trafficText);
        }
        
        // Добавляем пинг (при старте пока нет данных)
        if (currentPing > 0) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(currentPing).append("ms");
        }
        
        // Добавляем скорость (при старте пока нет данных)
        String speedText = formatSpeedForNotification();
        if (speedText != null) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(speedText);
        }
        
        // Формируем заголовок и текст
        String contentTitle = "KIZ VPN";
        String contentText;
        if (textBuilder.length() > 0) {
            contentText = textBuilder.toString();
        } else {
            contentText = "Подключено";
        }
        
        // Выбираем приоритет и видимость в зависимости от настроек
        int priority = notificationsEnabled ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_MIN;
        int visibility = notificationsEnabled ? NotificationCompat.VISIBILITY_PUBLIC : NotificationCompat.VISIBILITY_SECRET;
        
        // Вычисляем прогресс трафика для полоски в уведомлении
        int trafficProgressPercent = 0;
        // Показываем прогресс только для ограниченных подписок (не больше 500 GB)
        if (subscriptionUsedTraffic >= 0 && subscriptionTotalTraffic > 0 && subscriptionTotalTraffic <= 500L * 1024 * 1024 * 1024) {
            trafficProgressPercent = (int) ((subscriptionUsedTraffic * 100) / subscriptionTotalTraffic);
            if (trafficProgressPercent > 100) trafficProgressPercent = 100;
        }
        
        // Создаём кастомный layout для уведомления
        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_custom);
        customView.setTextViewText(R.id.notification_text, contentText);
        
        // Формируем дополнительную информацию (пинг и скорость) - при старте пока нет данных
        customView.setViewVisibility(R.id.notification_extra_info, android.view.View.GONE);
        
        // Настраиваем полосу прогресса (только для ограниченных подписок)
        if (subscriptionUsedTraffic >= 0 && subscriptionTotalTraffic > 0 && subscriptionTotalTraffic <= 500L * 1024 * 1024 * 1024) {
            customView.setProgressBar(R.id.notification_progress, 100, trafficProgressPercent, false);
            customView.setViewVisibility(R.id.notification_progress, android.view.View.VISIBLE);
        } else {
            customView.setViewVisibility(R.id.notification_progress, android.view.View.GONE);
        }
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId) // Монохромная иконка для строки состояния
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(priority) // Зависит от настроек
            .setVisibility(visibility) // Зависит от настроек
            .setShowWhen(false) // Убираем время
            .setAutoCancel(false)
            .setCustomContentView(customView) // Кастомный layout для свёрнутого уведомления
            .setStyle(new androidx.core.app.NotificationCompat.DecoratedCustomViewStyle()); // Стиль для кастомного уведомления
        
        Notification notification = notificationBuilder.build();
        
        Log.i(TAG, "Notification built, starting foreground service...");
        
        // Запускаем foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Log.i(TAG, "=== Foreground service started with icon ID: " + R.drawable.kiz_vpn_mono + " ===");
        
        // Если уведомления выключены, скрываем уведомление из шторки после запуска foreground service
        if (!notificationsEnabled) {
            // Используем stopForeground(false) чтобы отделить сервис от уведомления, но оставить сервис foreground
            // Затем отменяем уведомление через NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(false); // Отделяем сервис от уведомления, но оставляем сервис foreground
            }
            // Отменяем уведомление через NotificationManager (используем уже существующую переменную)
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
                Log.i(TAG, "Notification removed from notification shade (notifications disabled)");
            }
        }
        
        // Создаем VPN интерфейс
        try {
            Builder vpnBuilder = new Builder();
            vpnBuilder.addRoute("0.0.0.0", 0);
            vpnBuilder.addAddress("10.89.64.1", 32);
            vpnBuilder.addDnsServer("8.8.8.8");
            vpnBuilder.addDnsServer("8.8.4.4");
            vpnBuilder.setSession("KIZ VPN");
            vpnBuilder.setMtu(1500);
            
            Log.i(TAG, "Establishing VPN interface...");
            fileDescriptor = vpnBuilder.establish();
            
            if (fileDescriptor != null) {
                Log.i(TAG, "VPN interface created successfully! FD: " + fileDescriptor.getFileDescriptor());
                return true;
            } else {
                Log.e(TAG, "Failed to establish VPN interface - fileDescriptor is null");
                sendMessage("Error: Failed to create VPN interface. Check VPN permissions.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while creating VPN interface", e);
            sendMessage("Error: Exception creating VPN interface: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Запустить libxivpn
     */
    private boolean startLibxi() {
        Log.i(TAG, "start libxivpn");
        
        // Строим конфиг для xray
        Config config = null;
        try {
            config = buildXrayConfig();
        } catch (RuntimeException e) {
            Log.e(TAG, "build xray config", e);
            sendMessage("Error: Could not build xray config: " + e.getMessage());
            return false;
        }
        
        if (config == null) {
            sendMessage("Error: Config is null");
            return false;
        }
        
        // Логируем извлеченные параметры
        Log.i(TAG, "Parsed config - server: " + parsedConfig.getServer() + 
                   ", port: " + parsedConfig.getPort() + 
                   ", network: " + parsedConfig.getNetwork() + 
                   ", security: " + parsedConfig.getSecurity() +
                   ", path: " + parsedConfig.getPath() +
                   ", host: " + parsedConfig.getHost() +
                   ", sni: " + parsedConfig.getSni() +
                   ", allowInsecure: " + parsedConfig.getAllowInsecure() +
                   ", pbk: " + parsedConfig.getPbk() +
                   ", fp: " + parsedConfig.getFp() +
                   ", sid: " + parsedConfig.getSid() +
                   ", spx: " + parsedConfig.getSpx() +
                   ", flow: " + parsedConfig.getFlow());
        
        // Преобразуем в JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String xrayConfig = gson.toJson(config);
        Log.i(TAG, "xray config: " + xrayConfig);
        
        // Записываем конфиг в файл
        try {
            File configFile = new File(getFilesDir(), "config.json");
            FileOutputStream configStream = new FileOutputStream(configFile);
            configStream.write(xrayConfig.getBytes(StandardCharsets.UTF_8));
            configStream.close();
        } catch (IOException e) {
            Log.e(TAG, "write xray config", e);
            sendMessage("Error: Write xray config to file: " + e.getMessage());
            return false;
        }
        
        // Путь к IPC сокету
        String ipcPath = new File(getCacheDir(), "ipcsock").getAbsolutePath();
        
        // Запускаем libxivpn
        // Проверяем путь к библиотеке
        String libPath = getApplicationInfo().nativeLibraryDir + "/libxivpn.so";
        File libFile = new File(libPath);
        Log.i(TAG, "libxivpn path: " + libPath);
        Log.i(TAG, "libxivpn exists: " + libFile.exists());
        
        if (!libFile.exists()) {
            Log.e(TAG, "libxivpn.so not found at: " + libPath);
            // Попробуем альтернативный путь
            String altPath = getFilesDir().getAbsolutePath() + "/libxivpn.so";
            File altFile = new File(altPath);
            Log.i(TAG, "Trying alternative path: " + altPath);
            Log.i(TAG, "Alternative exists: " + altFile.exists());
            
            if (altFile.exists()) {
                libPath = altPath;
                libFile = altFile;
            } else {
                sendMessage("Error: libxivpn.so not found. Please check jniLibs folder.");
                return false;
            }
        }
        
        ProcessBuilder builder = new ProcessBuilder()
            .redirectErrorStream(true)
            .directory(getFilesDir())
            .command(libPath);
        
        // Устанавливаем переменные окружения
        java.util.Map<String, String> env = builder.environment();
        env.put("IPC_PATH", ipcPath);
        env.put("XRAY_LOCATION_ASSET", getFilesDir().getAbsolutePath());
        env.put("LOG_LEVEL", config.log.loglevel);
        env.put("XRAY_SNIFFING", "true");
        env.put("XRAY_SNIFFING_ROUTE_ONLY", "true");
        
        // Создаем IPC сокет
        LocalSocket socket = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            socket.bind(new LocalSocketAddress(ipcPath, LocalSocketAddress.Namespace.FILESYSTEM));
        } catch (IOException e) {
            Log.e(TAG, "bind ipc sock", e);
            sendMessage("error: bind ipc socket: " + e.getMessage());
            return false;
        }
        Log.i(TAG, "ipc sock bound");
        
        LocalServerSocket serverSocket = null;
        try {
            serverSocket = new LocalServerSocket(socket.getFileDescriptor());
            
            // Запускаем процесс
            libxivpnProcess = builder.start();
            
            // Ждем подключения
            socket = serverSocket.accept();
            ipcWriter = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "listen ipc sock", e);
            sendMessage("error: listen on ipc socket: " + e.getMessage());
            return false;
        }
        
        // Отправляем TUN file descriptor
        FileDescriptor[] fds = {fileDescriptor.getFileDescriptor()};
        socket.setFileDescriptorsForSend(fds);
        try {
            ipcWriter.write("ping\n".getBytes(StandardCharsets.US_ASCII));
            ipcWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, "write to ipc sock", e);
            sendMessage("error: write to ipc socket: " + e.getMessage());
            return false;
        }
        
        // Читаем вывод libxivpn
        teeThread = new Thread(() -> {
            Scanner scanner = new Scanner(libxivpnProcess.getInputStream());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Log.d("libxivpn", line);
            }
            scanner.close();
        });
        teeThread.start();
        
        // Обрабатываем IPC команды
        LocalSocket finalSocket = socket;
        ipcThread = new Thread(() -> {
            ipcLoop(finalSocket);
            
            synchronized (vpnStateLock) {
                if (vpnState != VPNState.STOPPING_LIBXI) {
                    sendMessage("error: libxivpn exit unexpectedly");
                    mustLibxiStop = true;
                    vpnStateLock.notify();
                }
            }
        });
        ipcThread.start();
        
        return true;
    }
    
    /**
     * Обработка IPC команд от libxivpn
     */
    private void ipcLoop(LocalSocket socket) {
        try {
            InputStream reader = socket.getInputStream();
            
            Field fdField = FileDescriptor.class.getDeclaredField("descriptor");
            fdField.setAccessible(true);
            
            Scanner scanner = new Scanner(reader);
            
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] splits = line.split(" ");
                
                Log.i(TAG, "ipc packet: " + Arrays.toString(splits));
                
                switch (splits[0]) {
                    case "ping":
                    case "pong":
                        break;
                    case "protect":
                        FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                        if (fds == null || fds.length != 1) {
                            Log.e(TAG, "invalid fd array");
                            break;
                        }
                        
                        int fd = fdField.getInt(fds[0]);
                        protectFd(fd);
                        
                        try {
                            Os.close(fds[0]);
                        } catch (ErrnoException e) {
                            Log.e(TAG, "protect os.close", e);
                        }
                        
                        Log.i(TAG, "ipc protect " + fd);
                        
                        if (ipcWriter != null) {
                            ipcWriter.write("protect_ack\n".getBytes(StandardCharsets.US_ASCII));
                            ipcWriter.flush();
                        }
                        break;
                }
            }
            
            scanner.close();
            Log.i(TAG, "ipc loop exit");
        } catch (Exception e) {
            Log.e(TAG, "ipc loop", e);
        } finally {
            ipcWriter = null;
        }
    }
    
    /**
     * Остановить libxivpn
     */
    private void stopLibxi() {
        if (ipcWriter != null) {
            try {
                ipcWriter.write("stop\n".getBytes(StandardCharsets.US_ASCII));
                ipcWriter.flush();
            } catch (Exception e) {
                Log.e(TAG, "ipc write stop", e);
            }
        }
        
        try {
            if (libxivpnProcess != null) {
                if (!libxivpnProcess.waitFor(5, TimeUnit.SECONDS)) {
                    sendMessage("error: timeout when waiting for libxivpn exit");
                    libxivpnProcess.destroyForcibly();
                    libxivpnProcess.waitFor();
                }
            }
            if (ipcThread != null) {
                ipcThread.join();
            }
            if (teeThread != null) {
                teeThread.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "wait for libxivpn", e);
        }
        
        libxivpnProcess = null;
    }
    
    /**
     * Остановить VPN
     */
    private void stopVPN() {
        try {
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "close tun fd", e);
        }
        fileDescriptor = null;
        
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    
    /**
     * Обновить уведомление с текущей информацией о подписке
     */
    private void updateNotification() {
        // Проверяем настройку уведомлений
        android.content.SharedPreferences prefs = getSharedPreferences("vpn_settings", MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        
        // Если уведомления выключены, отменяем уведомление
        if (!notificationsEnabled) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
                Log.i(TAG, "Notification cancelled (notifications disabled)");
            }
            // Отделяем сервис от уведомления, но оставляем сервис foreground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(false);
            }
            return;
        }
        
        // Формируем компактный текст (в одну строку) - объединяем время, трафик, пинг и скорость
        StringBuilder textBuilder = new StringBuilder();
        
        // Добавляем информацию о времени подписки
        if (subscriptionDays > 0 || subscriptionHours > 0) {
            if (subscriptionDays > 0) {
                int months = subscriptionDays / 30;
                int remainingDays = subscriptionDays % 30;
                if (months > 0 && remainingDays > 0) {
                    textBuilder.append(String.format("%dм %dд", months, remainingDays));
                } else if (months > 0) {
                    textBuilder.append(String.format("%d мес", months));
                } else {
                    textBuilder.append(String.format("%dд", subscriptionDays));
                    if (subscriptionHours > 0) {
                        textBuilder.append(String.format(" %dч", subscriptionHours));
                    }
                }
            } else {
                textBuilder.append(String.format("%d ч", subscriptionHours));
            }
        }
        
        // Добавляем информацию о трафике
        String trafficText = formatTrafficForNotification();
        if (trafficText != null) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(trafficText);
        }
        
        // Добавляем пинг
        if (currentPing > 0) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(currentPing).append("ms");
        }
        
        // Добавляем скорость
        String speedText = formatSpeedForNotification();
        if (speedText != null) {
            if (textBuilder.length() > 0) {
                textBuilder.append(" • ");
            }
            textBuilder.append(speedText);
        }
        
        String contentTitle = "KIZ VPN";
        String contentText;
        if (textBuilder.length() > 0) {
            contentText = textBuilder.toString();
        } else {
            contentText = "Подключено";
        }
        
        // Канал уже создан с IMPORTANCE_LOW в startVPN() или onCreate()
        // Создаем intent для открытия приложения
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        // Используем монохромную PNG иконку для статус бара
        int iconResId = R.drawable.kiz_vpn_mono;
        
        // Вычисляем прогресс трафика для полоски в уведомлении
        int trafficProgressPercent = 0;
        // Показываем прогресс только для ограниченных подписок (не больше 500 GB)
        if (subscriptionUsedTraffic >= 0 && subscriptionTotalTraffic > 0 && subscriptionTotalTraffic <= 500L * 1024 * 1024 * 1024) {
            trafficProgressPercent = (int) ((subscriptionUsedTraffic * 100) / subscriptionTotalTraffic);
            if (trafficProgressPercent > 100) trafficProgressPercent = 100;
        }
        
        // Создаём кастомный layout для уведомления
        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_custom);
        customView.setTextViewText(R.id.notification_text, contentText);
        
        // Убираем дополнительную строку - всё теперь в одной строке
        customView.setViewVisibility(R.id.notification_extra_info, android.view.View.GONE);
        
        // Настраиваем полосу прогресса (только для ограниченных подписок)
        if (subscriptionUsedTraffic >= 0 && subscriptionTotalTraffic > 0 && subscriptionTotalTraffic <= 500L * 1024 * 1024 * 1024) {
            customView.setProgressBar(R.id.notification_progress, 100, trafficProgressPercent, false);
            customView.setViewVisibility(R.id.notification_progress, android.view.View.VISIBLE);
        } else {
            customView.setViewVisibility(R.id.notification_progress, android.view.View.GONE);
        }
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconResId)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false) // Убираем время
            .setAutoCancel(false)
            .setCustomContentView(customView) // Кастомный layout для свёрнутого уведомления
            .setStyle(new androidx.core.app.NotificationCompat.DecoratedCustomViewStyle()); // Стиль для кастомного уведомления
        
        Notification notification = notificationBuilder.build();
        
        // Обновляем уведомление
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
            Log.i(TAG, "Notification updated with subscription info: " + contentText);
        }
    }
    
    /**
     * Построить конфиг для xray из ParsedConfig
     */
    private Config buildXrayConfig() {
        if (parsedConfig == null) {
            throw new RuntimeException("parsedConfig is null");
        }
        
        Config config = new Config();
        config.inbounds = new ArrayList<>();
        config.outbounds = new ArrayList<>();
        
        // Логи
        config.log.loglevel = "warning";
        
        // DNS
        config.dns = new XrayDNS();
        DNSServer dnsServer = new DNSServer();
        dnsServer.address = "8.8.8.8";
        dnsServer.port = 53;
        config.dns.servers.add(dnsServer);
        
        // Маршрутизация - все трафик через proxy
        config.routing = new Routing();
        config.routing.domainStrategy = "AsIs";
        
        RoutingRule rule = new RoutingRule();
        rule.type = "field";
        rule.outboundTag = "proxy";
        rule.ip = new ArrayList<>();
        rule.ip.add("0.0.0.0/0");
        config.routing.rules.add(rule);
        
        Log.d(TAG, "Routing configured: all traffic -> proxy");
        
        // Outbound - VLESS
        // Используем геттеры Kotlin data class
        ConfigParser.Protocol protocol = parsedConfig.getProtocol();
        if (protocol == ConfigParser.Protocol.VLESS) {
            Outbound<VlessSettings> outbound = new Outbound<>();
            outbound.protocol = "vless";
            outbound.tag = "proxy";
            
            VlessSettings settings = new VlessSettings();
            VlessServerSettings serverSettings = new VlessServerSettings();
            serverSettings.address = parsedConfig.getServer();
            serverSettings.port = parsedConfig.getPort();
            
            VlessUser user = new VlessUser();
            String uuid = parsedConfig.getUuid();
            if (uuid != null) {
                user.id = uuid;
            } else {
                throw new RuntimeException("UUID is required for VLESS");
            }
            user.encryption = "none";
            
            // Flow (xtls-rprx-vision, etc.)
            String flow = parsedConfig.getFlow();
            if (flow != null && !flow.isEmpty() && !flow.equals("none")) {
                user.flow = flow;
            }
            
            serverSettings.users.add(user);
            
            settings.vnext.add(serverSettings);
            outbound.settings = settings;
            
            // Stream settings (извлекаем из ParsedConfig)
            outbound.streamSettings = new StreamSettings();
            
            // Network (tcp, ws, grpc, etc.)
            String network = parsedConfig.getNetwork();
            if (network == null || network.isEmpty()) {
                network = "tcp"; // По умолчанию
            }
            outbound.streamSettings.network = network;
            
            // Security (none, tls, reality)
            String security = parsedConfig.getSecurity();
            if (security == null || security.isEmpty()) {
                security = "tls"; // По умолчанию
            }
            outbound.streamSettings.security = security;
            
            // WebSocket settings (если network = ws)
            if ("ws".equals(network)) {
                outbound.streamSettings.wsSettings = new com.kizvpn.client.xrayconfig.WsSettings();
                String path = parsedConfig.getPath();
                if (path != null && !path.isEmpty()) {
                    outbound.streamSettings.wsSettings.path = path;
                } else {
                    outbound.streamSettings.wsSettings.path = "/";
                }
                
                String host = parsedConfig.getHost();
                if (host != null && !host.isEmpty()) {
                    outbound.streamSettings.wsSettings.headers = new java.util.HashMap<>();
                    outbound.streamSettings.wsSettings.headers.put("Host", host);
                }
            }
            
            // Если security = "none", убираем TLS настройки
            if ("none".equals(security)) {
                outbound.streamSettings.security = "none";
                outbound.streamSettings.tlsSettings = null;
            }
            
            // TLS settings
            if ("tls".equals(security)) {
                TLSSettings tlsSettings = new TLSSettings();
                tlsSettings.allowInsecure = parsedConfig.getAllowInsecure();
                
                String sni = parsedConfig.getSni();
                if (sni != null && !sni.isEmpty()) {
                    tlsSettings.serverName = sni;
                }
                
                String alpn = parsedConfig.getAlpn();
                if (alpn != null && !alpn.isEmpty()) {
                    String[] alpnArray = alpn.split(",");
                    String[] trimmedAlpn = new String[alpnArray.length];
                    for (int i = 0; i < alpnArray.length; i++) {
                        trimmedAlpn[i] = alpnArray[i].trim();
                    }
                    tlsSettings.alpn = trimmedAlpn;
                }
                
                outbound.streamSettings.tlsSettings = tlsSettings;
            }
            
            // Reality settings
            if ("reality".equals(security)) {
                outbound.streamSettings.realitySettings = new com.kizvpn.client.xrayconfig.RealitySettings();
                
                String sni = parsedConfig.getSni();
                if (sni != null && !sni.isEmpty()) {
                    outbound.streamSettings.realitySettings.serverName = sni;
                }
                
                String pbk = parsedConfig.getPbk();
                if (pbk != null && !pbk.isEmpty()) {
                    outbound.streamSettings.realitySettings.publicKey = pbk;
                }
                
                String fp = parsedConfig.getFp();
                if (fp != null && !fp.isEmpty()) {
                    outbound.streamSettings.realitySettings.fingerprint = fp;
                } else {
                    outbound.streamSettings.realitySettings.fingerprint = "chrome"; // По умолчанию
                }
                
                String sid = parsedConfig.getSid();
                if (sid != null && !sid.isEmpty()) {
                    outbound.streamSettings.realitySettings.shortId = sid;
                }
                
                String spx = parsedConfig.getSpx();
                if (spx != null && !spx.isEmpty()) {
                    outbound.streamSettings.realitySettings.spiderX = spx;
                }
            }
            
            config.outbounds.add(outbound);
        } else if (protocol == ConfigParser.Protocol.WIREGUARD) {
            Outbound<WireguardSettings> outbound = new Outbound<>();
            outbound.protocol = "wireguard";
            outbound.tag = "proxy";
            
            WireguardSettings settings = new WireguardSettings();
            
            // Private key (secret key)
            String privateKey = parsedConfig.getPrivateKey();
            if (privateKey != null && !privateKey.isEmpty()) {
                settings.secretKey = privateKey;
            } else {
                throw new RuntimeException("Private key is required for WireGuard");
            }
            
            // Addresses (local addresses из секции [Interface])
            // WireGuard обычно использует адреса вида 10.0.0.2/32
            String address = parsedConfig.getAddress();
            if (address != null && !address.isEmpty()) {
                // Парсим адреса из поля address (могут быть через запятую)
                String[] addressParts = address.split(",");
                for (String addr : addressParts) {
                    String trimmedAddr = addr.trim();
                    if (!trimmedAddr.isEmpty()) {
                        settings.address.add(trimmedAddr);
                    }
                }
            }
            // Если address не указан, используем значение по умолчанию
            if (settings.address.isEmpty()) {
                settings.address.add("10.0.0.2/32");
            }
            
            // Peer
            WireguardPeer peer = new WireguardPeer();
            peer.endpoint = parsedConfig.getEndpoint();
            if (peer.endpoint == null || peer.endpoint.isEmpty()) {
                // Если endpoint не указан, создаем из server:port
                peer.endpoint = parsedConfig.getServer() + ":" + parsedConfig.getPort();
            }
            
            peer.publicKey = parsedConfig.getPublicKey();
            if (peer.publicKey == null || peer.publicKey.isEmpty()) {
                // Public key может быть не обязательным для некоторых конфигов
                // но лучше выбрасывать ошибку
                Log.w(TAG, "Public key not found in WireGuard config, connection may fail");
            }
            
            // PreSharedKey (опционально)
            String preSharedKey = parsedConfig.getPreSharedKey();
            if (preSharedKey != null && !preSharedKey.isEmpty()) {
                peer.preSharedKey = preSharedKey;
            }
            
            // Allowed IPs (маршруты из секции [Peer])
            String allowedIPs = parsedConfig.getAllowedIPs();
            if (allowedIPs != null && !allowedIPs.isEmpty()) {
                String[] allowedIPsArray = allowedIPs.split(",");
                String[] trimmedIPs = new String[allowedIPsArray.length];
                for (int i = 0; i < allowedIPsArray.length; i++) {
                    trimmedIPs[i] = allowedIPsArray[i].trim();
                }
                peer.allowedIPs = trimmedIPs;
            } else {
                // По умолчанию все трафик
                peer.allowedIPs = new String[]{"0.0.0.0/0", "::/0"};
            }
            
            // Keep alive (по умолчанию 60 секунд)
            peer.keepAlive = 60;
            
            settings.peers.add(peer);
            
            // Reserved (обычно [0, 0, 0])
            settings.reserved = new int[]{0, 0, 0};
            
            // Workers (по умолчанию 2)
            settings.workers = 2;
            
            // Kernel mode (обычно false для пользовательского пространства)
            settings.kernelMode = false;
            
            outbound.settings = settings;
            
            // WireGuard не использует streamSettings
            outbound.streamSettings = null;
            
            config.outbounds.add(outbound);
            
            Log.d(TAG, "WireGuard outbound configured: endpoint=" + peer.endpoint + 
                       ", publicKey=" + (peer.publicKey != null ? "***" : "null"));
        } else {
            throw new RuntimeException("Unsupported protocol: " + protocol);
        }
        
        return config;
    }
    
    @Override
    public void onRevoke() {
        Log.i(TAG, "on revoke");
        synchronized (vpnStateLock) {
            mustLibxiStop = true;
            vpnStateLock.notify();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return binder;
    }
    
    @Override
    public void protectFd(int fd) {
        Log.d(TAG, "protect " + fd);
        protect(fd);
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "on destroy");
        super.onDestroy();
    }
    
    // Enums и интерфейсы
    public enum VPNState {
        DISCONNECTED, ESTABLISHING_VPN, STARTING_LIBXI, CONNECTED, STOPPING_LIBXI, STOPPING_VPN
    }
    
    public enum Command {
        NONE, CONNECT, DISCONNECT
    }
    
    public interface VPNStateListener {
        void onStateChanged(VPNState status);
        void onMessage(String msg);
    }
    
    public class KizVpnBinder extends Binder {
        public VPNState getState() {
            return KizVpnService.this.vpnState;
        }
        
        public void addListener(VPNStateListener listener) {
            synchronized (listeners) {
                KizVpnService.this.listeners.add(listener);
            }
        }
        
        public void removeListener(VPNStateListener listener) {
            synchronized (listeners) {
                KizVpnService.this.listeners.remove(listener);
            }
        }
        
        public void setConfig(String configUrl) {
            KizVpnService.this.setConfig(configUrl);
        }
        
        public KizVpnService getService() {
            return KizVpnService.this;
        }
        
        public void refreshSubscriptionInfo() {
            KizVpnService.this.loadSubscriptionInfo();
            if (KizVpnService.this.vpnState == VPNState.CONNECTED) {
                KizVpnService.this.updateNotification();
            }
        }
        
        public void updatePing(int ping) {
            KizVpnService.this.setPingInfo(ping);
        }
        
        public void updateSpeed(long downloadSpeed, long uploadSpeed) {
            KizVpnService.this.setSpeedInfo(downloadSpeed, uploadSpeed);
        }
    }
    
    /**
     * Форматирует трафик в компактном виде (например, 822,93 MB -> 0,8GB)
     */
    private String formatBytes(long bytes) {
        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;
        double tb = gb / 1024.0;
        
        java.util.Locale locale = java.util.Locale.getDefault();
        String formatted;
        
        if (tb >= 1.0) {
            // Для TB: если целое число, не показываем десятичные
            if (tb == (int) tb) {
                formatted = String.format(locale, "%.0fTB", tb);
            } else {
                formatted = String.format(locale, "%.1fTB", tb);
            }
        } else if (gb >= 0.1) {
            // Для GB: если целое число, не показываем десятичные
            if (gb == (int) gb) {
                formatted = String.format(locale, "%.0fGB", gb);
            } else {
                formatted = String.format(locale, "%.1fGB", gb);
            }
        } else if (mb >= 100.0) {
            // Если MB >= 100, показываем в GB с одним знаком
            formatted = String.format(locale, "%.1fGB", gb);
        } else if (mb >= 1.0) {
            formatted = String.format(locale, "%.0fMB", mb);   // Целые MB
        } else if (kb >= 1.0) {
            formatted = String.format(locale, "%.0fKB", kb);   // Целые KB
        } else {
            return bytes + "B";
        }
        
        // Заменяем точку на запятую для русской локали
        if (locale.getLanguage().equals("ru") && formatted.contains(".")) {
            return formatted.replace(".", ",");
        }
        return formatted;
    }
    
    /**
     * Форматирует трафик в формате "45.79 MB / 50 GB" (использовано / всего)
     * Для безлимитных подписок: "Безлимит • X GB / ∞"
     */
    private String formatTrafficForNotification() {
        if (subscriptionUsedTraffic < 0) {
            return null;
        }
        
        String usedText = formatBytes(subscriptionUsedTraffic);
        String totalText;
        boolean isUnlimitedTraffic = false;
        
        // Проверяем безлимитность ТОЛЬКО по трафику: если totalTraffic <= 0 или очень большое число (больше 500 GB)
        if (subscriptionTotalTraffic <= 0 || subscriptionTotalTraffic > 500L * 1024 * 1024 * 1024) {
            totalText = "∞";
            isUnlimitedTraffic = true;
        } else {
            totalText = formatBytes(subscriptionTotalTraffic);
        }
        
        // "Безлимит" показываем ТОЛЬКО если трафик действительно безлимитный
        // Если подписка ограничена по времени (есть дни), но безлимитная по трафику - не показываем "Безлимит"
        boolean hasTimeLimitation = (subscriptionDays > 0);
        
        if (isUnlimitedTraffic && !hasTimeLimitation) {
            return "Безлимит • " + usedText + " / " + totalText;
        } else {
            return usedText + " / " + totalText;
        }
    }
    
    /**
     * Форматирует скорость в компактном виде для уведомления (например, "20kb ↑↓ 3kb")
     */
    private String formatSpeedForNotification() {
        if (currentDownloadSpeed <= 0 && currentUploadSpeed <= 0) {
            Log.d(TAG, "formatSpeedForNotification: No speed data (download=" + currentDownloadSpeed + ", upload=" + currentUploadSpeed + ")");
            return null;
        }
        
        String downloadText = formatSpeedCompact(currentDownloadSpeed);
        String uploadText = formatSpeedCompact(currentUploadSpeed);
        
        String result = downloadText + " ↓↑ " + uploadText;
        Log.d(TAG, "formatSpeedForNotification: " + result + " (download=" + currentDownloadSpeed + "B/s, upload=" + currentUploadSpeed + "B/s)");
        return result;
    }
    
    /**
     * Форматирует скорость в компактном виде (KB/s)
     */
    private String formatSpeedCompact(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0kb";
        }
        
        double kbps = bytesPerSecond / 1024.0;
        
        if (kbps >= 1024.0) {
            double mbps = kbps / 1024.0;
            return String.format(java.util.Locale.getDefault(), "%.1fmb", mbps);
        } else if (kbps >= 1.0) {
            return String.format(java.util.Locale.getDefault(), "%.0fkb", kbps);
        } else {
            return "0kb";
        }
    }
}


