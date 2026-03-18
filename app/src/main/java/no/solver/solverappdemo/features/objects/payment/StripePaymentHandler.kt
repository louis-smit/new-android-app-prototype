package no.solver.solverappdemo.features.objects.payment

import android.app.Activity
import android.util.Log
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import no.solver.solverappdemo.data.models.PaymentResponse
import no.solver.solverappdemo.data.models.PaymentResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Stripe payment sheet presentation and processing.
 * Matches iOS StripePaymentHandler.
 */
@Singleton
class StripePaymentHandler @Inject constructor() {
    companion object {
        private const val TAG = "StripePaymentHandler"
        private const val MERCHANT_DISPLAY_NAME = "Solver"
    }

    private var paymentSheet: PaymentSheet? = null
    private var pendingCallback: ((PaymentResult) -> Unit)? = null
    private var activity: Activity? = null
    private var isPresentingSheet: Boolean = false

    /**
     * Initialize PaymentSheet for an activity.
     * Must be called in onCreate before presenting.
     */
    fun initialize(activity: Activity) {
        this.activity = activity
        paymentSheet = PaymentSheet(activity as androidx.activity.ComponentActivity) { result ->
            handlePaymentResult(result)
        }
    }

    /**
     * Present the Stripe payment sheet with the given payment response.
     */
    fun presentPaymentSheet(
        paymentResponse: PaymentResponse,
        callback: (PaymentResult) -> Unit
    ) {
        val clientSecret = paymentResponse.clientSecret
        if (clientSecret == null) {
            Log.e(TAG, "No client secret in payment response")
            callback(PaymentResult.Failure("No payment secret provided"))
            return
        }

        val publishableKey = paymentResponse.publishableKey
        if (publishableKey == null) {
            Log.e(TAG, "No publishable key in payment response")
            callback(PaymentResult.Failure("No publishable key provided"))
            return
        }

        val sheet = paymentSheet
        if (sheet == null) {
            Log.e(TAG, "PaymentSheet not initialized. Call initialize() first.")
            callback(PaymentResult.Failure("Payment system not ready"))
            return
        }

        if (!tryAcquirePresentationLock()) {
            Log.w(TAG, "Ignoring Stripe presentation request because another sheet is already active")
            callback(PaymentResult.Failure("Another payment is already in progress"))
            return
        }

        pendingCallback = callback

        // Initialize Stripe with publishable key before presenting
        activity?.let { PaymentConfiguration.init(it, publishableKey) }

        // Configure payment sheet
        val configuration = PaymentSheet.Configuration(
            merchantDisplayName = MERCHANT_DISPLAY_NAME,
            allowsDelayedPaymentMethods = false
        )

        Log.i(TAG, "Presenting Stripe payment sheet")

        // Present payment sheet
        try {
            sheet.presentWithPaymentIntent(
                paymentIntentClientSecret = clientSecret,
                configuration = configuration
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to present Stripe payment sheet", error)
            completePresentation(PaymentResult.Failure(error.localizedMessage ?: "Could not open Stripe payment"))
        }
    }

    /**
     * Handle the payment sheet result.
     */
    private fun handlePaymentResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> {
                Log.i(TAG, "✅ Stripe payment completed successfully")
                completePresentation(PaymentResult.Success)
            }
            is PaymentSheetResult.Failed -> {
                val message = result.error.localizedMessage ?: "Payment failed"
                Log.e(TAG, "❌ Stripe payment failed: $message")
                completePresentation(PaymentResult.Failure(message))
            }
            is PaymentSheetResult.Canceled -> {
                Log.i(TAG, "Payment cancelled by user")
                completePresentation(PaymentResult.Cancelled)
            }
        }
    }

    /**
     * Configure Stripe with publishable key.
     * Called automatically by presentPaymentSheet, but can be called manually for initialization.
     */
    fun configureStripe(activity: Activity, publishableKey: String) {
        PaymentConfiguration.init(activity, publishableKey)
    }

    @Synchronized
    private fun tryAcquirePresentationLock(): Boolean {
        if (isPresentingSheet) {
            return false
        }

        isPresentingSheet = true
        return true
    }

    @Synchronized
    private fun releasePresentationLock(): ((PaymentResult) -> Unit)? {
        val callback = pendingCallback
        pendingCallback = null
        isPresentingSheet = false
        return callback
    }

    private fun completePresentation(result: PaymentResult) {
        releasePresentationLock()?.invoke(result)
    }
}
