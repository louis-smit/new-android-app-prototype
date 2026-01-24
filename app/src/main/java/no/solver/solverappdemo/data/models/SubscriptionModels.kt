package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable

/**
 * Subscription type enum.
 * Matches iOS SubscriptionType.
 */
enum class SubscriptionType(val id: Int) {
    REGULAR(1),
    RECURRING(3);

    val displayName: String
        get() = when (this) {
            REGULAR -> "Regular Subscription"
            RECURRING -> "Recurring Subscription"
        }

    val isRecurring: Boolean
        get() = this == RECURRING

    companion object {
        fun fromId(id: Int): SubscriptionType? {
            return entries.find { it.id == id }
        }
    }
}

/**
 * Subscription option available for an object.
 * Matches iOS SubscriptionOption.
 */
@Serializable
data class SubscriptionOption(
    val objectSubscriptionId: Int,
    val subscriptionTypeId: Int,
    val description: String? = null,
    val amount: Double? = null
) {
    val subscriptionType: SubscriptionType?
        get() = SubscriptionType.fromId(subscriptionTypeId)

    val displayTitle: String
        get() = description ?: "Subscription Option"

    val displayPrice: String
        get() = amount?.let { String.format("%.2f kr", it) } ?: "N/A"
}

/**
 * Request body for initiating a subscription payment.
 */
@Serializable
data class InitiateSubscriptionRequest(
    val command: String,
    val subscriptionId: Int
) {
    companion object {
        fun create(subscriptionId: Int, subscriptionType: SubscriptionType): InitiateSubscriptionRequest {
            // Recurring subscriptions use "recurring" command, others use "subscription"
            val command = if (subscriptionType.isRecurring) "recurring" else "subscription"
            return InitiateSubscriptionRequest(command = command, subscriptionId = subscriptionId)
        }
    }
}

/**
 * Response from subscription payment initiation API.
 */
@Serializable
data class SubscriptionPaymentResponse(
    val orderId: String? = null,
    val url: String? = null,
    val redirectUrl: String? = null,
    val vippsConfirmationUrl: String? = null,
    val clientSecret: String? = null,
    val publishableKey: String? = null
) {
    /**
     * Get the appropriate redirect URL based on subscription type.
     */
    fun getRedirectUrl(subscriptionType: SubscriptionType): String? {
        return if (subscriptionType.isRecurring) {
            vippsConfirmationUrl ?: redirectUrl
        } else {
            redirectUrl ?: url
        }
    }
}
