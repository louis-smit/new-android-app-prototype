package no.solver.solverappdemo.features.objects.detail

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
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.core.storage.FavouritesStore
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
import no.solver.solverappdemo.features.objects.middleware.DanalockMiddleware
import no.solver.solverappdemo.features.objects.middleware.MasterlockMiddleware
import no.solver.solverappdemo.features.objects.middleware.MiddlewareChain
import no.solver.solverappdemo.features.objects.middleware.PaymentMiddleware
import no.solver.solverappdemo.features.objects.middleware.SubscriptionMiddleware
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockBrand
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockCapabilities
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockManager
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockState
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockStatus
import no.solver.solverappdemo.ui.navigation.NavRoute
import javax.inject.Inject

sealed class ObjectDetailUiState {
    data object Loading : ObjectDetailUiState()
    data class Success(val solverObject: SolverObject) : ObjectDetailUiState()
    data class Error(val message: String) : ObjectDetailUiState()
}

/**
 * Cached operation state for smart lock UI
 */
enum class CachedOperation {
    NONE,
    UNLOCKING,
    LOCKING,
    CHECKING_STATUS,
    FETCHING_KEYS
}

@HiltViewModel
class ObjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager,
    private val favouritesStore: FavouritesStore,
    private val danalockMiddleware: DanalockMiddleware,
    private val masterlockMiddleware: MasterlockMiddleware,
    private val smartLockManager: SmartLockManager
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

    // Details sheet state
    private val _showDetailsSheet = MutableStateFlow(false)
    val showDetailsSheet: StateFlow<Boolean> = _showDetailsSheet.asStateFlow()

    // Info sheet state (HTML content)
    private val _showInfoSheet = MutableStateFlow(false)
    val showInfoSheet: StateFlow<Boolean> = _showInfoSheet.asStateFlow()

    // Smart lock state
    private val _lockBrand = MutableStateFlow<SmartLockBrand?>(null)
    val lockBrand: StateFlow<SmartLockBrand?> = _lockBrand.asStateFlow()

    private val _lockStatus = MutableStateFlow(SmartLockStatus.UNKNOWN)
    val lockStatus: StateFlow<SmartLockStatus> = _lockStatus.asStateFlow()

    private val _cachedOperation = MutableStateFlow(CachedOperation.NONE)
    val cachedOperation: StateFlow<CachedOperation> = _cachedOperation.asStateFlow()

    private val _cachedUnlockResult = MutableStateFlow<String?>(null)
    val cachedUnlockResult: StateFlow<String?> = _cachedUnlockResult.asStateFlow()

    val lockState: StateFlow<SmartLockState>
        get() = combine(_lockStatus, MutableStateFlow(Unit)) { status, _ ->
            status.state
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SmartLockState.UNKNOWN)

    val lockBatteryLevel: StateFlow<Int?>
        get() = combine(_lockStatus, MutableStateFlow(Unit)) { status, _ ->
            status.batteryLevel
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lockInRange: StateFlow<Boolean>
        get() = combine(_lockStatus, MutableStateFlow(Unit)) { status, _ ->
            status.inRange
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasLockTokens: StateFlow<Boolean>
        get() = combine(_lockStatus, MutableStateFlow(Unit)) { status, _ ->
            status.hasValidTokens
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lockCapabilities: StateFlow<SmartLockCapabilities?>
        get() = combine(_lockBrand, MutableStateFlow(Unit)) { brand, _ ->
            when (brand) {
                SmartLockBrand.DANALOCK -> SmartLockCapabilities.DANALOCK
                SmartLockBrand.MASTERLOCK -> SmartLockCapabilities.MASTERLOCK
                null -> null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Favourite state
    val isFavourite: StateFlow<Boolean> = favouritesStore.favouriteIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )
        .let { idsFlow ->
            combine(idsFlow, MutableStateFlow(objectId)) { ids, objId ->
                ids.contains(objId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )
        }

    // Middleware instances
    val paymentMiddleware = PaymentMiddleware()
    val subscriptionMiddleware = SubscriptionMiddleware()

    private val middlewareChain: MiddlewareChain by lazy {
        MiddlewareChain.createStandardChain(
            repository = objectsRepository,
            paymentMiddleware = paymentMiddleware,
            subscriptionMiddleware = subscriptionMiddleware,
            danalockMiddleware = danalockMiddleware,
            masterlockMiddleware = masterlockMiddleware,
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
                    
                    // Set up smart lock state if applicable
                    setupSmartLockState(solverObject)
                }
                is ApiResult.Error -> {
                    val message = result.exception.message ?: "Unknown error"
                    _uiState.value = ObjectDetailUiState.Error(message)
                    Log.e(TAG, "Failed to load object: $message")
                }
            }
        }
    }
    
    private fun setupSmartLockState(solverObject: SolverObject) {
        val brand = smartLockManager.lockBrand(solverObject)
        _lockBrand.value = brand
        
        if (brand != null) {
            Log.d(TAG, "Object is a ${brand.displayName} smart lock")
            val adapter = smartLockManager.adapter(brand)
            
            // Observe the adapter's status flow
            viewModelScope.launch {
                adapter.status.collect { status ->
                    _lockStatus.value = status
                }
            }
            
            // Auto-fetch keys if missing/expired, otherwise start polling
            viewModelScope.launch {
                autoFetchKeysIfNeeded(solverObject)
            }
        }
    }
    
    /**
     * Automatically fetch and cache smart lock keys if they don't exist or are expired.
     * Matches iOS autoFetchKeysIfNeeded behavior.
     */
    private suspend fun autoFetchKeysIfNeeded(solverObject: SolverObject) {
        val brand = _lockBrand.value ?: return
        val adapter = smartLockManager.adapter(brand)
        
        if (adapter.hasValidTokens(objectId)) {
            Log.i(TAG, "🔑 ${brand.displayName} has valid cached keys, skipping auto-fetch")
            
            // Show current cache status
            _cachedUnlockResult.value = "✅ Keys cached successfully"
            
            // Start polling with cached tokens
            adapter.startPolling(solverObject)
        } else {
            Log.i(TAG, "🔑 ${brand.displayName} keys missing or expired, auto-fetching...")
            fetchAndCacheKeys()
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

    // MARK: - Menu Actions

    fun toggleFavourite() {
        val obj = solverObject ?: return
        viewModelScope.launch {
            Log.i(TAG, "Toggling favourite for object ${obj.id}")

            val previousIds = favouritesStore.favouriteIds.value
            val previousFavourites = favouritesStore.favourites.value
            val wasFavourite = favouritesStore.isFavouriteLocal(obj.id)

            // Optimistic update
            if (wasFavourite) {
                favouritesStore.applyOptimisticRemove(obj.id)
            } else {
                favouritesStore.applyOptimisticAdd(obj)
            }

            // Make API call
            val result = if (wasFavourite) {
                favouritesStore.removeFavourite(obj.id)
            } else {
                favouritesStore.addFavourite(obj.id)
            }

            when (result) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Favourite toggled successfully: $wasFavourite -> ${!wasFavourite}")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to toggle favourite: ${result.exception.message}")
                    // Rollback on error
                    favouritesStore.rollback(previousIds, previousFavourites)
                    _commandError.value = "Failed to update favourite"
                }
            }
        }
    }

    fun showDetailsSheet() {
        _showDetailsSheet.value = true
    }

    fun dismissDetailsSheet() {
        _showDetailsSheet.value = false
    }

    fun showInfoSheet() {
        _showInfoSheet.value = true
    }

    fun dismissInfoSheet() {
        _showInfoSheet.value = false
    }

    fun handleExplicitSubscription() {
        val obj = solverObject ?: return
        Log.i(TAG, "Handling explicit subscription for object ${obj.id}")
        subscriptionMiddleware.handleSubscriptionSelected("explicit")
    }

    // MARK: - Smart Lock Actions

    fun executeCachedUnlock(commandName: String) {
        val obj = solverObject ?: return
        val brand = _lockBrand.value ?: return
        val adapter = smartLockManager.adapter(brand)

        viewModelScope.launch {
            _cachedOperation.value = if (commandName == "lock") CachedOperation.LOCKING else CachedOperation.UNLOCKING
            _cachedUnlockResult.value = null

            val result = if (commandName == "lock") {
                adapter.lock(obj)
            } else {
                adapter.unlock(obj)
            }

            result.fold(
                onSuccess = { message ->
                    Log.i(TAG, "Smart lock $commandName succeeded: $message")
                    _cachedUnlockResult.value = message
                },
                onFailure = { error ->
                    Log.e(TAG, "Smart lock $commandName failed: ${error.message}")
                    _cachedUnlockResult.value = "Error: ${error.message}"
                    _commandError.value = error.message
                }
            )

            _cachedOperation.value = CachedOperation.NONE
        }
    }

    fun checkLockStatus() {
        val obj = solverObject ?: return
        val brand = _lockBrand.value ?: return
        val adapter = smartLockManager.adapter(brand)

        viewModelScope.launch {
            _cachedOperation.value = CachedOperation.CHECKING_STATUS

            val result = adapter.checkStatus(obj)
            result.fold(
                onSuccess = { state ->
                    Log.i(TAG, "Lock status check succeeded: $state")
                },
                onFailure = { error ->
                    Log.e(TAG, "Lock status check failed: ${error.message}")
                    _commandError.value = error.message
                }
            )

            _cachedOperation.value = CachedOperation.NONE
        }
    }

    fun fetchAndCacheKeys() {
        val obj = solverObject ?: return
        val brand = _lockBrand.value ?: return
        val adapter = smartLockManager.adapter(brand)

        viewModelScope.launch {
            _cachedOperation.value = CachedOperation.FETCHING_KEYS
            _cachedUnlockResult.value = null

            val result = adapter.fetchTokens(obj)
            result.fold(
                onSuccess = { tokens ->
                    Log.i(TAG, "Keys fetched successfully for ${tokens.deviceIdentifier}")
                    
                    // Calculate validity time
                    val validityInfo = tokens.remainingValiditySeconds?.let { remaining ->
                        val minutes = (remaining / 60).toInt()
                        "Valid for $minutes minutes"
                    } ?: "Using fallback expiry"
                    
                    _cachedUnlockResult.value = "✅ Keys cached successfully\nDevice: ${tokens.deviceIdentifier}\n$validityInfo"
                    
                    // Start polling now that we have tokens
                    adapter.startPolling(obj)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to fetch keys: ${error.message}")
                    _cachedUnlockResult.value = "❌ GetKeys failed: ${error.message}"
                    _commandError.value = error.message
                }
            )

            _cachedOperation.value = CachedOperation.NONE
        }
    }

    fun clearCachedKeys() {
        val obj = solverObject ?: return
        val brand = _lockBrand.value ?: return
        val adapter = smartLockManager.adapter(brand)
        
        Log.i(TAG, "🗑️ Clearing cached keys for object $objectId")
        adapter.clearTokens(objectId)
        adapter.stopPolling()
        _lockStatus.value = SmartLockStatus.UNKNOWN
        _cachedUnlockResult.value = "🗑️ Keys cleared from cache"
        
        // Auto-fetch new keys after clearing (matches iOS behavior)
        viewModelScope.launch {
            autoFetchKeysIfNeeded(obj)
        }
    }

    fun stopLockStatePolling() {
        viewModelScope.launch {
            smartLockManager.stopAllAdapters()
        }
    }
    
    // MARK: - App Lifecycle Handling
    
    /**
     * Called when app enters foreground.
     * Restarts BLE polling if we have valid tokens.
     * Matches iOS willEnterForeground behavior.
     */
    fun onAppForeground() {
        val obj = solverObject ?: return
        val brand = _lockBrand.value ?: return
        
        Log.i(TAG, "🔐 App entered foreground - restarting polling if needed")
        
        val adapter = smartLockManager.adapter(brand)
        if (adapter.hasValidTokens(objectId)) {
            Log.i(TAG, "🔐 Valid tokens found, restarting lock state polling")
            adapter.startPolling(obj)
        } else {
            Log.i(TAG, "🔐 No valid tokens, will fetch on next command")
        }
    }
    
    /**
     * Called when app enters background.
     * Stops BLE polling to avoid battery drain and connection issues.
     * Matches iOS willResignActive/didEnterBackground behavior.
     */
    fun onAppBackground() {
        Log.i(TAG, "🔐 App entered background - stopping BLE polling")
        viewModelScope.launch {
            smartLockManager.stopAllAdapters()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            smartLockManager.stopAllAdapters()
        }
    }
}
