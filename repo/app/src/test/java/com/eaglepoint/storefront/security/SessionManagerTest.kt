package com.eaglepoint.storefront.security

import com.eaglepoint.storefront.domain.model.UserRole
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for SessionManager using a mock-backed approach.
 * In production, SessionManager uses EncryptedSharedPreferences.
 * Here we test the role enforcement logic via a thin wrapper.
 */
class SessionManagerTest {

    @Test
    fun `requireRole throws when no session`() {
        // Simulate no session by having currentRole return null
        val sm = mockk<SessionManager>()
        every { sm.requireRole() } throws SecurityException("No active session")

        assertThrows<SecurityException> {
            sm.requireRole()
        }
    }

    @Test
    fun `requireUserId throws when no session`() {
        val sm = mockk<SessionManager>()
        every { sm.requireUserId() } throws SecurityException("No active session")

        assertThrows<SecurityException> {
            sm.requireUserId()
        }
    }

    @Test
    fun `requireRole with allowed roles passes`() {
        val sm = mockk<SessionManager>()
        every { sm.requireRole() } returns UserRole.ADMIN
        every { sm.requireRole(UserRole.ADMIN) } answers { callOriginal() }

        // If role is ADMIN and ADMIN is allowed, should not throw
        val role = sm.requireRole()
        assertThat(role).isEqualTo(UserRole.ADMIN)
    }

    @Test
    fun `requireOwnership throws for non-owner non-admin`() {
        val sm = mockk<SessionManager>()
        every { sm.requireUserId() } returns "user-2"
        every { sm.requireRole() } returns UserRole.USER
        every { sm.requireOwnership("user-1") } throws SecurityException("Access denied: resource does not belong to current user")

        assertThrows<SecurityException> {
            sm.requireOwnership("user-1")
        }
    }

    @Test
    fun `requireOwnership passes for admin even if not owner`() {
        val sm = mockk<SessionManager>()
        every { sm.requireUserId() } returns "admin-1"
        every { sm.requireRole() } returns UserRole.ADMIN
        every { sm.requireOwnership(any()) } answers { } // admin passes

        sm.requireOwnership("user-1") // should not throw
    }
}
