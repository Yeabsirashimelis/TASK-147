package com.eaglepoint.storefront.ui.saved

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.usecase.SaveArticleOfflineUseCase
import kotlinx.coroutines.launch

class SavedArticlesViewModel(
    private val saveArticleOfflineUseCase: SaveArticleOfflineUseCase
) : ViewModel() {

    val savedArticles: LiveData<List<Article>> =
        saveArticleOfflineUseCase.getSavedArticles().asLiveData()

    fun unsaveArticle(articleId: String) {
        viewModelScope.launch {
            saveArticleOfflineUseCase.unsave(articleId)
        }
    }
}
