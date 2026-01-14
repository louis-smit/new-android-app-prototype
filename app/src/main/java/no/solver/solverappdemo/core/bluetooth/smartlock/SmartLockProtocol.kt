package no.solver.solverappdemo.core.bluetooth.smartlock

import kotlinx.coroutines.flow.StateFlow
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject

/**
 * Unified interface for all smart lock brands.
 * Each lock brand implements this interface via an adapter.
 * 
 * Matches iOS SmartLockProtocol for consistency.
 */
interface SmartLockProtocol {
    /**
     * The brand of this lock
     */
    val brand: SmartLockBrand

    /**
     * What this lock supports
     */
    val capabilities: SmartLockCapabilities

    /**
     * Current status (state, battery, range)
     */
    val status: StateFlow<SmartLockStatus>

    /**
     * Check if we have valid cached tokens
     */
    fun hasValidTokens(objectId: Int): Boolean

    /**
     * Fetch and cache new tokens from the server (GetKeys equivalent)
     */
    suspend fun fetchTokens(solverObject: SolverObject): Result<SmartLockTokens>

    /**
     * Clear cached tokens
     */
    fun clearTokens(objectId: Int)

    /**
     * Execute unlock command
     */
    suspend fun unlock(solverObject: SolverObject): Result<String>

    /**
     * Execute lock command (if supported)
     */
    suspend fun lock(solverObject: SolverObject): Result<String>

    /**
     * Start polling for lock state (if supported)
     */
    fun startPolling(solverObject: SolverObject)

    /**
     * Stop polling
     */
    fun stopPolling()

    /**
     * Stop polling and wait for cleanup
     */
    suspend fun stopPollingAndWait()

    /**
     * Manually check lock status (connect, read state, disconnect)
     * Use for locks that don't support continuous polling (e.g., Masterlock)
     */
    suspend fun checkStatus(solverObject: SolverObject): Result<SmartLockState>

    /**
     * Parse tokens from an ExecuteResponse context
     */
    fun parseTokens(response: ExecuteResponse, objectId: Int): SmartLockTokens?
}
