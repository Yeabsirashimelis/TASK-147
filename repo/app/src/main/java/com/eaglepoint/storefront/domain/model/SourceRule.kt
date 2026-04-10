package com.eaglepoint.storefront.domain.model

data class SourceRule(
    val id: String,
    val name: String,
    val url: String,
    val feedType: FeedType,
    val parseSelector: String? = null,
    val allowedDomains: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val allowedKeywords: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val intervalHours: Int = 6,
    val requestDelayMs: Long = 2000,
    val isActive: Boolean = true,
    val ruleVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)

enum class FeedType {
    RSS,
    ATOM,
    HTML_SCRAPE
}
