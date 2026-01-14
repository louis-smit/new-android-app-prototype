package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockBrand
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockManager
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import javax.inject.Inject

private const val TAG = "MasterlockMiddleware"

/**
 * Middleware for handling Masterlock Bluetooth lock commands.
 * 
 * Triggers when:
 * - Object type is 10 (Masterlock)
 * - Response contains Masterlock context keys (deviceIdentifier, accessProfile)
 * - Command is unlock (Masterlock doesn't support remote lock)
 * 
 * Behavior:
 * - Intercepts the server response and executes the actual Bluetooth command
 * - Reports success/failure to the user
 * - Early exits to prevent other middleware from running
 */
class MasterlockMiddleware @Inject constructor(
    private val smartLockManager: SmartLockManager
) : CommandMiddleware {

    override val name: String = "MasterlockMiddleware"
    override val shouldEarlyExit: Boolean = true

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        // Check if this is a Masterlock based on objectType
        val objectType = response.objectType ?: return false
        val brand = SmartLockBrand.fromObjectTypeId(objectType)
        
        if (brand != SmartLockBrand.MASTERLOCK) return false

        // Check for Masterlock context keys
        val hasDeviceId = response.hasContextKey("deviceIdentifier")
        val hasAccessProfile = response.hasContextKey("accessProfile")

        if (!hasDeviceId || !hasAccessProfile) {
            Log.d(TAG, "Missing Masterlock context keys (deviceIdentifier or accessProfile)")
            return false
        }

        // Check if command is unlock (Masterlock doesn't support lock)
        val commandName = command.commandName.lowercase()
        val isUnlockCommand = commandName == "unlock"

        Log.d(TAG, "Masterlock matches check: objectType=$objectType, hasKeys=$hasDeviceId&&$hasAccessProfile, isUnlock=$isUnlockCommand")

        return isUnlockCommand
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        Log.i(TAG, "🔵 Processing Masterlock command: ${command.commandName} for object ${solverObject.id}")

        val adapter = smartLockManager.adapter(SmartLockBrand.MASTERLOCK)

        // First, cache the tokens from the response for use by the adapter
        val tokens = adapter.parseTokens(response, solverObject.id)
        if (tokens == null) {
            Log.e(TAG, "Failed to parse Masterlock tokens from response")
            return MiddlewareResult.Handled("Failed to parse Bluetooth credentials")
        }

        // Execute the Bluetooth command (Masterlock only supports unlock)
        if (command.commandName.lowercase() != "unlock") {
            Log.w(TAG, "Masterlock only supports unlock command, got: ${command.commandName}")
            return MiddlewareResult.Handled("Masterlock doesn't support ${command.commandName}")
        }

        val result = adapter.unlock(solverObject)

        return when {
            result.isSuccess -> {
                Log.i(TAG, "✅ Masterlock unlock succeeded")
                MiddlewareResult.Handled(
                    message = result.getOrNull() ?: "Masterlock unlocked successfully",
                    suppressDebugUI = false
                )
            }
            else -> {
                val error = result.exceptionOrNull()
                Log.e(TAG, "❌ Masterlock unlock failed: ${error?.message}")
                MiddlewareResult.Handled(
                    message = "Bluetooth error: ${error?.message ?: "Unknown error"}",
                    suppressDebugUI = false
                )
            }
        }
    }
}
