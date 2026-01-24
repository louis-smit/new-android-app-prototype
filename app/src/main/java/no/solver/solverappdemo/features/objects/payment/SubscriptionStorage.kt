package no.solver.solverappdemo.features.objects.payment

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.SubscriptionOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pending subscription data for recovery after app backgrounding.
 */
data class PendingSubscription(
    val method: PaymentMethod,
    val option: SubscriptionOption,
    val objectId: Int
)

/**
 * Storage for pending subscription information.
 * Matches iOS SubscriptionStorage for recovering from external payment flows.
 */
@Singleton
class SubscriptionStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "subscription_storage"
        private const val KEY_SUBSCRIPTION_METHOD = "pending_subscription_method"
        private const val KEY_SUBSCRIPTION_OPTION = "pending_subscription_option"
        private const val KEY_OBJECT_ID = "pending_subscription_object_id"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Save pending subscription for recovery after external redirect.
     */
    fun savePendingSubscription(
        method: PaymentMethod,
        subscriptionOption: SubscriptionOption,
        objectId: Int
    ) {
        prefs.edit().apply {
            putString(KEY_SUBSCRIPTION_METHOD, method.value)
            putInt(KEY_OBJECT_ID, objectId)
            try {
                putString(KEY_SUBSCRIPTION_OPTION, json.encodeToString(subscriptionOption))
            } catch (e: Exception) {
                // Ignore encoding errors
            }
            apply()
        }
    }

    /**
     * Get pending subscription if one exists.
     */
    fun getPendingSubscription(): PendingSubscription? {
        val methodValue = prefs.getString(KEY_SUBSCRIPTION_METHOD, null) ?: return null
        val method = PaymentMethod.fromValue(methodValue) ?: return null
        val objectId = prefs.getInt(KEY_OBJECT_ID, -1)
        if (objectId == -1) return null

        val option = try {
            prefs.getString(KEY_SUBSCRIPTION_OPTION, null)?.let {
                json.decodeFromString<SubscriptionOption>(it)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return PendingSubscription(
            method = method,
            option = option,
            objectId = objectId
        )
    }

    /**
     * Clear pending subscription after completion or cancellation.
     */
    fun clearPendingSubscription() {
        prefs.edit().apply {
            remove(KEY_SUBSCRIPTION_METHOD)
            remove(KEY_SUBSCRIPTION_OPTION)
            remove(KEY_OBJECT_ID)
            apply()
        }
    }
}
