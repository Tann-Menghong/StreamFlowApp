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
import com.streamflow.BuildConfig
import com.streamflow.MainActivity
import com.streamflow.R
import com.streamflow.StreamFlowApp
import kotlinx.coroutines.flow.first

// Periodically checks GitHub for a newer app release and notifies once per
// version. Tapping the notification opens Settings, where the update banner
// offers Download & Install.
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? StreamFlowApp ?: return Result.success()
        if (!app.prefs.notifyAppUpdates.first()) return Result.success()
        if (inQuietHours(app.prefs)) return Result.success()

        val info = try {
            UpdateManager(applicationContext).checkForUpdate(BuildConfig.VERSION_NAME)
        } catch (_: Exception) { null } ?: return Result.success()

        // Only notify once per version
        if (app.prefs.lastNotifiedVersion.first() == info.latestVersion) return Result.success()

        notify(info.latestVersion, info.releaseNotes)
        app.prefs.setLastNotifiedVersion(info.latestVersion)
        return Result.success()
    }

    private fun notify(version: String, releaseNotes: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New StreamFlow versions" })
        }
        // Tap → Settings (shows the Download & Install banner)
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = "com.streamflow.shortcut.SETTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            applicationContext, 3001, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Action button → release page in the browser
        val pagePi = PendingIntent.getActivity(
            applicationContext, 3002,
            Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://github.com/Tann-Menghong/StreamFlowApp/releases/tag/v$version")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val firstLine = releaseNotes.lineSequence()
            .map { it.trim().trimStart('#', '-', '*', ' ') }
            .firstOrNull { it.isNotBlank() } ?: "A new version is ready to install."
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("StreamFlow v$version is available")
            .setContentText(firstLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$firstLine\n\nTap to open Settings and install the update."))
            .setContentIntent(openPi)
            .addAction(0, "What's new", pagePi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(3000, notification) } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val WORK_NAME = "app_update_check"
    }
}
