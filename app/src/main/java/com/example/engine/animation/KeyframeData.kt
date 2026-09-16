package com.example.engine.animation

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import java.util.UUID

/**
 * Extrapolation behavior when evaluating time before the first keyframe or after the last keyframe.
 */
enum class ExtrapolationMode {
  HOLD,                   // Clamp to first/last keyframe value
  CYCLE,                  // Loop animation periodically
  PING_PONG,              // Loop back and forth
  LINEAR_EXTRAPOLATION    // Continue tangent velocity indefinitely
}

/**
 * 2D Tangent Handle for Bezier curve manipulation.
 */
data class KeyframeTangent(
  val deltaX: Float = 0f, // Time offset (relative normalized or in ms)
  val deltaY: Float = 0f  // Value offset
)

/**
 * High-precision standalone property keyframe model.
 * Frame-accurate with microsecond precision and microsecond/millisecond accessors.
 */
data class PropertyKeyframe(
  val id: String = UUID.randomUUID().toString(),
  val propertyKey: String,
  val timestampUs: Long,
  val value: Float,
  val easing: EasingCurveType = EasingCurveType.LINEAR,
  val bezierCurve: CubicBezierCurve = CubicBezierCurve.EASE_IN_OUT,
  val inTangent: KeyframeTangent? = null,
  val outTangent: KeyframeTangent? = null
) {
  val timestampMs: Long get() = timestampUs / 1000L

  companion object {
    fun fromMs(
      id: String = UUID.randomUUID().toString(),
      propertyKey: String,
      timeMs: Long,
      value: Float,
      easing: EasingCurveType = EasingCurveType.LINEAR,
      bezierCurve: CubicBezierCurve = CubicBezierCurve.EASE_IN_OUT
    ): PropertyKeyframe = PropertyKeyframe(
      id = id,
      propertyKey = propertyKey,
      timestampUs = timeMs * 1000L,
      value = value,
      easing = easing,
      bezierCurve = bezierCurve
    )

    fun fromInterpolation(
      id: String = UUID.randomUUID().toString(),
      propertyKey: String,
      timestampUs: Long,
      value: Float,
      interpolation: KeyframeInterpolation,
      customCurvePoints: List<Float>? = null
    ): PropertyKeyframe {
      val easing = when (interpolation) {
        KeyframeInterpolation.LINEAR -> EasingCurveType.LINEAR
        KeyframeInterpolation.HOLD -> EasingCurveType.HOLD
        KeyframeInterpolation.EASE_IN -> EasingCurveType.EASE_IN
        KeyframeInterpolation.EASE_OUT -> EasingCurveType.EASE_OUT
        KeyframeInterpolation.EASE_IN_OUT -> EasingCurveType.EASE_IN_OUT
        KeyframeInterpolation.CUBIC_BEZIER,
        KeyframeInterpolation.CUSTOM_CURVE -> EasingCurveType.CUBIC_BEZIER
      }
      val bezier = if (customCurvePoints != null && customCurvePoints.size >= 4) {
        CubicBezierCurve.fromList(customCurvePoints)
      } else {
        CubicBezierCurve.EASE_IN_OUT
      }
      return PropertyKeyframe(
        id = id,
        propertyKey = propertyKey,
        timestampUs = timestampUs,
        value = value,
        easing = easing,
        bezierCurve = bezier
      )
    }
  }
}

/**
 * 2D Spatial Position Keyframe for smooth motion path interpolation.
 */
data class SpatialPositionKeyframe(
  val id: String = UUID.randomUUID().toString(),
  val timestampUs: Long,
  val posX: Float,
  val posY: Float,
  val easing: EasingCurveType = EasingCurveType.LINEAR,
  val bezierCurve: CubicBezierCurve = CubicBezierCurve.EASE_IN_OUT,
  // Control handles in 2D space (relative to position)
  val controlPointInX: Float? = null,
  val controlPointInY: Float? = null,
  val controlPointOutX: Float? = null,
  val controlPointOutY: Float? = null
) {
  val timestampMs: Long get() = timestampUs / 1000L
}

/**
 * Comprehensive evaluated state of all clip animatable parameters at an instant.
 */
data class EvaluatedClipTransform(
  val timestampUs: Long,
  val posX: Float = 0.0f,
  val posY: Float = 0.0f,
  val scaleX: Float = 1.0f,
  val scaleY: Float = 1.0f,
  val rotation: Float = 0.0f,
  val anchorX: Float = 0.5f,
  val anchorY: Float = 0.5f,
  val opacity: Float = 1.0f,
  val cropLeft: Float = 0.0f,
  val cropTop: Float = 0.0f,
  val cropRight: Float = 0.0f,
  val cropBottom: Float = 0.0f,
  val volume: Float = 1.0f,
  val blur: Float = 0.0f,
  val effectParam: Float = 0.0f,
  val effectIntensity: Float = 1.0f,
  val brightness: Float = 0.0f,
  val contrast: Float = 1.0f,
  val saturation: Float = 1.0f,
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
) {
  val timestampMs: Long get() = timestampUs / 1000L
  val scale: Float get() = (scaleX + scaleY) / 2.0f
}
