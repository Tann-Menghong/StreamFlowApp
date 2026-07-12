package com.streamflow.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamflow.StreamFlowApp
import com.streamflow.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.first

// Downloads Watch Later videos automatically. WorkManager only runs this on an
// unmetered network (constraint set at enqueue), so it never touches mobile data.
class AutoDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as StreamFlowApp
        if (!app.prefs.autoDlWatchLater.first()) return Result.success()

        val repo = YouTubeRepository()
        val watchLater = try { app.database.watchLaterDao().getAll().first() } catch (_: Exception) { return Result.success() }
        val downloaded = try {
            app.database.downloadDao().getAll().first().mapTo(HashSet()) { it.url }
        } catch (_: Exception) { emptySet<String>() }

        // A few per run keeps each pass light; the periodic schedule catches the rest
        watchLater.filter { it.url !in downloaded }.take(3).forEach { v ->
            try {
                val streams = repo.getDownloadStreams(v.url)
                val streamUrl = streams.videoUrl ?: return@forEach
                val id = DownloadHelper.enqueue(applicationContext, streamUrl, v.title, isAudio = false)
                app.database.downloadDao().insert(DownloadEntity(
                    url = v.url, title = v.title, thumbnailUrl = v.thumbnailUrl,
                    uploaderName = v.uploaderName, filePath = "", isAudio = false,
                    downloadId = id, status = "DOWNLOADING"
                ))
            } catch (_: Exception) {}
        }
        return Result.success()
    }

    companion object { const val WORK_NAME = "auto_download_watch_later" }
}
