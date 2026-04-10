package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "data_lineage",
    indices = [
        Index(value = ["source_entity", "source_id"]),
        Index(value = ["target_entity", "target_id"]),
        Index(value = ["batch_version_id"])
    ]
)
data class DataLineageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "source_entity")
    val sourceEntity: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "target_entity")
    val targetEntity: String,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    @ColumnInfo(name = "batch_version_id")
    val batchVersionId: String? = null,

    @ColumnInfo(name = "transformation")
    val transformation: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
