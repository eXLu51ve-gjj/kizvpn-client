package com.kizvpn.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kizvpn.client.R
import com.kizvpn.client.ui.models.Server
import com.kizvpn.client.ui.theme.BackgroundDark
import com.kizvpn.client.ui.theme.NeonPrimary
import com.kizvpn.client.ui.theme.NeonAccent
import com.kizvpn.client.ui.theme.NeonGreen
import com.kizvpn.client.ui.theme.StatusDisconnected
import com.kizvpn.client.ui.theme.TextPrimary
import com.kizvpn.client.ui.theme.TextSecondary
import com.kizvpn.client.ui.theme.CardDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onBack: () -> Unit,
    onServerSelected: (Server) -> Unit = {},
    servers: List<Server> = getSampleServers()
) {
    var selectedServerId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        TopAppBar(
            title = { 
                Text(
                    "Серверы",
                    color = TextPrimary
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BackgroundDark
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(servers) { server ->
                ServerRow(
                    server = server,
                    isSelected = selectedServerId == server.id,
                    onSelect = {
                        selectedServerId = server.id
                        onServerSelected(server)
                    }
                )
            }
        }
    }
}

@Composable
fun ServerRow(
    server: Server,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonPrimary.copy(alpha = 0.2f) else CardDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = server.flagEmoji,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                Column {
                    Text(
                        text = server.country,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    if (server.city != null) {
                        Text(
                            text = server.city,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ping
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${server.pingMs} мс",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                server.pingMs < 50 -> NeonGreen
                                server.pingMs < 100 -> NeonAccent
                                else -> StatusDisconnected
                            }
                        )
                    }
                    
                    // Load
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${server.loadPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                server.loadPercent < 30 -> NeonGreen
                                server.loadPercent < 70 -> NeonAccent
                                else -> StatusDisconnected
                            }
                        )
                    }
                    
                    // Selected indicator
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Выбрано",
                            tint = NeonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getSampleServers(): List<Server> {
    return listOf(
        Server("1", "Россия", "Москва", 28, 15, "🇷🇺", true),
        Server("2", "США", "Нью-Йорк", 120, 45, "🇺🇸"),
        Server("3", "Германия", "Франкфурт", 38, 22, "🇩🇪"),
        Server("4", "Япония", "Токио", 180, 12, "🇯🇵"),
        Server("5", "Нидерланды", "Амстердам", 42, 30, "🇳🇱"),
        Server("6", "Сингапур", null, 250, 18, "🇸🇬"),
        Server("7", "Бразилия", "Сан-Паулу", 220, 65, "🇧🇷")
    )
}

