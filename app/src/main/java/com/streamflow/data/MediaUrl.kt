package com.streamflow.data

/**
 * How a URL handed to the player must actually be played.
 *
 * This rule used to exist in three places — PlaybackService.isLocalOrDirectUrl,
 * PlayerScreen's inline `isDirectStream`, and PlayerViewModel.isDirectStream —
 * and the three copies had drifted apart. The service's copy checked
 * `file://` FIRST and treated it as local; the player screen's copy checked
 * only for file extensions.
 *
 * That difference was a real bug. A completed download is stored as a
 * `file:///storage/.../Title.mp4` URI, so the screen's copy matched on ".mp4",
 * decided it was a remote direct stream, and rendered it in a WebView whose
 * base URL is an https origin — which will not load a file:// source at all.
 * Downloaded videos therefore had no chance of playing, and none of the media
 * session's features (background audio, notification, resume position,
 * equalizer, error recovery) applied to them.
 *
 * One classifier, one ordering rule: scheme before extension.
 */
enum class MediaKind {
    /** On this device: a downloaded file or a picked content:// document.
     *  Plays through ExoPlayer with no extraction and no network. */
    LOCAL_FILE,

    /** A remote media URL that is already a playable stream (the Donghua /
     *  PdTV / Mkiss site tabs). Must NOT be handed to the YouTube extractor. */
    DIRECT_STREAM,

    /** A YouTube watch URL that has to be extracted before it can play. */
    YOUTUBE
}

object MediaUrl {

    /**
     * Scheme is checked before any extension, because a local file can carry
     * any extension a remote stream can. Getting this order wrong is the whole
     * bug this object exists to prevent.
     */
    // SdCardPath is suppressed deliberately: these are prefixes being RECOGNISED
    // in a path handed to us by DownloadManager, not a path being constructed.
    // The lint check exists to stop apps hardcoding a storage location; matching
    // one that the system produced is the opposite of that.
    @android.annotation.SuppressLint("SdCardPath")
    fun classify(url: String): MediaKind {
        val lower = url.trim().lowercase()

        if (lower.startsWith("file://") ||
            lower.startsWith("content://") ||
            // DownloadManager can hand back a bare absolute path rather than a
            // URI, depending on API level and destination.
            lower.startsWith("/storage/") ||
            lower.startsWith("/data/") ||
            lower.startsWith("/sdcard/")
        ) return MediaKind.LOCAL_FILE

        if (lower.contains(".m3u8") || lower.contains(".mpd") ||
            lower.contains(".mp4") || lower.contains(".m4a") ||
            lower.contains(".webm") || lower.contains(".mkv") ||
            lower.contains("/hls/") || lower.contains("/stream/")
        ) return MediaKind.DIRECT_STREAM

        return MediaKind.YOUTUBE
    }

    /** A file on this device — playable by ExoPlayer, never by the extractor. */
    fun isLocalFile(url: String) = classify(url) == MediaKind.LOCAL_FILE

    /** A remote stream that is already playable — the WebView player's job. */
    fun isDirectStream(url: String) = classify(url) == MediaKind.DIRECT_STREAM

    /**
     * Anything the YouTube extractor must not be handed. Replaces the old
     * PlaybackService.isLocalOrDirectUrl with identical intent.
     */
    fun isLocalOrDirect(url: String) = classify(url) != MediaKind.YOUTUBE
}
