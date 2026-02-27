package no.solver.solverappdemo.features.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.core.deeplink.DeepLinkParser
import javax.inject.Inject

enum class ScannerState {
    SCANNING,
    PAUSED
}

data class ScanUiState(
    val hasCameraPermission: Boolean = false,
    val scannerState: ScannerState = ScannerState.SCANNING,
    val errorMessage: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ScanViewModel"
        private const val SCAN_COOLDOWN_MS = 2000L
    }

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var lastScanTime: Long = 0L

    init {
        checkPermission()
    }

    fun checkPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.value = _uiState.value.copy(hasCameraPermission = hasPermission)
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Called when QR code is detected by camera.
     * Returns a parsed URI if valid and should be processed, null otherwise.
     * Pauses the scanner on successful detection.
     */
    fun onQrCodeScanned(rawValue: String): Uri? {
        // Only process when actively scanning
        if (_uiState.value.scannerState != ScannerState.SCANNING) return null

        val now = System.currentTimeMillis()

        // Check cooldown
        if (now - lastScanTime < SCAN_COOLDOWN_MS) {
            Log.d(TAG, "⏳ Scan cooldown active, ignoring: $rawValue")
            return null
        }
        lastScanTime = now

        Log.i(TAG, "📸 QR code scanned: $rawValue")

        // Try to parse as URI
        val uri = try {
            Uri.parse(rawValue)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Not a valid URI: $rawValue")
            return null
        }

        // Check if it's a valid Solver QR code
        if (!DeepLinkParser.isQrCommandDeepLink(uri)) {
            Log.w(TAG, "⚠️ Not a Solver QR code: $rawValue")
            _uiState.value = _uiState.value.copy(errorMessage = "Not a Solver QR code")
            return null
        }

        // Valid QR code — pause scanner and return URI for processing
        _uiState.value = _uiState.value.copy(scannerState = ScannerState.PAUSED)
        return uri
    }

    /**
     * Resume scanning after "Scan Again" is tapped.
     * Resets cooldown timer so the same QR code can be re-scanned.
     */
    fun scanAgain() {
        lastScanTime = 0L
        _uiState.value = _uiState.value.copy(
            scannerState = ScannerState.SCANNING,
            errorMessage = null
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
