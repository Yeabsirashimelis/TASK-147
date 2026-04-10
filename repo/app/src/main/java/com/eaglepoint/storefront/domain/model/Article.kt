package com.eaglepoint.storefront.domain.model

data class Article(
    val id: String,
    val sourceRuleId: String,
    val batchVersionId: String? = null,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val author: String? = null,
    val externalUrl: String,
    val imageUrl: String? = null,
    val team: String? = null,
    val league: String? = null,
    val isSavedOffline: Boolean = false,
    val savedAt: Long? = null,
    val curationStatus: CurationStatus = CurationStatus.PENDING_REVIEW,
    val curatedBy: String? = null,
    val isFeatured: Boolean = false,
    val topic: String? = null,
    val publishedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

enum class CurationStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    FEATURED
}

data class ArticleSearchQuery(
    val keyword: String? = null,
    val sourceRuleId: String? = null,
    val team: String? = null,
    val league: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
)

enum class UserRole {
    ADMIN,
    EDITOR,
    ANALYST,
    USER
}
