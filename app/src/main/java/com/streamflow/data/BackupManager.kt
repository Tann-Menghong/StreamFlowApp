package com.streamflow.data

import com.streamflow.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

// Single source of truth for the backup JSON, used by both the manual
// Settings export and the weekly AutoBackupWorker — keeping two copies of
// this in sync is exactly how bookmarks went missing from backups before.
object BackupManager {

    suspend fun buildBackupJson(db: AppDatabase): JSONObject = JSONObject().apply {
        // v4 adds customTabs. Older backups simply lack the key and restore fine —
        // every reader uses optJSONArray, so a missing section is skipped.
        put("app", "StreamFlow"); put("backupVersion", 4)
        put("history", JSONArray().also { arr ->
            db.historyDao().getAll().first().forEach { h ->
                arr.put(JSONObject()
                    .put("url", h.url).put("title", h.title)
                    .put("thumbnailUrl", h.thumbnailUrl).put("uploaderName", h.uploaderName)
                    .put("viewCount", h.viewCount).put("duration", h.duration)
                    .put("watchedAt", h.watchedAt).put("position", h.position))
            }
        })
        put("subscriptions", JSONArray().also { arr ->
            db.subscriptionDao().getAllOnce().forEach { s ->
                arr.put(JSONObject()
                    .put("channelUrl", s.channelUrl).put("name", s.name)
                    .put("avatarUrl", s.avatarUrl)
                    .put("groupName", s.groupName).put("notify", s.notify)
                    .put("subscribedAt", s.subscribedAt))
            }
        })
        put("favorites", JSONArray().also { arr ->
            db.favoriteDao().getAll().first().forEach { f ->
                arr.put(JSONObject()
                    .put("url", f.url).put("title", f.title)
                    .put("thumbnailUrl", f.thumbnailUrl).put("uploaderName", f.uploaderName)
                    .put("viewCount", f.viewCount).put("duration", f.duration)
                    .put("savedAt", f.savedAt))
            }
        })
        put("watchLater", JSONArray().also { arr ->
            db.watchLaterDao().getAll().first().forEach { w ->
                arr.put(JSONObject()
                    .put("url", w.url).put("title", w.title)
                    .put("thumbnailUrl", w.thumbnailUrl).put("uploaderName", w.uploaderName)
                    .put("viewCount", w.viewCount).put("duration", w.duration)
                    .put("addedAt", w.addedAt))
            }
        })
        put("blocked", JSONArray().also { arr ->
            db.blockedDao().getAll().first().forEach { b ->
                arr.put(JSONObject()
                    .put("itemKey", b.itemKey).put("type", b.type).put("name", b.name))
            }
        })
        put("playlists", JSONArray().also { arr ->
            db.playlistDao().getPlaylistsOnce().forEach { p ->
                arr.put(JSONObject().put("name", p.name)
                    .put("items", JSONArray().also { itemsArr ->
                        db.playlistDao().getItemsOnce(p.id).forEach { i ->
                            itemsArr.put(JSONObject()
                                .put("url", i.url).put("title", i.title)
                                .put("thumbnailUrl", i.thumbnailUrl)
                                .put("uploaderName", i.uploaderName)
                                .put("duration", i.duration)
                                .put("addedAt", i.addedAt))
                        }
                    }))
            }
        })
        put("bookmarks", JSONArray().also { arr ->
            db.bookmarkDao().getAll().first().forEach { b ->
                arr.put(JSONObject()
                    .put("videoUrl", b.videoUrl).put("title", b.title)
                    .put("thumbnailUrl", b.thumbnailUrl)
                    .put("uploaderName", b.uploaderName)
                    .put("positionMs", b.positionMs)
                    .put("createdAt", b.createdAt))
            }
        })
        // User-added website tabs. These were the only library data a backup did
        // not carry, so restoring onto a new phone silently lost them — the same
        // way bookmarks used to go missing, which is why this file exists.
        // The row id is deliberately NOT exported: it is a local autoincrement
        // key, and on import a fresh one is assigned.
        put("customTabs", JSONArray().also { arr ->
            db.customTabDao().getAll().first().forEach { t ->
                arr.put(JSONObject()
                    .put("title", t.title).put("url", t.url)
                    .put("iconKey", t.iconKey).put("position", t.position)
                    .put("createdAt", t.createdAt))
            }
        })
    }
}
