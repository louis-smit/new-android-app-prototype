package no.solver.solverappdemo.data.models

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val native: String? = null,
    val display: String? = null,
    val label: String? = null,
    val input: Boolean? = null,
    val type: String? = null,
    val validation: String? = null,
    val visible: Boolean? = null,
    val sortorder: Int? = null,
    val merge: String? = null,
    val tailGating: Int? = null
) {
    val commandName: String
        get() = native ?: ""

    val normalizedCommandName: String
        get() = commandName.trim().lowercase()

    val displayName: String
        get() = display ?: label ?: native ?: "Unknown"

    val isVisible: Boolean
        get() = visible ?: true

    val requiresInput: Boolean
        get() = input ?: false

    val sortOrder: Int
        get() = sortorder ?: 0

    val iconName: String?
        get() = when (commandName.lowercase()) {
            "unlock" -> "lock_open"
            "lock" -> "lock"
            "open" -> "door_open"
            "close" -> "door_closed"
            "status", "adminstatus" -> "info"
            "reset", "refresh" -> "refresh"
            "subscribe", "subscription" -> "star"
            "" -> null
            else -> "terminal"
        }

    val isBookPlanyoCommand: Boolean
        get() = normalizedCommandName == "book_planyo"

    val isClientOnlyCommand: Boolean
        get() = normalizedCommandName in setOf("null", "object", "book_planyo")
}

fun String.isBookPlanyoCommand(): Boolean = trim().lowercase() == "book_planyo"

@Serializable
data class CommandLanguage(
    val language: String? = null,
    val commands: List<Command>? = null
) {
    val localeCode: String
        get() = language ?: "en-US"

    val commandList: List<Command>
        get() = commands ?: emptyList()
}

@Serializable
data class CommandMapping(
    val adminCommands: List<CommandLanguage>? = null,
    val userCommands: List<CommandLanguage>? = null,
    val publicCommands: List<CommandLanguage>? = null
) {
    fun getCommands(accessLevel: CommandAccessLevel, locale: String = "en-US"): List<Command> {
        val languageList = when (accessLevel) {
            CommandAccessLevel.ADMIN -> adminCommands
            CommandAccessLevel.USER -> userCommands
            CommandAccessLevel.PUBLIC -> publicCommands
        }

        val matchingLanguage = languageList?.find { it.localeCode == locale }
        return matchingLanguage?.commandList
            ?.filter { it.isVisible }
            ?.sortedBy { it.sortOrder }
            ?: emptyList()
    }

    fun getUserCommands(hasUserAccess: Boolean, locale: String = "en-US"): List<Command> {
        return getCommands(
            if (hasUserAccess) CommandAccessLevel.USER else CommandAccessLevel.PUBLIC,
            locale
        )
    }
}

enum class CommandAccessLevel {
    ADMIN,
    USER,
    PUBLIC
}

@Serializable
data class CommandExecutionRequest(
    val command: String,
    val input: String? = null,
    val location: LocationData? = null
)

@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ExecuteResponse(
    val success: Boolean,
    val objectId: Int? = null,
    val objectName: String? = null,
    val objectType: Int? = null,
    val tenantId: Int? = null,
    val time: String? = null,
    val context: List<ContextItem>? = null
) {
    companion object {
        private val nonUserMessageContextKeys: Set<String> = setOf(
            "paymentrequired",
            "subscriptionrequired",
            "geofenceoverride",
            "vendingtransid",
            "command",
            "serial_number",
            "login_token",
            "advertising_key",
            "deviceidentifier",
            "accessprofile",
            "firmwareversion",
            "valid_from",
            "valid_to",
            "profileexpiration",
            "username"
        )
    }

    fun findContextItem(key: String): ContextItem? {
        return context?.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }

    fun findValueInContext(key: String): String? {
        return findContextItem(key)?.value
    }

    fun hasContextKey(key: String): Boolean {
        return findContextItem(key) != null
    }

    /**
     * Best-effort extraction of the message users should see for a failed command.
     *
     * Older APIs often used `context.error` (`label` + `value`) instead of `context.message`.
     */
    fun userFacingContextMessage(): String? {
        trimmedNonEmpty(findValueInContext("message"))?.let { message ->
            return message
        }

        findContextItem("error")?.let { errorItem ->
            val label = trimmedNonEmpty(errorItem.label)
            val value = trimmedNonEmpty(errorItem.value)

            if (label != null && value != null) {
                return "$label: $value"
            }

            return value ?: label
        }

        val contextItems = context.orEmpty()
        if (contextItems.isEmpty()) {
            return null
        }

        for (item in contextItems) {
            val key = item.key.lowercase(Locale.ROOT)
            if (key in nonUserMessageContextKeys) {
                continue
            }

            val label = trimmedNonEmpty(item.label)
            val value = trimmedNonEmpty(item.value)

            if (label != null && value != null) {
                return "$label: $value"
            }

            if (value != null) {
                return value
            }

            if (label != null) {
                return label
            }
        }

        return null
    }

    private fun trimmedNonEmpty(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}

@Serializable
data class SetStatusRequest(
    val success: Boolean,
    val objectName: String,
    val objectType: Int,
    val context: List<ContextItem>
)

@Serializable
data class ContextItem(
    val key: String,
    val label: String,
    val value: String
)
