package com.example.engine.controller

import android.net.Uri
import android.util.Log
import com.example.domain.model.Timeline
import com.example.domain.model.VideoClip
import com.example.engine.index.LegacyTimelineIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-precision timeline synchronization manager.
 * Synchronizes the authoritative ExoPlayer hardware clock with the editor timeline,
 * driving playhead, time counters, frame updates, text layers, stickers, and effects.
 *
 * Uses a lightweight ~16ms UI/render observation loop for fluid 60 FPS feedback without playhead drift.
 */
class TimelineSyncManager(
  private val playbackManager: PlaybackManager,
  private val onTimelinePositionUpdated: (Long) -> Unit,
  private val onClipTransition: (VideoClip?, Long) -> Unit,
  private val onPlaybackEnded: () -> Unit
) {

  companion object {
    private const val TAG = "TimelineSyncManager"
    private const val SYNC_INTERVAL_60FPS_MS = 16L
  }

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var syncJob: Job? = null

  private var currentTimeline: Timeline = Timeline()
  private var activeClip: VideoClip? = null
  private var isSyncing = false

  private val _timelinePositionMs = MutableStateFlow(0L)
  val timelinePositionMs: StateFlow<Long> = _timelinePositionMs.asStateFlow()

  fun updateTimeline(timeline: Timeline) {
    this.currentTimeline = timeline
  }

  fun setActiveClip(clip: VideoClip?) {
    this.activeClip = clip
  }

  fun setPosition(positionMs: Long) {
    val total = currentTimeline.totalDurationMs
    val bounded = positionMs.coerceIn(0L, total.coerceAtLeast(0L))
    _timelinePositionMs.value = bounded
    onTimelinePositionUpdated(bounded)
  }

  fun startSyncLoop() {
    stopSyncLoop()
    syncJob = scope.launch {
      while (isActive) {
        val isPlayerPlaying = playbackManager.isPlaying || playbackManager.player.playWhenReady
        if (isPlayerPlaying) {
          val active = activeClip
          if (active != null && active.isVideo) {
            // Hardware-backed authoritative calculation
            val playerPos = playbackManager.currentPosition
            val speed = active.speed.coerceAtLeast(0.01f)
            val sourceOffset = (playerPos - active.sourceStartMs).coerceAtLeast(0L)
            val offsetInClip = (sourceOffset / speed).toLong()
            val calculatedTimeline = active.timelineStartMs + offsetInClip

            if (calculatedTimeline >= active.timelineStartMs + active.durationMs) {
              handleClipEnd(active)
            } else {
              val bounded = calculatedTimeline.coerceIn(
                active.timelineStartMs,
                currentTimeline.totalDurationMs.coerceAtLeast(0L)
              )
              if (_timelinePositionMs.value != bounded) {
                _timelinePositionMs.value = bounded
                onTimelinePositionUpdated(bounded)
              }
            }
          } else {
            // Image / Gap advancing loop
            val currentPos = _timelinePositionMs.value
            val nextPos = currentPos + SYNC_INTERVAL_60FPS_MS
            if (nextPos >= currentTimeline.totalDurationMs) {
              _timelinePositionMs.value = 0L
              onTimelinePositionUpdated(0L)
              onPlaybackEnded()
              break
            } else {
              _timelinePositionMs.value = nextPos
              onTimelinePositionUpdated(nextPos)
              val nextClip = findClipAt(nextPos)
              if (nextClip != null && nextClip.id != active?.id) {
                activeClip = nextClip
                onClipTransition(nextClip, nextPos)
                if (nextClip.isVideo) {
                  // Hand off to hardware playback
                  continue
                }
              }
            }
          }
        }
        delay(SYNC_INTERVAL_60FPS_MS)
      }
    }
  }

  private fun handleClipEnd(endedClip: VideoClip) {
    val nextTimelinePos = endedClip.timelineStartMs + endedClip.durationMs
    if (nextTimelinePos >= currentTimeline.totalDurationMs) {
      _timelinePositionMs.value = 0L
      onTimelinePositionUpdated(0L)
      playbackManager.pause()
      playbackManager.seekTo(0L)
      onPlaybackEnded()
    } else {
      _timelinePositionMs.value = nextTimelinePos
      onTimelinePositionUpdated(nextTimelinePos)
      val nextClip = findClipAt(nextTimelinePos)
      activeClip = nextClip
      onClipTransition(nextClip, nextTimelinePos)
    }
  }

  fun findClipAt(positionMs: Long): VideoClip? {
    return LegacyTimelineIndex.getOrBuild(currentTimeline).findVideoClipAt(positionMs)
      ?: currentTimeline.videoClips.firstOrNull { clip ->
        positionMs >= clip.timelineStartMs && positionMs < (clip.timelineStartMs + clip.durationMs)
      }
  }

  fun stopSyncLoop() {
    syncJob?.cancel()
    syncJob = null
  }

  fun release() {
    stopSyncLoop()
    scope.cancel()
  }
}
