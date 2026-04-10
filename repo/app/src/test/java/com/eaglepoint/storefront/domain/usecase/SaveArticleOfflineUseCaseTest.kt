package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveArticleOfflineUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var saveUseCase: SaveArticleOfflineUseCase
    private val now = System.currentTimeMillis()

    private fun testArticle(saved: Boolean = false) = Article(
        id = "article-1", sourceRuleId = "rule-1", title = "Test",
        externalUrl = "https://example.com/1",
        isSavedOffline = saved,
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        saveUseCase = SaveArticleOfflineUseCase(articleRepository)
    }

    @Test
    fun `save calls repository saveForOffline`() = runTest {
        saveUseCase.save("article-1")
        coVerify { articleRepository.saveForOffline("article-1") }
    }

    @Test
    fun `unsave calls repository unsaveFromOffline`() = runTest {
        saveUseCase.unsave("article-1")
        coVerify { articleRepository.unsaveFromOffline("article-1") }
    }

    @Test
    fun `toggleSave saves unsaved article`() = runTest {
        val article = testArticle(saved = false)
        saveUseCase.toggleSave(article)
        coVerify { articleRepository.saveForOffline("article-1") }
    }

    @Test
    fun `toggleSave unsaves saved article`() = runTest {
        val article = testArticle(saved = true)
        saveUseCase.toggleSave(article)
        coVerify { articleRepository.unsaveFromOffline("article-1") }
    }
}
