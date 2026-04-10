package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.eaglepoint.storefront.domain.model.ParsedFeedItem
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ValidateBatchUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IngestionEngineTest {

    private lateinit var sourceRuleRepository: SourceRuleRepository
    private lateinit var jobRunRepository: IngestionJobRunRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var alertRepository: IngestionAlertRepository
    private lateinit var feedParser: FeedParser
    private lateinit var contentFilter: ContentFilter
    private lateinit var userAgentRotator: UserAgentRotator
    private lateinit var requestPacer: RequestPacer
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var validateBatch: ValidateBatchUseCase
    private lateinit var engine: IngestionEngine

    private val now = System.currentTimeMillis()
    private val testRule = SourceRule(
        id = "rule-1", name = "Test Source", url = "https://example.com/feed.xml",
        feedType = FeedType.RSS, createdAt = now, updatedAt = now
    )
    private val testBatchVersion = DataBatchVersion(
        id = "batch-1", batchName = "ingestion_rule-1", version = 1,
        sourceRuleId = "rule-1", ruleVersion = 1, createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        sourceRuleRepository = mockk(relaxed = true)
        jobRunRepository = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        alertRepository = mockk(relaxed = true)
        feedParser = mockk(relaxed = true)
        contentFilter = mockk(relaxed = true)
        userAgentRotator = mockk(relaxed = true)
        requestPacer = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        validateBatch = mockk(relaxed = true)

        engine = spyk(
            IngestionEngine(
                sourceRuleRepository, jobRunRepository, articleRepository, alertRepository,
                feedParser, contentFilter, userAgentRotator, requestPacer, logAuditEvent, validateBatch
            )
        )

        every { userAgentRotator.next() } returns "TestAgent/1.0"
        coEvery { jobRunRepository.createBatchVersion(any(), any(), any(), any()) } returns testBatchVersion
    }

    @Test
    fun `successful ingestion creates job run with SUCCESS status`() = runTest {
        val parsedItems = listOf(
            ParsedFeedItem("Article 1", url = "https://example.com/1"),
            ParsedFeedItem("Article 2", url = "https://example.com/2")
        )

        coEvery { engine.fetchContent(any()) } returns "<rss>feed content</rss>"
        every { feedParser.parse(any(), any(), any()) } returns parsedItems
        every { contentFilter.filter(any(), any()) } returns parsedItems
        coEvery { articleRepository.insertAll(any()) } returns 2

        val result = engine.runIngestionForSource(testRule, "admin-user")

        assertThat(result.status).isEqualTo(IngestionStatus.SUCCESS)
        assertThat(result.itemsParsed).isEqualTo(2)
        assertThat(result.itemsStored).isEqualTo(2)
        assertThat(result.batchVersionId).isEqualTo("batch-1")
        assertThat(result.failureReason).isNull()
    }

    @Test
    fun `failed ingestion creates job run with FAILURE status`() = runTest {
        coEvery { engine.fetchContent(any()) } throws IngestionException("Connection refused")

        val result = engine.runIngestionForSource(testRule, "admin-user")

        assertThat(result.status).isEqualTo(IngestionStatus.FAILURE)
        assertThat(result.failureReason).isEqualTo("Connection refused")
    }

    @Test
    fun `ingestion paces requests per source`() = runTest {
        coEvery { engine.fetchContent(any()) } returns "<rss></rss>"
        every { feedParser.parse(any(), any(), any()) } returns emptyList()
        every { contentFilter.filter(any(), any()) } returns emptyList()
        coEvery { articleRepository.insertAll(any()) } returns 0

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify { requestPacer.pace("rule-1", 2000L) }
    }

    @Test
    fun `ingestion records lineage for stored articles`() = runTest {
        val parsedItems = listOf(
            ParsedFeedItem("Article 1", url = "https://example.com/1")
        )

        coEvery { engine.fetchContent(any()) } returns "<rss>content</rss>"
        every { feedParser.parse(any(), any(), any()) } returns parsedItems
        every { contentFilter.filter(any(), any()) } returns parsedItems
        coEvery { articleRepository.insertAll(any()) } returns 1

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify { jobRunRepository.recordLineageBatch(any()) }
    }

    @Test
    fun `failure alerting triggers after threshold exceeded`() = runTest {
        coEvery { engine.fetchContent(any()) } throws IngestionException("Timeout")
        coEvery { jobRunRepository.countFailuresSince(eq("rule-1"), any()) } returns 5
        coEvery { alertRepository.hasUnacknowledgedForSource("rule-1") } returns false

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify { alertRepository.create(any()) }
    }

    @Test
    fun `no duplicate alert when unacknowledged alert exists`() = runTest {
        coEvery { engine.fetchContent(any()) } throws IngestionException("Timeout")
        coEvery { jobRunRepository.countFailuresSince(eq("rule-1"), any()) } returns 5
        coEvery { alertRepository.hasUnacknowledgedForSource("rule-1") } returns true

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify(exactly = 0) { alertRepository.create(any()) }
    }

    @Test
    fun `no alert when failure count below threshold`() = runTest {
        coEvery { engine.fetchContent(any()) } throws IngestionException("Timeout")
        coEvery { jobRunRepository.countFailuresSince(eq("rule-1"), any()) } returns 3

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify(exactly = 0) { alertRepository.create(any()) }
    }

    @Test
    fun `runIngestionForAllSources processes all active sources`() = runTest {
        val rule2 = testRule.copy(id = "rule-2", name = "Source 2")
        coEvery { sourceRuleRepository.getAllActiveList() } returns listOf(testRule, rule2)
        coEvery { engine.fetchContent(any()) } returns "<rss></rss>"
        every { feedParser.parse(any(), any(), any()) } returns emptyList()
        every { contentFilter.filter(any(), any()) } returns emptyList()
        coEvery { articleRepository.insertAll(any()) } returns 0

        val results = engine.runIngestionForAllSources("admin-user")

        assertThat(results).hasSize(2)
    }

    @Test
    fun `audit events logged for successful ingestion`() = runTest {
        coEvery { engine.fetchContent(any()) } returns "<rss></rss>"
        every { feedParser.parse(any(), any(), any()) } returns emptyList()
        every { contentFilter.filter(any(), any()) } returns emptyList()
        coEvery { articleRepository.insertAll(any()) } returns 0

        engine.runIngestionForSource(testRule, "admin-user")

        coVerify(atLeast = 2) { logAuditEvent(userId = "admin-user", action = any(), target = any(), targetId = any(), detail = any()) }
    }
}
