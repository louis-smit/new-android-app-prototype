package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Serializable
data class ObjectLog(
    val objectLogId: Int? = null,
    val createdAt: String? = null,
    val source: String? = null,
    val action: String? = null,
    val objectId: Int? = null,
    val status: String? = null,
    val userId: Int? = null,
    val objectName: String? = null,
    val userName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mobLatitude: Double? = null,
    val mobLongitude: Double? = null,
    val details: String? = null,
    val transId: Int? = null
) {
    val id: Int get() = objectLogId ?: 0

    val formattedCreatedAt: String
        get() {
            val dateString = createdAt ?: return "N/A"
            return formatDate(dateString)
        }

    private fun formatDate(dateString: String): String {
        return try {
            val parsed = parseBackendTimestamp(dateString) ?: return dateString
            val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            parsed.atZone(ZoneId.systemDefault()).format(displayFormatter)
        } catch (e: DateTimeParseException) {
            dateString
        }
    }

    private fun parseBackendTimestamp(dateString: String): Instant? {
        return try {
            if (dateString.hasExplicitOffset()) {
                OffsetDateTime.parse(dateString).toInstant()
            } else {
                // Backend sends UTC timestamps without timezone suffix. Treat as UTC.
                LocalDateTime.parse(dateString).toInstant(ZoneOffset.UTC)
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun String.hasExplicitOffset(): Boolean {
        return endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(this)
    }
}
