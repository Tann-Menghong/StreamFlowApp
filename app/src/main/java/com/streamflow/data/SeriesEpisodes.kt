package com.streamflow.data

import com.streamflow.data.model.VideoItem

/**
 * Reading a playlist as a series of episodes.
 *
 * A donghua series on YouTube is a playlist and its episodes are that
 * playlist's items, already in order. Nothing needs to be scraped or resolved
 * to know that -- the ordering is the playlist's own.
 *
 * What is missing is everything around it: which episode to resume, what an
 * episode is called, and what should play after the one you tapped. Those were
 * the gaps that made a playlist a list of videos rather than a series.
 *
 * Pure arithmetic and string handling with no Android types, so "tapping
 * episode 12 queues 13 onward" and "resume the furthest half-watched episode"
 * are provable in JVM tests instead of by scrubbing through a phone.
 */
object SeriesEpisodes {

    /** Below this an episode was opened, not watched. */
    const val STARTED = 0.02f

    /** Past this the player restarts from zero, so it counts as finished. */
    const val FINISHED = 0.92f

    /**
     * The episode number shown on a card.
     *
     * Parsed from the title only when the title actually says so, because
     * uploaders number episodes in the title and the playlist position is not
     * always the same thing -- a playlist can open with a trailer, or start at
     * episode 40 of an ongoing series.
     *
     * Deliberately conservative: the digits must follow an episode marker. A
     * bare number in a title is far more often a year, a resolution or part of
     * the series name ("Soul Land 2") than an episode number, and guessing
     * wrong puts a confidently wrong label on the card. When nothing matches,
     * the position in the playlist is used and is right often enough.
     */
    fun episodeNumber(title: String, index: Int): Int {
        for (re in EPISODE_PATTERNS) {
            val n = re.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            // 4+ digits is a year or a stray id, not an episode.
            if (n != null && n in 1..9999) return n
        }
        return index + 1
    }

    // (?!\d) is load-bearing: without it \d{1,4} happily matches the first four
    // digits of a longer run, so "Episode 123456" parsed as episode 1234 --
    // comfortably inside the plausible range, so the guard above never saw it.
    // The lookahead makes an over-long run match nothing and fall back to
    // position, which is the honest answer for a title we cannot read.
    private val EPISODE_PATTERNS = listOf(
        Regex("""(?:episode|ep)\.?\s*#?\s*(\d{1,4})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""第\s*(\d{1,4})(?!\d)\s*集"""),
        Regex("""\bE(\d{1,4})\b"""),
    )

    /**
     * What should play after [url].
     *
     * Tapping an episode used to play exactly that episode and leave the queue
     * untouched, so there was no next episode and playback simply stopped at
     * the end -- the one thing a series watcher needs most. The rest of the
     * playlist goes into the queue instead.
     *
     * An unknown url yields nothing rather than the whole playlist: queueing a
     * series behind a video that is not part of it is worse than queueing
     * nothing.
     */
    fun upNextFrom(episodes: List<VideoItem>, url: String): List<VideoItem> {
        val i = episodes.indexOfFirst { it.url == url }
        return if (i < 0) emptyList() else episodes.drop(i + 1)
    }

    fun next(episodes: List<VideoItem>, url: String): VideoItem? =
        upNextFrom(episodes, url).firstOrNull()

    fun previous(episodes: List<VideoItem>, url: String): VideoItem? {
        val i = episodes.indexOfFirst { it.url == url }
        return if (i > 0) episodes[i - 1] else null
    }

    /** Where the Continue button should take the user. */
    data class Resume(
        val episode: VideoItem,
        val index: Int,
        val fraction: Float,
        /** True when this is a fresh episode rather than a half-watched one. */
        val isNextUp: Boolean,
    )

    /**
     * The episode to offer as "Continue".
     *
     * Rules, in order:
     *  1. The furthest episode that is part-watched. Furthest rather than most
     *     recent, because someone who dips back to rewatch episode 3 has not
     *     stopped being at episode 40.
     *  2. Otherwise the first unwatched episode after the last finished one --
     *     the series is up to date and the next one is what is wanted.
     *  3. Otherwise nothing: nothing has been started, so the screen offers
     *     Play from the beginning instead of a Continue that means the same.
     */
    fun resumePoint(episodes: List<VideoItem>, progress: Map<String, Float>): Resume? {
        if (episodes.isEmpty()) return null

        var partial = -1
        var lastFinished = -1
        episodes.forEachIndexed { i, ep ->
            val p = progress[ep.url] ?: 0f
            if (p in STARTED..FINISHED) partial = i
            if (p > FINISHED) lastFinished = i
        }

        if (partial >= 0) {
            return Resume(episodes[partial], partial, progress[episodes[partial].url] ?: 0f, false)
        }
        if (lastFinished >= 0 && lastFinished < episodes.lastIndex) {
            val i = lastFinished + 1
            return Resume(episodes[i], i, 0f, true)
        }
        return null
    }

    /**
     * "12:43 left" for a part-watched episode, or null when there is nothing
     * useful to say -- an unstarted episode, or one whose duration YouTube did
     * not report (live and premiere items come through as zero).
     */
    fun remainingLabel(durationSeconds: Long, positionMs: Long): String? {
        if (durationSeconds <= 0L || positionMs <= 0L) return null
        val remaining = durationSeconds - positionMs / 1000L
        if (remaining <= 0L) return null
        val m = remaining / 60
        val s = remaining % 60
        return if (m >= 60) "${m / 60}h ${m % 60}m left"
        else "%d:%02d left".format(m, s)
    }

    /** Fraction watched, clamped, for a progress bar. */
    fun fractionOf(durationSeconds: Long, positionMs: Long): Float {
        if (durationSeconds <= 0L) return 0f
        return (positionMs / 1000f / durationSeconds).coerceIn(0f, 1f)
    }
}
