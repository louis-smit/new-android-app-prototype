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
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import no.solver.solverappdemo.data.repositories.TagRepository
import no.solver.solverappdemo.features.auth.services.SessionManager
import no.solver.solverappdemo.features.objects.middleware.DanalockMiddleware
import no.solver.solverappdemo.features.objects.middleware.GeofenceMiddleware
import no.solver.solverappdemo.features.objects.middleware.MasterlockMiddleware
import no.solver.solverappdemo.features.objects.middleware.MiddlewareChain
import no.solver.solverappdemo.features.objects.middleware.PaymentMiddleware
import no.solver.solverappdemo.features.objects.middleware.SubscriptionMiddleware
import javax.inject.Inject

/**
 * UI state for deep link execution.
 * Mirrors iOS DeepLinkHandler state.
 */
data class DeepLinkUiState(
    /** True while executing a QR command */
    val isExecuting: Boolean = false,
    /** True when status sheet should be shown */
    val sheetVisible: Boolean = false,
    /** The object for the status sheet */
    val objectForSheet: SolverObject? = null,
    /** The response for the status sheet */
    val responseForSheet: ExecuteResponse? = null,
    /** Error message if something went wrong */
    val error: String? = null,
    /** Payment callback state */
    val paymentCallbackMethod: PaymentMethod? = null,
    val paymentCallbackReference: String? = null,
    val showPaymentCallback: Boolean = false
)

/**
 * Handles QR/NFC deep link routing and command execution.
 * 
 * iOS Camera app and built-in NFC automatically open URLs like:
 * - solverapp://qr/{command}/{tag}
 * - https://solver.no/qr/{command}/{tag}
 * 
 * This ViewModel:
 * 1. Parses the deep link URL
 * 2. Fetches the object by tag
 * 3. Executes the command
 * 4. Processes middleware (payment, geofence, Danalock, etc.)
 * 5. Shows result via StatusBottomSheet
 */
