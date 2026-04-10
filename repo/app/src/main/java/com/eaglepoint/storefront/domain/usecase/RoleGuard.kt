package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager

class RoleGuard(private val sessionManager: SessionManager) {

    fun canAccessIngestionLogs(role: UserRole): Boolean {
        return role in setOf(UserRole.ADMIN, UserRole.EDITOR, UserRole.ANALYST)
    }

    fun canCurateContent(role: UserRole): Boolean {
        return role in setOf(UserRole.ADMIN, UserRole.EDITOR)
    }

    fun canConfigureSourceRules(role: UserRole): Boolean {
        return role == UserRole.ADMIN
    }

    fun canAccessCartData(role: UserRole): Boolean {
        return role in setOf(UserRole.ADMIN, UserRole.USER)
    }

    fun canAccessAuditLogs(role: UserRole): Boolean {
        return role in setOf(UserRole.ADMIN, UserRole.EDITOR, UserRole.ANALYST)
    }

    fun canManageUsers(role: UserRole): Boolean {
        return role == UserRole.ADMIN
    }

    fun canPerformBackupRestore(role: UserRole): Boolean {
        return role == UserRole.ADMIN
    }

    fun canExportAuditLogs(role: UserRole): Boolean {
        return role == UserRole.ADMIN
    }

    fun requireRole(actual: UserRole, required: Set<UserRole>) {
        if (actual !in required) {
            throw SecurityException("Access denied: role '$actual' not in $required")
        }
    }

    fun enforceSourceRuleAccess() {
        val role = sessionManager.requireRole()
        if (!canConfigureSourceRules(role)) {
            throw SecurityException("Access denied: source rule management requires ADMIN role")
        }
    }

    fun enforceIngestionConsoleAccess() {
        val role = sessionManager.requireRole()
        if (!canAccessIngestionLogs(role)) {
            throw SecurityException("Access denied: ingestion console requires ADMIN, EDITOR, or ANALYST role")
        }
    }

    fun enforceAuditLogAccess() {
        val role = sessionManager.requireRole()
        if (!canAccessAuditLogs(role)) {
            throw SecurityException("Access denied: audit log requires ADMIN, EDITOR, or ANALYST role")
        }
    }

    fun enforceAuditExportAccess() {
        val role = sessionManager.requireRole()
        if (!canExportAuditLogs(role)) {
            throw SecurityException("Access denied: audit export requires ADMIN role")
        }
    }

    fun enforceCurationAccess() {
        val role = sessionManager.requireRole()
        if (!canCurateContent(role)) {
            throw SecurityException("Access denied: content curation requires ADMIN or EDITOR role")
        }
    }

    fun enforceBackupRestoreAccess() {
        val role = sessionManager.requireRole()
        if (!canPerformBackupRestore(role)) {
            throw SecurityException("Access denied: backup/restore requires ADMIN role")
        }
    }

    fun enforceCartOwnership(cartUserId: String?) {
        sessionManager.requireOwnership(cartUserId)
    }
}
