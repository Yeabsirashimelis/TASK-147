package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.CurationStatus
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CurateArticleUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var curateUseCase: CurateArticleUseCase

    @BeforeEach
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
        logAuditEvent = mockk(relaxed = true)
        curateUseCase = CurateArticleUseCase(articleRepository, roleGuard, logAuditEvent, sessionManager)
    }

    @Test
    fun `editor can approve article`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"

        val result = curateUseCase.approve("art-1")
        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateCurationStatus("art-1", CurationStatus.APPROVED, "editor-1") }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_APPROVED, "article", "art-1", null) }
    }

    @Test
    fun `editor can reject article with reason`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"

        val result = curateUseCase.reject("art-1", "Low quality")
        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateCurationStatus("art-1", CurationStatus.REJECTED, "editor-1") }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_REJECTED, "article", "art-1", "Low quality") }
    }

    @Test
    fun `editor can set article as featured`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"

        val result = curateUseCase.setFeatured("art-1", true)
        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.setFeatured("art-1", true) }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_FEATURED, "article", "art-1", null) }
    }

    @Test
    fun `editor can update tags`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"

        val result = curateUseCase.updateTags("art-1", "Lakers", "NBA", "Playoffs")
        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateTags("art-1", "Lakers", "NBA", "Playoffs") }
    }

    @Test
    fun `regular user cannot approve articles — enforced via session`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.USER

        assertThrows<SecurityException> {
            curateUseCase.approve("art-1")
        }
    }

    @Test
    fun `analyst cannot curate articles — enforced via session`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.ANALYST

        assertThrows<SecurityException> {
            curateUseCase.approve("art-1")
        }
    }

    @Test
    fun `admin can curate articles`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"

        val result = curateUseCase.approve("art-1")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `userId is derived from session not caller`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "session-editor-id"

        curateUseCase.approve("art-1")

        coVerify { logAuditEvent("session-editor-id", AuditAction.ARTICLE_APPROVED, "article", "art-1", null) }
    }
}
