package no.solver.solverappdemo.data.repositories

import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.ObjectLog
import no.solver.solverappdemo.features.auth.services.SessionManager
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogsRepository @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    suspend fun fetchUserLogs(): ApiResult<List<ObjectLog>> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getUserLogs()

            if (response.isSuccessful) {
                sortLogs(response.body() ?: emptyList())
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    suspend fun fetchObjectLogs(objectId: Int): ApiResult<List<ObjectLog>> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getObjectLogs(objectId)

            if (response.isSuccessful) {
                sortLogs(response.body() ?: emptyList())
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    suspend fun fetchAdminLogs(): ApiResult<List<ObjectLog>> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getAdminLogs()

            if (response.isSuccessful) {
                sortLogs(response.body() ?: emptyList())
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    private fun sortLogs(logs: List<ObjectLog>): List<ObjectLog> {
        return logs.sortedByDescending { log ->
            log.createdAt?.let { dateString ->
                try {
                    val adjusted = if (!dateString.endsWith("Z")) dateString + "Z" else dateString
                    ZonedDateTime.parse(adjusted)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
