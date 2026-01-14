package no.solver.solverappdemo.features.more

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

data class LogsUiState(
    val logs: List<ObjectLog> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logsRepository: LogsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
    }

    fun loadLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.logs.isEmpty(),
                error = null
            )

            when (val result = logsRepository.fetchUserLogs()) {
                is ApiResult.Success -> {
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            when (val result = logsRepository.fetchUserLogs()) {
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
