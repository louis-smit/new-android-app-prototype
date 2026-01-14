package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
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
            val adjustedDateString = if (!dateString.endsWith("Z")) {
                dateString + "Z"
            } else {
                dateString
            }
            
            val parsed = ZonedDateTime.parse(adjustedDateString)
            val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            parsed.format(displayFormatter)
        } catch (e: DateTimeParseException) {
            dateString
        }
    }
}
