package com.example.domain.model

import java.util.UUID

/**
 * Modes for inserting media or clips into a non-linear timeline track.
 */
enum class InsertMode {
  /**
   * Ripple / Insert Editing: Splits any clip at the insertion point and pushes
   * all subsequent clips downstream by the inserted clip's duration.
   */
  RIPPLE,

  /**
   * Overwrite Editing: Places the clip at the target time, overwriting/trimming
   * any existing media in that window without shifting downstream clips.
   */
  OVERWRITE,

  /**
   * Overlay / Multi-clip Free placement: Places the clip at the target time,
   * allowing overlapping clips on the same track.
   */
  OVERLAY,

  /**
   * Append Editing: Appends the clip to the very end of the track.
   */
  APPEND
}

/**
 * Result of an edit operation on the timeline data model.
 */
data class TimelineEditResult(
  val state: CoreTimelineState,
  val affectedClipIds: Set<String> = emptySet(),
  val isSuccessful: Boolean = true,
  val message: String = ""
)

/**
 * Professional Non-Linear Editing (NLE) Data Operations.
 *
 * All operations execute purely on the timeline data model ([CoreTimelineState],
 * [TimelineTrack], [TimelineClip]) with microsecond and frame-accurate precision,
 * without directly manipulating UI elements.
 */
object TimelineEditOperations {

  private const val MIN_CLIP_DURATION_US = 33_333L // ~1 frame at 30fps

  // =========================================================================
  // 1. SPLIT CLIP AT CTI / PLAYHEAD
  // =========================================================================

  /**
   * Splits a clip at a given timeline position (CTI / playhead).
   *
   * Accurately calculates source in/out offsets, speeds, reverse flags,
   * and divides keyframes at the split boundary.
   */
  fun splitClip(
    state: CoreTimelineState,
    clipId: String,
    splitTimeUs: Long
  ): Pair<CoreTimelineState, Pair<TimelineClip, TimelineClip>?> {
    val track = state.findTrackForClip(clipId) ?: return state to null
    if (track.isLocked) return state to null

    val clip = track.clips.find { it.id == clipId } ?: return state to null

    // Boundary check: split position must be strictly inside the clip
    val minThreshold = MIN_CLIP_DURATION_US
    if (splitTimeUs <= clip.timelineStartUs + minThreshold ||
      splitTimeUs >= clip.timelineEndUs - minThreshold
    ) {
      return state to null
    }

    val firstDurationUs = splitTimeUs - clip.timelineStartUs
    val secondDurationUs = clip.timelineDurationUs - firstDurationUs

    // Compute source split offset accounting for speed and reverse playback
    val sourceOffsetUs = (firstDurationUs * clip.speed).toLong()
    val splitSourceUs = if (clip.isReversed) {
      (clip.sourceOutUs - sourceOffsetUs).coerceAtLeast(0L)
    } else {
      (clip.sourceInUs + sourceOffsetUs).coerceAtLeast(0L)
    }

    // Split keyframes (timeMs on keyframe is relative to clip start in milliseconds)
    val firstDurMs = firstDurationUs / 1000L
    val keyframes1 = clip.keyframes.filter { it.timeMs <= firstDurMs }
    val keyframes2 = clip.keyframes.filter { it.timeMs > firstDurMs }.map {
      it.copy(timeMs = it.timeMs - firstDurMs)
    }

    // Clip 1 (head segment)
    val clip1 = clip.copy(
      timelineDurationUs = firstDurationUs,
      sourceOutUs = splitSourceUs,
      keyframes = keyframes1
    )

    // Clip 2 (tail segment with new unique ID)
    val clip2 = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartUs = splitTimeUs,
      timelineDurationUs = secondDurationUs,
      sourceInUs = splitSourceUs,
      sourceOutUs = clip.sourceOutUs,
      keyframes = keyframes2
    )

    val updatedClips = track.clips.flatMap { existing ->
      if (existing.id == clipId) listOf(clip1, clip2) else listOf(existing)
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    val updatedState = state.copy(tracks = updatedTracks)

    return updatedState to (clip1 to clip2)
  }

  /**
   * Splits all unlocked tracks at the given CTI / playhead timestamp.
   */
  fun splitAllTracks(
    state: CoreTimelineState,
    splitTimeUs: Long
  ): CoreTimelineState {
    var currentState = state
    for (track in state.tracks) {
      if (track.isLocked) continue
      val clipUnderPlayhead = track.clips.find {
        splitTimeUs > it.timelineStartUs + MIN_CLIP_DURATION_US &&
          splitTimeUs < it.timelineEndUs - MIN_CLIP_DURATION_US
      }
      if (clipUnderPlayhead != null) {
        val (newState, _) = splitClip(currentState, clipUnderPlayhead.id, splitTimeUs)
        currentState = newState
      }
    }
    return currentState
  }

  // =========================================================================
  // 2. TRIM LEFT (HEAD TRIM) & RIPPLE TRIM LEFT
  // =========================================================================

  /**
   * Trims the left (in-point) edge of a clip to [newStartUs].
   *
   * @param ripple If true, shifts all subsequent clips on the track to close the gap.
   * @param preserveZeroPointLock If true, keeps the track's first clip pinned at 0s
   *        by adjusting duration and sourceInUs without moving its timeline start.
   */
  fun trimLeft(
    state: CoreTimelineState,
    clipId: String,
    newStartUs: Long,
    ripple: Boolean = false,
    preserveZeroPointLock: Boolean = false
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked) return state

    val clip = track.clips.find { it.id == clipId } ?: return state
    val isFirstClip = track.clips.minByOrNull { it.timelineStartUs }?.id == clipId

    val currentEndUs = clip.timelineEndUs
    val clampedStartUs = newStartUs.coerceIn(0L, currentEndUs - MIN_CLIP_DURATION_US)
    val deltaUs = clampedStartUs - clip.timelineStartUs

