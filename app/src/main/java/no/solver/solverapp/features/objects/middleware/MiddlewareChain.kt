package no.solver.solverapp.features.objects.middleware

import android.util.Log
import no.solver.solverapp.data.models.Command
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository

data class MiddlewareProcessResult(
    val handled: Boolean,
    val message: String?,
    val shouldShowDebugUI: Boolean
)

class MiddlewareChain(
    private val middlewares: List<CommandMiddleware>
) {
    companion object {
        private const val TAG = "MiddlewareChain"

        fun createStandardChain(
            repository: ObjectsRepository,
            paymentMiddleware: PaymentMiddleware,
            subscriptionMiddleware: SubscriptionMiddleware,
            onShowStatusSheet: (ExecuteResponse) -> Unit
        ): MiddlewareChain {
            val middlewares = listOf(
                paymentMiddleware,
                subscriptionMiddleware,
                GeofenceMiddleware(),
                SetAPIStatusMiddleware(repository),
                StatusMiddleware(onShowStatusSheet)
            )
            return MiddlewareChain(middlewares)
        }
    }

    suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareProcessResult {
        Log.d(TAG, "Processing middleware chain for command: ${command.commandName}")

        var middlewareHandled = false
        var middlewareMessage: String? = null
        var shouldShowDebugUI = false

        for (middleware in middlewares) {
            if (middleware.matches(response, command)) {
                Log.d(TAG, "✅ Matched: ${middleware.name}")

                val result = middleware.process(response, command, solverObject)

                when (result) {
                    is MiddlewareResult.Handled -> {
                        Log.d(TAG, "🎯 ${middleware.name} handled the response")
                        middlewareHandled = true
                        middlewareMessage = "[${middleware.name}] ${result.message}"

                        if (!result.suppressDebugUI) {
                            shouldShowDebugUI = true
                        }

                        if (middleware.shouldEarlyExit) {
                            Log.d(TAG, "⏹️ Early exit after ${middleware.name}")
                            break
                        }
                    }
                    is MiddlewareResult.NotApplicable -> {
                        Log.d(TAG, "⏭️ ${middleware.name} not applicable")
                    }
                }
            }
        }

        if (!middlewareHandled) {
            Log.d(TAG, "🔵 No middleware handled response, showing generic UI")
            shouldShowDebugUI = true
        }

        return MiddlewareProcessResult(
            handled = middlewareHandled,
            message = middlewareMessage,
            shouldShowDebugUI = shouldShowDebugUI
        )
    }
}
