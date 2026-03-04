package no.solver.solverappdemo.features.auth

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.R
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.ui.components.AppVersionText
import no.solver.solverappdemo.ui.theme.SolverAppTheme

// iOS-matching colors
private val MicrosoftBlue = Color(0xFF007AFF)
private val VippsOrange = Color(0xFFFF5B24)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {},
    onMobileSignIn: () -> Unit = {},
    autoTriggerProvider: String? = null,
    isAddAccountFlow: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val isDebugModeEnabled by viewModel.isDebugModeEnabled.collectAsState()
    
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var debugTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val debugTapTimeout = 2000L
    var hasStartedAddAccountAttempt by remember(isAddAccountFlow) { mutableStateOf(false) }
    var hasObservedAddAccountSigningIn by remember(isAddAccountFlow) { mutableStateOf(false) }
    
    // AppAuth activity result launcher for Vipps OAuth
    val vippsAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleVippsAuthResult(result.data)
    }

    // If a previous add-account attempt was interrupted, recover from stale signing state.
    LaunchedEffect(isAddAccountFlow) {
        if (isAddAccountFlow) {
            viewModel.resetStaleAddAccountState()
        }
    }

    // Auto-trigger provider sign-in for add account mode
    LaunchedEffect(autoTriggerProvider) {
        if (autoTriggerProvider != null && activity != null && uiState !is LoginUiState.SigningIn) {
            if (isAddAccountFlow) {
                hasStartedAddAccountAttempt = true
            }
            when (autoTriggerProvider) {
                "microsoft" -> viewModel.signInWithMicrosoft(activity)
                "vipps" -> viewModel.signInWithVipps(activity) { intent, _ ->
                    vippsAuthLauncher.launch(intent)
                }
            }
        }
    }

    LaunchedEffect(isAuthenticated, isAddAccountFlow) {
        if (!isAddAccountFlow && isAuthenticated) {
            onLoginSuccess()
        }
    }

    // Ignore stale Success from previously completed auth flows in the shared view model.
    // Add-account should only complete after this screen has observed a real SigningIn state.
    LaunchedEffect(uiState, isAddAccountFlow) {
        if (isAddAccountFlow && uiState is LoginUiState.SigningIn) {
            hasObservedAddAccountSigningIn = true
        }
    }

    // In add-account mode, user may already be authenticated with another provider.
    // Only close this screen when an attempt started here reaches SigningIn -> Success.
    LaunchedEffect(uiState, isAddAccountFlow, hasStartedAddAccountAttempt, hasObservedAddAccountSigningIn) {
        if (
            isAddAccountFlow &&
            hasStartedAddAccountAttempt &&
            hasObservedAddAccountSigningIn &&
            uiState is LoginUiState.Success
        ) {
            onLoginSuccess()
        }
    }

    // Ensure interrupted add-account OAuth doesn't leave the shared LoginViewModel stuck.
    DisposableEffect(isAddAccountFlow) {
        onDispose {
            if (isAddAccountFlow && viewModel.hasPendingVippsAuth()) {
                viewModel.cancelPendingVippsAuth()
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as LoginUiState.Error).message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Solver Logo with 7-tap gesture for debug mode
            Image(
                painter = painterResource(id = R.drawable.solver_logo),
                contentDescription = "Solver Logo",
                modifier = Modifier
                    .size(120.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime > debugTapTimeout) {
                                debugTapCount = 0
                            }
                            lastTapTime = currentTime
                            debugTapCount++
                            
                            if (debugTapCount >= 7) {
                                debugTapCount = 0
                                val newState = !isDebugModeEnabled
                                viewModel.toggleDebugMode()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = if (newState) "Debug mode enabled" else "Debug mode disabled",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Welcome to SolverApp",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "Sign in to access your objects",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Sign-in buttons
            SignInButtons(
                uiState = uiState,
                onMicrosoftSignIn = {
                    if (isAddAccountFlow) {
                        hasStartedAddAccountAttempt = true
                    }
                    activity?.let { viewModel.signInWithMicrosoft(it) }
                },
                onVippsSignIn = {
                    if (isAddAccountFlow) {
                        hasStartedAddAccountAttempt = true
                    }
                    activity?.let { act ->
                        viewModel.signInWithVipps(act) { intent, _ ->
                            // Launch Vipps OAuth via AppAuth (handles web fallback automatically)
                            vippsAuthLauncher.launch(intent)
                        }
                    }
                },
                onMobileSignIn = onMobileSignIn
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            // Version info at bottom
            AppVersionText()
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SignInButtons(
    uiState: LoginUiState,
    onMicrosoftSignIn: () -> Unit,
    onVippsSignIn: () -> Unit,
    onMobileSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSigningIn = uiState is LoginUiState.SigningIn
    val signingInProvider = (uiState as? LoginUiState.SigningIn)?.provider

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Microsoft sign-in button
        Button(
            onClick = onMicrosoftSignIn,
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MicrosoftBlue
            )
        ) {
            if (signingInProvider == AuthProvider.MICROSOFT) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Signing in...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_microsoft),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Microsoft",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        // Vipps sign-in button
        Button(
            onClick = onVippsSignIn,
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VippsOrange
            )
        ) {
            if (signingInProvider == AuthProvider.VIPPS) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Signing in...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vipps),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vipps",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        // Mobile sign-in button
        OutlinedButton(
            onClick = onMobileSignIn,
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_phone),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mobile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    SolverAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to SolverApp",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(48.dp))
            SignInButtons(
                uiState = LoginUiState.Idle,
                onMicrosoftSignIn = {},
                onVippsSignIn = {},
                onMobileSignIn = {}
            )
        }
    }
}
