package no.solver.solverappdemo.core.bluetooth.smartlock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.solver.solverappdemo.data.models.ExecuteResponse
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SmartLockTokenCache"
private const val PREFS_NAME = "SmartLockTokenCache"
private const val CACHE_KEY = "token_cache"

/**
 * Thread-safe cache for smart lock tokens, keyed by objectId.
 * Works with any lock brand (Danalock, Masterlock, etc.)
 * Persists to SharedPreferences for survival across app restarts.
 */
@Singleton
class SmartLockTokenCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = ConcurrentHashMap<Int, SmartLockTokens>()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadFromPreferences()
    }

    // MARK: - Persistence

    private fun loadFromPreferences() {
        try {
            val data = prefs.getString(CACHE_KEY, null) ?: return
            val decoded = json.decodeFromString<Map<Int, CachedTokenDTO>>(data)
            val validTokens = decoded.mapNotNull { (key, dto) ->
                val tokens = dto.toTokens()
                if (!tokens.isExpired) key to tokens else null
            }.toMap()
            cache.putAll(validTokens)
            Log.i(TAG, "Loaded ${cache.size} cached smart lock tokens from preferences")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode cached tokens: ${e.message}")
        }
    }

    private fun saveToPreferences() {
        try {
            val dtoMap = cache.mapValues { CachedTokenDTO.from(it.value) }
            val data = json.encodeToString(dtoMap)
            prefs.edit().putString(CACHE_KEY, data).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode tokens for persistence: ${e.message}")
        }
    }

    // MARK: - Public API

    /**
     * Store tokens for an object
     */
    fun store(objectId: Int, tokens: SmartLockTokens) {
        cache[objectId] = tokens
        saveToPreferences()

        val remainingMinutes = (tokens.remainingValiditySeconds ?: 0) / 60
        Log.i(TAG, "Cached ${tokens.brand.displayName} tokens for object $objectId " +
                "(device: ${tokens.deviceIdentifier}, valid for $remainingMinutes min)")
    }

    /**
     * Retrieve tokens for an object (returns null if not cached or expired)
     */
    fun tokens(objectId: Int): SmartLockTokens? {
        val tokens = cache[objectId] ?: return null

        if (tokens.isExpired) {
            cache.remove(objectId)
            saveToPreferences()
            Log.i(TAG, "Tokens expired for object $objectId")
            return null
        }

        return tokens
    }

    /**
     * Check if we have valid cached tokens for an object
     */
    fun hasTokens(objectId: Int): Boolean {
        return tokens(objectId) != null
    }

    /**
     * Clear tokens for a specific object
     */
    fun clear(objectId: Int) {
        cache.remove(objectId)
        saveToPreferences()
        Log.i(TAG, "Cleared tokens for object $objectId")
    }

    /**
     * Clear all cached tokens
     */
    fun clearAll() {
        cache.clear()
        saveToPreferences()
        Log.i(TAG, "Cleared all smart lock tokens")
    }

    companion object {
        /**
         * Parse Danalock tokens from ExecuteResponse context
         * Context keys: serial_number, login_token, advertising_key, valid_from, valid_to
         */
        fun parseDanalockTokens(response: ExecuteResponse, objectId: Int): SmartLockTokens? {
            val serialNumber = response.findValueInContext("serial_number")
            val loginTokenString = response.findValueInContext("login_token")

            if (serialNumber == null || loginTokenString == null) {
                Log.w(TAG, "Cannot parse Danalock tokens: missing required fields")
                return null
            }

            val loginTokenData = try {
                Base64.decode(loginTokenString, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode login_token: ${e.message}")
                return null
            }

            val broadcastKeyData = response.findValueInContext("advertising_key")?.let {
                try {
                    Base64.decode(it, Base64.DEFAULT)
                } catch (e: Exception) {
                    byteArrayOf()
                }
            } ?: byteArrayOf()

            // Parse validity timestamps (Unix timestamps as strings)
            val expiresAt = response.findValueInContext("valid_to")?.let { validToString ->
                try {
                    Date(validToString.toLong() * 1000)
                } catch (e: Exception) {
                    null
                }
            } ?: Date(System.currentTimeMillis() + 3600_000) // 1 hour fallback

            // Combine login token and broadcast key for storage
            // Format: [loginToken length (4 bytes)][loginToken][broadcastKey]
            val combinedData = ByteBuffer.allocate(4 + loginTokenData.size + broadcastKeyData.size).apply {
                putInt(loginTokenData.size)
                put(loginTokenData)
                put(broadcastKeyData)
            }.array()

            return SmartLockTokens(
                brand = SmartLockBrand.DANALOCK,
                deviceIdentifier = serialNumber,
                authData = combinedData,
                additionalData = null,
                cachedAt = Date(),
                expiresAt = expiresAt
            )
        }

        /**
         * Parse Masterlock tokens from ExecuteResponse context
         * Context keys: deviceIdentifier, accessProfile, profileExpiration, profileActivation, FirmwareVersion
         */
        fun parseMasterlockTokens(response: ExecuteResponse, objectId: Int): SmartLockTokens? {
            val deviceIdentifier = response.findValueInContext("deviceIdentifier")
            val accessProfile = response.findValueInContext("accessProfile")

            if (deviceIdentifier == null || accessProfile == null) {
                Log.w(TAG, "Cannot parse Masterlock tokens: missing required fields")
                return null
            }

            // Parse expiration (ISO date string like "2025-12-10T02:03:47.012312Z")
            val expiresAt = response.findValueInContext("profileExpiration")?.let { expirationString ->
                try {
                    val formats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                        "yyyy-MM-dd'T'HH:mm:ss'Z'"
                    )
                    var date: Date? = null
                    for (format in formats) {
                        try {
                            date = SimpleDateFormat(format, Locale.US).parse(expirationString)
                            if (date != null) break
                        } catch (_: Exception) {}
                    }
                    date
                } catch (e: Exception) {
                    null
                }
            } ?: Date(System.currentTimeMillis() + 3600_000) // 1 hour fallback

            // Store firmware version as additional data
            val firmwareVersionStr = response.findValueInContext("FirmwareVersion") ?: "-1"
            val firmwareVersion = firmwareVersionStr.toIntOrNull() ?: 0
            val firmwareData = ByteBuffer.allocate(4).putInt(firmwareVersion).array()

            // Access profile is the auth data
            val authData = accessProfile.toByteArray(Charsets.UTF_8)

            return SmartLockTokens(
                brand = SmartLockBrand.MASTERLOCK,
                deviceIdentifier = deviceIdentifier,
                authData = authData,
                additionalData = firmwareData,
                cachedAt = Date(),
                expiresAt = expiresAt
            )
        }
    }
}

