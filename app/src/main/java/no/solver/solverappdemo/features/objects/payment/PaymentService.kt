package no.solver.solverappdemo.features.objects.payment

import android.util.Log
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.models.InitiatePaymentRequest
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.PaymentResponse
import no.solver.solverappdemo.data.models.PaymentStatus
import no.solver.solverappdemo.features.auth.services.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling payment API integration.
 * Matches iOS PaymentService.
 */
@Singleton
class PaymentService @Inject constructor(
    private val apiClientManager: ApiClientManager,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "PaymentService"
        private const val CALLBACK_SCHEME = "solverapp"
    }

    private suspend fun getApiService(): no.solver.solverappdemo.data.api.SolverApiService {
        val session = sessionManager.getCurrentSession()
            ?: throw IllegalStateException("No active session")
        return apiClientManager.getApiService(session.environment, session.provider)
    }

    /**
     * Initiates a payment for the given method, object, and command.
     */
    suspend fun initiatePayment(
        method: PaymentMethod,
        objectId: Int,
        command: String,
        vendingTransId: Int? = null
    ): Result<PaymentResponse> {
        val callbackUri = "$CALLBACK_SCHEME://${method.value}/callback"
        val request = InitiatePaymentRequest(
            command = command,
            vendingTransId = vendingTransId
        )

        Log.i(TAG, "💳 Initiating ${method.value} payment for object $objectId, command: $command")

        return try {
            val apiService = getApiService()
            val response = when (method) {
                PaymentMethod.VIPPS -> apiService.initiateVippsPayment(objectId, callbackUri, request)
                PaymentMethod.CARD -> apiService.initiateCardPayment(objectId, callbackUri, request)
                PaymentMethod.STRIPE -> apiService.initiateStripePayment(objectId, callbackUri, request)
            }

            if (response.isSuccessful) {
                response.body()?.let { paymentResponse ->
                    Log.i(TAG, "✅ Payment initiated successfully. OrderID: ${paymentResponse.orderId}")
                    Result.success(paymentResponse)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val errorMessage = "Payment initiation failed: ${response.code()} ${response.message()}"
                Log.e(TAG, "❌ $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Payment initiation error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks the status of a Vipps payment (one-time).
     */
    suspend fun checkVippsPaymentStatus(orderId: String): Result<PaymentStatus> {
        return try {
            val apiService = getApiService()
            val response = apiService.getVippsPaymentStatus(orderId)
            if (response.isSuccessful) {
                val status = PaymentStatus.fromValue(response.body()?.status)
                Log.i(TAG, "Vipps payment $orderId status: ${status.value}")
                Result.success(status)
            } else {
                Result.failure(Exception("Failed to check status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Vipps payment status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks the status of a Vipps recurring payment (subscriptionTypeId === 3).
     */
    suspend fun checkVippsRecurringPaymentStatus(orderId: String): Result<PaymentStatus> {
        return try {
            val apiService = getApiService()
            val response = apiService.getVippsRecurringPaymentStatus(orderId)
            if (response.isSuccessful) {
                val status = PaymentStatus.fromValue(response.body()?.status)
                Log.i(TAG, "Vipps recurring payment $orderId status: ${status.value}")
                Result.success(status)
            } else {
                Result.failure(Exception("Failed to check status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Vipps recurring payment status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks the status of a Card payment.
     */
    suspend fun checkCardPaymentStatus(reference: String): Result<PaymentStatus> {
        return try {
            val apiService = getApiService()
            val response = apiService.getCardPaymentStatus(reference)
            if (response.isSuccessful) {
                val status = PaymentStatus.fromValue(response.body()?.status)
                Log.i(TAG, "Card payment $reference status: ${status.value}")
                Result.success(status)
            } else {
                Result.failure(Exception("Failed to check status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Card payment status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Checks the status of a Stripe payment.
     */
    suspend fun checkStripePaymentStatus(reference: String): Result<PaymentStatus> {
        return try {
            val apiService = getApiService()
            val response = apiService.getStripePaymentStatus(reference)
            if (response.isSuccessful) {
                val status = PaymentStatus.fromValue(response.body()?.status)
                Log.i(TAG, "Stripe payment $reference status: ${status.value}")
                Result.success(status)
            } else {
                Result.failure(Exception("Failed to check status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Stripe payment status: ${e.message}", e)
            Result.failure(e)
        }
    }
}
