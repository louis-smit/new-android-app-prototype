package no.solver.solverappdemo.features.objects.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ContextItem
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared status logging service for command execution results.
 * Keeps middleware-triggered and direct smart-lock flows on the same API path.
 */
@Singleton
class APIStatusService @Inject constructor(
    private val objectsRepository: ObjectsRepository
) {
    companion object {
        private const val TAG = "APIStatusService"
    }

    private val fireAndForgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun logCommandExecution(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject,
        additionalContextItems: List<ContextItem> = emptyList(),
        waitForResult: Boolean = false
    ) {
        if (!response.success) {
            Log.d(TAG, "Skipping SetStatus log for failed response")
            return
        }

        val baseContext = mutableListOf(
            ContextItem(
                key = "command",
                label = "Command",
                value = command.commandName
            )
        )
        baseContext += additionalContextItems

        if (waitForResult) {
            logToServer(response, solverObject.id, baseContext)
        } else {
            fireAndForgetScope.launch {
                logToServer(response, solverObject.id, baseContext)
            }
        }
    }

    private suspend fun logToServer(
        response: ExecuteResponse,
        objectId: Int,
        contextItems: List<ContextItem>
    ) {
        Log.d(TAG, "Posting command status to /api/Object/$objectId/SetStatus")
        when (val result = objectsRepository.logCommand(response, contextItems, objectId)) {
            is ApiResult.Success -> {
                Log.i(TAG, "SetStatus logged successfully for object $objectId")
            }

            is ApiResult.Error -> {
                Log.e(TAG, "SetStatus logging failed: ${result.exception.message}")
            }
        }
    }
}
