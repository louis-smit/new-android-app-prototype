package no.solver.solverappdemo.features.objects.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.R
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ContextItem
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.OnlineState
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.ui.components.ObjectIcon
import no.solver.solverappdemo.ui.theme.SolverAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailScreen(
    viewModel: ObjectDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()
    val executingCommandId by viewModel.executingCommandId.collectAsState()
    val showExecuteResponse by viewModel.showExecuteResponse.collectAsState()
    val lastExecuteResponse by viewModel.lastExecuteResponse.collectAsState()
    val middlewareMessage by viewModel.middlewareMessage.collectAsState()
    val commandError by viewModel.commandError.collectAsState()
    val showInputDialog by viewModel.showInputDialog.collectAsState()
    val pendingCommand by viewModel.pendingCommand.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()
    val showStatusSheet by viewModel.showStatusSheet.collectAsState()
    val statusSheetResponse by viewModel.statusSheetResponse.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error as snackbar
    LaunchedEffect(commandError) {
        commandError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissCommandError()
        }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                        executingCommandId = executingCommandId,
                        onCommandClick = { command ->
                            viewModel.handleCommand(command)
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

    // Input Dialog
    if (showInputDialog && pendingCommand != null) {
        CommandInputDialog(
            command = pendingCommand!!,
            input = commandInput,
            onInputChange = { viewModel.updateCommandInput(it) },
            onDismiss = { viewModel.dismissInputDialog() },
            onConfirm = { viewModel.executeCommandWithInput() }
        )
    }

    // Execute Response Dialog
    if (showExecuteResponse && lastExecuteResponse != null) {
        ExecuteResponseDialog(
            response = lastExecuteResponse!!,
            middlewareMessage = middlewareMessage,
            onDismiss = { viewModel.dismissExecuteResponse() }
        )
    }

    // Status Bottom Sheet
    if (showStatusSheet && statusSheetResponse != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissStatusSheet() },
            sheetState = sheetState
        ) {
            StatusSheetContent(
                response = statusSheetResponse!!,
                solverObject = viewModel.solverObject
            )
        }
    }
}

@Composable
private fun ObjectDetailContent(
    solverObject: SolverObject,
    baseUrl: String,
    executingCommandId: String?,
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
            executingCommandId = executingCommandId,
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
    executingCommandId: String?,
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
                            isExecuting = executingCommandId == command.commandName,
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
    isExecuting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isExecuting,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                command.iconName?.let { iconName ->
                    val iconRes = getCommandIconResource(iconName)
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = command.displayName,
                style = MaterialTheme.typography.labelLarge
            )
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
private fun CommandInputDialog(
    command: Command,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command Input") },
        text = {
            Column {
                Text("Enter input for '${command.displayName}'")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Enter value") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Execute")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExecuteResponseDialog(
    response: ExecuteResponse,
    middlewareMessage: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (response.success) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (response.success) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Text(if (response.success) "Success" else "Failed")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                middlewareMessage?.let { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Middleware",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                response.objectName?.let { name ->
                    DetailRow(label = "Object", value = name)
                }

                response.time?.let { time ->
                    DetailRow(label = "Time", value = time)
                }

                response.context?.takeIf { it.isNotEmpty() }?.let { context ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Context",
                        style = MaterialTheme.typography.labelMedium
                    )
                    context.forEach { item ->
                        DetailRow(label = item.label, value = item.value)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun StatusSheetContent(
    response: ExecuteResponse,
    solverObject: SolverObject?,
    modifier: Modifier = Modifier
) {
    val successColor = Color(0xFF4CAF50)
    val errorColor = Color(0xFFF44336)
    val statusColor = if (response.success) successColor else errorColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status Card - prominent display of success/failure
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = statusColor.copy(alpha = 0.1f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Icon in circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = statusColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (response.success) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (response.success) "Success" else "Failed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    response.time?.let { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Object Details Section
        solverObject?.let { obj ->
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "OBJECT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        StatusDetailRow(label = "Name", value = obj.name)
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        StatusDetailRow(label = "Status", value = obj.status.ifEmpty { "—" })
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        StatusDetailRow(
                            label = "Online",
                            value = if (obj.online) "Yes" else "No"
                        )
                    }
                }
            }
        }

        // Context Details Section (if present)
        response.context?.takeIf { it.isNotEmpty() }?.let { context ->
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "DETAILS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        context.forEachIndexed { index, item ->
                            StatusDetailRow(label = item.label, value = item.value)
                            if (index < context.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            executingCommandId = null,
            onCommandClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CommandsCardExecutingPreview() {
    SolverAppTheme {
        CommandsCard(
            commands = listOf(
                Command(native = "unlock", display = "Unlock"),
                Command(native = "lock", display = "Lock"),
                Command(native = "status", display = "Status")
            ),
            executingCommandId = "unlock",
            onCommandClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExecuteResponseDialogPreview() {
    SolverAppTheme {
        ExecuteResponseDialog(
            response = ExecuteResponse(
                success = true,
                objectId = 1,
                objectName = "Meeting Room A",
                time = "2025-12-25T12:00:00Z",
                context = listOf(
                    ContextItem(key = "status", label = "Status", value = "Unlocked")
                )
            ),
            middlewareMessage = "[StatusMiddleware] Status displayed",
            onDismiss = {}
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
