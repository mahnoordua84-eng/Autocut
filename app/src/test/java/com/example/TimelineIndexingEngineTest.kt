package com.example

import com.example.domain.model.*
import com.example.engine.index.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

class TimelineIndexingEngineTest {

  data class SimpleInterval(
    val id: String,
    override val startUs: Long,
    override val endUs: Long
  ) : TimeInterval

  @Test
  fun testIntervalTreeBasicQueries() {
    val intervals = listOf(
      SimpleInterval("c1", 0L, 1_000_000L),
      SimpleInterval("c2", 500_000L, 1_500_000L),
      SimpleInterval("c3", 2_000_000L, 3_000_000L),
      SimpleInterval("c4", 2_500_000L, 4_000_000L)
    )

    val tree = IntervalTree.buildFrom(intervals)
    assertEquals(4, tree.size)
    assertFalse(tree.isEmpty)

    // Point queries [startUs, endUs)
    val at0 = tree.queryPoint(0L)
    assertEquals(1, at0.size)
    assertEquals("c1", at0[0].id)

    val at750k = tree.queryPoint(750_000L)
    assertEquals(2, at750k.size)
    val idsAt750k = at750k.map { it.id }.toSet()
    assertTrue(idsAt750k.contains("c1"))
    assertTrue(idsAt750k.contains("c2"))

    val at1_75M = tree.queryPoint(1_750_000L)
    assertTrue(at1_75M.isEmpty())

    // Range queries [startUs, endUs)
    val rangeOverlap = tree.queryRange(1_200_000L, 2_200_000L)
    val rangeIds = rangeOverlap.map { it.id }.toSet()
    assertEquals(setOf("c2", "c3"), rangeIds)
  }

  @Test
  fun testTimelineSnapIndexBinarySearch() {
    val points = listOf(0L, 1000L, 2500L, 5000L, 10000L)
    val byClip = mapOf(
      "clip1" to listOf(0L, 1000L),
      "clip2" to listOf(2500L, 5000L),
      "clip3" to listOf(10000L)
    )

    val snapIndex = TimelineSnapIndex.build(points, byClip)

    // Snap within threshold
    val snap1 = snapIndex.findClosestSnap(candidatePosMs = 950L, thresholdMs = 100L)
    assertEquals(1000L, snap1)

    // Outside threshold -> returns null
    val snapNone = snapIndex.findClosestSnap(candidatePosMs = 1500L, thresholdMs = 100L)
    assertNull(snapNone)

    // Ignore clip
    val snapIgnore = snapIndex.findClosestSnap(
      candidatePosMs = 980L,
      thresholdMs = 50L,
      ignoreClipIds = setOf("clip1")
    )
    assertNull(snapIgnore)

    // Additional arbitrary points
    val snapCustom = snapIndex.findClosestSnap(
      candidatePosMs = 3290L,
      thresholdMs = 50L,
      additionalPoints = longArrayOf(3300L)
    )
    assertEquals(3300L, snapCustom)
  }

  @Test
  fun testTrackSpatialIndexAndCaching() {
    val clip1 = TimelineClip(
      id = "c1",
      timelineStartUs = 0L,
      timelineDurationUs = 2_000_000L
    )
    val clip2 = TimelineClip(
      id = "c2",
      timelineStartUs = 1_500_000L, // overlaps c1
      timelineDurationUs = 1_500_000L
    )
    val clip3 = TimelineClip(
      id = "c3",
      timelineStartUs = 4_000_000L, // gap between 3s and 4s
      timelineDurationUs = 2_000_000L
    )

    val track = TimelineTrack(
      id = "track1",
      clips = listOf(clip1, clip2, clip3)
    )

    // Cached duration check
    assertEquals(6_000_000L, track.durationUs)

    // Overlaps detection
    val overlaps = track.findOverlaps()
    assertEquals(1, overlaps.size)
    assertEquals("c1", overlaps[0].first.id)
    assertEquals("c2", overlaps[0].second.id)

    // Gaps detection
    val gaps = track.findGaps(track.durationUs)
    assertEquals(1, gaps.size)
    assertEquals(3_000_000L, gaps[0].startUs)
    assertEquals(1_000_000L, gaps[0].durationUs)

    // Range query
    val visible = track.getClipsInRange(500_000L, 1_800_000L)
    val visibleIds = visible.map { it.id }.toSet()
    assertEquals(setOf("c1", "c2"), visibleIds)
  }

  @Test
  fun testMasterTimelineIndexFastLookups() {
    val clipA = TimelineClip(id = "cA", trackId = "trackA", timelineStartUs = 0L, timelineDurationUs = 3_000_000L)
    val clipB = TimelineClip(id = "cB", trackId = "trackB", timelineStartUs = 1_000_000L, timelineDurationUs = 2_000_000L)

    val state = CoreTimelineState(
      durationUs = 5_000_000L,
      playheadPositionUs = 1_500_000L,
      tracks = listOf(
        TimelineTrack(id = "trackA", clips = listOf(clipA)),
        TimelineTrack(id = "trackB", clips = listOf(clipB))
      )
    )

    // Fast O(1) clip and track lookup
    assertNotNull(state.findClip("cA"))
    assertEquals("cA", state.findClip("cA")?.id)
    assertEquals("trackA", state.findTrackForClip("cA")?.id)

    assertNotNull(state.findClip("cB"))
    assertEquals("trackB", state.findTrackForClip("cB")?.id)

    assertNull(state.findClip("nonexistent"))
    assertNull(state.findTrackForClip("nonexistent"))

    // Query active clips at playhead (1.5s -> both cA and cB are active)
    val activeAtPlayhead = state.getClipsAtPlayhead()
    assertEquals(2, activeAtPlayhead.size)
    assertEquals(setOf("cA", "cB"), activeAtPlayhead.map { it.id }.toSet())
  }

