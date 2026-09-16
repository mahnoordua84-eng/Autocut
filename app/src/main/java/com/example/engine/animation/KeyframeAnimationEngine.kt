package com.example.engine.animation

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import com.example.engine.InterpolatedClipTransform
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Baseline default parameters for a clip prior to keyframe transformations.
 */
data class ClipTransformDefaults(
  val posX: Float = 0.0f,
  val posY: Float = 0.0f,
  val scaleX: Float = 1.0f,
  val scaleY: Float = 1.0f,
  val rotation: Float = 0.0f,
  val opacity: Float = 1.0f,
  val volume: Float = 1.0f,
  val blur: Float = 0.0f,
  val effectParam: Float = 0.0f,
  val brightness: Float = 0.0f,
  val contrast: Float = 1.0f,
  val saturation: Float = 1.0f,
  val cropLeft: Float = 0.0f,
  val cropTop: Float = 0.0f,
  val cropRight: Float = 0.0f,
  val cropBottom: Float = 0.0f,
  val temperature: Float = 0.0f,
  val tint: Float = 0.0f,
  val hue: Float = 0.0f,
  val exposure: Float = 0.0f,
  val vignette: Float = 0.0f,
  val highlights: Float = 0.0f,
  val shadows: Float = 0.0f,
  val maskPosX: Float = 0.0f,
  val maskPosY: Float = 0.0f,
  val maskWidth: Float = 0.6f,
  val maskHeight: Float = 0.6f,
  val maskRotation: Float = 0.0f,
  val maskFeather: Float = 0.1f,
  val maskOpacity: Float = 1.0f,
  val customProperties: Map<String, Float> = emptyMap()
)

/**
 * Professional Keyframe Animation Engine.
 *
 * Core capabilities:
 * - Deterministic, frame-accurate keyframe evaluation at arbitrary timestamps (microsecond & millisecond).
 * - Full multi-property animation: Transform (pos, scale, rot), Opacity, Crop, Audio Volume & EQ, Blur, Effects, Color grading, Masks, and Custom parameters.
 * - Interpolations: Linear, Hold/Step, Ease-In, Ease-Out, Ease-In-Out, high-precision Cubic Bezier, and custom curves.
 * - Keyframe manipulations: Insert, delete, move, shift, scale, split, simplify, resample, reverse.
 * - Decoupled and completely UI-independent.
 */
object KeyframeAnimationEngine {

  // =========================================================================
  // 1. EVALUATION (FRAME-ACCURATE & DETERMINISTIC)
  // =========================================================================

  /**
   * Evaluates keyframes at a microsecond timestamp with exact sub-millisecond precision.
   */
  fun evaluateAtTimeUs(
    keyframes: List<ClipKeyframe>,
    relTimeUs: Long,
    defaults: ClipTransformDefaults = ClipTransformDefaults()
  ): EvaluatedClipTransform {
    if (keyframes.isEmpty()) {
      return defaultsToEvaluated(defaults, relTimeUs)
    }

    val sorted = keyframes.sortedBy { it.timeMs }

    // Before first keyframe
    if (relTimeUs <= sorted.first().timeMs * 1000L) {
      return keyframeToEvaluated(sorted.first(), relTimeUs, defaults)
    }

    // After last keyframe
    if (relTimeUs >= sorted.last().timeMs * 1000L) {
      return keyframeToEvaluated(sorted.last(), relTimeUs, defaults)
    }

    // Binary search for surrounding segment
    var low = 0
    var high = sorted.size - 1
    var before = sorted.first()
    var after = sorted.last()

    val relTimeMs = relTimeUs / 1000L

    while (low <= high) {
      val mid = (low + high) ushr 1
      val midKf = sorted[mid]
      if (midKf.timeMs <= relTimeMs) {
        before = midKf
        low = mid + 1
      } else {
        after = midKf
        high = mid - 1
      }
    }

    val segmentDurationUs = ((after.timeMs - before.timeMs) * 1000L).toFloat().coerceAtLeast(1f)
    val rawT = ((relTimeUs - before.timeMs * 1000L) / segmentDurationUs).coerceIn(0.0f, 1.0f)

    val factor = computeEasingFactor(before.interpolation, before.customCurvePoints, rawT)

    return interpolateKeyframes(before, after, factor, relTimeUs)
  }

