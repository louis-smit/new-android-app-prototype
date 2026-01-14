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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import no.solver.solverappdemo.R
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ContextItem
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.OnlineState
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockBrand
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockCapabilities
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockStatus
import no.solver.solverappdemo.ui.components.ObjectIcon
import no.solver.solverappdemo.ui.components.SmartLockCard
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
    val showDetailsSheet by viewModel.showDetailsSheet.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val showInfoSheet by viewModel.showInfoSheet.collectAsState()

    // Smart lock state
    val lockBrand by viewModel.lockBrand.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val lockCapabilities by viewModel.lockCapabilities.collectAsState()
    val cachedOperation by viewModel.cachedOperation.collectAsState()
    val cachedUnlockResult by viewModel.cachedUnlockResult.collectAsState()

    var showOverflowMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bluetooth permission handling for Android 12+
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Execute the pending action now that we have permissions
            pendingBluetoothAction?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Bluetooth permission is required for smart lock control")
            }
        }
        pendingBluetoothAction = null
    }
    
    // Helper function to check permissions and execute action
    fun withBluetoothPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBluetoothScan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val hasBluetoothConnect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasBluetoothScan && hasBluetoothConnect) {
                action()
            } else {
                pendingBluetoothAction = action
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        } else {
            // Permissions are granted at install time on older Android versions
            action()
        }
    }

    // Request Bluetooth permissions proactively when smart lock is detected
    LaunchedEffect(lockBrand) {
        if (lockBrand != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBluetoothScan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val hasBluetoothConnect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasBluetoothScan || !hasBluetoothConnect) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        }
    }

    // App lifecycle observer for smart lock BLE polling
    // Matches iOS NotificationCenter observers for app foreground/background
    DisposableEffect(Unit) {
        val lifecycleOwner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // App entered foreground - restart BLE polling if needed
                    viewModel.onAppForeground()
                }
                Lifecycle.Event.ON_STOP -> {
                    // App entered background - stop BLE polling
                    viewModel.onAppBackground()
                }
                else -> { /* Ignore other events */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        val currentObject = (uiState as? ObjectDetailUiState.Success)?.solverObject
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isFavourite) "Remove from Favourites" else "Add to Favourites") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleFavourite()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
                                        contentDescription = null
                                    )
                                }
                            )
                            if (currentObject?.hasValidLocation == true) {
                                DropdownMenuItem(
                                    text = { Text("Open in Maps") },
                                    onClick = {
                                        showOverflowMenu = false
                                        currentObject.latitude?.let { lat ->
                                            currentObject.longitude?.let { lon ->
                                                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(currentObject.name)})")
                                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                                context.startActivity(intent)
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_location),
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            if (currentObject?.hasSubscription == true) {
                                DropdownMenuItem(
                                    text = { Text("Subscribe") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.handleExplicitSubscription()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_payment),
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            if (currentObject?.information?.hasValidHtmlContent == true) {
                                DropdownMenuItem(
                                    text = { Text("Info") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.showInfoSheet()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_info),
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Details") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.showDetailsSheet()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_info),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
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
                        lockBrand = lockBrand,
                        lockStatus = lockStatus,
                        lockCapabilities = lockCapabilities,
                        cachedOperation = cachedOperation,
                        cachedUnlockResult = cachedUnlockResult,
                        onCommandClick = { command ->
                            viewModel.handleCommand(command)
                        },
                        onOpenInMaps = { lat, lon, name ->
                            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        onUnlock = { withBluetoothPermission { viewModel.executeCachedUnlock("unlock") } },
                        onLock = { withBluetoothPermission { viewModel.executeCachedUnlock("lock") } },
                        onCheckStatus = { withBluetoothPermission { viewModel.checkLockStatus() } },
                        onGetKeys = { withBluetoothPermission { viewModel.fetchAndCacheKeys() } },
                        onClearKeys = { viewModel.clearCachedKeys() }
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

    // Execute Response Bottom Sheet
    if (showExecuteResponse && lastExecuteResponse != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissExecuteResponse() },
            sheetState = sheetState
        ) {
            ExecuteResponseSheetContent(
                response = lastExecuteResponse!!,
                middlewareMessage = middlewareMessage
            )
        }
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

    // Details Bottom Sheet
    if (showDetailsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissDetailsSheet() },
            sheetState = sheetState
        ) {
            ObjectDetailsSheetContent(
                solverObject = viewModel.solverObject
            )
        }
    }

    // Info Bottom Sheet (HTML Content)
    if (showInfoSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissInfoSheet() },
            sheetState = sheetState
        ) {
            ObjectInfoSheetContent(
                information = viewModel.solverObject?.information
            )
        }
    }
}

@Composable
private fun ObjectDetailContent(
    solverObject: SolverObject,
    baseUrl: String,
    executingCommandId: String?,
    lockBrand: SmartLockBrand?,
    lockStatus: SmartLockStatus,
    lockCapabilities: SmartLockCapabilities?,
    cachedOperation: CachedOperation,
    cachedUnlockResult: String?,
    onCommandClick: (Command) -> Unit,
    onOpenInMaps: (Double, Double, String) -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onCheckStatus: () -> Unit,
    onGetKeys: () -> Unit,
    onClearKeys: () -> Unit,
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

        // Smart lock card (shown below header for lock objects)
        if (lockBrand != null && lockCapabilities != null) {
            SmartLockCard(
                lockBrand = lockBrand,
                lockStatus = lockStatus,
                capabilities = lockCapabilities,
                operation = cachedOperation,
                debugResult = cachedUnlockResult,
                onUnlock = onUnlock,
                onLock = onLock,
                onCheckStatus = onCheckStatus,
                onGetKeys = onGetKeys,
                onClearKeys = onClearKeys
            )
        }

        ObjectInfoCard(
            solverObject = solverObject,
            onOpenInMaps = onOpenInMaps
        )

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
    onOpenInMaps: (Double, Double, String) -> Unit,
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

            if (solverObject.hasValidLocation) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LocationRow(
                    label = "Location",
                    latitude = solverObject.latitude!!,
                    longitude = solverObject.longitude!!,
                    objectName = solverObject.name,
                    onOpenInMaps = onOpenInMaps
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
private fun LocationRow(
    label: String,
    latitude: Double,
    longitude: Double,
    objectName: String,
    onOpenInMaps: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { onOpenInMaps(latitude, longitude, objectName) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(text = "Open in Maps")
        }
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
private fun ExecuteResponseSheetContent(
    response: ExecuteResponse,
    middlewareMessage: String?,
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title
        Text(
            text = "Command Result",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Middleware debug banner (if middleware triggered)
        middlewareMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Middleware Triggered",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Success/Failure indicator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                Text(
                    text = if (response.success) "Success" else "Failed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Response Details Section
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = "RESPONSE DETAILS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    response.objectName?.let { name ->
                        DetailsRow(label = "Object", value = name)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    response.objectId?.let { id ->
                        DetailsRow(label = "Object ID", value = id.toString())
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    response.time?.let { time ->
                        DetailsRow(label = "Time", value = time)
                    }
                }
            }
        }

        // Context Section (if available)
        response.context?.takeIf { it.isNotEmpty() }?.let { context ->
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "CONTEXT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        context.forEachIndexed { index, item ->
                            DetailsRow(label = item.label, value = item.value)
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
            ),
            onOpenInMaps = { _, _, _ -> }
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
private fun ExecuteResponseSheetContentPreview() {
    SolverAppTheme {
        ExecuteResponseSheetContent(
            response = ExecuteResponse(
                success = true,
                objectId = 1,
                objectName = "Meeting Room A",
                time = "2025-12-25T12:00:00Z",
                context = listOf(
                    ContextItem(key = "status", label = "Status", value = "Unlocked")
                )
            ),
            middlewareMessage = "[StatusMiddleware] Status displayed"
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

@Composable
private fun ObjectDetailsSheetContent(
    solverObject: SolverObject?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Object Details",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (solverObject == null) {
            Text(
                text = "No object data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                DetailsRow(label = "ID", value = solverObject.id.toString())
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                DetailsRow(label = "Name", value = solverObject.name)
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                DetailsRow(label = "Object Type ID", value = solverObject.objectTypeId.toString())
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                DetailsRow(label = "Status", value = solverObject.status.ifEmpty { "—" })
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                DetailsRow(label = "Online", value = if (solverObject.online) "Yes" else "No")
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                DetailsRow(label = "Active", value = if (solverObject.active) "Yes" else "No")
                
                solverObject.tenantName?.let { tenant ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    DetailsRow(label = "Tenant", value = tenant)
                }
                
                if (solverObject.hasValidLocation) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    DetailsRow(
                        label = "Location",
                        value = "${solverObject.latitude}, ${solverObject.longitude}"
                    )
                }
                
                solverObject.hasSubscription?.let { hasSub ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    DetailsRow(label = "Has Subscription", value = if (hasSub) "Yes" else "No")
                }
            }
        }
    }
}

@Composable
private fun DetailsRow(
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

@Preview(showBackground = true)
@Composable
private fun ObjectDetailsSheetContentPreview() {
    SolverAppTheme {
        ObjectDetailsSheetContent(
            solverObject = SolverObject(
                id = 123,
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

@Composable
private fun ObjectInfoSheetContent(
    information: no.solver.solverappdemo.data.models.ObjectInformation?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val htmlContent = information?.htmlContent
        if (htmlContent.isNullOrBlank()) {
            Text(
                text = "No information available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            HtmlContent(
                html = htmlContent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier
) {
    android.view.ViewGroup.LayoutParams.MATCH_PARENT.let { matchParent ->
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        matchParent,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    settings.apply {
                        @Suppress("SetJavaScriptEnabled")
                        javaScriptEnabled = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            },
            modifier = modifier
        )
    }
}
