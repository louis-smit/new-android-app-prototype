package no.solver.solverappdemo.features.objects

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.cache.IconCacheManager
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.config.DataSimulationEvent
import no.solver.solverappdemo.core.config.DebugConfigurationManager
import no.solver.solverappdemo.core.network.ConnectivityObserver
import no.solver.solverappdemo.core.network.NetworkStatus
import no.solver.solverappdemo.core.storage.FavouritesStore
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsLoadResult
import no.solver.solverappdemo.data.repositories.OfflineFirstObjectsRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
import no.solver.solverappdemo.features.objects.filter.LabelFilter
import no.solver.solverappdemo.features.objects.filter.buildLabelFilters
import no.solver.solverappdemo.features.objects.filter.countActiveFilters
import no.solver.solverappdemo.features.objects.filter.filterObjectsByLabels
import javax.inject.Inject

sealed class ObjectsUiState {
    data object Loading : ObjectsUiState()
    data class Success(
        val objects: List<SolverObject>,
        val isFromCache: Boolean = false,
        val lastSyncedAt: Long? = null
    ) : ObjectsUiState()
    data object Empty : ObjectsUiState()
    data class EmptyOffline(val lastSyncedAt: Long?) : ObjectsUiState()
    data class Error(val message: String) : ObjectsUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ObjectsViewModel @Inject constructor(
    private val offlineFirstRepository: OfflineFirstObjectsRepository,
    private val sessionManager: SessionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val favouritesStore: FavouritesStore,
    private val iconCacheManager: IconCacheManager,
    private val debugConfigManager: DebugConfigurationManager
) : ViewModel() {

    companion object {
        private const val TAG = "ObjectsViewModel"
        private const val SEARCH_DEBOUNCE_MS = 150L
    }

    val isOffline: StateFlow<Boolean> = connectivityObserver.networkStatus
        .map { status -> status == NetworkStatus.Unavailable }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = !connectivityObserver.isConnected()
        )

