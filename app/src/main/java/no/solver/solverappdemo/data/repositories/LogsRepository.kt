package no.solver.solverappdemo.data.repositories

import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.ObjectLog
import no.solver.solverappdemo.features.auth.services.SessionManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
                parseBackendTimestamp(dateString)
            }
        }
    }

    private fun parseBackendTimestamp(dateString: String): Instant? {
        return try {
            if (dateString.hasExplicitOffset()) {
                OffsetDateTime.parse(dateString).toInstant()
            } else {
                LocalDateTime.parse(dateString).toInstant(ZoneOffset.UTC)
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun String.hasExplicitOffset(): Boolean {
        return endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(this)
    }
}
