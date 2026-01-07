package no.solver.solverappdemo.features.auth.services

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
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
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import no.solver.solverappdemo.BuildConfig
import no.solver.solverappdemo.core.config.APIConfiguration
import no.solver.solverappdemo.core.config.AppEnvironment
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.UserInfo
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Vipps OAuth 2.0 authentication service using AppAuth library.
 * 
 * Uses AppAuth (net.openid.appauth) for OAuth flow - this automatically handles:
 * - Native Vipps app launch when installed
 * - Web browser fallback when Vipps app is not installed
 * - Proper OAuth redirect handling
 * 
 * Authentication Flow (matching iOS VippsAuthProvider.swift):
 * 1. OAuth Authorization → Opens Vipps via AppAuth (app or web)
 * 2. Code Exchange → Exchanges authorization code for Vipps access token
 * 3. Get User Info → Fetches user details from Vipps (name, phone, email, sub)
 * 4. Master Token → Gets admin token from Solver API using app credentials
 * 5. User Registration → Registers/updates user on Solver backend
 * 6. User Token → Gets Solver access/refresh tokens for the user
 * 7. Create Session → Returns AuthTokens with provider set to VIPPS
 */
@Singleton
class VippsAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    companion object {
        private const val TAG = "VippsAuthService"
        
        // Vipps OAuth endpoints (same as iOS)
        private const val VIPPS_AUTH_URL = "https://api.vipps.no/access-management-1.0/access/oauth2/auth"
        private const val VIPPS_TOKEN_URL = "https://api.vipps.no/access-management-1.0/access/oauth2/token"
        private const val VIPPS_USERINFO_URL = "https://api.vipps.no/vipps-userinfo-api/userinfo"
        
        // Redirect URI - must match what's registered with Vipps and in AndroidManifest
        private const val REDIRECT_URI = "solverapp://oauth/vipps"
        
        // OAuth scopes (same as iOS)
        private val SCOPES = listOf(
            "openid",
            "name",
            "phoneNumber",
            "address",
            "birthDate",
            "api_version_2"
        )
        
