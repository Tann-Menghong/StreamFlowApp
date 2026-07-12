package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun getAll(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM history")
    fun count(): Flow<Int>

    // position is ms, duration is seconds, so duration*950 == 95% of the video in ms.
    // Exclude videos watched past 95%: the player restarts those from 0 on tap (like
    // YouTube), so a near-full "Continue watching" card that restarts was misleading.
    // duration<=0 (unknown/live) still shows so real progress isn't hidden.
    @Query("SELECT * FROM history WHERE position > 30000 AND (duration <= 0 OR position < duration * 950) ORDER BY watchedAt DESC LIMIT :limit")
    fun getRecentWithProgress(limit: Int): Flow<List<HistoryEntity>>

    @Query("UPDATE history SET position = :pos WHERE url = :url")
    suspend fun updatePosition(url: String, pos: Long)

    @Query("SELECT position FROM history WHERE url = :url LIMIT 1")
    suspend fun getPosition(url: String): Long

    @Query("DELETE FROM history WHERE watchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
