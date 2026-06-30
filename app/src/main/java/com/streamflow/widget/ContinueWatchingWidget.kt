package com.streamflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.streamflow.MainActivity
import com.streamflow.R
import com.streamflow.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ContinueWatchingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_continue_watching)

            // Open app on tap
            val openIntent = Intent(context, MainActivity::class.java)
            val pendingOpen = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.get(context)
                    val items = db.historyDao().getRecentWithProgress(3).first()
                    val titles = items.take(3).joinToString("\n") { h ->
                        val pct = if (h.duration > 0) ((h.position / 1000f / h.duration) * 100).toInt() else 0
                        "• ${h.title.take(35)} ($pct%)"
                    }
                    views.setTextViewText(R.id.widget_content,
                        if (titles.isNotEmpty()) titles else "No videos in progress")
                } catch (_: Exception) {
                    views.setTextViewText(R.id.widget_content, "Open StreamFlow to continue watching")
                }
                manager.updateAppWidget(widgetId, views)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
