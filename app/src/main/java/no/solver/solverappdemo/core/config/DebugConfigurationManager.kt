package no.solver.solverappdemo.core.config

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverappdemo.features.auth.services.SessionManager
import okhttp3.Cache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages debug configuration settings for the app.
 * Mirrors iOS DebugConfigurationManager functionality.
 */
@Singleton
class DebugConfigurationManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode_enabled")
        private val KEY_MIDDLEWARE_DEBUG_UI = booleanPreferencesKey("debug_middleware_ui")
        private val KEY_API_MODE = stringPreferencesKey("debug_api_mode")
        private val KEY_API_ENVIRONMENT = stringPreferencesKey("debug_api_environment")
        private val KEY_DATASET_MULTIPLIER = intPreferencesKey("debug_dataset_multiplier")
        private val KEY_SIMULATING_LARGE_DATASET = booleanPreferencesKey("debug_simulating_large_dataset")
    }

    // Debug Mode
    val isDebugModeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    // Middleware Debug UI
    val isMiddlewareDebugUIEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MIDDLEWARE_DEBUG_UI] ?: false
    }

    // API Mode (Staging/Production)
    val apiModeFlow: Flow<APIMode> = dataStore.data.map { prefs ->
        val modeName = prefs[KEY_API_MODE] ?: APIMode.STAGING.name
        try {
            APIMode.valueOf(modeName)
        } catch (e: Exception) {
            APIMode.STAGING
        }
    }

    // API Environment (Solver/Zohm)
    val apiEnvironmentFlow: Flow<APIEnvironment> = dataStore.data.map { prefs ->
        val envName = prefs[KEY_API_ENVIRONMENT] ?: APIEnvironment.SOLVER.name
        try {
            APIEnvironment.valueOf(envName)
        } catch (e: Exception) {
            APIEnvironment.SOLVER
        }
    }

    // Dataset Multiplier for simulation (default 5)
    val datasetMultiplierFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_DATASET_MULTIPLIER] ?: 5
    }

    // Is simulating large dataset
    val isSimulatingLargeDatasetFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SIMULATING_LARGE_DATASET] ?: false
    }

    // Simulation events for ObjectsViewModel to observe
    private val _simulationEvents = MutableSharedFlow<DataSimulationEvent>()
    val simulationEvents: SharedFlow<DataSimulationEvent> = _simulationEvents.asSharedFlow()

    // Current API base URL
    fun getCurrentAPIBaseURL(provider: AuthProvider = AuthProvider.MICROSOFT): Flow<String> {
        return dataStore.data.map { prefs ->
            val mode = try {
                val modeName = prefs[KEY_API_MODE] ?: APIMode.STAGING.name
                APIMode.valueOf(modeName)
            } catch (e: Exception) {
                APIMode.STAGING
            }

            val environment = try {
                val envName = prefs[KEY_API_ENVIRONMENT] ?: APIEnvironment.SOLVER.name
                APIEnvironment.valueOf(envName)
            } catch (e: Exception) {
                APIEnvironment.SOLVER
            }

            val config = APIConfiguration.current(
                environment = environment.toAuthEnvironment(),
                mode = mode.toAppEnvironment(),
                provider = provider
            )
            config.baseURL
        }
    }

    suspend fun getAPIMode(): APIMode {
        return apiModeFlow.first()
    }

    suspend fun setAPIMode(mode: APIMode) {
        dataStore.edit { prefs ->
            prefs[KEY_API_MODE] = mode.name
        }
    }

    suspend fun getAPIEnvironment(): APIEnvironment {
        return apiEnvironmentFlow.first()
    }

    suspend fun setAPIEnvironment(environment: APIEnvironment) {
        val previousEnvironment = getAPIEnvironment()

        dataStore.edit { prefs ->
            prefs[KEY_API_ENVIRONMENT] = environment.name
        }

        // Also update SessionManager's auth environment to keep them in sync
        sessionManager.setAuthEnvironment(environment.toAuthEnvironment())

        // If environment changed, clear accounts and cache
        if (previousEnvironment != environment) {
            clearAccountsAndCache()
        }
    }

    suspend fun isDebugModeEnabled(): Boolean {
        return isDebugModeEnabledFlow.first()
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DEBUG_MODE] = enabled
        }
    }

    suspend fun isMiddlewareDebugUIEnabled(): Boolean {
        return isMiddlewareDebugUIEnabledFlow.first()
    }

    suspend fun setMiddlewareDebugUIEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_MIDDLEWARE_DEBUG_UI] = enabled
        }
    }

    suspend fun getDatasetMultiplier(): Int {
        return datasetMultiplierFlow.first()
    }

    suspend fun setDatasetMultiplier(multiplier: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_DATASET_MULTIPLIER] = multiplier
        }
    }

    suspend fun isSimulatingLargeDataset(): Boolean {
        return isSimulatingLargeDatasetFlow.first()
    }

    suspend fun setSimulatingLargeDataset(simulating: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SIMULATING_LARGE_DATASET] = simulating
        }
    }

    suspend fun emitSimulateEvent(multiplier: Int) {
        setSimulatingLargeDataset(true)
        _simulationEvents.emit(DataSimulationEvent.SimulateLargeDataset(multiplier))
    }

    suspend fun emitResetEvent() {
        setSimulatingLargeDataset(false)
        _simulationEvents.emit(DataSimulationEvent.ResetSimulation)
    }

    /**
     * Clear cache - clears OkHttp cache, WebView storage, and cookies
     */
    fun clearCache() {
        scope.launch {
            // Clear OkHttp cache
            try {
                val cacheDir = File(context.cacheDir, "http_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            } catch (e: Exception) {
                // Ignore cache clear errors
            }

            // Clear WebView data on main thread
            launch(Dispatchers.Main) {
                try {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                } catch (e: Exception) {
                    // Ignore WebView clear errors
                }
            }
        }
    }

    /**
     * Get approximate cache size in KB
     */
    fun getCacheSizeKB(): Long {
        return try {
            val cacheDir = File(context.cacheDir, "http_cache")
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun clearAccountsAndCache() {
        // Clear all sessions when switching environments
        sessionManager.clearAllSessions()
        clearCache()
    }
}

/**
 * API Mode - Staging vs Production
 */
enum class APIMode {
    STAGING,
    PRODUCTION;

    val displayName: String
        get() = when (this) {
            STAGING -> "Staging"
            PRODUCTION -> "Prod"
        }

    fun toAppEnvironment(): AppEnvironment = when (this) {
        STAGING -> AppEnvironment.STAGING
        PRODUCTION -> AppEnvironment.PRODUCTION
    }
}

/**
 * API Environment - Solver vs Zohm
 */
enum class APIEnvironment {
    SOLVER,
    ZOHM;

    val displayName: String
        get() = when (this) {
            SOLVER -> "Solver"
            ZOHM -> "Zohm"
        }

    fun toAuthEnvironment(): AuthEnvironment = when (this) {
        SOLVER -> AuthEnvironment.SOLVER
        ZOHM -> AuthEnvironment.ZOHM
    }
}

/**
 * Events for data simulation (large dataset testing)
 */
sealed class DataSimulationEvent {
    data class SimulateLargeDataset(val multiplier: Int) : DataSimulationEvent()
    object ResetSimulation : DataSimulationEvent()
}
