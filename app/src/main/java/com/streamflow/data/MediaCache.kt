package com.streamflow.data

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // Pre-download the first [bytes] of a stream into the cache so pressing
    // play on it starts instantly. CacheWriter skips ranges already cached,
    // and PlaybackService reads through the same cache.
    suspend fun warmStream(context: Context, url: String, bytes: Long) =
        withContext(Dispatchers.IO) {
            try {
                val ds = CacheDataSource.Factory()
                    .setCache(get(context))
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(OkHttpDownloader.instance.client))
                    .createDataSource()
                CacheWriter(ds, DataSpec(Uri.parse(url), 0, bytes), null, null).cache()
            } catch (_: Exception) {}
        }
}
