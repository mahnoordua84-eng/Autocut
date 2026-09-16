package com.example.domain.model

import kotlin.math.roundToLong

/**
 * Dedicated Time Coordinator strictly separating:
 * 1. Source Media Time (Clock within the raw media asset)
 * 2. Timeline Time (Master sequence ruler position)
 * 3. Playback Time (Active CTI transport clock driving the engine & player)
 */
object TimelineTimeCoordinator {

  /**
   * Converts a master Timeline timestamp (microseconds) to the corresponding Source Media timestamp (microseconds).
   * Takes into account:
   * - Clip timeline start offset
   * - Clip speed multiplier
   * - Variable speed curve preset / bezier points
   * - Reversed playback flag
   * - Freeze frame
   * - Source In and Source Out boundaries
   */
  fun timelineToSourceTimeUs(timelineUs: Long, clip: TimelineClip): Long {
    // If freeze frame is active
    clip.freezeFrameAtUs?.let { freezeTime ->
      return freezeTime.coerceIn(clip.sourceInUs, clip.sourceOutUs)
    }

    val offsetUs = (timelineUs - clip.timelineStartUs).coerceIn(0L, clip.timelineDurationUs)
    val durationUs = clip.timelineDurationUs.coerceAtLeast(1L)
    val normalizedT = (offsetUs.toDouble() / durationUs.toDouble()).toFloat().coerceIn(0f, 1f)

    // Evaluate speed curve multiplier
    val curveFactor = if (clip.speedCurve.preset != SpeedCurvePreset.STANDARD) {
      evaluateSpeedCurveFactor(normalizedT, clip.speedCurve)
    } else 1.0f

    val effectiveSpeed = (clip.speed * curveFactor).coerceAtLeast(0.001)
    val scaledOffsetUs = (offsetUs * effectiveSpeed).roundToLong()

    return if (clip.isReversed) {
      (clip.sourceOutUs - scaledOffsetUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
    } else {
      (clip.sourceInUs + scaledOffsetUs).coerceIn(clip.sourceInUs, clip.sourceOutUs)
    }
  }

  /**
   * Converts Source Media timestamp (microseconds) back to master Timeline timestamp (microseconds).
   */
  fun sourceToTimelineTimeUs(sourceUs: Long, clip: TimelineClip): Long {
    val boundedSourceUs = sourceUs.coerceIn(clip.sourceInUs, clip.sourceOutUs)
    val deltaUs = if (clip.isReversed) {
      (clip.sourceOutUs - boundedSourceUs).coerceAtLeast(0L)
    } else {
      (boundedSourceUs - clip.sourceInUs).coerceAtLeast(0L)
    }

    val effectiveSpeed = clip.speed.coerceAtLeast(0.001)
    val timelineOffsetUs = (deltaUs / effectiveSpeed).roundToLong()
    return (clip.timelineStartUs + timelineOffsetUs).coerceIn(clip.timelineStartUs, clip.timelineEndUs)
  }

  /**
   * Synchronizes Playback Time with Timeline Time.
   * Clamps playback CTI within timeline duration bounds.
   */
  fun playbackToTimelineTimeUs(playbackTime: PlaybackTime, timelineDurationUs: Long): TimelineTime {
    val clamped = playbackTime.micros.coerceIn(0L, timelineDurationUs.coerceAtLeast(0L))
    return TimelineTime(clamped)
  }

  /**
   * Converts Timeline Time to Playback Time.
   */
  fun timelineToPlaybackTimeUs(timelineTime: TimelineTime): PlaybackTime {
    return PlaybackTime(timelineTime.micros.coerceAtLeast(0L))
  }

  /**
   * Determines if a clip is active and visible at the given timeline timestamp.
   */
  fun isClipVisibleAt(clip: TimelineClip, track: TimelineTrack?, timelineUs: Long): Boolean {
    if (!clip.isVisible || !clip.isEnabled || clip.isLocked) return false
    if (track != null && (track.isHidden || track.isLocked)) return false
    return clip.containsTimelineTime(timelineUs)
  }

  /**
   * Determines if a clip should output audio at the given timeline timestamp.
   */
  fun isClipAudibleAt(clip: TimelineClip, track: TimelineTrack?, timelineUs: Long): Boolean {
    if (!clip.isEnabled || clip.isMuted || clip.volume <= 0f) return false
    if (track != null && (track.isMuted || track.volume <= 0f)) return false
    return clip.containsTimelineTime(timelineUs)
  }

  /**
   * Calculates effective audio volume for a clip at a timeline timestamp,
   * taking into account keyframes, clip volume, and track volume/mute/solo.
   */
  fun calculateEffectiveVolume(
    clip: TimelineClip,
    track: TimelineTrack?,
    timelineUs: Long,
    anyTrackSoloed: Boolean = false
  ): Float {
    if (clip.isMuted || !clip.isEnabled) return 0f
    if (track != null) {
      if (track.isMuted) return 0f
      if (anyTrackSoloed && !track.isSolo) return 0f
    }

    val clipBaseVol = clip.volume.coerceIn(0f, 2f)
    val trackVol = track?.volume?.coerceIn(0f, 2f) ?: 1f

    // Keyframe interpolation for volume if present
    val keyframeVol = if (clip.keyframes.isNotEmpty()) {
      val clipLocalMs = (timelineUs - clip.timelineStartUs) / 1000L
      evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.volume }
    } else 1.0f

    return (clipBaseVol * trackVol * keyframeVol).coerceIn(0f, 4f)
  }

