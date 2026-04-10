package com.eaglepoint.storefront.domain.model

data class User(
    val id: String,
    val username: String,
    val passwordHash: ByteArray,
    val passwordSalt: ByteArray,
    val role: UserRole = UserRole.USER,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "User(id=${id.take(4)}****, username=$username, isActive=$isActive)"
    }
}
