package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

class StatusMiddleware(
    private val onShowActionResult: (ExecuteResponse, Command, SolverObject) -> Unit
) : CommandMiddleware {

    companion object {
        private const val TAG = "StatusMiddleware"
    }

    override val name: String = "StatusMiddleware"
    override val shouldEarlyExit: Boolean = false

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        val commandName = command.commandName.lowercase()
        return commandName == "status" || commandName == "adminstatus"
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        Log.d(TAG, "Status command detected, showing unified action result")
        Log.d(TAG, "  → Command: ${command.commandName}")
        Log.d(TAG, "  → Object: ${solverObject.name}")
        Log.d(TAG, "  → Success: ${response.success}")

        onShowActionResult(response, command, solverObject)

        return MiddlewareResult.Handled(
            message = "Status displayed in action result sheet",
            suppressDebugUI = true
        )
    }
}
