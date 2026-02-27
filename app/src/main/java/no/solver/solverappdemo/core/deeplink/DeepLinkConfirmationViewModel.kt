package no.solver.solverappdemo.core.deeplink

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.repositories.TagRepository
import javax.inject.Inject

@HiltViewModel
class DeepLinkConfirmationViewModel @Inject constructor(
    private val tagRepository: TagRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DeepLinkConfirmVM"
    }

    private val _uiState = MutableStateFlow<ConfirmationState>(ConfirmationState.Loading)
    val uiState: StateFlow<ConfirmationState> = _uiState.asStateFlow()

    fun resolve(uri: Uri) {
        val deepLink = DeepLinkParser.parse(uri)
        if (deepLink !is DeepLink.QrCommand) {
            _uiState.value = ConfirmationState.Error("Invalid link")
            return
        }

        viewModelScope.launch {
            _uiState.value = ConfirmationState.Loading

            when (val result = tagRepository.getObjectByTag(deepLink.tag)) {
                is ApiResult.Success -> {
                    val obj = result.data
                    Log.i(TAG, "Resolved tag ${deepLink.tag} → ${obj.name}")
                    _uiState.value = ConfirmationState.Ready(
                        command = deepLink.command,
                        tag = deepLink.tag,
                        objectName = obj.name,
                        objectTypeId = obj.objectTypeId
                    )
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to resolve tag: ${deepLink.tag}")
                    _uiState.value = ConfirmationState.Error(
                        result.exception.message ?: "Could not find object"
                    )
                }
            }
        }
    }
}
