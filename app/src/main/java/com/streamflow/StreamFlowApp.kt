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
import com.streamflow.data.UpdateCheckWorker
import com.streamflow.data.PlaybackQueue
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

class StreamFlowApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { AppPreferences.get(this) }
    // Public: long-running work (AI model download) must outlive screen ViewModels
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Probe RAM + hardware codecs first — quality/buffer/cache decisions
        // elsewhere read these capabilities
        com.streamflow.data.DeviceCaps.init(this)
        NewPipe.init(OkHttpDownloader.instance)

        // Warm the TLS connections to YouTube's hosts right away so the first
        // feed load / thumbnail / video extraction skips the handshake cost.
        // SponsorBlock/RYD are hit on every video open, so warm those too.
        appScope.launch(Dispatchers.IO) {
            listOf(
                "https://www.youtube.com/generate_204",
                "https://i.ytimg.com/generate_204",
                "https://sponsor.ajay.app/",
                "https://returnyoutubedislikeapi.com/"
            ).forEach { url ->
                try {
                    OkHttpDownloader.instance.client.newCall(
                        okhttp3.Request.Builder().url(url).head().build()
                    ).execute().close()
                } catch (_: Exception) {}
            }
        }

        // Restore + persist the playback queue across app restarts
        PlaybackQueue.bind(prefs, appScope)

        // Auto-clear watch history older than the user's retention setting
        appScope.launch {
            try {
                val days = prefs.historyRetention.first().toIntOrNull() ?: 0
                if (days > 0) {
                    database.historyDao().deleteOlderThan(
                        System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)
                }
            } catch (_: Exception) {}
        }

        // Periodic new-upload check at the user's chosen frequency; the worker
        // itself no-ops when the notification setting is off. UPDATE policy so
        // a changed frequency takes effect on next app start too.
        appScope.launch {
            try {
                val hours = prefs.notifyFreq.first().toLongOrNull() ?: 6L
                WorkManager.getInstance(this@StreamFlowApp).enqueueUniquePeriodicWork(
                    NewVideosWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<NewVideosWorker>(hours, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                        )
                        .build()
                )
            } catch (_: Exception) {}
        }

        // Off the main thread: WorkManager.getInstance does disk I/O on first
        // touch, and these two enqueues ran synchronously during cold start
        appScope.launch {
            // Auto-download Watch Later videos, Wi-Fi only (the worker itself
            // no-ops when the setting is off)
            try {
                WorkManager.getInstance(this@StreamFlowApp).enqueueUniquePeriodicWork(
                    com.streamflow.data.AutoDownloadWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<com.streamflow.data.AutoDownloadWorker>(6, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
                        )
                        .build()
                )
            } catch (_: Exception) {}

            // Twice-daily check for a new app release (notifies once per version)
            try {
                WorkManager.getInstance(this@StreamFlowApp).enqueueUniquePeriodicWork(
                    UpdateCheckWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                        )
                        .build()
                )
            } catch (_: Exception) {}
        }
    }

    // Aggressive thumbnail caching: YouTube sends no-cache headers, so without
    // respectCacheHeaders(false) every scroll re-downloads the same thumbnails.
    // High-RAM devices get bigger caches — smoother scroll-back, fewer re-fetches.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(if (com.streamflow.data.DeviceCaps.isHighPerf) 0.35 else 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(if (com.streamflow.data.DeviceCaps.isHighPerf) 256L * 1024 * 1024
                              else 128L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        // Half the memory per thumbnail on devices without RAM to spare —
        // faster decodes and less GC pressure while scrolling
        .allowRgb565(!com.streamflow.data.DeviceCaps.isHighPerf)
        .crossfade(true)
        .build()
}
