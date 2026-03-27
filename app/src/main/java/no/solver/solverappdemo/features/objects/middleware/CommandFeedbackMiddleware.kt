package no.solver.solverappdemo.features.objects.middleware

import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

/**
 * Production command feedback for non-status commands that do not have dedicated middleware UI.
 */
class CommandFeedbackMiddleware(
    private val onShowCommandFeedback: (ExecuteResponse, Command, SolverObject) -> Unit
) : CommandMiddleware {

    override val name: String = "CommandFeedbackMiddleware"
    override val shouldEarlyExit: Boolean = false

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        val commandName = command.commandName.lowercase()
        val isStatusCommand = commandName == "status" || commandName == "adminstatus"
        if (isStatusCommand) return false

        val hasDedicatedFlow = response.hasContextKey("paymentRequired") ||
            response.hasContextKey("subscriptionRequired") ||
            response.hasContextKey("geofenceoverride")

        return !hasDedicatedFlow
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        onShowCommandFeedback(response, command, solverObject)

        return MiddlewareResult.Handled(
            message = "Command result displayed in action result sheet",
            suppressDebugUI = true
        )
    }
}
