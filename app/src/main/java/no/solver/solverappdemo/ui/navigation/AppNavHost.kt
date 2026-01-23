package no.solver.solverappdemo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import no.solver.solverappdemo.core.deeplink.DeepLinkViewModel
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.LoginScreen
import no.solver.solverappdemo.features.auth.LoginViewModel
import no.solver.solverappdemo.features.auth.MobileLoginScreen
import no.solver.solverappdemo.features.accounts.AccountsScreen
import no.solver.solverappdemo.features.find.FindScreen
import no.solver.solverappdemo.features.more.DanalockDemoScreen
import no.solver.solverappdemo.features.more.DebugScreen
import no.solver.solverappdemo.features.more.LogsScreen
import no.solver.solverappdemo.features.more.MasterlockDemoScreen
import no.solver.solverappdemo.features.more.MoreItem
import no.solver.solverappdemo.features.more.MoreScreen
import no.solver.solverappdemo.features.more.PaymentsScreen
import no.solver.solverappdemo.features.more.VisitScreen
import no.solver.solverappdemo.features.objects.ObjectsScreen
import no.solver.solverappdemo.features.objects.detail.ObjectDetailScreen
import no.solver.solverappdemo.ui.components.PaymentMethodSheetContent
import no.solver.solverappdemo.ui.components.StatusBottomSheetContent
import no.solver.solverappdemo.ui.components.SubscriptionSheetContent

private const val SPLASH_DELAY_MS = 500L
private const val TRANSITION_DURATION_MS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    loginViewModel: LoginViewModel = hiltViewModel(),
    deepLinkViewModel: DeepLinkViewModel = hiltViewModel()
) {
    val isAuthenticated by loginViewModel.isAuthenticated.collectAsState()
    var isInitialized by rememberSaveable { mutableStateOf(false) }
    
    // Deep link state
    val deepLinkState by deepLinkViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MS)
        isInitialized = true
    }

    // Reactive navigation based on auth state (like iOS)
    // When isAuthenticated changes AFTER initialization, navigate accordingly
    LaunchedEffect(isAuthenticated, isInitialized) {
        if (isInitialized) {
            val currentRoute = navController.currentDestination?.route
            val isOnAuthScreen = currentRoute?.contains("Login") == true || 
                                 currentRoute?.contains("MobileLogin") == true ||
                                 currentRoute?.contains("Splash") == true
            
            if (!isAuthenticated && !isOnAuthScreen) {
                // User signed out while on a protected screen → go to login
                navController.navigate(NavRoute.Login) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
    
    // Show error snackbar for deep link errors
    LaunchedEffect(deepLinkState.error) {
        deepLinkState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            deepLinkViewModel.dismissError()
        }
    }

    // Root Box to overlay deep link UI on top of navigation
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
        navController = navController,
        startDestination = NavRoute.Splash,
        enterTransition = {
            fadeIn(animationSpec = tween(TRANSITION_DURATION_MS))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(TRANSITION_DURATION_MS))
        }
    ) {
        composable<NavRoute.Splash> {
            SplashScreen()

            LaunchedEffect(isInitialized) {
                if (isInitialized) {
                    if (isAuthenticated) {
                        navController.navigate(NavRoute.Main) {
                            popUpTo(NavRoute.Splash) { inclusive = true }
                        }
                    } else {
                        navController.navigate(NavRoute.Login) {
                            popUpTo(NavRoute.Splash) { inclusive = true }
                        }
                    }
                }
            }
        }

        composable<NavRoute.Login>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(NavRoute.Main) {
                        popUpTo(NavRoute.Login) { inclusive = true }
                    }
                },
                onMobileSignIn = {
                    navController.navigate(NavRoute.MobileLogin)
                }
            )
        }

        composable<NavRoute.MobileLogin>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            MobileLoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoute.Main) {
                        popUpTo(NavRoute.Login) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavRoute.Main> {
            MainScreen(
                onObjectClick = { solverObject ->
                    navController.navigate(NavRoute.ObjectDetail(solverObject.id))
                },
                onSignOut = {
                    // Navigation is handled reactively by LaunchedEffect when isAuthenticated changes
                    loginViewModel.signOut()
                },
                onNavigateToMoreItem = { item ->
                    when (item) {
                        MoreItem.PAYMENTS -> navController.navigate(NavRoute.Payments)
                        MoreItem.VISIT -> navController.navigate(NavRoute.Visit)
                        MoreItem.LOGS -> navController.navigate(NavRoute.Logs)
                        MoreItem.DEBUG -> navController.navigate(NavRoute.Debug)
                        MoreItem.DANALOCK_DEMO -> navController.navigate(NavRoute.DanalockDemo)
                        MoreItem.MASTERLOCK_DEMO -> navController.navigate(NavRoute.MasterlockDemo)
                    }
                },
                onNavigateToMicrosoftLogin = {
                    navController.navigate(NavRoute.AddAccountLogin("microsoft"))
                },
                onNavigateToVippsLogin = {
                    navController.navigate(NavRoute.AddAccountLogin("vipps"))
                },
                onNavigateToMobileLogin = {
                    navController.navigate(NavRoute.AddAccountLogin("mobile"))
                }
            )
        }

        // Add Account Login - reuses login flows but returns to Main instead of replacing
        composable<NavRoute.AddAccountLogin>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<NavRoute.AddAccountLogin>()
            when (route.provider) {
                "mobile" -> {
                    MobileLoginScreen(
                        onLoginSuccess = {
                            navController.popBackStack()
                        },
                        onCancel = {
                            navController.popBackStack()
                        }
                    )
                }
                else -> {
                    // Microsoft or Vipps - use LoginScreen with add account mode
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = {
                            navController.popBackStack()
                        },
                        onMobileSignIn = {
                            navController.navigate(NavRoute.MobileLogin)
                        },
                        autoTriggerProvider = route.provider
                    )
                }
            }
        }

        composable<NavRoute.ObjectDetail>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            ObjectDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // More sub-screens
        composable<NavRoute.Payments>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            PaymentsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<NavRoute.Visit>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            VisitScreen()
        }

        composable<NavRoute.Logs>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            LogsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<NavRoute.Debug>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            DebugScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<NavRoute.DanalockDemo>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            DanalockDemoScreen()
        }

        composable<NavRoute.MasterlockDemo>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(TRANSITION_DURATION_MS)
                )
            }
        ) {
            MasterlockDemoScreen()
        }
    }
        
        // Deep link loading overlay (matches iOS)
        // Shows when executing a QR command
        if (deepLinkState.isExecuting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 20.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Executing command...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        
        // Status bottom sheet for deep link results (matches iOS StatusBottomSheet)
        if (deepLinkState.sheetVisible && deepLinkState.responseForSheet != null && deepLinkState.objectForSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { deepLinkViewModel.dismissSheet() },
                sheetState = sheetState
            ) {
                StatusBottomSheetContent(
                    response = deepLinkState.responseForSheet!!,
                    solverObject = deepLinkState.objectForSheet!!,
                    onDismiss = { deepLinkViewModel.dismissSheet() }
                )
            }
        }
        
        // Payment Middleware Sheet (for deep link commands that require payment)
        val showPaymentSheet by deepLinkViewModel.paymentMiddleware.showPaymentSheet.collectAsState()
        val paymentContext by deepLinkViewModel.paymentMiddleware.paymentContext.collectAsState()
        
        if (showPaymentSheet && paymentContext != null) {
            ModalBottomSheet(
                onDismissRequest = { deepLinkViewModel.paymentMiddleware.dismissPaymentSheet() },
                sheetState = rememberModalBottomSheetState()
            ) {
                PaymentMethodSheetContent(
                    context = paymentContext!!,
                    onSelectMethod = { method ->
                        deepLinkViewModel.paymentMiddleware.handlePaymentMethodSelected(method)
                    },
                    onDismiss = { deepLinkViewModel.paymentMiddleware.dismissPaymentSheet() }
                )
            }
        }
        
        // Subscription Middleware Sheet (for deep link commands that require subscription)
        val showSubscriptionSheet by deepLinkViewModel.subscriptionMiddleware.showSubscriptionSheet.collectAsState()
        val subscriptionContext by deepLinkViewModel.subscriptionMiddleware.subscriptionContext.collectAsState()
        
        if (showSubscriptionSheet && subscriptionContext != null) {
            ModalBottomSheet(
                onDismissRequest = { deepLinkViewModel.subscriptionMiddleware.dismissSubscriptionSheet() },
                sheetState = rememberModalBottomSheetState()
            ) {
                SubscriptionSheetContent(
                    context = subscriptionContext!!,
                    onSelectSubscription = { subscriptionId ->
                        deepLinkViewModel.subscriptionMiddleware.handleSubscriptionSelected(subscriptionId)
                    },
                    onDismiss = { deepLinkViewModel.subscriptionMiddleware.dismissSubscriptionSheet() }
                )
            }
        }
        
        // Snackbar host for errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

