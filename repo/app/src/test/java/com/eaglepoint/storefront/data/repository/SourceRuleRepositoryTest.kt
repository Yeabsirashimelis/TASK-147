package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.SourceRuleDao
import com.eaglepoint.storefront.data.db.entity.SourceRuleEntity
import com.eaglepoint.storefront.domain.model.FeedType
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SourceRuleRepositoryTest {

    private lateinit var dao: SourceRuleDao
    private lateinit var repository: SourceRuleRepository
    private val testDispatcher = StandardTestDispatcher()
    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = SourceRuleRepository(dao, testDispatcher)
    }

    private fun testEntity() = SourceRuleEntity(
        id = "rule-1", name = "Test", url = "https://test.com/feed",
        feedType = "RSS", intervalHours = 6, requestDelayMs = 2000,
        isActive = true, ruleVersion = 1,
        allowedDomains = "example.com,test.org",
        blockedDomains = "spam.net",
        allowedKeywords = null, blockedKeywords = "casino",
        createdAt = now, updatedAt = now
    )

    @Test
    fun `findById maps entity to domain correctly`() = runTest(testDispatcher) {
        coEvery { dao.findById("rule-1") } returns testEntity()

        val result = repository.findById("rule-1")

        assertThat(result).isNotNull()
        assertThat(result!!.name).isEqualTo("Test")
        assertThat(result.feedType).isEqualTo(FeedType.RSS)
        assertThat(result.allowedDomains).containsExactly("example.com", "test.org")
        assertThat(result.blockedDomains).containsExactly("spam.net")
        assertThat(result.allowedKeywords).isEmpty()
        assertThat(result.blockedKeywords).containsExactly("casino")
    }

    @Test
    fun `findById returns null for nonexistent rule`() = runTest(testDispatcher) {
        coEvery { dao.findById("missing") } returns null

        val result = repository.findById("missing")

        assertThat(result).isNull()
    }

    @Test
    fun `save inserts new rule`() = runTest(testDispatcher) {
        coEvery { dao.findById("rule-new") } returns null

        val rule = com.eaglepoint.storefront.domain.model.SourceRule(
            id = "rule-new", name = "New Source", url = "https://new.com/feed",
            feedType = FeedType.ATOM, createdAt = now, updatedAt = now
        )

        repository.save(rule)

        coVerify { dao.insert(any()) }
    }

    @Test
    fun `save updates existing rule`() = runTest(testDispatcher) {
        coEvery { dao.findById("rule-1") } returns testEntity()

        val rule = com.eaglepoint.storefront.domain.model.SourceRule(
            id = "rule-1", name = "Updated", url = "https://updated.com/feed",
            feedType = FeedType.RSS, createdAt = now, updatedAt = now
        )

        repository.save(rule)

        coVerify { dao.update(any()) }
    }

    @Test
    fun `deactivate delegates to dao`() = runTest(testDispatcher) {
        repository.deactivate("rule-1", now)

        coVerify { dao.deactivate("rule-1", now) }
    }
}
