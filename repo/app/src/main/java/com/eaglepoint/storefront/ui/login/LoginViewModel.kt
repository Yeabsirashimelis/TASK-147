package com.eaglepoint.storefront.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.domain.usecase.CreateUserUseCase
import com.eaglepoint.storefront.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val createUserUseCase: CreateUserUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<AuthResult>()
    val loginState: LiveData<AuthResult> = _loginState

    private val _isFirstLaunch = MutableLiveData<Boolean>()
    val isFirstLaunch: LiveData<Boolean> = _isFirstLaunch

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun checkFirstLaunch() {
        viewModelScope.launch {
            val hasUsers = authRepository.hasAnyUser()
            _isFirstLaunch.postValue(!hasUsers)
        }
    }

    fun login(username: String, password: CharArray) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = loginUseCase(username, password)
            _loginState.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun createFirstUser(username: String, password: CharArray) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = createUserUseCase(username, password)
            _loginState.postValue(result)
            _isLoading.postValue(false)
        }
    }
}
