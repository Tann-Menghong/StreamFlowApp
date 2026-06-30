package com.streamflow.data

import android.content.Context
import android.os.Environment
import com.streamflow.data.local.AppDatabase
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupManager {

    suspend fun exportToJson(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(context)
            val favorites = db.favoriteDao().getAll().first()
            val history   = db.historyDao().getAll().first()
            val watchLater = db.watchLaterDao().getAll().first()

            val root = JSONObject().apply {
                put("favorites", JSONArray(favorites.map { f ->
                    JSONObject().apply {
                        put("url", f.url); put("title", f.title)
                        put("thumbnailUrl", f.thumbnailUrl); put("uploaderName", f.uploaderName)
                        put("viewCount", f.viewCount); put("duration", f.duration)
                    }
                }))
                put("history", JSONArray(history.map { h ->
                    JSONObject().apply {
                        put("url", h.url); put("title", h.title)
                        put("thumbnailUrl", h.thumbnailUrl); put("uploaderName", h.uploaderName)
                        put("viewCount", h.viewCount); put("duration", h.duration)
                        put("position", h.position)
                    }
                }))
                put("watchLater", JSONArray(watchLater.map { w ->
                    JSONObject().apply {
                        put("url", w.url); put("title", w.title)
                        put("thumbnailUrl", w.thumbnailUrl); put("uploaderName", w.uploaderName)
                        put("viewCount", w.viewCount); put("duration", w.duration)
                    }
                }))
            }

            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "StreamFlow_backup.json")
            file.writeText(root.toString(2))
            file.absolutePath
        }
    }

    suspend fun importFromJson(context: Context, filePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(context)
            val json = JSONObject(File(filePath).readText())

            val favArray = json.optJSONArray("favorites")
            if (favArray != null) {
                for (i in 0 until favArray.length()) {
                    val o = favArray.getJSONObject(i)
                    db.favoriteDao().insert(FavoriteEntity(
                        url = o.getString("url"), title = o.getString("title"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        uploaderName = o.optString("uploaderName"),
                        viewCount = o.optLong("viewCount"), duration = o.optLong("duration")
                    ))
                }
            }

            val histArray = json.optJSONArray("history")
            if (histArray != null) {
                for (i in 0 until histArray.length()) {
                    val o = histArray.getJSONObject(i)
                    db.historyDao().insert(HistoryEntity(
                        url = o.getString("url"), title = o.getString("title"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        uploaderName = o.optString("uploaderName"),
                        viewCount = o.optLong("viewCount"), duration = o.optLong("duration"),
                        position = o.optLong("position")
                    ))
                }
            }

            val wlArray = json.optJSONArray("watchLater")
            if (wlArray != null) {
                for (i in 0 until wlArray.length()) {
                    val o = wlArray.getJSONObject(i)
                    db.watchLaterDao().insert(WatchLaterEntity(
                        url = o.getString("url"), title = o.getString("title"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        uploaderName = o.optString("uploaderName"),
                        viewCount = o.optLong("viewCount"), duration = o.optLong("duration")
                    ))
                }
            }

            "Restored successfully"
        }
    }
}
