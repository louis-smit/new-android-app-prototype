package no.solver.solverappdemo.core.bluetooth.smartlock

import android.util.Log
import no.solver.solverappdemo.data.models.SolverObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SmartLockManager"

/**
 * Factory and registry for smart lock adapters.
 * Provides the correct adapter based on object type.
 * 
 * Matches iOS SmartLockManager for consistency.
 */
@Singleton
class SmartLockManager @Inject constructor(
    private val danalockAdapter: DanalockAdapter,
    private val masterlockAdapter: MasterlockAdapter,
    private val tokenCache: SmartLockTokenCache
) {

    /**
     * Get the appropriate lock adapter for an object.
     * Returns null if the object is not a smart lock.
     */
    fun adapter(solverObject: SolverObject): SmartLockProtocol? {
        val brand = SmartLockBrand.fromObjectTypeId(solverObject.objectTypeId) ?: return null
        return adapter(brand)
    }

    /**
     * Get the appropriate lock adapter for a brand.
     */
    fun adapter(brand: SmartLockBrand): SmartLockProtocol {
        return when (brand) {
            SmartLockBrand.DANALOCK -> danalockAdapter
            SmartLockBrand.MASTERLOCK -> masterlockAdapter
        }
    }

    /**
     * Check if an object is a smart lock.
     */
    fun isSmartLock(solverObject: SolverObject): Boolean {
        return SmartLockBrand.fromObjectTypeId(solverObject.objectTypeId) != null
    }

    /**
     * Get the lock brand for an object (if any).
     */
    fun lockBrand(solverObject: SolverObject): SmartLockBrand? {
        return SmartLockBrand.fromObjectTypeId(solverObject.objectTypeId)
    }

    /**
     * Stop all lock adapters (call when leaving detail view).
     */
    suspend fun stopAllAdapters() {
        Log.i(TAG, "Stopping all smart lock adapters")
        danalockAdapter.stopPollingAndWait()
        masterlockAdapter.stopPollingAndWait()
    }

    /**
     * Clear all cached tokens.
     */
    fun clearAllTokens() {
        Log.i(TAG, "Clearing all smart lock tokens")
        tokenCache.clearAll()
    }
}
