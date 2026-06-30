package com.streamflow.data.local.dao

import androidx.room.*
import com.streamflow.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE url = :url")
    fun isDownloaded(url: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE url = :url")
    suspend fun delete(url: String)
}
