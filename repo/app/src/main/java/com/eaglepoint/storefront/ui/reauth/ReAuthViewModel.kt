package com.eaglepoint.storefront.ui.reauth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.usecase.ReAuthenticateUseCase
import kotlinx.coroutines.launch

class ReAuthViewModel(
    private val reAuthenticateUseCase: ReAuthenticateUseCase
) : ViewModel() {

    private val _reAuthResult = MutableLiveData<Boolean>()
    val reAuthResult: LiveData<Boolean> = _reAuthResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun reAuthenticate(userId: String, password: CharArray) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = reAuthenticateUseCase(userId, password)
            _reAuthResult.postValue(result)
            _isLoading.postValue(false)
        }
    }
}
