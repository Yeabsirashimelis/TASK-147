package com.eaglepoint.storefront.domain.model

data class AuditEvent(
    val id: String,
    val userId: String? = null,
    val actorType: ActorType = ActorType.USER,
    val action: AuditAction,
    val target: String? = null,
    val targetId: String? = null,
    val timestamp: Long,
    val detail: String? = null
)
