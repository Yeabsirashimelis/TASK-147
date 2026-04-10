package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.Cart

class MergeCartsUseCase(
    private val cartRepository: CartRepository,
    private val logAuditEvent: LogAuditEventUseCase
) {

    suspend operator fun invoke(guestCartId: String, userId: String): Result<Cart> {
        return try {
            val mergedCart = cartRepository.mergeGuestCartIntoUser(guestCartId, userId)

            logAuditEvent(
                userId = userId,
                action = AuditAction.CART_MERGED,
                target = "cart",
                targetId = mergedCart.id,
                detail = "Guest cart $guestCartId merged into user cart"
            )

            Result.success(mergedCart)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
