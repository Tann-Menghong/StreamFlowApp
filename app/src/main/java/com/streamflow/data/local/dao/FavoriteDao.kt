package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    fun isFavorite(url: String): Flow<Boolean>

    // Snapshot for swipe-to-delete undo (preserves savedAt so undo keeps list order)
    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Flow<Int>
}
