package com.eaglepoint.storefront.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.ArticleSearchQuery
import com.eaglepoint.storefront.domain.usecase.SaveArticleOfflineUseCase
import com.eaglepoint.storefront.domain.usecase.SearchArticlesUseCase
import kotlinx.coroutines.launch

class HomeViewModel(
    private val searchArticlesUseCase: SearchArticlesUseCase,
    private val saveArticleOfflineUseCase: SaveArticleOfflineUseCase
) : ViewModel() {

    private val _searchQuery = MutableLiveData(ArticleSearchQuery())
    val searchQuery: LiveData<ArticleSearchQuery> = _searchQuery

    private val _currentPage = MutableLiveData(0)

    val articles: LiveData<List<Article>> = _searchQuery.switchMap { query ->
        val hasFilters = query.keyword != null || query.sourceRuleId != null ||
                query.team != null || query.league != null ||
                query.dateFrom != null || query.dateTo != null

        if (hasFilters) {
            searchArticlesUseCase.search(query, PAGE_SIZE, 0).asLiveData()
        } else {
            searchArticlesUseCase.getPage(PAGE_SIZE, 0).asLiveData()
        }
    }

    val teams: LiveData<List<String>> = searchArticlesUseCase.getDistinctTeams().asLiveData()
    val leagues: LiveData<List<String>> = searchArticlesUseCase.getDistinctLeagues().asLiveData()
    val savedCount: LiveData<Int> = saveArticleOfflineUseCase.getSavedCount().asLiveData()

    fun setKeyword(keyword: String?) {
        _searchQuery.value = _searchQuery.value?.copy(keyword = keyword?.ifBlank { null })
    }

    fun setSourceFilter(sourceRuleId: String?) {
        _searchQuery.value = _searchQuery.value?.copy(sourceRuleId = sourceRuleId)
    }

    fun setTeamFilter(team: String?) {
        _searchQuery.value = _searchQuery.value?.copy(team = team)
    }

    fun setLeagueFilter(league: String?) {
        _searchQuery.value = _searchQuery.value?.copy(league = league)
    }

    fun setDateRange(from: Long?, to: Long?) {
        _searchQuery.value = _searchQuery.value?.copy(dateFrom = from, dateTo = to)
    }

    fun clearFilters() {
        _searchQuery.value = ArticleSearchQuery()
    }

    fun toggleSaveArticle(article: Article) {
        viewModelScope.launch {
            saveArticleOfflineUseCase.toggleSave(article)
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
