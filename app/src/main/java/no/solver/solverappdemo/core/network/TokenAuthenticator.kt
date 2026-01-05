package no.solver.solverappdemo.core.network

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.storage.TokenStorage
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.MicrosoftAuthService
import no.solver.solverappdemo.features.auth.services.SessionManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * OkHttp Authenticator that handles 401 Unauthorized responses by:
 * 1. Attempting to refresh the access token using MSAL's silent token acquisition
 * 2. Retrying the original request with the new token
 * 3. Giving up if refresh fails (user needs to re-authenticate)
 *
 * This mirrors the iOS APIClient's handleUnauthorizedResponse behavior.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
    // Use Provider to break circular dependency with OkHttpClient
    private val microsoftAuthServiceProvider: Provider<MicrosoftAuthService>
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        private const val MAX_RETRY_COUNT = 1
    }

    // Mutex to prevent concurrent refresh attempts (coalescing like iOS)
    private val refreshMutex = Mutex()
    
    // Track the token that was used for refresh to avoid infinite loops
    @Volatile
    private var lastRefreshedToken: String? = null

    override fun authenticate(route: Route?, response: Response): Request? {
        val originalRequest = response.request
        val currentToken = tokenStorage.getAccessToken()

        // If no token, can't retry
        if (currentToken == null) {
            Log.w(TAG, "No access token available, cannot refresh")
            return null
        }

        // Check retry count to prevent infinite loops
        val retryCount = originalRequest.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Max retry count reached, giving up")
            return null
        }

        // If we already refreshed with this token and still got 401, give up
        // This prevents infinite loops when the refresh itself produces an invalid token
        if (lastRefreshedToken == currentToken) {
            Log.w(TAG, "Already refreshed this token, giving up to prevent loop")
            lastRefreshedToken = null
            return null
        }

        Log.d(TAG, "🔐 Got 401, attempting token refresh for ${originalRequest.url.encodedPath}")

        // Attempt token refresh (synchronized to prevent concurrent refreshes)
        val newToken = runBlocking {
            refreshMutex.withLock {
                // Double-check: maybe another thread already refreshed
                val latestToken = tokenStorage.getAccessToken()
                if (latestToken != null && latestToken != currentToken) {
                    Log.d(TAG, "Token was already refreshed by another request")
                    return@withLock latestToken
                }

                refreshAccessToken()
            }
        }

        if (newToken == null) {
            Log.e(TAG, "Token refresh failed, cannot retry request")
            return null
        }

        // Mark this token as the one we just refreshed
        lastRefreshedToken = currentToken

        Log.i(TAG, "✅ Token refreshed successfully, retrying request to ${originalRequest.url.encodedPath}")

        // Retry the original request with the new token
        return originalRequest.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header("X-Retry-Count", (retryCount + 1).toString())
            .build()
    }

    /**
     * Refresh the access token based on the current session's auth provider.
     * Routes to the appropriate auth service (Microsoft, Vipps, Mobile).
     * 
     * If refresh fails, clears the session to force re-authentication (matching iOS behavior).
     */
    private suspend fun refreshAccessToken(): String? {
        val session = sessionManager.getCurrentSession()
        if (session == null) {
            Log.w(TAG, "No current session, cannot refresh token")
            return null
        }

        Log.d(TAG, "Refreshing token for provider: ${session.provider}")

        return try {
            val tokens: AuthTokens? = when (session.provider) {
                AuthProvider.MICROSOFT -> {
                    val microsoftAuthService = microsoftAuthServiceProvider.get()
                    // Ensure MSAL uses the correct environment from the session
                    microsoftAuthService.initialize(session.environment)
                    microsoftAuthService.getAccessTokenSilently()
                }
                AuthProvider.VIPPS -> {
                    // TODO: Implement VippsAuthService.refreshToken()
                    Log.w(TAG, "Vipps token refresh not yet implemented")
                    null
                }
                AuthProvider.MOBILE -> {
                    // TODO: Implement MobileAuthService.refreshToken()
                    Log.w(TAG, "Mobile token refresh not yet implemented")
                    null
                }
            }

            if (tokens != null) {
                Log.d(TAG, "Token refresh successful, new token expires in ${tokens.secondsUntilExpiry}s")
                tokens.accessToken
            } else {
                // Refresh failed - clear session to force re-login (matches iOS behavior)
                Log.w(TAG, "Token refresh returned null for provider: ${session.provider}")
                handleRefreshFailure(session)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed for ${session.provider}: ${e.message}", e)
            handleRefreshFailure(session)
            null
        }
    }

    /**
     * Handle refresh failure by clearing the session.
     * This forces the user back to login screen (matching iOS AccountManager.handle401Error behavior).
     */
    private suspend fun handleRefreshFailure(session: Session) {
        Log.w(TAG, "🚪 Clearing session due to refresh failure - user must re-authenticate")
        try {
            sessionManager.removeSession(session.id)
            tokenStorage.clearTokens()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session: ${e.message}", e)
        }
    }
}
