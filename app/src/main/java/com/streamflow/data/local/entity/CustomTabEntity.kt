package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-added website tab, shown in the bottom bar next to the built-in ones
 * and rendered by the same ad-blocking browser as Donghua / Drama / MKissa.
 *
 * [position] drives ordering so tabs can be reordered without rewriting ids.
 * [iconKey] is a stable string rather than a serialized ImageVector — vector
 * assets are code, not data, and must never end up inside the database.
 */
@Entity(tableName = "custom_tabs")
data class CustomTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val iconKey: String = "LANGUAGE",
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
