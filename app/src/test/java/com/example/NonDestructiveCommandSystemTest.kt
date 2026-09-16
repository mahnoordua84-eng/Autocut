package com.example

import com.example.domain.command.*
import com.example.domain.model.*
import com.example.engine.history.TimelineActionType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NonDestructiveCommandSystemTest {

  private fun createSampleState(): CoreTimelineState {
    val clip1 = TimelineClip(
      id = "clip_1",
      name = "Clip 1",
      trackId = "track_video_1",
      timelineStartUs = 0L,
      timelineDurationUs = 4_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 4_000_000L,
      sourceMediaId = "file://media/video1.mp4"
    )
    val clip2 = TimelineClip(
      id = "clip_2",
      name = "Clip 2",
      trackId = "track_video_1",
      timelineStartUs = 4_000_000L,
      timelineDurationUs = 4_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 4_000_000L,
      sourceMediaId = "file://media/video2.mp4"
    )
    val track1 = TimelineTrack(
      id = "track_video_1",
      name = "Video Track 1",
      kind = TrackKind.VIDEO,
      orderIndex = 0,
      clips = listOf(clip1, clip2)
    )
    val track2 = TimelineTrack(
      id = "track_audio_1",
      name = "Audio Track 1",
      kind = TrackKind.AUDIO,
      orderIndex = 1,
      clips = emptyList()
    )

    return CoreTimelineState(
      durationUs = 8_000_000L,
      tracks = listOf(track1, track2),
      trackOrder = listOf("track_video_1", "track_audio_1")
    )
  }

  // =========================================================================
  // 1. SPLIT COMMAND TEST
  // =========================================================================
  @Test
  fun testSplitClipCommandUndoRedo() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    // Split clip_1 at 2.0s (2_000_000 us)
    val splitCmd = SplitClipCommand(clipId = "clip_1", splitTimeUs = 2_000_000L)
    val afterSplitState = manager.executeCommand(splitCmd, initialState)

    val track = afterSplitState.findTrackById("track_video_1")!!
    assertEquals(3, track.clips.size)
    assertTrue(manager.canUndo.value)

    // Undo
    val (undoneState, _) = manager.undo(afterSplitState)!!
    val restoredTrack = undoneState.findTrackById("track_video_1")!!
    assertEquals(2, restoredTrack.clips.size)
    assertEquals("clip_1", restoredTrack.clips[0].id)
    assertEquals(4_000_000L, restoredTrack.clips[0].timelineDurationUs)

    // Redo
    val (redoneState, _) = manager.redo(undoneState)!!
    assertEquals(3, redoneState.findTrackById("track_video_1")!!.clips.size)
  }

  // =========================================================================
  // 2. TRIM COMMAND TEST
  // =========================================================================
  @Test
  fun testTrimClipCommandUndoRedo() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    // Trim clip_1 from 4s to 2.5s (trim right end)
    val trimCmd = TrimClipCommand(
      clipId = "clip_1",
      newInUs = 0L,
      newOutUs = 2_500_000L,
      newTimelineStartUs = 0L,
      isRipple = false
    )

    val afterTrim = manager.executeCommand(trimCmd, initialState)
    val trimmedClip = afterTrim.findClip("clip_1")!!
    assertEquals(2_500_000L, trimmedClip.timelineDurationUs)
    assertEquals(2_500_000L, trimmedClip.sourceOutUs)

    // Undo
    val (undone, _) = manager.undo(afterTrim)!!
    val restoredClip = undone.findClip("clip_1")!!
    assertEquals(4_000_000L, restoredClip.timelineDurationUs)
    assertEquals(4_000_000L, restoredClip.sourceOutUs)
  }

  // =========================================================================
  // 3. MOVE COMMAND TEST
  // =========================================================================
  @Test
  fun testMoveClipCommandUndoRedo() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    val moveCmd = MoveClipCommand(
      clipId = "clip_1",
      targetTimelineStartUs = 1_000_000L
    )

    val afterMove = manager.executeCommand(moveCmd, initialState)
    assertEquals(1_000_000L, afterMove.findClip("clip_1")!!.timelineStartUs)

    val (undone, _) = manager.undo(afterMove)!!
    assertEquals(0L, undone.findClip("clip_1")!!.timelineStartUs)

    val (redone, _) = manager.redo(undone)!!
    assertEquals(1_000_000L, redone.findClip("clip_1")!!.timelineStartUs)
  }

  // =========================================================================
  // 4. DELETE & RIPPLE DELETE COMMAND TEST
  // =========================================================================
  @Test
  fun testDeleteAndRippleDeleteCommandUndoRedo() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    // Ripple delete clip_1 (clip_2 should shift from 4s to 0s)
    val deleteCmd = DeleteClipCommand(clipId = "clip_1", isRipple = true)
    val afterDelete = manager.executeCommand(deleteCmd, initialState)

    assertNull(afterDelete.findClip("clip_1"))
    val shiftedClip2 = afterDelete.findClip("clip_2")!!
    assertEquals(0L, shiftedClip2.timelineStartUs)

    // Undo
    val (undone, _) = manager.undo(afterDelete)!!
    assertNotNull(undone.findClip("clip_1"))
    assertEquals(0L, undone.findClip("clip_1")!!.timelineStartUs)
    assertEquals(4_000_000L, undone.findClip("clip_2")!!.timelineStartUs)
  }

  // =========================================================================
  // 5. DUPLICATE COMMAND TEST
  // =========================================================================
  @Test
  fun testDuplicateCommandUndoRedo() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    val dupCmd = DuplicateClipCommand(clipId = "clip_1")
    val afterDup = manager.executeCommand(dupCmd, initialState)

    val track = afterDup.findTrackById("track_video_1")!!
    assertEquals(3, track.clips.size)

    val (undone, _) = manager.undo(afterDup)!!
    assertEquals(2, undone.findTrackById("track_video_1")!!.clips.size)
  }

  // =========================================================================
  // 6. INSERT & OVERWRITE COMMANDS TEST
  // =========================================================================
  @Test
  fun testInsertAndOverwriteCommands() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    val insertClip = TimelineClip(
      id = "insert_clip",
      timelineStartUs = 2_000_000L,
      timelineDurationUs = 1_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 1_000_000L
    )

    // Insert at 2.0s with ripple
    val insertCmd = InsertClipCommand(
      trackId = "track_video_1",
      clip = insertClip,
      insertAtUs = 2_000_000L
    )

    val afterInsert = manager.executeCommand(insertCmd, initialState)
    assertNotNull(afterInsert.findClip("insert_clip"))

    // Undo insert
    val (undoneInsert, _) = manager.undo(afterInsert)!!
    assertNull(undoneInsert.findClip("insert_clip"))
    assertEquals(2, undoneInsert.findTrackById("track_video_1")!!.clips.size)

    // Overwrite range
    val overwriteClip = TimelineClip(
      id = "overwrite_clip",
      timelineStartUs = 3_000_000L,
      timelineDurationUs = 2_000_000L,
      sourceInUs = 0L,
      sourceOutUs = 2_000_000L
    )
    val overwriteCmd = OverwriteClipCommand(
      trackId = "track_video_1",
      newClip = overwriteClip,
      overwriteStartUs = 3_000_000L
    )

    val afterOverwrite = manager.executeCommand(overwriteCmd, initialState)
    assertNotNull(afterOverwrite.findClip("overwrite_clip"))

    val (undoneOverwrite, _) = manager.undo(afterOverwrite)!!
    assertNull(undoneOverwrite.findClip("overwrite_clip"))
    assertEquals(2, undoneOverwrite.findTrackById("track_video_1")!!.clips.size)
  }

  // =========================================================================
  // 7. KEYFRAME & TRANSITION COMMANDS TEST
  // =========================================================================
  @Test
  fun testKeyframeAndTransitionCommands() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    // Keyframe change
    val keyframes = listOf(
      ClipKeyframe(timeMs = 0L, scaleX = 1f),
      ClipKeyframe(timeMs = 2000L, scaleX = 1.5f)
    )
    val kfCmd = KeyframeChangeCommand(
      clipId = "clip_1",
      oldKeyframes = emptyList(),
      newKeyframes = keyframes
    )

    val afterKf = manager.executeCommand(kfCmd, initialState)
    assertEquals(2, afterKf.findClip("clip_1")!!.keyframes.size)

    val (undoneKf, _) = manager.undo(afterKf)!!
    assertEquals(0, undoneKf.findClip("clip_1")!!.keyframes.size)

    // Transition change
    val transition = TimelineTransition(
      id = "tr_1",
      fromClipId = "clip_1",
      toClipId = "clip_2",
      type = TransitionType.FADE,
      durationUs = 500_000L
    )
    val trCmd = TransitionChangeCommand(
      oldTransitions = emptyList(),
      newTransitions = listOf(transition)
    )

    val afterTr = manager.executeCommand(trCmd, initialState)
    assertEquals(1, afterTr.transitions.size)

    val (undoneTr, _) = manager.undo(afterTr)!!
    assertEquals(0, undoneTr.transitions.size)
  }

  // =========================================================================
  // 8. TRANSACTION GROUPING (COMPOUND COMMAND & ATOMIC ROLLBACK)
  // =========================================================================
  @Test
  fun testTransactionGroupingAndRollback() {
    val initialState = createSampleState()
    val manager = TimelineTransactionManager()

    // Begin multi-step transaction (e.g. macro action or drag gesture)
    manager.beginTransaction(
      name = "Batch Edit",
      initialState = initialState
    )
    assertTrue(manager.isTransactionActive)

    // Step 1: Move clip_1
    val moveCmd = MoveClipCommand(clipId = "clip_1", targetTimelineStartUs = 500_000L)
    val state1 = manager.executeCommand(moveCmd, initialState)

    // Step 2: Trim clip_2
    val trimCmd = TrimClipCommand(
      clipId = "clip_2",
      newInUs = 1_000_000L,
      newOutUs = 4_000_000L,
      newTimelineStartUs = 4_000_000L
    )
    val state2 = manager.executeCommand(trimCmd, state1)

    // Commit Transaction
    val committed = manager.commitTransaction(state2)
    assertTrue(committed)
    assertFalse(manager.isTransactionActive)
    assertEquals(1, manager.commandHistory.value.size)
    assertTrue(manager.commandHistory.value[0].isCompound)

    // Single Undo rolls back the entire atomic transaction
    val (undoneBatch, _) = manager.undo(state2)!!
    assertEquals(0L, undoneBatch.findClip("clip_1")!!.timelineStartUs)
    assertEquals(0L, undoneBatch.findClip("clip_2")!!.sourceInUs)

    // Single Redo reapplies all grouped changes
    val (redoneBatch, _) = manager.redo(undoneBatch)!!
    assertEquals(500_000L, redoneBatch.findClip("clip_1")!!.timelineStartUs)
    assertEquals(1_000_000L, redoneBatch.findClip("clip_2")!!.sourceInUs)

    // Test Transaction Rollback on cancel
    manager.beginTransaction(name = "Cancelled Tx", initialState = redoneBatch)
    manager.executeCommand(MoveClipCommand(clipId = "clip_1", targetTimelineStartUs = 999_000L), redoneBatch)
    val rolledBack = manager.rollbackTransaction()
    assertNotNull(rolledBack)
    assertEquals(500_000L, rolledBack!!.findClip("clip_1")!!.timelineStartUs)
  }

  // =========================================================================
  // 9. HISTORY LIMITS TEST
  // =========================================================================
  @Test
  fun testHistoryLimitEnforcement() {
    val initialState = createSampleState()
    val smallHistoryManager = TimelineTransactionManager(maxHistorySize = 3)

    var cur = initialState
    for (i in 1..6) {
      val cmd = MoveClipCommand(
        clipId = "clip_1",
        targetTimelineStartUs = i * 100_000L
      )
      cur = smallHistoryManager.executeCommand(cmd, cur)
    }

    // Size must not exceed maxHistorySize (3)
    assertEquals(3, smallHistoryManager.commandHistory.value.size)
  }
}
