package com.kizvpn.client.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kizvpn.client.ui.components.BottomNavigationBar
import com.kizvpn.client.ui.models.ConnectionStatus
import com.kizvpn.client.ui.screens.HomeScreen
import com.kizvpn.client.ui.screens.ServersScreen
import com.kizvpn.client.ui.screens.SettingsScreen
import com.kizvpn.client.ui.screens.QRCodeScreen
import com.kizvpn.client.ui.screens.StatisticsScreen
import com.kizvpn.client.ui.screens.HistoryScreen
import com.kizvpn.client.ui.screens.AboutScreen
import com.kizvpn.client.ui.screens.ActivateKeyScreen
import com.kizvpn.client.data.SubscriptionInfo

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Servers : Screen("servers")
    object Settings : Screen("settings")
    object QRCode : Screen("qr_code")
    object Statistics : Screen("statistics")
    object History : Screen("history")
    object About : Screen("about")
    object ActivateKey : Screen("activate_key")
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    connectionStatus: ConnectionStatus = ConnectionStatus(),
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    subscriptionInfo: SubscriptionInfo? = null,
    trafficData: List<Float> = emptyList(),
    vpnConfig: String? = null,
    onConfigChange: (String) -> Unit = {},
    viewModel: com.kizvpn.client.ui.viewmodel.VpnViewModel? = null,
    onActivateKey: (String, (Int?) -> Unit) -> Unit = { _, _ -> },
    onSaveWireGuardConfig: (String) -> Unit = {},
    onSelectConfig: (String, com.kizvpn.client.config.ConfigParser.Protocol) -> Unit = { _, _ -> },
    onDeleteConfig: (String, com.kizvpn.client.config.ConfigParser.Protocol) -> Unit = { _, _ -> },
    configNotification: String? = null,
    onShowConfigNotification: (String) -> Unit = {}, // Callback для показа уведомления сверху экрана
    onSubscriptionUrlCheck: ((String) -> Unit)? = null, // Callback для проверки subscription URL
    onShowNetworkChart: () -> Unit = {}, // Callback для показа графика сети
    isVpnConnected: Boolean = false, // Статус подключения VPN
    onUpdateSubscriptionInfo: (com.kizvpn.client.data.SubscriptionInfo) -> Unit = {} // Callback для обновления информации о подписке
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    
    // Убираем Scaffold и padding, чтобы контент был до самого низа экрана
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenServers = { navController.navigate(Screen.Servers.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                connectionStatus = connectionStatus,
                onConnectClick = onConnectClick,
                onDisconnectClick = onDisconnectClick,
                trafficData = trafficData,
                vpnConfig = vpnConfig,
                onConfigChange = onConfigChange,
                onScanQRCode = { navController.navigate(Screen.QRCode.route) },
                onShowStatistics = { navController.navigate(Screen.Statistics.route) },
                onShowHistory = { navController.navigate(Screen.History.route) },
                onShowAbout = { navController.navigate(Screen.About.route) },
                onActivateKey = onActivateKey,
                onSaveWireGuardConfig = onSaveWireGuardConfig,
                onSelectConfig = onSelectConfig,
                configNotification = configNotification,
                subscriptionInfo = subscriptionInfo,
                viewModel = viewModel,
                onShowConfigNotification = onShowConfigNotification
            )
        }
        
        composable(Screen.Servers.route) {
            ServersScreen(
                onBack = { navController.popBackStack() },
                onServerSelected = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            val context = LocalContext.current
            SettingsScreen(
                onBack = { navController.popBackStack() },
                subscriptionInfo = subscriptionInfo,
                context = context,
                onSubscriptionUrlCheck = onSubscriptionUrlCheck
            )
        }
        
        composable(Screen.QRCode.route) {
            QRCodeScreen(
                onBack = { navController.popBackStack() },
                onConfigScanned = { config ->
                    try {
                        // Обработка отсканированного конфига
                        onConfigChange(config)
                        // Закрытие экрана происходит внутри QRCodeScreen после задержки
                    } catch (e: Exception) {
                        android.util.Log.e("NavGraph", "Ошибка при обработке отсканированного конфига", e)
                        // В случае ошибки все равно закрываем экран
                        navController.popBackStack()
                    }
                }
            )
        }
        
        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onBack = { navController.popBackStack() },
                connectionStatus = connectionStatus,
                viewModel = viewModel
            )
        }
        
        composable(Screen.History.route) {
            val context = LocalContext.current
            HistoryScreen(
                onBack = { navController.popBackStack() },
                context = context
            )
        }
        
        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ActivateKey.route) {
            ActivateKeyScreen(
                onBack = { navController.popBackStack() },
                onActivateKey = onActivateKey
            )
        }
        
    }
}

