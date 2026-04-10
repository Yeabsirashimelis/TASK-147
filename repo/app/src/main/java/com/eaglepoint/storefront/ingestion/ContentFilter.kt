package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.domain.model.ParsedFeedItem
import com.eaglepoint.storefront.domain.model.SourceRule
import java.net.URI

class ContentFilter {

    fun filter(items: List<ParsedFeedItem>, rule: SourceRule): List<ParsedFeedItem> {
        return items
            .filter { passesAllowedDomains(it, rule) }
            .filter { passesBlockedDomains(it, rule) }
            .filter { passesAllowedKeywords(it, rule) }
            .filter { passesBlockedKeywords(it, rule) }
    }

    private fun passesAllowedDomains(item: ParsedFeedItem, rule: SourceRule): Boolean {
        if (rule.allowedDomains.isEmpty()) return true
        val domain = extractDomain(item.url) ?: return false
        return rule.allowedDomains.any { allowed ->
            domain.equals(allowed, ignoreCase = true) || domain.endsWith(".$allowed", ignoreCase = true)
        }
    }

    private fun passesBlockedDomains(item: ParsedFeedItem, rule: SourceRule): Boolean {
        if (rule.blockedDomains.isEmpty()) return true
        val domain = extractDomain(item.url) ?: return true
        return rule.blockedDomains.none { blocked ->
            domain.equals(blocked, ignoreCase = true) || domain.endsWith(".$blocked", ignoreCase = true)
        }
    }

    private fun passesAllowedKeywords(item: ParsedFeedItem, rule: SourceRule): Boolean {
        if (rule.allowedKeywords.isEmpty()) return true
        val text = "${item.title} ${item.summary ?: ""}".lowercase()
        return rule.allowedKeywords.any { keyword ->
            text.contains(keyword.lowercase())
        }
    }

    private fun passesBlockedKeywords(item: ParsedFeedItem, rule: SourceRule): Boolean {
        if (rule.blockedKeywords.isEmpty()) return true
        val text = "${item.title} ${item.summary ?: ""}".lowercase()
        return rule.blockedKeywords.none { keyword ->
            text.contains(keyword.lowercase())
        }
    }

    private fun extractDomain(url: String): String? {
        return try {
            URI(url).host
        } catch (e: Exception) {
            null
        }
    }
}
