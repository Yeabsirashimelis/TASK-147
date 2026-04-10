package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoleGuardTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard

    @BeforeEach
    fun setUp() {
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
    }

    // Ingestion logs access
    @Test fun `admin can access ingestion logs`() { assertThat(roleGuard.canAccessIngestionLogs(UserRole.ADMIN)).isTrue() }
    @Test fun `editor can access ingestion logs`() { assertThat(roleGuard.canAccessIngestionLogs(UserRole.EDITOR)).isTrue() }
    @Test fun `analyst can access ingestion logs`() { assertThat(roleGuard.canAccessIngestionLogs(UserRole.ANALYST)).isTrue() }
    @Test fun `user cannot access ingestion logs`() { assertThat(roleGuard.canAccessIngestionLogs(UserRole.USER)).isFalse() }

    // Content curation
    @Test fun `admin can curate content`() { assertThat(roleGuard.canCurateContent(UserRole.ADMIN)).isTrue() }
    @Test fun `editor can curate content`() { assertThat(roleGuard.canCurateContent(UserRole.EDITOR)).isTrue() }
    @Test fun `analyst cannot curate content`() { assertThat(roleGuard.canCurateContent(UserRole.ANALYST)).isFalse() }
    @Test fun `user cannot curate content`() { assertThat(roleGuard.canCurateContent(UserRole.USER)).isFalse() }

    // Source rule configuration
    @Test fun `only admin can configure source rules`() {
        assertThat(roleGuard.canConfigureSourceRules(UserRole.ADMIN)).isTrue()
        assertThat(roleGuard.canConfigureSourceRules(UserRole.EDITOR)).isFalse()
        assertThat(roleGuard.canConfigureSourceRules(UserRole.ANALYST)).isFalse()
        assertThat(roleGuard.canConfigureSourceRules(UserRole.USER)).isFalse()
    }

    // Cart data
    @Test fun `editor cannot access cart data`() { assertThat(roleGuard.canAccessCartData(UserRole.EDITOR)).isFalse() }
    @Test fun `analyst cannot access cart data`() { assertThat(roleGuard.canAccessCartData(UserRole.ANALYST)).isFalse() }
    @Test fun `user can access cart data`() { assertThat(roleGuard.canAccessCartData(UserRole.USER)).isTrue() }
    @Test fun `admin can access cart data`() { assertThat(roleGuard.canAccessCartData(UserRole.ADMIN)).isTrue() }

    // Backup/restore
    @Test fun `only admin can perform backup restore`() {
        assertThat(roleGuard.canPerformBackupRestore(UserRole.ADMIN)).isTrue()
        assertThat(roleGuard.canPerformBackupRestore(UserRole.EDITOR)).isFalse()
        assertThat(roleGuard.canPerformBackupRestore(UserRole.USER)).isFalse()
    }

    // Audit export
    @Test fun `only admin can export audit logs`() {
        assertThat(roleGuard.canExportAuditLogs(UserRole.ADMIN)).isTrue()
        assertThat(roleGuard.canExportAuditLogs(UserRole.EDITOR)).isFalse()
    }

    // requireRole
    @Test fun `requireRole passes when role matches`() {
        roleGuard.requireRole(UserRole.EDITOR, setOf(UserRole.ADMIN, UserRole.EDITOR))
    }

    @Test fun `requireRole throws SecurityException when role does not match`() {
        assertThrows<SecurityException> {
            roleGuard.requireRole(UserRole.USER, setOf(UserRole.ADMIN, UserRole.EDITOR))
        }
    }

    // Session-based enforcement
    @Test fun `enforceSourceRuleAccess throws for non-admin`() {
        every { sessionManager.requireRole() } returns UserRole.USER
        assertThrows<SecurityException> {
            roleGuard.enforceSourceRuleAccess()
        }
    }

    @Test fun `enforceSourceRuleAccess passes for admin`() {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        roleGuard.enforceSourceRuleAccess()
    }

    @Test fun `enforceAuditLogAccess throws for regular user`() {
        every { sessionManager.requireRole() } returns UserRole.USER
        assertThrows<SecurityException> {
            roleGuard.enforceAuditLogAccess()
        }
    }

    @Test fun `enforceCurationAccess throws for analyst`() {
        every { sessionManager.requireRole() } returns UserRole.ANALYST
        assertThrows<SecurityException> {
            roleGuard.enforceCurationAccess()
        }
    }

    @Test fun `enforceBackupRestoreAccess throws for editor`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        assertThrows<SecurityException> {
            roleGuard.enforceBackupRestoreAccess()
        }
    }

    @Test fun `enforceCartOwnership delegates to sessionManager`() {
        every { sessionManager.requireOwnership("user-1") } throws SecurityException("not owner")
        assertThrows<SecurityException> {
            roleGuard.enforceCartOwnership("user-1")
        }
    }
}
