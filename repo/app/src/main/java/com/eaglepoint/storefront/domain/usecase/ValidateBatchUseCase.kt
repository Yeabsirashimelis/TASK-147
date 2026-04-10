package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.IngestionAlert
import com.eaglepoint.storefront.quality.BatchValidator
import com.eaglepoint.storefront.quality.ValidatableItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class ValidateBatchUseCase(
    private val batchValidationRepository: BatchValidationRepository,
    private val articleRepository: ArticleRepository,
    private val alertRepository: IngestionAlertRepository,
    private val batchValidator: BatchValidator,
    private val logAuditEvent: LogAuditEventUseCase,
    private val catalogRepository: CatalogRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        batchVersion: DataBatchVersion,
        userId: String
    ): BatchQualityReport = withContext(dispatcher) {
        // Collect validatable items from articles, catalog items, and inventory snapshots
        val items = mutableListOf<ValidatableItem>()
        items.addAll(collectArticleItems(batchVersion))
        items.addAll(collectCatalogItems(batchVersion))
        items.addAll(collectInventoryItems(batchVersion))

        // Run validation
        val report = batchValidator.validate(batchVersion, items)

        // Persist validation errors
        batchValidationRepository.clearErrorsForBatch(batchVersion.id)
        if (report.errors.isNotEmpty()) {
            batchValidationRepository.saveValidationErrors(report.errors)
        }

        // Update batch version with validation results
        batchValidationRepository.updateBatchValidation(report.batchVersion)

        // Audit log
        if (report.passed) {
            logAuditEvent(
                userId = userId,
                action = AuditAction.BATCH_VALIDATION_PASSED,
                target = "data_batch_version",
                targetId = batchVersion.id,
                detail = "Batch v${batchVersion.version}: ${report.totalItems} items, ${report.errorCount} errors (${formatRate(report.errorRate)})"
            )
        } else {
            logAuditEvent(
                userId = userId,
                action = AuditAction.BATCH_VALIDATION_FAILED,
                target = "data_batch_version",
                targetId = batchVersion.id,
                detail = "Batch v${batchVersion.version}: ${report.totalItems} items, ${report.errorCount} errors (${formatRate(report.errorRate)}) — exceeds ${formatRate(BatchQualityReport.ERROR_RATE_THRESHOLD)} threshold"
            )

            // Generate quality alert
            generateQualityAlert(batchVersion, report, userId)
        }

        report
    }

    private suspend fun collectArticleItems(batchVersion: DataBatchVersion): List<ValidatableItem> {
        val articles = articleRepository.getBySourceRule(batchVersion.sourceRuleId).first()
        return articles
            .filter { it.batchVersionId == batchVersion.id }
            .map { article ->
                ValidatableItem.fromArticle(
                    id = article.id,
                    batchVersionId = batchVersion.id,
                    publishedAt = article.publishedAt,
                    title = article.title,
                    externalUrl = article.externalUrl
                )
            }
    }

    private suspend fun collectCatalogItems(batchVersion: DataBatchVersion): List<ValidatableItem> {
        val items = catalogRepository.getByBatchVersionId(batchVersion.id)
        return items.map { item ->
            ValidatableItem.fromCatalogItem(
                id = item.id,
                batchVersionId = batchVersion.id,
                price = item.price,
                name = item.name
            )
        }
    }

    private suspend fun collectInventoryItems(batchVersion: DataBatchVersion): List<ValidatableItem> {
        val snapshots = catalogRepository.getInventorySnapshotsByBatchVersionId(batchVersion.id)
        return snapshots.map { snapshot ->
            ValidatableItem.fromInventorySnapshot(
                id = snapshot.id,
                batchVersionId = batchVersion.id,
                quantity = snapshot.quantity,
                catalogItemId = snapshot.catalogItemId
            )
        }
    }

    private suspend fun generateQualityAlert(
        batchVersion: DataBatchVersion,
        report: BatchQualityReport,
        userId: String
    ) {
        val alert = IngestionAlert(
            id = UUID.randomUUID().toString(),
            sourceRuleId = batchVersion.sourceRuleId,
            sourceName = "Batch v${batchVersion.version}",
            failureCount = report.errorCount,
            message = "Quality validation failed: ${report.errorCount} errors in ${report.totalItems} items (${formatRate(report.errorRate)} error rate, threshold: ${formatRate(BatchQualityReport.ERROR_RATE_THRESHOLD)})",
            createdAt = System.currentTimeMillis()
        )
        alertRepository.create(alert)

        logAuditEvent(
            userId = userId,
            action = AuditAction.QUALITY_ALERT_GENERATED,
            target = "data_batch_version",
            targetId = batchVersion.id,
            detail = "Error rate ${formatRate(report.errorRate)} exceeds threshold"
        )
    }

    private fun formatRate(rate: Double): String {
        return "%.1f%%".format(rate * 100)
    }
}
