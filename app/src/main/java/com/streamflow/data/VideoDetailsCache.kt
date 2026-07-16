package com.streamflow.data

import com.streamflow.data.model.VideoDetails

// In-memory LRU cache for extracted video details. YouTube stream URLs expire after
// a few hours, so entries have a short TTL; 30 min is safely inside the expiry window.
object VideoDetailsCache {
    private data class Entry(val details: VideoDetails, val at: Long)

    private const val TTL_MS = 30 * 60 * 1000L
    // High-RAM devices keep more extracted videos warm (each entry is small —
    // this is metadata + URLs, not media). First touched well after DeviceCaps.init.
    private val MAX_ENTRIES = if (DeviceCaps.isHighPerf) 80 else 40

    private val map = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    fun key(url: String, qualityPref: String, maxHeightOverride: Int?) =
        "$url|$qualityPref|${maxHeightOverride ?: -1}"

    @Synchronized
    fun get(key: String): VideoDetails? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.at > TTL_MS) {
            map.remove(key)
            return null
        }
        return e.details
    }

    @Synchronized
    fun put(key: String, details: VideoDetails) {
        map[key] = Entry(details, System.currentTimeMillis())
    }
}
