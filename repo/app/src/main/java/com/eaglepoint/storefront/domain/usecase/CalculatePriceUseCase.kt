package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.pricing.PricingEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalculatePriceUseCase(
    private val catalogRepository: CatalogRepository,
    private val pricingEngine: PricingEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend operator fun invoke(cart: Cart, stateCode: String = DEFAULT_STATE): PricingResult =
        withContext(dispatcher) {
            val priceRules = catalogRepository.getActivePriceRules()
            val coupon = cart.couponId?.let { catalogRepository.findCouponById(it) }
            val taxRate = catalogRepository.getTaxRate(stateCode)

            pricingEngine.calculatePrice(cart, priceRules, coupon, taxRate)
        }

    companion object {
        const val DEFAULT_STATE = "CA"
    }
}