        // Request code for activity result
        const val REQUEST_CODE = 2020
    }
    
    private var currentEnvironment: AuthEnvironment = AuthEnvironment.SOLVER
    
    // AppAuth service configuration
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(VIPPS_AUTH_URL),
        Uri.parse(VIPPS_TOKEN_URL)
    )
    
    // Pending auth continuation for when activity result arrives
    private var pendingAuthContinuation: ((Result<String>) -> Unit)? = null
    
    /**
     * Initialize the service with the given environment
     */
    fun initialize(environment: AuthEnvironment) {
        currentEnvironment = environment
        Log.d(TAG, "Initialized with environment: ${environment.displayName}")
    }
    
    /**
     * Get Vipps client ID based on current environment
     */
    private fun getClientId(): String {
        return when (currentEnvironment) {
            AuthEnvironment.SOLVER -> BuildConfig.SOLVER_VIPPS_CLIENT_ID
            AuthEnvironment.ZOHM -> BuildConfig.ZOHM_VIPPS_CLIENT_ID
        }
    }
    
    /**
     * Get Vipps client secret based on current environment
     */
    private fun getClientSecret(): String {
        return when (currentEnvironment) {
            AuthEnvironment.SOLVER -> BuildConfig.SOLVER_VIPPS_SECRET
            AuthEnvironment.ZOHM -> BuildConfig.ZOHM_VIPPS_SECRET
        }
    }
    
    /**
     * Get API base URL based on current environment (for Vipps, always use non-MS API)
     */
    private fun getApiBaseUrl(): String {
        val config = APIConfiguration.current(
            environment = currentEnvironment,
            mode = AppEnvironment.current,
            provider = AuthProvider.VIPPS
        )
        return config.baseURL
    }
    
    /**
     * Build the AppAuth authorization request.
     * Note: We do NOT set requested_flow=app_to_app to allow automatic web fallback
     * when the Vipps app is not installed (matching old Android app behavior).
     */
    private fun buildAuthorizationRequest(): AuthorizationRequest {
        val scopeString = SCOPES.joinToString(" ")
        
        return AuthorizationRequest.Builder(
            serviceConfig,
            getClientId(),
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScopes(SCOPES)
            .setCodeVerifier(null) // No PKCE, matching old implementation
            .build()
    }
    
    /**
     * Get the authorization intent to launch via startActivityForResult.
     * This should be called from the Activity to get the intent to launch.
     */
    fun getAuthorizationIntent(activity: Activity): Intent {
        val authService = AuthorizationService(activity)
        val authRequest = buildAuthorizationRequest()
        
        Log.d(TAG, "🔗 Vipps Auth URL: ${authRequest.toUri()}")
        
        return authService.getAuthorizationRequestIntent(authRequest)
    }
    
    /**
     * Start Vipps sign-in flow using AppAuth.
     * 
     * @param activity The current activity
     * @param launchForResult Callback to launch the authorization intent via startActivityForResult
     */
    suspend fun signIn(
        activity: Activity,
        launchForResult: (Intent, Int) -> Unit
    ): AuthTokens {
        Log.d(TAG, "Starting Vipps authentication flow with AppAuth")
        
        // Step 1: Get authorization code via AppAuth OAuth flow
        val code = suspendCancellableCoroutine { continuation ->
            pendingAuthContinuation = { result ->
                result.fold(
                    onSuccess = { code ->
                        continuation.resume(code)
                    },
                    onFailure = { error ->
                        continuation.resumeWithException(error)
                    }
                )
            }
            
            continuation.invokeOnCancellation {
                pendingAuthContinuation = null
            }
            
            // Launch the AppAuth OAuth flow
            val authIntent = getAuthorizationIntent(activity)
            launchForResult(authIntent, REQUEST_CODE)
        }
        
        // Step 2: Exchange code for Vipps token
        val vippsToken = exchangeCodeForVippsToken(code)
        
        // Step 3: Get user info from Vipps
        val vippsUserInfo = getVippsUserInfo(vippsToken.accessToken)
        
        // Step 4-6: Get Solver tokens (master token -> register -> user token)
        val solverTokens = getSolverTokens(vippsUserInfo)
        
        Log.i(TAG, "✅ Vipps authentication successful")
        return solverTokens
    }
    
    /**
     * Handle the OAuth result from onActivityResult.
     * Called by the Activity when receiving the result from AppAuth.
     */
    fun handleAuthResult(data: Intent?) {
        Log.d(TAG, "Handling AppAuth result")
        
        if (data == null) {
            Log.e(TAG, "No data in auth result")
            pendingAuthContinuation?.invoke(Result.failure(VippsAuthException("No auth result")))
            pendingAuthContinuation = null
            return
        }
        
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        
        when {
            response?.authorizationCode != null -> {
                Log.d(TAG, "Received authorization code")
                pendingAuthContinuation?.invoke(Result.success(response.authorizationCode!!))
            }
            exception != null -> {
                Log.e(TAG, "OAuth error: ${exception.errorDescription ?: exception.error}")
                pendingAuthContinuation?.invoke(Result.failure(
                    VippsAuthException(exception.errorDescription ?: exception.error ?: "Unknown error")
                ))
            }
            else -> {
                Log.e(TAG, "No code or error in result")
                pendingAuthContinuation?.invoke(Result.failure(VippsAuthException("Invalid auth result")))
            }
        }
        pendingAuthContinuation = null
    }
    
    /**
     * Cancel any pending auth flow (e.g., user pressed back)
     */
    fun cancelPendingAuth() {
        pendingAuthContinuation?.invoke(Result.failure(AuthCancelledException("User cancelled Vipps sign-in")))
        pendingAuthContinuation = null
    }
    
    /**
     * Check if there's a pending auth continuation (for handling back press)
     */
    fun hasPendingAuth(): Boolean = pendingAuthContinuation != null
    
    /**
     * Refresh Solver token using refresh token
     */
    suspend fun refreshToken(refreshToken: String): AuthTokens? {
        Log.d(TAG, "Refreshing Solver token for Vipps account")
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${getApiBaseUrl()}/api/token/refresh")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }
                
                val body = """{"refreshToken":"$refreshToken"}"""
                connection.outputStream.bufferedWriter().use { it.write(body) }
                
                if (connection.responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    val tokenResponse = json.decodeFromString<SolverTokenResponse>(responseBody)
                    
                    val expiresAtMillis = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)
                    
                    AuthTokens(
                        accessToken = tokenResponse.accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        expiresAtMillis = expiresAtMillis,
                        tokenType = tokenResponse.tokenType ?: "Bearer"
                    )
                } else {
                    Log.e(TAG, "❌ Token refresh failed: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Token refresh error", e)
                null
            }
        }
    }
    
    /**
     * Step 2: Exchange authorization code for Vipps token
     */
    private suspend fun exchangeCodeForVippsToken(code: String): VippsTokenResponse {
        Log.d(TAG, "🔄 Exchanging Vipps code for token")
        
        return withContext(Dispatchers.IO) {
            val url = URL(VIPPS_TOKEN_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                
                // Basic Auth: client_id:client_secret base64 encoded
                val authString = "${getClientId()}:${getClientSecret()}"
                val base64Auth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $base64Auth")
                
                doOutput = true
            }
            
            val bodyParams = listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI
            )
            val bodyString = bodyParams.joinToString("&") { "${it.first}=${it.second}" }
            connection.outputStream.bufferedWriter().use { it.write(bodyString) }
            
            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<VippsTokenResponse>(responseBody)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Vipps token exchange failed: ${connection.responseCode} - $errorBody")
                throw VippsAuthException("Token exchange failed: ${connection.responseCode}")
            }
        }
    }
    
    /**
     * Step 3: Get user info from Vipps
     */
    private suspend fun getVippsUserInfo(accessToken: String): VippsUserInfo {
        Log.d(TAG, "👤 Getting Vipps user info")
        
        return withContext(Dispatchers.IO) {
            val url = URL(VIPPS_USERINFO_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
            }
            
            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<VippsUserInfo>(responseBody)
            } else {
                Log.e(TAG, "❌ Vipps user info failed: ${connection.responseCode}")
                throw VippsAuthException("Failed to get user info")
            }
        }
    }
    
    /**
     * Steps 4-6: Get Solver tokens (Master -> Register -> User Token)
     */
    private suspend fun getSolverTokens(vippsUser: VippsUserInfo): AuthTokens {
        val baseUrl = getApiBaseUrl()
        Log.d(TAG, "📍 Using API endpoint: $baseUrl")
        
        // 4a: Get master token
        val masterToken = fetchMasterToken(baseUrl)
        
        // 4b: Register user on Solver
        val password = UUID.randomUUID().toString()
        val solverUserId = registerSolverUser(baseUrl, masterToken, vippsUser, password)
        
        // 4c: Get user token
        val userTokenResponse = fetchUserToken(baseUrl, vippsUser.sub, password)
        
        // Construct AuthTokens
        val userInfo = UserInfo(
            displayName = vippsUser.name,
            email = vippsUser.email,
            userName = vippsUser.phoneNumber,
            oid = vippsUser.sub
        )
        
        val expiresAtMillis = System.currentTimeMillis() + (userTokenResponse.expiresIn * 1000L)
        
        return AuthTokens(
            accessToken = userTokenResponse.accessToken,
            refreshToken = userTokenResponse.refreshToken,
            expiresAtMillis = expiresAtMillis,
            tokenType = userTokenResponse.tokenType ?: "Bearer",
            userId = solverUserId.toString(),
            userInfo = userInfo
        )
    }
    
    /**
     * Fetch master token using app credentials
     */
    private suspend fun fetchMasterToken(baseUrl: String): String {
        Log.d(TAG, "🔑 Getting master token for user registration")
        
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
                val tokenResponse = json.decodeFromString<MasterTokenResponse>(responseBody)
                Log.d(TAG, "✅ Got master token for registration")
                tokenResponse.accessToken
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Master token request failed: ${connection.responseCode} - $errorBody")
                throw VippsAuthException("Failed to get master token")
            }
        }
    }
    
    /**
     * Register user on Solver backend
     */
    private suspend fun registerSolverUser(
        baseUrl: String,
        masterToken: String,
        vippsUser: VippsUserInfo,
        password: String
    ): Int {
        Log.d(TAG, "📝 Registering user on Solver")
        
        return withContext(Dispatchers.IO) {
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
                    "userId": 0,
                    "pin": "",
                    "userTypeId": 1,
                    "active": false,
                    "displayName": "${vippsUser.name ?: ""}",
                    "mobilePhone": "${vippsUser.phoneNumber ?: ""}",
                    "userName": "${vippsUser.phoneNumber ?: ""}",
                    "sid": "${vippsUser.sub}",
                    "password": "$password",
                    "appId": "",
                    "deviceModel": "$deviceModel"
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }
            
            if (connection.responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val userId = try {
                    val jsonElement = json.parseToJsonElement(responseBody)
                    jsonElement.jsonObject["userId"]?.jsonPrimitive?.int ?: 1
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ User registered but no userId in response, using placeholder 1")
                    1
                }
                Log.d(TAG, "✅ User registered with ID: $userId")
                userId
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Register user failed: ${connection.responseCode} - $errorBody")
                throw VippsAuthException("Failed to register user: ${connection.responseCode}")
            }
        }
    }
    
    /**
     * Fetch user token from Solver
     */
    private suspend fun fetchUserToken(baseUrl: String, oid: String, password: String): SolverTokenResponse {
        Log.d(TAG, "🔐 Fetching user token from Solver")
        
        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/Token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            
            val body = """{"oid":"$oid","password":"$password"}"""
            connection.outputStream.bufferedWriter().use { it.write(body) }
            
            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                json.decodeFromString<SolverTokenResponse>(responseBody)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Fetch user token failed: $errorBody")
                throw VippsAuthException("Failed to get user token")
            }
        }
    }
}

// ============ Response Models ============

@Serializable
private data class VippsTokenResponse(
    @SerialName("access_token") val accessToken: String,
    val scope: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
private data class VippsUserInfo(
    val sub: String,
    val name: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val email: String? = null,
    val birthdate: String? = null
)

@Serializable
private data class MasterTokenResponse(
    @SerialName("access_token") val accessToken: String
)

@Serializable
private data class SolverTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String? = null
)

class VippsAuthException(message: String) : Exception(message)
// AuthCancelledException is defined in MicrosoftAuthService.kt
