package no.solver.solverappdemo.data.repositories

import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentsRepository @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    suspend fun getSignedUrl(): ApiResult<String> {
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getSignedUrl()

            if (response.isSuccessful) {
                response.body()?.string()?.trim()?.trim('"')
                    ?: throw ApiException.Unknown("Empty response")
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }
}
