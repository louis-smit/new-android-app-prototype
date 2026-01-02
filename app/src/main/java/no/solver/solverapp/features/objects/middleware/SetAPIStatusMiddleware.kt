package no.solver.solverapp.features.objects.middleware

import android.util.Log
import no.solver.solverapp.data.models.Command
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository

class SetAPIStatusMiddleware(
    private val repository: ObjectsRepository
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

        Log.d(TAG, "Logging command execution to server for object ${solverObject.id}")

        // TODO: Implement SetAPIStatus API call when endpoint is available
        // This would log the command execution result to the server
        // For now, just log locally
        Log.i(TAG, "Command '${command.commandName}' executed successfully on '${solverObject.name}'")

        return MiddlewareResult.NotApplicable
    }
}
