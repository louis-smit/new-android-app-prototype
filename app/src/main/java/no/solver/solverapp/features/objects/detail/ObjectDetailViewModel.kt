package no.solver.solverapp.features.objects.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.solver.solverapp.core.config.APIConfiguration
import no.solver.solverapp.core.network.ApiResult
import no.solver.solverapp.data.models.Command
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository
import no.solver.solverapp.features.auth.services.SessionManager
import no.solver.solverapp.ui.navigation.NavRoute
import javax.inject.Inject

sealed class ObjectDetailUiState {
    data object Loading : ObjectDetailUiState()
    data class Success(val solverObject: SolverObject) : ObjectDetailUiState()
    data class Error(val message: String) : ObjectDetailUiState()
}

@HiltViewModel
class ObjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "ObjectDetailViewModel"
    }

    private val route = savedStateHandle.toRoute<NavRoute.ObjectDetail>()
    val objectId: Int = route.objectId

    private val _uiState = MutableStateFlow<ObjectDetailUiState>(ObjectDetailUiState.Loading)
    val uiState: StateFlow<ObjectDetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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

    init {
        loadObject()
    }

    fun loadObject() {
        viewModelScope.launch {
            Log.d(TAG, "Loading object $objectId")
            _uiState.value = ObjectDetailUiState.Loading

            when (val result = objectsRepository.getObject(objectId)) {
                is ApiResult.Success -> {
                    val solverObject = result.data
                    _uiState.value = ObjectDetailUiState.Success(solverObject)
                    Log.d(TAG, "Successfully loaded object: ${solverObject.name}")
                }
                is ApiResult.Error -> {
                    val message = result.exception.message ?: "Unknown error"
                    _uiState.value = ObjectDetailUiState.Error(message)
                    Log.e(TAG, "Failed to load object: $message")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing object $objectId")
            _isRefreshing.value = true

            when (val result = objectsRepository.getObject(objectId)) {
                is ApiResult.Success -> {
                    val solverObject = result.data
                    _uiState.value = ObjectDetailUiState.Success(solverObject)
                    Log.d(TAG, "Successfully refreshed object: ${solverObject.name}")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to refresh object: ${result.exception.message}")
                }
            }

            _isRefreshing.value = false
        }
    }

    fun retry() {
        loadObject()
    }

    val solverObject: SolverObject?
        get() = (_uiState.value as? ObjectDetailUiState.Success)?.solverObject

    val commands: List<Command>
        get() = solverObject?.getCommands() ?: emptyList()
}
