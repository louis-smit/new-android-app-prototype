package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

data class SubscriptionContext(
    val command: String,
    val objectId: Int
)

class SubscriptionMiddleware : CommandMiddleware {

    companion object {
        private const val TAG = "SubscriptionMiddleware"
    }

    override val name: String = "SubscriptionMiddleware"
    override val shouldEarlyExit: Boolean = true

    private val _showSubscriptionSheet = MutableStateFlow(false)
    val showSubscriptionSheet: StateFlow<Boolean> = _showSubscriptionSheet.asStateFlow()

    private val _subscriptionContext = MutableStateFlow<SubscriptionContext?>(null)
    val subscriptionContext: StateFlow<SubscriptionContext?> = _subscriptionContext.asStateFlow()

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

        Log.i(TAG, "Subscription required for command: ${command.commandName}")

        val context = SubscriptionContext(
            command = command.commandName,
            objectId = solverObject.id
        )

        _subscriptionContext.value = context
        _showSubscriptionSheet.value = true

        Log.i(TAG, "Showing subscription options sheet")

        return MiddlewareResult.Handled(
            message = "Subscription flow initiated",
            suppressDebugUI = true
        )
    }

    fun dismissSubscriptionSheet() {
        _showSubscriptionSheet.value = false
        _subscriptionContext.value = null
    }

    fun handleSubscriptionSelected(subscriptionId: String) {
        val context = _subscriptionContext.value ?: return
        Log.i(TAG, "User selected subscription: $subscriptionId")

        // TODO: Implement subscription purchase flow
        // For now, just dismiss the sheet
        dismissSubscriptionSheet()
    }
}
