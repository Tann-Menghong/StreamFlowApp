package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastVideoUrl: String = "", // newest known video, for new-upload notifications
    val groupName: String = "",    // user-defined folder ("" = ungrouped)
    val notify: Boolean = true     // per-channel new-upload notification toggle
)
