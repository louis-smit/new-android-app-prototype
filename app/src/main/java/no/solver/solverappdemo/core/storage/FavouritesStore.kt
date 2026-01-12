package no.solver.solverappdemo.core.storage

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouritesStore @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "FavouritesStore"
    }

    private val _favourites = MutableStateFlow<List<SolverObject>>(emptyList())
    val favourites: StateFlow<List<SolverObject>> = _favourites.asStateFlow()

    private val _favouriteIds = MutableStateFlow<Set<Int>>(emptySet())
    val favouriteIds: StateFlow<Set<Int>> = _favouriteIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun loadFavourites(): ApiResult<List<SolverObject>> {
        _isLoading.value = true
        return try {
            val session = sessionManager.getCurrentSession()
                ?: return ApiResult.Error(ApiException.Unauthorized("No active session"))

            val apiService = apiClientManager.getApiService(
                environment = session.environment,
                provider = session.provider
            )

            val response = apiService.getFavourites()

            if (response.isSuccessful) {
                val objects = response.body()?.map { it.toDomainModel() } ?: emptyList()
                val sortedObjects = objects.sortedBy { it.name.lowercase() }
                _favourites.value = sortedObjects
                _favouriteIds.value = objects.map { it.id }.toSet()
                Log.d(TAG, "Loaded ${objects.size} favourites from API")
                ApiResult.Success(sortedObjects)
            } else {
                val error = ApiException.fromHttpCode(response.code(), response.message())
                Log.e(TAG, "Failed to load favourites: ${error.message}")
                ApiResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error loading favourites: ${e.message}")
            ApiResult.Error(ApiException.Network(e.message ?: "Network error"))
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun isFavourite(objectId: Int): ApiResult<Boolean> {
        val session = sessionManager.getCurrentSession()
            ?: return ApiResult.Error(ApiException.Unauthorized("No active session"))

        val apiService = apiClientManager.getApiService(
            environment = session.environment,
            provider = session.provider
        )

        return try {
            val response = apiService.isFavourite(objectId)
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: false)
            } else {
                ApiResult.Error(ApiException.fromHttpCode(response.code(), response.message()))
            }
        } catch (e: Exception) {
            ApiResult.Error(ApiException.Unknown(e.message ?: "Unknown error"))
        }
    }

    suspend fun addFavourite(objectId: Int): ApiResult<Unit> {
        val session = sessionManager.getCurrentSession()
            ?: return ApiResult.Error(ApiException.Unauthorized("No active session"))

        val apiService = apiClientManager.getApiService(
            environment = session.environment,
            provider = session.provider
        )

        return try {
            val response = apiService.addFavourite(objectId)
            if (response.isSuccessful) {
                Log.d(TAG, "Added favourite: $objectId")
                _favouriteIds.value = _favouriteIds.value + objectId
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(ApiException.fromHttpCode(response.code(), response.message()))
            }
        } catch (e: Exception) {
            ApiResult.Error(ApiException.Unknown(e.message ?: "Unknown error"))
        }
    }

    suspend fun removeFavourite(objectId: Int): ApiResult<Unit> {
        val session = sessionManager.getCurrentSession()
            ?: return ApiResult.Error(ApiException.Unauthorized("No active session"))

        val apiService = apiClientManager.getApiService(
            environment = session.environment,
            provider = session.provider
        )

        return try {
            val response = apiService.removeFavourite(objectId)
            if (response.isSuccessful) {
                Log.d(TAG, "Removed favourite: $objectId")
                _favouriteIds.value = _favouriteIds.value - objectId
                _favourites.value = _favourites.value.filter { it.id != objectId }
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(ApiException.fromHttpCode(response.code(), response.message()))
            }
        } catch (e: Exception) {
            ApiResult.Error(ApiException.Unknown(e.message ?: "Unknown error"))
        }
    }

    fun isFavouriteLocal(objectId: Int): Boolean {
        return _favouriteIds.value.contains(objectId)
    }

    fun applyOptimisticAdd(obj: SolverObject) {
        if (!_favouriteIds.value.contains(obj.id)) {
            _favouriteIds.value = _favouriteIds.value + obj.id
            _favourites.value = (_favourites.value + obj).sortedBy { it.name.lowercase() }
        }
    }

    fun applyOptimisticRemove(objectId: Int) {
        _favouriteIds.value = _favouriteIds.value - objectId
        _favourites.value = _favourites.value.filter { it.id != objectId }
    }

    fun rollback(previousIds: Set<Int>, previousFavourites: List<SolverObject>) {
        _favouriteIds.value = previousIds
        _favourites.value = previousFavourites
    }
}
