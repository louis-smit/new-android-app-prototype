package no.solver.solverappdemo.features.objects.middleware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context for a geofence override request.
 */
data class GeofenceContext(
    val distance: String,
    val command: Command,
    val objectId: Int,
    val objectName: String
) {
    val title: String = "Location Restriction"
    
    val message: String
        get() {
            val distanceDisplay = if (distance.isNotEmpty()) "${distance}m" else "unknown distance"
            return "You are $distanceDisplay away from the allowed zone for \"$objectName\".\n\nWould you like to override this restriction?"
        }
}

/**
 * Geofence middleware that handles geofenceoverride responses.
 * When triggered, shows a confirmation dialog to the user.
 * If confirmed, re-executes the command with "override" parameter.
 */
@Singleton
class GeofenceMiddleware @Inject constructor(
    private val objectsRepository: ObjectsRepository
) : CommandMiddleware {

    companion object {
        private const val TAG = "GeofenceMiddleware"
    }

    override val name: String = "GeofenceMiddleware"
    override val shouldEarlyExit: Boolean = true

    // UI State
    private val _showGeofenceDialog = MutableStateFlow(false)
    val showGeofenceDialog: StateFlow<Boolean> = _showGeofenceDialog.asStateFlow()

    private val _geofenceContext = MutableStateFlow<GeofenceContext?>(null)
    val geofenceContext: StateFlow<GeofenceContext?> = _geofenceContext.asStateFlow()

    private val _isOverriding = MutableStateFlow(false)
    val isOverriding: StateFlow<Boolean> = _isOverriding.asStateFlow()

    // Alert state for success/error
    private val _showSuccessAlert = MutableStateFlow(false)
    val showSuccessAlert: StateFlow<Boolean> = _showSuccessAlert.asStateFlow()

    private val _showErrorAlert = MutableStateFlow(false)
    val showErrorAlert: StateFlow<Boolean> = _showErrorAlert.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Callback for re-executing command via ViewModel
    private var onReExecuteCommand: (suspend (Command, String?) -> Unit)? = null

    /**
     * Set the callback for re-executing commands.
     * This should be called by ObjectDetailViewModel to provide the execution capability.
     */
    fun setReExecuteCallback(callback: suspend (Command, String?) -> Unit) {
        onReExecuteCommand = callback
    }

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        return !response.success && response.hasContextKey("geofenceoverride")
    }

    override suspend fun process(
        response: ExecuteResponse,
        command: Command,
        solverObject: SolverObject
    ): MiddlewareResult {
        if (!matches(response, command)) {
            return MiddlewareResult.NotApplicable
        }

        val distanceString = response.findValueInContext("geofenceoverride") ?: ""
        Log.i(TAG, "🟡 [GeofenceMiddleware] TRIGGERED")
        Log.i(TAG, "   → Command: ${command.commandName}")
        Log.i(TAG, "   → Object: ${solverObject.name}")
        Log.i(TAG, "   → Distance: ${distanceString}m")

        // Create geofence context
        val context = GeofenceContext(
            distance = distanceString,
            command = command,
            objectId = solverObject.id,
            objectName = solverObject.name
        )

        // Store context and show dialog
        _geofenceContext.value = context
        _showGeofenceDialog.value = true

        return MiddlewareResult.Handled(
            message = "Geofence override required: ${distanceString}m away",
            suppressDebugUI = true
        )
    }

    /**
     * Handle user confirming the geofence override.
     * Re-executes the command with "override" parameter.
     */
    suspend fun confirmOverride() {
        val context = _geofenceContext.value ?: run {
            Log.e(TAG, "Geofence context missing when confirming override")
            return
        }

        Log.i(TAG, "User confirmed geofence override for command: ${context.command.commandName}")
        _showGeofenceDialog.value = false
        _isOverriding.value = true

        try {
            // Re-execute command with "override" parameter
            val callback = onReExecuteCommand
            if (callback != null) {
                callback(context.command, "override")
                Log.i(TAG, "✅ Command re-executed with override parameter")
            } else {
                Log.e(TAG, "No re-execute callback set")
                showError("Unable to override - please try again")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Override execution failed: ${e.message}")
            showError(e.message ?: "Override failed")
        } finally {
            _isOverriding.value = false
            _geofenceContext.value = null
        }
    }

    /**
     * Handle user cancelling the geofence override.
     */
    fun cancelOverride() {
        Log.i(TAG, "User cancelled geofence override")
        _showGeofenceDialog.value = false
        _geofenceContext.value = null
    }

    fun dismissSuccessAlert() {
        _showSuccessAlert.value = false
    }

    fun dismissErrorAlert() {
        _showErrorAlert.value = false
        _errorMessage.value = null
    }

    private fun showError(message: String) {
        _errorMessage.value = message
        _showErrorAlert.value = true
    }
}
