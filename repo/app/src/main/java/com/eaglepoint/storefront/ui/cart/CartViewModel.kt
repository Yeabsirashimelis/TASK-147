package com.eaglepoint.storefront.ui.cart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.Coupon
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.domain.usecase.AddToCartUseCase
import com.eaglepoint.storefront.domain.usecase.ApplyCouponUseCase
import com.eaglepoint.storefront.domain.usecase.CalculatePriceUseCase
import com.eaglepoint.storefront.domain.usecase.UpdateCartUseCase
import kotlinx.coroutines.launch

class CartViewModel(
    private val cartRepository: CartRepository,
    private val addToCartUseCase: AddToCartUseCase,
    private val updateCartUseCase: UpdateCartUseCase,
    private val applyCouponUseCase: ApplyCouponUseCase,
    private val calculatePriceUseCase: CalculatePriceUseCase
) : ViewModel() {

    private val _cart = MutableLiveData<Cart?>()
    val cart: LiveData<Cart?> = _cart

    private val _pricing = MutableLiveData<PricingResult>()
    val pricing: LiveData<PricingResult> = _pricing

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _couponResult = MutableLiveData<Result<Coupon>>()
    val couponResult: LiveData<Result<Coupon>> = _couponResult

    fun loadCart(cartId: String) {
        viewModelScope.launch {
            val cart = cartRepository.findById(cartId)
            _cart.postValue(cart)
            if (cart != null) recalculatePrice(cart)
        }
    }

    fun loadOrCreateCart(userId: String?) {
        viewModelScope.launch {
            val cart = cartRepository.getOrCreateCart(userId)
            _cart.postValue(cart)
            recalculatePrice(cart)
        }
    }

    fun addItem(catalogItemId: String, quantity: Int = 1) {
        val cartId = _cart.value?.id ?: return
        viewModelScope.launch {
            val result = addToCartUseCase(cartId, catalogItemId, quantity)
            result.onFailure { _error.postValue(it.message) }
            reloadCart(cartId)
        }
    }

    fun updateQuantity(itemId: String, catalogItemId: String, quantity: Int) {
        viewModelScope.launch {
            val result = updateCartUseCase.updateQuantity(itemId, catalogItemId, quantity)
            result.onFailure { _error.postValue(it.message) }
            _cart.value?.id?.let { reloadCart(it) }
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            updateCartUseCase.removeItem(itemId)
            _cart.value?.id?.let { reloadCart(it) }
        }
    }

    fun applyCoupon(code: String) {
        val cartId = _cart.value?.id ?: return
        viewModelScope.launch {
            val result = applyCouponUseCase.apply(cartId, code)
            _couponResult.postValue(result)
            result.onFailure { _error.postValue(it.message) }
            reloadCart(cartId)
        }
    }

    fun removeCoupon() {
        val cartId = _cart.value?.id ?: return
        viewModelScope.launch {
            applyCouponUseCase.remove(cartId)
            reloadCart(cartId)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun reloadCart(cartId: String) {
        val cart = cartRepository.findById(cartId)
        _cart.postValue(cart)
        if (cart != null) recalculatePrice(cart)
    }

    private suspend fun recalculatePrice(cart: Cart) {
        val pricing = calculatePriceUseCase(cart)
        _pricing.postValue(pricing)
    }
}
