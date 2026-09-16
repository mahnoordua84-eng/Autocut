package com.example.domain.command

import com.example.domain.model.*
import com.example.engine.history.TimelineActionType
import java.util.UUID

// =========================================================================
// 1. SPLIT COMMAND
// =========================================================================

/**
 * Reversible command for splitting a clip at a specific timeline timestamp.
 */
data class SplitClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val splitTimeUs: Long,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Split Clip"
  override val description: String = "Split clip at ${splitTimeUs / 1000}ms"
  override val actionType: TimelineActionType = TimelineActionType.SPLIT_CLIP
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  private var originalClip: TimelineClip? = null
  private var leftClipId: String? = null
  private var rightClipId: String? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    originalClip = clip

    val (newState, splitPair) = TimelineEditOperations.splitClip(state, clipId, splitTimeUs)
    if (splitPair != null) {
      leftClipId = splitPair.first.id
      rightClipId = splitPair.second.id
    }
    return newState
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val orig = originalClip ?: return state
    val track = state.findTrackForClip(leftClipId ?: clipId)
      ?: state.findTrackForClip(rightClipId ?: clipId)
      ?: state.findTrackById(orig.trackId)
      ?: return state

    // Remove split parts and restore original single clip
    val cleanedClips = track.clips.filterNot { it.id == leftClipId || it.id == rightClipId || it.id == clipId }
    val restoredClips = (cleanedClips + orig).sortedBy { it.timelineStartUs }
    val updatedTrack = track.copy(clips = restoredClips)

    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 2. TRIM COMMAND
// =========================================================================

/**
 * Reversible command for trimming in/out or start/end of a clip (standard or ripple).
 */
data class TrimClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val newInUs: Long,
  val newOutUs: Long,
  val newTimelineStartUs: Long? = null,
  val isRipple: Boolean = false,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = if (isRipple) "Ripple Trim" else "Trim Clip"
  override val description: String = if (isRipple) "Ripple trim clip" else "Trim clip duration"
  override val actionType: TimelineActionType = if (isRipple) TimelineActionType.RIPPLE_DELETE else TimelineActionType.TRIM_LEFT
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  private var beforeClip: TimelineClip? = null
  private var beforeTrackClips: List<TimelineClip>? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    val track = state.findTrackForClip(clipId) ?: return state
    beforeClip = clip
    beforeTrackClips = track.clips

    var curState = state
    if (newTimelineStartUs != null && newTimelineStartUs != clip.timelineStartUs) {
      curState = TimelineEditOperations.trimLeft(
        state = curState,
        clipId = clipId,
        newStartUs = newTimelineStartUs,
        ripple = isRipple
      )
    }

    val curClip = curState.findClip(clipId) ?: return curState
    val desiredDurationUs = ((newOutUs - newInUs) / curClip.speed).toLong().coerceAtLeast(33_333L)
    if (desiredDurationUs != curClip.timelineDurationUs) {
      curState = TimelineEditOperations.trimRight(
        state = curState,
        clipId = clipId,
        newDurationUs = desiredDurationUs,
        ripple = isRipple
      )
    }

    val finalClip = curState.findClip(clipId)
    if (finalClip != null && (finalClip.sourceInUs != newInUs || finalClip.sourceOutUs != newOutUs)) {
      curState = curState.withClipUpdated(finalClip.copy(sourceInUs = newInUs, sourceOutUs = newOutUs))
    }

    return curState
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val origClip = beforeClip ?: return state
    val origTrackClips = beforeTrackClips ?: return state
    val track = state.findTrackForClip(clipId) ?: state.findTrackById(origClip.trackId) ?: return state

    val updatedTrack = track.copy(clips = origTrackClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 3. MOVE COMMAND
// =========================================================================

/**
 * Reversible command for moving a clip along the timeline or between tracks.
 */
data class MoveClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val targetTrackId: String? = null,
  val targetTimelineStartUs: Long,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Move Clip"
  override val description: String = "Move clip to ${targetTimelineStartUs / 1000}ms"
  override val actionType: TimelineActionType = TimelineActionType.MOVE_CLIP
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = targetTrackId?.let { setOf(it) } ?: emptySet()

  private var previousTrackId: String? = null
  private var previousStartUs: Long? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    val track = state.findTrackForClip(clipId) ?: return state
    previousTrackId = track.id
    previousStartUs = clip.timelineStartUs

    return if (targetTrackId != null && targetTrackId != track.id) {
      TimelineEditOperations.moveClipBetweenTracks(
        state = state,
        clipId = clipId,
        targetTrackId = targetTrackId,
        targetStartUs = targetTimelineStartUs
      )
    } else {
      TimelineEditOperations.moveClip(
        state = state,
        clipId = clipId,
        newStartUs = targetTimelineStartUs
      )
    }
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val prevStart = previousStartUs ?: return state
    val prevTrack = previousTrackId ?: return state
    val currentTrack = state.findTrackForClip(clipId)

    return if (currentTrack != null && currentTrack.id != prevTrack) {
      TimelineEditOperations.moveClipBetweenTracks(
        state = state,
        clipId = clipId,
        targetTrackId = prevTrack,
        targetStartUs = prevStart
      )
    } else {
      TimelineEditOperations.moveClip(
        state = state,
        clipId = clipId,
        newStartUs = prevStart
      )
    }
  }
}

