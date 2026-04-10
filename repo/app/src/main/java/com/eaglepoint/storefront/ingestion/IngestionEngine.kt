package com.eaglepoint.storefront.ingestion

import com.eaglepoint.storefront.data.db.entity.DataLineageEntity
import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.IngestionAlert
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ValidateBatchUseCase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class IngestionEngine(
    private val sourceRuleRepository: SourceRuleRepository,
    private val jobRunRepository: IngestionJobRunRepository,
    private val articleRepository: ArticleRepository,
    private val alertRepository: IngestionAlertRepository,
    private val feedParser: FeedParser,
    private val contentFilter: ContentFilter,
    private val userAgentRotator: UserAgentRotator,
    private val requestPacer: RequestPacer,
    private val logAuditEvent: LogAuditEventUseCase,
    private val validateBatch: ValidateBatchUseCase
) {

    suspend fun runIngestionForAllSources(systemUserId: String): List<IngestionJobRun> {
        val sources = sourceRuleRepository.getAllActiveList()
        return sources.map { source ->
            runIngestionForSource(source, systemUserId)
        }
    }

    suspend fun runIngestionForSource(rule: SourceRule, systemUserId: String): IngestionJobRun {
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        var jobRun = IngestionJobRun(
            id = runId,
            sourceRuleId = rule.id,
            ruleVersion = rule.ruleVersion,
            status = IngestionStatus.RUNNING,
            startedAt = now,
            createdAt = now,
            updatedAt = now
        )
        jobRunRepository.createRun(jobRun)

        logAuditEvent(
            userId = systemUserId,
            action = AuditAction.INGESTION_STARTED,
            target = "source_rule",
            targetId = rule.id,
            detail = "Ingestion started for ${rule.name} (v${rule.ruleVersion})"
        )

        try {
            // Pace the request
            requestPacer.pace(rule.id, rule.requestDelayMs)

            // Fetch the feed content
            val content = fetchContent(rule.url)

            // Parse the feed
            val parsedItems = feedParser.parse(content, rule.feedType, rule.parseSelector)
            val filteredItems = contentFilter.filter(parsedItems, rule)

            // Create batch version
            val batchVersion = jobRunRepository.createBatchVersion(
                sourceRuleId = rule.id,
                ruleVersion = rule.ruleVersion,
                itemsCount = filteredItems.size,
                ingestionJobRunId = runId
            )

            // Convert to articles and store
            val articles = filteredItems.map { item ->
                Article(
                    id = UUID.randomUUID().toString(),
                    sourceRuleId = rule.id,
                    batchVersionId = batchVersion.id,
                    title = item.title,
                    summary = item.summary,
                    content = item.content,
                    author = item.author,
                    externalUrl = item.url,
                    imageUrl = item.imageUrl,
                    publishedAt = item.publishedAt,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val storedCount = articleRepository.insertAll(articles)

            // Record lineage for each stored article
            val lineages = articles.map { article ->
                DataLineageEntity(
                    id = UUID.randomUUID().toString(),
                    sourceEntity = "source_rule",
                    sourceId = rule.id,
                    targetEntity = "article",
                    targetId = article.id,
                    batchVersionId = batchVersion.id,
                    transformation = "ingestion:${rule.feedType.name}",
                    createdAt = now
                )
            }
            jobRunRepository.recordLineageBatch(lineages)

            jobRun = jobRun.copy(
                status = IngestionStatus.SUCCESS,
                itemsParsed = parsedItems.size,
                itemsStored = storedCount,
                batchVersionId = batchVersion.id,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            jobRunRepository.updateRun(jobRun)

            logAuditEvent(
                userId = systemUserId,
                action = AuditAction.INGESTION_COMPLETED,
                target = "source_rule",
                targetId = rule.id,
                detail = "Parsed: ${parsedItems.size}, Stored: $storedCount"
            )

            // Run batch quality validation after ingestion
            try {
                validateBatch(batchVersion, systemUserId)
            } catch (validationError: Exception) {
                // Validation failure should not fail the ingestion itself
                logAuditEvent(
                    userId = systemUserId,
                    action = AuditAction.BATCH_VALIDATION_FAILED,
                    target = "data_batch_version",
                    targetId = batchVersion.id,
                    detail = "Validation error: ${validationError.message}"
                )
            }

        } catch (e: Exception) {
            jobRun = jobRun.copy(
                status = IngestionStatus.FAILURE,
                failureReason = e.message ?: "Unknown error",
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            jobRunRepository.updateRun(jobRun)

            logAuditEvent(
                userId = systemUserId,
                action = AuditAction.INGESTION_FAILED,
                target = "source_rule",
                targetId = rule.id,
                detail = "Failure: ${e.message}"
            )

            // Check for failure alerting threshold
            checkFailureThreshold(rule, systemUserId)
        }

        return jobRun
    }

    private suspend fun checkFailureThreshold(rule: SourceRule, systemUserId: String) {
        val twentyFourHoursAgo = System.currentTimeMillis() - FAILURE_WINDOW_MS
        val failureCount = jobRunRepository.countFailuresSince(rule.id, twentyFourHoursAgo)

        if (failureCount >= FAILURE_THRESHOLD) {
            // Only create alert if there isn't already an unacknowledged one
            val hasExisting = alertRepository.hasUnacknowledgedForSource(rule.id)
            if (!hasExisting) {
                val alert = IngestionAlert(
                    id = UUID.randomUUID().toString(),
                    sourceRuleId = rule.id,
                    sourceName = rule.name,
                    failureCount = failureCount,
                    message = "Source '${rule.name}' has failed $failureCount times in the last 24 hours",
                    createdAt = System.currentTimeMillis()
                )
                alertRepository.create(alert)

                logAuditEvent(
                    userId = systemUserId,
                    action = AuditAction.INGESTION_ALERT_GENERATED,
                    target = "source_rule",
                    targetId = rule.id,
                    detail = "Failure alert: $failureCount failures in 24h"
                )
            }
        }
    }

    internal fun fetchContent(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgentRotator.next())
            connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IngestionException("HTTP $responseCode from $urlString")
            }

            return BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val FAILURE_THRESHOLD = 5
        const val FAILURE_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val CONNECTION_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

class IngestionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
