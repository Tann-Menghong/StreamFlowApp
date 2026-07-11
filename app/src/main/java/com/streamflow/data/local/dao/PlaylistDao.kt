package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.PlaylistEntity
import com.streamflow.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val count: Int,
    val firstThumb: String?
)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.createdAt, " +
        "(SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS count, " +
        "(SELECT i.thumbnailUrl FROM playlist_items i WHERE i.playlistId = p.id ORDER BY i.addedAt ASC LIMIT 1) AS firstThumb " +
        "FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT name FROM playlists WHERE id = :id")
    suspend fun getName(id: Long): String?

    @Insert
    suspend fun create(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getItemsOnce(playlistId: Long): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND url = :url")
    suspend fun removeItem(playlistId: Long, url: String)

    // Items are ordered by addedAt, so swapping two timestamps reorders them
    @Query("UPDATE playlist_items SET addedAt = :addedAt WHERE playlistId = :playlistId AND url = :url")
    suspend fun setAddedAt(playlistId: Long, url: String, addedAt: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: Long)
}
