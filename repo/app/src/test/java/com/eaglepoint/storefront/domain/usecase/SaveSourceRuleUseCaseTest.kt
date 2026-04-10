package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveSourceRuleUseCaseTest {

    private lateinit var sourceRuleRepository: SourceRuleRepository
    private lateinit var inputValidator: InputValidator
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var roleGuard: RoleGuard
    private lateinit var sessionManager: SessionManager
    private lateinit var saveSourceRuleUseCase: SaveSourceRuleUseCase

    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        sourceRuleRepository = mockk(relaxed = true)
        inputValidator = InputValidator()
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)

        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin"

        saveSourceRuleUseCase = SaveSourceRuleUseCase(sourceRuleRepository, inputValidator, logAuditEvent, roleGuard, sessionManager)
    }

    private fun validRule(
        name: String = "Test Source",
        url: String = "https://example.com/feed.xml"
    ): SourceRule {
        return SourceRule(
            id = "rule-1", name = name, url = url,
            feedType = FeedType.RSS, createdAt = now, updatedAt = now
        )
    }

    @Test
    fun `saves new source rule successfully`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns null

        val result = saveSourceRuleUseCase(validRule())

        assertThat(result.isSuccess).isTrue()
        coVerify { sourceRuleRepository.save(any()) }
    }

    @Test
    fun `updates existing rule and bumps version`() = runTest {
        val existing = validRule().copy(ruleVersion = 3)
        coEvery { sourceRuleRepository.findById("rule-1") } returns existing

        val result = saveSourceRuleUseCase(validRule())

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.ruleVersion).isEqualTo(4)
    }

    @Test
    fun `rejects blank name`() = runTest {
        val result = saveSourceRuleUseCase(validRule(name = ""))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects invalid URL`() = runTest {
        val result = saveSourceRuleUseCase(validRule(url = "not-a-url"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects URL without http prefix`() = runTest {
        val result = saveSourceRuleUseCase(validRule(url = "ftp://example.com"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `logs audit event on save with session-derived userId`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns null

        saveSourceRuleUseCase(validRule())

        coVerify { logAuditEvent(userId = "admin", action = any(), target = "source_rule", targetId = "rule-1", detail = any()) }
    }
}
