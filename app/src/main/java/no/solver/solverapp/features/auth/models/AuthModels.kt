package no.solver.solverapp.features.auth.models

import kotlinx.serialization.Serializable
import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val userId: String? = null,
    val userInfo: UserInfo? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtMillis

    val shouldRefresh: Boolean
        get() = System.currentTimeMillis() >= (expiresAtMillis - 60_000)

    val secondsUntilExpiry: Long
        get() = maxOf(0, (expiresAtMillis - System.currentTimeMillis()) / 1000)
}

@Serializable
data class UserInfo(
    val displayName: String? = null,
    val email: String? = null,
    val userName: String? = null,
    val oid: String? = null,
    val givenName: String? = null,
    val familyName: String? = null
) {
    val effectiveDisplayName: String
        get() = displayName ?: userName ?: email ?: "Unknown User"

    val fullName: String?
        get() = if (givenName != null && familyName != null) "$givenName $familyName" else displayName

    val initials: String
        get() {
            if (givenName != null && familyName != null) {
                return "${givenName.first()}${familyName.first()}".uppercase()
            }
            displayName?.let { name ->
                if (name.isNotEmpty()) {
                    val parts = name.split(" ")
                    return if (parts.size >= 2) {
                        "${parts[0].first()}${parts[1].first()}".uppercase()
                    } else {
                        name.take(2).uppercase()
                    }
                }
            }
            email?.let { if (it.isNotEmpty()) return it.take(2).uppercase() }
            return "??"
        }
}

data class Session(
    val id: String,
    val provider: AuthProvider,
    val environment: AuthEnvironment,
    val tokens: AuthTokens,
    val isActive: Boolean = true
) {
    val displayName: String
        get() = tokens.userInfo?.effectiveDisplayName ?: "Unknown"

    val email: String?
        get() = tokens.userInfo?.email
}
