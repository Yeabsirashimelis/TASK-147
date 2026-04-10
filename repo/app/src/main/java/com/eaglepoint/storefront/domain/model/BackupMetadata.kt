package com.eaglepoint.storefront.domain.model

data class BackupMetadata(
    val dbVersion: Int,
    val timestamp: Long,
    val checksum: String,
    val appVersion: String
)
