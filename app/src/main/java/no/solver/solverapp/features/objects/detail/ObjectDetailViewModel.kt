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
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository
import no.solver.solverapp.features.auth.services.SessionManager
import no.solver.solverapp.features.objects.middleware.MiddlewareChain
import no.solver.solverapp.features.objects.middleware.PaymentMiddleware
import no.solver.solverapp.features.objects.middleware.SubscriptionMiddleware
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

    // Command execution state
    private val _executingCommandId = MutableStateFlow<String?>(null)
    val executingCommandId: StateFlow<String?> = _executingCommandId.asStateFlow()

    private val _lastExecuteResponse = MutableStateFlow<ExecuteResponse?>(null)
    val lastExecuteResponse: StateFlow<ExecuteResponse?> = _lastExecuteResponse.asStateFlow()

    private val _showExecuteResponse = MutableStateFlow(false)
    val showExecuteResponse: StateFlow<Boolean> = _showExecuteResponse.asStateFlow()

    private val _middlewareMessage = MutableStateFlow<String?>(null)
    val middlewareMessage: StateFlow<String?> = _middlewareMessage.asStateFlow()

    private val _commandError = MutableStateFlow<String?>(null)
    val commandError: StateFlow<String?> = _commandError.asStateFlow()

    // Input dialog state
    private val _showInputDialog = MutableStateFlow(false)
    val showInputDialog: StateFlow<Boolean> = _showInputDialog.asStateFlow()

    private val _pendingCommand = MutableStateFlow<Command?>(null)
    val pendingCommand: StateFlow<Command?> = _pendingCommand.asStateFlow()

    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    // Status sheet state
    private val _showStatusSheet = MutableStateFlow(false)
    val showStatusSheet: StateFlow<Boolean> = _showStatusSheet.asStateFlow()

    private val _statusSheetResponse = MutableStateFlow<ExecuteResponse?>(null)
    val statusSheetResponse: StateFlow<ExecuteResponse?> = _statusSheetResponse.asStateFlow()

    // Middleware instances
    val paymentMiddleware = PaymentMiddleware()
    val subscriptionMiddleware = SubscriptionMiddleware()

    private val middlewareChain: MiddlewareChain by lazy {
        MiddlewareChain.createStandardChain(
            repository = objectsRepository,
            paymentMiddleware = paymentMiddleware,
            subscriptionMiddleware = subscriptionMiddleware,
            onShowStatusSheet = { response ->
                _statusSheetResponse.value = response
                _showStatusSheet.value = true
            }
        )
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

    // MARK: - Command Execution

    fun handleCommand(command: Command) {
        if (command.requiresInput) {
            _pendingCommand.value = command
            _commandInput.value = ""
            _showInputDialog.value = true
        } else {
            executeCommand(command, input = null)
        }
    }

    fun updateCommandInput(input: String) {
        _commandInput.value = input
    }

    fun dismissInputDialog() {
        _showInputDialog.value = false
        _pendingCommand.value = null
        _commandInput.value = ""
    }

    fun executeCommandWithInput() {
        val command = _pendingCommand.value ?: return
        val input = _commandInput.value.ifEmpty { null }
        dismissInputDialog()
        executeCommand(command, input)
    }

    private fun executeCommand(command: Command, input: String?) {
        val obj = solverObject ?: run {
            Log.e(TAG, "Cannot execute command: no object loaded")
            return
        }

        viewModelScope.launch {
            Log.i(TAG, "Executing command '${command.commandName}' on object '${obj.name}'")
            _executingCommandId.value = command.commandName
            _commandError.value = null
            _lastExecuteResponse.value = null
            _middlewareMessage.value = null

            val result = objectsRepository.executeCommand(
                objectId = obj.id,
                command = command.commandName,
                input = input,
                latitude = null,  // TODO: Get user location if required
                longitude = null
            )

            _executingCommandId.value = null

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    Log.i(TAG, "Command '${command.commandName}' executed. Success: ${response.success}")
                    _lastExecuteResponse.value = response

                    // Process through middleware chain
                    val middlewareResult = middlewareChain.process(response, command, obj)
                    _middlewareMessage.value = middlewareResult.message

                    if (middlewareResult.shouldShowDebugUI) {
                        _showExecuteResponse.value = true
                    }

                    // Refresh object data if command was successful
                    if (response.success) {
                        refresh()
                    }
                }
                is ApiResult.Error -> {
                    val message = result.exception.message ?: "Unknown error"
                    Log.e(TAG, "Command execution failed: $message")
                    _commandError.value = "Failed to execute command: $message"
                }
            }
        }
    }

    fun isExecuting(command: Command): Boolean {
        return _executingCommandId.value == command.commandName
    }

    fun dismissExecuteResponse() {
        _showExecuteResponse.value = false
    }

    fun dismissStatusSheet() {
        _showStatusSheet.value = false
        _statusSheetResponse.value = null
    }

    fun dismissCommandError() {
        _commandError.value = null
    }
}
