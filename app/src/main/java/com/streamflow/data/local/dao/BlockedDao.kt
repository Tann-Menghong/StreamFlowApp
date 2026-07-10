package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.BlockedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked_items ORDER BY blockedAt DESC")
    fun getAll(): Flow<List<BlockedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedItemEntity)

    @Query("DELETE FROM blocked_items WHERE itemKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM blocked_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM blocked_items")
    fun count(): Flow<Int>
}
