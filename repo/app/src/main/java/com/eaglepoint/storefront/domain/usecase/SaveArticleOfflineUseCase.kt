package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article
import kotlinx.coroutines.flow.Flow

class SaveArticleOfflineUseCase(
    private val articleRepository: ArticleRepository
) {

    suspend fun save(articleId: String) {
        articleRepository.saveForOffline(articleId)
    }

    suspend fun unsave(articleId: String) {
        articleRepository.unsaveFromOffline(articleId)
    }

    suspend fun toggleSave(article: Article) {
        if (article.isSavedOffline) {
            articleRepository.unsaveFromOffline(article.id)
        } else {
            articleRepository.saveForOffline(article.id)
        }
    }

    fun getSavedArticles(): Flow<List<Article>> {
        return articleRepository.getSavedArticles()
    }

    fun getSavedCount(): Flow<Int> {
        return articleRepository.getSavedCount()
    }
}
