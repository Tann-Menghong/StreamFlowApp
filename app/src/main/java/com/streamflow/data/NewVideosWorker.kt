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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        // Notifications can be off while the latest-uploads widget still needs
        // fresh data — only bail out when neither consumer wants the fetch.
        // Quiet hours suppress notifications (baselines untouched, so the
        // notification arrives on the first check after quiet hours end).
        val notifyOn = app.prefs.notifyNewVideos.first() && !inQuietHours(app.prefs)
        val widgetIds = try {
            android.appwidget.AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
                android.content.ComponentName(applicationContext,
                    com.streamflow.widget.LatestUploadsWidget::class.java))
        } catch (_: Exception) { IntArray(0) }
        if (!notifyOn && widgetIds.isEmpty()) return Result.success()

        val repo = YouTubeRepository()
        // Only channels the user left the bell on for
        val subs = app.database.subscriptionDao().getAllOnce().filter { it.notify }.take(20)
        val maxNotifs = app.prefs.notifyMax.first().toIntOrNull() ?: 5
        var notified = 0
        var newCount = 0

        // Fetch all channels in parallel — 20 sequential extractions kept the
        // worker alive for minutes on slow networks; parallel finishes in one
        // round-trip's worth of time (notifications still posted in order below)
        val latestByChannel = coroutineScope {
            subs.map { sub ->
                async {
                    sub to try { repo.getChannelInfo(sub.channelUrl).videos.firstOrNull() }
                           catch (_: Exception) { null }
                }
            }.awaitAll()
        }

        // Refresh the widget feed regardless of notification settings
        if (widgetIds.isNotEmpty()) {
            try {
                val arr = org.json.JSONArray()
                latestByChannel.mapNotNull { (sub, latest) -> latest?.let { sub to it } }
                    .sortedByDescending { it.second.uploadedEpoch }
                    .take(10)
                    .forEach { (sub, v) ->
                        arr.put(org.json.JSONObject().apply {
                            put("title", v.title)
                            put("url", v.url)
                            put("thumb", v.thumbnailUrl)
                            put("channel", sub.name)
                        })
                    }
                if (arr.length() > 0) {
                    app.prefs.setWidgetFeed(arr.toString())
                    android.appwidget.AppWidgetManager.getInstance(applicationContext)
                        .notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_latest_list)
                }
            } catch (_: Exception) {}
        }

        // Baselines/notifications are skipped while notifications are off or
        // quieted so the user still gets alerted for uploads they haven't seen
        if (!notifyOn) return Result.success()

        latestByChannel.forEach { (sub, latest) ->
            if (latest == null) return@forEach
            try {
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
                        notify(notifId, sub.name, latest)
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

    private fun notify(id: Int, channelName: String, video: com.streamflow.data.model.VideoItem) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "New videos", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New uploads from subscribed channels" })
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(video.url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Quick-action: save to Watch later straight from the notification
        val wlIntent = Intent(applicationContext, WatchLaterReceiver::class.java).apply {
            putExtra("url", video.url)
            putExtra("title", video.title)
            putExtra("thumb", video.thumbnailUrl)
            putExtra("channel", channelName)
            putExtra("views", video.viewCount)
            putExtra("duration", video.duration)
            putExtra("notifId", id)
        }
        val wlPi = PendingIntent.getBroadcast(
            applicationContext, id + 10000, wlIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(channelName)
            .setContentText(video.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(video.title))
            .setContentIntent(pi)
            .addAction(0, "Watch later", wlPi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(id, notification) } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "new_videos"
        const val WORK_NAME = "new_videos_check"
    }
}
