package com.eaglepoint.storefront.domain.model

data class ParsedFeedItem(
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val author: String? = null,
    val url: String,
    val imageUrl: String? = null,
    val publishedAt: Long? = null
)
