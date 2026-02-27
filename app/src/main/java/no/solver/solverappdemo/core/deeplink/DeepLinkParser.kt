package no.solver.solverappdemo.core.deeplink

import android.net.Uri
import no.solver.solverappdemo.data.models.PaymentMethod

/**
 * Parses deep link URLs into structured DeepLink types.
 * 
 * Supported formats:
 * - solverapp://qr/{command}/{tag}
 * - solverapp://{method}/callback?reference={orderId}
 * - https://solver.no/qr/{command}/{tag}
 * - https://solver.no/{command}/{tag}  (NFC tags written by legacy apps)
 */
object DeepLinkParser {
    
    /**
     * Parse a URI into a DeepLink type.
     * @return DeepLink if the URI is recognized, null otherwise.
     */
    fun parse(uri: Uri): DeepLink? {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val segments = uri.pathSegments ?: emptyList()

        if (scheme != "solverapp" && scheme != "https") {
            return null
        }

        // Custom scheme: solverapp://qr/{command}/{tag}
        // host = "qr", pathSegments = [command, tag]
        if (scheme == "solverapp" && host == "qr" && segments.size >= 2) {
            return DeepLink.QrCommand(
                command = segments[0],
                tag = segments[1]
            )
        }

        // Payment callback: solverapp://{method}/callback
        // host = "vipps" | "card" | "stripe", pathSegments = ["callback"]
        if (scheme == "solverapp" && segments.getOrNull(0) == "callback") {
            val method = PaymentMethod.fromValue(host ?: "") ?: return null
            val reference = uri.getQueryParameter("reference") 
                ?: uri.getQueryParameter("orderId")
                ?: uri.lastPathSegment
                ?: return null
            
            return DeepLink.PaymentCallback(
                method = method,
                reference = reference
            )
        }

        // Universal Link: https://solver.no/...
        if (scheme == "https" && host == "solver.no") {
            // With /qr/ prefix: https://solver.no/qr/{command}/{tag}
            if (segments.size >= 3 && segments[0] == "qr") {
                return DeepLink.QrCommand(
                    command = segments[1],
                    tag = segments[2]
                )
            }
            // Without /qr/ prefix: https://solver.no/{command}/{tag} (legacy NFC tags)
            if (segments.size >= 2) {
                return DeepLink.QrCommand(
                    command = segments[0],
                    tag = segments[1]
                )
            }
        }

        return null
    }
    
    /**
     * Check if a URI is a QR command deep link (either scheme).
     */
    fun isQrCommandDeepLink(uri: Uri): Boolean {
        return parse(uri) is DeepLink.QrCommand
    }

    /**
     * Check if a URI is a payment callback deep link.
     */
    fun isPaymentCallbackDeepLink(uri: Uri): Boolean {
        return parse(uri) is DeepLink.PaymentCallback
    }
}
