package com.nazar.yolov8android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

class ObjectTracker(
    private val context: Context,
    private val minTrackFrames: Int = 3,
    private val maxTrackAge: Long = 5000L,
    private val loadingZoneTop: Float = 0.3f,
    private val loadingZoneBottom: Float = 0.7f,
    private val loadingZoneLeft: Float = 0.2f,
    private val loadingZoneRight: Float = 0.8f
) {
    private val activeTracks = mutableMapOf<Int, Track>()
    private val nextTrackId = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = 0L

    data class Track(
        val id: Int,
        var boundingBox: BoundingBox,
        var confidence: Float,
        var className: String,
        var lastSeen: Long,
        var stableCount: Int = 0,
        var loadingZoneEntered: Boolean = false
    ) {
        fun isStable(): Boolean = stableCount >= minTrackFrames
        fun isExpired(): Boolean = System.currentTimeMillis() - lastSeen > maxTrackAge
    }

    // PackageEvent for tracking events (internal use)
    data class InternalPackageEvent(
        val trackingId: Int,
        val packageName: String,
        val timestamp: Long,
        val eventType: EventType,
        val confidence: Float,
        val boundingBox: BoundingBox,
        val enteredLoadingZone: Boolean = false
    ) {
        enum class EventType {
            PACKAGE_DETECTED,
            PACKAGE_LOADED,
            PACKAGE_EXITED_LOADING_ZONE
        }
    }

    private val packageEvents = mutableListOf<InternalPackageEvent>()
    private val loadingZoneRects = mutableListOf<Rect>()

    init {
        calculateLoadingZoneRects()
    }

    private fun calculateLoadingZoneRects() {
        loadingZoneRects.clear()
        loadingZoneRects.add(Rect(
            top = loadingZoneTop,
            bottom = loadingZoneBottom,
            left = loadingZoneLeft,
            right = loadingZoneRight
        ))
    }

    fun updateTracks(detectedBoxes: List<BoundingBox>, timestamp: Long): List<PackageEvent> {
        val events = mutableListOf<PackageEvent>()
        val currentFrameTracks = mutableMapOf<Int, Track>()

        // Match existing tracks with new detections
        for (track in activeTracks.values) {
            track.lastSeen = timestamp
            val bestMatch = findBestMatch(detectedBoxes, track)
            
            if (bestMatch != null) {
                track.boundingBox = bestMatch.box
                track.confidence = bestMatch.cnf
                track.stableCount++
                currentFrameTracks[track.id] = track

// Check loading zone
                 val entered = isInLoadingZone(bestMatch.box)
                 if (entered && !track.loadingZoneEntered) {
                     track.loadingZoneEntered = true
                     events.add(createPackageLoadedEvent(track, bestMatch))
                 } else if (!entered && track.loadingZoneEntered) {
                     track.loadingZoneEntered = false
                     events.add(InternalPackageEvent(
                         trackingId = track.id,
                         packageName = bestMatch.clsName,
                         timestamp = timestamp,
                         eventType = EventType.PACKAGE_EXITED_LOADING_ZONE,
                         confidence = bestMatch.cnf,
                         boundingBox = bestMatch.box,
                         enteredLoadingZone = false
                     ))
                 }
            } else {
// Track expired
                 if (track.isStable()) {
                     events.add(InternalPackageEvent(
                         trackingId = track.id,
                         packageName = track.className,
                         timestamp = timestamp,
                         eventType = EventType.PACKAGE_EXITED_LOADING_ZONE,
                         confidence = track.confidence,
                         boundingBox = track.boundingBox,
                         enteredLoadingZone = false
                     ))
                 }
            }
        }

// Create new tracks for unmatched detections
         for (box in detectedBoxes) {
             val unmatched = activeTracks.values.none { findBestMatch(listOf(box), it) != null }
             if (unmatched) {
                 val newId = nextTrackId.removeFirstOrNull() ?: System.currentTimeMillis().toInt()
                 val newTrack = Track(
                     id = newId,
                     boundingBox = box,
                     confidence = box.cnf,
                     className = box.clsName,
                     lastSeen = timestamp,
                     stableCount = 1
                 )
                 currentFrameTracks[newId] = newTrack

                 if (newTrack.isStable()) {
                     events.add(InternalPackageEvent(
                         trackingId = newId,
                         packageName = box.clsName,
                         timestamp = timestamp,
                         eventType = EventType.PACKAGE_DETECTED,
                         confidence = box.cnf,
                         boundingBox = box
                     ))
                 }
             }
         }

        // Remove expired tracks
        val expiredIds = activeTracks.keys.filter { activeTracks[it]?.isExpired() == true }
        expiredIds.forEach { activeTracks.remove(it) }

        // Update active tracks
        activeTracks.clear()
        activeTracks.putAll(currentFrameTracks)

        // Return unique events only
        return deduplicateEvents(events)
    }

    private fun findBestMatch(detectedBoxes: List<BoundingBox>, track: Track): Pair<BoundingBox, Float>? {
        if (track.isExpired()) return null

        var bestMatch: Pair<BoundingBox, Float>? = null
        var bestScore = 0f

        for (box in detectedBoxes) {
            val centerDistance = calculateCenterDistance(box, track.boundingBox)
            val sizeSimilarity = calculateSizeSimilarity(box, track.boundingBox)
            val score = (1f - centerDistance) * 0.7f + sizeSimilarity * 0.3f

            if (score > bestScore && score > 0.5f) {
                bestScore = score
                bestMatch = Pair(box, score)
            }
        }

        return bestMatch
    }

    private fun calculateCenterDistance(box1: BoundingBox, box2: BoundingBox): Float {
        val cx1 = (box1.x1 + box1.x2) / 2f
        val cy1 = (box1.y1 + box1.y2) / 2f
        val cx2 = (box2.x1 + box2.x2) / 2f
        val cy2 = (box2.y1 + box2.y2) / 2f

        val dx = cx1 - cx2
        val dy = cy1 - cy2
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateSizeSimilarity(box1: BoundingBox, box2: BoundingBox): Float {
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val maxArea = maxOf(area1, area2)
        return if (maxArea > 0) minOf(area1, area2) / maxArea else 0f
    }

    private fun isInLoadingZone(box: BoundingBox): Boolean {
        val centerX = (box.x1 + box.x2) / 2f
        val centerY = (box.y1 + box.y2) / 2f

        for (zone in loadingZoneRects) {
            if (centerX >= zone.left && centerX <= zone.right &&
                centerY >= zone.top && centerY <= zone.bottom) {
                return true
            }
        }
        return false
    }

    private fun createPackageLoadedEvent(track: Track, box: BoundingBox): PackageEvent {
        return PackageEvent(
            trackingId = track.id,
            packageName = track.className,
            timestamp = track.lastSeen,
            eventType = EventType.PACKAGE_LOADED,
            confidence = track.confidence,
            boundingBox = track.boundingBox,
            enteredLoadingZone = true
        )
    }

    private fun deduplicateEvents(events: List<InternalPackageEvent>): List<InternalPackageEvent> {
        val uniqueEvents = mutableListOf<InternalPackageEvent>()
        val processed = mutableSetOf<Pair<Int, InternalPackageEvent.EventType>>() // (trackingId, eventType)

        for (event in events) {
            val key = Pair(event.trackingId, event.eventType)
            if (!processed.contains(key)) {
                processed.add(key)
                uniqueEvents.add(event)
            }
        }

        return uniqueEvents
    }

    fun getLoadingZoneRect(): Rect {
        return if (loadingZoneRects.isNotEmpty()) loadingZoneRects.first() else Rect(0.2f, 0.3f, 0.7f, 0.8f)
    }

    fun getConsumedEvents(): List<InternalPackageEvent> {
        val events = packageEvents
        packageEvents.clear()
        return events
    }

    fun getEvents(): List<InternalPackageEvent> {
        return packageEvents
    }

    fun clear() {
        activeTracks.clear()
        nextTrackId.clear()
    }

    data class Rect(
        val top: Float,
        val bottom: Float,
        val left: Float,
        val right: Float
    )

    fun addDebugTrack() {
        val debugId = nextTrackId.removeFirstOrNull() ?: System.currentTimeMillis().toInt()
        val debugTrack = Track(
            id = debugId,
            boundingBox = BoundingBox(0.5f, 0.5f, 0.6f, 0.6f, 0.55f, 0.55f, 0.1f, 0.1f, 0.9f, 0, "TEST"),
            confidence = 0.9f,
            className = "TEST",
            lastSeen = System.currentTimeMillis(),
            stableCount = minTrackFrames
        )
        activeTracks[debugId] = debugTrack
    }
}
