package com.kizvpn.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kizvpn.client.R
import com.kizvpn.client.ui.models.ConnectionStatus
import com.kizvpn.client.ui.theme.*
import com.kizvpn.client.ui.viewmodel.VpnViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    connectionStatus: ConnectionStatus,
    viewModel: VpnViewModel? = null
) {
    // Получаем данные из ViewModel
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.statistics_title),
                    color = TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.logout),
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Время подключения
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardDark
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Время подключения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formattedDuration,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (connectionStatus.isConnected) StatusConnected else TextSecondary
                    )
                }
            }

            // Скорость
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardDark
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Скорость",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        formattedSpeed,
                        style = MaterialTheme.typography.headlineSmall,
                        color = NeonPrimary
                    )
                }
            }

            // Трафик
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardDark
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Трафик",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Входящий: $formattedDownload",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Исходящий: $formattedUpload",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

