package com.example.engine.animation

import kotlin.math.*

/**
 * Standard interpolation curve types supported by the keyframe animation engine.
 */
enum class EasingCurveType(val displayName: String) {
  LINEAR("Linear"),
  HOLD("Hold (Constant)"),
  EASE_IN("Ease In"),
  EASE_OUT("Ease Out"),
  EASE_IN_OUT("Ease In-Out"),
  EASE_IN_QUAD("Ease In Quad"),
  EASE_OUT_QUAD("Ease Out Quad"),
  EASE_IN_OUT_QUAD("Ease In-Out Quad"),
  EASE_IN_CUBIC("Ease In Cubic"),
  EASE_OUT_CUBIC("Ease Out Cubic"),
  EASE_IN_OUT_CUBIC("Ease In-Out Cubic"),
  EASE_IN_QUART("Ease In Quart"),
  EASE_OUT_QUART("Ease Out Quart"),
  EASE_IN_OUT_QUART("Ease In-Out Quart"),
  EASE_IN_QUINT("Ease In Quint"),
  EASE_OUT_QUINT("Ease Out Quint"),
  EASE_IN_OUT_QUINT("Ease In-Out Quint"),
  EASE_IN_EXPO("Ease In Expo"),
  EASE_OUT_EXPO("Ease Out Expo"),
  EASE_IN_OUT_EXPO("Ease In-Out Expo"),
  EASE_IN_CIRC("Ease In Circ"),
  EASE_OUT_CIRC("Ease Out Circ"),
  EASE_IN_OUT_CIRC("Ease In-Out Circ"),
  EASE_IN_BACK("Ease In Back (Anticipate)"),
  EASE_OUT_BACK("Ease Out Back (Overshoot)"),
  EASE_IN_OUT_BACK("Ease In-Out Back"),
  EASE_IN_ELASTIC("Ease In Elastic"),
  EASE_OUT_ELASTIC("Ease Out Elastic"),
  EASE_IN_OUT_ELASTIC("Ease In-Out Elastic"),
  EASE_IN_BOUNCE("Ease In Bounce"),
  EASE_OUT_BOUNCE("Ease Out Bounce"),
  EASE_IN_OUT_BOUNCE("Ease In-Out Bounce"),
  CUBIC_BEZIER("Cubic Bezier"),
  CUSTOM_CURVE("Custom Curve")
}

/**
 * 2D point for Bezier and curve calculations.
 */
data class CurvePoint(val x: Float, val y: Float)

/**
 * Cubic Bezier control points definition: P0=(0,0), P1, P2, P3=(1,1).
 */
data class CubicBezierCurve(
  val p1x: Float = 0.42f,
  val p1y: Float = 0.0f,
  val p2x: Float = 0.58f,
  val p2y: Float = 1.0f
) {
  companion object {
    val LINEAR = CubicBezierCurve(0.0f, 0.0f, 1.0f, 1.0f)
    val EASE = CubicBezierCurve(0.25f, 0.1f, 0.25f, 1.0f)
    val EASE_IN = CubicBezierCurve(0.42f, 0.0f, 1.0f, 1.0f)
    val EASE_OUT = CubicBezierCurve(0.0f, 0.0f, 0.58f, 1.0f)
    val EASE_IN_OUT = CubicBezierCurve(0.42f, 0.0f, 0.58f, 1.0f)
    val FAST_OUT_SLOW_IN = CubicBezierCurve(0.4f, 0.0f, 0.2f, 1.0f)     // Material Standard
    val FAST_OUT_LINEAR_IN = CubicBezierCurve(0.4f, 0.0f, 1.0f, 1.0f)   // Material Acceleration
    val LINEAR_OUT_SLOW_IN = CubicBezierCurve(0.0f, 0.0f, 0.2f, 1.0f)   // Material Deceleration
    val OVERSHOOT = CubicBezierCurve(0.34f, 1.56f, 0.64f, 1.0f)
    val ANTICIPATE = CubicBezierCurve(0.36f, 0.0f, 0.66f, -0.56f)

    fun fromList(points: List<Float>): CubicBezierCurve {
      return if (points.size >= 4) {
        CubicBezierCurve(points[0], points[1], points[2], points[3])
      } else {
        EASE_IN_OUT
      }
    }
  }

  fun toList(): List<Float> = listOf(p1x, p1y, p2x, p2y)
}

/**
 * Core mathematical engine for easing and interpolation curve evaluation.
 * Deterministic and frame-accurate.
 */
object KeyframeEasingEngine {

  private const val BEZIER_EPSILON = 1e-6f
  private const val NEWTON_ITERATIONS = 8
  private const val BISECTION_ITERATIONS = 20

