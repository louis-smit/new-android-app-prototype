package no.solver.solverappdemo.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ObjectDao {
    @Query("SELECT * FROM cached_objects WHERE accountId = :accountId ORDER BY name")
    fun observeAll(accountId: String): Flow<List<CachedObjectEntity>>

    @Query("SELECT * FROM cached_objects WHERE accountId = :accountId ORDER BY name")
    suspend fun getAll(accountId: String): List<CachedObjectEntity>

    @Query("SELECT * FROM cached_objects WHERE id = :id AND accountId = :accountId")
    suspend fun getById(id: Int, accountId: String): CachedObjectEntity?

    @Upsert
    suspend fun upsertAll(objects: List<CachedObjectEntity>)

    @Query("DELETE FROM cached_objects WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("DELETE FROM cached_objects")
    suspend fun deleteAll()
}

@Dao
interface CacheMetadataDao {
    @Query("SELECT * FROM cache_metadata WHERE accountId = :accountId")
    suspend fun getMetadata(accountId: String): CacheMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: CacheMetadataEntity)

    @Query("DELETE FROM cache_metadata WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("DELETE FROM cache_metadata")
    suspend fun deleteAll()
}
