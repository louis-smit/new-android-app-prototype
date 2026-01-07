package no.solver.solverappdemo.data.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.solver.solverappdemo.data.models.SolverObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectsCacheRepository @Inject constructor(
    private val objectDao: ObjectDao,
    private val cacheMetadataDao: CacheMetadataDao
) {
    fun observeObjects(accountId: String): Flow<List<SolverObject>> {
        return objectDao.observeAll(accountId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getCachedObjects(accountId: String): List<SolverObject> {
        return objectDao.getAll(accountId).map { it.toDomainModel() }
    }

    suspend fun getCachedObject(id: Int, accountId: String): SolverObject? {
        return objectDao.getById(id, accountId)?.toDomainModel()
    }

    suspend fun cacheObjects(objects: List<SolverObject>, accountId: String) {
        val entities = objects.map { CachedObjectEntity.fromDomainModel(it, accountId) }
        objectDao.upsertAll(entities)
        updateSyncTimestamp(accountId)
    }

    suspend fun getLastSyncTime(accountId: String): Long? {
        return cacheMetadataDao.getMetadata(accountId)?.lastSyncedAt
    }

    private suspend fun updateSyncTimestamp(accountId: String) {
        cacheMetadataDao.upsert(
            CacheMetadataEntity(
                accountId = accountId,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearCacheForAccount(accountId: String) {
        objectDao.deleteAllForAccount(accountId)
        cacheMetadataDao.deleteForAccount(accountId)
    }

    suspend fun clearAllCache() {
        objectDao.deleteAll()
        cacheMetadataDao.deleteAll()
    }
}
