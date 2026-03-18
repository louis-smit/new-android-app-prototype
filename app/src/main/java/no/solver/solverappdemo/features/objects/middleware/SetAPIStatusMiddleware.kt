package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.objects.services.APIStatusService

class SetAPIStatusMiddleware(
    private val statusService: APIStatusService
) : CommandMiddleware {

    companion object {
        private const val TAG = "SetAPIStatusMiddleware"
    }

    override val name: String = "SetAPIStatusMiddleware"
    override val shouldEarlyExit: Boolean = false

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        return response.success
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        Log.d(TAG, "Logging command execution to SetStatus for object ${solverObject.id}")
        statusService.logCommandExecution(
            response = response,
            command = command,
            solverObject = solverObject,
            waitForResult = false
        )

        return MiddlewareResult.NotApplicable
    }
}
