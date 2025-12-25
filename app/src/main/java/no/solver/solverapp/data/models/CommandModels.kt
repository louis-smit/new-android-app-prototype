package no.solver.solverapp.data.models

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
}

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
    fun findValueInContext(key: String): String? {
        return context?.find { it.key == key }?.value
    }

    fun hasContextKey(key: String): Boolean {
        return context?.any { it.key == key } ?: false
    }
}

@Serializable
data class ContextItem(
    val key: String,
    val label: String,
    val value: String
)
