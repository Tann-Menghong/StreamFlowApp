package com.streamflow.data

import com.streamflow.data.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodesTest {

    private fun ep(url: String, title: String = "t", duration: Long = 1200L) = VideoItem(
        url = url, title = title, thumbnailUrl = "", uploaderName = "Studio",
        viewCount = 0L, duration = duration
    )

    private val series = listOf(ep("e1"), ep("e2"), ep("e3"), ep("e4"), ep("e5"))

    // ── episode numbering ───────────────────────────────────────────────────

    @Test fun `an episode number is read from the title when it is stated`() {
        assertEquals(284, SeriesEpisodes.episodeNumber("Perfect World Episode 284", 0))
        assertEquals(12, SeriesEpisodes.episodeNumber("Swallowed Star EP 12 [English Sub]", 0))
        assertEquals(7, SeriesEpisodes.episodeNumber("Renegade Immortal Ep.7", 3))
        assertEquals(45, SeriesEpisodes.episodeNumber("完美世界 第45集", 0))
    }

    @Test fun `numbering is case-insensitive and tolerates spacing`() {
        assertEquals(9, SeriesEpisodes.episodeNumber("EPISODE 9", 0))
        assertEquals(9, SeriesEpisodes.episodeNumber("episode9", 0))
        assertEquals(9, SeriesEpisodes.episodeNumber("Ep #9", 0))
    }

    // The reason parsing is conservative: a bare number in a title is far more
    // often a year, a resolution, or part of the series name than an episode.
    @Test fun `a bare number is not mistaken for an episode number`() {
        assertEquals(1, SeriesEpisodes.episodeNumber("Soul Land 2", 0))
        assertEquals(3, SeriesEpisodes.episodeNumber("Battle Through the Heavens 1080p", 2))
        assertEquals(5, SeriesEpisodes.episodeNumber("Donghua 2021 Trailer", 4))
    }

    @Test fun `position is used when the title says nothing`() {
        assertEquals(1, SeriesEpisodes.episodeNumber("Official Trailer", 0))
        assertEquals(40, SeriesEpisodes.episodeNumber("Untitled", 39))
    }

    @Test fun `an implausible number falls back to position`() {
        // A five-digit run is an id, not an episode.
        assertEquals(2, SeriesEpisodes.episodeNumber("Episode 123456", 1))
    }

    // ── queueing what comes next ────────────────────────────────────────────

    // The actual defect: tapping an episode played only that episode and left
    // the queue untouched, so playback stopped at the end of it.
    @Test fun `tapping an episode queues the rest of the series`() {
        assertEquals(
            listOf("e3", "e4", "e5"),
            SeriesEpisodes.upNextFrom(series, "e2").map { it.url }
        )
    }

    @Test fun `the last episode queues nothing`() {
        assertTrue(SeriesEpisodes.upNextFrom(series, "e5").isEmpty())
        assertNull(SeriesEpisodes.next(series, "e5"))
    }

    // Queueing a whole series behind a video that is not part of it is worse
    // than queueing nothing at all.
    @Test fun `an unknown episode queues nothing rather than everything`() {
        assertTrue(SeriesEpisodes.upNextFrom(series, "not-in-series").isEmpty())
        assertNull(SeriesEpisodes.next(series, "not-in-series"))
        assertNull(SeriesEpisodes.previous(series, "not-in-series"))
    }

    @Test fun `next and previous walk the series`() {
        assertEquals("e3", SeriesEpisodes.next(series, "e2")?.url)
        assertEquals("e1", SeriesEpisodes.previous(series, "e2")?.url)
        assertNull(SeriesEpisodes.previous(series, "e1"))
    }

    // ── resume ──────────────────────────────────────────────────────────────

    @Test fun `resume offers the part-watched episode`() {
        val r = SeriesEpisodes.resumePoint(series, mapOf("e3" to 0.4f))
        assertEquals("e3", r?.episode?.url)
        assertEquals(2, r?.index)
        assertFalse(r!!.isNextUp)
    }

    // Someone who dips back to rewatch episode 1 has not stopped being at
    // episode 4, so the furthest one wins rather than the most recent.
    @Test fun `the furthest part-watched episode wins`() {
        val r = SeriesEpisodes.resumePoint(series, mapOf("e1" to 0.5f, "e4" to 0.3f))
        assertEquals("e4", r?.episode?.url)
    }

    @Test fun `a caught-up series offers the next unwatched episode`() {
        val r = SeriesEpisodes.resumePoint(
            series, mapOf("e1" to 1f, "e2" to 0.99f)
        )
        assertEquals("e3", r?.episode?.url)
        assertTrue(r!!.isNextUp)
        assertEquals(0f, r.fraction, 0.001f)
    }

    // Nothing started means Continue would mean the same as Play, so the screen
    // should offer only one of them.
    @Test fun `an untouched series has no resume point`() {
        assertNull(SeriesEpisodes.resumePoint(series, emptyMap()))
        assertNull(SeriesEpisodes.resumePoint(series, mapOf("e1" to 0.001f)))
    }

    @Test fun `a fully finished series has no resume point`() {
        val done = series.associate { it.url to 1f }
        assertNull(SeriesEpisodes.resumePoint(series, done))
    }

    @Test fun `an empty series has no resume point`() {
        assertNull(SeriesEpisodes.resumePoint(emptyList(), mapOf("e1" to 0.5f)))
    }

    // ── labels ──────────────────────────────────────────────────────────────

    @Test fun `remaining time reads as time left`() {
        assertEquals("12:43 left", SeriesEpisodes.remainingLabel(1500L, 737_000L))
        assertEquals("0:30 left", SeriesEpisodes.remainingLabel(60L, 30_000L))
        assertEquals("1h 5m left", SeriesEpisodes.remainingLabel(4200L, 300_000L))
    }

    // Live and premiere items report zero duration; a label built from that
    // would claim a precise time the app does not know.
    @Test fun `nothing useful to say produces no label`() {
        assertNull(SeriesEpisodes.remainingLabel(0L, 60_000L))
        assertNull(SeriesEpisodes.remainingLabel(1200L, 0L))
        assertNull(SeriesEpisodes.remainingLabel(600L, 600_000L))
        assertNull(SeriesEpisodes.remainingLabel(600L, 999_000L))
    }

    @Test fun `fraction is clamped and safe at zero duration`() {
        assertEquals(0.5f, SeriesEpisodes.fractionOf(600L, 300_000L), 0.001f)
        assertEquals(1f, SeriesEpisodes.fractionOf(600L, 9_999_000L), 0.001f)
        assertEquals(0f, SeriesEpisodes.fractionOf(0L, 300_000L), 0.001f)
    }
}
