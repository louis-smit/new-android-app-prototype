package no.solver.solverappdemo.features.objects.result

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import no.solver.solverappdemo.data.models.Command
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.PaymentStatus
import no.solver.solverappdemo.data.models.SolverObject

enum class ActionResultKind(val displayName: String) {
    COMMAND("Command"),
    STATUS("Status"),
    PAYMENT("Payment"),
    SUBSCRIPTION("Subscription"),
    GEOFENCE("Geofence")
}

enum class ActionResultState {
    PROCESSING,
    SUCCESS,
    FAILURE,
    CANCELLED;

    companion object {
        fun fromPaymentStatus(status: PaymentStatus): ActionResultState {
            return when (status) {
                PaymentStatus.CAPTURED,
                PaymentStatus.ACTIVE -> SUCCESS

                PaymentStatus.CANCELLED,
                PaymentStatus.STOPPED -> CANCELLED

                PaymentStatus.FAILED -> FAILURE
                PaymentStatus.PENDING,
                PaymentStatus.INITIATED,
                PaymentStatus.UNKNOWN -> PROCESSING
            }
        }
    }
}

data class ActionResultDetail(
    val label: String,
    val value: String
)

data class ActionResultPresentation(
    val id: String = UUID.randomUUID().toString(),
    val kind: ActionResultKind,
    val state: ActionResultState,
    val title: String,
    val message: String,
    val timestampText: String? = null,
    val details: List<ActionResultDetail> = emptyList(),
    val correlationKey: String? = null,
    val secondaryActionTitle: String? = null,
    val secondaryAction: (() -> Unit)? = null
) {
    companion object {
        fun commandExecution(
            response: ExecuteResponse,
            command: Command,
            solverObject: SolverObject
        ): ActionResultPresentation {
            val commandName = command.commandName.lowercase(Locale.getDefault())
            val kind = if (commandName == "status" || commandName == "adminstatus") {
                ActionResultKind.STATUS
            } else {
                ActionResultKind.COMMAND
            }

            val state = if (response.success) ActionResultState.SUCCESS else ActionResultState.FAILURE
            val title = "${command.displayName} ${if (response.success) "Succeeded" else "Failed"}"
            val fallbackMessage = if (response.success) {
                "${command.displayName} completed for ${solverObject.name}."
            } else {
                "${command.displayName} could not be completed for ${solverObject.name}."
            }

            return ActionResultPresentation(
                kind = kind,
                state = state,
                title = title,
                message = response.userFacingContextMessage() ?: fallbackMessage,
                timestampText = localizedTimestampText(response.time)
            )
        }

        fun currentTimestampText(date: Date = Date()): String {
            return DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            ).format(date)
        }

        private fun localizedTimestampText(rawValue: String?): String? {
            if (rawValue.isNullOrBlank()) {
                return null
            }

            val date = parseTimestamp(rawValue)
            return if (date != null) {
                currentTimestampText(date)
            } else {
                rawValue
            }
        }

        private fun parseTimestamp(rawValue: String): Date? {
            val trimmed = rawValue.trim()

            try {
                return Date.from(Instant.parse(trimmed))
            } catch (_: DateTimeParseException) {
                // Fall back to non-ISO variants below.
            }

            val utcFormats = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
            )

            for (pattern in utcFormats) {
                runCatching {
                    val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                        isLenient = false
                    }
                    formatter.parse(trimmed)
                }.getOrNull()?.let { parsed ->
                    return parsed
                }
            }

            return null
        }
    }

    fun ensuringCompletionTimestamp(): ActionResultPresentation {
        if (timestampText != null || state == ActionResultState.PROCESSING) {
            return this
        }

        return copy(timestampText = currentTimestampText())
    }
}
