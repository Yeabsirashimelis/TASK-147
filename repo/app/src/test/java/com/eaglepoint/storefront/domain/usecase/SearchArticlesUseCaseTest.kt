package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.ArticleSearchQuery
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchArticlesUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var searchUseCase: SearchArticlesUseCase
    private val now = System.currentTimeMillis()

    private fun testArticle(
        id: String,
        title: String = "Test Article",
        team: String? = null,
        league: String? = null
    ) = Article(
        id = id, sourceRuleId = "rule-1", title = title,
        externalUrl = "https://example.com/$id",
        team = team, league = league,
        publishedAt = now, createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        searchUseCase = SearchArticlesUseCase(articleRepository)
    }

    @Test
    fun `search delegates to repository with correct query`() = runTest {
        val query = ArticleSearchQuery(keyword = "NBA", team = "Lakers")
        every { articleRepository.search(query, 50, 0) } returns flowOf(
            listOf(testArticle("a1", title = "NBA Finals", team = "Lakers"))
        )

        val results = searchUseCase.search(query).first()

        assertThat(results).hasSize(1)
        assertThat(results[0].team).isEqualTo("Lakers")
        verify { articleRepository.search(query, 50, 0) }
    }

    @Test
    fun `getPage returns paginated articles`() = runTest {
        val articles = (1..5).map { testArticle("a$it") }
        every { articleRepository.getPage(50, 0) } returns flowOf(articles)

        val results = searchUseCase.getPage().first()

        assertThat(results).hasSize(5)
    }

    @Test
    fun `searchByKeyword delegates to repository`() = runTest {
        every { articleRepository.searchByKeyword("soccer", 20, 0) } returns flowOf(
            listOf(testArticle("a1", title = "Soccer Match"))
        )

        val results = searchUseCase.searchByKeyword("soccer", 20).first()

        assertThat(results).hasSize(1)
        assertThat(results[0].title).contains("Soccer")
    }

    @Test
    fun `getDistinctTeams returns unique teams`() = runTest {
        every { articleRepository.getDistinctTeams() } returns flowOf(
            listOf("Lakers", "Celtics", "Warriors")
        )

        val teams = searchUseCase.getDistinctTeams().first()

        assertThat(teams).hasSize(3)
        assertThat(teams).contains("Lakers")
    }

    @Test
    fun `getDistinctLeagues returns unique leagues`() = runTest {
        every { articleRepository.getDistinctLeagues() } returns flowOf(
            listOf("NBA", "NFL", "Premier League")
        )

        val leagues = searchUseCase.getDistinctLeagues().first()

        assertThat(leagues).hasSize(3)
    }
}
