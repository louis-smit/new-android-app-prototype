package no.solver.solverappdemo.features.objects.payment

import android.util.Log
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.InitiateSubscriptionRequest
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.SubscriptionOption
import no.solver.solverappdemo.data.models.SubscriptionPaymentResponse
import no.solver.solverappdemo.data.models.SubscriptionType
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling subscription API integration.
 * Matches iOS SubscriptionService.
 */
@Singleton
class SubscriptionService @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "SubscriptionService"
        private const val CALLBACK_SCHEME = "solverapp"
    }

    private suspend fun getApiService(): no.solver.solverappdemo.data.api.SolverApiService {
        val session = sessionManager.getCurrentSession()
            ?: throw IllegalStateException("No active session")
        return apiClientManager.getApiService(session.environment, session.provider)
    }

    /**
     * Fetches available subscription options for an object.
     */
    suspend fun fetchSubscriptionOptions(objectId: Int): Result<List<SubscriptionOption>> {
        Log.i(TAG, "🔵 Fetching subscription options for object $objectId")

        return try {
            val apiService = getApiService()
            val response = apiService.getSubscriptionOptions(objectId)

            if (response.isSuccessful) {
                val options = response.body() ?: emptyList()
                Log.i(TAG, "✅ Fetched ${options.size} subscription options")
                Result.success(options)
            } else {
                val errorMessage = "Failed to fetch subscription options: ${response.code()} ${response.message()}"
                Log.e(TAG, "❌ $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching subscription options: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Initiates a subscription payment.
     */
    suspend fun initiateSubscription(
        method: PaymentMethod,
        objectId: Int,
        subscriptionOption: SubscriptionOption
    ): Result<SubscriptionPaymentResponse> {
        val subscriptionType = subscriptionOption.subscriptionType
            ?: return Result.failure(Exception("Unknown subscription type ID: ${subscriptionOption.subscriptionTypeId}"))

        val callbackUri = "$CALLBACK_SCHEME://${method.value}/callback"
        val request = InitiateSubscriptionRequest.create(
            subscriptionId = subscriptionOption.objectSubscriptionId,
            subscriptionType = subscriptionType
        )

        Log.i(TAG, "💳 Initiating ${method.value} subscription payment")
        Log.i(TAG, "   → Type: ${subscriptionType.displayName}")
        Log.i(TAG, "   → Option: ${subscriptionOption.displayTitle}")
        Log.i(TAG, "   → Amount: ${subscriptionOption.displayPrice}")

        return try {
            val apiService = getApiService()
            val response = when (method) {
                PaymentMethod.VIPPS -> {
                    if (subscriptionType.isRecurring) {
                        // Recurring Vipps subscriptions use RecurringPayment endpoint
                        apiService.initiateVippsRecurringPayment(objectId, callbackUri, request)
                    } else {
                        // Regular Vipps subscriptions use v2/InitiatePayment
                        apiService.initiateVippsSubscription(objectId, callbackUri, request)
                    }
                }
                PaymentMethod.CARD -> apiService.initiateCardSubscription(objectId, callbackUri, request)
                PaymentMethod.STRIPE -> apiService.initiateStripeSubscription(objectId, callbackUri, request)
            }

            if (response.isSuccessful) {
                response.body()?.let { paymentResponse ->
                    Log.i(TAG, "✅ Subscription payment initiated")
                    Result.success(paymentResponse)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMessage = "Subscription initiation failed: ${response.code()} ${response.message()}"
                Log.e(TAG, "❌ $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Subscription initiation error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
