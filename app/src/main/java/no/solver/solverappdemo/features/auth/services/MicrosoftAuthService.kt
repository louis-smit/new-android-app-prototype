package no.solver.solverappdemo.features.auth.services

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.microsoft.identity.client.PublicClientApplicationConfiguration
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.solver.solverappdemo.BuildConfig
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.config.AppEnvironment
import no.solver.solverappdemo.core.config.AuthConfiguration
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.storage.TokenStorage
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.UserInfo
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MicrosoftAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStorage: TokenStorage
) {
    private var msalApp: IMultipleAccountPublicClientApplication? = null
    private var currentEnvironment: AuthEnvironment = AuthEnvironment.SOLVER
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "MicrosoftAuthService"
    }

    suspend fun initialize(environment: AuthEnvironment = AuthEnvironment.SOLVER) {
        currentEnvironment = environment
        
        if (msalApp != null) return

        msalApp = suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createMultipleAccountPublicClientApplication(
                context,
                no.solver.solverappdemo.R.raw.msal_config,
                object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                    override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                        Log.d(TAG, "MSAL application created successfully")
                        continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        Log.e(TAG, "Failed to create MSAL application", exception)
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    suspend fun signIn(activity: Activity): AuthTokens {
        val app = msalApp ?: throw IllegalStateException("MSAL not initialized. Call initialize() first.")
        
        // Use ONLY the API scope - OIDC scopes (openid, profile, etc.) cause "scopes declined" error
        val apiScope = currentEnvironment.apiScope
        val scopes = listOf(apiScope)
        Log.d(TAG, "🔧 Attempting sign-in with scopes: $scopes")

        return suspendCancellableCoroutine { continuation ->
            val params = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(scopes)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        Log.d(TAG, "Sign in successful")
                        val tokens = authResultToTokens(authenticationResult)
                        saveTokens(tokens)
                        continuation.resume(tokens)
                    }

                    override fun onError(exception: MsalException) {
                        Log.e(TAG, "Sign in failed: ${exception.errorCode} - ${exception.message}", exception)
                        Log.e(TAG, "Exception type: ${exception.javaClass.simpleName}")
                        continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        Log.d(TAG, "Sign in cancelled")
                        continuation.resumeWithException(AuthCancelledException("User cancelled sign in"))
                    }
                })
                .build()

            app.acquireToken(params)
        }
    }

    suspend fun getAccessTokenSilently(): AuthTokens? {
        // Ensure MSAL is initialized
        if (msalApp == null) {
            Log.d(TAG, "MSAL not initialized, initializing now...")
            try {
                initialize(currentEnvironment)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MSAL for silent auth", e)
                return null
            }
        }

        val app = msalApp
        if (app == null) {
            Log.e(TAG, "MSAL initialization failed, cannot acquire token silently")
            return null
        }

        val config = AuthConfiguration.current(currentEnvironment)

        val accounts = app.accounts
        Log.d(TAG, "Found ${accounts.size} MSAL account(s)")
        
        if (accounts.isEmpty()) {
            Log.w(TAG, "No accounts found for silent token acquisition - user needs to sign in again")
            return null
        }

        val account = accounts.first()
        Log.d(TAG, "Attempting silent token acquisition for account: ${account.username}")

        return try {
            suspendCancellableCoroutine { continuation ->
                val params = AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(config.scopes)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            Log.d(TAG, "✅ Silent token acquisition successful")
                            val tokens = authResultToTokens(authenticationResult)
                            saveTokens(tokens)
                            continuation.resume(tokens)
                        }

                        override fun onError(exception: MsalException) {
                            Log.e(TAG, "❌ Silent token acquisition failed: ${exception.errorCode} - ${exception.message}", exception)
                            continuation.resumeWithException(exception)
                        }

                        override fun onCancel() {
                            Log.w(TAG, "Silent auth was cancelled")
                            continuation.resumeWithException(AuthCancelledException("Silent auth cancelled"))
                        }
                    })
                    .build()

                app.acquireTokenSilentAsync(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silent token acquisition exception: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext

        val accounts = app.accounts
        accounts.forEach { account ->
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    app.removeAccount(
                        account,
                        object : IMultipleAccountPublicClientApplication.RemoveAccountCallback {
                            override fun onRemoved() {
                                Log.d(TAG, "Account removed: ${account.username}")
                                continuation.resume(Unit)
                            }

                            override fun onError(exception: MsalException) {
                                Log.e(TAG, "Failed to remove account", exception)
                                continuation.resumeWithException(exception)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing account", e)
            }
        }

        tokenStorage.clearTokens()
        Log.d(TAG, "Sign out complete")
    }

    fun getAccounts(): List<IAccount> {
        return msalApp?.accounts ?: emptyList()
    }

    fun isSignedIn(): Boolean {
        return tokenStorage.hasValidToken() || (msalApp?.accounts?.isNotEmpty() == true)
    }

    private fun authResultToTokens(result: IAuthenticationResult): AuthTokens {
        val userInfo = parseUserInfoFromToken(result.accessToken)

        return AuthTokens(
            accessToken = result.accessToken,
            refreshToken = null, // MSAL handles refresh internally
            expiresAtMillis = result.expiresOn.time,
            tokenType = "Bearer",
            scope = result.scope.joinToString(" "),
            userId = result.account.id,
            userInfo = userInfo
        )
    }

    private fun parseUserInfoFromToken(accessToken: String): UserInfo? {
        return try {
            val parts = accessToken.split(".")
            if (parts.size != 3) return null

            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = String(decodedBytes, Charsets.UTF_8)
            val claims = Json.parseToJsonElement(json).jsonObject

            UserInfo(
                displayName = claims["name"]?.jsonPrimitive?.content,
                email = claims["preferred_username"]?.jsonPrimitive?.content
                    ?: claims["upn"]?.jsonPrimitive?.content,
                userName = claims["unique_name"]?.jsonPrimitive?.content,
                oid = claims["oid"]?.jsonPrimitive?.content,
                givenName = claims["given_name"]?.jsonPrimitive?.content,
                familyName = claims["family_name"]?.jsonPrimitive?.content
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user info from token", e)
            null
        }
    }

    private fun saveTokens(tokens: AuthTokens) {
        tokenStorage.saveAccessToken(tokens.accessToken)
        tokens.refreshToken?.let { tokenStorage.saveRefreshToken(it) }
        tokenStorage.saveTokenExpiry(tokens.expiresAtMillis)
    }

    suspend fun registerAndFetchUserId(tokens: AuthTokens, environment: AuthEnvironment): String {
        val registrationConfig = APIConfiguration.current(
            environment = environment,
            mode = AppEnvironment.current,
            provider = AuthProvider.VIPPS
        )
        val registrationBaseUrl = registrationConfig.baseURL

        val masterToken = fetchMasterToken(registrationBaseUrl)

        registerUser(registrationBaseUrl, masterToken, tokens)

        val msApiConfig = APIConfiguration.current(
            environment = environment,
            mode = AppEnvironment.current,
            provider = AuthProvider.MICROSOFT
        )
        val msApiBaseUrl = msApiConfig.baseURL

        return fetchUserId(msApiBaseUrl, tokens.accessToken)
    }

    private suspend fun fetchMasterToken(baseUrl: String): String {
        Log.d(TAG, "🔑 Getting master token for MS user registration")

        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/Token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            val body = """
                {
                    "oid": "${BuildConfig.APP_USER_OID}",
                    "password": "${BuildConfig.APP_USER_PASSWORD}",
                    "mobile": "",
                    "appId": ""
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val tokenResponse = json.decodeFromString<MsMasterTokenResponse>(responseBody)
                Log.d(TAG, "✅ Got master token for MS registration")
                tokenResponse.accessToken
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Master token request failed: ${connection.responseCode} - $errorBody")
                throw MsAuthRegistrationException("Failed to get master token: ${connection.responseCode}")
            }
        }
    }

    private suspend fun registerUser(baseUrl: String, masterToken: String, tokens: AuthTokens) {
        Log.d(TAG, "📝 Registering MS user on Solver (fire-and-forget)")

        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/User/Register")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $masterToken")
                    doOutput = true
                }

                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val body = """
                    {
                        "sid": "${tokens.userInfo?.oid ?: ""}",
                        "userId": 0,
                        "pin": "",
                        "userTypeId": 1,
                        "active": false,
                        "displayName": "${tokens.userInfo?.displayName ?: ""}",
                        "userName": "${tokens.userInfo?.email ?: ""}",
                        "password": "",
                        "appId": "",
                        "deviceModel": "$deviceModel"
                    }
                """.trimIndent()
                connection.outputStream.bufferedWriter().use { it.write(body) }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.d(TAG, "✅ MS user registered successfully")
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.w(TAG, "⚠️ MS user registration returned $responseCode - $errorBody")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ MS user registration failed (non-fatal)", e)
            }
        }
    }

    private suspend fun fetchUserId(msApiBaseUrl: String, accessToken: String): String {
        Log.d(TAG, "👤 Fetching real userId from /api/User/Me")

        return withContext(Dispatchers.IO) {
            val url = URL("$msApiBaseUrl/api/User/Me")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val jsonElement = json.parseToJsonElement(responseBody)
                val userId = jsonElement.jsonObject["userId"]?.jsonPrimitive?.int
                    ?: throw MsAuthRegistrationException("No userId found in /api/User/Me response")
                Log.d(TAG, "✅ Got real userId: $userId")
                userId.toString()
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ /api/User/Me failed: ${connection.responseCode} - $errorBody")
                throw MsAuthRegistrationException("Failed to fetch userId: ${connection.responseCode}")
            }
        }
    }
}

@Serializable
private data class MsMasterTokenResponse(
    @SerialName("access_token") val accessToken: String
)

class MsAuthRegistrationException(message: String) : Exception(message)

class AuthCancelledException(message: String) : Exception(message)
