package com.nazar.yolov8android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageEventDao {
    @Insert
    suspend fun insert(event: PackageEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<PackageEventEntity>): List<Long>

    @Query("SELECT * FROM package_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<PackageEventEntity>>

    @Query("SELECT * FROM package_events WHERE synced = 0 ORDER BY timestamp ASC LIMIT 100")
    fun getUnsyncedEvents(): Flow<List<PackageEventEntity>>

    @Query("UPDATE package_events SET synced = 1 WHERE id = :eventId")
    suspend fun markAsSynced(eventId: Long)

    @Update
    suspend fun update(event: PackageEventEntity)

    @Query("DELETE FROM package_events WHERE id IN (:eventIds)")
    suspend fun deleteByIds(eventIds: List<Long>)
}