enum class MainDestination(
    val label: String,
    val icon: ImageVector
) {
    OBJECTS("Objects", Icons.Default.Home),
    FIND("Find", Icons.Default.Search),
    ACCOUNTS("Accounts", Icons.Default.AccountCircle),
    MORE("More", Icons.Default.MoreVert)
}

@Composable
fun MainScreen(
    onObjectClick: (SolverObject) -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToMoreItem: (MoreItem) -> Unit = {},
    onNavigateToMicrosoftLogin: () -> Unit = {},
    onNavigateToVippsLogin: () -> Unit = {},
    onNavigateToMobileLogin: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.OBJECTS) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MainDestination.entries.forEach { destination ->
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
            MainDestination.OBJECTS -> {
                ObjectsScreen(
                    onObjectClick = onObjectClick
                )
            }
            MainDestination.FIND -> {
                FindScreen(
                    onObjectClick = onObjectClick
                )
            }
            MainDestination.ACCOUNTS -> {
                AccountsScreen(
                    onNavigateToMicrosoftLogin = onNavigateToMicrosoftLogin,
                    onNavigateToVippsLogin = onNavigateToVippsLogin,
                    onNavigateToMobileLogin = onNavigateToMobileLogin,
                    onAllAccountsRemoved = onSignOut
                )
            }
            MainDestination.MORE -> {
                MoreScreen(
                    onSignOut = onSignOut,
                    onNavigateToItem = onNavigateToMoreItem
                )
            }
        }
    }
}


