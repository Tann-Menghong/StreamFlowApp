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

    /**
     * How much disk this cache may use.
     *
     * The budget used to be chosen from RAM alone — 768 MB on a "high
     * performance" device, 256 MB otherwise. RAM does not predict free storage,
     * so a 12 GB phone with 2 GB left got the largest budget of all, and the
     * image cache asked for a further 256 MB beside it. Now the RAM-derived
     * figure is only a ceiling: the actual budget is capped at a tenth of what
     * is genuinely free, so the app never competes with the user's photos for
     * the last gigabyte.
     */
    fun budgetBytes(context: Context): Long {
        val ceiling = if (DeviceCaps.isHighPerf) 768L * 1024 * 1024 else 256L * 1024 * 1024
        val free = try {
            val stat = android.os.StatFs(context.cacheDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            return ceiling // unreadable: keep the old behaviour rather than guess
        }
        // A floor as well as a cap: below about 64 MB the cache stops being
        // useful for anything, and a tiny one just churns.
        return (free / 10).coerceIn(64L * 1024 * 1024, ceiling)
    }

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(budgetBytes(context)),
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
