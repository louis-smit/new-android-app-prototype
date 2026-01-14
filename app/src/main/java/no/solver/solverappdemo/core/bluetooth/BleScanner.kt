package no.solver.solverappdemo.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.polycontrol.blescans.DLBleScanData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "BleScanner"

/**
 * BLE Scanner for finding Danalock devices.
 * Ported from old Android reference app's BleScanner.java
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

    private var listenForDeviceId: String? = null
    private var deviceFoundCallback: DeviceFoundCallback? = null

    interface DeviceFoundCallback {
        fun onFound(macAddress: String, scanData: DLBleScanData, rssi: Int)
        fun onTimeout()
    }

    init {
        initializeScanner()
    }

    private fun initializeScanner() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScanning() {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled")
            return
        }

        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return
        }

        try {
            if (bleScanner == null) {
                bleScanner = bluetoothAdapter?.bluetoothLeScanner
            }

            if (scanCallback == null) {
                scanCallback = createScanCallback()
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(null, settings, scanCallback)
            Log.i(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting scan: ${e.message}")
        }
    }

    fun stopScanning() {
        try {
            scanCallback?.let { callback ->
                bleScanner?.stopScan(callback)
            }
            Log.i(TAG, "BLE scanning stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping scan: ${e.message}")
        }
    }

    fun listenForDevice(
        deviceId: String,
        callback: DeviceFoundCallback,
        timeoutMillis: Long = 30_000
    ) {
        listenForDeviceId = deviceId
        deviceFoundCallback = callback

        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            listenForDeviceId = null
            val tempCallback = deviceFoundCallback
            deviceFoundCallback = null
            tempCallback?.onTimeout()
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, timeoutMillis)
    }

    /**
     * Coroutine-friendly version for finding a device
     */
    suspend fun findDevice(
        deviceId: String,
        timeoutMillis: Long = 30_000
    ): ScanResultData = suspendCancellableCoroutine { continuation ->
        startScanning()

        listenForDevice(deviceId, object : DeviceFoundCallback {
            override fun onFound(macAddress: String, scanData: DLBleScanData, rssi: Int) {
                stopScanning()
                if (continuation.isActive) {
                    continuation.resume(ScanResultData(macAddress, scanData, rssi))
                }
            }

            override fun onTimeout() {
                stopScanning()
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockError.ConnectionTimeout
                    )
                }
            }
        }, timeoutMillis)

        continuation.invokeOnCancellation {
            stopScanning()
            cancelTimeout()
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { runnable ->
            timeoutHandler?.removeCallbacks(runnable)
        }
        timeoutRunnable = null
        timeoutHandler = null
    }

    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val device = result.device
                    val scanRecord = result.scanRecord?.bytes ?: byteArrayOf()
                    val rssi = result.rssi

                    val scanData = DLBleScanData.parseData(scanRecord, device) ?: return

                    val targetDeviceId = listenForDeviceId
                    if (targetDeviceId != null && targetDeviceId.equals(scanData.deviceId, ignoreCase = true)) {
                        Log.i(TAG, "Found target device: ${scanData.deviceId}")
                        
                        cancelTimeout()
                        listenForDeviceId = null
                        
                        val callback = deviceFoundCallback
                        deviceFoundCallback = null
                        callback?.onFound(device.address, scanData, rssi)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception in scan callback: ${e.message}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
            }
        }
    }

    fun reset() {
        listenForDeviceId = null
        deviceFoundCallback = null
        cancelTimeout()
        stopScanning()
    }
}

data class ScanResultData(
    val macAddress: String,
    val scanData: DLBleScanData,
    val rssi: Int
)
