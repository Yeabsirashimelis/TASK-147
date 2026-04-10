package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    @Query("SELECT * FROM articles ORDER BY published_at DESC LIMIT :limit OFFSET :offset")
    fun getPage(limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE source_rule_id = :sourceRuleId ORDER BY published_at DESC")
    fun getBySourceRule(sourceRuleId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ArticleEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE external_url = :url)")
    suspend fun existsByUrl(url: String): Boolean

    @Query("SELECT COUNT(*) FROM articles WHERE source_rule_id = :sourceRuleId")
    suspend fun countBySourceRule(sourceRuleId: String): Int

    // Composite-index search: sourceId + publishedAt for sub-50ms on 100k rows
    @Query("""
        SELECT * FROM articles
        WHERE source_rule_id = :sourceRuleId
        AND published_at BETWEEN :dateFrom AND :dateTo
        ORDER BY published_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getBySourceAndDateRange(
        sourceRuleId: String, dateFrom: Long, dateTo: Long,
        limit: Int, offset: Int
    ): Flow<List<ArticleEntity>>

    // Keyword search on title and summary
    @Query("""
        SELECT * FROM articles
        WHERE (title LIKE '%' || :keyword || '%' OR summary LIKE '%' || :keyword || '%')
        ORDER BY published_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchByKeyword(keyword: String, limit: Int, offset: Int): Flow<List<ArticleEntity>>

    // Full composite filter
    @Query("""
        SELECT * FROM articles
        WHERE (:sourceRuleId IS NULL OR source_rule_id = :sourceRuleId)
        AND (:team IS NULL OR team = :team)
        AND (:league IS NULL OR league = :league)
        AND (:dateFrom IS NULL OR published_at >= :dateFrom)
        AND (:dateTo IS NULL OR published_at <= :dateTo)
        AND (:keyword IS NULL OR title LIKE '%' || :keyword || '%' OR summary LIKE '%' || :keyword || '%')
        ORDER BY published_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchFiltered(
        keyword: String?,
        sourceRuleId: String?,
        team: String?,
        league: String?,
        dateFrom: Long?,
        dateTo: Long?,
        limit: Int,
        offset: Int
    ): Flow<List<ArticleEntity>>

    // Saved articles
    @Query("SELECT * FROM articles WHERE is_saved_offline = 1 ORDER BY saved_at DESC")
    fun getSavedArticles(): Flow<List<ArticleEntity>>

    @Query("UPDATE articles SET is_saved_offline = 1, saved_at = :savedAt WHERE id = :id")
    suspend fun saveForOffline(id: String, savedAt: Long)

    @Query("UPDATE articles SET is_saved_offline = 0, saved_at = NULL WHERE id = :id")
    suspend fun unsaveFromOffline(id: String)

    @Query("SELECT COUNT(*) FROM articles WHERE is_saved_offline = 1")
    fun getSavedCount(): Flow<Int>

    // Distinct values for filter chips
    @Query("SELECT DISTINCT team FROM articles WHERE team IS NOT NULL ORDER BY team ASC")
    fun getDistinctTeams(): Flow<List<String>>

    @Query("SELECT DISTINCT league FROM articles WHERE league IS NOT NULL ORDER BY league ASC")
    fun getDistinctLeagues(): Flow<List<String>>

    // Curation queries
    @Query("SELECT * FROM articles WHERE curation_status = :status ORDER BY published_at DESC LIMIT :limit OFFSET :offset")
    fun getByCurationStatus(status: String, limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE is_featured = 1 ORDER BY published_at DESC")
    fun getFeatured(): Flow<List<ArticleEntity>>

    @Query("UPDATE articles SET curation_status = :status, curated_by = :curatedBy, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCurationStatus(id: String, status: String, curatedBy: String, updatedAt: Long)

    @Query("UPDATE articles SET is_featured = :featured, updated_at = :updatedAt WHERE id = :id")
    suspend fun setFeatured(id: String, featured: Boolean, updatedAt: Long)

    @Query("UPDATE articles SET team = :team, league = :league, topic = :topic, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTags(id: String, team: String?, league: String?, topic: String?, updatedAt: Long)

    @Query("SELECT DISTINCT topic FROM articles WHERE topic IS NOT NULL ORDER BY topic ASC")
    fun getDistinctTopics(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM articles WHERE curation_status = 'PENDING_REVIEW'")
    fun getPendingReviewCount(): Flow<Int>
}
