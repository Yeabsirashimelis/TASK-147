package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article

class GetArticleDetailUseCase(
    private val articleRepository: ArticleRepository
) {

    suspend operator fun invoke(articleId: String): Article? {
        return articleRepository.findById(articleId)
    }
}
