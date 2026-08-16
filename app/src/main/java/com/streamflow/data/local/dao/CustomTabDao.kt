package com.streamflow.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.streamflow.data.local.entity.CustomTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomTabDao {
    @Query("SELECT * FROM custom_tabs ORDER BY position ASC, createdAt ASC")
    fun getAll(): Flow<List<CustomTabEntity>>

    @Query("SELECT * FROM custom_tabs WHERE id = :id")
    suspend fun getById(id: Long): CustomTabEntity?

    @Query("SELECT COUNT(*) FROM custom_tabs")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: CustomTabEntity): Long

    @Update
    suspend fun update(tab: CustomTabEntity)

    @Delete
    suspend fun delete(tab: CustomTabEntity)

    @Query("DELETE FROM custom_tabs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Next free slot, so a new tab lands at the end instead of fighting for 0. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM custom_tabs")
    suspend fun nextPosition(): Int
}
