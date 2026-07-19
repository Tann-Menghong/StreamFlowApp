package com.streamflow.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.streamflow.StreamFlowApp
import com.streamflow.data.local.entity.DownloadEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DownloadHelper {

    // Enqueue with the system DownloadManager: reliable, resumable, shows its own
    // progress notification. Returns the DownloadManager id.
    fun enqueue(
        context: Context,
        streamUrl: String,
        title: String,
        isAudio: Boolean
    ): Long {
        // ifBlank: a title that is ALL illegal characters sanitized down to
        // nothing and produced a hidden ".mp4" file the user couldn't find
        val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
            .trim().ifBlank { "video-${System.currentTimeMillis()}" }
        val ext = if (isAudio) "m4a" else "mp4"
        val request = DownloadManager.Request(Uri.parse(streamUrl))
            .setTitle(title)
            .setDescription("StreamFlow download")
            .setMimeType(if (isAudio) "audio/mp4" else "video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, "StreamFlow/$safeName.$ext"
            )
            .setAllowedOverMetered(true)
            .addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    // Companion subtitle file for a video download (same base name, .vtt) —
    // fire-and-forget: no Room row, failures just mean no offline captions
    fun enqueueSubtitle(context: Context, subtitleUrl: String, title: String) {
        try {
            val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                .trim().ifBlank { "video-${System.currentTimeMillis()}" }
            val request = DownloadManager.Request(Uri.parse(subtitleUrl))
                .setTitle("$title (subtitles)")
                .setMimeType("text/vtt")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "StreamFlow/$safeName.vtt")
                .setAllowedOverMetered(true)
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                .enqueue(request)
        } catch (_: Exception) {}
    }
}

// Updates the Room record when the system DownloadManager finishes a download
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return
        val app = context.applicationContext as? StreamFlowApp ?: return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val localUri = try {
                            c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: ""
                        } catch (_: Exception) { "" }
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            app.database.downloadDao().updateByDownloadId(id, "DONE", localUri)
                        } else {
                            app.database.downloadDao().updateByDownloadId(id, "FAILED", "")
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
