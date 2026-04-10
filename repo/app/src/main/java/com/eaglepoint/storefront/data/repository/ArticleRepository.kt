package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.ArticleDao
import com.eaglepoint.storefront.data.db.entity.ArticleEntity
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.ArticleSearchQuery
import com.eaglepoint.storefront.domain.model.CurationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun insertAll(articles: List<Article>): Int = withContext(dispatcher) {
        val entities = articles.map { domainToEntity(it) }
        val results = articleDao.insertAll(entities)
        results.count { it != -1L }
    }

    fun getPage(limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleDao.getPage(limit, offset).map { it.map(::entityToDomain) }
    }

    fun getBySourceRule(sourceRuleId: String): Flow<List<Article>> {
        return articleDao.getBySourceRule(sourceRuleId).map { it.map(::entityToDomain) }
    }

    suspend fun findById(id: String): Article? = withContext(dispatcher) {
        articleDao.findById(id)?.let(::entityToDomain)
    }

    suspend fun existsByUrl(url: String): Boolean = withContext(dispatcher) {
        articleDao.existsByUrl(url)
    }

    suspend fun countBySourceRule(sourceRuleId: String): Int = withContext(dispatcher) {
        articleDao.countBySourceRule(sourceRuleId)
    }

    fun search(query: ArticleSearchQuery, limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleDao.searchFiltered(
            keyword = query.keyword,
            sourceRuleId = query.sourceRuleId,
            team = query.team,
            league = query.league,
            dateFrom = query.dateFrom,
            dateTo = query.dateTo,
            limit = limit,
            offset = offset
        ).map { it.map(::entityToDomain) }
    }

    fun searchByKeyword(keyword: String, limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleDao.searchByKeyword(keyword, limit, offset).map { it.map(::entityToDomain) }
    }

    fun getBySourceAndDateRange(
        sourceRuleId: String, dateFrom: Long, dateTo: Long,
        limit: Int = 50, offset: Int = 0
    ): Flow<List<Article>> {
        return articleDao.getBySourceAndDateRange(sourceRuleId, dateFrom, dateTo, limit, offset)
            .map { it.map(::entityToDomain) }
    }

    // Saved / offline
    fun getSavedArticles(): Flow<List<Article>> {
        return articleDao.getSavedArticles().map { it.map(::entityToDomain) }
    }

    suspend fun saveForOffline(id: String) = withContext(dispatcher) {
        articleDao.saveForOffline(id, System.currentTimeMillis())
    }

    suspend fun unsaveFromOffline(id: String) = withContext(dispatcher) {
        articleDao.unsaveFromOffline(id)
    }

    fun getSavedCount(): Flow<Int> = articleDao.getSavedCount()

    fun getDistinctTeams(): Flow<List<String>> = articleDao.getDistinctTeams()

    fun getDistinctLeagues(): Flow<List<String>> = articleDao.getDistinctLeagues()

    // Curation
    fun getByCurationStatus(status: CurationStatus, limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleDao.getByCurationStatus(status.name, limit, offset).map { it.map(::entityToDomain) }
    }

    fun getFeatured(): Flow<List<Article>> {
        return articleDao.getFeatured().map { it.map(::entityToDomain) }
    }

    suspend fun updateCurationStatus(id: String, status: CurationStatus, curatedBy: String) =
        withContext(dispatcher) {
            articleDao.updateCurationStatus(id, status.name, curatedBy, System.currentTimeMillis())
        }

    suspend fun setFeatured(id: String, featured: Boolean) = withContext(dispatcher) {
        articleDao.setFeatured(id, featured, System.currentTimeMillis())
    }

    suspend fun updateTags(id: String, team: String?, league: String?, topic: String?) =
        withContext(dispatcher) {
            articleDao.updateTags(id, team, league, topic, System.currentTimeMillis())
        }

    fun getDistinctTopics(): Flow<List<String>> = articleDao.getDistinctTopics()

    fun getPendingReviewCount(): Flow<Int> = articleDao.getPendingReviewCount()

    private fun entityToDomain(entity: ArticleEntity): Article {
        return Article(
            id = entity.id,
            sourceRuleId = entity.sourceRuleId,
            batchVersionId = entity.batchVersionId,
            title = entity.title,
            summary = entity.summary,
            content = entity.content,
            author = entity.author,
            externalUrl = entity.externalUrl,
            imageUrl = entity.imageUrl,
            team = entity.team,
            league = entity.league,
            isSavedOffline = entity.isSavedOffline,
            savedAt = entity.savedAt,
            curationStatus = try { CurationStatus.valueOf(entity.curationStatus) } catch (e: Exception) { CurationStatus.PENDING_REVIEW },
            curatedBy = entity.curatedBy,
            isFeatured = entity.isFeatured,
            topic = entity.topic,
            publishedAt = entity.publishedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun domainToEntity(article: Article): ArticleEntity {
        return ArticleEntity(
            id = article.id,
            sourceRuleId = article.sourceRuleId,
            batchVersionId = article.batchVersionId,
            title = article.title,
            summary = article.summary,
            content = article.content,
            author = article.author,
            externalUrl = article.externalUrl,
            imageUrl = article.imageUrl,
            team = article.team,
            league = article.league,
            isSavedOffline = article.isSavedOffline,
            savedAt = article.savedAt,
            curationStatus = article.curationStatus.name,
            curatedBy = article.curatedBy,
            isFeatured = article.isFeatured,
            topic = article.topic,
            publishedAt = article.publishedAt,
            createdAt = article.createdAt,
            updatedAt = article.updatedAt
        )
    }
}
