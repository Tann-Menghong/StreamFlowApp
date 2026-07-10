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
}
