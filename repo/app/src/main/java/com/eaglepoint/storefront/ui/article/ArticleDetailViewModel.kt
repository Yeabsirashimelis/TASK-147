package com.eaglepoint.storefront.ui.article

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.usecase.GetArticleDetailUseCase
import com.eaglepoint.storefront.domain.usecase.SaveArticleOfflineUseCase
import kotlinx.coroutines.launch

class ArticleDetailViewModel(
    private val getArticleDetailUseCase: GetArticleDetailUseCase,
    private val saveArticleOfflineUseCase: SaveArticleOfflineUseCase
) : ViewModel() {

    private val _article = MutableLiveData<Article?>()
    val article: LiveData<Article?> = _article

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadArticle(articleId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = getArticleDetailUseCase(articleId)
            _article.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun toggleSave() {
        val current = _article.value ?: return
        viewModelScope.launch {
            saveArticleOfflineUseCase.toggleSave(current)
            // Reload to get updated save state
            val updated = getArticleDetailUseCase(current.id)
            _article.postValue(updated)
        }
    }
}
