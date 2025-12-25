package no.solver.solverapp.features.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider
import no.solver.solverapp.features.auth.models.Session
import no.solver.solverapp.features.auth.services.AuthCancelledException
import no.solver.solverapp.features.auth.services.MicrosoftAuthService
import no.solver.solverapp.features.auth.services.SessionManager
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class SigningIn(val provider: AuthProvider) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    data object Success : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val microsoftAuthService: MicrosoftAuthService,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val isAuthenticated: StateFlow<Boolean> = sessionManager.currentSessionFlow
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val currentSession: StateFlow<Session?> = sessionManager.currentSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val authEnvironment: StateFlow<AuthEnvironment> = sessionManager.authEnvironmentFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthEnvironment.SOLVER
        )

    fun signInWithMicrosoft(activity: Activity) {
        viewModelScope.launch {
            Log.d(TAG, "Starting Microsoft sign-in")
            _uiState.value = LoginUiState.SigningIn(AuthProvider.MICROSOFT)

            try {
                val environment = sessionManager.getAuthEnvironment()
                microsoftAuthService.initialize(environment)
                
                val tokens = microsoftAuthService.signIn(activity)
                
                sessionManager.createSession(
                    provider = AuthProvider.MICROSOFT,
                    environment = environment,
                    tokens = tokens
                )

                Log.d(TAG, "Microsoft sign-in successful")
                _uiState.value = LoginUiState.Success
            } catch (e: AuthCancelledException) {
                Log.d(TAG, "Microsoft sign-in cancelled by user")
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Microsoft sign-in failed", e)
                _uiState.value = LoginUiState.Error(
                    e.message ?: "Sign-in failed. Please try again."
                )
            }
        }
    }

    fun setAuthEnvironment(environment: AuthEnvironment) {
        viewModelScope.launch {
            sessionManager.setAuthEnvironment(environment)
        }
    }

    fun clearError() {
        _uiState.value = LoginUiState.Idle
    }

    fun signOut() {
        viewModelScope.launch {
            Log.d(TAG, "Signing out")
            try {
                microsoftAuthService.signOut()
                sessionManager.clearAllSessions()
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            }
        }
    }
}
