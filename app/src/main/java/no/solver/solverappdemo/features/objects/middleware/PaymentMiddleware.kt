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
import no.solver.solverappdemo.data.models.PaymentStatus
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.objects.payment.PaymentService
import no.solver.solverappdemo.features.objects.payment.PaymentStatusPoller
import no.solver.solverappdemo.features.objects.payment.PaymentStorage
import no.solver.solverappdemo.features.objects.payment.StripePaymentHandler
import no.solver.solverappdemo.features.objects.payment.SubscriptionStorage
import no.solver.solverappdemo.features.objects.result.ActionResultCenter
import no.solver.solverappdemo.features.objects.result.ActionResultDetail
import no.solver.solverappdemo.features.objects.result.ActionResultKind
import no.solver.solverappdemo.features.objects.result.ActionResultPresentation
import no.solver.solverappdemo.features.objects.result.ActionResultState

/**
 * Context for a payment flow.
 */
data class PaymentContext(
    val price: String,
    val command: String,
    val commandDisplayName: String? = null,
    val objectId: Int,
    val vendingTransId: Int? = null
) {
    private val actionName: String
        get() = commandDisplayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: command

    val message: String
        get() {
            val commandArticle = indefiniteArticle(actionName)
            val priceText = if (price.isEmpty()) "payment" else "payment of NOK $price"
            return "A $priceText is required to perform $commandArticle action."
        }

    private fun indefiniteArticle(word: String): String {
        val vowels = listOf("a", "e", "i", "o", "u")
        val firstLetter = word.lowercase(Locale.getDefault()).take(1)
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
    private val subscriptionStorage: SubscriptionStorage,
    private val paymentStatusPoller: PaymentStatusPoller,
    private val stripePaymentHandler: StripePaymentHandler,
    private val actionResultCenter: ActionResultCenter
) : CommandMiddleware {

    companion object {
        private const val TAG = "PaymentMiddleware"
    }

    override val name: String = "PaymentMiddleware"
    override val shouldEarlyExit: Boolean = true

    // UI State
    private val _showPaymentSheet = MutableStateFlow(false)
    val showPaymentSheet: StateFlow<Boolean> = _showPaymentSheet.asStateFlow()

    private val _paymentContext = MutableStateFlow<PaymentContext?>(null)
    val paymentContext: StateFlow<PaymentContext?> = _paymentContext.asStateFlow()

    private val _availableMethods = MutableStateFlow<AvailablePaymentMethods?>(null)
    val availableMethods: StateFlow<AvailablePaymentMethods?> = _availableMethods.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Prevent duplicate payment initiation when users tap repeatedly.
    private val isPaymentSelectionInFlight = AtomicBoolean(false)

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
            commandDisplayName = command.displayName,
            objectId = solverObject.id,
            vendingTransId = vendingTransId
        )

        // Store context for sheet presentation
        _paymentContext.value = context
        _availableMethods.value = methods
        _isLoading.value = false
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
        if (!isPaymentSelectionInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "Ignoring duplicate payment method selection while payment is in flight")
            return
        }

        val paymentContext = _paymentContext.value ?: run {
            Log.e(TAG, "Payment context missing when method selected")
            resetInFlightState()
            return
        }

        Log.i(TAG, "User selected payment method: ${method.value}")
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
                command = paymentContext.command,
                commandDisplayName = paymentContext.commandDisplayName
            )

            // Handle payment based on method
            handlePaymentResponse(
                method = method,
                response = paymentResponse,
                context = context,
                commandName = paymentContext.commandDisplayName ?: paymentContext.command
            )
        }.onFailure { error ->
            Log.e(TAG, "Payment initiation failed: ${error.message}")
            publishPaymentOutcome(
                state = ActionResultState.FAILURE,
                method = method,
                commandName = paymentContext.commandDisplayName ?: paymentContext.command,
                reason = error.message ?: "Payment initiation failed"
            )
            resetInFlightState()
        }
    }

    private fun handlePaymentResponse(
        method: PaymentMethod,
        response: PaymentResponse,
        context: Context,
        commandName: String?
    ) {
        when (method) {
            PaymentMethod.STRIPE -> handleStripePayment(response, commandName)
            PaymentMethod.VIPPS,
            PaymentMethod.CARD -> handleExternalPayment(method, response, context, commandName)
        }
    }

    private fun handleStripePayment(response: PaymentResponse, commandName: String?) {
        Log.i(TAG, "Handling Stripe payment...")
        _isLoading.value = false
        _showPaymentSheet.value = false

        stripePaymentHandler.presentPaymentSheet(response) { result ->
            when (result) {
                is PaymentResult.Success -> {
                    Log.i(TAG, "✅ Stripe payment successful")
                    paymentStorage.clearPendingPayment()
                    publishPaymentOutcome(
                        state = ActionResultState.SUCCESS,
                        method = PaymentMethod.STRIPE,
                        commandName = commandName
                    )
                }

                is PaymentResult.Failure -> {
                    Log.e(TAG, "❌ Stripe payment failed: ${result.message}")
                    paymentStorage.clearPendingPayment()
                    publishPaymentOutcome(
                        state = ActionResultState.FAILURE,
                        method = PaymentMethod.STRIPE,
                        commandName = commandName,
                        reason = result.message
                    )
                }

                is PaymentResult.Cancelled -> {
                    Log.i(TAG, "Stripe payment cancelled")
                    paymentStorage.clearPendingPayment()
                    publishPaymentOutcome(
                        state = ActionResultState.CANCELLED,
                        method = PaymentMethod.STRIPE,
                        commandName = commandName,
                        reason = "Payment was cancelled."
                    )
                }
            }

            clearPaymentSelectionState()
            resetInFlightState()
        }
    }

    private fun handleExternalPayment(
        method: PaymentMethod,
        response: PaymentResponse,
        context: Context,
        commandName: String?
    ) {
        Log.i(TAG, "Handling external payment redirect for ${method.value}")
        _isLoading.value = false

        val urlString = response.redirectUrl ?: response.url
        if (urlString == null) {
            Log.e(TAG, "No redirect URL in payment response")
            publishPaymentOutcome(
                state = ActionResultState.FAILURE,
                method = method,
                commandName = commandName,
                reason = "No redirect URL provided"
            )
            clearPaymentSelectionState()
            resetInFlightState()
            return
        }

        try {
            val uri = Uri.parse(urlString)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, uri)
            Log.i(TAG, "Opening external payment URL: $urlString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open payment URL: ${e.message}")

            val openedInBrowser = runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess

            if (!openedInBrowser) {
                publishPaymentOutcome(
                    state = ActionResultState.FAILURE,
                    method = method,
                    commandName = commandName,
                    reason = "Could not open payment page"
                )
            }
        }

        clearPaymentSelectionState()
        resetInFlightState()
    }

    /**
     * Handle payment/subscription callback from deep link.
     */
    suspend fun handlePaymentCallback(method: PaymentMethod, reference: String) {
        Log.i(TAG, "💳 Handling payment callback: method=${method.value}, reference=$reference")

        val pendingPayment = paymentStorage.getPendingPayment()
        val pendingSubscription = subscriptionStorage.getPendingSubscription()
        val isSubscriptionFlow = pendingSubscription?.method == method
        val isRecurringSubscription = pendingSubscription?.option?.subscriptionType?.isRecurring ?: false
        val pendingCommand = if (isSubscriptionFlow) {
            null
        } else {
            pendingPayment?.commandDisplayName ?: pendingPayment?.command
        }
        val kind = if (isSubscriptionFlow) ActionResultKind.SUBSCRIPTION else ActionResultKind.PAYMENT
        val correlationKey = "callback-${method.value}-$reference"

        if (kind == ActionResultKind.PAYMENT) {
            publishPaymentOutcome(
                state = ActionResultState.PROCESSING,
                method = method,
                commandName = pendingCommand,
                reason = "Checking ${method.displayName} status...",
                correlationKey = correlationKey
            )
        } else {
            actionResultCenter.publish(
                ActionResultPresentation(
                    kind = kind,
                    state = ActionResultState.PROCESSING,
                    title = "${kind.displayName} Processing",
                    message = "Checking ${method.displayName} status...",
                    correlationKey = correlationKey
                )
            )
        }

        val status = paymentStatusPoller.pollStatus(
            method = method,
            reference = reference,
            isRecurringSubscription = isRecurringSubscription
        )

        publishPolledResult(
            status = status,
            kind = kind,
            method = method,
            reference = reference,
            commandName = pendingCommand,
            correlationKey = correlationKey
        )

        paymentStorage.clearPendingPayment()
        subscriptionStorage.clearPendingSubscription()
    }

    private fun publishPolledResult(
        status: PaymentStatus,
        kind: ActionResultKind,
        method: PaymentMethod,
        reference: String,
        commandName: String?,
        correlationKey: String
    ) {
        val state = if (status == PaymentStatus.UNKNOWN) {
            ActionResultState.FAILURE
        } else {
            ActionResultState.fromPaymentStatus(status)
        }

        if (kind == ActionResultKind.PAYMENT) {
            val reason = if (status == PaymentStatus.UNKNOWN) {
                "We could not verify the payment status. Please try again."
            } else {
                status.displayName
            }

            publishPaymentOutcome(
                state = state,
                method = method,
                commandName = commandName,
                reason = reason,
                correlationKey = correlationKey
            )
            return
        }

        val title: String
        val message: String
        when (state) {
            ActionResultState.SUCCESS -> {
                title = "${kind.displayName} Succeeded"
                message = "${method.displayName} was confirmed successfully."
            }

            ActionResultState.CANCELLED -> {
                title = "${kind.displayName} Cancelled"
                message = status.displayName
            }

            ActionResultState.FAILURE -> {
                title = "${kind.displayName} Failed"
                message = if (status == PaymentStatus.UNKNOWN) {
                    "We could not verify the payment status. Please try again."
                } else {
                    status.displayName
                }
            }

            ActionResultState.PROCESSING -> {
                title = "${kind.displayName} Processing"
                message = "Still waiting for final status."
            }
        }

        actionResultCenter.publish(
            ActionResultPresentation(
                kind = kind,
                state = state,
                title = title,
                message = message,
                details = listOf(
                    ActionResultDetail(label = "Method", value = method.displayName),
                    ActionResultDetail(label = "Reference", value = reference)
                ),
                correlationKey = correlationKey
            )
        )
    }

    fun dismissPaymentSheet() {
        clearPaymentSelectionState()
        resetInFlightState()
    }

    private fun publishPaymentOutcome(
        state: ActionResultState,
        method: PaymentMethod,
        commandName: String?,
        reason: String? = null,
        correlationKey: String? = null
    ) {
        val commandDisplayName = commandName?.let(::displayCommandName)
        val presentationKind = if (commandDisplayName == null) {
            ActionResultKind.PAYMENT
        } else {
            ActionResultKind.COMMAND
        }

        val title: String
        val message: String

        when (state) {
            ActionResultState.PROCESSING -> {
                if (commandDisplayName != null) {
                    title = "$commandDisplayName Processing"
                    message = reason ?: "Finalizing payment with ${method.displayName}..."
                } else {
                    title = "Payment Processing"
                    message = reason ?: "Checking payment status..."
                }
            }

            ActionResultState.SUCCESS -> {
                if (commandDisplayName != null) {
                    title = "$commandDisplayName Succeeded"
                    message = "Payment with ${method.displayName} completed."
                } else {
                    title = "Payment Succeeded"
                    message = "Your payment was successful."
                }
            }

            ActionResultState.CANCELLED -> {
                if (commandDisplayName != null) {
                    title = "$commandDisplayName Cancelled"
                    message = reason ?: "Payment was cancelled."
                } else {
                    title = "Payment Cancelled"
                    message = reason ?: "Payment was cancelled."
                }
            }

            ActionResultState.FAILURE -> {
                if (commandDisplayName != null) {
                    title = "$commandDisplayName Failed"
                    message = reason ?: "Payment failed, so the command was not completed."
                } else {
                    title = "Payment Failed"
                    message = reason ?: "Payment was unsuccessful."
                }
            }
        }

        actionResultCenter.publish(
            ActionResultPresentation(
                kind = presentationKind,
                state = state,
                title = title,
                message = message,
                correlationKey = correlationKey
            )
        )
    }

    private fun displayCommandName(command: String): String {
        val words = command
            .trim()
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .map {
                it.lowercase(Locale.getDefault())
                    .replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
            }

        return if (words.isEmpty()) "Command" else words.joinToString(separator = " ")
    }

    private fun clearPaymentSelectionState() {
        _showPaymentSheet.value = false
        _paymentContext.value = null
        _availableMethods.value = null
    }

    private fun resetInFlightState() {
        _isLoading.value = false
        isPaymentSelectionInFlight.set(false)
    }
}
