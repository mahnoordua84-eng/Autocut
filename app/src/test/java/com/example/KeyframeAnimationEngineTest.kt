package com.example

import com.example.domain.model.ClipKeyframe
import com.example.domain.model.KeyframeInterpolation
import com.example.engine.animation.*
import org.junit.Assert.*
import org.junit.Test

class KeyframeAnimationEngineTest {

  @Test
  fun testLinearInterpolation() {
    val kf1 = ClipKeyframe(timeMs = 0L, posX = 0.0f, posY = 0.0f, interpolation = KeyframeInterpolation.LINEAR)
    val kf2 = ClipKeyframe(timeMs = 1000L, posX = 100.0f, posY = 200.0f, interpolation = KeyframeInterpolation.LINEAR)
    val keyframes = listOf(kf1, kf2)

    val at0 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 0L)
    assertEquals(0.0f, at0.posX, 0.001f)
    assertEquals(0.0f, at0.posY, 0.001f)

    val at500 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 500L)
    assertEquals(50.0f, at500.posX, 0.001f)
    assertEquals(100.0f, at500.posY, 0.001f)

    val at1000 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 1000L)
    assertEquals(100.0f, at1000.posX, 0.001f)
    assertEquals(200.0f, at1000.posY, 0.001f)
  }

  @Test
  fun testHoldInterpolation() {
    val kf1 = ClipKeyframe(timeMs = 0L, opacity = 1.0f, interpolation = KeyframeInterpolation.HOLD)
    val kf2 = ClipKeyframe(timeMs = 1000L, opacity = 0.0f, interpolation = KeyframeInterpolation.HOLD)
    val keyframes = listOf(kf1, kf2)

    val at0 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 0L)
    assertEquals(1.0f, at0.opacity, 0.001f)

    val at500 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 500L)
    assertEquals(1.0f, at500.opacity, 0.001f) // Holds until next keyframe

    val at1000 = KeyframeAnimationEngine.evaluateAtTimeMs(keyframes, 1000L)
    assertEquals(0.0f, at1000.opacity, 0.001f)
  }

  @Test
  fun testEaseInAndEaseOutInterpolation() {
    val kfEaseIn = ClipKeyframe(timeMs = 0L, scaleX = 1.0f, interpolation = KeyframeInterpolation.EASE_IN)
    val kfEnd = ClipKeyframe(timeMs = 1000L, scaleX = 2.0f, interpolation = KeyframeInterpolation.LINEAR)

    val atMidEaseIn = KeyframeAnimationEngine.evaluateAtTimeMs(listOf(kfEaseIn, kfEnd), 500L)
    // For cubic ease-in, 0.5^3 = 0.125 -> value = 1.0 + 1.0 * 0.125 = 1.125
    assertEquals(1.125f, atMidEaseIn.scaleX, 0.01f)

    val kfEaseOut = ClipKeyframe(timeMs = 0L, scaleX = 1.0f, interpolation = KeyframeInterpolation.EASE_OUT)
    val atMidEaseOut = KeyframeAnimationEngine.evaluateAtTimeMs(listOf(kfEaseOut, kfEnd), 500L)
    // For cubic ease-out, 1 - (1-0.5)^3 = 0.875 -> value = 1.0 + 1.0 * 0.875 = 1.875
    assertEquals(1.875f, atMidEaseOut.scaleX, 0.01f)
  }

  @Test
  fun testCubicBezierSolverPrecision() {
    // Standard EaseInOut (0.42, 0.0, 0.58, 1.0)
    val factorMid = KeyframeEasingEngine.solveCubicBezier(0.42f, 0.0f, 0.58f, 1.0f, 0.5f)
    assertEquals(0.5f, factorMid, 0.005f)

    // Linear curve (0,0, 1,1)
    val factorLinear = KeyframeEasingEngine.solveCubicBezier(0.0f, 0.0f, 1.0f, 1.0f, 0.25f)
    assertEquals(0.25f, factorLinear, 0.001f)

    // Boundary conditions
    assertEquals(0.0f, KeyframeEasingEngine.solveCubicBezier(0.42f, 0.0f, 0.58f, 1.0f, 0.0f), 0.0001f)
    assertEquals(1.0f, KeyframeEasingEngine.solveCubicBezier(0.42f, 0.0f, 0.58f, 1.0f, 1.0f), 0.0001f)
  }

  @Test
  fun testMultiPropertyEvaluation() {
    val kf1 = ClipKeyframe(
      timeMs = 0L,
      posX = -1.0f,
      posY = 2.0f,
      scaleX = 0.5f,
      scaleY = 1.5f,
      rotation = 0.0f,
      opacity = 0.0f,
      volume = 0.2f,
      blur = 0.8f,
      brightness = -0.5f,
      contrast = 0.5f,
      saturation = 0.0f,
      effectParam = 0.1f
    )
    val kf2 = ClipKeyframe(
      timeMs = 1000L,
      posX = 1.0f,
      posY = 4.0f,
      scaleX = 2.5f,
      scaleY = 3.5f,
      rotation = 180.0f,
      opacity = 1.0f,
      volume = 1.0f,
      blur = 0.0f,
      brightness = 0.5f,
      contrast = 1.5f,
      saturation = 2.0f,
      effectParam = 0.9f
    )

    val mid = KeyframeAnimationEngine.evaluateAtTimeMs(listOf(kf1, kf2), 500L)
    assertEquals(0.0f, mid.posX, 0.001f)
    assertEquals(3.0f, mid.posY, 0.001f)
    assertEquals(1.5f, mid.scaleX, 0.001f)
    assertEquals(2.5f, mid.scaleY, 0.001f)
    assertEquals(90.0f, mid.rotation, 0.001f)
    assertEquals(0.5f, mid.opacity, 0.001f)
    assertEquals(0.6f, mid.volume, 0.001f)
    assertEquals(0.4f, mid.blur, 0.001f)
    assertEquals(0.0f, mid.brightness, 0.001f)
    assertEquals(1.0f, mid.contrast, 0.001f)
    assertEquals(1.0f, mid.saturation, 0.001f)
    assertEquals(0.5f, mid.effectParam, 0.001f)
  }

  @Test
  fun testKeyframeTrackOperations() {
    var track = KeyframeTrack(propertyKey = StandardAnimatableProperty.VOLUME.propertyKey, defaultValue = 1.0f)
    assertTrue(track.isEmpty)

    // 1. Insert keyframes
    track = track.insertKeyframe(PropertyKeyframe.fromMs(timeMs = 0L, propertyKey = "volume", value = 0.0f))
    track = track.insertKeyframe(PropertyKeyframe.fromMs(timeMs = 1000L, propertyKey = "volume", value = 1.0f))
    assertEquals(2, track.count)

    // 2. Evaluation
    assertEquals(0.5f, track.evaluate(500_000L), 0.001f)

    // 3. Move keyframe
    val firstId = track.keyframes[0].id
    track = track.moveKeyframe(firstId, 200_000L)
    assertEquals(200_000L, track.keyframes[0].timestampUs)

    // 4. Delete keyframe
    track = track.deleteKeyframe(firstId)
    assertEquals(1, track.count)
  }

  @Test
  fun testKeyframeTrackExtrapolationModes() {
    val kf1 = PropertyKeyframe.fromMs(timeMs = 1000L, propertyKey = "pos", value = 10f)
    val kf2 = PropertyKeyframe.fromMs(timeMs = 2000L, propertyKey = "pos", value = 20f)

    // Hold mode
    val holdTrack = KeyframeTrack(propertyKey = "pos", keyframes = listOf(kf1, kf2), extrapolationMode = ExtrapolationMode.HOLD)
    assertEquals(10f, holdTrack.evaluate(500_000L), 0.001f)
    assertEquals(20f, holdTrack.evaluate(3_000_000L), 0.001f)

    // Cycle mode
    val cycleTrack = KeyframeTrack(propertyKey = "pos", keyframes = listOf(kf1, kf2), extrapolationMode = ExtrapolationMode.CYCLE)
    assertEquals(15f, cycleTrack.evaluate(2_500_000L), 0.001f)

    // Velocity evaluation
    val velocity = holdTrack.evaluateVelocity(1_500_000L)
    assertEquals(10.0f, velocity, 0.1f) // 10 units per 1 second = 10.0 velocity
  }

  @Test
  fun testKeyframeSplittingAndSimplification() {
    val kf1 = ClipKeyframe(timeMs = 0L, posX = 0f)
    val kf2 = ClipKeyframe(timeMs = 500L, posX = 50f)
    val kf3 = ClipKeyframe(timeMs = 1000L, posX = 100f)

    val (left, right) = KeyframeAnimationEngine.splitKeyframesAt(listOf(kf1, kf2, kf3), 500L)
    assertEquals(2, left.size)
    assertEquals(500L, left.last().timeMs)
    assertEquals(50f, left.last().posX, 0.001f)

    assertEquals(2, right.size)
    assertEquals(0L, right.first().timeMs)
    assertEquals(50f, right.first().posX, 0.001f)
    assertEquals(500L, right.last().timeMs)
    assertEquals(100f, right.last().posX, 0.001f)

    // Simplification: collinear points should simplify
    val simplified = KeyframeAnimationEngine.simplifyKeyframes(listOf(kf1, kf2, kf3), tolerance = 0.01f)
    assertEquals(2, simplified.size)
    assertEquals(0L, simplified.first().timeMs)
    assertEquals(1000L, simplified.last().timeMs)
  }
}
