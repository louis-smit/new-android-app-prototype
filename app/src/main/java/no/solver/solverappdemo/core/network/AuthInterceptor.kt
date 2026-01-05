package no.solver.solverappdemo.core.network

import android.util.Log
import no.solver.solverappdemo.core.storage.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val accessToken = tokenStorage.getAccessToken()
        
        val request = if (accessToken != null) {
            val isExpired = tokenStorage.isTokenExpired()
            if (isExpired) {
                Log.d(TAG, "⚠️ Token is expired, Authenticator will handle refresh on 401")
            }
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            Log.w(TAG, "No access token available for request to ${originalRequest.url.encodedPath}")
            originalRequest
        }

        return chain.proceed(request)
    }
}
