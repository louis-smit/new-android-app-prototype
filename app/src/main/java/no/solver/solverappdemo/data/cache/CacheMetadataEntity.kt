package no.solver.solverappdemo.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val accountId: String,
    val lastSyncedAt: Long
)
