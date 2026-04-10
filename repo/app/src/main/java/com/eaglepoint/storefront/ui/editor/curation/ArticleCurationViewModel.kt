package com.eaglepoint.storefront.ui.editor.curation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.CurationStatus
import com.eaglepoint.storefront.domain.usecase.CurateArticleUseCase
import com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCase
import kotlinx.coroutines.launch

class ArticleCurationViewModel(
    private val curateArticleUseCase: CurateArticleUseCase,
    private val reviewIngestionUseCase: ReviewIngestionUseCase
) : ViewModel() {

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun getPendingArticles(): LiveData<List<Article>> {
        return reviewIngestionUseCase.getPendingArticles().asLiveData()
    }

    fun approve(articleId: String) {
        viewModelScope.launch {
            curateArticleUseCase.approve(articleId)
                .onFailure { _error.postValue(it.message) }
        }
    }

    fun reject(articleId: String, reason: String? = null) {
        viewModelScope.launch {
            curateArticleUseCase.reject(articleId, reason)
                .onFailure { _error.postValue(it.message) }
        }
    }

    fun setFeatured(articleId: String, featured: Boolean) {
        viewModelScope.launch {
            curateArticleUseCase.setFeatured(articleId, featured)
                .onFailure { _error.postValue(it.message) }
        }
    }

    fun updateTags(articleId: String, team: String?, league: String?, topic: String?) {
        viewModelScope.launch {
            curateArticleUseCase.updateTags(articleId, team, league, topic)
                .onFailure { _error.postValue(it.message) }
        }
    }
}
