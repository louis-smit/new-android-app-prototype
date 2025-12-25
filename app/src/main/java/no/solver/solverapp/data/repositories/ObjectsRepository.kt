package no.solver.solverapp.data.repositories

import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider
import no.solver.solverapp.core.network.ApiException
import no.solver.solverapp.core.network.ApiResult
import no.solver.solverapp.data.api.ApiClientManager
import no.solver.solverapp.data.models.CommandExecutionRequest
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.features.auth.services.SessionManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectsRepository @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    suspend fun getUserObjects(): ApiResult<List<SolverObject>> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getUserObjects()

            if (response.isSuccessful) {
                response.body()?.map { it.toDomainModel() } ?: emptyList()
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    suspend fun getObject(objectId: Int): ApiResult<SolverObject> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getObject(objectId)

            if (response.isSuccessful) {
                response.body()?.toDomainModel()
                    ?: throw ApiException.NotFound("Object not found")
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    suspend fun executeCommand(
        objectId: Int,
        command: String,
        input: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): ApiResult<ExecuteResponse> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val locationData = if (latitude != null && longitude != null) {
                no.solver.solverapp.data.models.LocationData(latitude, longitude)
            } else null

            val request = CommandExecutionRequest(
                command = command,
                input = input,
                location = locationData
            )

            val response = apiService.executeCommand(objectId, request)

            if (response.isSuccessful) {
                response.body() ?: throw ApiException.Unknown("Empty response")
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }
}
