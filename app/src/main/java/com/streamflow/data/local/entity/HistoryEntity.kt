package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Indexed on watchedAt, which every read of this table orders by.
 *
 * Without it both list queries were a full scan plus a sort of the whole table.
 * That is cheap once and expensive continuously, because the player writes a
 * resume position into this table every 15 seconds of playback -- and a write
 * invalidates the table, so Room re-runs every observing query. Home's
 * "Continue watching" is one of them. So on a phone with a few thousand rows
 * (history retention defaults to "keep forever") watching a film meant
 * re-scanning and re-sorting the entire history four times a minute, for the
 * ten rows on screen.
 *
 * With the index the LIMITed query walks watchedAt in reverse and stops, and
 * the ORDER BY disappears from both plans. The cost is one b-tree update per
 * history write, against a table whose reads outnumber its writes many times
 * over.
 */
@Entity(tableName = "history", indices = [Index("watchedAt")])
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val viewCount: Long,
    val duration: Long,
    val watchedAt: Long = System.currentTimeMillis(),
    val position: Long = 0L
)