  /**
   * Evaluates normalized progress `t` in [0, 1] using the specified easing curve type and optional Bezier curve.
   */
  fun evaluate(
    curveType: EasingCurveType,
    t: Float,
    bezierCurve: CubicBezierCurve? = null
  ): Float {
    if (t <= 0.0f) return 0.0f
    if (t >= 1.0f) return 1.0f

    return when (curveType) {
      EasingCurveType.LINEAR -> t
      EasingCurveType.HOLD -> 0.0f

      // Quadratic
      EasingCurveType.EASE_IN,
      EasingCurveType.EASE_IN_QUAD -> t * t
      EasingCurveType.EASE_OUT,
      EasingCurveType.EASE_OUT_QUAD -> 1.0f - (1.0f - t) * (1.0f - t)
      EasingCurveType.EASE_IN_OUT,
      EasingCurveType.EASE_IN_OUT_QUAD -> {
        if (t < 0.5f) 2.0f * t * t
        else 1.0f - (-2.0f * t + 2.0f).let { it * it } / 2.0f
      }

      // Cubic
      EasingCurveType.EASE_IN_CUBIC -> t * t * t
      EasingCurveType.EASE_OUT_CUBIC -> 1.0f - (1.0f - t).let { it * it * it }
      EasingCurveType.EASE_IN_OUT_CUBIC -> {
        if (t < 0.5f) 4.0f * t * t * t
        else 1.0f - (-2.0f * t + 2.0f).let { it * it * it } / 2.0f
      }

      // Quartic
      EasingCurveType.EASE_IN_QUART -> t * t * t * t
      EasingCurveType.EASE_OUT_QUART -> 1.0f - (1.0f - t).let { it * it * it * it }
      EasingCurveType.EASE_IN_OUT_QUART -> {
        if (t < 0.5f) 8.0f * t * t * t * t
        else 1.0f - (-2.0f * t + 2.0f).let { it * it * it * it } / 2.0f
      }

      // Quintic
      EasingCurveType.EASE_IN_QUINT -> t * t * t * t * t
      EasingCurveType.EASE_OUT_QUINT -> 1.0f - (1.0f - t).let { it * it * it * it * it }
      EasingCurveType.EASE_IN_OUT_QUINT -> {
        if (t < 0.5f) 16.0f * t * t * t * t * t
        else 1.0f - (-2.0f * t + 2.0f).let { it * it * it * it * it } / 2.0f
      }

      // Exponential
      EasingCurveType.EASE_IN_EXPO -> 2.0.pow(10.0 * (t - 1.0)).toFloat()
      EasingCurveType.EASE_OUT_EXPO -> 1.0f - 2.0.pow(-10.0 * t).toFloat()
      EasingCurveType.EASE_IN_OUT_EXPO -> {
        if (t < 0.5f) (2.0.pow(20.0 * t - 10.0) / 2.0).toFloat()
        else ((2.0 - 2.0.pow(-20.0 * t + 10.0)) / 2.0).toFloat()
      }

      // Circular
      EasingCurveType.EASE_IN_CIRC -> 1.0f - sqrt(1.0f - t * t)
      EasingCurveType.EASE_OUT_CIRC -> sqrt(1.0f - (t - 1.0f).let { it * it })
      EasingCurveType.EASE_IN_OUT_CIRC -> {
        if (t < 0.5f) (1.0f - sqrt(1.0f - 4.0f * t * t)) / 2.0f
        else (sqrt(1.0f - (-2.0f * t + 2.0f).let { it * it }) + 1.0f) / 2.0f
      }

      // Back / Anticipate / Overshoot
      EasingCurveType.EASE_IN_BACK -> {
        val c1 = 1.70158f
        val c3 = c1 + 1.0f
        c3 * t * t * t - c1 * t * t
      }
      EasingCurveType.EASE_OUT_BACK -> {
        val c1 = 1.70158f
        val c3 = c1 + 1.0f
        val p = t - 1.0f
        1.0f + c3 * p * p * p + c1 * p * p
      }
      EasingCurveType.EASE_IN_OUT_BACK -> {
        val c1 = 1.70158f
        val c2 = c1 * 1.525f
        if (t < 0.5f) {
          (2.0f * t).let { (it * it * ((c2 + 1.0f) * it - c2)) / 2.0f }
        } else {
          (2.0f * t - 2.0f).let { (it * it * ((c2 + 1.0f) * it + c2) + 2.0f) / 2.0f }
        }
      }

      // Elastic
      EasingCurveType.EASE_IN_ELASTIC -> {
        val c4 = (2.0f * PI.toFloat()) / 3.0f
        (-2.0.pow(10.0 * (t - 1.0)) * sin((t - 1.1f) * c4)).toFloat()
      }
      EasingCurveType.EASE_OUT_ELASTIC -> {
        val c4 = (2.0f * PI.toFloat()) / 3.0f
        (2.0.pow(-10.0 * t) * sin((t - 0.1f) * c4) + 1.0).toFloat()
      }
      EasingCurveType.EASE_IN_OUT_ELASTIC -> {
        val c5 = (2.0f * PI.toFloat()) / 4.5f
        if (t < 0.5f) {
          (-0.5 * 2.0.pow(20.0 * t - 10.0) * sin((20.0 * t - 11.125) * c5)).toFloat()
        } else {
          (0.5 * 2.0.pow(-20.0 * t + 10.0) * sin((20.0 * t - 11.125) * c5) + 1.0).toFloat()
        }
      }

      // Bounce
      EasingCurveType.EASE_IN_BOUNCE -> 1.0f - evaluateBounceOut(1.0f - t)
      EasingCurveType.EASE_OUT_BOUNCE -> evaluateBounceOut(t)
      EasingCurveType.EASE_IN_OUT_BOUNCE -> {
        if (t < 0.5f) (1.0f - evaluateBounceOut(1.0f - 2.0f * t)) / 2.0f
        else (1.0f + evaluateBounceOut(2.0f * t - 1.0f)) / 2.0f
      }

      // Cubic Bezier
      EasingCurveType.CUBIC_BEZIER,
      EasingCurveType.CUSTOM_CURVE -> {
        val curve = bezierCurve ?: CubicBezierCurve.EASE_IN_OUT
        solveCubicBezier(curve.p1x, curve.p1y, curve.p2x, curve.p2y, t)
      }
    }
  }