    if (deltaUs == 0L) return state

    if (preserveZeroPointLock && isFirstClip) {
      // Pin start at 0, reduce duration, shift sourceIn
      val newDurationUs = (clip.timelineDurationUs - deltaUs).coerceAtLeast(MIN_CLIP_DURATION_US)
      val sourceDeltaUs = (deltaUs * clip.speed).toLong()
      val newSourceInUs = (clip.sourceInUs + sourceDeltaUs).coerceIn(0L, clip.sourceOutUs - MIN_CLIP_DURATION_US)

      val updatedClip = clip.copy(
        timelineStartUs = 0L,
        timelineDurationUs = newDurationUs,
        sourceInUs = newSourceInUs
      )

      val newClips = track.clips.map {
        if (it.id == clipId) updatedClip
        else if (ripple && it.timelineStartUs > clip.timelineStartUs) {
          it.copy(timelineStartUs = (it.timelineStartUs - deltaUs).coerceAtLeast(0L))
        } else it
      }.sortedBy { it.timelineStartUs }

      val updatedTrack = track.copy(clips = newClips)
      val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
      return state.copy(tracks = updatedTracks, durationUs = maxOf(state.durationUs, updatedTracks.maxOfOrNull { it.durationUs } ?: 0L))
    }

    val newDurationUs = currentEndUs - clampedStartUs
    val sourceDeltaUs = (deltaUs * clip.speed).toLong()
    val newSourceInUs = (clip.sourceInUs + sourceDeltaUs).coerceIn(0L, clip.sourceOutUs - MIN_CLIP_DURATION_US)

    val updatedClip = clip.copy(
      timelineStartUs = clampedStartUs,
      timelineDurationUs = newDurationUs,
      sourceInUs = newSourceInUs
    )

