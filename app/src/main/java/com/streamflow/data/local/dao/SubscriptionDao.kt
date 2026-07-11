package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelUrl = :channelUrl)")
    fun isSubscribed(channelUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channelUrl = :channelUrl")
    suspend fun delete(channelUrl: String)

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun count(): Flow<Int>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAllOnce(): List<SubscriptionEntity>

    @Query("UPDATE subscriptions SET lastVideoUrl = :videoUrl WHERE channelUrl = :channelUrl")
    suspend fun updateLastVideo(channelUrl: String, videoUrl: String)

    @Query("UPDATE subscriptions SET groupName = :group WHERE channelUrl = :channelUrl")
    suspend fun updateGroup(channelUrl: String, group: String)

    @Query("UPDATE subscriptions SET notify = :notify WHERE channelUrl = :channelUrl")
    suspend fun updateNotify(channelUrl: String, notify: Boolean)
}
