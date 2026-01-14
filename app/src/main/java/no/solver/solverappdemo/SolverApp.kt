package no.solver.solverappdemo

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.poly_control.dmi.Dmi
import com.poly_control.dmi.Dmi_SerialNumber
import com.polycontrol.BluetoothLeEkeyService
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "SolverApp"

@HiltAndroidApp
class SolverApp : Application() {
    
    companion object {
        private var instance: SolverApp? = null
        
        fun getInstance(): SolverApp? = instance
        
        /**
         * Get the BluetoothLeEkeyService instance.
         * Returns null if service is not yet bound.
         */
        fun getBleService(): BluetoothLeEkeyService? = instance?.bleService
    }
    
    private var bleService: BluetoothLeEkeyService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i(TAG, "BluetoothLeEkeyService connected")
            val binder = service as BluetoothLeEkeyService.LocalBinder
            bleService = binder.service
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.i(TAG, "BluetoothLeEkeyService disconnected")
            bleService = null
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDanalockSdk()
        bindBleService()
    }
    
    /**
     * Initialize the Danalock SDK.
     * Must be called before any Danalock operations.
     * The placeholder serial is replaced with the actual device serial during connection.
     */
    private fun initializeDanalockSdk() {
        try {
            Dmi.initialize(Dmi_SerialNumber("11:22:33:44:55:66"))
            Log.i(TAG, "Danalock SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Danalock SDK: ${e.message}")
        }
    }
    
    /**
     * Bind to BluetoothLeEkeyService.
     * This service provides the BLE network layer required by the Danalock SDK.
     */
    private fun bindBleService() {
        try {
            val intent = Intent(this, BluetoothLeEkeyService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound = true
            Log.i(TAG, "Binding to BluetoothLeEkeyService...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind BluetoothLeEkeyService: ${e.message}")
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    /**
     * Disconnect from the currently connected BLE device.
     */
    fun disconnectBleDevice() {
        try {
            bleService?.bleDisconnect()
            Log.i(TAG, "BLE device disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect BLE device: ${e.message}")
        }
    }
}
