package com.streamflow

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streamflow.data.NewVideosWorker
import com.streamflow.data.OkHttpDownloader
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.AppPreferences
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

class StreamFlowApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences.get(this) }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader.instance)

        // Periodic new-upload check; the worker itself no-ops when the
        // notification setting is off
        try {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                NewVideosWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewVideosWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
            )
        } catch (_: Exception) {}
    }

    // Aggressive thumbnail caching: YouTube sends no-cache headers, so without
    // respectCacheHeaders(false) every scroll re-downloads the same thumbnails
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
