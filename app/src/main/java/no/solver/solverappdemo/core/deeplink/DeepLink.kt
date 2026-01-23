package no.solver.solverappdemo.core.deeplink

/**
 * Represents parsed deep link types.
 * Format: solverapp://qr/{command}/{tag}
 * Example: solverapp://qr/unlock/abc-123-def
 * 
 * Also supports Universal Links:
 * https://solver.no/qr/{command}/{tag}
 */
sealed interface DeepLink {
    /**
     * QR/NFC command deep link.
     * @param command The command to execute (e.g., "unlock", "lock", "status")
     * @param tag The tag ID identifying the object
     */
    data class QrCommand(val command: String, val tag: String) : DeepLink
}
