package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// "Not interested" entries: itemKey is a video URL (type VIDEO) or a channel URL
// (type CHANNEL). Blocked items never appear in the home feed again.
@Entity(tableName = "blocked_items")
data class BlockedItemEntity(
    @PrimaryKey val itemKey: String,
    val type: String, // "VIDEO" | "CHANNEL"
    val name: String, // video title or channel name, for the settings screen
    val blockedAt: Long = System.currentTimeMillis()
)
