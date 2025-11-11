package com.kizvpn.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kizvpn.client.ui.theme.NeonPrimary

/**
 * Нижняя навигационная панель
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
            label = { Text("Главная") },
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonPrimary,
                selectedTextColor = NeonPrimary,
                indicatorColor = NeonPrimary.copy(alpha = 0.2f)
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Default.Storage, contentDescription = "Серверы") },
            label = { Text("Серверы") },
            selected = currentRoute == "servers",
            onClick = { onNavigate("servers") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonPrimary,
                selectedTextColor = NeonPrimary,
                indicatorColor = NeonPrimary.copy(alpha = 0.2f)
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
            label = { Text("Настройки") },
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonPrimary,
                selectedTextColor = NeonPrimary,
                indicatorColor = NeonPrimary.copy(alpha = 0.2f)
            )
        )
    }
}

