package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthorizationEnforcementTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard

    @BeforeEach
    fun setUp() {
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
    }

    // --- Source Rule Management: admin-only, enforced via session ---

    @Test
    fun `regular user cannot save source rules`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.USER
        every { sessionManager.requireUserId() } returns "user-1"
        val sourceRuleRepo = mockk<SourceRuleRepository>(relaxed = true)
        val useCase = SaveSourceRuleUseCase(sourceRuleRepo, InputValidator(), mockk(relaxed = true), roleGuard, sessionManager)

        val now = System.currentTimeMillis()
        val rule = SourceRule(
            id = "r1", name = "Test", url = "https://example.com/feed",
            feedType = FeedType.RSS, createdAt = now, updatedAt = now
        )

        assertThrows<SecurityException> {
            useCase(rule)
        }
    }

    @Test
    fun `editor cannot save source rules`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"
        val sourceRuleRepo = mockk<SourceRuleRepository>(relaxed = true)
        val useCase = SaveSourceRuleUseCase(sourceRuleRepo, InputValidator(), mockk(relaxed = true), roleGuard, sessionManager)

        val now = System.currentTimeMillis()
        val rule = SourceRule(
            id = "r1", name = "Test", url = "https://example.com/feed",
            feedType = FeedType.RSS, createdAt = now, updatedAt = now
        )

        assertThrows<SecurityException> {
            useCase(rule)
        }
    }

    // --- Audit Log Access: admin/editor/analyst only ---

    @Test
    fun `regular user cannot access audit logs`() {
        every { sessionManager.requireRole() } returns UserRole.USER
        val auditRepo = mockk<AuditRepository>(relaxed = true)
        val useCase = GetAuditLogUseCase(auditRepo, roleGuard)

        assertThrows<SecurityException> {
            useCase.getPage()
        }
    }

    @Test
    fun `admin can access audit logs`() {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        val auditRepo = mockk<AuditRepository>(relaxed = true)
        val useCase = GetAuditLogUseCase(auditRepo, roleGuard)

        // Should not throw
        useCase.getPage()
    }

    // --- Backup/Restore: admin-only ---

    @Test
    fun `non-admin cannot initiate backup`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        val useCase = BackupUseCase(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), roleGuard)

        assertThrows<SecurityException> {
            useCase("editor-1", "pass".toCharArray(), mockk())
        }
    }

    @Test
    fun `non-admin cannot initiate restore`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.USER
        val useCase = RestoreUseCase(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), roleGuard)

        assertThrows<SecurityException> {
            useCase("user-1", "pass".toCharArray(), mockk(), mockk())
        }
    }

    // --- Object-level ownership: enforced via session ---

    @Test
    fun `user cannot checkout another users cart`() = runTest {
        every { sessionManager.requireUserId() } returns "user-2"
        every { sessionManager.requireRole() } returns UserRole.USER
        every { sessionManager.requireOwnership("user-1") } throws SecurityException("not owner")

        val cartRepo = mockk<com.eaglepoint.storefront.data.repository.CartRepository>(relaxed = true)
        val now = System.currentTimeMillis()
        val cart = com.eaglepoint.storefront.domain.model.Cart(
            id = "cart-1", userId = "user-1",
            items = listOf(
                com.eaglepoint.storefront.domain.model.CartLineItem(
                    id = "li-1", cartId = "cart-1", catalogItemId = "c-1",
                    sku = "S1", name = "Item", unitPrice = 10.0, quantity = 1,
                    createdAt = now, updatedAt = now
                )
            ),
            createdAt = now, updatedAt = now
        )
        coEvery { cartRepo.findById("cart-1") } returns cart

        val useCase = CheckoutUseCase(cartRepo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), sessionManager, mockk(relaxed = true))

        assertThrows<SecurityException> {
            useCase.startCheckout("cart-1")
        }
    }

    // --- Curation: admin/editor only, session-enforced ---

    @Test
    fun `analyst cannot curate content`() {
        assertThat(roleGuard.canCurateContent(UserRole.ANALYST)).isFalse()
    }

    @Test
    fun `user cannot curate content`() {
        assertThat(roleGuard.canCurateContent(UserRole.USER)).isFalse()
    }

    // --- Authorization uses session, not caller-supplied role ---

    @Test
    fun `curation rejects even if caller claims to be admin but session says user`() = runTest {
        every { sessionManager.requireRole() } returns UserRole.USER
        every { sessionManager.requireUserId() } returns "attacker"
        val curateUseCase = CurateArticleUseCase(mockk(relaxed = true), roleGuard, mockk(relaxed = true), sessionManager)

        assertThrows<SecurityException> {
            curateUseCase.approve("art-1")
        }
    }
}
