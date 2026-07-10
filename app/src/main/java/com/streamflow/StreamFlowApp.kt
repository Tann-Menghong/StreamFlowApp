package com.streamflow

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streamflow.data.OkHttpDownloader
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.AppPreferences
import org.schabi.newpipe.extractor.NewPipe

class StreamFlowApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences.get(this) }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloader.instance)
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
