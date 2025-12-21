package com.kizvpn.client.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * Информация о подписке пользователя
 */
data class SubscriptionInfo(
    val days: Int = 0,
    val hours: Int = 0,
    val unlimited: Boolean = false,
    val expired: Boolean = false,
    // Новые поля для расширенной информации
    val expiryDate: Long? = null,           // Дата окончания в миллисекундах (timestamp)
    val totalTraffic: Long? = null,         // Общий трафик в байтах
    val usedTraffic: Long? = null,          // Использованный трафик в байтах
    val remainingTraffic: Long? = null,     // Оставшийся трафик в байтах
    val configsCount: Int? = null           // Количество конфигураций из Subscription URL
) {
    /**
     * Форматирует информацию о подписке для отображения
     */
    fun format(): String {
        // Проверяем, является ли подписка безлимитной
        // Безлимит если: unlimited = true ИЛИ expiryDate = 0 или очень маленькое значение (< 2001)
        // НО НЕ если expiryDate просто null - это может быть подписка с ограниченным сроком, но без сохраненной даты
        val isUnlimitedSubscription = unlimited || 
                (expiryDate != null && (expiryDate == 0L || expiryDate < 1000000000L)) // Только если expiryDate есть, но = 0 или < 2001
        
        if (isUnlimitedSubscription) {
            return "Безлимит"
        }
        
        // Если есть дата окончания, вычисляем актуальные дни/часы от текущего времени
        if (expiryDate != null && expiryDate > 0) {
            val now = System.currentTimeMillis()
            val timeUntilExpiry = expiryDate - now
            
            if (timeUntilExpiry > 0) {
                // Подписка активна, вычисляем дни и часы
                val actualDays = (timeUntilExpiry / (1000 * 60 * 60 * 24)).toInt()
                val actualHours = ((timeUntilExpiry % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()
                
                val parts = mutableListOf<String>()
                
                if (actualDays > 0) {
                    val daysText = when {
                        actualDays == 1 -> "день"
                        actualDays in 2..4 -> "дня"
                        else -> "дней"
                    }
                    parts.add("$actualDays $daysText")
                }
                
                if (actualHours > 0 || parts.isEmpty()) {
                    val hoursText = when {
                        actualHours == 1 -> "час"
                        actualHours in 2..4 -> "часа"
                        else -> "часов"
                    }
                    if (actualHours > 0) {
                        parts.add("$actualHours $hoursText")
                    }
                }
                
                // Если есть дни или часы, возвращаем их
                if (parts.isNotEmpty()) {
                    return parts.joinToString(" ")
                }
            } else {
                // Подписка истекла
                return "Истекла: ${formatExpiryDate() ?: "Неизвестно"}"
            }
        }
        
        // Если подписка истекла и нет дней/часов, показываем "Не активирован"
        if (expired && days == 0 && hours == 0) {
            return "Не активирован"
        }
        
        // Используем сохраненные дни/часы, если они есть
        val parts = mutableListOf<String>()
        
        if (days > 0) {
            val daysText = when {
                days == 1 -> "день"
                days in 2..4 -> "дня"
                else -> "дней"
            }
            parts.add("$days $daysText")
        }
        
        if (hours > 0) {
            val hoursText = when {
                hours == 1 -> "час"
                hours in 2..4 -> "часа"
                else -> "часов"
            }
            parts.add("$hours $hoursText")
        }
        
        // Если есть дата окончания, но нет дней/часов (случай edge) - показываем дату
        if (parts.isEmpty() && expiryDate != null) {
            val formattedDate = formatExpiryDate()
            if (formattedDate != null) {
                return formattedDate
            }
        }
        
        // Если всё ещё пусто, показываем "Не активирован"
        return if (parts.isEmpty()) "Не активирован" else parts.joinToString(" ")
    }
    
    /**
     * Форматирует дату окончания подписки
     */
    fun formatExpiryDate(): String? {
        if (expiryDate == null) return null
        return try {
            // Формат: "25 декабря 2025 10:37" для русской локали или "dd MMMM yyyy HH:mm" для других
            val dateFormat = if (Locale.getDefault().language == "ru") {
                // Для русской локали используем полное название месяца
                SimpleDateFormat("d MMMM yyyy HH:mm", Locale("ru", "RU"))
            } else {
                SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault())
            }
            dateFormat.format(Date(expiryDate))
        } catch (e: Exception) {
            // Fallback на простой формат
            try {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                dateFormat.format(Date(expiryDate))
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    /**
     * Форматирует трафик в удобочитаемый вид
     */
    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0
        
        // Используем запятую как разделитель дробной части для русской локали
        val locale = Locale.getDefault()
        
        val (formatString, value) = when {
            tb >= 1.0 -> "%.1fTB" to tb
            gb >= 0.1 -> {
                // Для GB: если целое число, не показываем десятичные
                if (gb == gb.toInt().toDouble()) {
                    "%.0fGB" to gb
                } else {
                    "%.1fGB" to gb
                }
            }
            mb >= 100.0 -> {
                // Если MB >= 100, показываем в GB с одним знаком
                "%.1fGB" to gb
            }
            mb >= 1.0 -> "%.0fMB" to mb   // Целые MB
            kb >= 1.0 -> "%.0fKB" to kb   // Целые KB
            else -> return "${bytes}B"
        }
        
        val formatted = String.format(locale, formatString, value)
        
        // Заменяем точку на запятую для русской локали, если форматирование использовало точку
        return if (locale.language == "ru" && formatted.contains(".")) {
            formatted.replace(".", ",")
        } else {
            formatted
        }
    }
    
    /**
     * Форматирует трафик в компактном виде (например, 822,55 MB -> 0,8GB)
     */
    private fun formatBytesCompact(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0
        
        val locale = Locale.getDefault()
        
        val (formatString, value) = when {
            tb >= 1.0 -> "%.1fTB" to tb
            gb >= 0.1 -> {
                // Для GB: если целое число, не показываем десятичные
                if (gb == gb.toInt().toDouble()) {
                    "%.0fGB" to gb
                } else {
                    "%.1fGB" to gb
                }
            }
            mb >= 100.0 -> {
                // Если MB >= 100, показываем в GB с одним знаком
                "%.1fGB" to gb
            }
            mb >= 1.0 -> "%.0fMB" to mb   // Целые MB
            kb >= 1.0 -> "%.0fKB" to kb   // Целые KB
            else -> return "${bytes}B"
        }
        
        val formatted = String.format(locale, formatString, value)
        
        return if (locale.language == "ru" && formatted.contains(".")) {
            formatted.replace(".", ",")
        } else {
            formatted
        }
    }
    
    /**
     * Форматирует использованный трафик в компактном виде
     */
    fun formatUsedTrafficCompact(): String? {
        if (usedTraffic == null) return null
        return formatBytesCompact(usedTraffic)
    }
    
    /**
     * Форматирует общий трафик в компактном виде
     * Возвращает "∞" для безлимитных подписок
     */
    fun formatTotalTrafficCompact(): String? {
        // Если подписка безлимитная, возвращаем ∞
        if (unlimited) return "∞"
        
        // Если дата окончания = 0 (безлимитная подписка)
        if (expiryDate != null && (expiryDate == 0L || expiryDate < 1000000000L)) return "∞"
        
        // Если нет информации о трафике
        if (totalTraffic == null) return "∞"
        
        // Если трафик очень большой (> 500 GB), считаем безлимитом
        if (totalTraffic > 500L * 1024 * 1024 * 1024) return "∞"
        
        return formatBytesCompact(totalTraffic)
    }
    
    /**
     * Форматирует время в компактном виде (например, 33 дней 23 часов -> 33д 23ч)
     */
    fun formatTimeCompact(): String {
        if (expired) return "Истекла"
        if (unlimited) return "Безлимит"
        
        val totalDays = days ?: 0
        val totalHours = hours ?: 0
        
        return when {
            totalDays > 0 && totalHours > 0 -> "${totalDays}д ${totalHours}ч"
            totalDays > 0 -> "${totalDays}д"
            totalHours > 0 -> "${totalHours}ч"
            else -> "Истекла"
        }
    }
    
    /**
     * Форматирует использованный трафик
     */
    fun formatUsedTraffic(): String? {
        if (usedTraffic == null) return null
        return formatBytes(usedTraffic)
    }
    
    /**
     * Форматирует оставшийся трафик
     */
    fun formatRemainingTraffic(): String? {
        // Сначала проверяем напрямую заданный remainingTraffic
        if (remainingTraffic != null) {
            return formatBytes(remainingTraffic)
        }
        
        // Вычисляем из totalTraffic и usedTraffic
        if (totalTraffic != null && usedTraffic != null) {
            val remaining = (totalTraffic - usedTraffic).coerceAtLeast(0)
            return formatBytes(remaining)
        }
        
        // Если есть только usedTraffic, но нет totalTraffic, значит безлимит - не показываем остаток
        // Если есть только totalTraffic, но нет usedTraffic, показываем весь totalTraffic как остаток
        if (totalTraffic != null && usedTraffic == null && totalTraffic > 0) {
            return formatBytes(totalTraffic)
        }
        
        return null
    }
    
    /**
     * Форматирует общий трафик
     */
    fun formatTotalTraffic(): String? {
        if (totalTraffic == null) return null
        return formatBytes(totalTraffic)
    }
    
    /**
     * Форматирует трафик в формате "0,8GB / 100GB" (использовано / всего)
     * Используется для уведомлений
     */
    fun formatTrafficForNotification(): String? {
        val usedText = formatUsedTrafficCompact() ?: return null
        val totalText: String
        val isUnlimitedTraffic: Boolean
        
        // Проверяем безлимитность ТОЛЬКО по трафику: если totalTraffic == null, <= 0 или очень большое число (больше 500 GB)
        if (totalTraffic == null || totalTraffic <= 0 || totalTraffic > 500L * 1024 * 1024 * 1024) {
            totalText = "∞"
            isUnlimitedTraffic = true
        } else {
            totalText = formatTotalTrafficCompact() ?: "∞"
            isUnlimitedTraffic = false
        }
        
        // "Безлимит" показываем ТОЛЬКО если трафик действительно безлимитный И нет ограничения по времени
        val hasTimeLimitation = (days != null && days > 0)
        
        return if (isUnlimitedTraffic && !hasTimeLimitation) {
            "Безлимит • $usedText / $totalText"
        } else {
            "$usedText / $totalText"
        }
    }
    
    /**
     * Проверяет, есть ли ограничение по трафику
     * Учитывает безлимитные подписки (unlimited = true или expiryDate = 0)
     * и очень большие значения трафика (> 500GB считается безлимитом)
     */
    fun hasTrafficLimit(): Boolean {
        // Если подписка помечена как безлимитная
        if (unlimited) return false
        
        // Если дата окончания = 0 или очень маленькая (безлимитная подписка)
        if (expiryDate != null && (expiryDate == 0L || expiryDate < 1000000000L)) return false
        
        // Если нет информации о трафике или трафик <= 0
        if (totalTraffic == null || totalTraffic <= 0) return false
        
        // Если трафик очень большой (> 500 GB), считаем безлимитом
        if (totalTraffic > 500L * 1024 * 1024 * 1024) return false
        
        return true
    }
    
    /**
     * Проверяет, превышен ли лимит трафика
     */
    fun isTrafficExceeded(): Boolean {
        if (!hasTrafficLimit()) return false
        if (usedTraffic == null) return false
        return usedTraffic >= (totalTraffic ?: 0)
    }
    
    companion object {
        fun fromDays(days: Int?): SubscriptionInfo {
            if (days == null || days <= 0) {
                return SubscriptionInfo(expired = true)
            }
            return SubscriptionInfo(days = days, hours = 0)
        }
    }
}

