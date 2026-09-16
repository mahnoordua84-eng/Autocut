package com.example

import com.example.domain.model.*
import com.example.engine.TimelineEngine
import com.example.engine.composition.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiLayerCompositingEngineTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setup() {
    timelineEngine = TimelineEngine()
  }

  @Test
  fun testUnlimitedVideoLayersAndTrackOrdering() {
    val track1 = TimelineTrack(
      id = "track_v1",
      name = "Background Video",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      clips = listOf(
        TimelineClip(
          id = "clip_v1",
          trackId = "track_v1",
          timelineStartUs = 0L,
          timelineDurationUs = 5_000_000L,
          sourceMediaId = "content://media/v1.mp4"
        )
      )
    )

    val track2 = TimelineTrack(
      id = "track_v2",
      name = "Middle Overlay",
      kind = TrackKind.OVERLAY,
      orderIndex = 1,
      clips = listOf(
        TimelineClip(
          id = "clip_v2",
          trackId = "track_v2",
          timelineStartUs = 1_000_000L,
          timelineDurationUs = 3_000_000L,
          sourceMediaId = "content://media/v2.mp4"
        )
      )
    )

    val track3 = TimelineTrack(
      id = "track_v3",
      name = "Top PiP Layer",
      kind = TrackKind.OVERLAY,
      orderIndex = 2,
      clips = listOf(
        TimelineClip(
          id = "clip_v3",
          trackId = "track_v3",
          timelineStartUs = 2_000_000L,
          timelineDurationUs = 2_000_000L,
          sourceMediaId = "content://media/v3.mp4"
        )
      )
    )

    val coreState = CoreTimelineState(
      durationUs = 6_000_000L,
      tracks = listOf(track1, track2, track3),
      trackOrder = listOf("track_v1", "track_v2", "track_v3")
    )

    // Evaluate at 2.5 seconds where all 3 layers overlap
    val descriptor = LayerCompositor.evaluateComposition(coreState, 2_500_000L)

    assertEquals(3, descriptor.visualLayers.size)
    // Deterministic Z-order check: track 0 (lowest) -> track 1 -> track 2 (top)
    assertEquals("clip_v1", descriptor.visualLayers[0].clipId)
    assertEquals("clip_v2", descriptor.visualLayers[1].clipId)
    assertEquals("clip_v3", descriptor.visualLayers[2].clipId)
    assertTrue(descriptor.visualLayers[0].layerZIndex < descriptor.visualLayers[1].layerZIndex)
    assertTrue(descriptor.visualLayers[1].layerZIndex < descriptor.visualLayers[2].layerZIndex)
  }

  @Test
  fun testOverlappingClipsOnSameTrack() {
    val track = TimelineTrack(
      id = "track_v1",
      name = "Main Video Track",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      clips = listOf(
        TimelineClip(
          id = "clip_a",
          trackId = "track_v1",
          layerIndex = 0,
          timelineStartUs = 0L,
          timelineDurationUs = 4_000_000L
        ),
        TimelineClip(
          id = "clip_b",
          trackId = "track_v1",
          layerIndex = 1,
          timelineStartUs = 2_000_000L,
          timelineDurationUs = 4_000_000L
        )
      )
    )

    val coreState = CoreTimelineState(
      durationUs = 6_000_000L,
      tracks = listOf(track)
    )

    // At 3.0s, both clip_a and clip_b are active and overlapping
    val descriptor = LayerCompositor.evaluateComposition(coreState, 3_000_000L)

    assertEquals(2, descriptor.visualLayers.size)
    assertEquals("clip_a", descriptor.visualLayers[0].clipId)
    assertEquals("clip_b", descriptor.visualLayers[1].clipId)
    assertTrue(descriptor.visualLayers[0].layerZIndex < descriptor.visualLayers[1].layerZIndex)
  }

  @Test
  fun testTrackSoloAndMuteAndVisibility() {
    val track1 = TimelineTrack(
      id = "track_1",
      name = "Background",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      isSolo = false,
      clips = listOf(
        TimelineClip(id = "c1", trackId = "track_1", timelineStartUs = 0L, timelineDurationUs = 5_000_000L)
      )
    )

    val track2 = TimelineTrack(
      id = "track_2",
      name = "Solo Layer",
      kind = TrackKind.VIDEO,
      orderIndex = 1,
      isSolo = true,
      clips = listOf(
        TimelineClip(id = "c2", trackId = "track_2", timelineStartUs = 0L, timelineDurationUs = 5_000_000L)
      )
    )

    val coreState = CoreTimelineState(
      durationUs = 5_000_000L,
      tracks = listOf(track1, track2)
    )

    // When track2 is soloed, only track2's clips should be in the visual composition
    val descriptor = LayerCompositor.evaluateComposition(coreState, 1_000_000L)
    assertEquals(1, descriptor.visualLayers.size)
    assertEquals("c2", descriptor.visualLayers[0].clipId)
  }

  @Test
  fun testLayerIsolation() {
    val track1 = TimelineTrack(
      id = "track_1",
      kind = TrackKind.VIDEO,
      clips = listOf(TimelineClip(id = "c1", trackId = "track_1", timelineStartUs = 0L, timelineDurationUs = 5_000_000L))
    )
    val track2 = TimelineTrack(
      id = "track_2",
      kind = TrackKind.VIDEO,
      clips = listOf(TimelineClip(id = "c2", trackId = "track_2", timelineStartUs = 0L, timelineDurationUs = 5_000_000L))
    )
    val coreState = CoreTimelineState(
      durationUs = 5_000_000L,
      tracks = listOf(track1, track2)
    )

    // Isolate clip c1 specifically
    val descriptor = LayerCompositor.evaluateComposition(coreState, 1_000_000L, isolatedClipId = "c1")
    assertEquals(1, descriptor.visualLayers.size)
    assertEquals("c1", descriptor.visualLayers[0].clipId)
    assertTrue(descriptor.visualLayers[0].isIsolated)
  }

  @Test
  fun testAlphaCompositingAndBlendModes() {
    val track = TimelineTrack(
      id = "track_blend",
      kind = TrackKind.VIDEO,
      clips = listOf(
        TimelineClip(
          id = "c_screen",
          trackId = "track_blend",
          timelineStartUs = 0L,
          timelineDurationUs = 3_000_000L,
          opacity = 0.75f,
          blendMode = "Screen"
        )
      )
    )

    val coreState = CoreTimelineState(
      durationUs = 3_000_000L,
      tracks = listOf(track)
    )

    val descriptor = LayerCompositor.evaluateComposition(coreState, 1_000_000L)
    val node = descriptor.visualLayers[0]

    assertEquals(0.75f, node.opacity, 0.001f)
    assertEquals(CompositeBlendMode.SCREEN, node.blendMode)
  }

  @Test
  fun testAdjustmentLayersAndMasks() {
    val videoTrack = TimelineTrack(
      id = "t_vid",
      kind = TrackKind.VIDEO,
      clips = listOf(
        TimelineClip(
          id = "clip_masked",
          trackId = "t_vid",
          timelineStartUs = 0L,
          timelineDurationUs = 4_000_000L,
          mask = MaskSettings(
            enabled = true,
            shape = MaskShape.CIRCLE,
            width = 0.8f,
            height = 0.8f
          )
        )
      )
    )

    val adjustmentTrack = TimelineTrack(
      id = "t_adj",
      kind = TrackKind.ADJUSTMENT,
      orderIndex = 1,
      clips = listOf(
        TimelineClip(
          id = "clip_adj",
          trackId = "t_adj",
          kind = TrackKind.ADJUSTMENT,
          timelineStartUs = 0L,
          timelineDurationUs = 4_000_000L
        )
      )
    )

    val coreState = CoreTimelineState(
      durationUs = 4_000_000L,
      tracks = listOf(videoTrack, adjustmentTrack)
    )

    val descriptor = LayerCompositor.evaluateComposition(coreState, 1_000_000L)

    assertEquals(1, descriptor.visualLayers.size)
    assertEquals(1, descriptor.adjustmentLayers.size)

    val maskedNode = descriptor.visualLayers[0]
    assertTrue(maskedNode.mask.enabled)
    assertEquals(MaskShape.CIRCLE, maskedNode.mask.shape)
    assertNotNull(maskedNode.createMaskPath(1080f, 1920f))
  }
}
