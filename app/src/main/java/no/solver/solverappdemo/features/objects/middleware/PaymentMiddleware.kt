package no.solver.solverappdemo.features.objects.middleware

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.data.models.AvailablePaymentMethods
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.PaymentResponse
import no.solver.solverappdemo.data.models.PaymentResult
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.objects.payment.PaymentService
import no.solver.solverappdemo.features.objects.payment.PaymentStorage
import no.solver.solverappdemo.features.objects.payment.StripePaymentHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context for a payment flow.
 */
data class PaymentContext(
    val price: String,
    val command: String,
    val objectId: Int,
    val vendingTransId: Int? = null
) {
    val message: String
        get() {
            val commandArticle = indefiniteArticle(command)
            val priceText = if (price.isEmpty()) "payment" else "payment of NOK $price"
            return "A $priceText is required to perform $commandArticle action."
        }

    private fun indefiniteArticle(word: String): String {
        val vowels = listOf("a", "e", "i", "o", "u")
        val firstLetter = word.lowercase().take(1)
        return if (firstLetter in vowels) "an $word" else "a $word"
    }
}

/**
 * Payment middleware that handles paymentRequired responses.
 * Matches iOS PaymentMiddleware.
 */
@Singleton
class PaymentMiddleware @Inject constructor(
    private val paymentService: PaymentService,
    private val paymentStorage: PaymentStorage,
    private val stripePaymentHandler: StripePaymentHandler
) : CommandMiddleware {

    companion object {
        private const val TAG = "PaymentMiddleware"
    }

    override val name: String = "PaymentMiddleware"
    override val shouldEarlyExit: Boolean = true

    // UI State
    private val _showPaymentSheet = MutableStateFlow(false)
    val showPaymentSheet: StateFlow<Boolean> = _showPaymentSheet.asStateFlow()

    private val _showPaymentCallback = MutableStateFlow(false)
    val showPaymentCallback: StateFlow<Boolean> = _showPaymentCallback.asStateFlow()

    private val _paymentContext = MutableStateFlow<PaymentContext?>(null)
    val paymentContext: StateFlow<PaymentContext?> = _paymentContext.asStateFlow()

    private val _availableMethods = MutableStateFlow<AvailablePaymentMethods?>(null)
    val availableMethods: StateFlow<AvailablePaymentMethods?> = _availableMethods.asStateFlow()

    private val _callbackReference = MutableStateFlow<String?>(null)
    val callbackReference: StateFlow<String?> = _callbackReference.asStateFlow()

    private val _callbackMethod = MutableStateFlow<PaymentMethod?>(null)
    val callbackMethod: StateFlow<PaymentMethod?> = _callbackMethod.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Alert state
    private val _showSuccessAlert = MutableStateFlow(false)
    val showSuccessAlert: StateFlow<Boolean> = _showSuccessAlert.asStateFlow()

    private val _showErrorAlert = MutableStateFlow(false)
    val showErrorAlert: StateFlow<Boolean> = _showErrorAlert.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        return !response.success && response.hasContextKey("paymentRequired")
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        Log.i(TAG, "🔵 [PaymentMiddleware] TRIGGERED for command: ${command.commandName}")

        val priceString = response.findValueInContext("paymentRequired") ?: ""
        val vendingTransIdString = response.findValueInContext("vendingTransId")
        val vendingTransId = vendingTransIdString?.toIntOrNull()

        // Get available payment methods from object's vippsCredentials
        val methods = AvailablePaymentMethods.from(solverObject.vippsCredentials)

        if (methods.none) {
            Log.e(TAG, "No payment methods available for object ${solverObject.id}")
            return MiddlewareResult.Handled(
                message = "Payment required but no payment methods configured",
                suppressDebugUI = false
            )
        }

        // Create payment context
        val context = PaymentContext(
            price = priceString,
            command = command.commandName,
            objectId = solverObject.id,
            vendingTransId = vendingTransId
        )

        // Store context for sheet presentation
        _paymentContext.value = context
        _availableMethods.value = methods
        _showPaymentSheet.value = true

        Log.i(TAG, "Showing payment method sheet. Price: $priceString, Available methods: ${methods.methods.size}")

        return MiddlewareResult.Handled(
            message = "Payment flow initiated",
            suppressDebugUI = true
        )
    }

    /**
     * Handle payment method selection from the sheet.
     */
    suspend fun handlePaymentMethodSelected(method: PaymentMethod, context: Context) {
        val paymentContext = _paymentContext.value ?: run {
            Log.e(TAG, "Payment context missing when method selected")
            return
        }

        Log.i(TAG, "User selected payment method: ${method.value}")
        _showPaymentSheet.value = false
        _isLoading.value = true

        val result = paymentService.initiatePayment(
            method = method,
            objectId = paymentContext.objectId,
            command = paymentContext.command,
            vendingTransId = paymentContext.vendingTransId
        )

        result.onSuccess { paymentResponse ->
            // Save pending payment for recovery
            paymentStorage.savePendingPayment(
                method = method,
                response = paymentResponse,
                objectId = paymentContext.objectId,
                command = paymentContext.command
            )

            // Handle payment based on method
            handlePaymentResponse(method, paymentResponse, context)
        }.onFailure { error ->
            Log.e(TAG, "Payment initiation failed: ${error.message}")
            _isLoading.value = false
            showError(error.message ?: "Payment initiation failed")
        }
    }

    private fun handlePaymentResponse(
        method: PaymentMethod,
        response: PaymentResponse,
        context: Context
    ) {
        when (method) {
            PaymentMethod.STRIPE -> handleStripePayment(response)
            PaymentMethod.VIPPS, PaymentMethod.CARD -> handleExternalPayment(method, response, context)
        }
    }

    private fun handleStripePayment(response: PaymentResponse) {
        Log.i(TAG, "Handling Stripe payment...")
        _isLoading.value = false

        stripePaymentHandler.presentPaymentSheet(response) { result ->
            when (result) {
                is PaymentResult.Success -> {
                    Log.i(TAG, "✅ Stripe payment successful")
                    paymentStorage.clearPendingPayment()
                    showSuccess()
                }
                is PaymentResult.Failure -> {
                    Log.e(TAG, "❌ Stripe payment failed: ${result.message}")
                    paymentStorage.clearPendingPayment()
                    showError(result.message)
                }
                is PaymentResult.Cancelled -> {
                    Log.i(TAG, "Stripe payment cancelled")
                    paymentStorage.clearPendingPayment()
                }
            }
        }
    }

    private fun handleExternalPayment(
        method: PaymentMethod,
        response: PaymentResponse,
        context: Context
    ) {
        Log.i(TAG, "Handling external payment redirect for ${method.value}")
        _isLoading.value = false

        val urlString = response.redirectUrl ?: response.url
        if (urlString == null) {
            Log.e(TAG, "No redirect URL in payment response")
            showError("No redirect URL provided")
            return
        }

        try {
            val uri = Uri.parse(urlString)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, uri)
            Log.i(TAG, "Opening external payment URL: $urlString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open payment URL: ${e.message}")
            // Fallback to regular browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                showError("Could not open payment page")
            }
        }
    }

    /**
     * Handle payment callback from deep link.
     */
    fun handlePaymentCallback(method: PaymentMethod, reference: String) {
        Log.i(TAG, "💳 Handling payment callback: method=${method.value}, reference=$reference")
        _callbackMethod.value = method
        _callbackReference.value = reference
        _showPaymentCallback.value = true
    }

    fun dismissPaymentSheet() {
        _showPaymentSheet.value = false
        _paymentContext.value = null
        _availableMethods.value = null
    }

    fun dismissPaymentCallback() {
        _showPaymentCallback.value = false
        _callbackReference.value = null
        _callbackMethod.value = null
        paymentStorage.clearPendingPayment()
    }

    fun dismissSuccessAlert() {
        _showSuccessAlert.value = false
    }

    fun dismissErrorAlert() {
        _showErrorAlert.value = false
        _errorMessage.value = null
    }

    private fun showSuccess() {
        _showSuccessAlert.value = true
    }

    private fun showError(message: String) {
        _errorMessage.value = message
        _showErrorAlert.value = true
    }
}
