package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.CartDao
import com.eaglepoint.storefront.data.db.dao.CartLineItemDao
import com.eaglepoint.storefront.data.db.entity.CartEntity
import com.eaglepoint.storefront.data.db.entity.CartLineItemEntity
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.eaglepoint.storefront.domain.model.CartStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class CartRepository(
    private val cartDao: CartDao,
    private val lineItemDao: CartLineItemDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getOrCreateCart(userId: String?): Cart = withContext(dispatcher) {
        val existing = if (userId != null) {
            cartDao.findActiveByUserId(userId)
        } else null

        if (existing != null) {
            val items = lineItemDao.getByCartIdList(existing.id)
            cartEntityToDomain(existing, items)
        } else {
            val now = System.currentTimeMillis()
            val cart = CartEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                status = CartStatus.ACTIVE.name,
                createdAt = now,
                updatedAt = now
            )
            cartDao.insert(cart)
            cartEntityToDomain(cart, emptyList())
        }
    }

    suspend fun findById(cartId: String): Cart? = withContext(dispatcher) {
        val entity = cartDao.findById(cartId) ?: return@withContext null
        val items = lineItemDao.getByCartIdList(cartId)
        cartEntityToDomain(entity, items)
    }

    fun observeCart(cartId: String): Flow<Cart?> {
        return cartDao.observeById(cartId).combine(
            lineItemDao.getByCartId(cartId)
        ) { cartEntity, lineItems ->
            cartEntity?.let { cartEntityToDomain(it, lineItems) }
        }
    }

    suspend fun addItem(cartId: String, item: CartLineItem) = withContext(dispatcher) {
        val existing = lineItemDao.findByCartAndSku(cartId, item.sku)
        val now = System.currentTimeMillis()
        if (existing != null) {
            lineItemDao.updateQuantity(existing.id, existing.quantity + item.quantity, now)
        } else {
            lineItemDao.upsert(lineItemToEntity(item))
        }
        cartDao.findById(cartId)?.let {
            cartDao.update(it.copy(updatedAt = now))
        }
    }

    suspend fun updateItemQuantity(itemId: String, quantity: Int) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        if (quantity <= 0) {
            lineItemDao.delete(itemId)
        } else {
            lineItemDao.updateQuantity(itemId, quantity, now)
        }
    }

    suspend fun removeItem(itemId: String) = withContext(dispatcher) {
        lineItemDao.delete(itemId)
    }

    suspend fun clearCart(cartId: String) = withContext(dispatcher) {
        lineItemDao.deleteAllForCart(cartId)
        val now = System.currentTimeMillis()
        cartDao.findById(cartId)?.let {
            cartDao.update(it.copy(updatedAt = now))
        }
    }

    suspend fun assignUserToCart(cartId: String, userId: String) = withContext(dispatcher) {
        cartDao.assignUser(cartId, userId, System.currentTimeMillis())
    }

    suspend fun setCoupon(cartId: String, couponId: String?) = withContext(dispatcher) {
        cartDao.setCoupon(cartId, couponId, System.currentTimeMillis())
    }

    suspend fun setPriceLock(cartId: String, lockedAt: Long, expiresAt: Long) = withContext(dispatcher) {
        cartDao.setPriceLock(cartId, lockedAt, expiresAt, System.currentTimeMillis())
        // Lock individual line item prices
        val items = lineItemDao.getByCartIdList(cartId)
        val now = System.currentTimeMillis()
        items.forEach { item ->
            lineItemDao.update(item.copy(lockedPrice = item.unitPrice, updatedAt = now))
        }
    }

    suspend fun clearPriceLock(cartId: String) = withContext(dispatcher) {
        cartDao.setPriceLock(cartId, null, null, System.currentTimeMillis())
        val items = lineItemDao.getByCartIdList(cartId)
        val now = System.currentTimeMillis()
        items.forEach { item ->
            lineItemDao.update(item.copy(lockedPrice = null, updatedAt = now))
        }
    }

    suspend fun updateStatus(cartId: String, status: CartStatus) = withContext(dispatcher) {
        cartDao.updateStatus(cartId, status.name, System.currentTimeMillis())
    }

    suspend fun mergeGuestCartIntoUser(guestCartId: String, userId: String): Cart = withContext(dispatcher) {
        val userCart = getOrCreateCart(userId)
        val guestItems = lineItemDao.getByCartIdList(guestCartId)
        val now = System.currentTimeMillis()

        for (guestItem in guestItems) {
            val existingItem = lineItemDao.findByCartAndSku(userCart.id, guestItem.sku)
            if (existingItem != null) {
                lineItemDao.updateQuantity(existingItem.id, existingItem.quantity + guestItem.quantity, now)
            } else {
                val newItem = guestItem.copy(
                    id = UUID.randomUUID().toString(),
                    cartId = userCart.id,
                    createdAt = now,
                    updatedAt = now
                )
                lineItemDao.upsert(newItem)
            }
        }

        // Mark guest cart as merged
        cartDao.updateStatus(guestCartId, CartStatus.MERGED.name, now)

        findById(userCart.id)!!
    }

    suspend fun getItemCount(cartId: String): Int = withContext(dispatcher) {
        lineItemDao.totalQuantityForCart(cartId) ?: 0
    }

    private fun cartEntityToDomain(entity: CartEntity, items: List<CartLineItemEntity>): Cart {
        return Cart(
            id = entity.id,
            userId = entity.userId,
            status = try { CartStatus.valueOf(entity.status) } catch (e: Exception) { CartStatus.ACTIVE },
            couponId = entity.couponId,
            priceLockedAt = entity.priceLockedAt,
            priceLockExpiresAt = entity.priceLockExpiresAt,
            items = items.map { lineItemEntityToDomain(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun lineItemEntityToDomain(entity: CartLineItemEntity): CartLineItem {
        return CartLineItem(
            id = entity.id,
            cartId = entity.cartId,
            catalogItemId = entity.catalogItemId,
            sku = entity.sku,
            name = entity.name,
            unitPrice = entity.unitPrice,
            quantity = entity.quantity,
            lockedPrice = entity.lockedPrice,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun lineItemToEntity(item: CartLineItem): CartLineItemEntity {
        return CartLineItemEntity(
            id = item.id,
            cartId = item.cartId,
            catalogItemId = item.catalogItemId,
            sku = item.sku,
            name = item.name,
            unitPrice = item.unitPrice,
            quantity = item.quantity,
            lockedPrice = item.lockedPrice,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }
}
