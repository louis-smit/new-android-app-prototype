package no.solver.solverappdemo.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import no.solver.solverappdemo.data.models.SolverObject

@Entity(tableName = "cached_objects")
data class CachedObjectEntity(
    @PrimaryKey val id: Int,
    val accountId: String,
    val name: String,
    val objectTypeId: Int,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val active: Boolean,
    val online: Boolean,
    val state: Int?,
    val tenantName: String?,
    val userAccess: Boolean?,
    val hasSubscription: Boolean?,
    val lastUpdated: Long
) {
    fun toDomainModel(): SolverObject = SolverObject(
        id = id,
        name = name,
        objectTypeId = objectTypeId,
        status = status,
        latitude = latitude,
        longitude = longitude,
        active = active,
        online = online,
        state = state,
        tenantName = tenantName,
        commandMap = null,
        userAccess = userAccess,
        hasSubscription = hasSubscription
    )

    companion object {
        fun fromDomainModel(obj: SolverObject, accountId: String): CachedObjectEntity =
            CachedObjectEntity(
                id = obj.id,
                accountId = accountId,
                name = obj.name,
                objectTypeId = obj.objectTypeId,
                status = obj.status,
                latitude = obj.latitude,
                longitude = obj.longitude,
                active = obj.active,
                online = obj.online,
                state = obj.state,
                tenantName = obj.tenantName,
                userAccess = obj.userAccess,
                hasSubscription = obj.hasSubscription,
                lastUpdated = System.currentTimeMillis()
            )
    }
}
