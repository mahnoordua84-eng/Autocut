package com.example.domain.model

import java.util.UUID
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Result of evaluating a child clip inside a compound/nested clip at parent timeline time T.
 */
data class NestedClipTimeMapping(
  val parentTimelineUs: Long,
  val compoundClipId: String,
  val compoundLocalTimeUs: Long,
  val childTrackId: String,
  val childClip: TimelineClip,
  val childSourceTimeUs: Long,
  val cascadedTransform: NormalizedTransform,
  val cascadedOpacity: Float,
  val cascadedVolume: Float,
  val nestingDepth: Int = 1
) {
  val parentTimelineMs: Long get() = parentTimelineUs / 1000L
  val compoundLocalTimeMs: Long get() = compoundLocalTimeUs / 1000L
  val childSourceTimeMs: Long get() = childSourceTimeUs / 1000L
}

/**
 * Professional Compound Timeline Time Coordinator & Mapper.
 *
 * Implements the 3-Tier Hierarchical Time Domain:
 * Parent Timeline Time (Master sequence ruler)
 *   → Compound Local Time (Sub-timeline ruler within the compound container)
 *     → Child Clip Source Time (Clock within the raw child media asset)
 *
 * Designed to seamlessly support arbitrary recursive nesting depths,
 * variable speed curves, reverse playback, in/out trimming, and cascading transforms/keyframes.
 */
object CompoundTimelineTimeMapper {

  /**
   * Maps parent timeline timestamp (microseconds) to local compound timeline timestamp (microseconds).
   *
   * @param parentTimelineUs Master timeline position in microseconds.
   * @param compoundClip The container compound clip.
   * @return Compound local time in microseconds, or null if the parent time falls outside the compound clip.
   */
  fun parentToCompoundLocalTimeUs(
    parentTimelineUs: Long,
    compoundClip: TimelineClip,
    clampToBounds: Boolean = false
  ): Long? {
    if (!clampToBounds && !compoundClip.containsTimelineTime(parentTimelineUs)) {
      return null
    }

    if (compoundClip.freezeFrameAtUs != null) {
      return compoundClip.freezeFrameAtUs.coerceIn(compoundClip.sourceInUs, compoundClip.sourceOutUs)
    }

    val offsetUs = (parentTimelineUs - compoundClip.timelineStartUs).coerceIn(0L, compoundClip.timelineDurationUs)
    val durationUs = compoundClip.timelineDurationUs.coerceAtLeast(1L)
    val normalizedT = (offsetUs.toDouble() / durationUs.toDouble()).toFloat().coerceIn(0f, 1f)

    // Evaluate speed curves if configured on compound container
    val curveFactor = if (compoundClip.speedCurve.preset != SpeedCurvePreset.STANDARD) {
      evaluateSpeedCurveFactor(normalizedT, compoundClip.speedCurve)
    } else 1.0f

    val effectiveSpeed = (compoundClip.speed * curveFactor).coerceAtLeast(0.001)
    val scaledOffsetUs = (offsetUs * effectiveSpeed).roundToLong()

    return if (compoundClip.isReversed) {
      (compoundClip.sourceOutUs - scaledOffsetUs).coerceIn(compoundClip.sourceInUs, compoundClip.sourceOutUs)
    } else {
      (compoundClip.sourceInUs + scaledOffsetUs).coerceIn(compoundClip.sourceInUs, compoundClip.sourceOutUs)
    }
  }

  /**
   * Maps compound local timestamp (microseconds) to child clip source timestamp (microseconds).
   */
  fun compoundLocalToChildSourceTimeUs(
    compoundLocalUs: Long,
    childClip: TimelineClip
  ): Long? {
    if (!childClip.containsTimelineTime(compoundLocalUs)) {
      return null
    }
    return TimelineTimeCoordinator.timelineToSourceTimeUs(compoundLocalUs, childClip)
  }

  /**
   * Maps child clip source timestamp back to compound local timeline timestamp.
   */
  fun childSourceToCompoundLocalTimeUs(
    childSourceUs: Long,
    childClip: TimelineClip
  ): Long {
    return TimelineTimeCoordinator.sourceToTimelineTimeUs(childSourceUs, childClip)
  }

  /**
   * Maps compound local timestamp back to parent master timeline timestamp.
   */
  fun compoundLocalToParentTimeUs(
    compoundLocalUs: Long,
    compoundClip: TimelineClip
  ): Long {
    val boundedLocalUs = compoundLocalUs.coerceIn(compoundClip.sourceInUs, compoundClip.sourceOutUs)
    val deltaUs = if (compoundClip.isReversed) {
      (compoundClip.sourceOutUs - boundedLocalUs).coerceAtLeast(0L)
    } else {
      (boundedLocalUs - compoundClip.sourceInUs).coerceAtLeast(0L)
    }

    val effectiveSpeed = compoundClip.speed.coerceAtLeast(0.001)
    val parentOffsetUs = (deltaUs / effectiveSpeed).roundToLong()
    return (compoundClip.timelineStartUs + parentOffsetUs).coerceIn(compoundClip.timelineStartUs, compoundClip.timelineEndUs)
  }

