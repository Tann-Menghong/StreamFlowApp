package com.streamflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.streamflow.MainActivity
import com.streamflow.R

// Scrollable home-screen widget showing the newest upload from each subscribed
// channel. Data comes from the JSON feed NewVideosWorker caches in prefs;
// tapping a row opens the video via MainActivity's ACTION_VIEW deep link.
class LatestUploadsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_latest)

            // List rows come from the RemoteViewsService factory
            val svc = Intent(context, LatestUploadsService::class.java).apply {
                // Unique data URI so the system doesn't collapse intents across instances
                data = Uri.parse("streamflow://widget/latest/$id")
            }
            views.setRemoteAdapter(R.id.widget_latest_list, svc)
            views.setEmptyView(R.id.widget_latest_list, R.id.widget_latest_empty)

            // Template the rows fill in with the video URL — must be MUTABLE
            // on Android 12+ or the per-row fill-in data is silently dropped
            val template = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mutability = if (android.os.Build.VERSION.SDK_INT >= 31)
                PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(R.id.widget_latest_list,
                PendingIntent.getActivity(context, 20, template,
                    mutability or PendingIntent.FLAG_UPDATE_CURRENT))

            // Header tap opens the subscriptions feed
            val feedIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.streamflow.shortcut.FEED"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(R.id.widget_latest_header,
                PendingIntent.getActivity(context, 21, feedIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

            manager.updateAppWidget(id, views)
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_latest_list)
    }
}
