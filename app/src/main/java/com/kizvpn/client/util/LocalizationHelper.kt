package com.kizvpn.client.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocalizationHelper {
    /**
     * Получает локализованную строку по ID ресурса с учетом выбранного языка
     * @param context Контекст приложения
     * @param stringResId ID строкового ресурса (например, R.string.app_name)
     * @return Локализованная строка
     */
    fun getString(context: Context, stringResId: Int, vararg formatArgs: Any?): String {
        val language = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
            .getString("language", "ru") ?: "ru"
        
        val locale = when (language) {
            "en" -> Locale.ENGLISH
            "ru" -> Locale("ru")
            else -> Locale.getDefault()
        }
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)
        
        return if (formatArgs.isNotEmpty()) {
            localizedContext.resources.getString(stringResId, *formatArgs)
        } else {
            localizedContext.resources.getString(stringResId)
        }
    }
    
    /**
     * Получает текущий выбранный язык
     */
    fun getCurrentLanguage(context: Context): String {
        return context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
            .getString("language", "ru") ?: "ru"
    }
    
    /**
     * Обновляет локаль контекста приложения (для системных ресурсов)
     */
    fun updateLocale(context: Context, language: String): Context {
        val locale = when (language) {
            "en" -> Locale.ENGLISH
            "ru" -> Locale("ru")
            else -> Locale.getDefault()
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

/**
 * Composable функция для получения локализованной строки
 */
@androidx.compose.runtime.Composable
fun localizedString(stringResId: Int, vararg formatArgs: Any?): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return LocalizationHelper.getString(context, stringResId, *formatArgs)
}



























