package no.solver.solverappdemo.features.auth

import android.app.Activity
import android.content.Intent
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
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.AuthCancelledException
import no.solver.solverappdemo.features.auth.services.MicrosoftAuthService
import no.solver.solverappdemo.features.auth.services.SessionManager
import no.solver.solverappdemo.features.auth.services.VippsAuthException
import no.solver.solverappdemo.features.auth.services.VippsAuthService
import no.solver.solverappdemo.data.repositories.IconRepository
import no.solver.solverappdemo.data.repositories.OfflineFirstObjectsRepository
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
    private val vippsAuthService: VippsAuthService,
    private val sessionManager: SessionManager,
    private val offlineFirstRepository: OfflineFirstObjectsRepository,
    private val iconRepository: IconRepository
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
                prefetchIcons()
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

    /**
     * Start Vipps sign-in flow using AppAuth.
     * @param activity The current activity
     * @param launchForResult Callback to launch the OAuth intent via startActivityForResult
     */
    fun signInWithVipps(activity: Activity, launchForResult: (Intent, Int) -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "Starting Vipps sign-in with AppAuth")
            _uiState.value = LoginUiState.SigningIn(AuthProvider.VIPPS)

            try {
                val environment = sessionManager.getAuthEnvironment()
                vippsAuthService.initialize(environment)

                val tokens = vippsAuthService.signIn(activity, launchForResult)

                sessionManager.createSession(
                    provider = AuthProvider.VIPPS,
                    environment = environment,
                    tokens = tokens
                )

                Log.d(TAG, "Vipps sign-in successful")
                prefetchIcons()
                _uiState.value = LoginUiState.Success
            } catch (e: AuthCancelledException) {
                Log.d(TAG, "Vipps sign-in cancelled by user")
                _uiState.value = LoginUiState.Idle
            } catch (e: VippsAuthException) {
                Log.e(TAG, "Vipps sign-in failed", e)
                _uiState.value = LoginUiState.Error(
                    e.message ?: "Vipps sign-in failed. Please try again."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Vipps sign-in failed", e)
                _uiState.value = LoginUiState.Error(
                    e.message ?: "Sign-in failed. Please try again."
                )
            }
        }
    }

    /**
     * Handle Vipps OAuth result from onActivityResult (AppAuth callback)
     */
    fun handleVippsAuthResult(data: Intent?) {
        Log.d(TAG, "Handling Vipps auth result")
        vippsAuthService.handleAuthResult(data)
    }

    /**
     * Cancel any pending Vipps auth flow
     */
    fun cancelPendingVippsAuth() {
        vippsAuthService.cancelPendingAuth()
    }
    
    /**
     * Check if there's a pending Vipps auth flow
     */
    fun hasPendingVippsAuth(): Boolean = vippsAuthService.hasPendingAuth()

    fun setAuthEnvironment(environment: AuthEnvironment) {
        viewModelScope.launch {
            sessionManager.setAuthEnvironment(environment)
        }
    }

    fun clearError() {
        _uiState.value = LoginUiState.Idle
    }

    /**
     * Pre-fetch all object type icons in the background for smooth scrolling.
     */
    private fun prefetchIcons() {
        viewModelScope.launch {
            try {
                iconRepository.prefetchIcons()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prefetch icons", e)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            Log.d(TAG, "Signing out")
            try {
                microsoftAuthService.signOut()
                offlineFirstRepository.clearAllCache()
                sessionManager.clearAllSessions()
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            }
        }
    }
}
