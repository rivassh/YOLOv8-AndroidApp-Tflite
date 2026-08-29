package com.nazar.yolov8android.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nazar.yolov8android.BoundingBox

@Entity(tableName = "package_events")
data class PackageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackingId: Int,
    val packageName: String,
    val timestamp: Long,
    val eventType: String,
    val confidence: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val enteredLoadingZone: Boolean = false,
    val ocrText: String = "",
    val speechText: String = "",
    val status: String = "PACKAGE_LOADED",
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): com.nazar.yolov8android.ObjectTracker.InternalPackageEvent {
        return com.nazar.yolov8android.ObjectTracker.InternalPackageEvent(
            trackingId = trackingId,
            packageName = packageName,
            timestamp = timestamp,
            eventType = com.nazar.yolov8android.ObjectTracker.InternalPackageEvent.EventType.valueOf(eventType),
            confidence = confidence,
            boundingBox = BoundingBox(x1, y1, x2, y2, (x1 + x2) / 2, (y1 + y2) / 2, x2 - x1, y2 - y1, confidence, 0, packageName),
            enteredLoadingZone = enteredLoadingZone
        )
    }

    companion object {
        fun fromDomain(event: com.nazar.yolov8android.ObjectTracker.InternalPackageEvent): PackageEventEntity {
            return PackageEventEntity(
                trackingId = event.trackingId,
                packageName = event.packageName,
                timestamp = event.timestamp,
                eventType = event.eventType.name,
                confidence = event.confidence,
                x1 = event.boundingBox.x1,
                y1 = event.boundingBox.y1,
                x2 = event.boundingBox.x2,
                y2 = event.boundingBox.y2,
                enteredLoadingZone = event.enteredLoadingZone,
                ocrText = "",
                speechText = ""
            )
        }
    }
}