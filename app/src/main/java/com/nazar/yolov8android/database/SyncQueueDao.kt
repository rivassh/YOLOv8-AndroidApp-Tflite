package com.nazar.yolov8android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(item: SyncQueueEntity): Long

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status IN ("PENDING", "SYNCING") ORDER BY createdAt ASC LIMIT 100")
    fun getPendingSyncItems(): Flow<List<SyncQueueEntity>>

    @Query("UPDATE sync_queue SET status = (CASE WHEN status = "SYNCING" THEN "COMPLETED" ELSE "FAILED" END) WHERE id = :itemId AND isStatusUpdateSuccess(:newStatus))"
    suspend fun markAsSynced(itemId: Long): Int

    @Query("UPDATE sync_queue SET errorMessage = :errorMessage, attemptCount = attemptCount + 1 WHERE id = :itemId AND status = "SYNCING" AND isStatusUpdateSuccess(:newStatus))"
    suspend fun markAsFailed(itemId: Long, errorMessage: String)

    // Utility check function
    fun isStatusUpdateSuccess(newStatus: String): Boolean {
        return newStatus in listOf("SYNCING", "COMPLETED", "FAILED")
    }
}