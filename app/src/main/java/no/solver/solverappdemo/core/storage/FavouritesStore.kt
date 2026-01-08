package no.solver.solverappdemo.core.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsRepository
import javax.inject.Inject
import javax.inject.Singleton

private val Context.favouritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favourites")

@Singleton
class FavouritesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FavouritesStore"
        private val FAVOURITE_IDS_KEY = stringSetPreferencesKey("favourite_ids")
    }

    private val _favourites = MutableStateFlow<List<SolverObject>>(emptyList())
    val favourites: StateFlow<List<SolverObject>> = _favourites.asStateFlow()

    private val _favouriteIds = MutableStateFlow<Set<Int>>(emptySet())
    val favouriteIds: StateFlow<Set<Int>> = _favouriteIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val favouriteIdsFlow: Flow<Set<Int>> = context.favouritesDataStore.data.map { preferences ->
        preferences[FAVOURITE_IDS_KEY]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun loadFavourites(repository: ObjectsRepository) {
        _isLoading.value = true
        try {
            val ids = context.favouritesDataStore.data.first()[FAVOURITE_IDS_KEY]
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            _favouriteIds.value = ids

            if (ids.isEmpty()) {
                _favourites.value = emptyList()
                return
            }

            when (val result = repository.getUserObjects()) {
                is ApiResult.Success -> {
                    val favouriteObjects = result.data
                        .filter { ids.contains(it.id) }
                        .sortedBy { it.name.lowercase() }
                    _favourites.value = favouriteObjects
                    Log.d(TAG, "Loaded ${favouriteObjects.size} favourite objects")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to load favourites: ${result.exception.message}")
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun addFavourite(objectId: Int) {
        Log.d(TAG, "Adding favourite: $objectId")
        context.favouritesDataStore.edit { preferences ->
            val currentIds = preferences[FAVOURITE_IDS_KEY]?.toMutableSet() ?: mutableSetOf()
            currentIds.add(objectId.toString())
            preferences[FAVOURITE_IDS_KEY] = currentIds
        }
        _favouriteIds.value = _favouriteIds.value + objectId
    }

    suspend fun removeFavourite(objectId: Int) {
        Log.d(TAG, "Removing favourite: $objectId")
        context.favouritesDataStore.edit { preferences ->
            val currentIds = preferences[FAVOURITE_IDS_KEY]?.toMutableSet() ?: mutableSetOf()
            currentIds.remove(objectId.toString())
            preferences[FAVOURITE_IDS_KEY] = currentIds
        }
        _favouriteIds.value = _favouriteIds.value - objectId
        _favourites.value = _favourites.value.filter { it.id != objectId }
    }

    fun isFavourite(objectId: Int): Boolean {
        return _favouriteIds.value.contains(objectId)
    }

    suspend fun toggleFavourite(objectId: Int): Boolean {
        return if (isFavourite(objectId)) {
            removeFavourite(objectId)
            false
        } else {
            addFavourite(objectId)
            true
        }
    }

    fun applyOptimisticAdd(obj: SolverObject) {
        if (!_favouriteIds.value.contains(obj.id)) {
            _favouriteIds.value = _favouriteIds.value + obj.id
            _favourites.value = (_favourites.value + obj).sortedBy { it.name.lowercase() }
        }
    }

    fun applyOptimisticRemove(objectId: Int) {
        _favouriteIds.value = _favouriteIds.value - objectId
        _favourites.value = _favourites.value.filter { it.id != objectId }
    }

    fun rollback(previousIds: Set<Int>, previousFavourites: List<SolverObject>) {
        _favouriteIds.value = previousIds
        _favourites.value = previousFavourites
    }
}
