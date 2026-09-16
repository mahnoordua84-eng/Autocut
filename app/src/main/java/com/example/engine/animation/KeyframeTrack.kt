package com.example.engine.animation

import kotlin.math.abs

/**
 * An isolated, deterministic, single-property animation track.
 * Maintains keyframes in strict chronological order with microsecond precision.
 */
data class KeyframeTrack(
  val propertyKey: String,
  val keyframes: List<PropertyKeyframe> = emptyList(),
  val extrapolationMode: ExtrapolationMode = ExtrapolationMode.HOLD,
  val defaultValue: Float = 0.0f
) {

  val isEmpty: Boolean get() = keyframes.isEmpty()
  val isNotEmpty: Boolean get() = keyframes.isNotEmpty()
  val count: Int get() = keyframes.size
  val firstKeyframe: PropertyKeyframe? get() = keyframes.firstOrNull()
  val lastKeyframe: PropertyKeyframe? get() = keyframes.lastOrNull()
  val startTimestampUs: Long get() = keyframes.firstOrNull()?.timestampUs ?: 0L
  val endTimestampUs: Long get() = keyframes.lastOrNull()?.timestampUs ?: 0L

  /**
   * Evaluates the property value at any arbitrary timeline timestamp (in microseconds).
   * Frame-accurate and deterministic.
   */
  fun evaluate(timeUs: Long): Float {
    if (keyframes.isEmpty()) return defaultValue

    val first = keyframes.first()
    val last = keyframes.last()

    // Before first keyframe
    if (timeUs <= first.timestampUs) {
      return when (extrapolationMode) {
        ExtrapolationMode.HOLD -> first.value
        ExtrapolationMode.CYCLE -> {
          val duration = (last.timestampUs - first.timestampUs).coerceAtLeast(1L)
          val offset = (timeUs - first.timestampUs) % duration
          val loopedTime = if (offset < 0) first.timestampUs + duration + offset else first.timestampUs + offset
          evaluate(loopedTime)
        }
        ExtrapolationMode.PING_PONG -> {
          val duration = (last.timestampUs - first.timestampUs).coerceAtLeast(1L)
          val cycle = ((timeUs - first.timestampUs) / duration)
          val remainder = (timeUs - first.timestampUs) % duration
          val positiveRem = if (remainder < 0) duration + remainder else remainder
          val loopedTime = if (abs(cycle) % 2L == 1L) last.timestampUs - positiveRem else first.timestampUs + positiveRem
          evaluate(loopedTime)
        }
        ExtrapolationMode.LINEAR_EXTRAPOLATION -> {
          if (keyframes.size == 1) return first.value
          val second = keyframes[1]
          val dt = (second.timestampUs - first.timestampUs).toFloat()
          if (dt <= 0f) return first.value
          val slope = (second.value - first.value) / dt
          first.value + slope * (timeUs - first.timestampUs)
        }
      }
    }

    // After last keyframe
    if (timeUs >= last.timestampUs) {
      return when (extrapolationMode) {
        ExtrapolationMode.HOLD -> last.value
        ExtrapolationMode.CYCLE -> {
          val duration = (last.timestampUs - first.timestampUs).coerceAtLeast(1L)
          val offset = (timeUs - first.timestampUs) % duration
          evaluate(first.timestampUs + offset)
        }
        ExtrapolationMode.PING_PONG -> {
          val duration = (last.timestampUs - first.timestampUs).coerceAtLeast(1L)
          val cycle = (timeUs - first.timestampUs) / duration
          val remainder = (timeUs - first.timestampUs) % duration
          val loopedTime = if (cycle % 2L == 1L) last.timestampUs - remainder else first.timestampUs + remainder
          evaluate(loopedTime)
        }
        ExtrapolationMode.LINEAR_EXTRAPOLATION -> {
          if (keyframes.size == 1) return last.value
          val prev = keyframes[keyframes.size - 2]
          val dt = (last.timestampUs - prev.timestampUs).toFloat()
          if (dt <= 0f) return last.value
          val slope = (last.value - prev.value) / dt
          last.value + slope * (timeUs - last.timestampUs)
        }
      }
    }

    // Binary search for surrounding keyframe segment
    var low = 0
    var high = keyframes.size - 1
    var before = first
    var after = last

    while (low <= high) {
      val mid = (low + high) ushr 1
      val midKf = keyframes[mid]
      if (midKf.timestampUs <= timeUs) {
        before = midKf
        low = mid + 1
      } else {
        after = midKf
        high = mid - 1
      }
    }

    if (before.id == after.id || before.timestampUs == after.timestampUs) {
      return before.value
    }

    // Normalized progress between [before, after]
    val segmentDuration = (after.timestampUs - before.timestampUs).toFloat().coerceAtLeast(1f)
    val rawT = ((timeUs - before.timestampUs) / segmentDuration).coerceIn(0.0f, 1.0f)
    val easedT = KeyframeEasingEngine.evaluate(before.easing, rawT, before.bezierCurve)

    return before.value + (after.value - before.value) * easedT
  }

  /**
   * Evaluates the velocity (rate of change in value units per second) at timestamp.
   */
  fun evaluateVelocity(timeUs: Long, deltaUs: Long = 10_000L): Float {
    val t0 = (timeUs - deltaUs / 2).coerceAtLeast(0L)
    val t1 = t0 + deltaUs
    val v0 = evaluate(t0)
    val v1 = evaluate(t1)
    val dtSec = deltaUs / 1_000_000.0f
    return (v1 - v0) / dtSec
  }

  /**
   * Inserts or updates a keyframe. If an existing keyframe is within `toleranceUs`, it is replaced.
   */
  fun insertKeyframe(newKeyframe: PropertyKeyframe, toleranceUs: Long = 1000L): KeyframeTrack {
    val filtered = keyframes.filterNot {
      abs(it.timestampUs - newKeyframe.timestampUs) <= toleranceUs || it.id == newKeyframe.id
    }
    val updated = (filtered + newKeyframe).sortedBy { it.timestampUs }
    return copy(keyframes = updated)
  }

  /**
   * Deletes a keyframe by its unique ID.
   */
  fun deleteKeyframe(keyframeId: String): KeyframeTrack {
    val updated = keyframes.filterNot { it.id == keyframeId }
    return copy(keyframes = updated)
  }

  /**
   * Deletes a keyframe at or near the given timestamp.
   */
  fun deleteKeyframeAt(timestampUs: Long, toleranceUs: Long = 1000L): KeyframeTrack {
    val updated = keyframes.filterNot { abs(it.timestampUs - timestampUs) <= toleranceUs }
    return copy(keyframes = updated)
  }

  /**
   * Deletes all keyframes in the range [startUs, endUs].
   */
  fun deleteKeyframesInRange(startUs: Long, endUs: Long): KeyframeTrack {
    val updated = keyframes.filterNot { it.timestampUs in startUs..endUs }
    return copy(keyframes = updated)
  }

  /**
   * Moves a keyframe to a new timestamp while maintaining sorted order.
   */
  fun moveKeyframe(
    keyframeId: String,
    newTimestampUs: Long,
    minUs: Long = 0L,
    maxUs: Long = Long.MAX_VALUE
  ): KeyframeTrack {
    val clampedUs = newTimestampUs.coerceIn(minUs, maxUs)
    val targetKf = keyframes.find { it.id == keyframeId } ?: return this
    val updatedKf = targetKf.copy(timestampUs = clampedUs)
    val filtered = keyframes.filterNot { it.id == keyframeId || it.timestampUs == clampedUs }
    val updated = (filtered + updatedKf).sortedBy { it.timestampUs }
    return copy(keyframes = updated)
  }

  /**
   * Shifts all keyframes by a time delta in microseconds.
   */
  fun shiftKeyframes(deltaUs: Long, minUs: Long = 0L, maxUs: Long = Long.MAX_VALUE): KeyframeTrack {
    val updated = keyframes.map { kf ->
      kf.copy(timestampUs = (kf.timestampUs + deltaUs).coerceIn(minUs, maxUs))
    }.sortedBy { it.timestampUs }
    return copy(keyframes = updated)
  }

  /**
   * Scales keyframe timing (e.g. for clip speed adjustments).
   */
  fun scaleKeyframes(scaleFactor: Double, anchorUs: Long = 0L): KeyframeTrack {
    if (scaleFactor <= 0.0) return this
    val updated = keyframes.map { kf ->
      val rel = (kf.timestampUs - anchorUs) * scaleFactor
      kf.copy(timestampUs = (anchorUs + rel).toLong().coerceAtLeast(0L))
    }.sortedBy { it.timestampUs }
    return copy(keyframes = updated)
  }

  /**
   * Finds the exact or closest keyframe within `toleranceUs`.
   */
  fun findKeyframeAt(timestampUs: Long, toleranceUs: Long = 1000L): PropertyKeyframe? {
    return keyframes.minByOrNull { abs(it.timestampUs - timestampUs) }?.takeIf {
      abs(it.timestampUs - timestampUs) <= toleranceUs
    }
  }

  /**
   * Returns the immediate previous keyframe before `timestampUs`.
   */
  fun getPreviousKeyframe(timestampUs: Long): PropertyKeyframe? {
    return keyframes.filter { it.timestampUs < timestampUs }.maxByOrNull { it.timestampUs }
  }

  /**
   * Returns the immediate next keyframe after `timestampUs`.
   */
  fun getNextKeyframe(timestampUs: Long): PropertyKeyframe? {
    return keyframes.filter { it.timestampUs > timestampUs }.minByOrNull { it.timestampUs }
  }

  /**
   * Returns bounding keyframe pair surrounding `timestampUs`.
   */
  fun findBoundingKeyframes(timestampUs: Long): Pair<PropertyKeyframe, PropertyKeyframe>? {
    if (keyframes.size < 2) return null
    for (i in 0 until keyframes.size - 1) {
      if (timestampUs >= keyframes[i].timestampUs && timestampUs <= keyframes[i + 1].timestampUs) {
        return Pair(keyframes[i], keyframes[i + 1])
      }
    }
    return null
  }
}
