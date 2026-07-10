package com.streamflow.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamflow.MainActivity
import com.streamflow.R
import com.streamflow.StreamFlowApp
import kotlinx.coroutines.flow.first

// Periodically checks subscribed channels for new uploads and notifies.
// The pref gate means the periodic job can stay scheduled; disabling the
// setting just makes it a no-op.
class NewVideosWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? StreamFlowApp ?: return Result.success()
        if (!app.prefs.notifyNewVideos.first()) return Result.success()

        val repo = YouTubeRepository()
        val subs = app.database.subscriptionDao().getAllOnce().take(20)
        var notifId = 2000

        subs.forEach { sub ->
            try {
                val info = repo.getChannelInfo(sub.channelUrl)
                val latest = info.videos.firstOrNull() ?: return@forEach
                // First check just records the baseline; notify only on a change
                if (sub.lastVideoUrl.isNotEmpty() && sub.lastVideoUrl != latest.url) {
                    notify(notifId++, sub.name, latest.title, latest.url)
                }
                app.database.subscriptionDao().updateLastVideo(sub.channelUrl, latest.url)
            } catch (_: Exception) {
            }
        }
        return Result.success()
    }

    private fun notify(id: Int, channelName: String, videoTitle: String, videoUrl: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "New videos", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New uploads from subscribed channels" })
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(videoUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(channelName)
            .setContentText(videoTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(videoTitle))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(id, notification) } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "new_videos"
        const val WORK_NAME = "new_videos_check"
    }
}