// =========================================================================
// 4. DELETE COMMAND
// =========================================================================

/**
 * Reversible command for deleting a clip (standard or ripple).
 */
data class DeleteClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val isRipple: Boolean = false,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = if (isRipple) "Ripple Delete" else "Delete Clip"
  override val description: String = if (isRipple) "Ripple delete clip" else "Delete clip from timeline"
  override val actionType: TimelineActionType = if (isRipple) TimelineActionType.RIPPLE_DELETE else TimelineActionType.DELETE_CLIP
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  private var deletedClip: TimelineClip? = null
  private var sourceTrackId: String? = null
  private var beforeTrackClips: List<TimelineClip>? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    val track = state.findTrackForClip(clipId) ?: return state
    deletedClip = clip
    sourceTrackId = track.id
    beforeTrackClips = track.clips

    return TimelineEditOperations.deleteClips(
      state = state,
      clipIds = setOf(clipId),
      ripple = isRipple
    )
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val clip = deletedClip ?: return state
    val trackId = sourceTrackId ?: clip.trackId
    val track = state.findTrackById(trackId) ?: return state

    val restoredClips = beforeTrackClips ?: (track.clips + clip).sortedBy { it.timelineStartUs }
    val updatedTrack = track.copy(clips = restoredClips)
    val updatedTracks = state.tracks.map { if (it.id == trackId) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 5. DUPLICATE COMMAND
// =========================================================================

/**
 * Reversible command for duplicating a clip.
 */
data class DuplicateClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val targetStartUs: Long? = null,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Duplicate Clip"
  override val description: String = "Duplicate clip"
  override val actionType: TimelineActionType = TimelineActionType.DUPLICATE_CLIP
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  private var duplicatedClipId: String? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val (newState, duplicated) = TimelineEditOperations.duplicateClip(state, clipId, targetStartUs)
    duplicatedClipId = duplicated?.id
    return newState
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val dupId = duplicatedClipId ?: return state
    return TimelineEditOperations.deleteClips(state, setOf(dupId), ripple = false)
  }
}

// =========================================================================
// 6. RIPPLE EDIT COMMAND
// =========================================================================

/**
 * Reversible command for shifting all downstream clips along a track.
 */
data class RippleEditCommand(
  override val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  val anchorTimeUs: Long,
  val deltaUs: Long,
  override val description: String = "Ripple edit",
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Ripple Edit"
  override val actionType: TimelineActionType = TimelineActionType.RIPPLE_DELETE
  override val affectedClipIds: Set<String> get() = emptySet()
  override val affectedTrackIds: Set<String> = setOf(trackId)

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    val updatedClips = track.clips.map { clip ->
      if (clip.timelineStartUs >= anchorTimeUs) {
        clip.copy(timelineStartUs = (clip.timelineStartUs + deltaUs).coerceAtLeast(0L))
      } else clip
    }.sortedBy { it.timelineStartUs }
    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == trackId) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    val updatedClips = track.clips.map { clip ->
      if (clip.timelineStartUs >= (anchorTimeUs + deltaUs)) {
        clip.copy(timelineStartUs = (clip.timelineStartUs - deltaUs).coerceAtLeast(0L))
      } else clip
    }.sortedBy { it.timelineStartUs }
    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == trackId) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 7. INSERT COMMAND
// =========================================================================

/**
 * Reversible command for inserting a clip at a timeline timestamp and rippling downstream clips.
 */
data class InsertClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  val clip: TimelineClip,
  val insertAtUs: Long,
  val mode: InsertMode = InsertMode.RIPPLE,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Insert Clip"
  override val description: String = "Insert clip at ${insertAtUs / 1000}ms"
  override val actionType: TimelineActionType = TimelineActionType.ADD_CLIP
  override val affectedClipIds: Set<String> = setOf(clip.id)
  override val affectedTrackIds: Set<String> = setOf(trackId)

  private var beforeTrackClips: List<TimelineClip>? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    beforeTrackClips = track.clips

    return TimelineEditOperations.insertClip(
      state = state,
      trackId = trackId,
      clip = clip,
      targetStartUs = insertAtUs,
      mode = mode
    )
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val origClips = beforeTrackClips ?: return state
    val track = state.findTrackById(trackId) ?: return state

    val updatedTrack = track.copy(clips = origClips)
    val updatedTracks = state.tracks.map { if (it.id == trackId) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 8. OVERWRITE COMMAND
// =========================================================================

/**
 * Reversible command for overwriting a region of a track with a new clip.
 */
data class OverwriteClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  val newClip: TimelineClip,
  val overwriteStartUs: Long,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Overwrite Clip"
  override val description: String = "Overwrite clip region"
  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT
  override val affectedClipIds: Set<String> = setOf(newClip.id)
  override val affectedTrackIds: Set<String> = setOf(trackId)

  private var beforeTrackClips: List<TimelineClip>? = null

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    beforeTrackClips = track.clips

    return TimelineEditOperations.insertClip(
      state = state,
      trackId = trackId,
      clip = newClip,
      targetStartUs = overwriteStartUs,
      mode = InsertMode.OVERWRITE
    )
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val origClips = beforeTrackClips ?: return state
    val track = state.findTrackById(trackId) ?: return state

    val updatedTrack = track.copy(clips = origClips)
    val updatedTracks = state.tracks.map { if (it.id == trackId) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }
}

