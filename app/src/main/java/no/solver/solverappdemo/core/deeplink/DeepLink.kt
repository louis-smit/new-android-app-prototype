package no.solver.solverappdemo.core.deeplink

import no.solver.solverappdemo.data.models.PaymentMethod

/**
 * Represents parsed deep link types.
 * 
 * Supported formats:
 * - solverapp://qr/{command}/{tag}
 * - solverapp://{method}/callback?reference={orderId}
 * - https://solver.no/qr/{command}/{tag}
 */
sealed interface DeepLink {
    /**
     * QR/NFC command deep link.
     * @param command The command to execute (e.g., "unlock", "lock", "status")
     * @param tag The tag ID identifying the object
     */
    data class QrCommand(val command: String, val tag: String) : DeepLink

    /**
     * Payment callback deep link.
     * Returned from Vipps/Card external payment flows.
     * @param method The payment method (vipps, card, stripe)
     * @param reference The order ID or reference for status polling
     */
    data class PaymentCallback(val method: PaymentMethod, val reference: String) : DeepLink
}
