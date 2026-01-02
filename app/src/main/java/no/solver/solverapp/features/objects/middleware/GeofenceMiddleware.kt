package no.solver.solverapp.features.objects.middleware

import android.util.Log
import no.solver.solverapp.data.models.Command
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObject

class GeofenceMiddleware : CommandMiddleware {

    companion object {
        private const val TAG = "GeofenceMiddleware"
    }

    override val name: String = "GeofenceMiddleware"
    override val shouldEarlyExit: Boolean = true

    override fun matches(response: ExecuteResponse, command: Command): Boolean {
        return response.hasContextKey("geofenceoverride")
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
        Log.i(TAG, "Geofence override detected. Distance: $distanceString meters")

        // TODO: Implement geofence override UI
        // Show dialog asking user if they want to override the geofence restriction
        // For now, just log and show debug UI

        return MiddlewareResult.Handled(
            message = "Geofence override required: $distanceString meters",
            suppressDebugUI = false
        )
    }
}
