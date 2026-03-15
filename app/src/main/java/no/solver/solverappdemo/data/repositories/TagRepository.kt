package no.solver.solverappdemo.data.repositories

import android.util.Log
import kotlinx.serialization.json.Json
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for tag-based operations.
 * Used by QR/NFC deep linking to fetch objects and execute commands by tag.
 * 
 * API Endpoints:
 * - GET /api/Object/Tag/{tagId} - Fetch object by tag
 * - PUT /api/Object/Execute/{tag}/{command} - Execute command by tag
 */
@Singleton
class TagRepository @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager,
    private val json: Json
) {
    companion object {
        private const val TAG = "TagRepository"
    }

    /**
     * Fetch object by tag ID.
     * API: GET /api/Object/Tag/{tagId}
     */
    suspend fun getObjectByTag(tag: String): ApiResult<SolverObject> {
        Log.i(TAG, "Fetching object by tag: $tag")
        
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getObjectByTag(tag)

            if (response.isSuccessful) {
                val dto = response.body()
                    ?: throw ApiException.NotFound("Object not found for tag: $tag")
                val solverObject = dto.toDomainModel()
                Log.i(TAG, "✅ Successfully fetched object: ${solverObject.name} (ID: ${solverObject.id})")
                solverObject
            } else {
                Log.e(TAG, "❌ Failed to fetch object by tag: ${response.code()} ${response.message()}")
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    /**
     * Execute command by tag.
     * API: PUT /api/Object/Execute/{tag}/{command}
     * 
     * @param command The command to execute (e.g., "unlock", "lock")
     * @param tag The tag ID
     * @param latitude Optional latitude for geofence commands
     * @param longitude Optional longitude for geofence commands
     */
    suspend fun executeCommandByTag(
        command: String,
        tag: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): ApiResult<ExecuteResponse> {
        Log.i(TAG, "Executing command '$command' by tag: $tag")
        
        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            Log.i(TAG, "🔐 Session: provider=${session.provider}, environment=${session.environment}")
            Log.i(TAG, "🔑 Token present: ${session.tokens.accessToken.take(20)}...")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            // TODO: Add location support when implementing geofence features
            Log.i(TAG, "🌐 Calling API: PUT /api/Object/Execute/$tag/$command")
            val response = apiService.executeCommandByTag(tag, command)
            Log.i(TAG, "📥 Response code: ${response.code()}, message: ${response.message()}")

            if (response.isSuccessful) {
                val executeResponse = response.body()
                    ?: throw ApiException.Unknown("Empty response")
                Log.i(TAG, "✅ Command executed. Success: ${executeResponse.success}")
                executeResponse
            } else if (response.code() == 403) {
                // For command execution, 403 might contain valid middleware context
                // (payment required, subscription required, geofence override, etc.)
                val errorBody = response.errorBody()?.string()
                Log.w(TAG, "⚠️ Got 403 response. Error body: $errorBody")
                if (errorBody != null) {
                    try {
                        val executeResponse = json.decodeFromString<ExecuteResponse>(errorBody)
                        Log.i(TAG, "✅ Command returned 403 with middleware context")
                        executeResponse
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to parse 403 response: ${e.message}")
                        throw ApiException.Forbidden("Access denied")
                    }
                } else {
                    throw ApiException.Forbidden("Access denied")
                }
            } else {
                Log.e(TAG, "❌ Command execution failed: ${response.code()} ${response.message()}")
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }

    suspend fun createBookingSignedUrl(objectId: Int): ApiResult<String> {
        Log.i(TAG, "Creating booking signed URL for object: $objectId")

        return ApiResult.runCatching {
            val session = sessionManager.getCurrentSession()
                ?: throw ApiException.Unauthorized("No active session")

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.createBookingSignedUrl(objectId)

            if (response.isSuccessful) {
                val rawBody = response.body()?.string()
                    ?: throw ApiException.Unknown("Empty booking signed URL response")

                val guid = rawBody
                    .trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .trim()

                if (guid.isEmpty()) {
                    throw ApiException.Unknown("Invalid booking signed URL response")
                }

                guid
            } else {
                throw ApiException.fromHttpCode(response.code(), response.message())
            }
        }
    }
}
