package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchLaterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE url = :url)")
    fun isInWatchLater(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM watch_later")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM watch_later")
    fun count(): Flow<Int>
}
