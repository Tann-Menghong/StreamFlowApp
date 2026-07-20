package com.streamflow.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.streamflow.R
import com.streamflow.data.local.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

class LatestUploadsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LatestUploadsFactory(applicationContext)
}

private data class WidgetRow(
    val title: String, val url: String, val thumb: String, val channel: String)

// Reads the JSON feed NewVideosWorker cached in prefs. onDataSetChanged runs on
// a binder thread where blocking I/O is expected — thumbnails are fetched
// synchronously there so getViewAt never blocks the launcher.
private class LatestUploadsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetRow> = emptyList()
    private val thumbs = HashMap<String, Bitmap?>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = try {
            val json = runBlocking { AppPreferences.get(context).widgetFeed.first() }
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WidgetRow(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    thumb = o.optString("thumb"),
                    channel = o.optString("channel")
                ).takeIf { it.url.isNotEmpty() && it.title.isNotEmpty() }
            }
        } catch (_: Exception) { emptyList() }

        // Small fixed-size rows: decode with a sample size to keep each bitmap
        // well under the RemoteViews transaction limit
        thumbs.keys.retainAll(rows.map { it.thumb }.toSet())
        rows.forEach { row ->
            if (row.thumb.isNotEmpty() && row.thumb !in thumbs) {
                thumbs[row.thumb] = try {
                    // Use the shared client, which has connect/read/call timeouts —
                    // a raw URL.openStream() has NONE, so a dead network could hang
                    // this binder thread (and the launcher's widget refresh) forever.
                    val req = okhttp3.Request.Builder().url(row.thumb).build()
                    com.streamflow.data.OkHttpDownloader.instance.client.newCall(req)
                        .execute().use { resp ->
                            val bytes = resp.body?.bytes() ?: ByteArray(0)
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            if (bytes.isEmpty()) null
                            else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        }
                } catch (_: Exception) { null }
            }
        }
    }

    override fun onDestroy() { thumbs.clear() }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_latest_item)
        return RemoteViews(context.packageName, R.layout.widget_latest_item).apply {
            setTextViewText(R.id.widget_item_title, row.title)
            setTextViewText(R.id.widget_item_channel, row.channel)
            val bmp = thumbs[row.thumb]
            if (bmp != null) setImageViewBitmap(R.id.widget_item_thumb, bmp)
            else setImageViewResource(R.id.widget_item_thumb, R.mipmap.ic_launcher)
            // Fills the provider's ACTION_VIEW template with this video's URL
            setOnClickFillInIntent(R.id.widget_item_root,
                Intent().setData(Uri.parse(row.url)))
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.url?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
