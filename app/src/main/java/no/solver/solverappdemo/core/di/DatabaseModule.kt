package no.solver.solverappdemo.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import no.solver.solverappdemo.data.cache.CacheMetadataDao
import no.solver.solverappdemo.data.cache.ObjectDao
import no.solver.solverappdemo.data.cache.SolverDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSolverDatabase(
        @ApplicationContext context: Context
    ): SolverDatabase {
        return Room.databaseBuilder(
            context,
            SolverDatabase::class.java,
            SolverDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideObjectDao(database: SolverDatabase): ObjectDao {
        return database.objectDao()
    }

    @Provides
    @Singleton
    fun provideCacheMetadataDao(database: SolverDatabase): CacheMetadataDao {
        return database.cacheMetadataDao()
    }
}
