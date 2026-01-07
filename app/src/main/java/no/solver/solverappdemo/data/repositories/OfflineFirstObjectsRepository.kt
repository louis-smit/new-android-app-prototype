package no.solver.solverappdemo.data.repositories

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.cache.ObjectsCacheRepository
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

sealed class ObjectsLoadResult {
    data class Success(
        val objects: List<SolverObject>,
        val isFromCache: Boolean,
        val lastSyncedAt: Long?
    ) : ObjectsLoadResult()

    data class Error(
        val exception: Exception,
        val cachedObjects: List<SolverObject>?,
        val lastSyncedAt: Long?
    ) : ObjectsLoadResult()
}

@Singleton
class OfflineFirstObjectsRepository @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager,
    private val cacheRepository: ObjectsCacheRepository
) {
    companion object {
        private const val TAG = "OfflineFirstObjectsRepo"
    }

    fun observeCachedObjects(): Flow<List<SolverObject>> {
        val accountId = getCurrentAccountId() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return cacheRepository.observeObjects(accountId)
    }

    suspend fun loadObjects(forceRefresh: Boolean = false): ObjectsLoadResult {
        val accountId = getCurrentAccountId()
            ?: return ObjectsLoadResult.Error(
                exception = ApiException.Unauthorized("No active session"),
                cachedObjects = null,
                lastSyncedAt = null
            )

        val cachedObjects = cacheRepository.getCachedObjects(accountId)
        val lastSyncedAt = cacheRepository.getLastSyncTime(accountId)

        if (cachedObjects.isNotEmpty() && !forceRefresh) {
            Log.d(TAG, "Returning ${cachedObjects.size} cached objects")
            fetchAndCacheInBackground(accountId)
            return ObjectsLoadResult.Success(
                objects = cachedObjects,
                isFromCache = true,
                lastSyncedAt = lastSyncedAt
            )
        }

        return fetchFromNetwork(accountId, cachedObjects, lastSyncedAt)
    }

    suspend fun refreshObjects(): ObjectsLoadResult {
        val accountId = getCurrentAccountId()
            ?: return ObjectsLoadResult.Error(
                exception = ApiException.Unauthorized("No active session"),
                cachedObjects = null,
                lastSyncedAt = null
            )

        val cachedObjects = cacheRepository.getCachedObjects(accountId)
        val lastSyncedAt = cacheRepository.getLastSyncTime(accountId)

        return fetchFromNetwork(accountId, cachedObjects, lastSyncedAt)
    }

    private suspend fun fetchFromNetwork(
        accountId: String,
        cachedObjects: List<SolverObject>,
        lastSyncedAt: Long?
    ): ObjectsLoadResult {
        return when (val result = fetchObjectsFromApi()) {
            is ApiResult.Success -> {
                cacheRepository.cacheObjects(result.data, accountId)
                val newLastSyncedAt = cacheRepository.getLastSyncTime(accountId)
                Log.d(TAG, "Fetched and cached ${result.data.size} objects from API")
                ObjectsLoadResult.Success(
                    objects = result.data,
                    isFromCache = false,
                    lastSyncedAt = newLastSyncedAt
                )
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Network error: ${result.exception.message}")
                if (cachedObjects.isNotEmpty()) {
                    ObjectsLoadResult.Success(
                        objects = cachedObjects,
                        isFromCache = true,
                        lastSyncedAt = lastSyncedAt
                    )
                } else {
                    ObjectsLoadResult.Error(
                        exception = result.exception,
                        cachedObjects = null,
                        lastSyncedAt = null
                    )
                }
            }
        }
    }

    private suspend fun fetchAndCacheInBackground(accountId: String) {
        when (val result = fetchObjectsFromApi()) {
            is ApiResult.Success -> {
                cacheRepository.cacheObjects(result.data, accountId)
                Log.d(TAG, "Background sync completed: ${result.data.size} objects")
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Background sync failed: ${result.exception.message}")
            }
        }
    }

    private suspend fun fetchObjectsFromApi(): ApiResult<List<SolverObject>> {
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

    suspend fun getLastSyncTime(): Long? {
        val accountId = getCurrentAccountId() ?: return null
        return cacheRepository.getLastSyncTime(accountId)
    }

    suspend fun clearCacheForCurrentAccount() {
        val accountId = getCurrentAccountId() ?: return
        cacheRepository.clearCacheForAccount(accountId)
        Log.d(TAG, "Cleared cache for account: $accountId")
    }

    suspend fun clearAllCache() {
        cacheRepository.clearAllCache()
        Log.d(TAG, "Cleared all cache")
    }

    private fun getCurrentAccountId(): String? {
        return kotlinx.coroutines.runBlocking {
            sessionManager.getCurrentSession()?.id
        }
    }
}
