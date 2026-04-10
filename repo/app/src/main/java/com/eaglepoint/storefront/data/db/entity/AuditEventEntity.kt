package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["action"])
    ]
)
data class AuditEventEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String? = null,

    @ColumnInfo(name = "actor_type")
    val actorType: String = "USER",

    val action: String,

    val target: String? = null,

    @ColumnInfo(name = "target_id")
    val targetId: String? = null,

    val timestamp: Long,

    val detail: String? = null
)
