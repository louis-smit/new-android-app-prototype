package no.solver.solverappdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import no.solver.solverappdemo.ui.navigation.AppNavHost
import no.solver.solverappdemo.ui.theme.SolverAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
