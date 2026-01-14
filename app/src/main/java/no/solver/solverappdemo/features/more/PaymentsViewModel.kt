package no.solver.solverappdemo.features.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.repositories.PaymentsRepository
import java.util.Locale
import javax.inject.Inject

data class PaymentsUiState(
    val guid: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val paymentUrl: String?
        get() {
            val guid = this.guid ?: return null
            val locale = Locale.getDefault().language
            return "https://user-payments.azurewebsites.net/$guid/agreements?lang=$locale"
        }
}

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val paymentsRepository: PaymentsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    init {
        fetchGuid()
    }

    fun fetchGuid() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            when (val result = paymentsRepository.getSignedUrl()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        guid = result.data,
                        isLoading = false,
                        error = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exception.message ?: "Failed to load payment data"
                    )
                }
            }
        }
    }
}
