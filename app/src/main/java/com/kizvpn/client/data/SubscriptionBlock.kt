package com.kizvpn.client.data

/**
 * Блок Subscription URL с информацией о подписке и конфигах
 */
data class SubscriptionBlock(
    val url: String,
    val subscriptionInfo: SubscriptionInfo,
    val configs: List<String> // Список конфигов из этого Subscription URL
)













