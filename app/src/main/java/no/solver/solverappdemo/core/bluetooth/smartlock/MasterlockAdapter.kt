package no.solver.solverappdemo.core.bluetooth.smartlock

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.masterlock.mlbluetoothsdk.Interfaces.IMLLockScannerDelegate
import com.masterlock.mlbluetoothsdk.Interfaces.IMLProductDelegate
import com.masterlock.mlbluetoothsdk.MLBluetoothSDK
import com.masterlock.mlbluetoothsdk.MLProduct
import com.masterlock.mlbluetoothsdk.enums.MLBroadcastState
import com.masterlock.mlbluetoothsdk.enums.MLDisconnectType
import com.masterlock.mlbluetoothsdk.lockstate.LockState
import com.masterlock.mlbluetoothsdk.models.MLFirmwareUpdateStatus
import com.masterlock.mlbluetoothsdk.models.audittrail.MLAuditTrailEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "MasterlockAdapter"
private const val POLL_INTERVAL_MS = 2000L
private const val SCAN_TIMEOUT_MS = 10_000L

/**
 * Masterlock implementation of SmartLockProtocol.
 * Ported from iOS MasterlockAdapter and old Android MasterlockHelper/MasterlockInteractMiddleware.
 */
@Singleton
class MasterlockAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val objectsRepository: ObjectsRepository,
    private val tokenCache: SmartLockTokenCache
) : SmartLockProtocol, IMLLockScannerDelegate, IMLProductDelegate {

    override val brand: SmartLockBrand = SmartLockBrand.MASTERLOCK
    override val capabilities: SmartLockCapabilities = SmartLockCapabilities.MASTERLOCK

    private val _status = MutableStateFlow(SmartLockStatus.UNKNOWN)
    override val status: StateFlow<SmartLockStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null
    private var currentObjectId: Int? = null
    private var currentObject: SolverObject? = null

    // Masterlock SDK state
    private var pendingProduct: MLProduct? = null
    private var foundCallback: ((MLProduct?) -> Unit)? = null
    private var isCheckInProgress = false

    private val license = "FJMVlmAbKm7/sqzBQCajukbYkVDjA0yZlyOf6EPuelACTcZQ7xYVANBosSs3i3BLp8rKUu6pLULcrPVnlTDPnIUCFc1/94ZDY3Z2qN9yglWm7ELON1Zfmr+4Qki4oXbwjV71aQGYKPl45Jhk46dozdc0BCn/TZJXJWfD910SM0RXGAc8Uu6TjShq0cPO4brQBYU8vynm9QDyQBPIu68yKnX5oIZuTKAIJBaGFsOBiXt44oWOaGwfv5cAiYcP2GKRirT5gvOlbTe83siMaidlGx2Ikh8QGNG6BSATYlcU74T23gfjSfxV6Qafu1vjgPhqtuRziFTylsd+yUkDwxz2sA==|l7GG5hZs6mIezzhItf7tefmNZNWlshsh8b4EXlhUhCIGChrBli3sknZ+pzdyzCKt9LeTEnkfMZnhtHLzZJCS6McCJzGV0CnViIR8Yfq33vh3RXPZUTcB12LYHndovAdJRdYaURCdKkoKIzVxZzF5O22k136oqSRK+os2hfgewaTtRcLImxhcWBApxKeXKp2DqyMJcSpPCGFTClE9dM++v1u6/LIzJ1Y60FNUtT5VlC3NwE0tq4Rw4ajko+igGHs5L3QH6AgZlUEqNcmOVrNhTK6434EX3IDhBJZkWba6LELw/MJTOCsSjdYGtd9ipI/CnfhdLk5aAwadEnLdAEBjCw==|dJRXLLfvScmG5fSLrmb2CtUnqSUznuzWO6J7W95zTSZQ5/ZXMenEA7C66CWnujP4jW1wyN/XFfryfu29e99WOJVumxkWq4iSwBxabR7nkmNBq4fDuc1goGvuQUAp1bZtB0z2cq85b/VlyLIDtf5UHbPE1BQ8NMzno/s+P4rtH/fsrgXpeq8borKes3KRXzu1R/P+bEIYOJpZzRUid7eROtVevNz56dlGUQqyYXT07mnccdNpxSipTJyofY+dHGVLWTMoIxiYeHjyMjNl8icN3NPBk9cFsBANMoo8YVx6fsgli+kM9IeHCcvlFbWgWSAJx6+fIcVeD2X6SS88DkAYdQ=="
    
    private var mlSdk: MLBluetoothSDK? = null

    init {
        configureSdk()
    }

    private fun configureSdk() {
        try {
            mlSdk = MLBluetoothSDK(license, context)
            mlSdk?.setLockScannerDelegate(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure Masterlock SDK: ${e.message}")
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return btManager?.adapter?.isEnabled == true
    }

    override fun hasValidTokens(objectId: Int): Boolean {
        return tokenCache.hasTokens(objectId)
    }

    override suspend fun fetchTokens(solverObject: SolverObject): Result<SmartLockTokens> {
        Log.i(TAG, "📡 Fetching Masterlock tokens for object ${solverObject.id}")

        return when (val result = objectsRepository.executeCommand(solverObject.id, "GetKeys")) {
            is ApiResult.Success -> {
                val response = result.data
                if (!response.success) {
                    Result.failure(SmartLockError.OperationFailed("GetKeys returned success=false"))
                } else {
                    val tokens = parseTokens(response, solverObject.id)
                    if (tokens == null) {
                        Result.failure(SmartLockError.InvalidTokenFormat)
                    } else {
                        tokenCache.store(solverObject.id, tokens)
                        _status.value = SmartLockStatus(
                            state = SmartLockState.UNKNOWN,
                            batteryLevel = null,
                            inRange = false,
                            hasValidTokens = true,
                            tokensExpireAt = tokens.expiresAt
                        )
                        Result.success(tokens)
                    }
                }
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.exception.message))
            }
        }
    }

    override fun clearTokens(objectId: Int) {
        Log.i(TAG, "🗑️ Clearing Masterlock tokens for object $objectId")
        tokenCache.clear(objectId)
        _status.value = SmartLockStatus.UNKNOWN
    }

    override suspend fun unlock(solverObject: SolverObject): Result<String> {
        Log.i(TAG, "🔵 Executing Masterlock unlock for object ${solverObject.id}")

        // Get or fetch tokens
        val tokens = tokenCache.tokens(solverObject.id) ?: run {
            val fetchResult = fetchTokens(solverObject)
            fetchResult.getOrNull() ?: return Result.failure(
                fetchResult.exceptionOrNull() ?: SmartLockError.MissingTokens
            )
        }

        // Extract Masterlock-specific tokens
        val (accessProfile, firmwareVersion) = tokens.extractMasterlockTokens()
            ?: return Result.failure(SmartLockError.InvalidTokenFormat)

        // Create MLProduct
        val product = MLProduct(tokens.deviceIdentifier, accessProfile, firmwareVersion)
        product.delegate = this

        return try {
            // Find device
            Log.i(TAG, "Scanning for Masterlock device: ${tokens.deviceIdentifier}")
            val foundProduct = findDevice(product, SCAN_TIMEOUT_MS)

            // Unlock
            performUnlock(foundProduct)

            // Disconnect
            disconnect(foundProduct)

            // Update status
            _status.value = SmartLockStatus(
                state = SmartLockState.UNLOCKED,
                batteryLevel = null,
                inRange = true,
                hasValidTokens = true,
                tokensExpireAt = tokens.expiresAt
            )

            Log.i(TAG, "✅ Masterlock unlock succeeded")
            Result.success("Masterlock unlocked successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Masterlock unlock failed: ${e.message}")
            disconnect(product)
            Result.failure(e)
        }
    }

    override suspend fun lock(solverObject: SolverObject): Result<String> {
        // Masterlock doesn't support lock command
        return Result.failure(SmartLockError.UnsupportedOperation("lock"))
    }

    override suspend fun checkStatus(solverObject: SolverObject): Result<SmartLockState> {
        // If another check is in progress, just return current status
        if (isCheckInProgress) {
            Log.d(TAG, "🔵 Skipping status check - another check in progress")
            return Result.success(_status.value.state)
        }

        Log.i(TAG, "🔵 Checking Masterlock status for object ${solverObject.id}")

        // Get or fetch tokens
        val tokens = tokenCache.tokens(solverObject.id) ?: run {
            val fetchResult = fetchTokens(solverObject)
            fetchResult.getOrNull() ?: return Result.failure(
                fetchResult.exceptionOrNull() ?: SmartLockError.MissingTokens
            )
        }

        // Extract Masterlock-specific tokens
        val (accessProfile, firmwareVersion) = tokens.extractMasterlockTokens()
            ?: return Result.failure(SmartLockError.InvalidTokenFormat)

        // Create MLProduct
        val product = MLProduct(tokens.deviceIdentifier, accessProfile, firmwareVersion)
        product.delegate = this

        return try {
            isCheckInProgress = true

            // Find device with shorter timeout for status checks
            val foundProduct = findDevice(product, 3000)

            // For status check, just confirm we can connect
            val lockState = SmartLockState.UNKNOWN // State reading requires specific SDK method

            isCheckInProgress = false

            // Disconnect
            disconnect(foundProduct)

            // Update status
            _status.value = SmartLockStatus(
                state = lockState,
                batteryLevel = null,
                inRange = true,
                hasValidTokens = true,
                tokensExpireAt = tokens.expiresAt
            )

            Result.success(lockState)

        } catch (e: Exception) {
            isCheckInProgress = false
            Log.w(TAG, "Status check failed: ${e.message}")

            // Update status as out of range
            _status.value = SmartLockStatus(
                state = SmartLockState.UNKNOWN,
                batteryLevel = null,
                inRange = false,
                hasValidTokens = tokenCache.hasTokens(solverObject.id),
                tokensExpireAt = tokens.expiresAt
            )

            Result.success(SmartLockState.UNKNOWN)
        }
    }

    override fun startPolling(solverObject: SolverObject) {
        Log.i(TAG, "🔄 Starting Masterlock polling for object ${solverObject.id}")

        pollingJob?.cancel()
        currentObjectId = solverObject.id
        currentObject = solverObject

        // Set initial status
        _status.value = SmartLockStatus(
            state = SmartLockState.UNKNOWN,
            batteryLevel = null,
            inRange = false,
            hasValidTokens = tokenCache.hasTokens(solverObject.id),
            tokensExpireAt = tokenCache.tokens(solverObject.id)?.expiresAt
        )

        pollingJob = scope.launch {
            // Do an initial poll immediately
            pollOnce(solverObject)

            // Then poll periodically
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                pollOnce(solverObject)
            }

            Log.i(TAG, "🔄 Masterlock polling stopped for object ${solverObject.id}")
        }
    }

    override fun stopPolling() {
        Log.i(TAG, "🛑 Stopping Masterlock polling")
        pollingJob?.cancel()
        pollingJob = null
        currentObject = null
        reset()
    }

    override suspend fun stopPollingAndWait() {
        Log.i(TAG, "🛑 Stopping Masterlock polling and waiting")
        pollingJob?.cancelAndJoin()
        pollingJob = null
        currentObject = null
        reset()
    }

    override fun parseTokens(response: ExecuteResponse, objectId: Int): SmartLockTokens? {
        return SmartLockTokenCache.parseMasterlockTokens(response, objectId)
    }

    // MARK: - Private Methods

    private suspend fun pollOnce(solverObject: SolverObject) {
        Log.d(TAG, "🔄 Masterlock poll cycle for object ${solverObject.id}")
        val result = checkStatus(solverObject)
        if (result.isSuccess) {
            Log.d(TAG, "🔄 Masterlock poll result: ${result.getOrNull()?.displayText}")
        }
    }

    private suspend fun findDevice(product: MLProduct, timeoutMs: Long): MLProduct =
        suspendCancellableCoroutine { continuation ->
            pendingProduct = product
            pendingProduct?.delegate = this

            // Start scanning
            mlSdk?.startScanning()

            foundCallback = { foundProduct ->
                if (foundProduct != null && continuation.isActive) {
                    continuation.resume(foundProduct)
                } else if (continuation.isActive) {
                    continuation.resumeWithException(SmartLockError.DeviceNotFound)
                }
            }

            // Timeout
            scope.launch {
                delay(timeoutMs)
                if (continuation.isActive) {
                    Log.i(TAG, "Scan timed out for device: ${product.deviceId}")
                    reset()
                    continuation.resumeWithException(SmartLockError.DeviceNotFound)
                }
            }

            continuation.invokeOnCancellation {
                reset()
            }
        }

    private suspend fun performUnlock(product: MLProduct): Unit =
        suspendCancellableCoroutine { continuation ->
            product.delegate = this

            val relockTime = 30 // seconds
            product.unlockPrimaryLock(relockTime) { result, error ->
                if (error != null) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } else {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }

    private fun disconnect(product: MLProduct) {
        product.disconnect(MLDisconnectType.None) { _, _ ->
            Log.i(TAG, "Disconnected from Masterlock")
        }
    }

    private fun reset() {
        foundCallback = null
        pendingProduct = null
        mlSdk?.stopScanning()
    }

    // MARK: - IMLLockScannerDelegate

    override fun bluetoothReady() {
        Log.i(TAG, "Bluetooth ready")
        mlSdk?.startScanning()
    }

    override fun bluetoothDown() {
        Log.i(TAG, "Bluetooth down")
    }

    override fun didDiscoverDevice(deviceId: String) {
        Log.i(TAG, "Did discover Masterlock device $deviceId")
    }

    override fun shouldConnect(deviceId: String, rssi: Int): Boolean {
        Log.i(TAG, "Checking if should connect to $deviceId")
        return pendingProduct?.deviceId == deviceId
    }

    override fun productForDevice(deviceId: String): MLProduct? {
        Log.i(TAG, "Getting product for device ID $deviceId")
        return if (pendingProduct?.deviceId == deviceId) pendingProduct else null
    }

    override fun bluetoothFailedWithDisconnectCode(deviceId: String, disconnectCode: Int) {
        Log.e(TAG, "Bluetooth failed for $deviceId with code $disconnectCode")
    }

    override fun onScanFailed(errorCode: Int) {
        Log.e(TAG, "Scan failed with error code: $errorCode")
    }

    // MARK: - IMLProductDelegate

    override fun didConnect(product: MLProduct) {
        Log.i(TAG, "Connected to Masterlock")
        val callback = foundCallback
        foundCallback = null
        callback?.invoke(product)
    }

    override fun didDisconnect(product: MLProduct) {
        Log.i(TAG, "Disconnected from Masterlock")
    }

    override fun didFailToConnect(product: MLProduct, error: Exception?) {
        Log.e(TAG, "Failed to connect to Masterlock: ${error?.message}")
        val callback = foundCallback
        foundCallback = null
        callback?.invoke(null)
    }

    override fun didChangeState(product: MLProduct, state: MLBroadcastState) {
        Log.i(TAG, "Product state changed: ${state.name}")
    }

    override fun firmwareUpdateStatusUpdate(product: MLProduct, status: MLFirmwareUpdateStatus) {
        // No-op
    }

    override fun shouldUpdateProductData(product: MLProduct) {
        // No-op
    }

    override fun didUploadAuditEntries(product: MLProduct, entries: Array<MLAuditTrailEntry>) {
        Log.d(TAG, "Received ${entries.size} audit entries")
    }

    override fun didReadAuditEntries(product: MLProduct, entries: Array<MLAuditTrailEntry>) {
        Log.d(TAG, "Read ${entries.size} audit entries")
    }

    override fun onLockStateChange(product: MLProduct, lockState: LockState) {
        Log.i(TAG, "Lock state changed: $lockState")
    }
}
