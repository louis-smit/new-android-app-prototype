package no.solver.solverappdemo.features.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject

data class AccountsUiState(
    val sessions: List<Session> = emptyList(),
    val currentSessionId: String? = null,
    val isEditing: Boolean = false,
    val showAddAccount: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val currentSession: Session?
        get() = sessions.find { it.id == currentSessionId }

    val hasMultipleAccounts: Boolean
        get() = sessions.size > 1

    val sortedSessions: List<Session>
        get() = sessions.sortedBy { it.displayName.lowercase() }
}

sealed class AccountsEvent {
    data object NavigateToMicrosoftLogin : AccountsEvent()
    data object NavigateToVippsLogin : AccountsEvent()
    data object NavigateToMobileLogin : AccountsEvent()
    data object AllAccountsRemoved : AccountsEvent()
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AccountsEvent>()
    val events: SharedFlow<AccountsEvent> = _events.asSharedFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            combine(
                sessionManager.allSessionsFlow,
                sessionManager.currentSessionFlow
            ) { sessions, currentSession ->
                Pair(sessions, currentSession)
            }.collect { (sessions, currentSession) ->
                _uiState.update {
                    it.copy(
                        sessions = sessions,
                        currentSessionId = currentSession?.id
                    )
                }
            }
        }
    }

    fun onToggleEdit() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    fun onSwitch(sessionId: String) {
        if (sessionId == _uiState.value.currentSessionId) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                sessionManager.switchToSession(sessionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to switch account") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onRemove(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                sessionManager.removeSession(sessionId)

                val remainingSessions = sessionManager.getAllSessions()
                if (remainingSessions.isEmpty()) {
                    _events.emit(AccountsEvent.AllAccountsRemoved)
                } else if (remainingSessions.size <= 1) {
                    // Exit editing mode when only one account remains
                    _uiState.update { it.copy(isEditing = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to remove account") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onShowAddAccount() {
        _uiState.update { it.copy(showAddAccount = true) }
    }

    fun onHideAddAccount() {
        _uiState.update { it.copy(showAddAccount = false) }
    }

    fun onAddMicrosoftAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(showAddAccount = false) }
            _events.emit(AccountsEvent.NavigateToMicrosoftLogin)
        }
    }

    fun onAddVippsAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(showAddAccount = false) }
            _events.emit(AccountsEvent.NavigateToVippsLogin)
        }
    }

    fun onAddMobileAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(showAddAccount = false) }
            _events.emit(AccountsEvent.NavigateToMobileLogin)
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
