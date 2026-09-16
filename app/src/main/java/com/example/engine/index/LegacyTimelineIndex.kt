package com.example.engine.index

import com.example.domain.model.*
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * High-performance spatial & temporal index for legacy [Timeline] (millisecond precision).
 *
 * Provides:
 * - $O(1)$ clip lookup by ID across video, overlay, audio, text, sticker, and effect tracks.
 * - $O(\log N)$ binary search lookup for clip under playhead.
 * - $O(\log N)$ snapping using sorted binary search with 0 allocations during dragging.
 * - $O(1)$ cached total timeline duration.
 * - $O(\log N + K)$ viewport visible-range queries.
 */
class LegacyTimelineIndex private constructor(
  val timeline: Timeline,
  val cachedDurationMs: Long,
  val snapIndex: TimelineSnapIndex,
  private val clipMap: Map<String, Any>,
  private val videoTree: IntervalTree<IndexedClipWrapper<VideoClip>>,
  private val overlayTree: IntervalTree<IndexedClipWrapper<VideoClip>>,
  private val audioTree: IntervalTree<IndexedClipWrapper<AudioClip>>,
  private val textTree: IntervalTree<IndexedClipWrapper<TextClip>>,
  private val stickerTree: IntervalTree<IndexedClipWrapper<StickerClip>>,
  private val effectTree: IntervalTree<IndexedClipWrapper<EffectClip>>
) {

  /**
   * Fast $O(1)$ lookup for any clip by ID.
   */
  fun findClip(id: String): Any? = clipMap[id]

  /**
   * Binary-search accelerated lookup for active video clip at `posMs`.
   */
  fun findVideoClipAt(posMs: Long): VideoClip? {
    val results = videoTree.queryPoint(posMs * 1000L)
    return results.firstOrNull()?.clip
  }

  /**
   * High-performance playhead query across all tracks.
   * Runs in $O(\log N)$ without linear scans.
   */
  fun findClipUnderPlayhead(posMs: Long): String? {
    val timeUs = posMs * 1000L
    val vid = videoTree.queryPoint(timeUs).firstOrNull()
    if (vid != null) return vid.id

    val ovl = overlayTree.queryPoint(timeUs).firstOrNull()
    if (ovl != null) return ovl.id

    val aud = audioTree.queryPoint(timeUs).firstOrNull()
    if (aud != null) return aud.id

    val txt = textTree.queryPoint(timeUs).firstOrNull()
    if (txt != null) return txt.id

    val stk = stickerTree.queryPoint(timeUs).firstOrNull()
    if (stk != null) return stk.id

    val eff = effectTree.queryPoint(timeUs).firstOrNull()
    if (eff != null) return eff.id

    return null
  }

  /**
   * Viewport visible range query for video clips.
   */
  fun getVisibleVideoClips(startMs: Long, endMs: Long): List<VideoClip> {
    return videoTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  /**
   * Viewport visible range query for overlay clips.
   */
  fun getVisibleOverlayClips(startMs: Long, endMs: Long): List<VideoClip> {
    return overlayTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  /**
   * Viewport visible range query for audio clips.
   */
  fun getVisibleAudioClips(startMs: Long, endMs: Long): List<AudioClip> {
    return audioTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  /**
   * Viewport visible range query for text clips.
   */
  fun getVisibleTextClips(startMs: Long, endMs: Long): List<TextClip> {
    return textTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  /**
   * Viewport visible range query for sticker clips.
   */
  fun getVisibleStickerClips(startMs: Long, endMs: Long): List<StickerClip> {
    return stickerTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  /**
   * Viewport visible range query for effect clips.
   */
  fun getVisibleEffectClips(startMs: Long, endMs: Long): List<EffectClip> {
    return effectTree.queryRange(startMs * 1000L, endMs * 1000L).map { it.clip }
  }

  companion object {
    private val cache = ConcurrentHashMap<Timeline, LegacyTimelineIndex>()

    fun getOrBuild(timeline: Timeline): LegacyTimelineIndex {
      return cache.computeIfAbsent(timeline) { build(it) }
    }

    fun build(timeline: Timeline): LegacyTimelineIndex {
      val map = HashMap<String, Any>()
      val snapPoints = mutableSetOf<Long>()
      val pointsByClip = HashMap<String, MutableList<Long>>()

      // 1. Video Clips
      val videoWrappers = timeline.videoClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        val clipSnaps = mutableListOf(startMs, endMs)
        clip.keyframes.forEach { kf ->
          val kfMs = startMs + kf.timeMs
          snapPoints.add(kfMs)
          clipSnaps.add(kfMs)
        }
        pointsByClip[clip.id] = clipSnaps
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L, isEnabled = !clip.isHidden)
      }
      val vTree = IntervalTree.buildFrom(videoWrappers)

      // 2. Transitions
      timeline.transitions.forEach { tr ->
        val clip = timeline.videoClips.getOrNull(tr.clipIndexBefore)
        if (clip != null) {
          val cutMs = clip.timelineStartMs + clip.durationMs
          snapPoints.add(cutMs - tr.durationMs / 2)
          snapPoints.add(cutMs + tr.durationMs / 2)
        }
      }

      // 3. Overlay Clips
      val overlayWrappers = timeline.overlayClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        val clipSnaps = mutableListOf(startMs, endMs)
        clip.keyframes.forEach { kf ->
          val kfMs = startMs + kf.timeMs
          snapPoints.add(kfMs)
          clipSnaps.add(kfMs)
        }
        pointsByClip[clip.id] = clipSnaps
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L, isEnabled = !clip.isHidden)
      }
      val oTree = IntervalTree.buildFrom(overlayWrappers)

      // 4. Audio Clips
      val audioWrappers = timeline.audioClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        val clipSnaps = mutableListOf(startMs, endMs)
        clip.keyframes.forEach { kf ->
          val kfMs = startMs + kf.timeMs
          snapPoints.add(kfMs)
          clipSnaps.add(kfMs)
        }
        val wave = clip.waveformData
        if (wave != null && wave.isNotEmpty() && !clip.isMuted) {
          val step = (wave.size / 20).coerceAtLeast(1)
          for (i in 0 until wave.size step step) {
            if (wave[i] > 0.8f) {
              val beatMs = (i.toFloat() / wave.size * clip.durationMs).toLong()
              snapPoints.add(startMs + beatMs)
              clipSnaps.add(startMs + beatMs)
            }
          }
        }
        pointsByClip[clip.id] = clipSnaps
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L, isEnabled = !clip.isMuted)
      }
      val aTree = IntervalTree.buildFrom(audioWrappers)

      // 5. Text Clips
      val textWrappers = timeline.textClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        pointsByClip[clip.id] = mutableListOf(startMs, endMs)
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L)
      }
      val tTree = IntervalTree.buildFrom(textWrappers)

      // 6. Sticker Clips
      val stickerWrappers = timeline.stickerClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        val clipSnaps = mutableListOf(startMs, endMs)
        clip.keyframes.forEach { kf ->
          val kfMs = startMs + kf.timeMs
          snapPoints.add(kfMs)
          clipSnaps.add(kfMs)
        }
        pointsByClip[clip.id] = clipSnaps
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L)
      }
      val sTree = IntervalTree.buildFrom(stickerWrappers)

      // 7. Effect Clips
      val effectWrappers = timeline.effectClips.map { clip ->
        map[clip.id] = clip
        val startMs = clip.timelineStartMs
        val endMs = clip.timelineStartMs + clip.durationMs
        snapPoints.add(startMs)
        snapPoints.add(endMs)
        val clipSnaps = mutableListOf(startMs, endMs)
        clip.keyframes.forEach { kf ->
          val kfMs = startMs + kf.timeMs
          snapPoints.add(kfMs)
          clipSnaps.add(kfMs)
        }
        pointsByClip[clip.id] = clipSnaps
        IndexedClipWrapper(clip, clip.id, startMs * 1000L, endMs * 1000L, isEnabled = !clip.isHidden)
      }
      val eTree = IntervalTree.buildFrom(effectWrappers)

      // Total duration
      val maxDur = maxOf(
        timeline.videoClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.audioClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.textClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.stickerClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L,
        timeline.effectClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
      )

      snapPoints.add(0L)
      snapPoints.add(maxDur)

      val sIdx = TimelineSnapIndex.build(snapPoints, pointsByClip)

      return LegacyTimelineIndex(
        timeline = timeline,
        cachedDurationMs = maxDur,
        snapIndex = sIdx,
        clipMap = map,
        videoTree = vTree,
        overlayTree = oTree,
        audioTree = aTree,
        textTree = tTree,
        stickerTree = sTree,
        effectTree = eTree
      )
    }
  }
}
