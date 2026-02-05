package no.solver.solverappdemo.features.objects.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.ObjectLog
import no.solver.solverappdemo.data.repositories.LogsRepository
import javax.inject.Inject

data class ObjectLogsUiState(
    val logs: List<ObjectLog> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ObjectLogsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val logsRepository: LogsRepository
) : ViewModel() {

    private val objectId: Int = savedStateHandle.get<Int>("objectId") ?: 0

    private val _uiState = MutableStateFlow(ObjectLogsUiState())
    val uiState: StateFlow<ObjectLogsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadLogsIfNeeded() {
        if (!hasLoaded) {
            loadLogs()
        }
    }

    fun loadLogs() {
        if (objectId == 0) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Invalid object ID"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.logs.isEmpty(),
                error = null
            )

            when (val result = logsRepository.fetchObjectLogs(objectId)) {
                is ApiResult.Success -> {
                    hasLoaded = true
                    _uiState.value = _uiState.value.copy(
                        logs = result.data,
                        isLoading = false,
                        error = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exception.message ?: "Failed to load logs"
                    )
                }
            }
        }
    }

    fun refresh() {
        if (objectId == 0) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            when (val result = logsRepository.fetchObjectLogs(objectId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        logs = result.data,
                        isRefreshing = false,
                        error = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.exception.message ?: "Failed to refresh logs"
                    )
                }
            }
        }
    }
}
