package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetArticleDetailUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var getArticleDetailUseCase: GetArticleDetailUseCase
    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        getArticleDetailUseCase = GetArticleDetailUseCase(articleRepository)
    }

    @Test
    fun `returns article when found`() = runTest {
        val article = Article(
            id = "a1", sourceRuleId = "rule-1", title = "Test Article",
            content = "Full article content here",
            externalUrl = "https://example.com/a1",
            createdAt = now, updatedAt = now
        )
        coEvery { articleRepository.findById("a1") } returns article

        val result = getArticleDetailUseCase("a1")

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Test Article")
        assertThat(result.content).isEqualTo("Full article content here")
    }

    @Test
    fun `returns null when article not found`() = runTest {
        coEvery { articleRepository.findById("nonexistent") } returns null

        val result = getArticleDetailUseCase("nonexistent")

        assertThat(result).isNull()
    }

    @Test
    fun `returns article with offline save state`() = runTest {
        val article = Article(
            id = "a1", sourceRuleId = "rule-1", title = "Saved Article",
            externalUrl = "https://example.com/a1",
            isSavedOffline = true, savedAt = now - 1000,
            createdAt = now, updatedAt = now
        )
        coEvery { articleRepository.findById("a1") } returns article

        val result = getArticleDetailUseCase("a1")

        assertThat(result!!.isSavedOffline).isTrue()
        assertThat(result.savedAt).isNotNull()
    }
}
