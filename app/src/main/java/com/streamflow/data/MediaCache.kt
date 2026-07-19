package com.streamflow.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

// Process-wide disk cache for media streams. Replaying a video, returning to it
// from another screen, or seeking back past the in-memory back-buffer reads
// from disk instead of re-downloading. Must be a singleton: SimpleCache throws
// if two instances ever point at the same directory.
object MediaCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(
                if (DeviceCaps.isHighPerf) 768L * 1024 * 1024 else 256L * 1024 * 1024
            ),
            StandaloneDatabaseProvider(context)
        ).also { cache = it }
    }
}
