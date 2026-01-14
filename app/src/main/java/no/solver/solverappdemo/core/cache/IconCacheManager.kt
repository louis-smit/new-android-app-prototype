package no.solver.solverappdemo.core.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.solver.solverappdemo.data.models.ResourceIcon
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IconCacheManager"
private const val ICON_CACHE_DIR = "icon_cache"
private const val ICON_FILE_PREFIX = "resource_"

/**
 * Manages pre-fetching and caching of object type icons.
 * 
 * Icons are:
 * 1. Pre-fetched at startup from /api/Resource/Icons (all icons in one request)
 * 2. Stored to disk cache for persistence across app restarts
 * 3. Loaded into memory LruCache for fast access during scrolling
 * 
 * This approach matches the old Android app's BitmapCache strategy for smooth scrolling.
 */
@Singleton
class IconCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Memory cache - use 1/8 of available memory
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    
    private val memoryCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
    
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, ICON_CACHE_DIR).also { it.mkdirs() }
    }
    
    init {
        // Load disk cache into memory on startup
        scope.launch {
            loadFromDiskCache()
        }
    }
    
    /**
     * Get a cached icon bitmap for the given object type ID.
     * Returns null if not cached.
     */
    fun getIcon(objectTypeId: Int): Bitmap? {
        return memoryCache.get(objectTypeId)
    }
    
    /**
     * Check if an icon is cached for the given object type ID.
     */
    fun hasIcon(objectTypeId: Int): Boolean {
        return memoryCache.get(objectTypeId) != null
    }
    
    /**
     * Pre-fetch and cache all icons from the API response.
     * Called after fetching /api/Resource/Icons.
     */
    suspend fun cacheIcons(icons: List<ResourceIcon>) {
        withContext(Dispatchers.IO) {
            icons.forEach { icon ->
                try {
                    val bitmap = decodeBase64ToBitmap(icon.base64Data)
                    if (bitmap != null) {
                        // Save to memory cache
                        memoryCache.put(icon.id, bitmap)
                        // Save to disk cache
                        saveToDisk(icon.id, bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cache icon ${icon.id}: ${e.message}")
                }
            }
            _isLoaded.value = true
            Log.i(TAG, "Cached ${icons.size} icons")
        }
    }
    
    /**
     * Cache a single icon from base64 data.
     */
    suspend fun cacheIcon(objectTypeId: Int, base64Data: String) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeBase64ToBitmap(base64Data)
                if (bitmap != null) {
                    memoryCache.put(objectTypeId, bitmap)
                    saveToDisk(objectTypeId, bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache icon $objectTypeId: ${e.message}")
            }
        }
    }
    
    /**
     * Load all icons from disk cache into memory.
     */
    private suspend fun loadFromDiskCache() {
        withContext(Dispatchers.IO) {
            val files = cacheDir.listFiles { file ->
                file.name.startsWith(ICON_FILE_PREFIX)
            } ?: return@withContext
            
            var loadedCount = 0
            files.forEach { file ->
                try {
                    val objectTypeId = file.name
                        .removePrefix(ICON_FILE_PREFIX)
                        .removeSuffix(".png")
                        .toIntOrNull()
                    
                    if (objectTypeId != null) {
                        val bitmap = loadFromDisk(objectTypeId)
                        if (bitmap != null) {
                            memoryCache.put(objectTypeId, bitmap)
                            loadedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load icon from ${file.name}: ${e.message}")
                }
            }
            
            if (loadedCount > 0) {
                _isLoaded.value = true
                Log.i(TAG, "Loaded $loadedCount icons from disk cache")
            }
        }
    }
    
    private fun saveToDisk(objectTypeId: Int, bitmap: Bitmap) {
        try {
            val file = File(cacheDir, "$ICON_FILE_PREFIX$objectTypeId.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save icon $objectTypeId to disk: ${e.message}")
        }
    }
    
    private fun loadFromDisk(objectTypeId: Int): Bitmap? {
        return try {
            val file = File(cacheDir, "$ICON_FILE_PREFIX$objectTypeId.png")
            if (file.exists()) {
                FileInputStream(file).use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon $objectTypeId from disk: ${e.message}")
            null
        }
    }
    
    private fun decodeBase64ToBitmap(base64Data: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode base64 to bitmap: ${e.message}")
            null
        }
    }
    
    /**
     * Clear all cached icons (both memory and disk).
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            memoryCache.evictAll()
            cacheDir.listFiles()?.forEach { it.delete() }
            _isLoaded.value = false
            Log.i(TAG, "Icon cache cleared")
        }
    }
}
