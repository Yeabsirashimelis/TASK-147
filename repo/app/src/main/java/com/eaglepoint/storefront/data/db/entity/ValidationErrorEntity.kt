package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "validation_errors",
    indices = [
        Index(value = ["batch_version_id"]),
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["rule_name"])
    ]
)
data class ValidationErrorEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "batch_version_id")
    val batchVersionId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "field_name")
    val fieldName: String,

    @ColumnInfo(name = "rule_name")
    val ruleName: String,

    val message: String,

    @ColumnInfo(name = "actual_value")
    val actualValue: String? = null,

    val severity: String = "ERROR",

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
