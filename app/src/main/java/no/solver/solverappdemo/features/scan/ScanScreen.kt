package no.solver.solverappdemo.features.scan

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.solver.solverappdemo.core.deeplink.DeepLinkViewModel
import java.util.concurrent.Executors

private const val TAG = "ScanScreen"

/**
 * QR Scanner tab screen.
 * 
 * Captures QR codes via CameraX + ML Kit and forwards valid Solver URLs
 * to DeepLinkViewModel for command execution. Matches iOS ScanView behavior:
 * - Pauses scanner after detecting a valid QR code
 * - Shows "Scan Again" overlay after the entire flow completes
 * - Delegates ALL execution logic to DeepLinkViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    deepLinkViewModel: DeepLinkViewModel,
    isActive: Boolean = true,
    onScanSuccessNavigateHome: () -> Unit = {},
    scanViewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by scanViewModel.uiState.collectAsState()
    val isBusy by deepLinkViewModel.isBusy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isAppActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val lifecycleOwner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isAppActive = true
                Lifecycle.Event.ON_STOP -> isAppActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scanViewModel.onPermissionResult(granted)
    }

    // Show error as snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            scanViewModel.dismissError()
        }
    }

    // Re-check permission when screen becomes visible
    LaunchedEffect(Unit) {
        scanViewModel.checkPermission()
    }

    // Show "Scan Again" when scanner is paused AND deep link flow is fully done
    val showScanAgain = uiState.scannerState == ScannerState.PAUSED && !isBusy
    val isCameraSessionActive =
        isActive &&
            isAppActive &&
            uiState.hasCameraPermission &&
            uiState.scannerState == ScannerState.SCANNING

    // Successful scan flow completed: reset scanner and return user to Objects tab.
    LaunchedEffect(showScanAgain) {
        if (showScanAgain) {
            scanViewModel.scanAgain()
            onScanSuccessNavigateHome()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scan") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.hasCameraPermission -> {
                    // Camera preview + viewfinder
                    CameraPreviewContent(
                        scanViewModel = scanViewModel,
                        deepLinkViewModel = deepLinkViewModel,
                        isSessionActive = isCameraSessionActive
                    )

                    // Viewfinder overlay (only while scanning)
                    AnimatedVisibility(
                        visible = isCameraSessionActive,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ViewfinderOverlay()
                    }

                    // Instruction text (only while scanning)
                    AnimatedVisibility(
                        visible = isCameraSessionActive,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Text(
                            text = "Point camera at QR code",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier
                                .padding(bottom = 32.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }

                }

                else -> {
                    // Permission not granted
                    PermissionContent(
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = { scanViewModel.openSettings(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    scanViewModel: ScanViewModel,
    deepLinkViewModel: DeepLinkViewModel,
    isSessionActive: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val preview = remember { Preview.Builder().build() }
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    DisposableEffect(imageAnalyzer) {
        imageAnalyzer.setAnalyzer(cameraExecutor, QRCodeAnalyzer { rawValue ->
            val uri = scanViewModel.onQrCodeScanned(rawValue)
            if (uri != null) {
                Log.i(TAG, "📸 Valid QR code, forwarding to DeepLinkViewModel: $uri")
                deepLinkViewModel.handle(uri)
            }
        })

        onDispose {
            imageAnalyzer.clearAnalyzer()
        }
    }

    LaunchedEffect(previewView) {
        val currentPreviewView = previewView ?: return@LaunchedEffect
        if (cameraProvider != null) return@LaunchedEffect

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    cameraProvider = cameraProviderFuture.get()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to obtain camera provider", error)
                }
            },
            ContextCompat.getMainExecutor(currentPreviewView.context)
        )
    }

    LaunchedEffect(cameraProvider, previewView, lifecycleOwner, isSessionActive) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val currentPreviewView = previewView ?: return@LaunchedEffect

        runCatching {
            provider.unbindAll()

            if (isSessionActive) {
                preview.setSurfaceProvider(currentPreviewView.surfaceProvider)
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } else {
                Log.d(TAG, "Camera session inactive, camera unbound")
            }
        }.onFailure { error ->
            Log.e(TAG, "Camera bind/unbind failed", error)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            imageAnalyzer.clearAnalyzer()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                previewView = view
            }
        },
        update = { view ->
            previewView = view
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * "Scan Again" overlay shown after QR code processing completes.
 * Matches iOS: green checkmark, "QR Code Processed" text, "Scan Again" button.
 */
@Composable
private fun ScanAgainOverlay(onScanAgain: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(56.dp)
            )

            Text(
                text = "QR Code Processed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Button(onClick = onScanAgain) {
                Text("Scan Again")
            }
        }
    }
}

/**
 * Permission request/denied content.
 * Matches iOS: camera icon, title, description, action button.
 */
@Composable
private fun PermissionContent(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SolverApp needs camera access to scan QR codes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Settings")
        }
    }
}
