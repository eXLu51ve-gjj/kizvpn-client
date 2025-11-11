package com.kizvpn.client.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kizvpn.client.ui.navigation.AppNavHost
import com.kizvpn.client.ui.theme.KizVpnTheme

/**
 * MainActivity с Jetpack Compose UI
 * 
 * Это альтернативная версия MainActivity с Compose UI.
 * Чтобы использовать, замените MainActivity в AndroidManifest.xml
 * или создайте новый Activity для Compose.
 */
class MainActivityCompose : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KizVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.kizvpn.client.ui.theme.BackgroundDark
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

