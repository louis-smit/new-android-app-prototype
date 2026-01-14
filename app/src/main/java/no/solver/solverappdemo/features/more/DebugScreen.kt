package no.solver.solverappdemo.features.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.core.config.APIEnvironment
import no.solver.solverappdemo.core.config.APIMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Update cache size when screen appears
    LaunchedEffect(Unit) {
        viewModel.updateCacheSize()
    }

    // Clear Cache Dialog
    if (uiState.showClearCacheDialog) {
        ClearCacheDialog(
            cacheSize = uiState.cacheSize,
            onConfirm = { viewModel.clearCache() },
            onDismiss = { viewModel.dismissClearCacheDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        // Debug Mode Section
        DebugSection(title = "DEBUG") {
            DebugToggleRow(
                title = "Set debug mode",
                isOn = uiState.isDebugModeEnabled,
                onToggle = { viewModel.setDebugModeEnabled(it) },
                label = if (uiState.isDebugModeEnabled) "Debug" else "Off"
            )

            DebugDivider()

            DebugToggleRow(
                title = "Show Middleware Debug UI",
                description = "Show ExecuteResponseSheet even when middleware has custom UI",
                isOn = uiState.isMiddlewareDebugUIEnabled,
                onToggle = { viewModel.setMiddlewareDebugUIEnabled(it) },
                label = if (uiState.isMiddlewareDebugUIEnabled) "ON" else "OFF"
            )
        }

        // General Section
        DebugSection(title = "GENERAL") {
            DebugButtonRow(
                title = "Clear Cache",
                value = "${uiState.cacheSize} KB",
                onClick = { viewModel.showClearCacheDialog() }
            )
        }

        // API Section
        DebugSection(
            title = "API",
            subtitle = uiState.currentAPIBaseURL
        ) {
            DebugToggleRow(
                title = "Set API",
                description = "Toggle API: Staging/Production",
                isOn = uiState.apiMode == APIMode.PRODUCTION,
                onToggle = { isProduction ->
                    viewModel.setAPIMode(if (isProduction) APIMode.PRODUCTION else APIMode.STAGING)
                },
                label = uiState.apiMode.displayName
            )

            DebugDivider()

            DebugToggleRow(
                title = "Zohm API",
                description = "Swap to Zohm API",
                isOn = uiState.apiEnvironment == APIEnvironment.SOLVER,
                onToggle = { isSolver ->
                    viewModel.setAPIEnvironment(if (isSolver) APIEnvironment.SOLVER else APIEnvironment.ZOHM)
                },
                label = uiState.apiEnvironment.displayName
            )
        }

        // Current Session Info Section
        uiState.currentSession?.let { session ->
            DebugSection(title = "CURRENT SESSION") {
                DebugInfoRow(
                    label = "User",
                    value = session.displayName
                )

                DebugDivider()

                DebugInfoRow(
                    label = "Provider",
                    value = session.provider.displayName
                )

                DebugDivider()

                session.email?.let { email ->
                    DebugInfoRow(
                        label = "Email",
                        value = email
                    )
                    DebugDivider()
                }

                DebugInfoRow(
                    label = "User ID",
                    value = session.tokens.userId ?: session.id.take(8)
                )

                DebugDivider()

                DebugInfoRow(
                    label = "Token Expires",
                    value = "${session.tokens.secondsUntilExpiry / 60}m"
                )
            }
        }

        // Environment Info Section
        DebugSection(title = "ENVIRONMENT INFO") {
            DebugInfoRow(
                label = "App Version",
                value = uiState.appVersion
            )

            DebugDivider()

            DebugInfoRow(
                label = "Build",
                value = uiState.buildNumber
            )

            DebugDivider()

            DebugInfoRow(
                label = "Package",
                value = uiState.packageName
            )

            DebugDivider()

            DebugInfoRow(
                label = "Build Type",
                value = uiState.buildType
            )

            DebugDivider()

            DebugInfoRow(
                label = "Build Environment",
                value = uiState.buildEnvironment
            )
        }

            // Bottom spacing
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        // Section Header
        Column(modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Section Content
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DebugToggleRow(
    title: String,
    description: String? = null,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = isOn,
            onCheckedChange = onToggle
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun DebugButtonRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}

@Composable
private fun DebugDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun ClearCacheDialog(
    cacheSize: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Clear Cache",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This will clear $cacheSize KB of cached data. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Cache")
                    }
                }
            }
        }
    }
}