    val apiBaseUrl: StateFlow<String> = sessionManager.currentSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
        .let { sessionFlow ->
            combine(sessionFlow, MutableStateFlow(Unit)) { session, _ ->
                if (session != null) {
                    APIConfiguration.current(
                        environment = session.environment,
                        provider = session.provider
                    ).baseURL
                } else {
                    "https://api365-demo.solver.no"
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = "https://api365-demo.solver.no"
            )
        }

    private val _uiState = MutableStateFlow<ObjectsUiState>(ObjectsUiState.Loading)
    val uiState: StateFlow<ObjectsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allObjects = MutableStateFlow<List<SolverObject>>(emptyList())
    
    // Store real objects for simulation reset
    private var _realObjects: List<SolverObject> = emptyList()
    private var _isSimulating = false

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    val currentSession = sessionManager.currentSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _selectedTab = MutableStateFlow(0) // 0 = All, 1 = Favourites
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val favourites: StateFlow<List<SolverObject>> = favouritesStore.favourites

    private val _isFavouritesLoading = MutableStateFlow(false)
    val isFavouritesLoading: StateFlow<Boolean> = _isFavouritesLoading.asStateFlow()

    private val _labelFilters = MutableStateFlow<List<LabelFilter>>(emptyList())
    val labelFilters: StateFlow<List<LabelFilter>> = _labelFilters.asStateFlow()

    val activeFilterCount: StateFlow<Int> = _labelFilters
        .map { countActiveFilters(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val filteredFavourites: StateFlow<List<SolverObject>> = combine(
        favouritesStore.favourites,
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { favs, query ->
        filterAndSortObjects(favs, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val filteredObjects: StateFlow<List<SolverObject>> = combine(
        _allObjects,
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS),
        _labelFilters
    ) { objects, query, filters ->
        val labelFiltered = filterObjectsByLabels(objects, filters)
        filterAndSortObjects(labelFiltered, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        loadObjects()
        loadFavourites()
        observeConnectivityChanges()
        observeAccountChanges()
        observeDataSimulation()
    }

    private fun observeAccountChanges() {
        viewModelScope.launch {
            var previousSessionId: String? = null
            sessionManager.currentSessionFlow.collect { session ->
                val currentSessionId = session?.id
                // Only reload if session actually changed (not on initial load)
                if (previousSessionId != null && currentSessionId != previousSessionId) {
                    Log.d(TAG, "Account switched, reloading objects")
                    // Reset simulation state
                    _isSimulating = false
                    _realObjects = emptyList()
                    // Reset state and reload for the new account
                    _allObjects.value = emptyList()
                    _labelFilters.value = emptyList()  // Reset label filters for new account
                    loadObjects()
                    loadFavourites()
                }
                previousSessionId = currentSessionId
            }
        }
    }

    private fun observeDataSimulation() {
        viewModelScope.launch {
            debugConfigManager.simulationEvents.collect { event ->
                when (event) {
                    is DataSimulationEvent.SimulateLargeDataset -> {
                        simulateLargeDataset(event.multiplier)
                    }
                    is DataSimulationEvent.ResetSimulation -> {
                        resetToRealData()
                    }
                }
            }
        }
    }

    private fun simulateLargeDataset(multiplier: Int) {
        Log.d(TAG, "Simulating large dataset with multiplier: $multiplier")
        
        // Store real objects if not already stored
        if (!_isSimulating) {
            _realObjects = _allObjects.value
        }
        _isSimulating = true
        
        // Duplicate objects
        val baseObjects = _realObjects.ifEmpty { _allObjects.value }
        val simulatedObjects = mutableListOf<SolverObject>()
        
        repeat(multiplier) { copyIndex ->
            baseObjects.forEach { obj ->
                val simulatedObj = obj.copy(
                    id = obj.id + (copyIndex + 1) * 1_000_000,
                    name = if (copyIndex == 0) obj.name else "${obj.name} (Copy ${copyIndex + 1})"
                )
                simulatedObjects.add(simulatedObj)
            }
        }
        
        _allObjects.value = simulatedObjects
        _labelFilters.value = buildLabelFilters(simulatedObjects, _labelFilters.value)
        
        _uiState.value = ObjectsUiState.Success(
            objects = simulatedObjects,
            isFromCache = false,
            lastSyncedAt = _lastSyncedAt.value
        )
        
        Log.d(TAG, "Simulated ${simulatedObjects.size} objects (${baseObjects.size} x $multiplier)")
    }

    private fun resetToRealData() {
        Log.d(TAG, "Resetting to real data")
        
        if (_isSimulating && _realObjects.isNotEmpty()) {
            _allObjects.value = _realObjects
            _labelFilters.value = buildLabelFilters(_realObjects, _labelFilters.value)
            
            _uiState.value = ObjectsUiState.Success(
                objects = _realObjects,
                isFromCache = false,
                lastSyncedAt = _lastSyncedAt.value
            )
            
            Log.d(TAG, "Restored ${_realObjects.size} real objects")
        }
        
        _isSimulating = false
        _realObjects = emptyList()
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun loadFavourites() {
        viewModelScope.launch {
            Log.d(TAG, "Loading favourites")
            _isFavouritesLoading.value = true
            favouritesStore.loadFavourites()
            _isFavouritesLoading.value = false
        }
    }

    private fun observeConnectivityChanges() {
        viewModelScope.launch {
            connectivityObserver.networkStatus.collect { status ->
                if (status == NetworkStatus.Available) {
                    val currentState = _uiState.value
                    if (currentState is ObjectsUiState.Success && currentState.isFromCache) {
                        Log.d(TAG, "Network restored, refreshing in background...")
                        refreshInBackground()
                    } else if (currentState is ObjectsUiState.EmptyOffline) {
                        loadObjects()
                    }
                }
            }
        }
    }

    fun loadObjects() {
        viewModelScope.launch {
            Log.d(TAG, "Loading objects")
            _uiState.value = ObjectsUiState.Loading

            when (val result = offlineFirstRepository.loadObjects()) {
                is ObjectsLoadResult.Success -> handleSuccess(result)
                is ObjectsLoadResult.Error -> handleError(result)
            }
        }
    }

    fun refreshObjects() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing objects")
            _isRefreshing.value = true

            when (val result = offlineFirstRepository.refreshObjects()) {
                is ObjectsLoadResult.Success -> handleSuccess(result)
                is ObjectsLoadResult.Error -> {
                    if (_allObjects.value.isEmpty()) {
                        handleError(result)
                    } else {
                        Log.w(TAG, "Refresh failed but keeping cached data: ${result.exception.message}")
                    }
                }
            }

            _isRefreshing.value = false
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            when (val result = offlineFirstRepository.refreshObjects()) {
                is ObjectsLoadResult.Success -> {
                    _allObjects.value = result.objects
                    _lastSyncedAt.value = result.lastSyncedAt
                    _uiState.value = ObjectsUiState.Success(
                        objects = result.objects,
                        isFromCache = false,
                        lastSyncedAt = result.lastSyncedAt
                    )
                    // Rebuild label filters with fresh data
                    _labelFilters.value = buildLabelFilters(result.objects, _labelFilters.value)
                    Log.d(TAG, "Background refresh completed: ${result.objects.size} objects")
                }
                is ObjectsLoadResult.Error -> {
                    Log.w(TAG, "Background refresh failed: ${result.exception.message}")
                }
            }
        }
    }

    private fun handleSuccess(result: ObjectsLoadResult.Success) {
        val objects = result.objects
        _allObjects.value = objects
        _lastSyncedAt.value = result.lastSyncedAt
        
        // Store as real objects if not simulating
        if (!_isSimulating) {
            _realObjects = objects
        }

        _labelFilters.value = buildLabelFilters(objects, _labelFilters.value)

        _uiState.value = if (objects.isEmpty()) {
            if (!connectivityObserver.isConnected()) {
                ObjectsUiState.EmptyOffline(result.lastSyncedAt)
            } else {
                ObjectsUiState.Empty
            }
        } else {
            ObjectsUiState.Success(
                objects = objects,
                isFromCache = result.isFromCache,
                lastSyncedAt = result.lastSyncedAt
            )
        }
        Log.d(TAG, "Loaded ${objects.size} objects (fromCache: ${result.isFromCache})")
    }

    private fun handleError(result: ObjectsLoadResult.Error) {
        val message = result.exception.message ?: "Unknown error"
        if (result.cachedObjects != null && result.cachedObjects.isNotEmpty()) {
            _allObjects.value = result.cachedObjects
            _lastSyncedAt.value = result.lastSyncedAt
            _uiState.value = ObjectsUiState.Success(
                objects = result.cachedObjects,
                isFromCache = true,
                lastSyncedAt = result.lastSyncedAt
            )
            Log.w(TAG, "Error but showing cached data: $message")
        } else {
            if (!connectivityObserver.isConnected()) {
                _uiState.value = ObjectsUiState.EmptyOffline(null)
            } else {
                _uiState.value = ObjectsUiState.Error(message)
            }
            Log.e(TAG, "Failed to load objects: $message")
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun retry() {
        loadObjects()
    }

    fun updateLabelFilter(filterId: String, optionValue: String, isChecked: Boolean) {
        _labelFilters.value = _labelFilters.value.map { filter ->
            if (filter.id == filterId) {
                filter.copy(
                    options = filter.options.map { option ->
                        if (option.value == optionValue) {
                            option.copy(isChecked = isChecked)
                        } else {
                            option
                        }
                    }
                )
            } else {
                filter
            }
        }
    }

    fun clearLabelFilters() {
        _labelFilters.value = _labelFilters.value.map { filter ->
            filter.copy(
                options = filter.options.map { it.copy(isChecked = false) }
            )
        }
    }

    private fun filterAndSortObjects(
        objects: List<SolverObject>,
        query: String
    ): List<SolverObject> {
        val trimmedQuery = query.trim()
        val sortedObjects = objects.sortedBy { it.name.lowercase() }

        if (trimmedQuery.isEmpty()) {
            return sortedObjects
        }

        val normalizedQuery = trimmedQuery.lowercase()

        return sortedObjects.filter { obj ->
            obj.name.lowercase().contains(normalizedQuery) ||
            obj.status.lowercase().contains(normalizedQuery) ||
            (obj.tenantName?.lowercase()?.contains(normalizedQuery) == true)
        }
    }

    val statistics: ObjectsStatistics
        get() {
            val objects = _allObjects.value
            return ObjectsStatistics(
                total = objects.size,
                online = objects.count { it.online },
                offline = objects.count { !it.online },
                active = objects.count { it.active },
                inactive = objects.count { !it.active },
                available = objects.count { it.isAvailable }
            )
        }

    /**
     * Get a cached icon bitmap for the given object type ID.
     * Returns null if not cached - the UI should fall back to network loading.
     */
    fun getCachedIcon(objectTypeId: Int): Bitmap? {
        return iconCacheManager.getIcon(objectTypeId)
    }
}

data class ObjectsStatistics(
    val total: Int,
    val online: Int,
    val offline: Int,
    val active: Int,
    val inactive: Int,
    val available: Int
) {
    val availabilityPercentage: Float
        get() = if (total > 0) available.toFloat() / total * 100 else 0f

    val onlinePercentage: Float
        get() = if (total > 0) online.toFloat() / total * 100 else 0f
}
