package no.solver.solverappdemo.features.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverappdemo.BuildConfig
import no.solver.solverappdemo.core.config.APIEnvironment
import no.solver.solverappdemo.core.config.APIMode
import no.solver.solverappdemo.core.config.AppEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.config.DebugConfigurationManager
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject

data class DebugUiState(
    val isDebugModeEnabled: Boolean = false,
    val isMiddlewareDebugUIEnabled: Boolean = false,
    val apiMode: APIMode = APIMode.STAGING,
    val apiEnvironment: APIEnvironment = APIEnvironment.SOLVER,
    val currentAPIBaseURL: String = "",
    val cacheSize: Long = 0,
    val currentSession: Session? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val buildNumber: String = BuildConfig.VERSION_CODE.toString(),
    val packageName: String = BuildConfig.APPLICATION_ID,
    val buildType: String = if (BuildConfig.DEBUG) "Debug" else "Release",
    val buildEnvironment: String = AppEnvironment.current.name,
    val showClearCacheDialog: Boolean = false
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugConfigManager: DebugConfigurationManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _showClearCacheDialog = MutableStateFlow(false)
    val showClearCacheDialog: StateFlow<Boolean> = _showClearCacheDialog.asStateFlow()

    private val _cacheSize = MutableStateFlow(0L)

    val uiState: StateFlow<DebugUiState> = combine(
        debugConfigManager.isDebugModeEnabledFlow,
        debugConfigManager.isMiddlewareDebugUIEnabledFlow,
        debugConfigManager.apiModeFlow,
        debugConfigManager.apiEnvironmentFlow,
        debugConfigManager.getCurrentAPIBaseURL(AuthProvider.MICROSOFT),
        sessionManager.currentSessionFlow,
        _cacheSize,
        _showClearCacheDialog
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DebugUiState(
            isDebugModeEnabled = values[0] as Boolean,
            isMiddlewareDebugUIEnabled = values[1] as Boolean,
            apiMode = values[2] as APIMode,
            apiEnvironment = values[3] as APIEnvironment,
            currentAPIBaseURL = values[4] as String,
            currentSession = values[5] as Session?,
            cacheSize = values[6] as Long,
            showClearCacheDialog = values[7] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DebugUiState()
    )

    init {
        updateCacheSize()
    }

    fun setDebugModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            debugConfigManager.setDebugModeEnabled(enabled)
        }
    }

    fun setMiddlewareDebugUIEnabled(enabled: Boolean) {
        viewModelScope.launch {
            debugConfigManager.setMiddlewareDebugUIEnabled(enabled)
        }
    }

    fun setAPIMode(mode: APIMode) {
        viewModelScope.launch {
            debugConfigManager.setAPIMode(mode)
        }
    }

    fun setAPIEnvironment(environment: APIEnvironment) {
        viewModelScope.launch {
            debugConfigManager.setAPIEnvironment(environment)
        }
    }

    fun showClearCacheDialog() {
        _showClearCacheDialog.value = true
    }

    fun dismissClearCacheDialog() {
        _showClearCacheDialog.value = false
    }

    fun clearCache() {
        debugConfigManager.clearCache()
        _showClearCacheDialog.value = false
        updateCacheSize()
    }

    fun updateCacheSize() {
        _cacheSize.value = debugConfigManager.getCacheSizeKB()
    }
}
