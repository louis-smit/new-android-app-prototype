package no.solver.solverapp.features.objects

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverapp.core.config.APIConfiguration
import no.solver.solverapp.core.network.ApiResult
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository
import no.solver.solverapp.features.auth.services.SessionManager
import javax.inject.Inject

sealed class ObjectsUiState {
    data object Loading : ObjectsUiState()
    data class Success(val objects: List<SolverObject>) : ObjectsUiState()
    data object Empty : ObjectsUiState()
    data class Error(val message: String) : ObjectsUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ObjectsViewModel @Inject constructor(
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "ObjectsViewModel"
        private const val SEARCH_DEBOUNCE_MS = 150L
    }

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
                    "https://api365-demo.solver.no" // Fallback
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
    }

    fun loadObjects() {
        viewModelScope.launch {
            Log.d(TAG, "Loading objects")
            _uiState.value = ObjectsUiState.Loading

            when (val result = objectsRepository.getUserObjects()) {
                is ApiResult.Success -> {
                    val objects = result.data
                    _allObjects.value = objects

                    _uiState.value = if (objects.isEmpty()) {
                        ObjectsUiState.Empty
                    } else {
                        ObjectsUiState.Success(objects)
                    }
                    Log.d(TAG, "Successfully loaded ${objects.size} objects")
                }
                is ApiResult.Error -> {
                    val message = result.exception.message ?: "Unknown error"
                    _uiState.value = ObjectsUiState.Error(message)
                    Log.e(TAG, "Failed to load objects: $message")
                }
            }
        }
    }

    fun refreshObjects() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing objects")
            _isRefreshing.value = true

            when (val result = objectsRepository.getUserObjects()) {
                is ApiResult.Success -> {
                    val objects = result.data
                    _allObjects.value = objects

                    _uiState.value = if (objects.isEmpty()) {
                        ObjectsUiState.Empty
                    } else {
                        ObjectsUiState.Success(objects)
                    }
                    Log.d(TAG, "Successfully refreshed ${objects.size} objects")
                }
                is ApiResult.Error -> {
                    // Don't change to error state if we already have data on refresh failure
                    if (_uiState.value is ObjectsUiState.Error) {
                        val message = result.exception.message ?: "Unknown error"
                        _uiState.value = ObjectsUiState.Error(message)
                    }
                    Log.e(TAG, "Failed to refresh objects: ${result.exception.message}")
                }
            }

            _isRefreshing.value = false
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

    // Statistics
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
