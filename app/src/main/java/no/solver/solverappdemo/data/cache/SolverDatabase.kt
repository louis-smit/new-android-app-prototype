package no.solver.solverappdemo.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedObjectEntity::class,
        CacheMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SolverDatabase : RoomDatabase() {
    abstract fun objectDao(): ObjectDao
    abstract fun cacheMetadataDao(): CacheMetadataDao

    companion object {
        const val DATABASE_NAME = "solver_database"
    }
}