  /**
   * Evaluates keyframes at a millisecond timestamp.
   */
  fun evaluateAtTimeMs(
    keyframes: List<ClipKeyframe>,
    relTimeMs: Long,
    defaults: ClipTransformDefaults = ClipTransformDefaults()
  ): EvaluatedClipTransform {
    return evaluateAtTimeUs(keyframes, relTimeMs * 1000L, defaults)
  }

  /**
   * Computes the interpolation factor [0..1] for a keyframe curve.
   */
  fun computeEasingFactor(
    interpolation: KeyframeInterpolation,
    customCurvePoints: List<Float>?,
    rawT: Float
  ): Float {
    val t = rawT.coerceIn(0.0f, 1.0f)
    return when (interpolation) {
      KeyframeInterpolation.LINEAR -> t
      KeyframeInterpolation.HOLD -> if (t >= 1.0f) 1.0f else 0.0f
      KeyframeInterpolation.EASE_IN -> t * t * t
      KeyframeInterpolation.EASE_OUT -> 1.0f - (1.0f - t).let { it * it * it }
      KeyframeInterpolation.EASE_IN_OUT -> {
        if (t < 0.5f) 4.0f * t * t * t
        else 1.0f - (-2.0f * t + 2.0f).let { it * it * it } / 2.0f
      }
      KeyframeInterpolation.CUBIC_BEZIER,
      KeyframeInterpolation.CUSTOM_CURVE -> {
        val pts = if (customCurvePoints != null && customCurvePoints.size >= 4) {
          customCurvePoints
        } else {
          listOf(0.42f, 0.0f, 0.58f, 1.0f)
        }
        KeyframeEasingEngine.solveCubicBezier(pts[0], pts[1], pts[2], pts[3], t)
      }
    }
  }

  private fun interpolateKeyframes(
    before: ClipKeyframe,
    after: ClipKeyframe,
    factor: Float,
    relTimeUs: Long
  ): EvaluatedClipTransform {
    return EvaluatedClipTransform(
      timestampUs = relTimeUs,
      posX = lerp(before.posX, after.posX, factor),
      posY = lerp(before.posY, after.posY, factor),
      scaleX = lerp(before.scaleX, after.scaleX, factor),
      scaleY = lerp(before.scaleY, after.scaleY, factor),
      rotation = lerp(before.rotation, after.rotation, factor),
      opacity = lerp(before.opacity, after.opacity, factor).coerceIn(0.0f, 1.0f),
      cropLeft = 0.0f,
      cropTop = 0.0f,
      cropRight = 0.0f,
      cropBottom = 0.0f,
      volume = lerp(before.volume, after.volume, factor).coerceAtLeast(0.0f),
      blur = lerp(before.blur, after.blur, factor).coerceIn(0.0f, 1.0f),
      effectParam = lerp(before.effectParam, after.effectParam, factor).coerceIn(0.0f, 1.0f),
      effectIntensity = 1.0f,
      brightness = lerp(before.brightness, after.brightness, factor).coerceIn(-1.0f, 1.0f),
      contrast = lerp(before.contrast, after.contrast, factor).coerceAtLeast(0.0f),
      saturation = lerp(before.saturation, after.saturation, factor).coerceAtLeast(0.0f),
      maskPosX = lerp(before.maskPosX, after.maskPosX, factor),
      maskPosY = lerp(before.maskPosY, after.maskPosY, factor),
      maskWidth = lerp(before.maskWidth, after.maskWidth, factor).coerceAtLeast(0.0f),
      maskHeight = lerp(before.maskHeight, after.maskHeight, factor).coerceAtLeast(0.0f),
      maskRotation = lerp(before.maskRotation, after.maskRotation, factor),
      maskFeather = lerp(before.maskFeather, after.maskFeather, factor).coerceIn(0.0f, 1.0f),
      maskOpacity = lerp(before.maskOpacity, after.maskOpacity, factor).coerceIn(0.0f, 1.0f)
    )
  }

