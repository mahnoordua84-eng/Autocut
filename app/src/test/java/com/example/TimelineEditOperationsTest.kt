package com.example

import com.example.domain.model.*
import com.example.engine.TimelineEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class TimelineEditOperationsTest {

  private lateinit var initialCoreState: CoreTimelineState
  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()

    val clip1 = TimelineClip(
      id = "clip_1",
      sourceMediaId = "file:///video1.mp4",
      name = "Clip 1",
      trackId = "track_main_video",
      kind = TrackKind.VIDEO,
      timelineStartUs = 0L,
      timelineDurationUs = 4_000_000L, // 4.0s
      sourceInUs = 0L,
      sourceOutUs = 4_000_000L,
      speed = 1.0
    )

    val clip2 = TimelineClip(
      id = "clip_2",
      sourceMediaId = "file:///video2.mp4",
      name = "Clip 2",
      trackId = "track_main_video",
      kind = TrackKind.VIDEO,
      timelineStartUs = 4_000_000L,
      timelineDurationUs = 3_000_000L, // 3.0s (ends at 7.0s)
      sourceInUs = 1_000_000L,
      sourceOutUs = 4_000_000L,
      speed = 1.0
    )

    val videoTrack = TimelineTrack(
      id = "track_main_video",
      name = "Main Video",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      clips = listOf(clip1, clip2)
    )

    val overlayTrack = TimelineTrack(
      id = "track_overlay",
      name = "Overlay",
      kind = TrackKind.OVERLAY,
      orderIndex = 1,
      clips = emptyList()
    )

    val audioTrack = TimelineTrack(
      id = "track_audio",
      name = "Audio",
      kind = TrackKind.AUDIO,
      orderIndex = 2,
      clips = listOf(
        TimelineClip(
          id = "audio_1",
          sourceMediaId = "file:///audio1.mp3",
          name = "BGM",
          trackId = "track_audio",
          kind = TrackKind.AUDIO,
          timelineStartUs = 0L,
          timelineDurationUs = 7_000_000L,
          sourceInUs = 0L,
          sourceOutUs = 7_000_000L
        )
      )
    )

    initialCoreState = CoreTimelineState(
      durationUs = 7_000_000L,
      tracks = listOf(videoTrack, overlayTrack, audioTrack),
      trackOrder = listOf("track_main_video", "track_overlay", "track_audio")
    )
  }

  @Test
  fun testSplitClipAtCTI() {
    // Split clip 1 at 1.5 seconds (1_500_000 us)
    val (newState, splitClips) = TimelineEditOperations.splitClip(
      initialCoreState,
      clipId = "clip_1",
      splitTimeUs = 1_500_000L
    )

    assertNotNull(splitClips)
    val (head, tail) = splitClips!!

    assertEquals("clip_1", head.id)
    assertEquals(0L, head.timelineStartUs)
    assertEquals(1_500_000L, head.timelineDurationUs)
    assertEquals(0L, head.sourceInUs)
    assertEquals(1_500_000L, head.sourceOutUs)

    assertNotEquals("clip_1", tail.id)
    assertEquals(1_500_000L, tail.timelineStartUs)
    assertEquals(2_500_000L, tail.timelineDurationUs)
    assertEquals(1_500_000L, tail.sourceInUs)
    assertEquals(4_000_000L, tail.sourceOutUs)

    val vTrack = newState.findTrackById("track_main_video")!!
    assertEquals(3, vTrack.clips.size)
    assertEquals(head.id, vTrack.clips[0].id)
    assertEquals(tail.id, vTrack.clips[1].id)
    assertEquals("clip_2", vTrack.clips[2].id)
  }

  @Test
  fun testTrimLeftAndTrimRight() {
    // Trim left on clip 2 from 4.0s to 4.5s
    val trimmedLeftState = TimelineEditOperations.trimLeft(
      initialCoreState,
      clipId = "clip_2",
      newStartUs = 4_500_000L,
      ripple = false
    )

    val clip2Trimmed = trimmedLeftState.findClip("clip_2")!!
    assertEquals(4_500_000L, clip2Trimmed.timelineStartUs)
    assertEquals(2_500_000L, clip2Trimmed.timelineDurationUs)
    assertEquals(1_500_000L, clip2Trimmed.sourceInUs) // sourceIn moved from 1s to 1.5s

    // Trim right on clip 1 to 2.0s
    val trimmedRightState = TimelineEditOperations.trimRight(
      initialCoreState,
      clipId = "clip_1",
      newDurationUs = 2_000_000L,
      ripple = false
    )
    val clip1Trimmed = trimmedRightState.findClip("clip_1")!!
    assertEquals(2_000_000L, clip1Trimmed.timelineDurationUs)
    assertEquals(2_000_000L, clip1Trimmed.sourceOutUs)
  }

  @Test
  fun testRippleTrimRight() {
    // Ripple trim right on clip 1 from 4.0s down to 2.5s (delta = -1.5s)
    val rippleState = TimelineEditOperations.trimRight(
      initialCoreState,
      clipId = "clip_1",
      newDurationUs = 2_500_000L,
      ripple = true
    )

    val clip1 = rippleState.findClip("clip_1")!!
    val clip2 = rippleState.findClip("clip_2")!!

    assertEquals(2_500_000L, clip1.timelineDurationUs)
    // clip2 shifted backward from 4.0s to 2.5s
    assertEquals(2_500_000L, clip2.timelineStartUs)
    val vTrack = rippleState.findTrackById("track_main_video")!!
    assertEquals(5_500_000L, vTrack.durationUs)
  }

  @Test
  fun testMoveClipBetweenTracks() {
    // Move clip 2 to overlay track at 1.0s
    val migratedState = TimelineEditOperations.moveClipBetweenTracks(
      initialCoreState,
      clipId = "clip_2",
      targetTrackId = "track_overlay",
      targetStartUs = 1_000_000L
    )

    val vTrack = migratedState.findTrackById("track_main_video")!!
    val oTrack = migratedState.findTrackById("track_overlay")!!

    assertEquals(1, vTrack.clips.size)
    assertEquals("clip_1", vTrack.clips[0].id)

    assertEquals(1, oTrack.clips.size)
    val overlayClip = oTrack.clips[0]
    assertEquals("clip_2", overlayClip.id)
    assertEquals("track_overlay", overlayClip.trackId)
    assertEquals(1_000_000L, overlayClip.timelineStartUs)
    assertEquals(TrackKind.OVERLAY, overlayClip.kind)
  }

  @Test
  fun testInsertEditingRipple() {
    val newClip = TimelineClip(
      id = "insert_clip",
      sourceMediaId = "file:///insert.mp4",
      name = "Insert",
      timelineDurationUs = 2_000_000L // 2.0s
    )

    // Ripple Insert at 2.0s (splits clip 1 and ripples subsequent content)
    val insertState = TimelineEditOperations.insertClip(
      initialCoreState,
      trackId = "track_main_video",
      clip = newClip,
      targetStartUs = 2_000_000L,
      mode = InsertMode.RIPPLE
    )

    val vTrack = insertState.findTrackById("track_main_video")!!
    // clip1 split into 2 + insert_clip + clip2 = 4 clips
    assertEquals(4, vTrack.clips.size)

    assertEquals(0L, vTrack.clips[0].timelineStartUs)
    assertEquals(2_000_000L, vTrack.clips[0].timelineDurationUs) // First half of clip 1

    assertEquals(2_000_000L, vTrack.clips[1].timelineStartUs)
    assertEquals(2_000_000L, vTrack.clips[1].timelineDurationUs) // Inserted clip

    assertEquals(4_000_000L, vTrack.clips[2].timelineStartUs)
    assertEquals(2_000_000L, vTrack.clips[2].timelineDurationUs) // Second half of clip 1

    assertEquals(6_000_000L, vTrack.clips[3].timelineStartUs) // Clip 2 shifted by 2.0s
    assertEquals(9_000_000L, insertState.durationUs)
  }

  @Test
  fun testOverwriteEditing() {
    val overwriteClip = TimelineClip(
      id = "overwrite_clip",
      sourceMediaId = "file:///overwrite.mp4",
      name = "Overwrite",
      timelineDurationUs = 2_000_000L // 2.0s duration
    )

    // Overwrite at 3.0s to 5.0s (spans tail of clip 1 [3-4s] and head of clip 2 [4-5s])
    val overwriteState = TimelineEditOperations.insertClip(
      initialCoreState,
      trackId = "track_main_video",
      clip = overwriteClip,
      targetStartUs = 3_000_000L,
      mode = InsertMode.OVERWRITE
    )

    val vTrack = overwriteState.findTrackById("track_main_video")!!
    assertEquals(3, vTrack.clips.size)

    val clip1 = vTrack.clips.find { it.id == "clip_1" }!!
    assertEquals(0L, clip1.timelineStartUs)
    assertEquals(3_000_000L, clip1.timelineDurationUs) // trimmed from 4s to 3s

    val placed = vTrack.clips.find { it.id == "overwrite_clip" }!!
    assertEquals(3_000_000L, placed.timelineStartUs)
    assertEquals(2_000_000L, placed.timelineDurationUs)

    val clip2 = vTrack.clips.find { it.id == "clip_2" }!!
    assertEquals(5_000_000L, clip2.timelineStartUs) // trimmed left to start at 5s
    assertEquals(2_000_000L, clip2.timelineDurationUs) // was 3s, now 2s
  }

  @Test
  fun testRippleDelete() {
    // Ripple delete clip 1 (4.0s duration)
    val rippleState = TimelineEditOperations.deleteClips(
      initialCoreState,
      clipIds = setOf("clip_1"),
      ripple = true
    )

    val vTrack = rippleState.findTrackById("track_main_video")!!
    assertEquals(1, vTrack.clips.size)

    val remainingClip2 = vTrack.clips[0]
    assertEquals("clip_2", remainingClip2.id)
    assertEquals(0L, remainingClip2.timelineStartUs) // Shifted backward by 4.0s to index 0
  }

  @Test
  fun testDuplicateClip() {
    val (dupState, duplicate) = TimelineEditOperations.duplicateClip(
      initialCoreState,
      clipId = "clip_2"
    )

    assertNotNull(duplicate)
    assertEquals(7_000_000L, duplicate!!.timelineStartUs) // Placed right after clip 2
    assertEquals(3_000_000L, duplicate.timelineDurationUs)

    val vTrack = dupState.findTrackById("track_main_video")!!
    assertEquals(3, vTrack.clips.size)
    assertEquals(10_000_000L, dupState.durationUs)
  }

  @Test
  fun testReplaceMedia() {
    val replacedState = TimelineEditOperations.replaceMedia(
      initialCoreState,
      clipId = "clip_1",
      newSourceMediaId = "file:///new_video.mp4",
      newName = "New Video"
    )

    val clip = replacedState.findClip("clip_1")!!
    assertEquals("file:///new_video.mp4", clip.sourceMediaId)
    assertEquals("New Video", clip.name)
    assertEquals(0L, clip.timelineStartUs) // Preserved
    assertEquals(4_000_000L, clip.timelineDurationUs) // Preserved
  }

  @Test
  fun testRollEdit() {
    // Roll edit cut point between clip 1 and clip 2 by +1.0s (1_000_000 us)
    val rollState = TimelineEditOperations.rollEdit(
      initialCoreState,
      outgoingClipId = "clip_1",
      incomingClipId = "clip_2",
      deltaUs = 1_000_000L
    )

    val clip1 = rollState.findClip("clip_1")!!
    val clip2 = rollState.findClip("clip_2")!!

    assertEquals(5_000_000L, clip1.timelineDurationUs) // extended from 4s to 5s
    assertEquals(5_000_000L, clip2.timelineStartUs) // starts at 5s
    assertEquals(2_000_000L, clip2.timelineDurationUs) // reduced from 3s to 2s
    assertEquals(7_000_000L, rollState.durationUs) // total duration unchanged!
  }

  @Test
  fun testGapsDetectionAndCloseGap() {
    // Move clip 2 forward to 6.0s (creates a 2.0s gap between clip 1 [0-4s] and clip 2 [6-9s])
    val movedState = TimelineEditOperations.moveClip(
      initialCoreState,
      clipId = "clip_2",
      newStartUs = 6_000_000L
    )

    val vTrack = movedState.findTrackById("track_main_video")!!
    val gaps = vTrack.findGaps(movedState.durationUs)
    assertEquals(1, gaps.size)
    assertEquals(4_000_000L, gaps[0].startUs)
    assertEquals(2_000_000L, gaps[0].durationUs)

    // Close the gap
    val closedState = TimelineEditOperations.closeGap(
      movedState,
      trackId = "track_main_video",
      gapStartUs = 4_000_000L,
      gapDurationUs = 2_000_000L
    )

    val clip2Closed = closedState.findClip("clip_2")!!
    assertEquals(4_000_000L, clip2Closed.timelineStartUs)
  }

  @Test
  fun testTimelineEngineIntegration() {
    // Test that TimelineEngine executes NLE operations through state locking and records history
    timelineEngine.addVideoClip(uri = "file:///test1.mp4", name = "Test 1", durationMs = 5000L, width = 1920, height = 1080)
    timelineEngine.addVideoClip(uri = "file:///test2.mp4", name = "Test 2", durationMs = 3000L, width = 1920, height = 1080)

    val clips = timelineEngine.timeline.value.videoClips
    assertEquals(2, clips.size)

    // Ripple Trim Right
    val success = timelineEngine.rippleTrimRight(clips[0].id, 3000L)
    assertTrue(success)

    val updatedClips = timelineEngine.timeline.value.videoClips
    assertEquals(3000L, updatedClips[0].durationMs)
    assertEquals(3000L, updatedClips[1].timelineStartMs) // shifted from 5000ms to 3000ms
  }
}
