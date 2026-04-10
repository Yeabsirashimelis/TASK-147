package com.eaglepoint.storefront.api

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.CurationStatus
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.usecase.CurateArticleUseCase
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.RoleGuard
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * API-level functional tests for content curation.
 * Covers approve, reject, feature, tag updates,
 * and role-based permission enforcement via session.
 */
class ContentCurationFlowTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard
    private lateinit var curateArticleUseCase: CurateArticleUseCase

    @BeforeEach
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
        curateArticleUseCase = CurateArticleUseCase(articleRepository, roleGuard, logAuditEvent, sessionManager)
    }

    private fun setSession(userId: String, role: UserRole) {
        every { sessionManager.requireUserId() } returns userId
        every { sessionManager.requireRole() } returns role
    }

    // --- Approve article ---
    @Test
    fun `editor approves article successfully`() = runTest {
        setSession("editor-1", UserRole.EDITOR)

        val result = curateArticleUseCase.approve("art-1")

        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateCurationStatus("art-1", CurationStatus.APPROVED, "editor-1") }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_APPROVED, "article", "art-1", null) }
    }

    // --- Reject with reason ---
    @Test
    fun `editor rejects article with reason`() = runTest {
        setSession("editor-1", UserRole.EDITOR)

        val result = curateArticleUseCase.reject("art-1", "Duplicate content")

        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateCurationStatus("art-1", CurationStatus.REJECTED, "editor-1") }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_REJECTED, "article", "art-1", "Duplicate content") }
    }

    // --- Feature article ---
    @Test
    fun `editor features article and status changes to FEATURED`() = runTest {
        setSession("editor-1", UserRole.EDITOR)

        val result = curateArticleUseCase.setFeatured("art-1", true)

        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.setFeatured("art-1", true) }
        coVerify { articleRepository.updateCurationStatus("art-1", CurationStatus.FEATURED, "editor-1") }
    }

    @Test
    fun `editor unfeatures article`() = runTest {
        setSession("editor-1", UserRole.EDITOR)

        val result = curateArticleUseCase.setFeatured("art-1", false)

        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.setFeatured("art-1", false) }
        coVerify { logAuditEvent("editor-1", AuditAction.ARTICLE_UNFEATURED, "article", "art-1", null) }
    }

    // --- Tag updates ---
    @Test
    fun `editor updates tags with team league and topic`() = runTest {
        setSession("editor-1", UserRole.EDITOR)

        val result = curateArticleUseCase.updateTags("art-1", "Lakers", "NBA", "Playoffs")

        assertThat(result.isSuccess).isTrue()
        coVerify { articleRepository.updateTags("art-1", "Lakers", "NBA", "Playoffs") }
    }

    // --- Permission enforcement via session ---
    @Test
    fun `regular user cannot approve articles`() = runTest {
        setSession("user-1", UserRole.USER)
        assertThrows<SecurityException> {
            curateArticleUseCase.approve("art-1")
        }
    }

    @Test
    fun `analyst cannot approve articles`() = runTest {
        setSession("analyst-1", UserRole.ANALYST)
        assertThrows<SecurityException> {
            curateArticleUseCase.approve("art-1")
        }
    }

    @Test
    fun `regular user cannot reject articles`() = runTest {
        setSession("user-1", UserRole.USER)
        assertThrows<SecurityException> {
            curateArticleUseCase.reject("art-1")
        }
    }

    @Test
    fun `regular user cannot feature articles`() = runTest {
        setSession("user-1", UserRole.USER)
        assertThrows<SecurityException> {
            curateArticleUseCase.setFeatured("art-1", true)
        }
    }

    @Test
    fun `regular user cannot update tags`() = runTest {
        setSession("user-1", UserRole.USER)
        assertThrows<SecurityException> {
            curateArticleUseCase.updateTags("art-1", "Lakers", "NBA", "Playoffs")
        }
    }

    // --- Admin can curate ---
    @Test
    fun `admin can approve articles`() = runTest {
        setSession("admin-1", UserRole.ADMIN)
        val result = curateArticleUseCase.approve("art-1")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `admin can reject articles`() = runTest {
        setSession("admin-1", UserRole.ADMIN)
        val result = curateArticleUseCase.reject("art-1")
        assertThat(result.isSuccess).isTrue()
    }
}
