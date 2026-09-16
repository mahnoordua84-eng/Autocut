package com.example.engine.index

import java.util.Arrays
import kotlin.math.abs

/**
 * Categorized snap point types for timeline snapping.
 */
enum class SnapType {
  CLIP_START,
  CLIP_END,
  KEYFRAME,
  TRANSITION_CUT,
  TRANSITION_START,
  TRANSITION_END,
  BEAT,
  PLAYHEAD,
  TIMELINE_BOUNDARY
}

/**
 * A discrete snap point along the timeline with microsecond/millisecond accessors.
 */
data class SnapPoint(
  val timeUs: Long,
  val type: SnapType,
  val sourceId: String? = null
) : Comparable<SnapPoint> {
  val timeMs: Long get() = timeUs / 1000L

  override fun compareTo(other: SnapPoint): Int = this.timeUs.compareTo(other.timeUs)

  companion object {
    fun fromMs(timeMs: Long, type: SnapType, sourceId: String? = null): SnapPoint =
      SnapPoint(timeUs = timeMs * 1000L, type = type, sourceId = sourceId)
  }
}

/**
 * Represents an intersection between two overlapping clips.
 */
data class ClipOverlap<T>(
  val first: T,
  val second: T,
  val overlapStartUs: Long,
  val overlapEndUs: Long
) {
  val overlapDurationUs: Long get() = (overlapEndUs - overlapStartUs).coerceAtLeast(0L)
  val overlapDurationMs: Long get() = overlapDurationUs / 1000L
}

/**
 * Generic wrapper to adapt any domain clip into an indexable TimeInterval.
 */
data class IndexedClipWrapper<T>(
  val clip: T,
  val id: String,
  override val startUs: Long,
  override val endUs: Long,
  val trackId: String = "",
  val isEnabled: Boolean = true
) : TimeInterval {
  val startMs: Long get() = startUs / 1000L
  val endMs: Long get() = endUs / 1000L
  val durationMs: Long get() = durationUs / 1000L
}
