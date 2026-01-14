package no.solver.solverappdemo.core.bluetooth.smartlock

import android.content.Context
import android.util.Base64
import android.util.Log
import com.danalock.webservices.danaserver.model.LoginToken
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.polycontrol.BluetoothLeEkeyService
import com.polycontrol.devices.models.DLDevice
import com.polycontrol.devices.models.DMILock
import com.polycontrol.devices.models.DanaDevice
import com.polycontrol.keys.DLKey
import com.polycontrol.keys.DLV3Key
import com.polycontrol.keys.DLV3LoginToken
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
import no.solver.solverappdemo.SolverApp
import no.solver.solverappdemo.core.bluetooth.BleScanner
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Type
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "DanalockAdapter"
private const val POLL_INTERVAL_MS = 500L

/**
 * Danalock implementation of SmartLockProtocol.
 * Ported from iOS DanalockAdapter and old Android DanalockHelper/DanalockInteractMiddleware.
 * 
 * Note: Full BLE connection requires Application-level BluetoothLeEkeyService binding.
 * This implementation uses BleScanner for device discovery and DMILock for operations.
 */
@Singleton
class DanalockAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val objectsRepository: ObjectsRepository,
    private val tokenCache: SmartLockTokenCache,
    private val bleScanner: BleScanner
) : SmartLockProtocol {

    override val brand: SmartLockBrand = SmartLockBrand.DANALOCK
    override val capabilities: SmartLockCapabilities = SmartLockCapabilities.DANALOCK

    private val _status = MutableStateFlow(SmartLockStatus.UNKNOWN)
    override val status: StateFlow<SmartLockStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null
    private var currentObjectId: Int? = null

    override fun hasValidTokens(objectId: Int): Boolean {
        return tokenCache.hasTokens(objectId)
    }

    override suspend fun fetchTokens(solverObject: SolverObject): Result<SmartLockTokens> {
        Log.i(TAG, "📡 Fetching Danalock tokens for object ${solverObject.id}")

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
                        _status.value = _status.value.copy(
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
        Log.i(TAG, "🗑️ Clearing Danalock tokens for object $objectId")
        tokenCache.clear(objectId)
        _status.value = SmartLockStatus.UNKNOWN
        stopPolling()
    }

    override suspend fun unlock(solverObject: SolverObject): Result<String> {
        return executeCommand(solverObject, "unlock")
    }

    override suspend fun lock(solverObject: SolverObject): Result<String> {
        return executeCommand(solverObject, "lock")
    }

    override fun startPolling(solverObject: SolverObject) {
        if (!hasValidTokens(solverObject.id)) {
            Log.i(TAG, "🔐 Cannot start polling: no valid tokens for object ${solverObject.id}")
            _status.value = SmartLockStatus.UNKNOWN
            return
        }

        currentObjectId = solverObject.id
        pollingJob?.cancel()

        pollingJob = scope.launch {
            pollLockState(solverObject)
        }
    }

    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override suspend fun stopPollingAndWait() {
        pollingJob?.cancelAndJoin()
        pollingJob = null
        bleScanner.reset()
        Log.i(TAG, "🔐 Danalock polling fully stopped")
    }

    override suspend fun checkStatus(solverObject: SolverObject): Result<SmartLockState> {
        // Danalock uses continuous polling, not manual status checks
        Log.i(TAG, "🔐 Danalock checkStatus called - returning current polled state")
        return Result.success(_status.value.state)
    }

    override fun parseTokens(response: ExecuteResponse, objectId: Int): SmartLockTokens? {
        return SmartLockTokenCache.parseDanalockTokens(response, objectId)
    }

    // MARK: - Private Methods

    private suspend fun executeCommand(solverObject: SolverObject, command: String): Result<String> {
        Log.i(TAG, "🔵 Executing Danalock $command for object ${solverObject.id}")

        // Stop polling before command to avoid BLE conflicts
        stopPollingAndWait()

        // Get or fetch tokens
        val tokens = tokenCache.tokens(solverObject.id) ?: run {
            val fetchResult = fetchTokens(solverObject)
            fetchResult.getOrNull() ?: return Result.failure(
                fetchResult.exceptionOrNull() ?: SmartLockError.MissingTokens
            )
        }

        // Extract Danalock-specific tokens
        val (loginToken, broadcastKey) = tokens.extractDanalockTokens()
            ?: return Result.failure(SmartLockError.InvalidTokenFormat)

        return try {
            // Get DLDevice from tokens
            val device = getDanaDeviceFromTokens(
                loginToken = loginToken,
                serial = tokens.deviceIdentifier,
                broadcastKey = broadcastKey,
                name = solverObject.name
            ) ?: return Result.failure(SmartLockError.InvalidTokenFormat)

            // Find device via BLE scan
            Log.i(TAG, "Scanning for Danalock device: ${tokens.deviceIdentifier}")
            val scanResult = bleScanner.findDevice(device.deviceId, 30_000)
            
            // Touch device with scan data
            device.touch(scanResult.scanData, scanResult.rssi, scanResult.macAddress)
            bleScanner.stopScanning()
            
            // Get BLE service - required for the network layer
            val bleService = SolverApp.getBleService()
                ?: return Result.failure(SmartLockError.OperationFailed("BLE service not available"))
            
            // Connect via BluetoothLeEkeyService BEFORE calling lock operations
            Log.i(TAG, "Connecting to device via BluetoothLeEkeyService: ${scanResult.macAddress}")
            val connectResult = connectViaBleService(bleService, scanResult.macAddress)
            if (connectResult.isFailure) {
                return Result.failure(connectResult.exceptionOrNull() ?: SmartLockError.ConnectionFailed)
            }

            // Execute command via DMILock
            val dmiLock = device as DMILock
            val result = executeOnLock(dmiLock, command)
            
            // Disconnect after operation
            SolverApp.getInstance()?.disconnectBleDevice()

            when {
                result.isSuccess -> {
                    // Update state immediately
                    _status.value = SmartLockStatus(
                        state = if (command == "lock") SmartLockState.LOCKED else SmartLockState.UNLOCKED,
                        batteryLevel = _status.value.batteryLevel,
                        inRange = true,
                        hasValidTokens = true,
                        tokensExpireAt = tokens.expiresAt
                    )

                    // Delay before restarting polling to avoid Android's "scanning too frequently" throttle
                    // Android throttles apps that start BLE scans within 30 seconds of stopping
                    delay(2000L)
                    
                    // Restart polling
                    startPolling(solverObject)

                    Result.success("Bluetooth $command executed successfully")
                }
                else -> {
                    Result.failure(result.exceptionOrNull() ?: SmartLockError.OperationFailed("Unknown error"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Danalock command failed: ${e.message}")
            SolverApp.getInstance()?.disconnectBleDevice()
            Result.failure(e)
        }
    }
    
    private suspend fun connectViaBleService(
        bleService: BluetoothLeEkeyService,
        macAddress: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            Log.i(TAG, "🔵 Calling bleConnect for $macAddress")
            bleService.bleConnect(macAddress, DLKey.DLKeyType.V3) { bleStatus ->
                Log.d(TAG, "bleConnect status: $bleStatus")
                if (bleStatus == null || bleStatus == BluetoothLeEkeyService.BleStatus.Connected) {
                    if (continuation.isActive) {
                        Log.i(TAG, "✅ BLE connection established")
                        continuation.resume(Result.success(Unit))
                    }
                } else {
                    if (continuation.isActive) {
                        Log.e(TAG, "❌ BLE connection failed: $bleStatus")
                        continuation.resume(Result.failure(SmartLockError.ConnectionFailed))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during bleConnect: ${e.message}")
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

    private suspend fun executeOnLock(lock: DMILock, command: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            try {
                when (command.lowercase()) {
                    "unlock" -> lock.unlock { status ->
                        Log.d(TAG, "Unlock status: $status")
                        if (status == DanaDevice.Status.OKAY || status == DanaDevice.Status.AlreadyExists) {
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                        } else {
                            if (continuation.isActive) continuation.resumeWithException(
                                SmartLockError.OperationFailed(status.toString())
                            )
                        }
                    }
                    "lock" -> lock.lock { status ->
                        Log.d(TAG, "Lock status: $status")
                        if (status == DanaDevice.Status.OKAY || status == DanaDevice.Status.AlreadyExists) {
                            if (continuation.isActive) continuation.resume(Result.success(Unit))
                        } else {
                            if (continuation.isActive) continuation.resumeWithException(
                                SmartLockError.OperationFailed(status.toString())
                            )
                        }
                    }
                    else -> {
                        if (continuation.isActive) continuation.resumeWithException(
                            SmartLockError.UnsupportedOperation(command)
                        )
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

    private suspend fun pollLockState(solverObject: SolverObject) {
        Log.i(TAG, "🔐 Starting Danalock polling for object ${solverObject.id}")
        
        val tokens = tokenCache.tokens(solverObject.id) ?: run {
            Log.w(TAG, "No tokens for polling")
            _status.value = SmartLockStatus.UNKNOWN
            return
        }
        
        val (loginToken, broadcastKey) = tokens.extractDanalockTokens() ?: run {
            Log.w(TAG, "Invalid token format for polling")
            _status.value = SmartLockStatus.UNKNOWN
            return
        }
        
        val device = getDanaDeviceFromTokens(
            loginToken = loginToken,
            serial = tokens.deviceIdentifier,
            broadcastKey = broadcastKey,
            name = solverObject.name
        ) ?: run {
            Log.w(TAG, "Could not create device for polling")
            _status.value = SmartLockStatus.UNKNOWN
            return
        }
        
        // Start continuous BLE scanning to detect device advertisements
        bleScanner.startScanning()
        
        // Cast to DMILock to access getLockState()
        val dmiLock = device as? DMILock
        
        // Listen for our device - the callback will "touch" the device with scan data
        bleScanner.listenForDevice(
            deviceId = device.deviceId,
            callback = object : BleScanner.DeviceFoundCallback {
                override fun onFound(macAddress: String, scanData: com.polycontrol.blescans.DLBleScanData, rssi: Int) {
                    // Touch the device to update its internal state
                    device.touch(scanData, rssi, macAddress)
                    
                    // Get lock state from device after touch (decrypted from broadcast)
                    val lockState = dmiLock?.let { lock ->
                        when (lock.lockState) {
                            DLDevice.LockState.LATCHED, DLDevice.LockState.REMOTE_LATCHED -> SmartLockState.LOCKED
                            DLDevice.LockState.UNLATCHED, DLDevice.LockState.REMOTE_UNLATCHED -> SmartLockState.UNLOCKED
                            else -> _status.value.state // Keep current state if unknown
                        }
                    } ?: _status.value.state
                    
                    // Update status with new range info and lock state
                    _status.value = SmartLockStatus(
                        state = lockState,
                        batteryLevel = _status.value.batteryLevel,
                        inRange = true,
                        hasValidTokens = true,
                        tokensExpireAt = tokens.expiresAt
                    )
                    
                    // Re-register to keep listening (listenForDevice clears after first match)
                    if (pollingJob?.isActive == true) {
                        bleScanner.listenForDevice(device.deviceId, this, 5_000)
                    }
                }
                
                override fun onTimeout() {
                    // Device not seen in timeout period - mark as out of range
                    _status.value = SmartLockStatus(
                        state = _status.value.state,
                        batteryLevel = _status.value.batteryLevel,
                        inRange = false,
                        hasValidTokens = true,
                        tokensExpireAt = tokens.expiresAt
                    )
                    
                    // Re-register to keep listening
                    if (pollingJob?.isActive == true) {
                        bleScanner.listenForDevice(device.deviceId, this, 5_000)
                    }
                }
            },
            timeoutMillis = 5_000
        )

        // Keep the coroutine alive while polling is active
        while (scope.isActive && pollingJob?.isActive == true) {
            delay(POLL_INTERVAL_MS)
        }

        Log.i(TAG, "🔐 Danalock polling stopped for object ${solverObject.id}")
        bleScanner.stopScanning()
    }

    companion object {
        /**
         * Creates a DLDevice from token data.
         * Ported from old Android DanalockHelper.getDanaDeviceFromJSON
         */
        fun getDanaDeviceFromTokens(
            loginToken: ByteArray,
            serial: String,
            broadcastKey: ByteArray,
            name: String
        ): DLDevice? {
            val loginTokenBase64 = Base64.encodeToString(loginToken, Base64.NO_WRAP)
            val broadcastKeyBase64 = Base64.encodeToString(broadcastKey, Base64.NO_WRAP)

            val customGson = GsonBuilder()
                .registerTypeHierarchyAdapter(ByteArray::class.java, ByteArrayToBase64TypeAdapter())
                .create()

            val json = buildMockDanaApiJSON(loginTokenBase64, serial, broadcastKeyBase64, name)
            val token = customGson.fromJson(json.toString(), LoginToken::class.java)
            val dlKey = DLV3Key.getInstance(token) as? DLV3LoginToken ?: return null
            val dlDevice = DLDevice.getDevice(dlKey)

            return if (dlDevice is DMILock) dlDevice else null
        }

        private fun buildMockDanaApiJSON(
            loginToken: String,
            serial: String,
            advertisingKey: String,
            name: String
        ): JSONObject {
            return JSONObject().apply {
                put("device", JSONObject().apply {
                    put("name", name)
                    put("serial_number", serial)
                    put("device_type", "danalockv3")
                    put("timezone", "Europe/Oslo")
                })
                put("blob", loginToken)
                put("broadcast_key", advertisingKey)
                put("metadata", JSONObject().apply {
                    put("permissions", buildDanaPermissions())
                    put("valid_from", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT))
                    put("valid_to", ZonedDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_INSTANT))
                })
            }
        }

        private fun buildDanaPermissions(): JSONArray {
            val permsJson = """[
                {"name":"afi_device_settings_reset","description":"Settings Reset Permission"},
                {"name":"afi_device_dfu_enable","description":"DFU Enable Permission"},
                {"name":"afi_lock_operate","description":"Operate Lock Permission"},
                {"name":"afi_lock_configure","description":"Configure Lock Setup"},
                {"name":"afi_time_update","description":"Set time Permission"},
                {"name":"afi_bridge_setup","description":"Bridge Setup Permission"},
                {"name":"afi_disenroll","description":"Enroll Permission"},
                {"name":"afi_extension_module_set_network","description":"Set Afi Extension Board network state permission"},
                {"name":"afi_outputs_set_settings","description":"Set outputs settings"},
                {"name":"afi_outputs_operate","description":"Operate outputs"},
                {"name":"afi_pin_codes_configure","description":"Configure pin codes"},
                {"name":"afi_authority_pairing","description":"Setup pairing between devices"}
            ]"""
            return JSONArray(permsJson)
        }

        private class ByteArrayToBase64TypeAdapter : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
            override fun deserialize(
                json: JsonElement,
                typeOfT: Type,
                context: JsonDeserializationContext
            ): ByteArray {
                return Base64.decode(json.asString, Base64.NO_WRAP)
            }

            override fun serialize(
                src: ByteArray,
                typeOfSrc: Type,
                context: JsonSerializationContext
            ): JsonElement {
                return JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP))
            }
        }
    }
}
