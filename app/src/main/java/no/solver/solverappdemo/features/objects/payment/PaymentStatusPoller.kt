package no.solver.solverappdemo.features.objects.payment

import android.util.Log
import kotlinx.coroutines.delay
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.PaymentStatus
import javax.inject.Inject

/**
 * Helper for polling payment status with timeout.
 * Matches iOS PaymentStatusPoller with 500ms interval and 30 attempts (15s timeout).
 */
class PaymentStatusPoller @Inject constructor(
    private val paymentService: PaymentService
) {
    companion object {
        private const val TAG = "PaymentStatusPoller"
        private const val MAX_ATTEMPTS = 30  // 30 attempts at 500ms = 15 seconds
        private const val POLL_INTERVAL_MS = 500L
    }

    /**
     * Polls the payment status until success, failure, or timeout.
     * Returns the final payment status.
     */
    suspend fun pollStatus(
        method: PaymentMethod,
        reference: String,
        isRecurringSubscription: Boolean = false
    ): PaymentStatus {
        for (attempt in 1..MAX_ATTEMPTS) {
            Log.d(TAG, "Polling payment status (attempt $attempt/$MAX_ATTEMPTS)")

            val result = when (method) {
                PaymentMethod.VIPPS -> {
                    if (isRecurringSubscription) {
                        paymentService.checkVippsRecurringPaymentStatus(reference)
                    } else {
                        paymentService.checkVippsPaymentStatus(reference)
                    }
                }
                PaymentMethod.CARD -> paymentService.checkCardPaymentStatus(reference)
                PaymentMethod.STRIPE -> paymentService.checkStripePaymentStatus(reference)
            }

            result.onSuccess { status ->
                // Success cases - stop polling
                if (status == PaymentStatus.CAPTURED || status == PaymentStatus.ACTIVE) {
                    Log.i(TAG, "✅ Payment ${status.value}!")
                    return status
                }

                // User cancelled - stop immediately
                if (status == PaymentStatus.STOPPED || status == PaymentStatus.CANCELLED) {
                    Log.i(TAG, "🟠 Payment ${status.value} by user")
                    return status
                }

                // Actual failure - stop immediately
                if (status == PaymentStatus.FAILED) {
                    Log.w(TAG, "❌ Payment ${status.value}")
                    return status
                }

                // For PENDING, INITIATED, etc. - keep polling
                Log.d(TAG, "Current status: ${status.value}, continuing to poll...")
            }.onFailure { error ->
                Log.e(TAG, "Status check failed: ${error.message}")
            }

            // Wait before next attempt (unless last attempt)
            if (attempt < MAX_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
            }
        }

        Log.w(TAG, "⏱️ Payment status polling timed out")
        return PaymentStatus.UNKNOWN
    }
}
