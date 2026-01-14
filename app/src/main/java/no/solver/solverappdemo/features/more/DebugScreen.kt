package no.solver.solverappdemo.features.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.BuildConfig
import no.solver.solverappdemo.core.config.AppEnvironment

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = hiltViewModel()
) {
    val currentSession by viewModel.currentSession.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Session Info
        currentSession?.let { session ->
            DebugSection(title = "Current Session") {
                DebugInfoRow(label = "User", value = session.displayName)
                DebugInfoRow(label = "Provider", value = session.provider.name)
                session.email?.let { email ->
                    DebugInfoRow(label = "Email", value = email)
                }
                DebugInfoRow(
                    label = "Token Expires",
                    value = "${session.tokens.secondsUntilExpiry / 60}m"
                )
            }
        }

        // Environment Info
        DebugSection(title = "Environment Info") {
            DebugInfoRow(label = "App Version", value = BuildConfig.VERSION_NAME)
            DebugInfoRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
            DebugInfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
            DebugInfoRow(
                label = "Build Type",
                value = if (BuildConfig.DEBUG) "Debug" else "Release"
            )
            DebugInfoRow(
                label = "Environment",
                value = AppEnvironment.current.name
            )
        }

        // API Info
        DebugSection(title = "API Configuration") {
            currentSession?.let { session ->
                DebugInfoRow(label = "Auth Environment", value = session.environment.displayName)
            }
            DebugInfoRow(
                label = "Mode",
                value = if (BuildConfig.IS_PRODUCTION) "Production" else "Staging"
            )
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
