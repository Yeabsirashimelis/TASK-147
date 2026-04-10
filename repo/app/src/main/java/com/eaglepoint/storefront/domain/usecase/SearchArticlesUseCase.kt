package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.ArticleSearchQuery
import kotlinx.coroutines.flow.Flow

class SearchArticlesUseCase(
    private val articleRepository: ArticleRepository
) {

    fun search(query: ArticleSearchQuery, limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleRepository.search(query, limit, offset)
    }

    fun getPage(limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleRepository.getPage(limit, offset)
    }

    fun searchByKeyword(keyword: String, limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        return articleRepository.searchByKeyword(keyword, limit, offset)
    }

    fun getDistinctTeams(): Flow<List<String>> = articleRepository.getDistinctTeams()

    fun getDistinctLeagues(): Flow<List<String>> = articleRepository.getDistinctLeagues()
}
