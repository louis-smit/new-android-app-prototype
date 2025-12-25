package no.solver.solverapp.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import no.solver.solverapp.core.config.APIConfiguration
import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider
import no.solver.solverapp.core.network.AuthInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val apiCache = mutableMapOf<String, SolverApiService>()

    fun getApiService(
        environment: AuthEnvironment,
        provider: AuthProvider
    ): SolverApiService {
        val config = APIConfiguration.current(environment, provider = provider)
        val cacheKey = "${config.baseURL}_${provider.name}"

        return apiCache.getOrPut(cacheKey) {
            createApiService(config.baseURL)
        }
    }

    private fun createApiService(baseUrl: String): SolverApiService {
        val contentType = "application/json".toMediaType()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(SolverApiService::class.java)
    }
}
