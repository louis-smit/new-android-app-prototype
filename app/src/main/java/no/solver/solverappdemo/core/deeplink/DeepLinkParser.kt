package no.solver.solverappdemo.core.deeplink

import android.net.Uri

/**
 * Parses deep link URLs into structured DeepLink types.
 * 
 * Supported formats:
 * - solverapp://qr/{command}/{tag}
 * - https://solver.no/qr/{command}/{tag}
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

        // Custom scheme: solverapp://qr/{command}/{tag}
        // host = "qr", pathSegments = [command, tag]
        if (scheme == "solverapp" && host == "qr" && segments.size >= 2) {
            return DeepLink.QrCommand(
                command = segments[0],
                tag = segments[1]
            )
        }

        // Universal Link: https://solver.no/qr/{command}/{tag}
        // host = "solver.no", pathSegments = ["qr", command, tag]
        if (scheme == "https" && host == "solver.no" && segments.size >= 3 && segments[0] == "qr") {
            return DeepLink.QrCommand(
                command = segments[1],
                tag = segments[2]
            )
        }

        return null
    }
    
    /**
     * Check if a URI is a QR command deep link (either scheme).
     */
    fun isQrCommandDeepLink(uri: Uri): Boolean {
        return parse(uri) is DeepLink.QrCommand
    }
}
