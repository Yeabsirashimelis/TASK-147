package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.domain.model.FeedType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HtmlScrapeParserTest {

    private lateinit var parser: FeedParser

    @BeforeEach
    fun setUp() {
        parser = FeedParser()
    }

    @Test
    fun `HTML_SCRAPE extracts items using default selectors`() {
        val html = """
            <html><body>
                <article>
                    <h2>Game Highlights</h2>
                    <a href="https://example.com/game1">Read more</a>
                    <p>Great game last night with dramatic finish</p>
                </article>
                <article>
                    <h2>Trade Deadline</h2>
                    <a href="https://example.com/trade">Details</a>
                    <p>Several major trades completed</p>
                </article>
            </body></html>
        """.trimIndent()

        val items = parser.parse(html, FeedType.HTML_SCRAPE)

        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("Game Highlights")
        assertThat(items[0].url).isEqualTo("https://example.com/game1")
        assertThat(items[0].summary).isEqualTo("Great game last night with dramatic finish")
        assertThat(items[1].title).isEqualTo("Trade Deadline")
        assertThat(items[1].url).isEqualTo("https://example.com/trade")
    }

    @Test
    fun `HTML_SCRAPE extracts items with custom selectors`() {
        val html = """
            <html><body>
                <div class="news-item">
                    <h3>Breaking News</h3>
                    <a href="https://example.com/breaking">Link</a>
                    <span class="desc">Important update on the season</span>
                </div>
                <div class="news-item">
                    <h3>Scores</h3>
                    <a href="https://example.com/scores">Link</a>
                    <span class="desc">Final scores from all games</span>
                </div>
            </body></html>
        """.trimIndent()

        val items = parser.parse(
            html, FeedType.HTML_SCRAPE,
            "container=div.news-item;title=h3;url=a;summary=span.desc"
        )

        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("Breaking News")
        assertThat(items[0].url).isEqualTo("https://example.com/breaking")
        assertThat(items[0].summary).isEqualTo("Important update on the season")
    }

    @Test
    fun `HTML_SCRAPE falls back to links when no containers found`() {
        val html = """
            <html><body>
                <a href="https://example.com/story1">First Story</a>
                <a href="https://example.com/story2">Second Story</a>
                <a href="/relative-link">Relative Link</a>
            </body></html>
        """.trimIndent()

        val items = parser.parse(html, FeedType.HTML_SCRAPE)

        assertThat(items).hasSize(2) // relative link excluded
        assertThat(items[0].title).isEqualTo("First Story")
        assertThat(items[0].url).isEqualTo("https://example.com/story1")
    }

    @Test
    fun `HTML_SCRAPE returns empty list for empty page`() {
        val items = parser.parse("<html><body></body></html>", FeedType.HTML_SCRAPE)
        assertThat(items).isEmpty()
    }

    @Test
    fun `HTML_SCRAPE handles malformed HTML gracefully`() {
        val html = "<div><h2>Title<a href='url'>link</div>"

        val items = parser.parse(html, FeedType.HTML_SCRAPE)
        // Should not throw, may return empty or partial
        assertThat(items).isNotNull()
    }

    @Test
    fun `HTML_SCRAPE skips items without title or url`() {
        val html = """
            <html><body>
                <article>
                    <h2></h2>
                    <a href="https://example.com/no-title">link</a>
                </article>
                <article>
                    <h2>Has Title</h2>
                    <a href="https://example.com/valid">link</a>
                    <p>Summary</p>
                </article>
            </body></html>
        """.trimIndent()

        val items = parser.parse(html, FeedType.HTML_SCRAPE)

        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Has Title")
    }

    @Test
    fun `HTML_SCRAPE throws FeedParseException for null content`() {
        assertThrows<FeedParseException> {
            parser.parseHtmlScrape(null.toString(), null)
        }.also {
            // Should get a parse exception, not NPE
        }
    }

    @Test
    fun `extractElements finds elements by tag and class`() {
        val html = """<div class="item">one</div><div class="other">two</div><div class="item">three</div>"""
        val elements = parser.extractElements(html, "div.item")
        assertThat(elements).hasSize(2)
    }

    @Test
    fun `extractFirstHref finds href attribute`() {
        val html = """<a href="https://example.com/test">click</a>"""
        val href = parser.extractFirstHref(html, "a")
        assertThat(href).isEqualTo("https://example.com/test")
    }
}
