package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.ParsedFeedItem
import com.eaglepoint.storefront.domain.model.SourceRule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContentFilterTest {

    private lateinit var filter: ContentFilter
    private val now = System.currentTimeMillis()

    private val testItems = listOf(
        ParsedFeedItem(title = "NBA Finals Game 5", url = "https://sports.example.com/nba-finals", summary = "Lakers vs Celtics game 5 preview"),
        ParsedFeedItem(title = "Weather Update", url = "https://weather.example.com/forecast", summary = "Rain expected tomorrow"),
        ParsedFeedItem(title = "Soccer Transfer News", url = "https://football.example.org/transfers", summary = "Transfer window update"),
        ParsedFeedItem(title = "Casino Bonus Offer", url = "https://spam.example.net/bonus", summary = "Free casino bonus chips")
    )

    @BeforeEach
    fun setUp() {
        filter = ContentFilter()
    }

    private fun baseRule(
        allowedDomains: List<String> = emptyList(),
        blockedDomains: List<String> = emptyList(),
        allowedKeywords: List<String> = emptyList(),
        blockedKeywords: List<String> = emptyList()
    ): SourceRule {
        return SourceRule(
            id = "rule-1", name = "Test", url = "https://test.com",
            feedType = FeedType.RSS,
            allowedDomains = allowedDomains,
            blockedDomains = blockedDomains,
            allowedKeywords = allowedKeywords,
            blockedKeywords = blockedKeywords,
            createdAt = now, updatedAt = now
        )
    }

    @Test
    fun `no filters passes all items`() {
        val result = filter.filter(testItems, baseRule())
        assertThat(result).hasSize(4)
    }

    @Test
    fun `allowed domains filters to matching domains only`() {
        val rule = baseRule(allowedDomains = listOf("example.com"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(2)
        assertThat(result.map { it.title }).containsExactly("NBA Finals Game 5", "Weather Update")
    }

    @Test
    fun `blocked domains excludes matching domains`() {
        val rule = baseRule(blockedDomains = listOf("spam.example.net"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(3)
        assertThat(result.none { it.title == "Casino Bonus Offer" }).isTrue()
    }

    @Test
    fun `allowed keywords filters by title and summary content`() {
        val rule = baseRule(allowedKeywords = listOf("nba", "soccer"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(2)
        assertThat(result.map { it.title }).containsExactly("NBA Finals Game 5", "Soccer Transfer News")
    }

    @Test
    fun `blocked keywords excludes matching content`() {
        val rule = baseRule(blockedKeywords = listOf("casino", "bonus"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(3)
        assertThat(result.none { it.title == "Casino Bonus Offer" }).isTrue()
    }

    @Test
    fun `combined filters apply in sequence`() {
        val rule = baseRule(
            allowedDomains = listOf("example.com", "example.org"),
            blockedKeywords = listOf("weather")
        )
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(2)
        assertThat(result.map { it.title }).containsExactly("NBA Finals Game 5", "Soccer Transfer News")
    }

    @Test
    fun `keyword matching is case insensitive`() {
        val rule = baseRule(allowedKeywords = listOf("NBA"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("NBA Finals Game 5")
    }

    @Test
    fun `subdomain matching works for allowed domains`() {
        val rule = baseRule(allowedDomains = listOf("example.org"))
        val result = filter.filter(testItems, rule)
        assertThat(result).hasSize(1)
        assertThat(result[0].url).contains("example.org")
    }
}
