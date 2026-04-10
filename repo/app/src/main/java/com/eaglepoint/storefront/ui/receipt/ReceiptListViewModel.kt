package com.eaglepoint.storefront.ui.receipt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Order
import com.eaglepoint.storefront.domain.usecase.GetOrdersUseCase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReceiptListViewModel(
    private val getOrdersUseCase: GetOrdersUseCase
) : ViewModel() {

    private val _orders = MutableLiveData<List<Order>>()
    val orders: LiveData<List<Order>> = _orders

    private val _orderDetail = MutableLiveData<Order?>()
    val orderDetail: LiveData<Order?> = _orderDetail

    fun loadOrders() {
        viewModelScope.launch {
            getOrdersUseCase.getMyOrders().collectLatest { list ->
                _orders.postValue(list)
            }
        }
    }

    fun loadOrderDetail(orderId: String) {
        viewModelScope.launch {
            val order = getOrdersUseCase.getOrderDetail(orderId)
            _orderDetail.postValue(order)
        }
    }
}
