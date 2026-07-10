package com.streamflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String,
    val subscribedAt: Long = System.currentTimeMillis()
)