  private fun evaluateBounceOut(t: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
      t < 1.0f / d1 -> n1 * t * t
      t < 2.0f / d1 -> {
        val p = t - 1.5f / d1
        n1 * p * p + 0.75f
      }
      t < 2.5f / d1 -> {
        val p = t - 2.25f / d1
        n1 * p * p + 0.9375f
      }
      else -> {
        val p = t - 2.625f / d1
        n1 * p * p + 0.984375f
      }
    }
  }

  /**
   * High precision Cubic Bezier solver using Newton-Raphson iteration with bisection fallback.
   * Finds parameter `u` such that X(u) == targetX, then computes Y(u).
   */
  fun solveCubicBezier(p1x: Float, p1y: Float, p2x: Float, p2y: Float, targetX: Float): Float {
    val clampedX = targetX.coerceIn(0.0f, 1.0f)
    if (clampedX <= 0.0f) return 0.0f
    if (clampedX >= 1.0f) return 1.0f

    // Initial estimate for u
    var u = clampedX

    // 1. Newton-Raphson fast convergence
    for (i in 0 until NEWTON_ITERATIONS) {
      val currentX = evaluateBezier1D(p1x, p2x, u) - clampedX
      if (abs(currentX) < BEZIER_EPSILON) {
        return evaluateBezier1D(p1y, p2y, u).coerceIn(0.0f, 1.0f)
      }
      val dx = evaluateBezierDerivative1D(p1x, p2x, u)
      if (abs(dx) < 1e-5f) break // Derivative too flat, switch to bisection
      u -= currentX / dx
      if (u < 0.0f || u > 1.0f) {
        u = clampedX
        break
      }
    }

    // 2. Bisection fallback for unconditional numerical stability
    var low = 0.0f
    var high = 1.0f
    u = clampedX
    for (i in 0 until BISECTION_ITERATIONS) {
      val currentX = evaluateBezier1D(p1x, p2x, u)
      if (abs(currentX - clampedX) < BEZIER_EPSILON) break
      if (currentX < clampedX) {
        low = u
      } else {
        high = u
      }
      u = (low + high) * 0.5f
    }

    return evaluateBezier1D(p1y, p2y, u).coerceIn(0.0f, 1.0f)
  }

  private fun evaluateBezier1D(p1: Float, p2: Float, u: Float): Float {
    val oneMinusU = 1.0f - u
    return 3.0f * oneMinusU * oneMinusU * u * p1 + 3.0f * oneMinusU * u * u * p2 + u * u * u
  }

  private fun evaluateBezierDerivative1D(p1: Float, p2: Float, u: Float): Float {
    val oneMinusU = 1.0f - u
    return 3.0f * oneMinusU * oneMinusU * p1 + 6.0f * oneMinusU * u * (p2 - p1) + 3.0f * u * u * (1.0f - p2)
  }
}
