package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable

/**
 * Payment method types supported by the app.
 * Matches iOS PaymentMethod enum.
 */
enum class PaymentMethod(val value: String, val displayName: String) {
    VIPPS("vipps", "Vipps"),
    CARD("card", "Card Payment"),
    STRIPE("stripe", "Credit Card");

    companion object {
        fun fromValue(value: String): PaymentMethod? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * Available payment methods for an object, derived from VippsCredentials.
 * Matches iOS AvailablePaymentMethods.
 */
data class AvailablePaymentMethods(
    val hasVipps: Boolean,
    val hasCard: Boolean,
    val hasStripe: Boolean
) {
    val none: Boolean
        get() = !hasVipps && !hasCard && !hasStripe

    val methods: List<PaymentMethod>
        get() = buildList {
            if (hasVipps) add(PaymentMethod.VIPPS)
            if (hasCard) add(PaymentMethod.CARD)
            if (hasStripe) add(PaymentMethod.STRIPE)
        }

    companion object {
        fun from(vippsCredentials: VippsCredentials?): AvailablePaymentMethods {
            if (vippsCredentials == null) {
                return AvailablePaymentMethods(hasVipps = false, hasCard = false, hasStripe = false)
            }

            var hasVipps = false
            var hasCard = false
            var hasStripe = false

            // Check paymentMethods array
            vippsCredentials.paymentMethods?.let { methods ->
                hasVipps = methods.contains("vipps")
                hasCard = methods.contains("card")
            }

            // Check paymentProviders array for Stripe
            vippsCredentials.paymentProviders?.let { providers ->
                hasStripe = providers.contains("stripe")
            }

            return AvailablePaymentMethods(hasVipps = hasVipps, hasCard = hasCard, hasStripe = hasStripe)
        }
    }
}

/**
 * VippsCredentials model from API response.
 * Contains payment configuration for an object.
 */
@Serializable
data class VippsCredentials(
    val msn: String? = null,
    val subkey: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val production: Boolean? = null,
    val transactionText: String? = null,
    val stripeAccount: String? = null,
    val stripeSecretKey: String? = null,
    val stripePublishableKey: String? = null,
    val paymentMethods: List<String>? = null,
    val paymentProviders: List<String>? = null
)

/**
 * Request body for initiating a payment.
 */
@Serializable
data class InitiatePaymentRequest(
    val command: String,
    val subscriptionId: Int? = null,
    val vendingTransId: Int? = null
)

/**
 * Response from payment initiation API.
 */
@Serializable
data class PaymentResponse(
    val orderId: String? = null,
    val url: String? = null,
    val clientSecret: String? = null,
    val publishableKey: String? = null,
    val redirectUrl: String? = null
)

/**
 * Vipps order status response.
 */
@Serializable
data class VippsOrder(
    val orderId: String? = null,
    val orderTime: String? = null,
    val merchantSerialNumber: String? = null,
    val objectId: Int? = null,
    val userId: Int? = null,
    val amount: Double? = null,
    val command: String? = null,
    val status: String? = null,
    val objectSubscriptionId: Int? = null,
    val pos: String? = null
)

/**
 * Stripe order status response.
 */
@Serializable
data class StripeOrder(
    val stripeOrderId: Int? = null,
    val reference: String? = null,
    val orderTime: String? = null,
    val merchantSerialNumber: String? = null,
    val amount: Int? = null,
    val command: String? = null,
    val status: String? = null,
    val orderText: String? = null,
    val userId: Int? = null,
    val objectId: Int? = null,
    val objectSubscriptionId: Int? = null
)

/**
 * Payment status enum.
 * Matches iOS PaymentStatus.
 */
enum class PaymentStatus(val value: String) {
    CAPTURED("CAPTURED"),
    ACTIVE("ACTIVE"),
    PENDING("PENDING"),
    INITIATED("INITIATED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    STOPPED("STOPPED"),
    UNKNOWN("UNKNOWN");

    val isSuccess: Boolean
        get() = this == CAPTURED || this == ACTIVE

    val displayName: String
        get() = when (this) {
            STOPPED -> "Cancelled by user"
            PENDING -> "Processing"
            else -> value.lowercase().replaceFirstChar { it.uppercase() }
        }

    companion object {
        fun fromValue(value: String?): PaymentStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * Result of a payment operation.
 */
sealed class PaymentResult {
    data object Success : PaymentResult()
    data class Failure(val message: String) : PaymentResult()
    data object Cancelled : PaymentResult()
}
