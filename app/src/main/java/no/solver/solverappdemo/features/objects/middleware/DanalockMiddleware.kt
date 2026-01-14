package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockBrand
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockManager
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import javax.inject.Inject

private const val TAG = "DanalockMiddleware"

/**
 * Middleware for handling Danalock Bluetooth lock commands.
 * 
 * Triggers when:
 * - Object type is 4 (Danalock)
 * - Response contains Danalock context keys (serial_number, login_token)
 * - Command is lock or unlock
 * 
 * Behavior:
 * - Intercepts the server response and executes the actual Bluetooth command
 * - Reports success/failure to the user
 * - Early exits to prevent other middleware from running
 */
class DanalockMiddleware @Inject constructor(
    private val smartLockManager: SmartLockManager
) : CommandMiddleware {

    override val name: String = "DanalockMiddleware"
    override val shouldEarlyExit: Boolean = true

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        // Check if this is a Danalock based on objectType
        val objectType = response.objectType ?: return false
        val brand = SmartLockBrand.fromObjectTypeId(objectType)
        
        if (brand != SmartLockBrand.DANALOCK) return false

        // Check for Danalock context keys
        val hasSerialNumber = response.hasContextKey("serial_number")
        val hasLoginToken = response.hasContextKey("login_token")

        if (!hasSerialNumber || !hasLoginToken) {
            Log.d(TAG, "Missing Danalock context keys (serial_number or login_token)")
            return false
        }

        // Check if command is a lock operation
        val commandName = command.commandName.lowercase()
        val isLockCommand = commandName == "lock" || commandName == "unlock"

        Log.d(TAG, "Danalock matches check: objectType=$objectType, hasKeys=$hasSerialNumber&&$hasLoginToken, isLockCmd=$isLockCommand")

        return isLockCommand
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        Log.i(TAG, "🔵 Processing Danalock command: ${command.commandName} for object ${solverObject.id}")

        val adapter = smartLockManager.adapter(SmartLockBrand.DANALOCK)

        // First, cache the tokens from the response for use by the adapter
        val tokens = adapter.parseTokens(response, solverObject.id)
        if (tokens == null) {
            Log.e(TAG, "Failed to parse Danalock tokens from response")
            return MiddlewareResult.Handled("Failed to parse Bluetooth credentials")
        }

        // Execute the Bluetooth command
        val result = when (command.commandName.lowercase()) {
            "unlock" -> adapter.unlock(solverObject)
            "lock" -> adapter.lock(solverObject)
            else -> {
                Log.w(TAG, "Unsupported Danalock command: ${command.commandName}")
                return MiddlewareResult.Handled("Unsupported command: ${command.commandName}")
            }
        }

        return when {
            result.isSuccess -> {
                Log.i(TAG, "✅ Danalock ${command.commandName} succeeded")
                MiddlewareResult.Handled(
                    message = result.getOrNull() ?: "Bluetooth command executed successfully",
                    suppressDebugUI = false
                )
            }
            else -> {
                val error = result.exceptionOrNull()
                Log.e(TAG, "❌ Danalock ${command.commandName} failed: ${error?.message}")
                MiddlewareResult.Handled(
                    message = "Bluetooth error: ${error?.message ?: "Unknown error"}",
                    suppressDebugUI = false
                )
            }
        }
    }
}
