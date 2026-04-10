package com.eaglepoint.storefront.ui.checkout

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CheckoutResult
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.domain.usecase.CalculatePriceUseCase
import com.eaglepoint.storefront.domain.usecase.CheckoutUseCase
import kotlinx.coroutines.launch

class CheckoutViewModel(
    private val cartRepository: CartRepository,
    private val checkoutUseCase: CheckoutUseCase,
    private val calculatePriceUseCase: CalculatePriceUseCase
) : ViewModel() {

    private val _cart = MutableLiveData<Cart?>()
    val cart: LiveData<Cart?> = _cart

    private val _pricing = MutableLiveData<PricingResult>()
    val pricing: LiveData<PricingResult> = _pricing

    private val _checkoutResult = MutableLiveData<CheckoutResult?>()
    val checkoutResult: LiveData<CheckoutResult?> = _checkoutResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _priceLockExpired = MutableLiveData(false)
    val priceLockExpired: LiveData<Boolean> = _priceLockExpired

    fun loadCheckout(cartId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val startResult = checkoutUseCase.startCheckout(cartId)
            startResult.onFailure {
                _checkoutResult.postValue(CheckoutResult(success = false, error = it.message))
                _isLoading.postValue(false)
                return@launch
            }

            val cart = cartRepository.findById(cartId)
            _cart.postValue(cart)
            if (cart != null) {
                val pricing = calculatePriceUseCase(cart)
                _pricing.postValue(pricing)
            }
            _isLoading.postValue(false)
        }
    }

    fun completeCheckout(cartId: String, stateCode: String, confirmedPriceChange: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = checkoutUseCase.completeCheckout(cartId, stateCode, confirmedPriceChange)
            _checkoutResult.postValue(result)

            if (result.requiresPriceReconfirm) {
                _priceLockExpired.postValue(true)
                // Recalculate with current prices
                val cart = cartRepository.findById(cartId)
                _cart.postValue(cart)
                if (cart != null) {
                    val pricing = calculatePriceUseCase(cart, stateCode)
                    _pricing.postValue(pricing)
                }
            }
            _isLoading.postValue(false)
        }
    }
}
