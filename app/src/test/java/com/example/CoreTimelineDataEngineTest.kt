package com.example

import com.example.domain.model.*
import com.example.engine.TimelineEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CoreTimelineDataEngineTest {

  private lateinit var timelineEngine: TimelineEngine

  @Before
  fun setUp() {
    timelineEngine = TimelineEngine()
  }

  @Test
  fun testTimebaseRationalMathAndSnapping() {
    val tb30 = Timebase.FPS_30
    assertEquals(30L, tb30.numerator)
    assertEquals(1L, tb30.denominator)
    assertEquals(30.0, tb30.fps, 0.001)
    assertEquals(33_333L, tb30.frameDurationUs)

    // 1 second in micros = 1_000_000
    val frameAt1s = tb30.microsToFrames(1_000_000L)
    assertEquals(30L, frameAt1s)

    val microsAt30Frames = tb30.framesToMicros(30L)
    assertEquals(1_000_000L, microsAt30Frames)

    // Frame snapping
    val snappedUs = tb30.snapUsToFrame(35_000L)
    assertEquals(33_333L, snappedUs)

    // 29.97fps NTSC
    val tb2997 = Timebase.FPS_29_97
    assertEquals(29.97, tb2997.fps, 0.01)
  }

  @Test
  fun testSMPTETimecodeFormattingAndParsing() {
    val tb = Timebase.FPS_30

    // 0 us
    assertEquals("00:00:00:00", TimecodeUtils.formatSMPTE(0L, tb))

    // 1 second + 15 frames = 1_500_000 us -> 45 frames -> 00:00:01:15
    val tc = TimecodeUtils.formatSMPTE(1_500_000L, tb)
    assertEquals("00:00:01:15", tc)

    // Parse back
    val parsedUs = TimecodeUtils.parseSMPTE("00:00:01:15", tb)
    assertNotNull(parsedUs)
    assertEquals(1_500_000L, parsedUs!!)

    // Invalid format
    assertNull(TimecodeUtils.parseSMPTE("invalid_timecode", tb))
  }

  @Test
  fun testTrackModelAndTrackOrdering() {
    val vTrack = TimelineTrack(
      id = "t_video",
      name = "Video 1",
      kind = TrackKind.VIDEO,
      orderIndex = 0
    )
    val aTrack = TimelineTrack(
      id = "t_audio",
      name = "Audio 1",
      kind = TrackKind.AUDIO,
      orderIndex = 1
    )
    val txtTrack = TimelineTrack(
      id = "t_text",
      name = "Text 1",
      kind = TrackKind.TEXT,
      orderIndex = 2
    )

    val core = CoreTimelineState(
      tracks = listOf(vTrack, aTrack, txtTrack),
      trackOrder = listOf("t_video", "t_audio", "t_text")
    )

    assertEquals(3, core.tracks.size)
    assertEquals(TrackKind.VIDEO, core.getTrackById("t_video")?.kind)
    assertEquals(TrackKind.AUDIO, core.getTrackById("t_audio")?.kind)
    assertEquals(TrackKind.TEXT, core.getTrackById("t_text")?.kind)

    // Reorder tracks
    val reordered = core.reorderTracks(listOf("t_text", "t_video", "t_audio"))
    assertEquals("t_text", reordered.tracks[0].id)
    assertEquals(0, reordered.tracks[0].orderIndex)
    assertEquals("t_video", reordered.tracks[1].id)
    assertEquals(1, reordered.tracks[1].orderIndex)
    assertEquals("t_audio", reordered.tracks[2].id)
    assertEquals(2, reordered.tracks[2].orderIndex)
  }

  @Test
  fun testClipModelPropertiesAndTransform() {
    val clip = TimelineClip(
      id = "c1",
      sourceMediaId = "file:///video.mp4",
      trackId = "t_video",
      kind = TrackKind.VIDEO,
      timelineStartUs = 2_000_000L, // 2s
      timelineDurationUs = 4_000_000L, // 4s
      sourceInUs = 1_000_000L, // 1s
      sourceOutUs = 5_000_000L, // 5s
      speed = 1.0,
      volume = 0.8f,
      opacity = 0.95f,
      transform = NormalizedTransform(
        normalizedX = 0.1f,
        normalizedY = -0.2f,
        scaleX = 1.5f,
        scaleY = 1.5f,
        rotation = 45f
      )
    )

    assertEquals(6_000_000L, clip.timelineEndUs)
    assertTrue(clip.containsTimelineTime(3_000_000L))
    assertFalse(clip.containsTimelineTime(1_000_000L))
    assertFalse(clip.containsTimelineTime(7_000_000L))
    assertEquals(4000L, clip.durationMs)
    assertEquals(2000L, clip.timelineStartMs)
    assertEquals(1.5f, clip.transform.uniformScale, 0.001f)
  }

  @Test
  fun testGapDetectionAndClosing() {
    val clip1 = TimelineClip(
      id = "c1",
      timelineStartUs = 0L,
      timelineDurationUs = 3_000_000L // 0 to 3s
    )
    val clip2 = TimelineClip(
      id = "c2",
      timelineStartUs = 5_000_000L, // Gap of 2s between 3s and 5s
      timelineDurationUs = 4_000_000L
    )

    val track = TimelineTrack(
      id = "t1",
      name = "Track 1",
      kind = TrackKind.VIDEO,
      clips = listOf(clip1, clip2)
    )

    val gaps = track.findGaps()
    assertEquals(1, gaps.size)
    assertEquals(3_000_000L, gaps[0].startUs)
    assertEquals(5_000_000L, gaps[0].endUs)
    assertEquals(2_000_000L, gaps[0].durationUs)
    assertEquals(2000L, gaps[0].durationMs)
  }

  @Test
  fun testTimeCoordinatorTimeSeparationAndMapping() {
    val clip = TimelineClip(
      id = "c1",
      timelineStartUs = 2_000_000L, // 2s on timeline
      timelineDurationUs = 5_000_000L, // 5s duration
      sourceInUs = 10_000_000L, // starts at 10s of source
      sourceOutUs = 15_000_000L, // ends at 15s of source
      speed = 1.0
    )

    // At timeline time = 3s (1s into clip)
    val sourceTimeUs = TimelineTimeCoordinator.timelineToSourceTimeUs(3_000_000L, clip)
    assertEquals(11_000_000L, sourceTimeUs)

    // Map back from source time 11s -> should be 3s on timeline
    val timelineTimeUs = TimelineTimeCoordinator.sourceToTimelineTimeUs(11_000_000L, clip)
    assertEquals(3_000_000L, timelineTimeUs)

    // With 2.0x speed
    val fastClip = clip.copy(speed = 2.0, sourceOutUs = 20_000_000L)
    val fastSourceTimeUs = TimelineTimeCoordinator.timelineToSourceTimeUs(3_000_000L, fastClip)
    // 1s elapsed * 2.0 = 2s offset in source
    assertEquals(12_000_000L, fastSourceTimeUs)
  }

  @Test
  fun testTimelineValidationAndAutoSanitization() {
    // Corrupt clip with negative start and inverted source points
    val corruptClip = TimelineClip(
      id = "bad_clip",
      timelineStartUs = -500_000L,
      timelineDurationUs = 0L,
      sourceInUs = -100L,
      sourceOutUs = -200L,
      speed = -5.0,
      volume = -2.0f,
      opacity = 5.0f
    )

    val track = TimelineTrack(
      id = "track_corrupt",
      name = "Corrupt Track",
      kind = TrackKind.VIDEO,
      clips = listOf(corruptClip)
    )

    val corruptTimeline = CoreTimelineState(
      durationUs = -10_000L,
      playheadPositionUs = -5_000L,
      tracks = listOf(track)
    )

    val report = TimelineValidator.validateTimeline(corruptTimeline)
    assertFalse(report.isValid)
    assertTrue(report.hasErrors)

    // Sanitize
    val clean = TimelineValidator.sanitizeTimeline(corruptTimeline)
    val cleanReport = TimelineValidator.validateTimeline(clean)
    assertTrue(cleanReport.isValid)

    val cleanClip = clean.tracks[0].clips[0]
    assertTrue(cleanClip.timelineStartUs >= 0L)
    assertTrue(cleanClip.timelineDurationUs >= TimelineValidator.MIN_CLIP_DURATION_US)
    assertTrue(cleanClip.sourceInUs >= 0L)
    assertTrue(cleanClip.sourceOutUs > cleanClip.sourceInUs)
    assertTrue(cleanClip.speed > 0.0)
    assertTrue(cleanClip.volume >= 0f)
    assertTrue(cleanClip.opacity in 0f..1f)
    assertTrue(clean.durationUs >= 0L)
    assertTrue(clean.playheadPositionUs >= 0L)
  }

  @Test
  fun testTwoWayBackwardCompatibilityWithLegacyTimeline() {
    val legacy = Timeline(
      videoClips = listOf(
        VideoClip(id = "v1", name = "v1", uri = "file:///v1.mp4", durationMs = 3000L, timelineStartMs = 0L, speed = 1.0f)
      ),
      audioClips = listOf(
        AudioClip(id = "a1", uri = "file:///a1.mp3", title = "Audio Track", durationMs = 5000L, timelineStartMs = 0L)
      ),
      textClips = listOf(
        TextClip(id = "t1", text = "Hello World", durationMs = 2000L, timelineStartMs = 500L)
      )
    )

    // Convert to modern CoreTimelineState
    val core = legacy.toCoreTimeline(fps = 30)
    assertEquals(30, core.fps.fps)
    assertEquals(5_000_000L, core.durationUs)
    assertTrue(core.tracks.any { it.kind == TrackKind.VIDEO && it.clips.size == 1 })
    assertTrue(core.tracks.any { it.kind == TrackKind.AUDIO && it.clips.size == 1 })
    assertTrue(core.tracks.any { it.kind == TrackKind.TEXT && it.clips.size == 1 })

    // Convert back to legacy Timeline
    val restored = core.toLegacyTimeline()
    assertEquals(1, restored.videoClips.size)
    assertEquals("v1", restored.videoClips[0].id)
    assertEquals(3000L, restored.videoClips[0].durationMs)
    assertEquals(1, restored.audioClips.size)
    assertEquals("a1", restored.audioClips[0].id)
    assertEquals(1, restored.textClips.size)
    assertEquals("Hello World", restored.textClips[0].text)
  }

  @Test
  fun testTimelineEngineMicrosecondIntegration() {
    timelineEngine.setTimelineFps(60)
    assertEquals(60, timelineEngine.timelineFps.value)
    assertEquals(60L, timelineEngine.timebase.value.numerator)

    // Set position via microsecond API
    timelineEngine.setPositionUs(1_500_000L, snap = false)
    assertEquals(1_500_000L, timelineEngine.currentPositionUs.value)
    assertEquals(1500L, timelineEngine.currentPositionMs.value)

    // Get core timeline from engine
    val core = timelineEngine.getCoreTimeline()
    assertNotNull(core)
    val validation = timelineEngine.validateTimeline()
    assertTrue(validation.isValid)
  }
}
