package no.solver.solverappdemo.features.objects.middleware

import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

sealed class MiddlewareResult {
    data class Handled(
        val message: String,
        val suppressDebugUI: Boolean = false
    ) : MiddlewareResult()

    data object NotApplicable : MiddlewareResult()
}

interface CommandMiddleware {
    val name: String
    val shouldEarlyExit: Boolean

    fun matches(response: ExecuteResponse, command: Command): Boolean

    suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult
}
