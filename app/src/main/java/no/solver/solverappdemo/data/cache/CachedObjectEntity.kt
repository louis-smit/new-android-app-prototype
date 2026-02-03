package no.solver.solverappdemo.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.solver.solverappdemo.data.models.KeyValuePair
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
    val labelsJson: String?,
    val lastUpdated: Long
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
    fun toDomainModel(): SolverObject {
        val labels: List<KeyValuePair>? = labelsJson?.let {
            try {
                json.decodeFromString<List<KeyValuePair>>(it)
            } catch (e: Exception) {
                null
            }
        }
        
        return SolverObject(
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
            hasSubscription = hasSubscription,
            labels = labels
        )
    }
}

fun CachedObjectEntity.Companion.fromDomainModel(obj: SolverObject, accountId: String): CachedObjectEntity {
    val json = Json { ignoreUnknownKeys = true }
    val labelsJson = obj.labels?.let {
        try {
            json.encodeToString(it)
        } catch (e: Exception) {
            null
        }
    }
    
    return CachedObjectEntity(
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
        labelsJson = labelsJson,
        lastUpdated = System.currentTimeMillis()
    )
}
