package com.kizvpn.client.data

/**
 * Информация о подписке пользователя
 */
data class SubscriptionInfo(
    val days: Int = 0,
    val hours: Int = 0,
    val unlimited: Boolean = false,
    val expired: Boolean = false
) {
    /**
     * Форматирует информацию о подписке для отображения
     */
    fun format(): String {
        if (unlimited) {
            return "Безлимит"
        }
        if (expired || (days == 0 && hours == 0)) {
            return "Не активирован"
        }
        
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
        
        return if (parts.isEmpty()) "Не активирован" else parts.joinToString(" ")
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