  /**
   * Recursively evaluates all active child clips within a compound clip at parent timestamp [parentTimelineUs].
   * Recursion allows compound clips to contain nested compound clips to arbitrary depth.
   */
  fun evaluateNestedClipsAtParentTime(
    parentTimelineUs: Long,
    compoundClip: TimelineClip,
    parentTransform: NormalizedTransform = NormalizedTransform(),
    parentOpacity: Float = 1.0f,
    parentVolume: Float = 1.0f,
    currentDepth: Int = 1
  ): List<NestedClipTimeMapping> {
    val innerState = compoundClip.nestedTimeline ?: return emptyList()
    val compoundLocalUs = parentToCompoundLocalTimeUs(parentTimelineUs, compoundClip) ?: return emptyList()

    val compoundEffectiveTransform = TimelineTimeCoordinator.calculateEffectiveTransform(compoundClip, parentTimelineUs)
    val compoundEffectiveOpacity = (compoundClip.opacity * compoundEffectiveTransform.opacity * parentOpacity).coerceIn(0f, 1f)
    val compoundEffectiveVolume = (compoundClip.volume * parentVolume).coerceIn(0f, 4f)

    val cascadedParentTransform = combineTransforms(parentTransform, compoundEffectiveTransform)

    val results = mutableListOf<NestedClipTimeMapping>()

    for (track in innerState.orderedTracks()) {
      if (track.isHidden) continue

      val activeClips = track.clips.filter { it.isEnabled && it.containsTimelineTime(compoundLocalUs) }
      for (childClip in activeClips) {
        val childEffectiveTransform = TimelineTimeCoordinator.calculateEffectiveTransform(childClip, compoundLocalUs)
        val combinedChildTransform = combineTransforms(cascadedParentTransform, childEffectiveTransform)
        val childEffectiveOpacity = (childClip.opacity * childEffectiveTransform.opacity * compoundEffectiveOpacity).coerceIn(0f, 1f)
        val childEffectiveVolume = (childClip.volume * track.volume * compoundEffectiveVolume).coerceIn(0f, 4f)

        if (childClip.isCompound && childClip.nestedTimeline != null) {
          // Recursive evaluation for nested compound clips
          val deeperNested = evaluateNestedClipsAtParentTime(
            parentTimelineUs = compoundLocalUs, // local time becomes parent time for deeper level
            compoundClip = childClip,
            parentTransform = cascadedParentTransform,
            parentOpacity = compoundEffectiveOpacity,
            parentVolume = compoundEffectiveVolume,
            currentDepth = currentDepth + 1
          )
          results.addAll(deeperNested)
        } else {
          val childSourceUs = TimelineTimeCoordinator.timelineToSourceTimeUs(compoundLocalUs, childClip)
          results.add(
            NestedClipTimeMapping(
              parentTimelineUs = parentTimelineUs,
              compoundClipId = compoundClip.id,
              compoundLocalTimeUs = compoundLocalUs,
              childTrackId = track.id,
              childClip = childClip,
              childSourceTimeUs = childSourceUs,
              cascadedTransform = combinedChildTransform,
              cascadedOpacity = childEffectiveOpacity,
              cascadedVolume = childEffectiveVolume,
              nestingDepth = currentDepth
            )
          )
        }
      }
    }

    return results
  }

  /**
   * Combines two normalized transforms hierarchically (parent container -> child element).
   */
  fun combineTransforms(parent: NormalizedTransform, child: NormalizedTransform): NormalizedTransform {
    val totalRotation = (parent.rotation + child.rotation) % 360f
    val totalScaleX = parent.scaleX * child.scaleX
    val totalScaleY = parent.scaleY * child.scaleY

    // Apply parent rotation to child's normalized translation offset
    val rad = Math.toRadians(parent.rotation.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    val rotatedChildX = child.normalizedX * cosR - child.normalizedY * sinR
    val rotatedChildY = child.normalizedX * sinR + child.normalizedY * cosR

    val combinedX = parent.normalizedX + (rotatedChildX * parent.scaleX)
    val combinedY = parent.normalizedY + (rotatedChildY * parent.scaleY)

    return NormalizedTransform(
      normalizedX = combinedX.coerceIn(-2f, 2f),
      normalizedY = combinedY.coerceIn(-2f, 2f),
      scaleX = totalScaleX.coerceIn(0.01f, 10f),
      scaleY = totalScaleY.coerceIn(0.01f, 10f),
      rotation = totalRotation,
      opacity = (parent.opacity * child.opacity).coerceIn(0f, 1f),
      anchorX = child.anchorX,
      anchorY = child.anchorY,
      cropLeft = maxOf(parent.cropLeft, child.cropLeft),
      cropTop = maxOf(parent.cropTop, child.cropTop),
      cropRight = maxOf(parent.cropRight, child.cropRight),
      cropBottom = maxOf(parent.cropBottom, child.cropBottom),
      flipX = parent.flipX xor child.flipX,
      flipY = parent.flipY xor child.flipY,
      zIndex = parent.zIndex + child.zIndex
    )
  }

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
}
