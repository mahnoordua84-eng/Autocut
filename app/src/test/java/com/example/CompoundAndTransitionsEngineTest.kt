package com.example

import com.example.domain.model.*
import com.example.engine.composition.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompoundAndTransitionsEngineTest {

  // =========================================================================
  // 1. TRANSITIONS TESTS
  // =========================================================================

  @Test
  fun testTransitionsBetweenAdjacentClips() {
    val clipA = TimelineClip(
      id = "clip_a",
      trackId = "track_1",
      timelineStartUs = 0L,
      timelineDurationUs = 4_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 4_000_000L,
      sourceMediaId = "content://media/clip_a.mp4"
    )
    val clipB = TimelineClip(
      id = "clip_b",
      trackId = "track_1",
      timelineStartUs = 4_000_000L,
      timelineDurationUs = 4_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 4_000_000L,
      sourceMediaId = "content://media/clip_b.mp4"
    )
    val track = TimelineTrack(
      id = "track_1",
      name = "Main Video",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      clips = listOf(clipA, clipB)
    )
    val initialState = CoreTimelineState(
      durationUs = 8_000_000L,
      tracks = listOf(track),
      trackOrder = listOf("track_1")
    )

    // Add centered transition at cut point (cut point = 4.0s = 4_000_000 us, duration = 1.0s = 1_000_000 us)
    val (stateWithTransition, transition) = TimelineEditOperations.addTransition(
      state = initialState,
      fromClipId = "clip_a",
      toClipId = "clip_b",
      type = TransitionType.DISSOLVE,
      durationUs = 1_000_000L,
      alignment = TransitionAlignment.CENTER,
      customParams = mapOf("feather" to 0.5f)
    )

    assertNotNull(transition)
    assertEquals(1, stateWithTransition.transitions.size)
    assertEquals(TransitionType.DISSOLVE, transition!!.type)
    assertEquals(1_000_000L, transition.durationUs)
    assertEquals(TransitionAlignment.CENTER, transition.alignment)

    // Verify start and end evaluation
    // Center alignment: start = cut - dur/2 = 4_000_000 - 500_000 = 3_500_000 us
    // End = start + dur = 4_500_000 us
    val startUs = transition.calculateStartUs(4_000_000L)
    val endUs = transition.calculateEndUs(4_000_000L)
    assertEquals(3_500_000L, startUs)
    assertEquals(4_500_000L, endUs)

    // Verify active check
    assertFalse(transition.isActiveAt(3_000_000L, 4_000_000L))
    assertTrue(transition.isActiveAt(3_500_000L, 4_000_000L))
    assertTrue(transition.isActiveAt(4_000_000L, 4_000_000L))
    assertTrue(transition.isActiveAt(4_499_999L, 4_000_000L))
    assertFalse(transition.isActiveAt(4_500_000L, 4_000_000L))

    // Verify normalized progress computation
    assertEquals(0.0f, transition.getProgressAt(3_500_000L, 4_000_000L), 0.001f)
    assertEquals(0.5f, transition.getProgressAt(4_000_000L, 4_000_000L), 0.001f)
    assertEquals(0.75f, transition.getProgressAt(4_250_000L, 4_000_000L), 0.001f)
    assertEquals(1.0f, transition.getProgressAt(4_500_000L, 4_000_000L), 0.001f)

    // Verify evaluation in CompositorPipeline
    val frameAtCut = LayerCompositor.evaluateComposition(stateWithTransition, 4_000_000L)
    assertEquals(1, frameAtCut.activeTransitionDescriptors.size)
    val activeDescriptor = frameAtCut.activeTransitionDescriptors[0]
    assertEquals(TransitionType.DISSOLVE, activeDescriptor.type)
    assertEquals(0.5f, activeDescriptor.progress, 0.01f)
    assertEquals("clip_a", activeDescriptor.fromClipId)
    assertEquals("clip_b", activeDescriptor.toClipId)
  }

  @Test
  fun testTransitionAlignmentModesAndAllCuts() {
    val clip1 = TimelineClip(id = "c1", timelineStartUs = 0L, timelineDurationUs = 3_000_000L)
    val clip2 = TimelineClip(id = "c2", timelineStartUs = 3_000_000L, timelineDurationUs = 3_000_000L)
    val clip3 = TimelineClip(id = "c3", timelineStartUs = 6_000_000L, timelineDurationUs = 3_000_000L)

    val track = TimelineTrack(id = "tr1", kind = TrackKind.VIDEO, clips = listOf(clip1, clip2, clip3))
    val state = CoreTimelineState(durationUs = 9_000_000L, tracks = listOf(track))

    // Apply transitions to all cuts
    val stateAllCuts = TimelineEditOperations.applyTransitionToAllCuts(
      state = state,
      trackId = "tr1",
      type = TransitionType.WIPE,
      durationUs = 600_000L,
      alignment = TransitionAlignment.START_AT_CUT
    )

    assertEquals(2, stateAllCuts.transitions.size)
    val tr1 = stateAllCuts.transitions[0]
    val tr2 = stateAllCuts.transitions[1]

    assertEquals("c1", tr1.fromClipId)
    assertEquals("c2", tr1.toClipId)
    assertEquals(TransitionAlignment.START_AT_CUT, tr1.alignment)
    // START_AT_CUT: start = 3.0s, end = 3.6s
    assertEquals(3_000_000L, tr1.calculateStartUs(3_000_000L))
    assertEquals(3_600_000L, tr1.calculateEndUs(3_000_000L))

    assertEquals("c2", tr2.fromClipId)
    assertEquals("c3", tr2.toClipId)
    assertEquals(6_000_000L, tr2.calculateStartUs(6_000_000L))
    assertEquals(6_600_000L, tr2.calculateEndUs(6_000_000L))
  }

  // =========================================================================
  // 2. GROUPING TESTS
  // =========================================================================

  @Test
  fun testGroupingAndMovingGroupPreservingRelativeTiming() {
    val clip1 = TimelineClip(id = "c1", trackId = "t1", timelineStartUs = 1_000_000L, timelineDurationUs = 2_000_000L)
    val clip2 = TimelineClip(id = "c2", trackId = "t2", timelineStartUs = 2_500_000L, timelineDurationUs = 3_000_000L)
    val clip3 = TimelineClip(id = "c3", trackId = "t1", timelineStartUs = 8_000_000L, timelineDurationUs = 2_000_000L)

    val track1 = TimelineTrack(id = "t1", kind = TrackKind.VIDEO, clips = listOf(clip1, clip3))
    val track2 = TimelineTrack(id = "t2", kind = TrackKind.OVERLAY, clips = listOf(clip2))
    val state = CoreTimelineState(durationUs = 10_000_000L, tracks = listOf(track1, track2))

    // Group clip1 and clip2
    val (groupedState, group) = TimelineEditOperations.groupClips(
      state = state,
      clipIds = setOf("c1", "c2"),
      groupName = "Action Montage"
    )

    assertNotNull(group)
    assertEquals(1, groupedState.groups.size)
    assertEquals(group!!.id, groupedState.findClip("c1")?.groupId)
    assertEquals(group.id, groupedState.findClip("c2")?.groupId)
    assertNull(groupedState.findClip("c3")?.groupId)

    // Move group forward by 2.0s (+2_000_000 us)
    val movedState = TimelineEditOperations.moveGroup(
      state = groupedState,
      groupId = group.id,
      deltaUs = 2_000_000L
    )

    val movedClip1 = movedState.findClip("c1")!!
    val movedClip2 = movedState.findClip("c2")!!
    val untouchedClip3 = movedState.findClip("c3")!!

    // Clip1 was at 1.0s -> now at 3.0s
    assertEquals(3_000_000L, movedClip1.timelineStartUs)
    // Clip2 was at 2.5s -> now at 4.5s (relative offset of 1.5s between clip1 and clip2 strictly preserved!)
    assertEquals(4_500_000L, movedClip2.timelineStartUs)
    assertEquals(1_500_000L, movedClip2.timelineStartUs - movedClip1.timelineStartUs)

    // Untouched clip3 remains at 8.0s
    assertEquals(8_000_000L, untouchedClip3.timelineStartUs)

    // Ungroup
    val ungroupedState = TimelineEditOperations.ungroupClips(movedState, group.id)
    assertEquals(0, ungroupedState.groups.size)
    assertNull(ungroupedState.findClip("c1")?.groupId)
    assertNull(ungroupedState.findClip("c2")?.groupId)
    assertEquals(3_000_000L, ungroupedState.findClip("c1")?.timelineStartUs)
    assertEquals(4_500_000L, ungroupedState.findClip("c2")?.timelineStartUs)
  }

  // =========================================================================
  // 3. COMPOUND / NESTED CLIPS TESTS
  // =========================================================================

  @Test
  fun testCreateAndUnpackCompoundClip() {
    val clipA = TimelineClip(id = "c_a", trackId = "t1", timelineStartUs = 2_000_000L, timelineDurationUs = 4_000_000L)
    val clipB = TimelineClip(id = "c_b", trackId = "t2", timelineStartUs = 3_000_000L, timelineDurationUs = 5_000_000L)

    val track1 = TimelineTrack(id = "t1", kind = TrackKind.VIDEO, clips = listOf(clipA))
    val track2 = TimelineTrack(id = "t2", kind = TrackKind.OVERLAY, clips = listOf(clipB))
    val state = CoreTimelineState(durationUs = 10_000_000L, tracks = listOf(track1, track2))

    // Create Compound Clip
    val (compoundState, compoundClip) = TimelineEditOperations.createCompoundClip(
      state = state,
      clipIds = setOf("c_a", "c_b"),
      compoundName = "Intro Sequence",
      targetTrackId = "t1"
    )

    assertNotNull(compoundClip)
    assertTrue(compoundClip!!.isCompound)
    assertNotNull(compoundClip.nestedTimeline)

    // Compound spans from min(2.0s) to max(3.0s + 5.0s = 8.0s) -> duration = 6.0s
    assertEquals(2_000_000L, compoundClip.timelineStartUs)
    assertEquals(6_000_000L, compoundClip.timelineDurationUs)

    // Inner timeline has normalized relative clip offsets (clipA starts at 0s, clipB starts at 1s)
    val innerClips = compoundClip.nestedTimeline!!.allClips()
    val innerClipA = innerClips.first { it.id == "c_a" }
    val innerClipB = innerClips.first { it.id == "c_b" }
    assertEquals(0L, innerClipA.timelineStartUs)
    assertEquals(1_000_000L, innerClipB.timelineStartUs)

    // Unpack compound clip
    val unpackedState = TimelineEditOperations.unpackCompoundClip(compoundState, compoundClip.id)
    val restoredClips = unpackedState.allClips()
    assertEquals(2, restoredClips.size)
    assertTrue(restoredClips.any { it.timelineStartUs == 2_000_000L })
    assertTrue(restoredClips.any { it.timelineStartUs == 3_000_000L })
  }

  // =========================================================================
  // 4. COMPOUND TIMELINE TIME MAPPING & RECURSIVE EVALUATION TESTS
  // =========================================================================

  @Test
  fun testCompoundTimelineHierarchicalTimeMapping() {
    // Child clip inside nested timeline: starts at 1.0s, duration 4.0s (sourceIn = 10.0s = 10_000_000 us)
    val innerChild = TimelineClip(
      id = "child_1",
      trackId = "inner_t1",
      timelineStartUs = 1_000_000L,
      timelineDurationUs = 4_000_000L,
      sourceInUs = 10_000_000L,
      sourceOutUs = 14_000_000L,
      speed = 1.0,
      transform = NormalizedTransform(scaleX = 1.2f, scaleY = 1.2f)
    )

    val innerTrack = TimelineTrack(
      id = "inner_t1",
      kind = TrackKind.VIDEO,
      clips = listOf(innerChild)
    )

    val nestedState = CoreTimelineState(
      durationUs = 6_000_000L,
      tracks = listOf(innerTrack)
    )

    // Container Compound Clip in master timeline: starts at 5.0s, duration 6.0s (speed = 2.0x)
    val compoundClip = TimelineClip(
      id = "compound_main",
      trackId = "master_t1",
      timelineStartUs = 5_000_000L,
      timelineDurationUs = 3_000_000L, // 3s on parent timeline with 2x speed represents 6s of compound timeline
      sourceInUs = 0L,
      sourceOutUs = 6_000_000L,
      speed = 2.0,
      opacity = 0.8f,
      isCompound = true,
      nestedTimeline = nestedState,
      transform = NormalizedTransform(rotation = 45f)
    )

    // Test Parent -> Compound Local Time
    // At parent time = 6.0s (1s after compound start):
    // Offset = 1.0s * speed(2.0) = 2.0s -> Compound local time = 2_000_000 us
    val compoundLocalUs = CompoundTimelineTimeMapper.parentToCompoundLocalTimeUs(6_000_000L, compoundClip)
    assertEquals(2_000_000L, compoundLocalUs)

    // Test Compound Local -> Child Source Time
    // At compound local time = 2.0s (1s after child start at 1.0s):
    // Child source time = sourceIn(10.0s) + 1.0s = 11.0s = 11_000_000 us
    val childSourceUs = CompoundTimelineTimeMapper.compoundLocalToChildSourceTimeUs(compoundLocalUs!!, innerChild)
    assertEquals(11_000_000L, childSourceUs)

    // Test Recursive Evaluator
    val evaluatedNodes = CompoundTimelineTimeMapper.evaluateNestedClipsAtParentTime(
      parentTimelineUs = 6_000_000L,
      compoundClip = compoundClip
    )

    assertEquals(1, evaluatedNodes.size)
    val node = evaluatedNodes[0]
    assertEquals("child_1", node.childClip.id)
    assertEquals(6_000_000L, node.parentTimelineUs)
    assertEquals(2_000_000L, node.compoundLocalTimeUs)
    assertEquals(11_000_000L, node.childSourceTimeUs)
    assertEquals(0.8f, node.cascadedOpacity, 0.01f)
    assertEquals(45f, node.cascadedTransform.rotation, 0.01f)
    assertEquals(1.2f, node.cascadedTransform.scaleX, 0.01f)
  }

  @Test
  fun testArbitraryRecursiveNestingDepth() {
    // Level 3: Deepest leaf clip
    val leafClip = TimelineClip(
      id = "leaf_clip",
      trackId = "leaf_track",
      timelineStartUs = 0L,
      timelineDurationUs = 5_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 5_000_000L,
      transform = NormalizedTransform(scaleX = 2.0f, scaleY = 2.0f)
    )
    val leafTrack = TimelineTrack(id = "leaf_track", clips = listOf(leafClip))
    val level2Timeline = CoreTimelineState(durationUs = 5_000_000L, tracks = listOf(leafTrack))

    // Level 2: Middle compound clip
    val middleCompound = TimelineClip(
      id = "middle_compound",
      trackId = "mid_track",
      timelineStartUs = 0L,
      timelineDurationUs = 5_000_000L,
      isCompound = true,
      nestedTimeline = level2Timeline,
      transform = NormalizedTransform(rotation = 30f)
    )
    val midTrack = TimelineTrack(id = "mid_track", clips = listOf(middleCompound))
    val level1Timeline = CoreTimelineState(durationUs = 5_000_000L, tracks = listOf(midTrack))

    // Level 1: Outer root compound clip
    val rootCompound = TimelineClip(
      id = "root_compound",
      trackId = "root_track",
      timelineStartUs = 1_000_000L,
      timelineDurationUs = 5_000_000L,
      isCompound = true,
      nestedTimeline = level1Timeline,
      transform = NormalizedTransform(rotation = 15f)
    )

    // Evaluate at parent time = 3.0s (3_000_000 us)
    val results = CompoundTimelineTimeMapper.evaluateNestedClipsAtParentTime(
      parentTimelineUs = 3_000_000L,
      compoundClip = rootCompound
    )

    assertEquals(1, results.size)
    val leafMapping = results[0]
    assertEquals("leaf_clip", leafMapping.childClip.id)
    assertEquals(2, leafMapping.nestingDepth) // Evaluated recursively through 2 levels of containers
    assertEquals(45f, leafMapping.cascadedTransform.rotation, 0.01f) // 15 + 30 = 45 degrees
    assertEquals(2.0f, leafMapping.cascadedTransform.scaleX, 0.01f)
  }
}
