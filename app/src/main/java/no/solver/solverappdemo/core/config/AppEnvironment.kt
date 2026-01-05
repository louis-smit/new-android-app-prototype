package no.solver.solverappdemo.core.config

import no.solver.solverappdemo.BuildConfig

enum class AppEnvironment {
    STAGING,
    PRODUCTION;

    companion object {
        val current: AppEnvironment
            get() = if (BuildConfig.IS_PRODUCTION) PRODUCTION else STAGING
    }
}

enum class AuthEnvironment {
    SOLVER,
    ZOHM;

    val clientID: String
        get() = when (this) {
            SOLVER -> BuildConfig.SOLVER_MS_CLIENT_ID
            ZOHM -> BuildConfig.ZOHM_MS_CLIENT_ID
        }

    val apiScope: String
        get() = when (this) {
            SOLVER -> "https://solver.no/O365User/user_impersonation"
            ZOHM -> "https://zohm.no/Zohm365User/user_impersonation"
        }

    val displayName: String
        get() = when (this) {
            SOLVER -> "Solver"
            ZOHM -> "Zohm"
        }
}

enum class AuthProvider {
    MICROSOFT,
    VIPPS,
    MOBILE
}

data class APIConfiguration(
    val baseURL: String,
    val timeoutSeconds: Long = 30
) {
    companion object {
        fun current(
            environment: AuthEnvironment,
            mode: AppEnvironment = AppEnvironment.current,
            provider: AuthProvider = AuthProvider.MICROSOFT
        ): APIConfiguration {
            val baseURL = when (environment to mode) {
                AuthEnvironment.SOLVER to AppEnvironment.STAGING -> {
                    if (provider == AuthProvider.VIPPS || provider == AuthProvider.MOBILE) {
                        "https://api-demo.solver.no"
                    } else {
                        "https://api365-demo.solver.no"
                    }
                }
                AuthEnvironment.SOLVER to AppEnvironment.PRODUCTION -> {
                    if (provider == AuthProvider.VIPPS || provider == AuthProvider.MOBILE) {
                        "https://api.solver.no"
                    } else {
                        "https://api365.solver.no"
                    }
                }
                AuthEnvironment.ZOHM to AppEnvironment.STAGING -> {
                    if (provider == AuthProvider.VIPPS || provider == AuthProvider.MOBILE) {
                        "https://api-demo.zohm.no"
                    } else {
                        "https://api365-demo.zohm.no"
                    }
                }
                AuthEnvironment.ZOHM to AppEnvironment.PRODUCTION -> {
                    if (provider == AuthProvider.VIPPS || provider == AuthProvider.MOBILE) {
                        "https://api.zohm.no"
                    } else {
                        "https://api365.zohm.no"
                    }
                }
                else -> "https://api-demo.solver.no"
            }

            return APIConfiguration(baseURL = baseURL)
        }
    }
}

data class AuthConfiguration(
    val clientID: String,
    val scopes: List<String>,
    val redirectURI: String,
    val authorityURL: String
) {
    companion object {
        fun current(environment: AuthEnvironment): AuthConfiguration {
            // Note: Android MSAL rejects OIDC scopes (openid, profile, email, offline_access)
            // combined with custom API scopes. Use only the API scope for token acquisition.
            // MSAL automatically includes OIDC scopes when needed.
            val scopes = listOf(environment.apiScope)

            return AuthConfiguration(
                clientID = environment.clientID,
                scopes = scopes,
                redirectURI = "msauth://no.solver.solverappdemo/ga0RGNYHvNM5d0SLGQfpQWAPGJ8%3D",
                authorityURL = "https://login.microsoftonline.com/common"
            )
        }
    }
}
