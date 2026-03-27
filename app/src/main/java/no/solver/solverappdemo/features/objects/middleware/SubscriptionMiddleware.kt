package no.solver.solverappdemo.features.objects.middleware

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
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
import no.solver.solverappdemo.data.models.SubscriptionPaymentResponse
import no.solver.solverappdemo.features.objects.payment.StripePaymentHandler
import no.solver.solverappdemo.features.objects.payment.SubscriptionService
import no.solver.solverappdemo.features.objects.payment.SubscriptionStorage
import no.solver.solverappdemo.features.objects.result.ActionResultCenter
import no.solver.solverappdemo.features.objects.result.ActionResultKind
import no.solver.solverappdemo.features.objects.result.ActionResultPresentation
import no.solver.solverappdemo.features.objects.result.ActionResultState

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
        val firstLetter = word.lowercase(Locale.getDefault()).take(1)
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
    private val subscriptionStorage: SubscriptionStorage,
    private val stripePaymentHandler: StripePaymentHandler,
    private val actionResultCenter: ActionResultCenter
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

    // Prevent duplicate payment initiation when users tap repeatedly.
    private val isPaymentSelectionInFlight = AtomicBoolean(false)
    private var pendingStripeResponse: SubscriptionPaymentResponse? = null

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

        val result = subscriptionService.fetchSubscriptionOptions(solverObject.id)

        return result.fold(
            onSuccess = { options ->
                if (options.isEmpty()) {
                    Log.e(TAG, "No subscription options available for object ${solverObject.id}")
                    MiddlewareResult.Handled(
                        message = "Subscription required but no options available",
                        suppressDebugUI = false
                    )
                } else {
                    Log.i(TAG, "Fetched ${options.size} subscription options")

                    val methods = AvailablePaymentMethods.from(solverObject.vippsCredentials)
                    if (methods.none) {
                        Log.e(TAG, "No payment methods available for object ${solverObject.id}")
                        MiddlewareResult.Handled(
                            message = "Subscription required but no payment methods configured",
                            suppressDebugUI = false
                        )
                    } else {
                        val context = SubscriptionContext(
                            command = command.commandName,
                            objectId = solverObject.id,
                            subscriptionOptions = options
                        )

                        _subscriptionContext.value = context
                        _selectedSubscription.value = null
                        _availableMethods.value = methods
                        resetInFlightState()
                        _showSubscriptionOptionsSheet.value = true

                        Log.i(TAG, "Showing subscription options sheet")

                        MiddlewareResult.Handled(
                            message = "Subscription flow initiated",
                            suppressDebugUI = true
                        )
                    }
                }
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
        resetInFlightState()

        // Recurring subscriptions (type 3) only support Vipps.
        val currentMethods = _availableMethods.value
        if (option.subscriptionType?.isRecurring == true && currentMethods != null) {
            _availableMethods.value = AvailablePaymentMethods(
                hasVipps = currentMethods.hasVipps,
                hasCard = false,
                hasStripe = false
            )
        }

        _showPaymentMethodSheet.value = true
    }

    /**
     * Handle payment method selection for subscription.
     */
    suspend fun handlePaymentMethodSelected(method: PaymentMethod, context: Context) {
        if (!isPaymentSelectionInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "Ignoring duplicate subscription payment selection while request is in flight")
            return
        }

        val subscription = _selectedSubscription.value ?: run {
            Log.e(TAG, "Missing subscription when payment method selected")
            resetInFlightState()
            return
        }
        val subscriptionContext = _subscriptionContext.value ?: run {
            Log.e(TAG, "Missing context when payment method selected")
            resetInFlightState()
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
            // Save pending subscription for recovery.
            subscriptionStorage.savePendingSubscription(
                method = method,
                subscriptionOption = subscription,
                objectId = subscriptionContext.objectId
            )

            handleSubscriptionPaymentResponse(method, paymentResponse, subscription, context)
        }.onFailure { error ->
            Log.e(TAG, "Subscription payment initiation failed: ${error.message}")
            clearPaymentSelectionState()
            publishSubscriptionOutcome(
                state = ActionResultState.FAILURE,
                message = error.message ?: "Subscription initiation failed"
            )
            resetInFlightState()
        }
    }

    private fun handleSubscriptionPaymentResponse(
        method: PaymentMethod,
        response: SubscriptionPaymentResponse,
        subscriptionOption: SubscriptionOption,
        context: Context
    ) {
        when (method) {
            PaymentMethod.STRIPE -> {
                // Dismiss selector first, then present Stripe from sheet dismissal callback.
                pendingStripeResponse = response
                _showPaymentMethodSheet.value = false
            }

            PaymentMethod.VIPPS,
            PaymentMethod.CARD -> {
                _showPaymentMethodSheet.value = false
                _isLoading.value = false
                handleExternalSubscriptionPayment(method, response, subscriptionOption, context)
            }
        }
    }

    /**
     * Called after the payment method sheet is dismissed.
     * If Stripe is pending we defer SDK presentation until the sheet is fully gone.
     */
    fun handlePaymentMethodSheetDismissed() {
        val stripeResponse = pendingStripeResponse ?: run {
            resetInFlightState()
            return
        }

        pendingStripeResponse = null
        handleStripeSubscriptionPayment(stripeResponse)
    }

    private fun handleStripeSubscriptionPayment(response: SubscriptionPaymentResponse) {
        Log.i(TAG, "Handling Stripe subscription payment...")
        _isLoading.value = false

        val stripeResponse = PaymentResponse(
            orderId = response.orderId,
            url = response.url,
            clientSecret = response.clientSecret,
            publishableKey = response.publishableKey,
            redirectUrl = response.redirectUrl
        )

        stripePaymentHandler.presentPaymentSheet(stripeResponse) { result ->
            when (result) {
                is PaymentResult.Success -> {
                    Log.i(TAG, "✅ Stripe subscription payment successful")
                    subscriptionStorage.clearPendingSubscription()
                    publishSubscriptionOutcome(
                        state = ActionResultState.SUCCESS,
                        message = "Your subscription payment was successful."
                    )
                }

                is PaymentResult.Failure -> {
                    Log.e(TAG, "❌ Stripe subscription payment failed: ${result.message}")
                    subscriptionStorage.clearPendingSubscription()
                    publishSubscriptionOutcome(
                        state = ActionResultState.FAILURE,
                        message = result.message
                    )
                }

                is PaymentResult.Cancelled -> {
                    Log.i(TAG, "Stripe subscription payment cancelled")
                    subscriptionStorage.clearPendingSubscription()
                    publishSubscriptionOutcome(
                        state = ActionResultState.CANCELLED,
                        message = "Subscription payment was cancelled."
                    )
                }
            }

            clearPaymentSelectionState()
            resetInFlightState()
        }
    }

    private fun handleExternalSubscriptionPayment(
        method: PaymentMethod,
        response: SubscriptionPaymentResponse,
        subscriptionOption: SubscriptionOption,
        context: Context
    ) {
        Log.i(TAG, "Handling external subscription payment redirect for ${method.value}")

        val subscriptionType = subscriptionOption.subscriptionType
        if (subscriptionType == null) {
            Log.e(TAG, "Unknown subscription type")
            subscriptionStorage.clearPendingSubscription()
            publishSubscriptionOutcome(
                state = ActionResultState.FAILURE,
                message = "Unknown subscription type"
            )
            clearPaymentSelectionState()
            resetInFlightState()
            return
        }

        val urlString = response.getRedirectUrl(subscriptionType)
        if (urlString == null) {
            Log.e(TAG, "No redirect URL in subscription payment response")
            subscriptionStorage.clearPendingSubscription()
            publishSubscriptionOutcome(
                state = ActionResultState.FAILURE,
                message = "No redirect URL provided"
            )
            clearPaymentSelectionState()
            resetInFlightState()
            return
        }

        try {
            val uri = Uri.parse(urlString)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, uri)
            Log.i(TAG, "Opening external subscription payment URL: $urlString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open payment URL: ${e.message}")
            val openedInBrowser = runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess

            if (!openedInBrowser) {
                subscriptionStorage.clearPendingSubscription()
                publishSubscriptionOutcome(
                    state = ActionResultState.FAILURE,
                    message = "Could not open payment page"
                )
            }
        }

        clearPaymentSelectionState()
        resetInFlightState()
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
                publishSubscriptionOutcome(
                    state = ActionResultState.FAILURE,
                    message = "No subscription options available."
                )
                return@onSuccess
            }

            Log.i(TAG, "Fetched ${options.size} subscription options")

            val methods = AvailablePaymentMethods.from(solverObject.vippsCredentials)
            if (methods.none) {
                Log.e(TAG, "No payment methods available for object ${solverObject.id}")
                publishSubscriptionOutcome(
                    state = ActionResultState.FAILURE,
                    message = "No payment methods are configured for this object."
                )
                return@onSuccess
            }

            val context = SubscriptionContext(
                command = "subscription",
                objectId = solverObject.id,
                subscriptionOptions = options
            )

            _subscriptionContext.value = context
            _selectedSubscription.value = null
            _availableMethods.value = methods
            resetInFlightState()
            _showSubscriptionOptionsSheet.value = true

            Log.i(TAG, "Showing subscription options sheet (explicit flow)")
        }.onFailure { error ->
            _isLoading.value = false
            Log.e(TAG, "Failed to fetch subscription options: ${error.message}")
            publishSubscriptionOutcome(
                state = ActionResultState.FAILURE,
                message = "Failed to fetch subscription options."
            )
        }
    }

    fun dismissSubscriptionOptionsSheet() {
        _showSubscriptionOptionsSheet.value = false
        _subscriptionContext.value = null
    }

    fun dismissPaymentMethodSheet() {
        clearPaymentSelectionState()
        resetInFlightState()
    }

    private fun clearPaymentSelectionState() {
        _showPaymentMethodSheet.value = false
        _selectedSubscription.value = null
    }

    private fun resetInFlightState() {
        _isLoading.value = false
        pendingStripeResponse = null
        isPaymentSelectionInFlight.set(false)
    }

    private fun publishSubscriptionOutcome(state: ActionResultState, message: String) {
        val title = when (state) {
            ActionResultState.SUCCESS -> "Subscription Succeeded"
            ActionResultState.FAILURE -> "Subscription Failed"
            ActionResultState.CANCELLED -> "Subscription Cancelled"
            ActionResultState.PROCESSING -> "Subscription Processing"
        }

        actionResultCenter.publish(
            ActionResultPresentation(
                kind = ActionResultKind.SUBSCRIPTION,
                state = state,
                title = title,
                message = message
            )
        )
    }
}
