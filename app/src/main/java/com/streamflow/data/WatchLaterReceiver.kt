package com.streamflow.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.streamflow.StreamFlowApp
import com.streamflow.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Handles the "Watch later" action button on new-upload notifications:
// saves the video without opening the app, then dismisses the notification.
class WatchLaterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val app = context.applicationContext as? StreamFlowApp ?: return
        val title = intent.getStringExtra("title") ?: ""
        val thumb = intent.getStringExtra("thumb") ?: ""
        val channel = intent.getStringExtra("channel") ?: ""
        val views = intent.getLongExtra("views", -1L)
        val duration = intent.getLongExtra("duration", 0L)
        val notifId = intent.getIntExtra("notifId", -1)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.database.watchLaterDao().insert(WatchLaterEntity(
                    url = url, title = title, thumbnailUrl = thumb,
                    uploaderName = channel, viewCount = views, duration = duration))
            } catch (_: Exception) {}
            if (notifId != -1) {
                try {
                    (context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager).cancel(notifId)
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                try { Toast.makeText(context, "Saved to Watch later", Toast.LENGTH_SHORT).show() }
                catch (_: Exception) {}
            }
            pending.finish()
        }
    }
}