// =========================================================================
// 9. TRACK CHANGE COMMAND
// =========================================================================

/**
 * Reversible command for track mutations (add, remove, mute, lock, solo, reorder).
 */
data class TrackChangeCommand(
  override val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  override val name: String = "Track Settings",
  override val description: String = "Modify track",
  val oldTracks: List<TimelineTrack>,
  val newTracks: List<TimelineTrack>,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val actionType: TimelineActionType = TimelineActionType.TRACK_SETTINGS_EDIT
  override val affectedClipIds: Set<String> get() = emptySet()
  override val affectedTrackIds: Set<String> = setOf(trackId)

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    return state.copy(
      tracks = newTracks,
      trackOrder = newTracks.map { it.id },
      durationUs = newTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    )
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    return state.copy(
      tracks = oldTracks,
      trackOrder = oldTracks.map { it.id },
      durationUs = oldTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    )
  }
}

// =========================================================================
// 10. KEYFRAME CHANGE COMMAND
// =========================================================================

/**
 * Reversible command for adding, removing, or updating keyframes.
 */
data class KeyframeChangeCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  val oldKeyframes: List<ClipKeyframe>,
  val newKeyframes: List<ClipKeyframe>,
  override val description: String = "Keyframe edit",
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Keyframe Edit"
  override val actionType: TimelineActionType = TimelineActionType.KEYFRAME_EDIT
  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    return state.withClipUpdated(clip.copy(keyframes = newKeyframes))
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    val clip = state.findClip(clipId) ?: return state
    return state.withClipUpdated(clip.copy(keyframes = oldKeyframes))
  }
}

// =========================================================================
// 11. TRANSITION CHANGE COMMAND
// =========================================================================

/**
 * Reversible command for adding, removing, or modifying transitions.
 */
data class TransitionChangeCommand(
  override val id: String = UUID.randomUUID().toString(),
  val oldTransitions: List<TimelineTransition>,
  val newTransitions: List<TimelineTransition>,
  override val description: String = "Transition change",
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val name: String = "Transition"
  override val actionType: TimelineActionType = TimelineActionType.ADD_TRANSITION
  override val affectedClipIds: Set<String> get() = (oldTransitions + newTransitions).flatMap { listOfNotNull(it.fromClipId, it.toClipId) }.toSet()
  override val affectedTrackIds: Set<String> get() = emptySet()

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    return state.copy(transitions = newTransitions)
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    return state.copy(transitions = oldTransitions)
  }
}

// =========================================================================
// 12. COMPOUND CLIP COMMAND
// =========================================================================

/**
 * Reversible command for creating or unpacking compound clips.
 */
data class CompoundClipCommand(
  override val id: String = UUID.randomUUID().toString(),
  override val name: String = "Compound Clip",
  override val description: String = "Compound clip operation",
  val beforeState: CoreTimelineState,
  val afterState: CoreTimelineState,
  override val affectedClipIds: Set<String> = emptySet(),
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT
  override val affectedTrackIds: Set<String> get() = emptySet()

  override fun execute(state: CoreTimelineState): CoreTimelineState = afterState

  override fun undo(state: CoreTimelineState): CoreTimelineState = beforeState
}

// =========================================================================
// 13. PROPERTY CHANGE COMMAND
// =========================================================================

/**
 * Reversible command for modifying clip properties (transform, volume, opacity, filter, mask, speed).
 */
data class PropertyChangeCommand(
  override val id: String = UUID.randomUUID().toString(),
  val clipId: String,
  override val name: String,
  override val description: String = name,
  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT,
  val oldClip: TimelineClip,
  val newClip: TimelineClip,
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val affectedClipIds: Set<String> = setOf(clipId)
  override val affectedTrackIds: Set<String> get() = emptySet()

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    return state.withClipUpdated(newClip)
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    return state.withClipUpdated(oldClip)
  }
}

// =========================================================================
// 14. GROUPING COMMANDS
// =========================================================================

/**
 * Reversible command for grouping or ungrouping clips.
 */
data class GroupingCommand(
  override val id: String = UUID.randomUUID().toString(),
  override val name: String,
  override val description: String = name,
  val oldGroups: List<TimelineGroup>,
  val newGroups: List<TimelineGroup>,
  val oldTracks: List<TimelineTrack>,
  val newTracks: List<TimelineTrack>,
  override val affectedClipIds: Set<String> = emptySet(),
  override val timestampMs: Long = System.currentTimeMillis()
) : TimelineCommand {

  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT
  override val affectedTrackIds: Set<String> get() = emptySet()

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    return state.copy(groups = newGroups, tracks = newTracks)
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    return state.copy(groups = oldGroups, tracks = oldTracks)
  }
}
