package no.solver.solverapp.features.objects.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverapp.R
import no.solver.solverapp.data.models.Command
import no.solver.solverapp.data.models.OnlineState
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.ui.components.ObjectIcon
import no.solver.solverapp.ui.theme.SolverAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailScreen(
    viewModel: ObjectDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? ObjectDetailUiState.Success)?.solverObject?.name ?: "Object"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ObjectDetailUiState.Loading -> {
                    LoadingContent()
                }
                is ObjectDetailUiState.Success -> {
                    ObjectDetailContent(
                        solverObject = state.solverObject,
                        baseUrl = apiBaseUrl,
                        onCommandClick = { command ->
                            // TODO: Handle command execution in next step
                        }
                    )
                }
                is ObjectDetailUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.retry() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectDetailContent(
    solverObject: SolverObject,
    baseUrl: String,
    onCommandClick: (Command) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ObjectHeaderCard(
            solverObject = solverObject,
            baseUrl = baseUrl
        )

        ObjectInfoCard(solverObject = solverObject)

        CommandsCard(
            commands = solverObject.getCommands(),
            onCommandClick = onCommandClick
        )
    }
}

@Composable
private fun ObjectHeaderCard(
    solverObject: SolverObject,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ObjectIcon(
                objectTypeId = solverObject.objectTypeId,
                baseUrl = baseUrl,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = solverObject.name,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnlineStatusBadge(onlineState = solverObject.onlineState)
                    StatusBadge(status = solverObject.status)
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusBadge(
    onlineState: OnlineState,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (onlineState) {
        OnlineState.ONLINE -> Color(0xFF4CAF50) to "Online"
        OnlineState.OFFLINE -> Color(0xFFF44336) to "Offline"
        OnlineState.UNKNOWN -> Color.Gray to "Unknown"
        OnlineState.NOT_APPLICABLE -> return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_globe),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = status,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun ObjectInfoCard(
    solverObject: SolverObject,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            DetailRow(label = "Name", value = solverObject.name)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Status", value = solverObject.status)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Online", value = if (solverObject.online) "Yes" else "No")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Active", value = if (solverObject.active) "Yes" else "No")

            solverObject.tenantName?.let { tenant ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow(label = "Tenant", value = tenant)
            }

            if (solverObject.latitude != null && solverObject.longitude != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow(
                    label = "Location",
                    value = String.format("%.4f, %.4f", solverObject.latitude, solverObject.longitude)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CommandsCard(
    commands: List<Command>,
    onCommandClick: (Command) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Commands",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (commands.isEmpty()) {
                Text(
                    text = "No commands available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    commands.forEach { command ->
                        CommandButton(
                            command = command,
                            onClick = { onCommandClick(command) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandButton(
    command: Command,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            command.iconName?.let { iconName ->
                val iconRes = getCommandIconResource(iconName)
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(text = command.displayName)
        }
    }
}

private fun getCommandIconResource(iconName: String): Int? {
    return when (iconName) {
        "lock_open" -> R.drawable.ic_lock_open
        "lock" -> R.drawable.ic_lock
        "info" -> R.drawable.ic_info
        "refresh" -> R.drawable.ic_refresh
        else -> null
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading object...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Error Loading Object",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ObjectHeaderCardPreview() {
    SolverAppTheme {
        ObjectHeaderCard(
            solverObject = SolverObject(
                id = 1,
                name = "Meeting Room A",
                objectTypeId = 1,
                status = "Available",
                online = true,
                active = true,
                state = 1
            ),
            baseUrl = "https://api365-demo.solver.no"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ObjectInfoCardPreview() {
    SolverAppTheme {
        ObjectInfoCard(
            solverObject = SolverObject(
                id = 1,
                name = "Meeting Room A",
                objectTypeId = 1,
                status = "Available",
                latitude = 59.9139,
                longitude = 10.7522,
                online = true,
                active = true,
                tenantName = "Solver AS"
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CommandsCardPreview() {
    SolverAppTheme {
        CommandsCard(
            commands = listOf(
                Command(native = "unlock", display = "Unlock"),
                Command(native = "lock", display = "Lock"),
                Command(native = "status", display = "Status")
            ),
            onCommandClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    SolverAppTheme {
        ErrorContent(
            message = "Network connection failed. Please check your internet connection.",
            onRetry = {}
        )
    }
}