  /**
   * Calculates effective transform for a clip at a timeline timestamp,
   * interpolating keyframes smoothly.
   */
  fun calculateEffectiveTransform(clip: TimelineClip, timelineUs: Long): NormalizedTransform {
    if (clip.keyframes.isEmpty()) {
      return clip.transform
    }

    val clipLocalMs = (timelineUs - clip.timelineStartUs) / 1000L
    val base = clip.transform

    val posX = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.posX }
    val posY = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.posY }
    val scaleX = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.scaleX }
    val scaleY = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.scaleY }
    val rotation = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.rotation }
    val opacity = evaluateKeyframedProperty(clip.keyframes, clipLocalMs) { it.opacity }

    return base.copy(
      normalizedX = posX,
      normalizedY = posY,
      scaleX = scaleX,
      scaleY = scaleY,
      rotation = rotation,
      opacity = opacity.coerceIn(0f, 1f)
    )
  }

  /**
   * Evaluates speed curve factor at normalized position [0..1].
   */
  private fun evaluateSpeedCurveFactor(normalizedT: Float, curve: SpeedCurve): Float {
    val t = normalizedT.coerceIn(0f, 1f)
    return when (curve.preset) {
      SpeedCurvePreset.EASE_IN -> t * t
      SpeedCurvePreset.EASE_OUT -> 1f - (1f - t) * (1f - t)
      SpeedCurvePreset.HERO_MONTAGE -> if (t < 0.33f || t > 0.66f) 2.2f else 0.4f
      SpeedCurvePreset.BULLET_TIME -> if (t in 0.25f..0.75f) 0.25f else 1.8f
      SpeedCurvePreset.JUMPER -> if (t < 0.5f) 2.0f else 0.5f
      SpeedCurvePreset.CUSTOM_BEZIER -> {
        val pts = curve.bezierPoints.ifEmpty { listOf(0.42f, 0f, 0.58f, 1f) }
        val p1y = pts.getOrNull(1) ?: 0.0f
        val p2y = pts.getOrNull(3) ?: 1.0f
        3f * (1f - t) * (1f - t) * t * p1y + 3f * (1f - t) * t * t * p2y + t * t * t
      }
      else -> 1.0f
    }
  }

  /**
   * Interpolates an arbitrary float property across keyframes with easing support.
   */
  private inline fun evaluateKeyframedProperty(
    keyframes: List<ClipKeyframe>,
    timeMs: Long,
    propertySelector: (ClipKeyframe) -> Float
  ): Float {
    if (keyframes.isEmpty()) return 1.0f
    if (keyframes.size == 1) return propertySelector(keyframes[0])

    val sorted = keyframes.sortedBy { it.timeMs }
    if (timeMs <= sorted.first().timeMs) return propertySelector(sorted.first())
    if (timeMs >= sorted.last().timeMs) return propertySelector(sorted.last())

    for (i in 0 until sorted.size - 1) {
      val k1 = sorted[i]
      val k2 = sorted[i + 1]
      if (timeMs in k1.timeMs..k2.timeMs) {
        val dt = (k2.timeMs - k1.timeMs).coerceAtLeast(1L)
        val progress = ((timeMs - k1.timeMs).toFloat() / dt.toFloat()).coerceIn(0f, 1f)
        val eased = applyInterpolation(progress, k1.interpolation)
        val v1 = propertySelector(k1)
        val v2 = propertySelector(k2)
        return v1 + (v2 - v1) * eased
      }
    }
    return propertySelector(sorted.last())
  }

  private fun applyInterpolation(t: Float, interpolation: KeyframeInterpolation): Float {
    return when (interpolation) {
      KeyframeInterpolation.LINEAR -> t
      KeyframeInterpolation.EASE_IN -> t * t
      KeyframeInterpolation.EASE_OUT -> 1f - (1f - t) * (1f - t)
      KeyframeInterpolation.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
      KeyframeInterpolation.HOLD -> if (t < 1.0f) 0f else 1f
      else -> t
    }
  }
}
