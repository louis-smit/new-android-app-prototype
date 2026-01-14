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
import no.solver.solverappdemo.core.network.ConnectivityObserver
import no.solver.solverappdemo.core.network.NetworkStatus
import no.solver.solverappdemo.core.storage.FavouritesStore
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsLoadResult
import no.solver.solverappdemo.data.repositories.OfflineFirstObjectsRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
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
    private val iconCacheManager: IconCacheManager
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

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0 = All, 1 = Favourites
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val favourites: StateFlow<List<SolverObject>> = favouritesStore.favourites

    private val _isFavouritesLoading = MutableStateFlow(false)
    val isFavouritesLoading: StateFlow<Boolean> = _isFavouritesLoading.asStateFlow()

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
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { objects, query ->
        filterAndSortObjects(objects, query)
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
    }

    private fun observeAccountChanges() {
        viewModelScope.launch {
            var previousSessionId: String? = null
            sessionManager.currentSessionFlow.collect { session ->
                val currentSessionId = session?.id
                // Only reload if session actually changed (not on initial load)
                if (previousSessionId != null && currentSessionId != previousSessionId) {
                    Log.d(TAG, "Account switched, reloading objects")
                    // Reset state and reload for the new account
                    _allObjects.value = emptyList()
                    loadObjects()
                    loadFavourites()
                }
                previousSessionId = currentSessionId
            }
        }
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
