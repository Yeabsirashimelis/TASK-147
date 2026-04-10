package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.ParsedFeedItem
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

class FeedParser {

    fun parse(content: String, feedType: FeedType, parseSelector: String? = null): List<ParsedFeedItem> {
        return when (feedType) {
            FeedType.RSS -> parseRss(content)
            FeedType.ATOM -> parseAtom(content)
            FeedType.HTML_SCRAPE -> parseHtmlScrape(content, parseSelector)
        }
    }

    private fun parseRss(content: String): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            // Disable external entities for security
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(content)))

            val itemNodes = doc.getElementsByTagName("item")
            for (i in 0 until itemNodes.length) {
                val node = itemNodes.item(i)
                val children = node.childNodes

                var title = ""
                var link = ""
                var description: String? = null
                var author: String? = null
                var pubDate: String? = null

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    when (child.nodeName) {
                        "title" -> title = child.textContent?.trim() ?: ""
                        "link" -> link = child.textContent?.trim() ?: ""
                        "description" -> description = child.textContent?.trim()
                        "author", "dc:creator" -> author = child.textContent?.trim()
                        "pubDate" -> pubDate = child.textContent?.trim()
                    }
                }

                if (title.isNotBlank() && link.isNotBlank()) {
                    items.add(
                        ParsedFeedItem(
                            title = title,
                            summary = description,
                            url = link,
                            author = author,
                            publishedAt = pubDate?.let { parseDate(it) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw FeedParseException("Failed to parse RSS feed: ${e.message}", e)
        }
        return items
    }

    private fun parseAtom(content: String): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(content)))

            val entryNodes = doc.getElementsByTagName("entry")
            for (i in 0 until entryNodes.length) {
                val node = entryNodes.item(i)
                val children = node.childNodes

                var title = ""
                var link = ""
                var summary: String? = null
                var author: String? = null
                var updated: String? = null

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    when (child.localName ?: child.nodeName) {
                        "title" -> title = child.textContent?.trim() ?: ""
                        "link" -> {
                            val href = child.attributes?.getNamedItem("href")?.textContent
                            if (href != null) link = href.trim()
                            else if (link.isBlank()) link = child.textContent?.trim() ?: ""
                        }
                        "summary", "content" -> {
                            if (summary == null) summary = child.textContent?.trim()
                        }
                        "author" -> {
                            val nameNode = child.childNodes
                            for (k in 0 until nameNode.length) {
                                if ((nameNode.item(k).localName ?: nameNode.item(k).nodeName) == "name") {
                                    author = nameNode.item(k).textContent?.trim()
                                }
                            }
                        }
                        "updated", "published" -> {
                            if (updated == null) updated = child.textContent?.trim()
                        }
                    }
                }

                if (title.isNotBlank() && link.isNotBlank()) {
                    items.add(
                        ParsedFeedItem(
                            title = title,
                            summary = summary,
                            url = link,
                            author = author,
                            publishedAt = updated?.let { parseDate(it) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw FeedParseException("Failed to parse Atom feed: ${e.message}", e)
        }
        return items
    }

    /**
     * Parse HTML content using CSS-like selectors specified in parseSelector.
     *
     * Selector format: "container=<selector>;title=<selector>;url=<selector>;summary=<selector>;date=<selector>"
     * Each selector is a simple tag[.class] pattern.
     *
     * If no parseSelector is given, falls back to extracting all anchor tags
     * that have both href and text content as minimal items.
     */
    internal fun parseHtmlScrape(content: String, parseSelector: String?): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        try {
            val selectors = parseSelectorConfig(parseSelector)

            val containerTag = selectors["container"] ?: "article"
            val titleTag = selectors["title"] ?: "h2"
            val urlTag = selectors["url"] ?: "a"
            val summaryTag = selectors["summary"] ?: "p"
            val dateTag = selectors["date"]

            // Extract container blocks
            val containers = extractElements(content, containerTag)

            if (containers.isEmpty()) {
                // Fallback: try to extract links as items directly
                val fallbackItems = extractLinksAsFeedItems(content)
                return fallbackItems
            }

            for (containerHtml in containers) {
                val title = extractFirstTextContent(containerHtml, titleTag)
                val url = extractFirstHref(containerHtml, urlTag)
                    ?: extractFirstHref(containerHtml, "a")
                val summary = extractFirstTextContent(containerHtml, summaryTag)
                val dateStr = if (dateTag != null) extractFirstTextContent(containerHtml, dateTag) else null

                if (!title.isNullOrBlank() && !url.isNullOrBlank()) {
                    items.add(
                        ParsedFeedItem(
                            title = title.trim(),
                            summary = summary?.trim(),
                            url = url.trim(),
                            publishedAt = dateStr?.let { parseDate(it) }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw FeedParseException("Failed to parse HTML scrape: ${e.message}", e)
        }
        return items
    }

    private fun parseSelectorConfig(selector: String?): Map<String, String> {
        if (selector.isNullOrBlank()) return emptyMap()
        return selector.split(";").mapNotNull { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank() && kv[1].isNotBlank()) {
                kv[0].trim() to kv[1].trim()
            } else null
        }.toMap()
    }

    /**
     * Extract HTML elements matching a simple tag or tag.class selector.
     * Returns the inner HTML of each matching element.
     */
    internal fun extractElements(html: String, selector: String): List<String> {
        val (tag, className) = parseCssSelector(selector)
        val results = mutableListOf<String>()

        val pattern = if (className != null) {
            // Match tag with class attribute containing the class name
            Pattern.compile(
                "<$tag\\b[^>]*class\\s*=\\s*[\"'][^\"']*\\b${Pattern.quote(className)}\\b[^\"']*[\"'][^>]*>(.*?)</$tag>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
        } else {
            Pattern.compile(
                "<$tag\\b[^>]*>(.*?)</$tag>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
        }

        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            // Group 0 = full match including outer tag, which we want for nested parsing
            results.add(matcher.group(0) ?: "")
        }
        return results
    }

    private fun parseCssSelector(selector: String): Pair<String, String?> {
        val parts = selector.split(".", limit = 2)
        val tag = parts[0].trim()
        val className = if (parts.size > 1) parts[1].trim() else null
        return tag to className
    }

    internal fun extractFirstTextContent(html: String, selector: String): String? {
        val elements = extractElements(html, selector)
        if (elements.isEmpty()) return null
        // Strip all HTML tags to get text content
        return elements[0].replace(Regex("<[^>]+>"), "").trim().takeIf { it.isNotBlank() }
    }

    internal fun extractFirstHref(html: String, selector: String): String? {
        val (tag, className) = parseCssSelector(selector)
        val pattern = if (className != null) {
            Pattern.compile(
                "<$tag\\b[^>]*class\\s*=\\s*[\"'][^\"']*\\b${Pattern.quote(className)}\\b[^\"']*[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
        } else {
            Pattern.compile(
                "<$tag\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
        }
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractLinksAsFeedItems(html: String): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        val pattern = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            val url = matcher.group(1)?.trim() ?: continue
            val text = (matcher.group(2) ?: "").replace(Regex("<[^>]+>"), "").trim()
            if (text.isNotBlank() && url.startsWith("http")) {
                items.add(ParsedFeedItem(title = text, url = url))
            }
        }
        return items
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                .parse(dateStr)?.time
        } catch (e: Exception) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .parse(dateStr)?.time
            } catch (e2: Exception) {
                null
            }
        }
    }
}

class FeedParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
