package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.SourceRuleDao
import com.eaglepoint.storefront.data.db.entity.SourceRuleEntity
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.SourceRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SourceRuleRepository(
    private val sourceRuleDao: SourceRuleDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun getAllActive(): Flow<List<SourceRule>> {
        return sourceRuleDao.getAllActive().map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getAll(): Flow<List<SourceRule>> {
        return sourceRuleDao.getAll().map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    suspend fun findById(id: String): SourceRule? = withContext(dispatcher) {
        sourceRuleDao.findById(id)?.let { entityToDomain(it) }
    }

    suspend fun save(rule: SourceRule) = withContext(dispatcher) {
        val entity = domainToEntity(rule)
        val existing = sourceRuleDao.findById(rule.id)
        if (existing != null) {
            sourceRuleDao.update(entity)
        } else {
            sourceRuleDao.insert(entity)
        }
    }

    suspend fun deactivate(id: String, timestamp: Long) = withContext(dispatcher) {
        sourceRuleDao.deactivate(id, timestamp)
    }

    suspend fun getAllActiveList(): List<SourceRule> = withContext(dispatcher) {
        sourceRuleDao.getAllActiveList().map { entityToDomain(it) }
    }

    private fun entityToDomain(entity: SourceRuleEntity): SourceRule {
        return SourceRule(
            id = entity.id,
            name = entity.name,
            url = entity.url,
            feedType = FeedType.valueOf(entity.feedType),
            parseSelector = entity.parseSelector,
            allowedDomains = entity.allowedDomains?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            blockedDomains = entity.blockedDomains?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            allowedKeywords = entity.allowedKeywords?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            blockedKeywords = entity.blockedKeywords?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            intervalHours = entity.intervalHours,
            requestDelayMs = entity.requestDelayMs,
            isActive = entity.isActive,
            ruleVersion = entity.ruleVersion,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun domainToEntity(rule: SourceRule): SourceRuleEntity {
        return SourceRuleEntity(
            id = rule.id,
            name = rule.name,
            url = rule.url,
            feedType = rule.feedType.name,
            parseSelector = rule.parseSelector,
            allowedDomains = rule.allowedDomains.joinToString(",").ifBlank { null },
            blockedDomains = rule.blockedDomains.joinToString(",").ifBlank { null },
            allowedKeywords = rule.allowedKeywords.joinToString(",").ifBlank { null },
            blockedKeywords = rule.blockedKeywords.joinToString(",").ifBlank { null },
            intervalHours = rule.intervalHours,
            requestDelayMs = rule.requestDelayMs,
            isActive = rule.isActive,
            ruleVersion = rule.ruleVersion,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt
        )
    }
}
