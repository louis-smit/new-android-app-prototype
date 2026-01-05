package no.solver.solverappdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import no.solver.solverappdemo.features.auth.services.VippsAuthService
import no.solver.solverappdemo.ui.navigation.AppNavHost
import no.solver.solverappdemo.ui.theme.SolverAppTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var vippsAuthService: VippsAuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle initial deep link intent
        handleDeepLink(intent)
        
        setContent {
            SolverAppTheme {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // If we resume without a Vipps callback (user pressed back from Custom Tab),
        // cancel any pending Vipps auth to reset the UI state
        if (intent?.data?.let { isVippsCallback(it) } != true) {
            vippsAuthService.cancelPendingAuth()
        }
    }

    private fun isVippsCallback(uri: android.net.Uri): Boolean {
        return uri.scheme == "solverapp" && uri.host == "oauth" && uri.path == "/vipps"
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        
        Log.d(TAG, "Received deep link: $uri")

        // Handle Vipps OAuth callback: solverapp://oauth/vipps?code=...
        if (uri.scheme == "solverapp" && uri.host == "oauth" && uri.path == "/vipps") {
            Log.d(TAG, "Processing Vipps OAuth callback")
            vippsAuthService.handleAuthCallback(uri)
        }
    }
}