// MARK: - Token Extraction Extensions

/**
 * Extract Danalock-specific tokens from combined authData
 * Returns Pair(loginToken, broadcastKey)
 */
fun SmartLockTokens.extractDanalockTokens(): Pair<ByteArray, ByteArray>? {
    if (brand != SmartLockBrand.DANALOCK) return null
    if (authData.size < 4) return null

    val buffer = ByteBuffer.wrap(authData)
    val length = buffer.int

    if (authData.size < 4 + length) return null

    val loginToken = ByteArray(length)
    buffer.get(loginToken)

    val broadcastKeySize = authData.size - 4 - length
    val broadcastKey = ByteArray(broadcastKeySize)
    buffer.get(broadcastKey)

    return Pair(loginToken, broadcastKey)
}

/**
 * Extract Masterlock-specific data
 * Returns Pair(accessProfile, firmwareVersion)
 */
fun SmartLockTokens.extractMasterlockTokens(): Pair<String, Int>? {
    if (brand != SmartLockBrand.MASTERLOCK) return null

    val accessProfile = String(authData, Charsets.UTF_8)

    val firmwareVersion = additionalData?.let {
        if (it.size >= 4) ByteBuffer.wrap(it).int else 0
    } ?: 0

    return Pair(accessProfile, firmwareVersion)
}

// MARK: - Persistence DTO

@Serializable
private data class CachedTokenDTO(
    val brand: String,
    val deviceIdentifier: String,
    val authData: String, // Base64 encoded
    val additionalData: String?, // Base64 encoded
    val cachedAt: Long,
    val expiresAt: Long
) {
    fun toTokens(): SmartLockTokens {
        return SmartLockTokens(
            brand = SmartLockBrand.valueOf(brand),
            deviceIdentifier = deviceIdentifier,
            authData = Base64.decode(authData, Base64.DEFAULT),
            additionalData = additionalData?.let { Base64.decode(it, Base64.DEFAULT) },
            cachedAt = Date(cachedAt),
            expiresAt = Date(expiresAt)
        )
    }

    companion object {
        fun from(tokens: SmartLockTokens): CachedTokenDTO {
            return CachedTokenDTO(
                brand = tokens.brand.name,
                deviceIdentifier = tokens.deviceIdentifier,
                authData = Base64.encodeToString(tokens.authData, Base64.DEFAULT),
                additionalData = tokens.additionalData?.let {
                    Base64.encodeToString(it, Base64.DEFAULT)
                },
                cachedAt = tokens.cachedAt.time,
                expiresAt = tokens.expiresAt.time
            )
        }
    }
}
