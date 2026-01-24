package no.solver.solverappdemo.features.objects.payment

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.PaymentResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pending payment data for recovery after app backgrounding.
 */
data class PendingPayment(
    val method: PaymentMethod,
    val response: PaymentResponse?,
    val objectId: Int,
    val command: String
)

/**
 * Storage for pending payment information.
 * Matches iOS PaymentStorage for recovering from external payment flows.
 */
@Singleton
class PaymentStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "payment_storage"
        private const val KEY_PAYMENT_METHOD = "pending_payment_method"
        private const val KEY_PAYMENT_RESPONSE = "pending_payment_response"
        private const val KEY_OBJECT_ID = "pending_payment_object_id"
        private const val KEY_COMMAND = "pending_payment_command"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Save pending payment for recovery after external redirect.
     */
    fun savePendingPayment(
        method: PaymentMethod,
        response: PaymentResponse,
        objectId: Int,
        command: String
    ) {
        prefs.edit().apply {
            putString(KEY_PAYMENT_METHOD, method.value)
            putInt(KEY_OBJECT_ID, objectId)
            putString(KEY_COMMAND, command)
            try {
                putString(KEY_PAYMENT_RESPONSE, json.encodeToString(response))
            } catch (e: Exception) {
                // Ignore encoding errors
            }
            apply()
        }
    }

    /**
     * Get pending payment if one exists.
     */
    fun getPendingPayment(): PendingPayment? {
        val methodValue = prefs.getString(KEY_PAYMENT_METHOD, null) ?: return null
        val method = PaymentMethod.fromValue(methodValue) ?: return null
        val objectId = prefs.getInt(KEY_OBJECT_ID, -1)
        if (objectId == -1) return null
        val command = prefs.getString(KEY_COMMAND, null) ?: return null

        val response = try {
            prefs.getString(KEY_PAYMENT_RESPONSE, null)?.let {
                json.decodeFromString<PaymentResponse>(it)
            }
        } catch (e: Exception) {
            null
        }

        return PendingPayment(
            method = method,
            response = response,
            objectId = objectId,
            command = command
        )
    }

    /**
     * Clear pending payment after completion or cancellation.
     */
    fun clearPendingPayment() {
        prefs.edit().apply {
            remove(KEY_PAYMENT_METHOD)
            remove(KEY_PAYMENT_RESPONSE)
            remove(KEY_OBJECT_ID)
            remove(KEY_COMMAND)
            apply()
        }
    }
}
