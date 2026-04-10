package com.eaglepoint.storefront.domain.model

data class BatchQualityReport(
    val batchVersion: DataBatchVersion,
    val totalItems: Int,
    val errorCount: Int,
    val errorRate: Double,
    val passed: Boolean,
    val errors: List<ValidationError>,
    val errorsByRule: Map<String, Int>,
    val errorsByField: Map<String, Int>
) {
    companion object {
        const val ERROR_RATE_THRESHOLD = 0.02 // 2%
    }
}
