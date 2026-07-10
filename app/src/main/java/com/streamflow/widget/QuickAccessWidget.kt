package com.streamflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.streamflow.MainActivity
import com.streamflow.R

// Home-screen widget with quick actions: Search, Subscriptions feed, Library.
// Each button launches MainActivity with a shortcut action that NavGraph routes.
class QuickAccessWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_access)
            views.setOnClickPendingIntent(R.id.widget_btn_search,
                actionIntent(context, "com.streamflow.shortcut.SEARCH", 10))
            views.setOnClickPendingIntent(R.id.widget_btn_subs,
                actionIntent(context, "com.streamflow.shortcut.FEED", 11))
            views.setOnClickPendingIntent(R.id.widget_btn_library,
                actionIntent(context, "com.streamflow.shortcut.LIBRARY", 12))
            manager.updateAppWidget(id, views)
        }
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
