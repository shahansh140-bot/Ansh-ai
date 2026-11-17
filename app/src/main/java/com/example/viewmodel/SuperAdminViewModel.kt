package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.FirestoreRepository
import com.example.model.AccountStatus
import com.example.model.AiRequestMetric
import com.example.model.AppConfig
import com.example.model.SystemErrorLog
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SuperAdminViewModel(
    private val firestoreRepo: FirestoreRepository
) : ViewModel() {

    val usersState: StateFlow<UiState<List<UserProfile>>> = firestoreRepo.observeAllUsersForAdmin()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Unauthorized or failed to load users"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val telemetryState: StateFlow<UiState<List<AiRequestMetric>>> = firestoreRepo.observeTelemetryForAdmin()
        .map<List<AiRequestMetric>, UiState<List<AiRequestMetric>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load telemetry"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val errorsState: StateFlow<UiState<List<SystemErrorLog>>> = firestoreRepo.observeSystemErrorsForAdmin()
        .map<List<SystemErrorLog>, UiState<List<SystemErrorLog>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load system errors"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appConfig: StateFlow<AppConfig> = firestoreRepo.observeAppConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppConfig())

    private val _adminToast = MutableStateFlow<String?>(null)
    val adminToast: StateFlow<String?> = _adminToast.asStateFlow()

    fun clearToast() {
        _adminToast.value = null
    }

    fun toggleUserStatus(user: UserProfile) {
        val nextStatus = if (user.accountStatusEnum() == AccountStatus.ACTIVE) {
            AccountStatus.DISABLED
        } else {
            AccountStatus.ACTIVE
        }
        viewModelScope.launch {
            val res = firestoreRepo.adminSetUserStatus(user.userId, nextStatus)
            _adminToast.value = if (res.isSuccess) {
                "${user.displayName} is now ${nextStatus.name}."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update user status."
            }
        }
    }

    fun updateGlobalConfig(config: AppConfig) {
        viewModelScope.launch {
            val res = firestoreRepo.adminUpdateGlobalConfig(config)
            _adminToast.value = if (res.isSuccess) {
                "Global configuration saved."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update configuration."
            }
        }
    }

    fun dismissError(errorId: String) {
        viewModelScope.launch {
            firestoreRepo.adminDeleteSystemError(errorId)
        }
    }

    companion object {
        fun provideFactory(firestoreRepo: FirestoreRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SuperAdminViewModel(firestoreRepo) }
            }
    }
}