@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val objectsRepository: ObjectsRepository,
    private val sessionManager: SessionManager,
    private val danalockMiddleware: DanalockMiddleware,
    private val masterlockMiddleware: MasterlockMiddleware,
    private val geofenceMiddleware: GeofenceMiddleware,
    val paymentMiddleware: PaymentMiddleware,
    val subscriptionMiddleware: SubscriptionMiddleware
) : ViewModel() {

    companion object {
        private const val TAG = "DeepLinkViewModel"
    }

    private val _uiState = MutableStateFlow(DeepLinkUiState())
    val uiState: StateFlow<DeepLinkUiState> = _uiState.asStateFlow()

    private val middlewareChain: MiddlewareChain by lazy {
        MiddlewareChain.createStandardChain(
            repository = objectsRepository,
            paymentMiddleware = paymentMiddleware,
            subscriptionMiddleware = subscriptionMiddleware,
            geofenceMiddleware = geofenceMiddleware,
            danalockMiddleware = danalockMiddleware,
            masterlockMiddleware = masterlockMiddleware,
            onShowStatusSheet = { response ->
                // Status sheet will be shown after middleware processing
            }
        )
    }

    // Track last handled URI to prevent duplicate execution
    private var lastHandledUri: Uri? = null
    private var lastHandledTimestamp: Long = 0L

    /**
     * Handle incoming URL from QR scan or NFC tap.
     * Called from MainActivity.onCreate or onNewIntent.
     */
    fun handle(uri: Uri) {
        Log.i(TAG, "📲 Received deep link: ${uri}")

        // Dedupe: ignore if same URI within 2 seconds
        val now = System.currentTimeMillis()
        if (uri == lastHandledUri && (now - lastHandledTimestamp) < 2000) {
            Log.w(TAG, "⚠️ Ignoring duplicate deep link")
            return
        }
        lastHandledUri = uri
        lastHandledTimestamp = now

        // Parse deep link
        val deepLink = DeepLinkParser.parse(uri)
        if (deepLink == null) {
            Log.w(TAG, "⚠️ Could not parse deep link: $uri")
            return
        }

        when (deepLink) {
            is DeepLink.QrCommand -> {
                Log.i(TAG, "🔓 QR command: ${deepLink.command}, tag: ${deepLink.tag}")
                executeQrCommand(deepLink.command, deepLink.tag)
            }
            is DeepLink.PaymentCallback -> {
                Log.i(TAG, "💳 Payment callback: ${deepLink.method}, reference: ${deepLink.reference}")
                handlePaymentCallback(deepLink.method, deepLink.reference)
            }
        }
    }

    /**
     * Handle payment callback from external payment flow (Vipps/Card).
     */
    private fun handlePaymentCallback(method: PaymentMethod, reference: String) {
        _uiState.value = _uiState.value.copy(
            paymentCallbackMethod = method,
            paymentCallbackReference = reference,
            showPaymentCallback = true
        )
        // Also notify the payment middleware
        paymentMiddleware.handlePaymentCallback(method, reference)
    }

    /**
     * Dismiss payment callback screen.
     */
    fun dismissPaymentCallback() {
        _uiState.value = _uiState.value.copy(
            paymentCallbackMethod = null,
            paymentCallbackReference = null,
            showPaymentCallback = false
        )
    }

    /**
     * Execute QR/NFC command and show result via StatusBottomSheet.
     * Same UI as button presses on ObjectDetailScreen.
     */
    private fun executeQrCommand(command: String, tag: String) {
        viewModelScope.launch {
            _uiState.value = DeepLinkUiState(isExecuting = true)

            // 1. Fetch object by tag
            val objectResult = tagRepository.getObjectByTag(tag)
            if (objectResult is ApiResult.Error) {
                Log.e(TAG, "❌ Failed to fetch object by tag: ${objectResult.exception.message}")
                _uiState.value = DeepLinkUiState(
                    isExecuting = false,
                    error = "Object not found: ${objectResult.exception.message}"
                )
                return@launch
            }

            val solverObject = (objectResult as ApiResult.Success).data
            Log.i(TAG, "✅ Fetched object: ${solverObject.name}")

            // 2. Execute command by tag
            val executeResult = tagRepository.executeCommandByTag(command, tag)
            if (executeResult is ApiResult.Error) {
                Log.e(TAG, "❌ Failed to execute command: ${executeResult.exception.message}")
                _uiState.value = DeepLinkUiState(
                    isExecuting = false,
                    objectForSheet = solverObject,
                    error = "Command failed: ${executeResult.exception.message}"
                )
                return@launch
            }

            val response = (executeResult as ApiResult.Success).data
            Log.i(TAG, "✅ Command executed. Success: ${response.success}")

            // 3. Process middleware chain (IDENTICAL to button press flow)
            val commandObj = Command(
                native = command,
                display = command.replaceFirstChar { it.uppercase() },
                label = null,
                input = false,
                type = null,
                validation = null,
                visible = true,
                sortorder = 0,
                merge = null,
                tailGating = 0
            )

            val middlewareResult = middlewareChain.process(
                response = response,
                command = commandObj,
                solverObject = solverObject
            )

            Log.i(TAG, "Middleware processing complete. Handled: ${middlewareResult.handled}, TookOverUI: ${middlewareResult.middlewareTookOverUI}")

            // 4. Show status sheet with result ONLY if no middleware took over UI
            if (middlewareResult.middlewareTookOverUI) {
                // Middleware (e.g., payment/subscription) is showing its own UI
                Log.i(TAG, "Middleware took over UI - skipping status sheet")
                _uiState.value = DeepLinkUiState(
                    isExecuting = false,
                    objectForSheet = solverObject,
                    responseForSheet = response
                )
            } else {
                // Show status sheet with result (same UI as ObjectDetailView)
                _uiState.value = DeepLinkUiState(
                    isExecuting = false,
                    sheetVisible = true,
                    objectForSheet = solverObject,
                    responseForSheet = response
                )
            }
        }
    }

    /**
     * Reset the deep link state after handling.
     * Called when status sheet is dismissed.
     */
    fun reset() {
        _uiState.value = DeepLinkUiState()
    }

    /**
     * Dismiss error message.
     */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Dismiss status sheet.
     */
    fun dismissSheet() {
        _uiState.value = _uiState.value.copy(
            sheetVisible = false,
            objectForSheet = null,
            responseForSheet = null
        )
    }
}
