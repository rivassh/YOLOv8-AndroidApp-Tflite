package com.nazar.yolov8android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionSessionDao {
    @Insert
    suspend fun insert(session: DetectionSessionEntity): Long

    @Update
    suspend fun update(session: DetectionSessionEntity)

    @Query("SELECT * FROM detection_sessions ORDER BY sessionStart DESC LIMIT 1")
    fun getLatestSession(): Flow<DetectionSessionEntity?>

    @Query("SELECT * FROM detection_sessions WHERE sessionEnd IS NULL LIMIT 1")
    fun getActiveSession(): Flow<DetectionSessionEntity?>
}