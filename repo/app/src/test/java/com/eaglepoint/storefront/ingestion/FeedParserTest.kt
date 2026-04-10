package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.domain.model.FeedType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FeedParserTest {

    private lateinit var parser: FeedParser

    @BeforeEach
    fun setUp() {
        parser = FeedParser()
    }

    @Test
    fun `parseRss extracts items from valid RSS`() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Sports News</title>
                    <item>
                        <title>Game Results</title>
                        <link>https://example.com/game</link>
                        <description>Final scores from tonight</description>
                        <author>John Doe</author>
                        <pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate>
                    </item>
                    <item>
                        <title>Transfer News</title>
                        <link>https://example.com/transfers</link>
                        <description>Latest transfer updates</description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val items = parser.parse(rss, FeedType.RSS)

        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("Game Results")
        assertThat(items[0].url).isEqualTo("https://example.com/game")
        assertThat(items[0].summary).isEqualTo("Final scores from tonight")
        assertThat(items[0].author).isEqualTo("John Doe")
        assertThat(items[0].publishedAt).isNotNull()
        assertThat(items[1].title).isEqualTo("Transfer News")
    }

    @Test
    fun `parseRss skips items without title or link`() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <item>
                        <title></title>
                        <link>https://example.com/empty</link>
                    </item>
                    <item>
                        <title>Valid Item</title>
                        <link>https://example.com/valid</link>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val items = parser.parse(rss, FeedType.RSS)
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Valid Item")
    }

    @Test
    fun `parseAtom extracts entries from valid Atom feed`() {
        val atom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Sports Feed</title>
                <entry>
                    <title>Match Report</title>
                    <link href="https://example.com/match" />
                    <summary>Detailed match analysis</summary>
                    <author><name>Jane Smith</name></author>
                    <updated>2024-01-15T10:30:00Z</updated>
                </entry>
            </feed>
        """.trimIndent()

        val items = parser.parse(atom, FeedType.ATOM)

        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Match Report")
        assertThat(items[0].url).isEqualTo("https://example.com/match")
        assertThat(items[0].summary).isEqualTo("Detailed match analysis")
        assertThat(items[0].author).isEqualTo("Jane Smith")
    }

    @Test
    fun `parseRss throws FeedParseException for invalid XML`() {
        val invalid = "not xml at all <broken"

        assertThrows<FeedParseException> {
            parser.parse(invalid, FeedType.RSS)
        }
    }

    @Test
    fun `parseAtom throws FeedParseException for invalid XML`() {
        assertThrows<FeedParseException> {
            parser.parse("<invalid>", FeedType.ATOM)
        }
    }

    @Test
    fun `HTML_SCRAPE parses articles from HTML page`() {
        val html = """
            <html><body>
                <article>
                    <h2>Story One</h2>
                    <a href="https://example.com/one">Read</a>
                    <p>Summary of story one</p>
                </article>
            </body></html>
        """.trimIndent()

        val items = parser.parse(html, FeedType.HTML_SCRAPE)
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Story One")
        assertThat(items[0].url).isEqualTo("https://example.com/one")
    }

    @Test
    fun `parseRss handles empty channel`() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Empty</title>
                </channel>
            </rss>
        """.trimIndent()

        val items = parser.parse(rss, FeedType.RSS)
        assertThat(items).isEmpty()
    }
}
