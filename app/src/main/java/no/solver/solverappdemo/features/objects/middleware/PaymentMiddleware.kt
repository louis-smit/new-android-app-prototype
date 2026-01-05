package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

data class PaymentContext(
    val price: String,
    val command: String,
    val objectId: Int,
    val vendingTransId: Int? = null
)

class PaymentMiddleware : CommandMiddleware {

    companion object {
        private const val TAG = "PaymentMiddleware"
    }

    override val name: String = "PaymentMiddleware"
    override val shouldEarlyExit: Boolean = true

    private val _showPaymentSheet = MutableStateFlow(false)
    val showPaymentSheet: StateFlow<Boolean> = _showPaymentSheet.asStateFlow()

    private val _paymentContext = MutableStateFlow<PaymentContext?>(null)
    val paymentContext: StateFlow<PaymentContext?> = _paymentContext.asStateFlow()

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

        Log.i(TAG, "Payment required for command: ${command.commandName}")

        val priceString = response.findValueInContext("paymentRequired") ?: ""
        val vendingTransIdString = response.findValueInContext("vendingTransId")
        val vendingTransId = vendingTransIdString?.toIntOrNull()

        val context = PaymentContext(
            price = priceString,
            command = command.commandName,
            objectId = solverObject.id,
            vendingTransId = vendingTransId
        )

        _paymentContext.value = context
        _showPaymentSheet.value = true

        Log.i(TAG, "Showing payment sheet. Price: $priceString")

        return MiddlewareResult.Handled(
            message = "Payment flow initiated",
            suppressDebugUI = true
        )
    }

    fun dismissPaymentSheet() {
        _showPaymentSheet.value = false
        _paymentContext.value = null
    }

    fun handlePaymentMethodSelected(method: String) {
        val context = _paymentContext.value ?: return
        Log.i(TAG, "User selected payment method: $method")

        // TODO: Implement Stripe/Vipps payment flow
        // For now, just dismiss the sheet
        dismissPaymentSheet()
    }
}
