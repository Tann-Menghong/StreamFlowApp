package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BookmarkEntity>>

    // Markers on the player seekbar for the video being watched
    @Query("SELECT * FROM bookmarks WHERE videoUrl = :url ORDER BY positionMs ASC")
    fun getForVideo(url: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}
