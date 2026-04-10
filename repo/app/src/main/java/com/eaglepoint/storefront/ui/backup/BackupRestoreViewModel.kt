package com.eaglepoint.storefront.ui.backup

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.eaglepoint.storefront.domain.usecase.BackupUseCase
import com.eaglepoint.storefront.domain.usecase.RestoreUseCase
import kotlinx.coroutines.launch

class BackupRestoreViewModel(
    private val backupUseCase: BackupUseCase,
    private val restoreUseCase: RestoreUseCase,
    private val logAuditEvent: com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
) : ViewModel() {

    private val _backupResult = MutableLiveData<Result<BackupMetadata>>()
    val backupResult: LiveData<Result<BackupMetadata>> = _backupResult

    private val _restoreResult = MutableLiveData<Result<Unit>>()
    val restoreResult: LiveData<Result<Unit>> = _restoreResult

    private val _verifyResult = MutableLiveData<Result<BackupMetadata>>()
    val verifyResult: LiveData<Result<BackupMetadata>> = _verifyResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun createBackup(userId: String, password: CharArray, destinationUri: Uri) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = backupUseCase(userId, password, destinationUri)
            _backupResult.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun verifyBackup(sourceUri: Uri, metadataUri: Uri) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = restoreUseCase.verifyBackup(sourceUri, metadataUri)
            _verifyResult.postValue(result)
            _isLoading.postValue(false)
        }
    }

    fun restoreBackup(
        userId: String,
        password: CharArray,
        sourceUri: Uri,
        metadataUri: Uri
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = restoreUseCase(userId, password, sourceUri, metadataUri)
            _restoreResult.postValue(result)
            _isLoading.postValue(false)
        }
    }
}