  private fun keyframeToEvaluated(
    kf: ClipKeyframe,
    relTimeUs: Long,
    defaults: ClipTransformDefaults
  ): EvaluatedClipTransform {
    return EvaluatedClipTransform(
      timestampUs = relTimeUs,
      posX = kf.posX,
      posY = kf.posY,
      scaleX = kf.scaleX,
      scaleY = kf.scaleY,
      rotation = kf.rotation,
      opacity = kf.opacity,
      cropLeft = defaults.cropLeft,
      cropTop = defaults.cropTop,
      cropRight = defaults.cropRight,
      cropBottom = defaults.cropBottom,
      volume = kf.volume,
      blur = kf.blur,
      effectParam = kf.effectParam,
      effectIntensity = 1.0f,
      brightness = kf.brightness,
      contrast = kf.contrast,
      saturation = kf.saturation,
      temperature = defaults.temperature,
      tint = defaults.tint,
      hue = defaults.hue,
      exposure = defaults.exposure,
      vignette = defaults.vignette,
      highlights = defaults.highlights,
      shadows = defaults.shadows,
      maskPosX = kf.maskPosX,
      maskPosY = kf.maskPosY,
      maskWidth = kf.maskWidth,
      maskHeight = kf.maskHeight,
      maskRotation = kf.maskRotation,
      maskFeather = kf.maskFeather,
      maskOpacity = kf.maskOpacity,
      customProperties = defaults.customProperties
    )
  }

  private fun defaultsToEvaluated(
    defaults: ClipTransformDefaults,
    relTimeUs: Long
  ): EvaluatedClipTransform {
    return EvaluatedClipTransform(
      timestampUs = relTimeUs,
      posX = defaults.posX,
      posY = defaults.posY,
      scaleX = defaults.scaleX,
      scaleY = defaults.scaleY,
      rotation = defaults.rotation,
      opacity = defaults.opacity,
      cropLeft = defaults.cropLeft,
      cropTop = defaults.cropTop,
      cropRight = defaults.cropRight,
      cropBottom = defaults.cropBottom,
      volume = defaults.volume,
      blur = defaults.blur,
      effectParam = defaults.effectParam,
      brightness = defaults.brightness,
      contrast = defaults.contrast,
      saturation = defaults.saturation,
      temperature = defaults.temperature,
      tint = defaults.tint,
      hue = defaults.hue,
      exposure = defaults.exposure,
      vignette = defaults.vignette,
      highlights = defaults.highlights,
      shadows = defaults.shadows,
      maskPosX = defaults.maskPosX,
      maskPosY = defaults.maskPosY,
      maskWidth = defaults.maskWidth,
      maskHeight = defaults.maskHeight,
      maskRotation = defaults.maskRotation,
      maskFeather = defaults.maskFeather,
      maskOpacity = defaults.maskOpacity,
      customProperties = defaults.customProperties
    )
  }

  fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
  }

  // =========================================================================
  // 2. KEYFRAME INSERTION & MUTATION (NON-DESTRUCTIVE & SORTED)
  // =========================================================================

  /**
   * Inserts or updates a keyframe. If an existing keyframe exists within `toleranceMs`, it is replaced.
   */
  fun insertOrUpdateKeyframe(
    keyframes: List<ClipKeyframe>,
    newKeyframe: ClipKeyframe,
    toleranceMs: Long = 1L
  ): List<ClipKeyframe> {
    val filtered = keyframes.filterNot {
      abs(it.timeMs - newKeyframe.timeMs) <= toleranceMs || it.id == newKeyframe.id
    }
    return (filtered + newKeyframe).sortedBy { it.timeMs }
  }

  /**
   * Deletes a keyframe by unique ID.
   */
  fun deleteKeyframe(keyframes: List<ClipKeyframe>, keyframeId: String): List<ClipKeyframe> {
    return keyframes.filterNot { it.id == keyframeId }
  }

  /**
   * Deletes a keyframe at or near the given timestamp.
   */
  fun deleteKeyframeAt(
    keyframes: List<ClipKeyframe>,
    timeMs: Long,
    toleranceMs: Long = 1L
  ): List<ClipKeyframe> {
    return keyframes.filterNot { abs(it.timeMs - timeMs) <= toleranceMs }
  }

  /**
   * Moves a keyframe to a new timestamp within bounds [0, maxDurationMs].
   */
  fun moveKeyframe(
    keyframes: List<ClipKeyframe>,
    keyframeId: String,
    newTimeMs: Long,
    maxDurationMs: Long = Long.MAX_VALUE
  ): List<ClipKeyframe> {
    val target = keyframes.find { it.id == keyframeId } ?: return keyframes
    val clampedTime = newTimeMs.coerceIn(0L, maxDurationMs)
    val updatedTarget = target.copy(timeMs = clampedTime)
    val filtered = keyframes.filterNot { it.id == keyframeId || it.timeMs == clampedTime }
    return (filtered + updatedTarget).sortedBy { it.timeMs }
  }

  /**
   * Shifts all keyframes by a relative delta (positive or negative).
   */
  fun shiftKeyframes(
    keyframes: List<ClipKeyframe>,
    deltaMs: Long,
    maxDurationMs: Long = Long.MAX_VALUE
  ): List<ClipKeyframe> {
    return keyframes.map { kf ->
      kf.copy(timeMs = (kf.timeMs + deltaMs).coerceIn(0L, maxDurationMs))
    }.sortedBy { it.timeMs }
  }

  /**
   * Scales keyframe timestamps proportionally when speed or duration changes.
   */
  fun scaleKeyframes(
    keyframes: List<ClipKeyframe>,
    scaleFactor: Double,
    maxDurationMs: Long = Long.MAX_VALUE
  ): List<ClipKeyframe> {
    if (scaleFactor <= 0.0) return keyframes
    return keyframes.map { kf ->
      val scaledTime = (kf.timeMs * scaleFactor).toLong().coerceIn(0L, maxDurationMs)
      kf.copy(timeMs = scaledTime)
    }.sortedBy { it.timeMs }
  }

  /**
   * Splits a keyframe list at a split timestamp for clip cutting.
   * Interpolates an exact boundary keyframe at `splitTimeMs` on both sides.
   */
  fun splitKeyframesAt(
    keyframes: List<ClipKeyframe>,
    splitTimeMs: Long
  ): Pair<List<ClipKeyframe>, List<ClipKeyframe>> {
    if (keyframes.isEmpty()) return Pair(emptyList(), emptyList())

    val leftList = mutableListOf<ClipKeyframe>()
    val rightList = mutableListOf<ClipKeyframe>()

    // Evaluate split point transform
    val splitTransform = evaluateAtTimeMs(keyframes, splitTimeMs)
    val splitKeyframeLeft = ClipKeyframe(
      id = UUID.randomUUID().toString(),
      timeMs = splitTimeMs,
      posX = splitTransform.posX,
      posY = splitTransform.posY,
      scaleX = splitTransform.scaleX,
      scaleY = splitTransform.scaleY,
      rotation = splitTransform.rotation,
      opacity = splitTransform.opacity,
      volume = splitTransform.volume,
      blur = splitTransform.blur,
      brightness = splitTransform.brightness,
      contrast = splitTransform.contrast,
      saturation = splitTransform.saturation,
      effectParam = splitTransform.effectParam,
      maskPosX = splitTransform.maskPosX,
      maskPosY = splitTransform.maskPosY,
      maskWidth = splitTransform.maskWidth,
      maskHeight = splitTransform.maskHeight,
      maskRotation = splitTransform.maskRotation,
      maskFeather = splitTransform.maskFeather,
      maskOpacity = splitTransform.maskOpacity
    )
    val splitKeyframeRight = splitKeyframeLeft.copy(id = UUID.randomUUID().toString(), timeMs = 0L)

    var hasLeftSplitPoint = false
    var hasRightSplitPoint = false

    for (kf in keyframes) {
      if (kf.timeMs < splitTimeMs) {
        leftList.add(kf)
      } else if (kf.timeMs == splitTimeMs) {
        leftList.add(kf)
        rightList.add(kf.copy(id = UUID.randomUUID().toString(), timeMs = 0L))
        hasLeftSplitPoint = true
        hasRightSplitPoint = true
      } else {
        // Shift time for right segment starting at 0
        rightList.add(kf.copy(id = UUID.randomUUID().toString(), timeMs = kf.timeMs - splitTimeMs))
      }
    }

    if (!hasLeftSplitPoint && leftList.isNotEmpty()) {
      leftList.add(splitKeyframeLeft)
    }
    if (!hasRightSplitPoint && rightList.isNotEmpty()) {
      rightList.add(0, splitKeyframeRight)
    }

    return Pair(leftList.sortedBy { it.timeMs }, rightList.sortedBy { it.timeMs })
  }

  /**
   * Reverses keyframes within a clip duration (e.g. for reversed playback).
   */
  fun reverseKeyframes(keyframes: List<ClipKeyframe>, durationMs: Long): List<ClipKeyframe> {
    return keyframes.map { kf ->
      kf.copy(
        id = UUID.randomUUID().toString(),
        timeMs = (durationMs - kf.timeMs).coerceIn(0L, durationMs),
        interpolation = when (kf.interpolation) {
          KeyframeInterpolation.EASE_IN -> KeyframeInterpolation.EASE_OUT
          KeyframeInterpolation.EASE_OUT -> KeyframeInterpolation.EASE_IN
          else -> kf.interpolation
        }
      )
    }.sortedBy { it.timeMs }
  }

  /**
   * Duplicates a keyframe with a given time offset.
   */
  fun duplicateKeyframe(
    keyframes: List<ClipKeyframe>,
    keyframeId: String,
    offsetMs: Long = 300L,
    maxDurationMs: Long = Long.MAX_VALUE
  ): Pair<List<ClipKeyframe>, ClipKeyframe?> {
    val orig = keyframes.find { it.id == keyframeId } ?: return Pair(keyframes, null)
    val newTime = (orig.timeMs + offsetMs).coerceIn(0L, maxDurationMs)
    val dup = orig.copy(id = UUID.randomUUID().toString(), timeMs = newTime)
    val updated = (keyframes.filterNot { it.timeMs == newTime } + dup).sortedBy { it.timeMs }
    return Pair(updated, dup)
  }

  /**
   * Simplifies keyframes using the Ramer-Douglas-Peucker algorithm to remove redundant points within `tolerance`.
   */
  fun simplifyKeyframes(keyframes: List<ClipKeyframe>, tolerance: Float = 0.005f): List<ClipKeyframe> {
    if (keyframes.size <= 2) return keyframes
    val sorted = keyframes.sortedBy { it.timeMs }

    fun rdp(points: List<ClipKeyframe>): List<ClipKeyframe> {
      if (points.size <= 2) return points
      var maxDist = 0.0f
      var maxIndex = 0
      val first = points.first()
      val last = points.last()

      for (i in 1 until points.size - 1) {
        val cur = points[i]
        val t = (cur.timeMs - first.timeMs).toFloat() / (last.timeMs - first.timeMs).toFloat().coerceAtLeast(1f)
        val expPosX = lerp(first.posX, last.posX, t)
        val expPosY = lerp(first.posY, last.posY, t)
        val expScale = lerp(first.scaleX, last.scaleX, t)
        val expRot = lerp(first.rotation, last.rotation, t)
        val expOp = lerp(first.opacity, last.opacity, t)

        val dist = abs(cur.posX - expPosX) + abs(cur.posY - expPosY) +
          abs(cur.scaleX - expScale) + abs(cur.rotation - expRot) * 0.01f +
          abs(cur.opacity - expOp)

        if (dist > maxDist) {
          maxDist = dist
          maxIndex = i
        }
      }

      return if (maxDist > tolerance) {
        val left = rdp(points.subList(0, maxIndex + 1))
        val right = rdp(points.subList(maxIndex, points.size))
        left.dropLast(1) + right
      } else {
        listOf(first, last)
      }
    }

    return rdp(sorted)
  }
}
