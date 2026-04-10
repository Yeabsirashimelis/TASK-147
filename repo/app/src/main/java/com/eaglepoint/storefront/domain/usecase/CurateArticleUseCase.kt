package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.CurationStatus
import com.eaglepoint.storefront.security.SessionManager

class CurateArticleUseCase(
    private val articleRepository: ArticleRepository,
    private val roleGuard: RoleGuard,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager
) {

    suspend fun approve(articleId: String): Result<Unit> {
        roleGuard.enforceCurationAccess()
        val userId = sessionManager.requireUserId()
        return try {
            articleRepository.updateCurationStatus(articleId, CurationStatus.APPROVED, userId)
            logAuditEvent(userId, AuditAction.ARTICLE_APPROVED, "article", articleId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reject(articleId: String, reason: String? = null): Result<Unit> {
        roleGuard.enforceCurationAccess()
        val userId = sessionManager.requireUserId()
        return try {
            articleRepository.updateCurationStatus(articleId, CurationStatus.REJECTED, userId)
            logAuditEvent(userId, AuditAction.ARTICLE_REJECTED, "article", articleId, reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFeatured(articleId: String, featured: Boolean): Result<Unit> {
        roleGuard.enforceCurationAccess()
        val userId = sessionManager.requireUserId()
        return try {
            articleRepository.setFeatured(articleId, featured)
            if (featured) {
                articleRepository.updateCurationStatus(articleId, CurationStatus.FEATURED, userId)
            }
            logAuditEvent(
                userId,
                if (featured) AuditAction.ARTICLE_FEATURED else AuditAction.ARTICLE_UNFEATURED,
                "article", articleId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTags(
        articleId: String, team: String?, league: String?, topic: String?
    ): Result<Unit> {
        roleGuard.enforceCurationAccess()
        val userId = sessionManager.requireUserId()
        return try {
            articleRepository.updateTags(articleId, team, league, topic)
            logAuditEvent(
                userId, AuditAction.ARTICLE_TAGS_UPDATED, "article", articleId,
                "team=$team, league=$league, topic=$topic"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
