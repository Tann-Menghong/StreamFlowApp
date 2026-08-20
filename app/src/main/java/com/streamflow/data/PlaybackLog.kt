package com.streamflow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A short, in-memory record of what happened to playback.
 *
 * The app has 155 `catch (_: Exception)` sites and 40 of them are completely
 * empty, so a failure was not merely unreported — it was unrecorded. When
 * playback stopped there was nothing to consult afterwards, which made every
 * diagnosis a guess. CrashReporter already proved the pattern works for
 * crashes; this is the same idea for the failures that do not crash.
 *
 * Deliberate constraints:
 *
 *  - **Memory only.** Entries can contain video titles and URLs. Nothing is
 *    written to disk and nothing is ever uploaded; the log dies with the
 *    process, which is the correct lifetime for a "what just went wrong"
 *    buffer.
 *  - **Bounded.** A fixed ring of [CAPACITY] entries, so a long session with a
 *    flapping connection cannot grow it without limit.
 *  - **Cheap.** Appending is a synchronized list write. It is called from the
 *    playback path, including while buffering, so it must never do I/O.
 *  - **Honest about incognito.** URLs are dropped when incognito is on; the
 *    event itself is still recorded, because knowing playback failed is not
 *    private information and is the whole point.
 */
object PlaybackLog {

    const val CAPACITY = 200

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val at: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()

    /** Bumped on every append so a Compose screen can observe the log without
     *  the log itself having to hold a list StateFlow. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var redactUrls = false

    /** Set from the incognito preference; when on, URLs are stripped. */
    fun setRedactUrls(redact: Boolean) { redactUrls = redact }

    fun add(level: Level, tag: String, message: String) {
        val e = Entry(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            entries.addLast(e)
            while (entries.size > CAPACITY) entries.removeFirst()
        }
        _revision.value = _revision.value + 1
    }

    fun info(tag: String, message: String) = add(Level.INFO, tag, message)
    fun warn(tag: String, message: String) = add(Level.WARN, tag, message)
    fun error(tag: String, message: String) = add(Level.ERROR, tag, message)

    /**
     * Formats a video URL for inclusion in a message: just the video id, or
     * nothing at all while incognito. The full URL is never worth the privacy
     * cost — the id is enough to correlate two entries.
     */
    fun ref(url: String?): String {
        if (url == null) return "-"
        if (redactUrls) return "(hidden)"
        val id = Regex("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        return id ?: url.substringAfterLast('/').take(40)
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun clear() {
        synchronized(lock) { entries.clear() }
        _revision.value = _revision.value + 1
    }

    /** Newest first, ready to paste into an issue. */
    fun asText(): String {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val list = snapshot()
        if (list.isEmpty()) return "No playback events recorded this session."
        return buildString {
            append("StreamFlow playback log — ")
            append(list.size)
            append(" event(s), newest first\n\n")
            list.asReversed().forEach { e ->
                append(fmt.format(java.util.Date(e.at)))
                append(when (e.level) {
                    Level.INFO -> "  ·  "
                    Level.WARN -> "  !  "
                    Level.ERROR -> "  x  "
                })
                append(e.tag)
                append(": ")
                append(e.message)
                append('\n')
            }
        }
    }
}
