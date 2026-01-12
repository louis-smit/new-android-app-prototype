package no.solver.solverappdemo.features.find

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject

sealed class FindUiState {
    data object Idle : FindUiState()
    data object Loading : FindUiState()
    data class Success(val objects: List<SolverObject>) : FindUiState()
    data class Error(val message: String) : FindUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class FindViewModel @Inject constructor(
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "FindViewModel"
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MIN_QUERY_LENGTH = 2
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<FindUiState>(FindUiState.Idle)
    val uiState: StateFlow<FindUiState> = _uiState.asStateFlow()

    val apiBaseUrl: StateFlow<String> = sessionManager.currentSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
        .let { sessionFlow ->
            kotlinx.coroutines.flow.combine(sessionFlow, MutableStateFlow(Unit)) { session, _ ->
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

    init {
        observeSearchQuery()
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    private suspend fun performSearch(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isEmpty()) {
            _uiState.value = FindUiState.Idle
            return
        }

        if (trimmedQuery.length < MIN_QUERY_LENGTH) {
            _uiState.value = FindUiState.Idle
            return
        }

        Log.d(TAG, "Performing search for query: '$trimmedQuery'")
        _uiState.value = FindUiState.Loading

        when (val result = objectsRepository.searchObjects(trimmedQuery)) {
            is ApiResult.Success -> {
                Log.d(TAG, "Search completed with ${result.data.size} results")
                _uiState.value = FindUiState.Success(result.data)
            }
            is ApiResult.Error -> {
                val errorMessage = result.exception.message ?: "Search failed"
                Log.e(TAG, "Search failed: $errorMessage")
                _uiState.value = FindUiState.Error(errorMessage)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun retry() {
        viewModelScope.launch {
            performSearch(_searchQuery.value)
        }
    }
}
