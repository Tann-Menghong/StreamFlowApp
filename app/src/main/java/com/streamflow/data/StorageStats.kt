package com.streamflow.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * How much disk the app is actually using, and how to give it back.
 *
 * The app can hold a lot without ever saying so: the media cache alone is
 * allowed up to 768 MB on a high-RAM device, plus an image cache up to 256 MB
 * and however many downloads. Settings previously listed counts (favourites,
 * history entries) but no bytes, so "why is this app using 2 GB?" had no answer
 * inside the app and the only remedy was clearing app data — which also wipes
 * favourites, history and playlists.
 */
object StorageStats {

    data class Snapshot(
        val mediaCacheBytes: Long = 0L,
        val imageCacheBytes: Long = 0L,
        val downloadBytes: Long = 0L,
    ) {
        val totalBytes: Long get() = mediaCacheBytes + imageCacheBytes + downloadBytes
    }

    /**
     * Recursive directory size.
     *
     * walkTopDown() rather than a hand-rolled recursion so a symlink loop cannot
     * hang the coroutine, and every length() is guarded: files under a live cache
     * can vanish mid-walk while playback evicts them.
     */
    private fun dirSize(dir: File): Long = try {
        if (!dir.exists()) 0L
        else dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { runCatching { it.length() }.getOrDefault(0L) }
    } catch (_: Throwable) { 0L }

    suspend fun snapshot(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val media = dirSize(File(context.cacheDir, "media_cache"))
        val images = dirSize(File(context.cacheDir, "image_cache"))
        // Downloads go to the PUBLIC Downloads/StreamFlow folder via
        // DownloadManager, not app storage, so they are sized separately and
        // deliberately never auto-deleted — those are the user's files.
        val downloads = try {
            @Suppress("DEPRECATION")
            dirSize(File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "StreamFlow"
            ))
        } catch (_: Throwable) { 0L }
        Snapshot(media, images, downloads)
    }

    /**
     * Clears cached video/audio segments.
     *
     * Goes through SimpleCache's own API rather than deleting the directory:
     * the cache is a live singleton held open by the playback service, and
     * removing files under it would leave its index describing content that no
     * longer exists. Falls back to nothing rather than risking that corruption.
     */
    suspend fun clearMediaCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cache = MediaCache.get(context)
            for (key in cache.keys.toList()) {
                runCatching { cache.removeResource(key) }
            }
            true
        } catch (_: Throwable) { false }
    }

    /** Clears cached thumbnails. Coil rebuilds these on demand. */
    suspend fun clearImageCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            File(context.cacheDir, "image_cache").deleteRecursively()
            true
        } catch (_: Throwable) { false }
    }

    /** "1.4 GB", "812 MB", "0 B" — sized for a settings row, not a file manager. */
    fun format(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "${bytes / 1_048_576} MB"
        bytes >= 1024L          -> "${bytes / 1024} KB"
        else                    -> "$bytes B"
    }
}
