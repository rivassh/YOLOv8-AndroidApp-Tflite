package com.nazar.yolov8android.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageEventId: Long,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastAttempt: Long? = null,
    val errorMessage: String? = null,
    val status: String = "PENDING"
) {
    enum class Status {
        PENDING,
        SYNCING,
        COMPLETED,
        FAILED
    }
}