    val newClips = track.clips.map {
      if (it.id == clipId) {
        if (ripple) updatedClip.copy(timelineStartUs = clip.timelineStartUs) else updatedClip
      } else if (ripple && it.timelineStartUs > clip.timelineStartUs) {
        it.copy(timelineStartUs = (it.timelineStartUs - deltaUs).coerceAtLeast(0L))
      } else it
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = newClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: 0L)
  }

  // =========================================================================
  // 3. TRIM RIGHT (TAIL TRIM) & RIPPLE TRIM RIGHT
  // =========================================================================

  /**
   * Trims the right (out-point) edge of a clip by setting a [newDurationUs].
   *
   * @param ripple If true, ripples subsequent clips forward/backward to match the duration delta.
   * @param maxSourceDurationUs Optional upper bound on source media duration in microseconds.
   */
  fun trimRight(
    state: CoreTimelineState,
    clipId: String,
    newDurationUs: Long,
    ripple: Boolean = false,
    maxSourceDurationUs: Long? = null
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked) return state

    val clip = track.clips.find { it.id == clipId } ?: return state
    val clampedDurationUs = newDurationUs.coerceAtLeast(MIN_CLIP_DURATION_US)
    val deltaDurationUs = clampedDurationUs - clip.timelineDurationUs

    val sourceSpanUs = (clampedDurationUs * clip.speed).toLong()
    val rawSourceOutUs = clip.sourceInUs + sourceSpanUs
    val clampedSourceOutUs = if (maxSourceDurationUs != null && maxSourceDurationUs > 0L) {
      rawSourceOutUs.coerceIn(clip.sourceInUs + MIN_CLIP_DURATION_US, maxSourceDurationUs)
    } else {
      rawSourceOutUs
    }

    val actualDurationUs = ((clampedSourceOutUs - clip.sourceInUs) / clip.speed).toLong().coerceAtLeast(MIN_CLIP_DURATION_US)
    val actualDeltaUs = actualDurationUs - clip.timelineDurationUs

    val updatedClip = clip.copy(
      timelineDurationUs = actualDurationUs,
      sourceOutUs = clampedSourceOutUs
    )

    val oldEndUs = clip.timelineEndUs
    val newClips = track.clips.map {
      if (it.id == clipId) updatedClip
      else if (ripple && it.timelineStartUs >= oldEndUs) {
        it.copy(timelineStartUs = (it.timelineStartUs + actualDeltaUs).coerceAtLeast(0L))
      } else it
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = newClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: 0L)
  }

  // =========================================================================
  // 4. MOVE CLIP WITHIN TRACK & MULTI-CLIP MOVE
  // =========================================================================

  /**
   * Moves a single clip to a [newStartUs] on its existing track.
   */
  fun moveClip(
    state: CoreTimelineState,
    clipId: String,
    newStartUs: Long
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked) return state

    val clampedStartUs = newStartUs.coerceAtLeast(0L)
    val updatedClips = track.clips.map {
      if (it.id == clipId) it.copy(timelineStartUs = clampedStartUs) else it
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    return state.copy(tracks = updatedTracks, durationUs = newDurationUs)
  }

  /**
   * Moves multiple clips by [deltaUs] simultaneously across their respective tracks.
   */
  fun moveMultipleClips(
    state: CoreTimelineState,
    clipIds: Set<String>,
    deltaUs: Long
  ): CoreTimelineState {
    if (clipIds.isEmpty() || deltaUs == 0L) return state

    val targetClips = state.allClips().filter { it.id in clipIds }
    if (targetClips.isEmpty()) return state

    val minStartUs = targetClips.minOf { it.timelineStartUs }
    val effectiveDeltaUs = if (deltaUs < 0L) maxOf(deltaUs, -minStartUs) else deltaUs

    val updatedTracks = state.tracks.map { track ->
      if (track.isLocked) track
      else {
        val newClips = track.clips.map { clip ->
          if (clip.id in clipIds) {
            clip.copy(timelineStartUs = (clip.timelineStartUs + effectiveDeltaUs).coerceAtLeast(0L))
          } else clip
        }.sortedBy { it.timelineStartUs }
        track.copy(clips = newClips)
      }
    }

    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    return state.copy(tracks = updatedTracks, durationUs = newDurationUs)
  }

  // =========================================================================
  // 5. MOVE CLIP BETWEEN TRACKS (TRACK MIGRATION)
  // =========================================================================

  /**
   * Moves a clip from its current track to a destination track [targetTrackId]
   * at [targetStartUs] (or keeps its current timeline start if null).
   */
  fun moveClipBetweenTracks(
    state: CoreTimelineState,
    clipId: String,
    targetTrackId: String,
    targetStartUs: Long? = null
  ): CoreTimelineState {
    val sourceTrack = state.findTrackForClip(clipId) ?: return state
    val destTrack = state.findTrackById(targetTrackId) ?: return state

    if (sourceTrack.id == destTrack.id) {
      return if (targetStartUs != null) moveClip(state, clipId, targetStartUs) else state
    }
    if (sourceTrack.isLocked || destTrack.isLocked) return state

    val clip = sourceTrack.clips.find { it.id == clipId } ?: return state
    val newStartUs = targetStartUs?.coerceAtLeast(0L) ?: clip.timelineStartUs

    // Adjust clip properties for new track kind
    val updatedClip = clip.copy(
      trackId = destTrack.id,
      timelineStartUs = newStartUs,
      layerIndex = destTrack.orderIndex,
      kind = destTrack.kind
    )

    val newSourceClips = sourceTrack.clips.filterNot { it.id == clipId }
    val newDestClips = (destTrack.clips + updatedClip).sortedBy { it.timelineStartUs }

    val updatedTracks = state.tracks.map { track ->
      when (track.id) {
        sourceTrack.id -> track.copy(clips = newSourceClips)
        destTrack.id -> track.copy(clips = newDestClips)
        else -> track
      }
    }

    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    return state.copy(tracks = updatedTracks, durationUs = newDurationUs)
  }

  // =========================================================================
  // 6. INSERT CLIP (RIPPLE, OVERWRITE, FREE, APPEND)
  // =========================================================================

  /**
   * Inserts a clip into [trackId] at [targetStartUs] using the specified [InsertMode].
   */
  fun insertClip(
    state: CoreTimelineState,
    trackId: String,
    clip: TimelineClip,
    targetStartUs: Long,
    mode: InsertMode = InsertMode.RIPPLE
  ): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    if (track.isLocked) return state

    val startUs = targetStartUs.coerceAtLeast(0L)
    val clipToInsert = clip.copy(
      trackId = trackId,
      timelineStartUs = startUs,
      layerIndex = track.orderIndex
    )
    val insertDurUs = clipToInsert.timelineDurationUs

    when (mode) {
      InsertMode.RIPPLE -> {
        // 1. Split any existing clip covering the insertion point
        val existingCoveringClip = track.clips.find {
          startUs > it.timelineStartUs && startUs < it.timelineEndUs
        }

        val baseClips = if (existingCoveringClip != null) {
          val firstDurUs = startUs - existingCoveringClip.timelineStartUs
          val secondDurUs = existingCoveringClip.timelineDurationUs - firstDurUs
          val sourceSplitUs = existingCoveringClip.sourceInUs + (firstDurUs * existingCoveringClip.speed).toLong()

          val clip1 = existingCoveringClip.copy(
            timelineDurationUs = firstDurUs,
            sourceOutUs = sourceSplitUs
          )
          val clip2 = existingCoveringClip.copy(
            id = UUID.randomUUID().toString(),
            timelineStartUs = startUs,
            timelineDurationUs = secondDurUs,
            sourceInUs = sourceSplitUs
          )

          track.clips.flatMap {
            if (it.id == existingCoveringClip.id) listOf(clip1, clip2) else listOf(it)
          }
        } else {
          track.clips
        }

        // 2. Ripple shift all clips starting at or after insertion point
        val rippledClips = baseClips.map { existing ->
          if (existing.timelineStartUs >= startUs) {
            existing.copy(timelineStartUs = existing.timelineStartUs + insertDurUs)
          } else existing
        }

        val finalClips = (rippledClips + clipToInsert).sortedBy { it.timelineStartUs }
        val updatedTrack = track.copy(clips = finalClips)
        val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
        return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
      }

      InsertMode.OVERWRITE -> {
        val endUs = startUs + insertDurUs
        val resultingClips = mutableListOf<TimelineClip>()

        for (existing in track.clips) {
          when {
            // Completely outside the overwrite window: keep
            existing.timelineEndUs <= startUs || existing.timelineStartUs >= endUs -> {
              resultingClips.add(existing)
            }
            // Completely enveloped: remove (overwrite)
            existing.timelineStartUs >= startUs && existing.timelineEndUs <= endUs -> {
              // dropped
            }
            // Overwrite covers the tail of this clip: trim right
            existing.timelineStartUs < startUs && existing.timelineEndUs <= endUs -> {
              val newDurUs = startUs - existing.timelineStartUs
              val newSourceOutUs = existing.sourceInUs + (newDurUs * existing.speed).toLong()
              resultingClips.add(existing.copy(timelineDurationUs = newDurUs, sourceOutUs = newSourceOutUs))
            }
            // Overwrite covers the head of this clip: trim left
            existing.timelineStartUs >= startUs && existing.timelineEndUs > endUs -> {
              val deltaUs = endUs - existing.timelineStartUs
              val newDurUs = existing.timelineDurationUs - deltaUs
              val newSourceInUs = existing.sourceInUs + (deltaUs * existing.speed).toLong()
              resultingClips.add(existing.copy(timelineStartUs = endUs, timelineDurationUs = newDurUs, sourceInUs = newSourceInUs))
            }
            // Overwrite punches a hole through the middle: split into two clips around window
            existing.timelineStartUs < startUs && existing.timelineEndUs > endUs -> {
              val headDurUs = startUs - existing.timelineStartUs
              val headSourceOutUs = existing.sourceInUs + (headDurUs * existing.speed).toLong()
              val headClip = existing.copy(timelineDurationUs = headDurUs, sourceOutUs = headSourceOutUs)

              val tailDeltaUs = endUs - existing.timelineStartUs
              val tailDurUs = existing.timelineDurationUs - tailDeltaUs
              val tailSourceInUs = existing.sourceInUs + (tailDeltaUs * existing.speed).toLong()
              val tailClip = existing.copy(
                id = UUID.randomUUID().toString(),
                timelineStartUs = endUs,
                timelineDurationUs = tailDurUs,
                sourceInUs = tailSourceInUs
              )
              resultingClips.add(headClip)
              resultingClips.add(tailClip)
            }
          }
        }

        resultingClips.add(clipToInsert)
        val sortedClips = resultingClips.sortedBy { it.timelineStartUs }
        val updatedTrack = track.copy(clips = sortedClips)
        val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
        return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
      }

      InsertMode.OVERLAY, InsertMode.APPEND -> {
        val actualStartUs = if (mode == InsertMode.APPEND) track.durationUs else startUs
        val placedClip = clipToInsert.copy(timelineStartUs = actualStartUs)
        val updatedClips = (track.clips + placedClip).sortedBy { it.timelineStartUs }
        val updatedTrack = track.copy(clips = updatedClips)
        val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
        return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
      }
    }
  }

  // =========================================================================
  // 7. DELETE CLIP (LIFT DELETE VS RIPPLE DELETE)
  // =========================================================================

  /**
   * Deletes one or more clips from the timeline.
   *
   * @param clipIds Set of clip IDs to delete.
   * @param ripple If true, shifts subsequent clips backward on the track (closing the gap).
   * @param syncAllTracks If true and ripple is true, shifts downstream clips across all unlocked tracks.
   */
  fun deleteClips(
    state: CoreTimelineState,
    clipIds: Set<String>,
    ripple: Boolean = false,
    syncAllTracks: Boolean = false
  ): CoreTimelineState {
    if (clipIds.isEmpty()) return state

    var currentState = state
    val targets = state.allClips().filter { it.id in clipIds }.sortedBy { it.timelineStartUs }

    if (!ripple) {
      // Normal / Lift Delete: leaves gaps
      val updatedTracks = currentState.tracks.map { track ->
        if (track.isLocked) track
        else track.copy(clips = track.clips.filterNot { it.id in clipIds })
      }
      return currentState.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: 0L)
    }

    // Ripple Delete: closes gaps
    for (clip in targets) {
      val track = currentState.findTrackForClip(clip.id) ?: continue
      if (track.isLocked) continue

      val delStartUs = clip.timelineStartUs
      val delDurUs = clip.timelineDurationUs

      val updatedTracks = currentState.tracks.map { currentTrack ->
        if (currentTrack.isLocked) currentTrack
        else if (syncAllTracks || currentTrack.id == track.id) {
          val filteredClips = currentTrack.clips.filterNot { it.id == clip.id }
          val rippledClips = filteredClips.map { c ->
            if (c.timelineStartUs >= delStartUs) {
              c.copy(timelineStartUs = (c.timelineStartUs - delDurUs).coerceAtLeast(0L))
            } else c
          }.sortedBy { it.timelineStartUs }
          currentTrack.copy(clips = rippledClips)
        } else currentTrack
      }
      currentState = currentState.copy(tracks = updatedTracks)
    }

    val finalDurationUs = currentState.tracks.maxOfOrNull { it.durationUs } ?: 0L
    return currentState.copy(durationUs = finalDurationUs)
  }

  // =========================================================================
  // 8. DUPLICATE CLIP
  // =========================================================================

  /**
   * Duplicates a clip and places the copy at [targetStartUs] (defaulting to the end of the original clip).
   */
  fun duplicateClip(
    state: CoreTimelineState,
    clipId: String,
    targetStartUs: Long? = null
  ): Pair<CoreTimelineState, TimelineClip?> {
    val track = state.findTrackForClip(clipId) ?: return state to null
    if (track.isLocked) return state to null

    val clip = track.clips.find { it.id == clipId } ?: return state to null
    val placementUs = targetStartUs?.coerceAtLeast(0L) ?: clip.timelineEndUs

    val duplicate = clip.copy(
      id = UUID.randomUUID().toString(),
      timelineStartUs = placementUs
    )

    val updatedClips = (track.clips + duplicate).sortedBy { it.timelineStartUs }
    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs

    return state.copy(tracks = updatedTracks, durationUs = newDurationUs) to duplicate
  }

  // =========================================================================
  // 9. REPLACE MEDIA
  // =========================================================================

  /**
   * Replaces the underlying media of a clip while strictly preserving its timeline position,
   * duration, keyframes, transforms, opacity, volume, filters, and masks.
   */
  fun replaceMedia(
    state: CoreTimelineState,
    clipId: String,
    newSourceMediaId: String,
    newName: String,
    newSourceDurationUs: Long? = null,
    isVideo: Boolean? = null
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked) return state

    val clip = track.clips.find { it.id == clipId } ?: return state

    // Safely clamp source ranges if new duration is shorter than previous source window
    val sourceInUs = if (newSourceDurationUs != null && newSourceDurationUs > 0L) {
      clip.sourceInUs.coerceIn(0L, (newSourceDurationUs - MIN_CLIP_DURATION_US).coerceAtLeast(0L))
    } else clip.sourceInUs

    val sourceOutUs = if (newSourceDurationUs != null && newSourceDurationUs > 0L) {
      clip.sourceOutUs.coerceIn(sourceInUs + MIN_CLIP_DURATION_US, newSourceDurationUs)
    } else clip.sourceOutUs

    val updatedMeta = clip.metadata.toMutableMap()
    updatedMeta["uri"] = newSourceMediaId
    updatedMeta["name"] = newName

    val updatedClip = clip.copy(
      sourceMediaId = newSourceMediaId,
      name = newName,
      sourceInUs = sourceInUs,
      sourceOutUs = sourceOutUs,
      kind = if (isVideo != null) (if (isVideo) TrackKind.VIDEO else TrackKind.IMAGE) else clip.kind,
      metadata = updatedMeta
    )

    val updatedClips = track.clips.map { if (it.id == clipId) updatedClip else it }
    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks)
  }

  // =========================================================================
  // 10. SOURCE IN/OUT EDITING (3-POINT / 4-POINT)
  // =========================================================================

  /**
   * Directly sets the source in and source out points of a clip in microseconds.
   */
  fun editSourceRange(
    state: CoreTimelineState,
    clipId: String,
    newSourceInUs: Long,
    newSourceOutUs: Long,
    ripple: Boolean = false,
    maxSourceDurationUs: Long? = null
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked) return state

    val clip = track.clips.find { it.id == clipId } ?: return state

    val maxBound = maxSourceDurationUs ?: maxOf(clip.sourceOutUs, newSourceOutUs)
    val clampedInUs = newSourceInUs.coerceIn(0L, (maxBound - MIN_CLIP_DURATION_US).coerceAtLeast(0L))
    val clampedOutUs = newSourceOutUs.coerceIn(clampedInUs + MIN_CLIP_DURATION_US, maxBound)

    val newDurationUs = (((clampedOutUs - clampedInUs) / clip.speed).toLong()).coerceAtLeast(MIN_CLIP_DURATION_US)
    val deltaDurationUs = newDurationUs - clip.timelineDurationUs

    val updatedClip = clip.copy(
      sourceInUs = clampedInUs,
      sourceOutUs = clampedOutUs,
      timelineDurationUs = newDurationUs
    )

    val oldEndUs = clip.timelineEndUs
    val newClips = track.clips.map {
      if (it.id == clipId) updatedClip
      else if (ripple && it.timelineStartUs >= oldEndUs) {
        it.copy(timelineStartUs = (it.timelineStartUs + deltaDurationUs).coerceAtLeast(0L))
      } else it
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = newClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }

  // =========================================================================
  // 11. SLIP, SLIDE & ROLL EDITING
  // =========================================================================

  /**
   * Slip edit: Shifts the source in/out window without moving the clip on the timeline.
   */
  fun slipClip(
    state: CoreTimelineState,
    clipId: String,
    deltaUs: Long,
    maxSourceDurationUs: Long? = null
  ): CoreTimelineState {
    val track = state.findTrackForClip(clipId) ?: return state
    if (track.isLocked || deltaUs == 0L) return state

    val clip = track.clips.find { it.id == clipId } ?: return state
    val sourceDeltaUs = (deltaUs * clip.speed).toLong()
    val sourceSpanUs = clip.sourceDurationUs

    val newInUs = (clip.sourceInUs + sourceDeltaUs).coerceAtLeast(0L)
    val maxSource = maxSourceDurationUs ?: (newInUs + sourceSpanUs)
    val clampedInUs = if (newInUs + sourceSpanUs > maxSource) (maxSource - sourceSpanUs).coerceAtLeast(0L) else newInUs
    val clampedOutUs = clampedInUs + sourceSpanUs

    val updatedClip = clip.copy(sourceInUs = clampedInUs, sourceOutUs = clampedOutUs)
    val updatedClips = track.clips.map { if (it.id == clipId) updatedClip else it }
    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks)
  }

  /**
   * Roll edit: Moves the edit point between two adjacent clips.
   * Extends the outgoing clip while reducing the incoming clip (or vice-versa).
   */
  fun rollEdit(
    state: CoreTimelineState,
    outgoingClipId: String,
    incomingClipId: String,
    deltaUs: Long
  ): CoreTimelineState {
    val track = state.findTrackForClip(outgoingClipId) ?: return state
    if (track.isLocked || deltaUs == 0L) return state

    val outClip = track.clips.find { it.id == outgoingClipId } ?: return state
    val inClip = track.clips.find { it.id == incomingClipId } ?: return state

    // Clamp delta so neither clip shrinks below minimum duration
    val minDelta = -(outClip.timelineDurationUs - MIN_CLIP_DURATION_US)
    val maxDelta = inClip.timelineDurationUs - MIN_CLIP_DURATION_US
    val clampedDeltaUs = deltaUs.coerceIn(minDelta, maxDelta)

    if (clampedDeltaUs == 0L) return state

    val newOutDurUs = outClip.timelineDurationUs + clampedDeltaUs
    val newOutSourceOutUs = outClip.sourceInUs + (newOutDurUs * outClip.speed).toLong()
    val updatedOutClip = outClip.copy(
      timelineDurationUs = newOutDurUs,
      sourceOutUs = newOutSourceOutUs
    )

    val newInStartUs = inClip.timelineStartUs + clampedDeltaUs
    val newInDurUs = inClip.timelineDurationUs - clampedDeltaUs
    val newInSourceInUs = inClip.sourceInUs + (clampedDeltaUs * inClip.speed).toLong()
    val updatedInClip = inClip.copy(
      timelineStartUs = newInStartUs,
      timelineDurationUs = newInDurUs,
      sourceInUs = newInSourceInUs
    )

    val updatedClips = track.clips.map {
      when (it.id) {
        outgoingClipId -> updatedOutClip
        incomingClipId -> updatedInClip
        else -> it
      }
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks)
  }

  // =========================================================================
  // 12. GAPS MANAGEMENT (CLOSE GAP / INSERT GAP)
  // =========================================================================

  /**
   * Closes a gap at [gapStartUs] by shifting all subsequent clips backward by [gapDurationUs].
   */
  fun closeGap(
    state: CoreTimelineState,
    trackId: String,
    gapStartUs: Long,
    gapDurationUs: Long
  ): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    if (track.isLocked || gapDurationUs <= 0L) return state

    val updatedClips = track.clips.map { clip ->
      if (clip.timelineStartUs >= gapStartUs + gapDurationUs) {
        clip.copy(timelineStartUs = (clip.timelineStartUs - gapDurationUs).coerceAtLeast(0L))
      } else clip
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: 0L)
  }

  /**
   * Inserts an empty gap of [gapDurationUs] at [atUs] by pushing subsequent clips downstream.
   */
  fun insertGap(
    state: CoreTimelineState,
    trackId: String,
    atUs: Long,
    gapDurationUs: Long
  ): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    if (track.isLocked || gapDurationUs <= 0L) return state

    val updatedClips = track.clips.map { clip ->
      if (clip.timelineStartUs >= atUs) {
        clip.copy(timelineStartUs = clip.timelineStartUs + gapDurationUs)
      } else clip
    }.sortedBy { it.timelineStartUs }

    val updatedTrack = track.copy(clips = updatedClips)
    val updatedTracks = state.tracks.map { if (it.id == track.id) updatedTrack else it }
    return state.copy(tracks = updatedTracks, durationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs)
  }

  // =========================================================================
  // 13. TRANSITION OPERATIONS
  // =========================================================================

  /**
   * Adds or updates a transition between two adjacent clips on a track.
   */
  fun addTransition(
    state: CoreTimelineState,
    fromClipId: String,
    toClipId: String,
    type: TransitionType = TransitionType.FADE,
    durationUs: Long = 500_000L,
    alignment: TransitionAlignment = TransitionAlignment.CENTER,
    customParams: Map<String, Float> = emptyMap(),
    soundEffectName: String? = null
  ): Pair<CoreTimelineState, TimelineTransition?> {
    val fromClip = state.findClip(fromClipId) ?: return state to null
    val toClip = state.findClip(toClipId) ?: return state to null
    val track = state.findTrackForClip(fromClipId) ?: return state to null

    if (track.isLocked) return state to null

    val cutPositionUs = fromClip.timelineEndUs
    val maxAllowedDurationUs = minOf(fromClip.timelineDurationUs, toClip.timelineDurationUs)
    val safeDurationUs = durationUs.coerceIn(MIN_CLIP_DURATION_US, maxAllowedDurationUs)

    val transition = TimelineTransition(
      id = UUID.randomUUID().toString(),
      fromClipId = fromClipId,
      toClipId = toClipId,
      trackId = track.id,
      cutPositionUs = cutPositionUs,
      type = type,
      durationUs = safeDurationUs,
      alignment = alignment,
      customParams = customParams,
      soundEffectName = soundEffectName
    )

    // Remove existing transition between these two clips if present
    val filteredTransitions = state.transitions.filterNot {
      (it.fromClipId == fromClipId && it.toClipId == toClipId) ||
        (it.trackId == track.id && it.cutPositionUs == cutPositionUs)
    }

    val updatedState = state.copy(transitions = filteredTransitions + transition)
    return updatedState to transition
  }

  /**
   * Removes a transition from the timeline by transition ID.
   */
  fun removeTransition(
    state: CoreTimelineState,
    transitionId: String
  ): CoreTimelineState {
    val filtered = state.transitions.filterNot { it.id == transitionId }
    return state.copy(transitions = filtered)
  }

  /**
   * Updates transition properties (type, duration, alignment, parameters).
   */
  fun updateTransition(
    state: CoreTimelineState,
    transitionId: String,
    type: TransitionType? = null,
    durationUs: Long? = null,
    alignment: TransitionAlignment? = null,
    customParams: Map<String, Float>? = null,
    soundEffectName: String? = null
  ): CoreTimelineState {
    val updatedTransitions = state.transitions.map { tr ->
      if (tr.id == transitionId) {
        val newDur = durationUs?.coerceAtLeast(MIN_CLIP_DURATION_US) ?: tr.durationUs
        tr.copy(
          type = type ?: tr.type,
          durationUs = newDur,
          alignment = alignment ?: tr.alignment,
          customParams = customParams ?: tr.customParams,
          soundEffectName = soundEffectName ?: tr.soundEffectName
        )
      } else tr
    }
    return state.copy(transitions = updatedTransitions)
  }

  /**
   * Applies a transition type across all consecutive cut points on a track.
   */
  fun applyTransitionToAllCuts(
    state: CoreTimelineState,
    trackId: String,
    type: TransitionType = TransitionType.FADE,
    durationUs: Long = 500_000L,
    alignment: TransitionAlignment = TransitionAlignment.CENTER
  ): CoreTimelineState {
    val track = state.findTrackById(trackId) ?: return state
    if (track.isLocked || track.clips.size < 2) return state

    val sortedClips = track.clips.sortedBy { it.timelineStartUs }
    val newTransitions = mutableListOf<TimelineTransition>()

    for (i in 0 until sortedClips.size - 1) {
      val clipA = sortedClips[i]
      val clipB = sortedClips[i + 1]

      // Check if clips are adjacent (gap is smaller than 1 frame)
      if (kotlin.math.abs(clipA.timelineEndUs - clipB.timelineStartUs) <= MIN_CLIP_DURATION_US) {
        val maxDur = minOf(clipA.timelineDurationUs, clipB.timelineDurationUs)
        val safeDur = durationUs.coerceIn(MIN_CLIP_DURATION_US, maxDur)
        newTransitions.add(
          TimelineTransition(
            fromClipId = clipA.id,
            toClipId = clipB.id,
            trackId = track.id,
            cutPositionUs = clipA.timelineEndUs,
            type = type,
            durationUs = safeDur,
            alignment = alignment
          )
        )
      }
    }

    val existingOtherTrackTransitions = state.transitions.filterNot { it.trackId == trackId }
    return state.copy(transitions = existingOtherTrackTransitions + newTransitions)
  }

  /**
   * Clears all transitions on a given track (or all tracks if trackId is null).
   */
  fun clearTransitions(
    state: CoreTimelineState,
    trackId: String? = null
  ): CoreTimelineState {
    val remaining = if (trackId != null) {
      state.transitions.filterNot { it.trackId == trackId }
    } else emptyList()
    return state.copy(transitions = remaining)
  }

  // =========================================================================
  // 14. GROUPING OPERATIONS (GROUP, UNGROUP, MOVE GROUP)
  // =========================================================================

  /**
   * Groups multiple clips together across one or multiple tracks.
   * All member clips will move together and preserve exact relative timing offsets.
   */
  fun groupClips(
    state: CoreTimelineState,
    clipIds: Set<String>,
    groupName: String = "Group",
    colorTag: Long = 0xFF3B82F6
  ): Pair<CoreTimelineState, TimelineGroup?> {
    if (clipIds.size < 2) return state to null

    val targetClips = state.allClips().filter { it.id in clipIds }
    if (targetClips.isEmpty()) return state to null

    val group = TimelineGroup(
      id = UUID.randomUUID().toString(),
      name = groupName,
      clipIds = clipIds,
      colorTag = colorTag
    )

    // Assign groupId to all member clips
    val updatedTracks = state.tracks.map { track ->
      val newClips = track.clips.map { clip ->
        if (clip.id in clipIds) clip.copy(groupId = group.id) else clip
      }
      track.copy(clips = newClips)
    }

    val updatedGroups = state.groups + group
    val updatedState = state.copy(tracks = updatedTracks, groups = updatedGroups)
    return updatedState to group
  }

  /**
   * Ungroups a timeline group, removing the group association while preserving clip positions.
   */
  fun ungroupClips(
    state: CoreTimelineState,
    groupId: String
  ): CoreTimelineState {
    val group = state.findGroup(groupId) ?: return state

    val updatedTracks = state.tracks.map { track ->
      val newClips = track.clips.map { clip ->
        if (clip.groupId == groupId || clip.id in group.clipIds) {
          clip.copy(groupId = null)
        } else clip
      }
      track.copy(clips = newClips)
    }

    val updatedGroups = state.groups.filterNot { it.id == groupId }
    return state.copy(tracks = updatedTracks, groups = updatedGroups)
  }

  /**
   * Moves all clips in a group simultaneously by [deltaUs], strictly preserving
   * relative inter-clip timing and relative start offsets.
   */
  fun moveGroup(
    state: CoreTimelineState,
    groupId: String,
    deltaUs: Long
  ): CoreTimelineState {
    val group = state.findGroup(groupId) ?: return state
    if (group.isLocked || deltaUs == 0L) return state

    val groupedClips = state.allClips().filter { it.id in group.clipIds || it.groupId == groupId }
    if (groupedClips.isEmpty()) return state

    val minStartUs = groupedClips.minOf { it.timelineStartUs }
    val effectiveDeltaUs = if (deltaUs < 0L) maxOf(deltaUs, -minStartUs) else deltaUs

    val targetClipIds = groupedClips.map { it.id }.toSet()

    val updatedTracks = state.tracks.map { track ->
      if (track.isLocked) track
      else {
        val newClips = track.clips.map { clip ->
          if (clip.id in targetClipIds) {
            clip.copy(timelineStartUs = (clip.timelineStartUs + effectiveDeltaUs).coerceAtLeast(0L))
          } else clip
        }.sortedBy { it.timelineStartUs }
        track.copy(clips = newClips)
      }
    }

    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    return state.copy(tracks = updatedTracks, durationUs = newDurationUs)
  }

  /**
   * Sets lock state for an entire group.
   */
  fun setGroupLock(
    state: CoreTimelineState,
    groupId: String,
    isLocked: Boolean
  ): CoreTimelineState {
    val updatedGroups = state.groups.map {
      if (it.id == groupId) it.copy(isLocked = isLocked) else it
    }
    val targetGroup = state.findGroup(groupId) ?: return state.copy(groups = updatedGroups)

    val updatedTracks = state.tracks.map { track ->
      val newClips = track.clips.map { clip ->
        if (clip.id in targetGroup.clipIds || clip.groupId == groupId) {
          clip.copy(isLocked = isLocked)
        } else clip
      }
      track.copy(clips = newClips)
    }

    return state.copy(tracks = updatedTracks, groups = updatedGroups)
  }

  /**
   * Sets visibility for an entire group.
   */
  fun setGroupVisibility(
    state: CoreTimelineState,
    groupId: String,
    isVisible: Boolean
  ): CoreTimelineState {
    val updatedGroups = state.groups.map {
      if (it.id == groupId) it.copy(isHidden = !isVisible) else it
    }
    val targetGroup = state.findGroup(groupId) ?: return state.copy(groups = updatedGroups)

    val updatedTracks = state.tracks.map { track ->
      val newClips = track.clips.map { clip ->
        if (clip.id in targetGroup.clipIds || clip.groupId == groupId) {
          clip.copy(isVisible = isVisible)
        } else clip
      }
      track.copy(clips = newClips)
    }

    return state.copy(tracks = updatedTracks, groups = updatedGroups)
  }

  // =========================================================================
  // 15. COMPOUND / NESTED CLIPS
  // =========================================================================

  /**
   * Encapsulates multiple clips and tracks into a single editable Compound Clip.
   *
   * @param clipIds Set of clip IDs to package into the compound clip.
   * @param compoundName Display name of the resulting compound clip.
   * @param targetTrackId ID of the track where the compound clip will be placed.
   */
  fun createCompoundClip(
    state: CoreTimelineState,
    clipIds: Set<String>,
    compoundName: String = "Compound Clip",
    targetTrackId: String? = null
  ): Pair<CoreTimelineState, TimelineClip?> {
    if (clipIds.isEmpty()) return state to null

    val selectedClips = state.allClips().filter { it.id in clipIds }
    if (selectedClips.isEmpty()) return state to null

    val minStartUs = selectedClips.minOf { it.timelineStartUs }
    val maxEndUs = selectedClips.maxOf { it.timelineEndUs }
    val compoundDurationUs = (maxEndUs - minStartUs).coerceAtLeast(MIN_CLIP_DURATION_US)

    // Create normalized inner tracks where clip starts are zero-based relative to minStartUs
    val tracksWithClips = state.tracks.filter { track ->
      track.clips.any { it.id in clipIds }
    }

    val innerTracks = tracksWithClips.mapIndexed { idx, track ->
      val innerClips = track.clips
        .filter { it.id in clipIds }
        .map { clip ->
          clip.copy(
            timelineStartUs = clip.timelineStartUs - minStartUs,
            groupId = null // clear parent group
          )
        }
        .sortedBy { it.timelineStartUs }

      track.copy(
        id = UUID.randomUUID().toString(),
        orderIndex = idx,
        clips = innerClips
      )
    }

    val innerTimelineState = CoreTimelineState(
      durationUs = compoundDurationUs,
      timebase = state.timebase,
      fps = state.fps,
      resolution = state.resolution,
      aspectRatio = state.aspectRatio,
      tracks = innerTracks,
      trackOrder = innerTracks.map { it.id }
    )

    val destinationTrackId = targetTrackId ?: tracksWithClips.firstOrNull()?.id ?: state.tracks.firstOrNull()?.id ?: return state to null

    val compoundClip = TimelineClip(
      id = UUID.randomUUID().toString(),
      name = compoundName,
      sourceMediaId = "compound://${UUID.randomUUID()}",
      trackId = destinationTrackId,
      kind = TrackKind.VIDEO,
      timelineStartUs = minStartUs,
      timelineDurationUs = compoundDurationUs,
      sourceInUs = 0L,
      sourceOutUs = compoundDurationUs,
      isCompound = true,
      nestedTimeline = innerTimelineState,
      compoundPayload = CompoundPayload(
        originalTrackCount = innerTracks.size,
        originalClipCount = selectedClips.size,
        naturalDurationUs = compoundDurationUs,
        customLabel = compoundName
      )
    )

    // Remove original clips from parent timeline and insert compound clip
    val updatedTracks = state.tracks.map { track ->
      val remainingClips = track.clips.filterNot { it.id in clipIds }
      if (track.id == destinationTrackId) {
        val withCompound = (remainingClips + compoundClip).sortedBy { it.timelineStartUs }
        track.copy(clips = withCompound)
      } else {
        track.copy(clips = remainingClips)
      }
    }

    val updatedState = state.copy(
      tracks = updatedTracks,
      durationUs = maxOf(state.durationUs, updatedTracks.maxOfOrNull { it.durationUs } ?: 0L)
    )

    return updatedState to compoundClip
  }

  /**
   * Unpacks a compound clip back into individual standalone clips on the parent timeline.
   */
  fun unpackCompoundClip(
    state: CoreTimelineState,
    compoundClipId: String
  ): CoreTimelineState {
    val compoundClip = state.findClip(compoundClipId) ?: return state
    val innerState = compoundClip.nestedTimeline ?: return state
    val parentTrack = state.findTrackForClip(compoundClipId) ?: return state

    val baseStartUs = compoundClip.timelineStartUs
    val unpackedClips = mutableListOf<TimelineClip>()

    // Unpack inner clips with parent start offset applied
    for (innerTrack in innerState.tracks) {
      for (innerClip in innerTrack.clips) {
        val restoredStartUs = baseStartUs + (innerClip.timelineStartUs * compoundClip.speed).toLong()
        val restoredDurUs = (innerClip.timelineDurationUs * compoundClip.speed).toLong()

        unpackedClips.add(
          innerClip.copy(
            id = UUID.randomUUID().toString(),
            trackId = parentTrack.id,
            timelineStartUs = restoredStartUs,
            timelineDurationUs = restoredDurUs,
            isCompound = innerClip.isCompound,
            nestedTimeline = innerClip.nestedTimeline
          )
        )
      }
    }

    val updatedTracks = state.tracks.map { track ->
      if (track.id == parentTrack.id) {
        val withoutCompound = track.clips.filterNot { it.id == compoundClipId }
        val combined = (withoutCompound + unpackedClips).sortedBy { it.timelineStartUs }
        track.copy(clips = combined)
      } else track
    }

    val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: state.durationUs
    return state.copy(tracks = updatedTracks, durationUs = newDurationUs)
  }

  /**
   * Modifies the nested timeline of a compound clip.
   */
  fun modifyNestedTimeline(
    state: CoreTimelineState,
    compoundClipId: String,
    modifier: (CoreTimelineState) -> CoreTimelineState
  ): CoreTimelineState {
    val clip = state.findClip(compoundClipId) ?: return state
    val innerState = clip.nestedTimeline ?: return state
    val updatedInnerState = modifier(innerState)

    val updatedClip = clip.copy(
      nestedTimeline = updatedInnerState,
      timelineDurationUs = maxOf(clip.timelineDurationUs, updatedInnerState.durationUs)
    )

    return state.withClipUpdated(updatedClip)
  }
}
