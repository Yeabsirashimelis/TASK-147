package com.eaglepoint.storefront.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eaglepoint.storefront.domain.model.UserRole

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun startSession(userId: String, role: UserRole) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE, role.name)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    fun endSession() {
        prefs.edit().clear().apply()
    }

    val isLoggedIn: Boolean
        get() = prefs.getString(KEY_USER_ID, null) != null

    val currentUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val currentRole: UserRole?
        get() = prefs.getString(KEY_ROLE, null)?.let {
            try { UserRole.valueOf(it) } catch (_: Exception) { null }
        }

    fun requireUserId(): String {
        return currentUserId ?: throw SecurityException("No active session")
    }

    fun requireRole(): UserRole {
        return currentRole ?: throw SecurityException("No active session")
    }

    fun requireRole(vararg allowed: UserRole) {
        val role = requireRole()
        if (role !in allowed) {
            throw SecurityException("Access denied: role '$role' not permitted for this operation")
        }
    }

    fun requireOwnership(resourceOwnerId: String?) {
        val userId = requireUserId()
        val role = requireRole()
        if (role == UserRole.ADMIN) return
        if (resourceOwnerId == null || resourceOwnerId != userId) {
            throw SecurityException("Access denied: resource does not belong to current user")
        }
    }

    companion object {
        private const val PREFS_NAME = "storefront_session"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_ROLE = "session_role"
        private const val KEY_LOGIN_TIME = "session_login_time"
    }
}
