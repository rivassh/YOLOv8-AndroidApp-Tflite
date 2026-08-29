package com.nazar.yolov8android.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_sessions")
data class DetectionSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionStart: Long,
    val sessionEnd: Long? = null,
    val totalDetections: Int = 0,
    val locationX: Float = 0f,
    val locationY: Float = 0f
)