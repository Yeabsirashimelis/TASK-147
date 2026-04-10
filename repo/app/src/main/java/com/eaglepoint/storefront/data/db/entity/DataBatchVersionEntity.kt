package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eaglepoint.storefront.data.db.entity.SourceRuleEntity

@Entity(
    tableName = "data_batch_versions",
    foreignKeys = [
        ForeignKey(
            entity = SourceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_rule_id"]),
        Index(value = ["batch_name"])
    ]
)
data class DataBatchVersionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "batch_name")
    val batchName: String,

    val version: Int,

    @ColumnInfo(name = "source_rule_id")
    val sourceRuleId: String,

    @ColumnInfo(name = "rule_version")
    val ruleVersion: Int,

    @ColumnInfo(name = "items_count")
    val itemsCount: Int = 0,

    @ColumnInfo(name = "ingestion_job_run_id")
    val ingestionJobRunId: String? = null,

    @ColumnInfo(name = "error_count")
    val errorCount: Int = 0,

    @ColumnInfo(name = "validation_status")
    val validationStatus: String? = null,

    @ColumnInfo(name = "error_rate")
    val errorRate: Double = 0.0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
