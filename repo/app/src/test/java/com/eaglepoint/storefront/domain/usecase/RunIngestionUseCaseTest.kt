package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.ingestion.IngestionEngine
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RunIngestionUseCaseTest {

    private lateinit var ingestionEngine: IngestionEngine
    private lateinit var sourceRuleRepository: SourceRuleRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var runIngestionUseCase: RunIngestionUseCase

    private val now = System.currentTimeMillis()
    private val testRule = SourceRule(
        id = "rule-1", name = "Test", url = "https://test.com/feed",
        feedType = FeedType.RSS, createdAt = now, updatedAt = now
    )
    private val testJobRun = IngestionJobRun(
        id = "run-1", sourceRuleId = "rule-1", ruleVersion = 1,
        status = IngestionStatus.SUCCESS, startedAt = now, createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        ingestionEngine = mockk(relaxed = true)
        sourceRuleRepository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        every { sessionManager.requireUserId() } returns "admin"
        runIngestionUseCase = RunIngestionUseCase(ingestionEngine, sourceRuleRepository, sessionManager)
    }

    @Test
    fun `runForSource fails when source not found`() = runTest {
        coEvery { sourceRuleRepository.findById("nonexistent") } returns null

        val result = runIngestionUseCase.runForSource("nonexistent")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `runForSource fails when source is inactive`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns testRule.copy(isActive = false)

        val result = runIngestionUseCase.runForSource("rule-1")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `runForSource succeeds for active source`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns testRule
        coEvery { ingestionEngine.runIngestionForSource(testRule, "admin") } returns testJobRun

        val result = runIngestionUseCase.runForSource("rule-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.status).isEqualTo(IngestionStatus.SUCCESS)
    }

    @Test
    fun `runAll delegates to engine with session userId`() = runTest {
        coEvery { ingestionEngine.runIngestionForAllSources("admin") } returns listOf(testJobRun)

        val results = runIngestionUseCase.runAll()

        assertThat(results).hasSize(1)
    }
}
