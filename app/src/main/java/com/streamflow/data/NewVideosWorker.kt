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
import com.streamflow.data.local.AppPreferences
import kotlinx.coroutines.flow.first

// True while the user's quiet-hours window is active ("start-end" in 24h form,
// e.g. "22-7" = 22:00 tonight until 07:00 tomorrow). "OFF" disables it.
internal suspend fun inQuietHours(prefs: AppPreferences): Boolean {
    val range = prefs.quietHours.first()
    if (range == "OFF") return false
    val parts = range.split("-")
    val start = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val end   = parts.getOrNull(1)?.toIntOrNull() ?: return false
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (start <= end) h in start until end else (h >= start || h < end)
}

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
        // During quiet hours skip entirely (baselines untouched, so the
        // notification arrives on the first check after quiet hours end)
        if (inQuietHours(app.prefs)) return Result.success()

        val repo = YouTubeRepository()
        // Only channels the user left the bell on for
        val subs = app.database.subscriptionDao().getAllOnce().filter { it.notify }.take(20)
        val maxNotifs = app.prefs.notifyMax.first().toIntOrNull() ?: 5
        var notified = 0
        var newCount = 0

        subs.forEach { sub ->
            try {
                val info = repo.getChannelInfo(sub.channelUrl)
                val latest = info.videos.firstOrNull() ?: return@forEach
                // First check just records the baseline; notify only on a change
                if (sub.lastVideoUrl.isNotEmpty() && sub.lastVideoUrl != latest.url) {
                    newCount++
                    if (maxNotifs <= 0 || notified < maxNotifs) {
                        // Stable per-channel id (not a counter reset every run) so a
                        // notification from an earlier run can't get silently
                        // overwritten by an unrelated channel's alert hours later —
                        // a new upload from the SAME channel correctly replaces its
                        // own older notification instead of stacking duplicates.
                        val notifId = 2000 + (sub.channelUrl.hashCode() and 0x7FFFFFFF) % 8000
                        notify(notifId, sub.name, latest.title, latest.url)
                        notified++
                    }
                }
                app.database.subscriptionDao().updateLastVideo(sub.channelUrl, latest.url)
            } catch (_: Exception) {
            }
        }
        // Feeds the "NEW" badge on the Feed button; cleared when the feed opens
        if (newCount > 0) app.prefs.addUnseenFeed(newCount)
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
