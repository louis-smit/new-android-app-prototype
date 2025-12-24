package no.solver.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import dagger.hilt.android.AndroidEntryPoint
import no.solver.app.features.objects.ObjectsScreen
import no.solver.app.ui.theme.SolverAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolverAppTheme {
                SolverAppApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun SolverAppApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.OBJECTS) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestination.OBJECTS -> {
                ObjectsScreen(
                    onObjectClick = { solverObject ->
                        // TODO: Navigate to object detail screen (M2)
                    }
                )
            }
            AppDestination.FIND -> {
                PlaceholderScreen(title = "Find")
            }
            AppDestination.ACCOUNTS -> {
                PlaceholderScreen(title = "Accounts")
            }
            AppDestination.MORE -> {
                PlaceholderScreen(title = "More")
            }
        }
    }
}

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
) {
    OBJECTS("Objects", Icons.Default.Home),
    FIND("Find", Icons.Default.Search),
    ACCOUNTS("Accounts", Icons.Default.AccountCircle),
    MORE("More", Icons.Default.MoreVert),
}

@Composable
private fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
