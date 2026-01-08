package no.solver.solverappdemo.features.favourites

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
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.storage.FavouritesStore
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject

sealed class FavouritesUiState {
    data object Loading : FavouritesUiState()
    data class Success(val favourites: List<SolverObject>) : FavouritesUiState()
    data object Empty : FavouritesUiState()
    data class Error(val message: String) : FavouritesUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favouritesStore: FavouritesStore,
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "FavouritesViewModel"
        private const val SEARCH_DEBOUNCE_MS = 150L
    }

    private val _uiState = MutableStateFlow<FavouritesUiState>(FavouritesUiState.Loading)
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    val filteredFavourites: StateFlow<List<SolverObject>> = combine(
        favouritesStore.favourites,
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS)
    ) { favourites, query ->
        filterAndSortFavourites(favourites, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        loadFavourites()
    }

    fun loadFavourites() {
        viewModelScope.launch {
            Log.d(TAG, "Loading favourites")
            _uiState.value = FavouritesUiState.Loading

            favouritesStore.loadFavourites(objectsRepository)

            val favourites = favouritesStore.favourites.value
            _uiState.value = if (favourites.isEmpty()) {
                FavouritesUiState.Empty
            } else {
                FavouritesUiState.Success(favourites)
            }
            Log.d(TAG, "Loaded ${favourites.size} favourites")
        }
    }

    fun refreshFavourites() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing favourites")
            _isRefreshing.value = true

            favouritesStore.loadFavourites(objectsRepository)

            val favourites = favouritesStore.favourites.value
            _uiState.value = if (favourites.isEmpty()) {
                FavouritesUiState.Empty
            } else {
                FavouritesUiState.Success(favourites)
            }

            _isRefreshing.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun retry() {
        loadFavourites()
    }

    private fun filterAndSortFavourites(
        favourites: List<SolverObject>,
        query: String
    ): List<SolverObject> {
        val trimmedQuery = query.trim()
        val sortedFavourites = favourites.sortedBy { it.name.lowercase() }

        if (trimmedQuery.isEmpty()) {
            return sortedFavourites
        }

        val normalizedQuery = trimmedQuery.lowercase()

        return sortedFavourites.filter { obj ->
            obj.name.lowercase().contains(normalizedQuery) ||
            obj.status.lowercase().contains(normalizedQuery) ||
            (obj.tenantName?.lowercase()?.contains(normalizedQuery) == true)
        }
    }
}
