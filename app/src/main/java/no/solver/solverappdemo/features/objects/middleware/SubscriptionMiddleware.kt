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
import no.solver.solverappdemo.data.models.SubscriptionOption
import no.solver.solverappdemo.features.objects.payment.PaymentService
import no.solver.solverappdemo.features.objects.payment.StripePaymentHandler
import no.solver.solverappdemo.features.objects.payment.SubscriptionService
import no.solver.solverappdemo.features.objects.payment.SubscriptionStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context for a subscription flow.
 */
data class SubscriptionContext(
    val command: String,
    val objectId: Int,
    val subscriptionOptions: List<SubscriptionOption> = emptyList()
) {
    val message: String
        get() {
            val commandArticle = indefiniteArticle(command)
            return "A subscription is required to perform $commandArticle action."
        }

    private fun indefiniteArticle(word: String): String {
        val vowels = listOf("a", "e", "i", "o", "u")
        val firstLetter = word.lowercase().take(1)
        return if (firstLetter in vowels) "an $word" else "a $word"
    }
}

/**
 * Subscription middleware that handles subscriptionRequired responses.
 * Matches iOS SubscriptionMiddleware.
 */
@Singleton
class SubscriptionMiddleware @Inject constructor(
    private val subscriptionService: SubscriptionService,
    private val paymentService: PaymentService,
    private val subscriptionStorage: SubscriptionStorage,
    private val stripePaymentHandler: StripePaymentHandler
) : CommandMiddleware {

    companion object {
        private const val TAG = "SubscriptionMiddleware"
    }

    override val name: String = "SubscriptionMiddleware"
    override val shouldEarlyExit: Boolean = true

    // UI State
    private val _showSubscriptionOptionsSheet = MutableStateFlow(false)
    val showSubscriptionOptionsSheet: StateFlow<Boolean> = _showSubscriptionOptionsSheet.asStateFlow()

    private val _showPaymentMethodSheet = MutableStateFlow(false)
    val showPaymentMethodSheet: StateFlow<Boolean> = _showPaymentMethodSheet.asStateFlow()

    private val _subscriptionContext = MutableStateFlow<SubscriptionContext?>(null)
    val subscriptionContext: StateFlow<SubscriptionContext?> = _subscriptionContext.asStateFlow()

    private val _selectedSubscription = MutableStateFlow<SubscriptionOption?>(null)
    val selectedSubscription: StateFlow<SubscriptionOption?> = _selectedSubscription.asStateFlow()

    private val _availableMethods = MutableStateFlow<AvailablePaymentMethods?>(null)
    val availableMethods: StateFlow<AvailablePaymentMethods?> = _availableMethods.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Alert state
    private val _showSuccessAlert = MutableStateFlow(false)
    val showSuccessAlert: StateFlow<Boolean> = _showSuccessAlert.asStateFlow()

    private val _showErrorAlert = MutableStateFlow(false)
    val showErrorAlert: StateFlow<Boolean> = _showErrorAlert.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Keep track of current object for payment method filtering
    private var currentObject: SolverObject? = null

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        return !response.success && response.hasContextKey("subscriptionRequired")
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        Log.i(TAG, "🟣 [SubscriptionMiddleware] TRIGGERED for command: ${command.commandName}")

        // Fetch subscription options
        val result = subscriptionService.fetchSubscriptionOptions(solverObject.id)

        return result.fold(
            onSuccess = { options ->
                if (options.isEmpty()) {
                    Log.e(TAG, "No subscription options available for object ${solverObject.id}")
                    return MiddlewareResult.Handled(
                        message = "Subscription required but no options available",
                        suppressDebugUI = false
                    )
                }

                Log.i(TAG, "Fetched ${options.size} subscription options")

                // Get available payment methods
                val methods = AvailablePaymentMethods.from(solverObject.vippsCredentials)

                if (methods.none) {
                    Log.e(TAG, "No payment methods available for object ${solverObject.id}")
                    return MiddlewareResult.Handled(
                        message = "Subscription required but no payment methods configured",
                        suppressDebugUI = false
                    )
                }

                // Create context and show UI
                val context = SubscriptionContext(
                    command = command.commandName,
                    objectId = solverObject.id,
                    subscriptionOptions = options
                )

                currentObject = solverObject
                _subscriptionContext.value = context
                _availableMethods.value = methods
                _showSubscriptionOptionsSheet.value = true

                Log.i(TAG, "Showing subscription options sheet")

                MiddlewareResult.Handled(
                    message = "Subscription flow initiated",
                    suppressDebugUI = true
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to fetch subscription options: ${error.message}")
                MiddlewareResult.Handled(
                    message = "Failed to fetch subscription options: ${error.message}",
                    suppressDebugUI = false
                )
            }
        )
    }

    /**
     * Handle subscription option selection.
     */
    fun handleSubscriptionSelected(option: SubscriptionOption) {
        Log.i(TAG, "User selected subscription: ${option.displayTitle}")

        _showSubscriptionOptionsSheet.value = false
        _selectedSubscription.value = option

        // Filter payment methods based on subscription type
        // Recurring subscriptions (type 3) only support Vipps
        val currentMethods = _availableMethods.value
        if (option.subscriptionType?.isRecurring == true && currentMethods != null) {
            _availableMethods.value = AvailablePaymentMethods(
                hasVipps = currentMethods.hasVipps,
                hasCard = false,  // Card not supported for recurring
                hasStripe = false  // Stripe not supported for recurring
            )
        }

        // Show payment method selection
        _showPaymentMethodSheet.value = true
    }

    /**
     * Handle payment method selection for subscription.
     */
    suspend fun handlePaymentMethodSelected(method: PaymentMethod, context: Context) {
        val subscription = _selectedSubscription.value ?: run {
            Log.e(TAG, "Missing subscription when payment method selected")
            return
        }
        val subscriptionContext = _subscriptionContext.value ?: run {
            Log.e(TAG, "Missing context when payment method selected")
            return
        }

        Log.i(TAG, "User selected payment method: ${method.value}")
        _isLoading.value = true

        val result = subscriptionService.initiateSubscription(
            method = method,
            objectId = subscriptionContext.objectId,
            subscriptionOption = subscription
        )

        result.onSuccess { paymentResponse ->
            // Save pending subscription for recovery
            subscriptionStorage.savePendingSubscription(
                method = method,
                subscriptionOption = subscription,
                objectId = subscriptionContext.objectId
            )

            // Handle payment based on method
            handleSubscriptionPaymentResponse(method, paymentResponse, subscription, context)
        }.onFailure { error ->
            Log.e(TAG, "Subscription payment initiation failed: ${error.message}")
            _isLoading.value = false
            _showPaymentMethodSheet.value = false
            showError(error.message ?: "Subscription initiation failed")
        }
    }

    private fun handleSubscriptionPaymentResponse(
        method: PaymentMethod,
        response: no.solver.solverappdemo.data.models.SubscriptionPaymentResponse,
        subscriptionOption: SubscriptionOption,
        context: Context
    ) {
        when (method) {
            PaymentMethod.STRIPE -> {
                // For Stripe, keep payment sheet open while Stripe presents on top
                handleStripeSubscriptionPayment(response)
            }
            PaymentMethod.VIPPS, PaymentMethod.CARD -> {
                // For external payments, dismiss immediately before redirecting
                _showPaymentMethodSheet.value = false
                _isLoading.value = false
                handleExternalSubscriptionPayment(method, response, subscriptionOption, context)
            }
        }
    }

    private fun handleStripeSubscriptionPayment(
        response: no.solver.solverappdemo.data.models.SubscriptionPaymentResponse
    ) {
        Log.i(TAG, "Handling Stripe subscription payment...")
        _isLoading.value = false

        // Convert to PaymentResponse format for StripePaymentHandler
        val stripeResponse = PaymentResponse(
            orderId = response.orderId,
            url = response.url,
            clientSecret = response.clientSecret,
            publishableKey = response.publishableKey,
            redirectUrl = response.redirectUrl
        )

        stripePaymentHandler.presentPaymentSheet(stripeResponse) { result ->
            // Dismiss payment method sheet now that Stripe is done
            _showPaymentMethodSheet.value = false

            when (result) {
                is PaymentResult.Success -> {
                    Log.i(TAG, "✅ Stripe subscription payment successful")
                    subscriptionStorage.clearPendingSubscription()
                    showSuccess()
                }
                is PaymentResult.Failure -> {
                    Log.e(TAG, "❌ Stripe subscription payment failed: ${result.message}")
                    subscriptionStorage.clearPendingSubscription()
                    showError(result.message)
                }
                is PaymentResult.Cancelled -> {
                    Log.i(TAG, "Stripe subscription payment cancelled")
                    subscriptionStorage.clearPendingSubscription()
                }
            }
        }
    }

    private fun handleExternalSubscriptionPayment(
        method: PaymentMethod,
        response: no.solver.solverappdemo.data.models.SubscriptionPaymentResponse,
        subscriptionOption: SubscriptionOption,
        context: Context
    ) {
        Log.i(TAG, "Handling external subscription payment redirect for ${method.value}")

        val subscriptionType = subscriptionOption.subscriptionType
        if (subscriptionType == null) {
            Log.e(TAG, "Unknown subscription type")
            showError("Unknown subscription type")
            return
        }

        val urlString = response.getRedirectUrl(subscriptionType)
        if (urlString == null) {
            Log.e(TAG, "No redirect URL in subscription payment response")
            showError("No redirect URL provided")
            return
        }

        try {
            val uri = Uri.parse(urlString)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, uri)
            Log.i(TAG, "Opening external subscription payment URL: $urlString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open payment URL: ${e.message}")
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
     * Triggers explicit subscription flow when user taps "Subscribe" button.
     */
    suspend fun triggerExplicitSubscription(solverObject: SolverObject) {
        Log.i(TAG, "🟣 [SubscriptionMiddleware] Explicit subscription triggered by user")

        _isLoading.value = true

        val result = subscriptionService.fetchSubscriptionOptions(solverObject.id)

        result.onSuccess { options ->
            _isLoading.value = false

            if (options.isEmpty()) {
                Log.e(TAG, "No subscription options available for object ${solverObject.id}")
                showError("No subscription options available")
                return
            }

            Log.i(TAG, "Fetched ${options.size} subscription options")

            // Get available payment methods
            val methods = AvailablePaymentMethods.from(solverObject.vippsCredentials)

            if (methods.none) {
                Log.e(TAG, "No payment methods available for object ${solverObject.id}")
                showError("No payment methods configured")
                return
            }

            // Create context and show UI
            val context = SubscriptionContext(
                command = "subscription",  // Generic command for explicit flow
                objectId = solverObject.id,
                subscriptionOptions = options
            )

            currentObject = solverObject
            _subscriptionContext.value = context
            _availableMethods.value = methods
            _showSubscriptionOptionsSheet.value = true

            Log.i(TAG, "Showing subscription options sheet (explicit flow)")
        }.onFailure { error ->
            _isLoading.value = false
            Log.e(TAG, "Failed to fetch subscription options: ${error.message}")
            showError("Failed to fetch subscription options")
        }
    }

    fun dismissSubscriptionOptionsSheet() {
        _showSubscriptionOptionsSheet.value = false
        _subscriptionContext.value = null
        currentObject = null
    }

    fun dismissPaymentMethodSheet() {
        _showPaymentMethodSheet.value = false
        _selectedSubscription.value = null
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