  @Test
  fun testLegacyTimelineIndexOptimization() {
    val v1 = VideoClip(id = "v1", name = "Clip 1", timelineStartMs = 0L, durationMs = 2000L)
    val v2 = VideoClip(id = "v2", name = "Clip 2", timelineStartMs = 2000L, durationMs = 3000L)
    val a1 = AudioClip(id = "a1", uri = "audio://a1", title = "Audio 1", timelineStartMs = 1000L, durationMs = 3000L)
    val t1 = TextClip(id = "t1", text = "Title", timelineStartMs = 500L, durationMs = 1000L)

    val timeline = Timeline(
      videoClips = listOf(v1, v2),
      audioClips = listOf(a1),
      textClips = listOf(t1)
    )

    assertEquals(5000L, timeline.totalDurationMs)

    // findClip
    assertEquals(v1, timeline.findClip("v1"))
    assertEquals(a1, timeline.findClip("a1"))
    assertEquals(t1, timeline.findClip("t1"))

    // findClipUnderPlayhead
    assertEquals("v1", timeline.findClipUnderPlayhead(1500L))
    assertEquals("v2", timeline.findClipUnderPlayhead(2500L))

    // Visible clips query
    val visibleVideo = timeline.getVisibleVideoClips(1900L, 2100L)
    assertEquals(2, visibleVideo.size)
  }

  @Test
  fun testThreadSafeTimelineCoordinatorConcurrency() {
    val initialTimeline = Timeline()
    val initialCore = CoreTimelineState()
    val coordinator = ThreadSafeTimelineCoordinator(initialCore, initialTimeline)

    val latch = CountDownLatch(10)
    val readErrors = java.util.concurrent.atomic.AtomicInteger(0)

    // 5 reader threads
    for (i in 0 until 5) {
      Thread {
        try {
          for (j in 0 until 100) {
            coordinator.readCore { state, index ->
              val duration = index.cachedDurationUs
              assertTrue(duration >= 0L)
            }
          }
        } catch (e: Exception) {
          readErrors.incrementAndGet()
        } finally {
          latch.countDown()
        }
      }.start()
    }

    // 5 writer threads mutating timeline
    for (i in 0 until 5) {
      Thread {
        try {
          for (j in 0 until 20) {
            coordinator.mutateLegacy { tl ->
              val newClips = tl.videoClips + VideoClip(
                id = "clip_${Thread.currentThread().id}_$j",
                name = "Clip $j",
                timelineStartMs = j * 1000L,
                durationMs = 1000L
              )
              tl.copy(videoClips = newClips)
            }
          }
        } catch (e: Exception) {
          readErrors.incrementAndGet()
        } finally {
          latch.countDown()
        }
      }.start()
    }

    assertTrue(latch.await(5, TimeUnit.SECONDS))
    assertEquals(0, readErrors.get())
    assertTrue(coordinator.legacyTimeline.videoClips.size >= 50)
  }

  @Test
  fun testHighDensityTimelinePerformanceStressTest() {
    // Stress test: 1000 clips across 10 tracks
    val tracks = mutableListOf<TimelineTrack>()
    val clipCountPerTrack = 100
    val clipDurationUs = 2_000_000L // 2 seconds each

    for (trackIdx in 0 until 10) {
      val clips = mutableListOf<TimelineClip>()
      for (clipIdx in 0 until clipCountPerTrack) {
        val startUs = clipIdx * 1_500_000L // overlapping clips
        clips.add(
          TimelineClip(
            id = "t${trackIdx}_c$clipIdx",
            trackId = "track_$trackIdx",
            timelineStartUs = startUs,
            timelineDurationUs = clipDurationUs
          )
        )
      }
      tracks.add(
        TimelineTrack(
          id = "track_$trackIdx",
          orderIndex = trackIdx,
          clips = clips
        )
      )
    }

    val heavyState = CoreTimelineState(
      tracks = tracks,
      durationUs = 150_000_000L,
      playheadPositionUs = 50_000_000L
    )

    // Build index
    val indexBuildTimeMs = measureNanoTime {
      val idx = MasterTimelineIndex.build(heavyState)
      assertEquals(1000, idx.totalClipsCount)
    } / 1_000_000L
    println("Indexed 1000 clips in ${indexBuildTimeMs}ms")

    // Benchmark query at playhead - must be sub-millisecond
    val masterIndex = MasterTimelineIndex.getOrBuild(heavyState)
    val queryNanos = measureNanoTime {
      val active = masterIndex.getClipsAtPlayhead(50_000_000L)
      assertTrue(active.isNotEmpty())
    }
    val queryMicros = queryNanos / 1000L
    println("Queried active clips across 1000 clips in ${queryMicros}μs")
    assertTrue("Playhead query across 1000 clips should be ultra-fast (< 5ms)", queryMicros < 5_000L)

    // Benchmark visible range query
    val rangeNanos = measureNanoTime {
      val visible = masterIndex.getClipsInVisibleRange(40_000_000L, 60_000_000L)
      assertTrue(visible.isNotEmpty())
    }
    val rangeMicros = rangeNanos / 1000L
    println("Queried visible range across 1000 clips in ${rangeMicros}μs")
    assertTrue("Range query should be ultra-fast (< 5ms)", rangeMicros < 5_000L)
  }
}
