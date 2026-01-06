package no.solver.solverappdemo.features.auth.services

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.UserInfo
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mobile authentication service - handles phone + SMS OTP login.
 * Matches the iOS AuthService.registerMobileUser / confirmMobileUser flow.
 *
 * Authentication Flow:
 * 1. Register: POST /api/User/Pending → creates pending user, triggers SMS
 * 2. Register: POST /api/User/Register → registers user with pending SID
 * 3. Confirm: POST /api/Token → login with SID/password to get tokens
 * 4. Confirm: PUT /api/User/Confirm → confirm PIN (may return non-200, ignore like RN)
 * 5. Confirm: GET /api/User/Me → get user profile
 * 6. Return session with tokens
 *
 * API Endpoint: Uses api-demo.solver.no (Solver API), NOT api365 (Microsoft API)
 * This is the same endpoint used by Vipps auth.
 */
@Singleton
class MobileAuthService @Inject constructor(
    private val json: Json
) {
    companion object {
        private const val TAG = "MobileAuthService"
        private const val USER_TYPE_PENDING = 97  // Match iOS/RN for pending user
        private const val USER_TYPE_MOBILE = 4    // Match iOS/RN for registered mobile user
    }

    private var currentEnvironment: AuthEnvironment = AuthEnvironment.SOLVER

    /**
     * Initialize the service with the given environment
     */
    fun initialize(environment: AuthEnvironment) {
        currentEnvironment = environment
        Log.d(TAG, "Initialized with environment: ${environment.displayName}")
    }

    /**
     * Get API base URL based on current environment.
     * IMPORTANT: Mobile auth uses the non-MS API (api-demo.solver.no), same as Vipps.
     */
    private fun getApiBaseUrl(): String {
        val config = APIConfiguration.current(
            environment = currentEnvironment,
            mode = AppEnvironment.current,
            provider = AuthProvider.MOBILE
        )
        return config.baseURL
    }

    /**
     * Step 1-2: Register mobile user.
     * Creates pending user, triggers SMS, and registers the user.
     * Returns RegisteredMobileUser containing sid, password, userId for confirmation step.
     */
    suspend fun registerMobileUser(input: MobileRegistrationInput): RegisteredMobileUser {
        val baseUrl = getApiBaseUrl()
        Log.d(TAG, "📍 Using API endpoint: $baseUrl for mobile auth")
        Log.d(TAG, "📱 Registering mobile user: ${input.displayName}, ${input.mobileNumber}")

        // Step 1a: Get Master Token (to create pending user)
        val masterToken = fetchMasterToken(baseUrl)

        // Step 1b: Create Pending User (triggers SMS)
        val pendingUser = createPendingUser(baseUrl, masterToken, input)

        // Step 2: Register User
        val password = UUID.randomUUID().toString()
        val registeredUserId = registerUser(baseUrl, masterToken, input, pendingUser.sid, password)

        Log.i(TAG, "✅ Mobile user registered, waiting for PIN confirmation")

        return RegisteredMobileUser(
            sid = pendingUser.sid,
            password = password,
            userId = registeredUserId,
            mobileNumber = input.mobileNumber
        )
    }

    /**
     * Step 3-5: Confirm mobile user with PIN.
     * Logs in with SID/password, confirms PIN, fetches profile, returns session.
     */
    suspend fun confirmMobileUser(input: MobileConfirmationInput): AuthTokens {
        val baseUrl = getApiBaseUrl()
        Log.d(TAG, "🔐 Confirming mobile user PIN")

        // Step 3: Get User Token (login)
        val tokens = loginUser(baseUrl, input.sid, input.password)

        // Step 4: Confirm PIN (may return non-200, we ignore like RN/iOS)
        confirmPin(baseUrl, tokens.accessToken, input.sid, input.pin)

        // Step 5: Get User Profile
        val userProfile = fetchUserProfile(baseUrl, tokens.accessToken)

        Log.i(TAG, "✅ Mobile user confirmed: ${userProfile.displayName}")

        val expiresAtMillis = System.currentTimeMillis() + (tokens.expiresIn * 1000L)

        return AuthTokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMillis = expiresAtMillis,
            tokenType = "Bearer",
            userId = userProfile.userId.toString(),
            userInfo = UserInfo(
                displayName = userProfile.displayName,
                email = userProfile.email,
                userName = userProfile.userName,
                oid = userProfile.oid ?: input.sid
            )
        )
    }

    /**
     * Refresh token for mobile accounts.
     * Uses the same endpoint as Vipps: POST /api/token/refresh
     */
    suspend fun refreshToken(refreshToken: String): AuthTokens? {
        Log.d(TAG, "🔄 Refreshing token for mobile account")

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
                    val tokenResponse = json.decodeFromString<MobileTokenResponse>(responseBody)

                    val expiresAtMillis = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

                    Log.d(TAG, "✅ Token refreshed successfully")
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
                Log.e(TAG, "❌ Token refresh error: ${e.message}", e)
                null
            }
        }
    }

    // ============ Private Helper Methods ============

    /**
     * Fetch master token using app credentials (same pattern as iOS/Vipps)
     */
    private suspend fun fetchMasterToken(baseUrl: String): String {
        Log.d(TAG, "🔑 Getting master token for mobile registration")

        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/Token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            // Match iOS exactly: "AppClient" for both mobile and appId
            val body = """
                {
                    "oid": "${BuildConfig.APP_USER_OID}",
                    "password": "${BuildConfig.APP_USER_PASSWORD}",
                    "mobile": "AppClient",
                    "appId": "AppClient"
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val tokenResponse = json.decodeFromString<MobileMasterTokenResponse>(responseBody)
                Log.d(TAG, "✅ Got master token")
                tokenResponse.accessToken
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Master token failed: ${connection.responseCode} - $errorBody")
                throw MobileAuthException("Failed to get master token: ${connection.responseCode}")
            }
        }
    }

    /**
     * Create pending user (triggers SMS)
     */
    private suspend fun createPendingUser(
        baseUrl: String,
        masterToken: String,
        input: MobileRegistrationInput
    ): PendingUserResponse {
        Log.d(TAG, "📱 Creating pending user (triggers SMS)")

        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/User/Pending")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $masterToken")
                doOutput = true
            }

            val body = """
                {
                    "userId": 0,
                    "userName": "${input.mobileNumber}",
                    "displayName": "${input.displayName}",
                    "userTypeId": $USER_TYPE_PENDING
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            if (connection.responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "✅ Pending user created: $responseBody")

                val jsonElement = json.parseToJsonElement(responseBody)
                val sid = jsonElement.jsonObject["sid"]?.jsonPrimitive?.content
                    ?: throw MobileAuthException("No SID in pending user response")
                val userId = jsonElement.jsonObject["userId"]?.jsonPrimitive?.int ?: 0

                PendingUserResponse(sid = sid, userId = userId)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Create pending user failed: ${connection.responseCode} - $errorBody")
                throw MobileAuthException("Failed to create pending user: ${connection.responseCode}")
            }
        }
    }

    /**
     * Register user with pending SID
     */
    private suspend fun registerUser(
        baseUrl: String,
        masterToken: String,
        input: MobileRegistrationInput,
        sid: String,
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

            // Match iOS exactly with empty appId and deviceModel
            val body = """
                {
                    "userId": 0,
                    "userTypeId": $USER_TYPE_MOBILE,
                    "displayName": "${input.displayName}",
                    "mobilePhone": "${input.mobileNumber}",
                    "userName": "${input.mobileNumber}",
                    "sid": "$sid",
                    "password": "$password",
                    "appId": "",
                    "deviceModel": ""
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            if (connection.responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "✅ User registered: $responseBody")

                val userId = try {
                    val jsonElement = json.parseToJsonElement(responseBody)
                    jsonElement.jsonObject["userId"]?.jsonPrimitive?.int ?: 0
                } catch (e: Exception) {
                    0
                }
                userId
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "❌ Register user failed: ${connection.responseCode} - $errorBody")
                throw MobileAuthException("Failed to register user: ${connection.responseCode}")
            }
        }
    }

    /**
     * Login user to get tokens
     */
    private suspend fun loginUser(baseUrl: String, sid: String, password: String): TokenLoginResponse {
        Log.d(TAG, "🔐 Step 1: Logging in user (POST /api/Token)")

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
                    "oid": "$sid",
                    "password": "$password",
                    "mobile": "",
                    "appId": ""
                }
            """.trimIndent()
            connection.outputStream.bufferedWriter().use { it.write(body) }

            Log.d(TAG, "Step 1 Response: ${connection.responseCode}")

            if (connection.responseCode == 400 || connection.responseCode == 401) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "Login failed with ${connection.responseCode}: $errorBody")
                throw MobileAuthException("Invalid credentials")
            }

            if (connection.responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Step 1 Success: Got access token")
                json.decodeFromString<TokenLoginResponse>(responseBody)
            } else {
                Log.e(TAG, "❌ Login failed: ${connection.responseCode}")
                throw MobileAuthException("Login failed: ${connection.responseCode}")
            }
        }
    }

    /**
     * Confirm PIN (may return non-200, we ignore like iOS/RN)
     */
    private suspend fun confirmPin(baseUrl: String, accessToken: String, sid: String, pin: String) {
        Log.d(TAG, "🔐 Step 2: Confirming PIN (PUT /api/User/Confirm)")

        withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/User/Confirm")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                doOutput = true
            }

            // PIN must be a string
            val body = """{"sid":"$sid","pin":"$pin"}"""
            Log.d(TAG, "Step 2 Payload: $body")
            connection.outputStream.bufferedWriter().use { it.write(body) }

            Log.d(TAG, "Step 2 Response: ${connection.responseCode}")

            // Match iOS/RN behavior: ignore non-200 responses and continue
            if (connection.responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.w(TAG, "Step 2 returned ${connection.responseCode} but proceeding to match RN behavior. Body: $errorBody")
            } else {
                Log.d(TAG, "Step 2 Success: PIN confirmed")
            }
        }
    }

    /**
     * Fetch user profile
     */
    private suspend fun fetchUserProfile(baseUrl: String, accessToken: String): UserProfileResponse {
        Log.d(TAG, "👤 Step 3: Fetching User Profile (GET /api/User/Me)")

        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl/api/User/Me")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            Log.d(TAG, "Step 3 Response: ${connection.responseCode}")

            if (connection.responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Step 3 Success: Got user profile")
                json.decodeFromString<UserProfileResponse>(responseBody)
            } else {
                Log.e(TAG, "❌ Fetch user profile failed: ${connection.responseCode}")
                throw MobileAuthException("Failed to fetch user profile: ${connection.responseCode}")
            }
        }
    }
}

// ============ Data Classes ============

data class MobileRegistrationInput(
    val displayName: String,
    val mobileNumber: String
)

data class RegisteredMobileUser(
    val sid: String,
    val password: String,
    val userId: Int,
    val mobileNumber: String
)

data class MobileConfirmationInput(
    val sid: String,
    val password: String,
    val pin: String
)

private data class PendingUserResponse(
    val sid: String,
    val userId: Int
)

@Serializable
private data class MobileMasterTokenResponse(
    @SerialName("access_token") val accessToken: String
)

@Serializable
private data class TokenLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 3600
)

@Serializable
private data class MobileTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String? = null
)

@Serializable
private data class UserProfileResponse(
    val userId: Int,
    val displayName: String = "",
    val userName: String = "",
    val email: String? = null,
    val oid: String? = null
)

class MobileAuthException(message: String) : Exception(message)
