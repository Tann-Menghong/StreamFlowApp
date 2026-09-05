package com.streamflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStatsTest {

    private val midnight = 1_700_000_000_000L // arbitrary fixed "local midnight"
    private val day = LibraryStats.DAY_MS

    private fun watch(at: Long, ms: Long = 0L, who: String = "Chan") =
        LibraryStats.Watch(watchedAt = at, positionMs = ms, uploader = who)

    // The regression this file exists for: the two dashboards on the Library
    // screen labelled the same span "week" and computed it differently, so the
    // number above the tabs and the number below them disagreed. The chart and
    // the tile now come from one summary and cannot drift apart.
    @Test fun `week count is exactly the sum of the chart`() {
        val s = LibraryStats.summarize(
            listOf(
                watch(midnight),                 // today
                watch(midnight + 3 * 3_600_000), // today, later
                watch(midnight - day),           // yesterday
                watch(midnight - 6 * day),       // oldest day still on the chart
                watch(midnight - 7 * day),       // one day too old
            ),
            midnight
        )
        assertEquals(4, s.weekCount)
        assertEquals(s.dayCounts.sum(), s.weekCount)
        assertEquals(LibraryStats.WEEK_DAYS, s.dayCounts.size)
    }

    @Test fun `today is the last chart slot, not a separate count`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight), watch(midnight + 1000), watch(midnight - day)),
            midnight
        )
        assertEquals(2, s.todayCount)
        assertEquals(s.dayCounts.last(), s.todayCount)
    }

    @Test fun `days are ordered oldest first`() {
        val s = LibraryStats.summarize(listOf(watch(midnight - 6 * day)), midnight)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), s.dayCounts)
    }

    @Test fun `a watch older than the window still counts toward all-time`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight - 90 * day, ms = 30 * 60_000L)),
            midnight
        )
        assertEquals(0, s.weekCount)
        assertEquals(0L, s.weekMinutes)
        assertEquals(30L, s.totalMinutes)
    }

    // A restored backup or a corrected clock can leave a row dated tomorrow.
    // It must not land in the array out of bounds, and must not be silently
    // dropped from the all-time total either.
    @Test fun `a future timestamp does not crash or vanish`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight + 5 * day, ms = 10 * 60_000L)),
            midnight
        )
        assertEquals(0, s.weekCount)
        assertEquals(10L, s.totalMinutes)
    }

    @Test fun `top channels are ranked and capped`() {
        val s = LibraryStats.summarize(
            listOf(
                watch(midnight, who = "A"), watch(midnight, who = "A"), watch(midnight, who = "A"),
                watch(midnight, who = "B"), watch(midnight, who = "B"),
                watch(midnight, who = "C"),
                watch(midnight, who = "D"),
            ),
            midnight, topChannelLimit = 2
        )
        assertEquals(listOf("A" to 3, "B" to 2), s.topChannels)
    }

    // Blank uploaders came through as a nameless bar at the top of the
    // leaderboard whenever a few rows were missing their channel.
    @Test fun `blank uploaders are not a channel`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight, who = ""), watch(midnight, who = "   "), watch(midnight, who = "A")),
            midnight
        )
        assertEquals(listOf("A" to 1), s.topChannels)
    }

    @Test fun `negative positions cannot subtract from watch time`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight, ms = 60_000L), watch(midnight, ms = -999_000L)),
            midnight
        )
        assertEquals(1L, s.totalMinutes)
        assertEquals(1L, s.weekMinutes)
    }

    @Test fun `empty history summarizes to zeroes, not an exception`() {
        val s = LibraryStats.summarize(emptyList(), midnight)
        assertEquals(0, s.todayCount)
        assertEquals(0, s.weekCount)
        assertEquals(0L, s.totalMinutes)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), s.dayCounts)
        assertTrue(s.topChannels.isEmpty())
        assertTrue(s.isEmpty)
    }

    // isEmpty decides whether the dashboard opens expanded. History that is all
    // older than a week is still history worth showing.
    @Test fun `old history is not an empty summary`() {
        val s = LibraryStats.summarize(
            listOf(watch(midnight - 30 * day, ms = 5 * 60_000L)),
            midnight
        )
        assertFalse(s.isEmpty)
    }

    @Test fun `minutes format switches to hours at sixty`() {
        assertEquals("0m", LibraryStats.formatMinutes(0))
        assertEquals("59m", LibraryStats.formatMinutes(59))
        assertEquals("1h 0m", LibraryStats.formatMinutes(60))
        assertEquals("1h 35m", LibraryStats.formatMinutes(95))
        assertEquals("0m", LibraryStats.formatMinutes(-5))
    }
}
