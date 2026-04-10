package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.CatalogItemDao
import com.eaglepoint.storefront.data.db.dao.CouponDao
import com.eaglepoint.storefront.data.db.dao.InventorySnapshotDao
import com.eaglepoint.storefront.data.db.dao.PriceRuleDao
import com.eaglepoint.storefront.data.db.dao.TaxRateDao
import com.eaglepoint.storefront.data.db.entity.CatalogItemEntity
import com.eaglepoint.storefront.data.db.entity.CouponEntity
import com.eaglepoint.storefront.data.db.entity.PriceRuleEntity
import com.eaglepoint.storefront.data.db.entity.InventorySnapshotEntity
import com.eaglepoint.storefront.domain.model.CatalogItem
import com.eaglepoint.storefront.domain.model.Coupon
import com.eaglepoint.storefront.domain.model.InventorySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val catalogItemDao: CatalogItemDao,
    private val inventorySnapshotDao: InventorySnapshotDao,
    private val priceRuleDao: PriceRuleDao,
    private val couponDao: CouponDao,
    private val taxRateDao: TaxRateDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun findItemById(id: String): CatalogItem? = withContext(dispatcher) {
        catalogItemDao.findById(id)?.let(::catalogEntityToDomain)
    }

    suspend fun findItemBySku(sku: String): CatalogItem? = withContext(dispatcher) {
        catalogItemDao.findBySku(sku)?.let(::catalogEntityToDomain)
    }

    fun getActiveItems(): Flow<List<CatalogItem>> {
        return catalogItemDao.getActive().map { it.map(::catalogEntityToDomain) }
    }

    suspend fun getAvailableQuantity(catalogItemId: String): Int = withContext(dispatcher) {
        inventorySnapshotDao.getAvailableQuantity(catalogItemId) ?: 0
    }

    suspend fun getActivePriceRules(): List<PriceRuleEntity> = withContext(dispatcher) {
        priceRuleDao.getActiveRules()
    }

    suspend fun findCouponByCode(code: String): Coupon? = withContext(dispatcher) {
        couponDao.findByCode(code)?.let(::couponEntityToDomain)
    }

    suspend fun findCouponById(id: String): Coupon? = withContext(dispatcher) {
        couponDao.findById(id)?.let(::couponEntityToDomain)
    }

    suspend fun incrementCouponUsage(couponId: String) = withContext(dispatcher) {
        couponDao.incrementUsage(couponId, System.currentTimeMillis())
    }

    suspend fun getByBatchVersionId(batchVersionId: String): List<CatalogItem> = withContext(dispatcher) {
        catalogItemDao.getByBatchVersionId(batchVersionId).map(::catalogEntityToDomain)
    }

    suspend fun getInventorySnapshotsByBatchVersionId(batchVersionId: String): List<InventorySnapshot> = withContext(dispatcher) {
        inventorySnapshotDao.getByBatchVersionId(batchVersionId).map(::inventoryEntityToDomain)
    }

    suspend fun getTaxRate(stateCode: String): Double = withContext(dispatcher) {
        taxRateDao.findByStateCode(stateCode)?.rate ?: 0.0
    }

    private fun catalogEntityToDomain(entity: CatalogItemEntity): CatalogItem {
        return CatalogItem(
            id = entity.id,
            sku = entity.sku,
            name = entity.name,
            description = entity.description,
            price = entity.price,
            imageUrl = entity.imageUrl,
            category = entity.category,
            isActive = entity.isActive
        )
    }

    private fun inventoryEntityToDomain(entity: InventorySnapshotEntity): InventorySnapshot {
        return InventorySnapshot(
            id = entity.id,
            catalogItemId = entity.catalogItemId,
            quantity = entity.quantity,
            reservedQuantity = entity.reservedQuantity,
            batchVersionId = entity.batchVersionId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun couponEntityToDomain(entity: CouponEntity): Coupon {
        return Coupon(
            id = entity.id,
            code = entity.code,
            description = entity.description,
            discountPercent = entity.discountPercent,
            discountAmount = entity.discountAmount,
            minOrderAmount = entity.minOrderAmount,
            maxUses = entity.maxUses,
            currentUses = entity.currentUses,
            isActive = entity.isActive,
            expiresAt = entity.expiresAt
        )
    }
}
