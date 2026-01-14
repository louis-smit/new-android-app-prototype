package no.solver.solverappdemo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.features.auth.LoginScreen
import no.solver.solverappdemo.features.auth.LoginViewModel
import no.solver.solverappdemo.features.auth.MobileLoginScreen

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

private const val SPLASH_DELAY_MS = 500L
private const val TRANSITION_DURATION_MS = 300

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val isAuthenticated by loginViewModel.isAuthenticated.collectAsState()
    var isInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MS)
        isInitialized = true
    }

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
                    loginViewModel.signOut()
                    navController.navigate(NavRoute.Login) {
                        popUpTo(NavRoute.Main) { inclusive = true }
                    }
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
                }
            )
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
            PaymentsScreen()
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
            LogsScreen()
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
            DebugScreen()
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
    onNavigateToMoreItem: (MoreItem) -> Unit = {}
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
                AccountsPlaceholderScreen(onSignOut = onSignOut)
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

@Composable
private fun AccountsPlaceholderScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Accounts")
            androidx.compose.material3.Button(onClick = onSignOut) {
                Text("Sign Out")
            }
        }
    }
}

private val Int.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this.toFloat())
