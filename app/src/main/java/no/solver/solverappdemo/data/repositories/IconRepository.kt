package no.solver.solverappdemo.data.repositories

import android.util.Log
import no.solver.solverappdemo.core.cache.IconCacheManager
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IconRepository"

/**
 * Repository for fetching and caching object type icons.
 * 
 * Pre-fetches all icons at startup to ensure smooth scrolling in object lists.
 */
@Singleton
class IconRepository @Inject constructor(
    private val iconCacheManager: IconCacheManager,
    private val sessionManager: SessionManager,
    private val apiClientManager: ApiClientManager
) {
    
    /**
     * Pre-fetch all icons from the API and cache them.
     * Should be called after successful authentication.
     */
    suspend fun prefetchIcons() {
        try {
            val session = sessionManager.getCurrentSession() ?: run {
                Log.w(TAG, "No active session, cannot prefetch icons")
                return
            }
            
            val api = apiClientManager.getApiService(session.environment, session.provider)
            
            val response = api.getResourceIcons()
            if (response.isSuccessful) {
                val icons = response.body() ?: emptyList()
                iconCacheManager.cacheIcons(icons)
                Log.i(TAG, "Pre-fetched ${icons.size} icons")
            } else {
                Log.e(TAG, "Failed to fetch icons: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pre-fetching icons: ${e.message}")
        }
    }
    
    /**
     * Get the IconCacheManager for direct access to cached icons.
     */
    fun getCacheManager(): IconCacheManager = iconCacheManager
}
