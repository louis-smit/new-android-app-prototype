package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SolverObject(
    val id: Int,
    val name: String,
    val objectTypeId: Int,
    val status: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val active: Boolean = true,
    val online: Boolean = true,
    val state: Int? = null,
    val tenantName: String? = null,
    val commandMap: CommandMapping? = null,
    val userAccess: Boolean? = null,
    val hasSubscription: Boolean? = null,
    val information: ObjectInformation? = null,
    val vippsCredentials: VippsCredentials? = null
) {
    val isAvailable: Boolean
        get() = active && online

    val onlineState: OnlineState
        get() = when (state) {
            0 -> OnlineState.UNKNOWN
            1 -> OnlineState.ONLINE
            2 -> OnlineState.OFFLINE
            3 -> OnlineState.NOT_APPLICABLE
            else -> OnlineState.UNKNOWN
        }

    val statusColor: ObjectStatusColor
        get() = ObjectStatusColor.fromStatus(status, online, active)

    /**
     * Returns true if the object has a valid location.
     * Treats 0,0 coordinates as no location (API returns 0,0 for objects without location).
     */
    val hasValidLocation: Boolean
        get() = latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)

    fun getCommands(locale: String = "en-US"): List<Command> {
        val hasAccess = userAccess ?: false
        return commandMap?.getUserCommands(hasAccess, locale) ?: emptyList()
    }
}

enum class OnlineState {
    UNKNOWN,
    ONLINE,
    OFFLINE,
    NOT_APPLICABLE
}

enum class ObjectStatusColor {
    AVAILABLE,
    LOCKED,
    OFFLINE,
    RESTRICTED,
    UNKNOWN;

    companion object {
        fun fromStatus(status: String, isOnline: Boolean, isActive: Boolean): ObjectStatusColor {
            if (!isActive || !isOnline) return OFFLINE

            return when (status.lowercase()) {
                "available", "open", "unlocked" -> AVAILABLE
                "locked", "closed" -> LOCKED
                "restricted", "secured" -> RESTRICTED
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
data class SolverObjectDTO(
    val objectId: Int? = null,
    val name: String? = null,
    val objectTypeId: Int? = null,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val active: Boolean? = null,
    val state: Int? = null,
    val online: Boolean? = null,
    val tenantName: String? = null,
    val commandMap: CommandMapping? = null,
    val userAccess: Boolean? = null,
    val hasSubscription: Boolean? = null,
    val information: ObjectInformation? = null,
    val vippsCredentials: VippsCredentials? = null
) {
    fun toDomainModel(): SolverObject {
        val isOnline = state?.let { it == 1 } ?: (online ?: true)

        return SolverObject(
            id = objectId ?: 0,
            name = name ?: "Unknown Object",
            objectTypeId = objectTypeId ?: 0,
            status = status ?: "Unknown",
            latitude = latitude,
            longitude = longitude,
            active = active ?: true,
            online = isOnline,
            state = state,
            tenantName = tenantName,
            commandMap = commandMap,
            userAccess = userAccess,
            hasSubscription = hasSubscription,
            information = information,
            vippsCredentials = vippsCredentials
        )
    }
}

@Serializable
data class ObjectInformation(
    val description: String? = null,
    val htmlContent: String? = null,
    val attributes: List<ObjectAttribute>? = null
) {
    val hasValidHtmlContent: Boolean
        get() = !htmlContent.isNullOrBlank()
    
    val isHtmlMode: Boolean
        get() = attributes?.any { attr ->
            attr.label?.lowercase() == "html" && attr.value?.lowercase() == "true"
        } ?: false
}

@Serializable
data class ObjectAttribute(
    val label: String? = null,
    val value: String? = null
)
