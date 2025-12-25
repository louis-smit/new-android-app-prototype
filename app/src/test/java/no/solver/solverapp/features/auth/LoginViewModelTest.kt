package no.solver.solverapp.features.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import no.solver.solverapp.MainDispatcherRule
import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider
import no.solver.solverapp.features.auth.models.AuthTokens
import no.solver.solverapp.features.auth.models.Session
import no.solver.solverapp.features.auth.models.UserInfo
import no.solver.solverapp.features.auth.services.AuthCancelledException
import no.solver.solverapp.features.auth.services.MicrosoftAuthService
import no.solver.solverapp.features.auth.services.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var microsoftAuthService: MicrosoftAuthService
    private lateinit var sessionManager: SessionManager
    
    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val environmentFlow = MutableStateFlow(AuthEnvironment.SOLVER)

    @Before
    fun setup() {
        microsoftAuthService = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        
        every { sessionManager.currentSessionFlow } returns sessionFlow
        every { sessionManager.authEnvironmentFlow } returns environmentFlow
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `isAuthenticated is false when no session exists`() = runTest {
        sessionFlow.value = null
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)

        assertFalse(viewModel.isAuthenticated.value)
    }

    @Test
    fun `isAuthenticated is true when session exists`() = runTest {
        val session = createTestSession()
        sessionFlow.value = session
        
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)

        assertTrue(viewModel.isAuthenticated.value)
    }

    @Test
    fun `signInWithMicrosoft shows Success on successful auth`() = runTest {
        val activity = mockk<android.app.Activity>(relaxed = true)
        val tokens = createTestTokens()
        
        coEvery { sessionManager.getAuthEnvironment() } returns AuthEnvironment.SOLVER
        coEvery { microsoftAuthService.signIn(any()) } returns tokens
        coEvery { sessionManager.createSession(any(), any(), any()) } returns mockk()

        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        viewModel.signInWithMicrosoft(activity)

        assertEquals(LoginUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `signInWithMicrosoft shows Error on exception`() = runTest {
        val activity = mockk<android.app.Activity>(relaxed = true)
        
        coEvery { sessionManager.getAuthEnvironment() } returns AuthEnvironment.SOLVER
        coEvery { microsoftAuthService.signIn(any()) } throws Exception("Network error")

        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        viewModel.signInWithMicrosoft(activity)

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertEquals("Network error", (state as LoginUiState.Error).message)
    }

    @Test
    fun `signInWithMicrosoft returns to Idle on user cancellation`() = runTest {
        val activity = mockk<android.app.Activity>(relaxed = true)
        
        coEvery { sessionManager.getAuthEnvironment() } returns AuthEnvironment.SOLVER
        coEvery { microsoftAuthService.signIn(any()) } throws AuthCancelledException("User cancelled")

        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        viewModel.signInWithMicrosoft(activity)

        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `clearError resets state to Idle`() = runTest {
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        
        viewModel.clearError()
        
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `signOut clears session and returns to Idle`() = runTest {
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        
        viewModel.signOut()
        
        coVerify { microsoftAuthService.signOut() }
        coVerify { sessionManager.clearAllSessions() }
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `setAuthEnvironment updates session manager`() = runTest {
        val viewModel = LoginViewModel(microsoftAuthService, sessionManager)
        
        viewModel.setAuthEnvironment(AuthEnvironment.ZOHM)
        
        coVerify { sessionManager.setAuthEnvironment(AuthEnvironment.ZOHM) }
    }

    private fun createTestSession() = Session(
        id = "test-session",
        provider = AuthProvider.MICROSOFT,
        environment = AuthEnvironment.SOLVER,
        tokens = createTestTokens(),
        isActive = true
    )

    private fun createTestTokens() = AuthTokens(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
        expiresAtMillis = System.currentTimeMillis() + 3600000,
        tokenType = "Bearer",
        scope = "openid profile email",
        userId = "test-user-id",
        userInfo = UserInfo(
            displayName = "Test User",
            email = "test@example.com"
        )
    )
}
