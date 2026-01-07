package no.solver.solverappdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import no.solver.solverappdemo.ui.navigation.AppNavHost
import no.solver.solverappdemo.ui.theme.SolverAppTheme

/**
 * Main activity for the SolverApp.
 * 
 * Note: Vipps OAuth is handled via AppAuth's RedirectUriReceiverActivity,
 * which automatically captures the OAuth redirect and returns the result
 * to the calling activity via startActivityForResult/ActivityResultLauncher.
 * 
 * This means we don't need to manually handle deep links for Vipps OAuth here -
 * the LoginScreen's ActivityResultLauncher handles the callback.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            SolverAppTheme {
                AppNavHost()
            }
        }
    }
}
