package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, filePath = :filePath WHERE downloadId = :downloadId")
    suspend fun updateByDownloadId(downloadId: Long, status: String, filePath: String)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun delete(url: String)

    // Removes only the tapped row — delete(url) would take the other format
    // (video vs audio) of the same video down with it
    @Query("DELETE FROM downloads WHERE url = :url AND isAudio = :isAudio")
    suspend fun deleteVariant(url: String, isAudio: Boolean)

    @Query("SELECT COUNT(*) FROM downloads")
    fun count(): Flow<Int>
}
