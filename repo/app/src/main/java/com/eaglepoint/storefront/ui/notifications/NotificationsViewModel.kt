package com.eaglepoint.storefront.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.usecase.GetNotificationsUseCase
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val getNotificationsUseCase: GetNotificationsUseCase
) : ViewModel() {

    private val _recipientId = MutableLiveData<String>()

    val notifications: LiveData<List<Notification>> = _recipientId.switchMap { id ->
        getNotificationsUseCase.getAll(id).asLiveData()
    }

    val unreadCount: LiveData<Int> = _recipientId.switchMap { id ->
        getNotificationsUseCase.getUnreadCount(id).asLiveData()
    }

    fun setRecipient(recipientId: String) {
        _recipientId.value = recipientId
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            getNotificationsUseCase.markRead(notificationId)
        }
    }

    fun markAllRead() {
        val recipientId = _recipientId.value ?: return
        viewModelScope.launch {
            getNotificationsUseCase.markAllRead(recipientId)
        }
    }
}
