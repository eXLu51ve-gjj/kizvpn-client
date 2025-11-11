package com.kizvpn.client.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kizvpn.client.data.ConnectionHistoryManager
import com.kizvpn.client.data.ConnectionHistoryEntry
import com.kizvpn.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    context: Context? = null
) {
    val ctx = context ?: LocalContext.current
    val historyManager = remember { ConnectionHistoryManager(ctx) }
    var history by remember { mutableStateOf<List<ConnectionHistoryEntry>>(emptyList()) }
    
    // Загружаем историю при открытии экрана
    LaunchedEffect(Unit) {
        history = historyManager.getHistory()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = {
                Text(
                    "История подключений",
                    color = TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "История подключений будет отображаться здесь",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { entry ->
                    HistoryItem(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: ConnectionHistoryEntry) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    val date = dateFormat.format(Date(entry.timestamp))
    
    val actionText = when (entry.action) {
        "connected" -> "Подключено"
        "disconnected" -> "Отключено"
        else -> entry.action
    }
    
    val actionColor = when (entry.action) {
        "connected" -> StatusConnected
        "disconnected" -> StatusDisconnected
        else -> TextSecondary
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CardDark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = actionColor
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            if (entry.server != null) {
                Text(
                    text = "Сервер: ${entry.server}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
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
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

