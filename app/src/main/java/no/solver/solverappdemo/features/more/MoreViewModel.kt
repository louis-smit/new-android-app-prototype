package no.solver.solverappdemo.features.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.config.DebugConfigurationManager
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val debugConfigurationManager: DebugConfigurationManager
) : ViewModel() {

    val currentSession: StateFlow<Session?> = sessionManager.currentSessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isDebugModeEnabled: StateFlow<Boolean> = debugConfigurationManager.isDebugModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    fun signOut() {
        viewModelScope.launch {
            _isSigningOut.value = true
            sessionManager.clearAllSessions()
            _isSigningOut.value = false
        }
    }
}
