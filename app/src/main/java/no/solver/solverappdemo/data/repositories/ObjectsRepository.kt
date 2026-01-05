package no.solver.solverappdemo.data.repositories

import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.CommandExecutionRequest
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.services.SessionManager
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

            // TODO: Add location support when implementing geofence features
            // val locationData = if (latitude != null && longitude != null) {
            //     LocationData(latitude, longitude)
            // } else null

            val response = if (input != null) {
                apiService.executeCommandWithInput(objectId, command, input)
            } else {
                apiService.executeCommand(objectId, command)
            }

            if (response.isSuccessful) {
                response.body() ?: throw ApiException.Unknown("Empty response")
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }
}
