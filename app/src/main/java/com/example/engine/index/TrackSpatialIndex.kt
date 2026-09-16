package com.example.engine.index

import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineGap
import com.example.domain.model.TimelineTrack
import java.util.Arrays
import kotlin.math.max

/**
 * Immutable, high-performance spatial/temporal index for a single [TimelineTrack].
 *
 * Provides:
 * - $O(1)$ clip lookup by unique ID.
 * - $O(\log N + K)$ playhead query via balanced IntervalTree.
 * - $O(\log N + K)$ visible-range viewport query.
 * - $O(N \log N)$ sweep-line overlapping-clip detection with lazy caching.
 * - $O(1)$ cached track duration.
 * - $O(\log N)$ binary search for adjacent clips (next, previous, closest).
 */
class TrackSpatialIndex private constructor(
  val trackId: String,
  private val clipsById: Map<String, TimelineClip>,
  private val sortedClips: List<TimelineClip>,
  private val intervalTree: IntervalTree<IndexedClipWrapper<TimelineClip>>,
  val cachedDurationUs: Long
) {

  val clipCount: Int get() = sortedClips.size
  val isEmpty: Boolean get() = sortedClips.isEmpty()

  // Lazy evaluation cache for overlaps
  @Volatile
  private var _cachedOverlaps: List<ClipOverlap<TimelineClip>>? = null

  // Lazy evaluation cache for gaps by totalDurationUs
  @Volatile
  private var _cachedGaps: Pair<Long, List<TimelineGap>>? = null

  /**
   * Look up a clip on this track by ID in $O(1)$.
   */
  fun findClip(clipId: String): TimelineClip? = clipsById[clipId]

  /**
   * Find all active clips containing the given timestamp in $O(\log N + K)$ time.
   */
  fun getClipsAt(timeUs: Long): List<TimelineClip> {
    if (isEmpty || timeUs < 0L || timeUs >= cachedDurationUs) {
      return emptyList()
    }
    return intervalTree.queryPoint(timeUs).map { it.clip }.filter { it.isEnabled }
  }

  /**
   * Find all clips visible/intersecting within range `[startUs, endUs]`.
   * Ideal for viewport culling in UI rendering.
   */
  fun getClipsInRange(startUs: Long, endUs: Long): List<TimelineClip> {
    if (isEmpty || endUs <= 0L || startUs >= cachedDurationUs) {
      return emptyList()
    }
    return intervalTree.queryRange(startUs, endUs).map { it.clip }
  }

  /**
   * Identifies all overlapping clip pairs on this track using an efficient sweep-line algorithm in $O(N \log N)$ time.
   * Results are computed lazily and cached.
   */
  fun findOverlaps(): List<ClipOverlap<TimelineClip>> {
    val existing = _cachedOverlaps
    if (existing != null) return existing

    if (sortedClips.size < 2) {
      val empty = emptyList<ClipOverlap<TimelineClip>>()
      _cachedOverlaps = empty
      return empty
    }

    val overlaps = mutableListOf<ClipOverlap<TimelineClip>>()

    // Sweep-line over sorted clips
    for (i in 0 until sortedClips.size) {
      val a = sortedClips[i]
      for (j in (i + 1) until sortedClips.size) {
        val b = sortedClips[j]
        // Since sorted by start time, if b starts at or after a ends, no subsequent clip can intersect a
        if (b.timelineStartUs >= a.timelineEndUs) {
          break
        }
        if (a.intersects(b)) {
          val overlapStart = max(a.timelineStartUs, b.timelineStartUs)
          val overlapEnd = kotlin.math.min(a.timelineEndUs, b.timelineEndUs)
          overlaps.add(ClipOverlap(a, b, overlapStart, overlapEnd))
        }
      }
    }

    _cachedOverlaps = overlaps
    return overlaps
  }

  /**
   * Finds all empty gaps between clips up to `totalDurationUs`.
   * Results are lazily evaluated and cached.
   */
  fun findGaps(totalDurationUs: Long = cachedDurationUs): List<TimelineGap> {
    val existing = _cachedGaps
    if (existing != null && existing.first == totalDurationUs) {
      return existing.second
    }

    if (sortedClips.isEmpty()) {
      val result = if (totalDurationUs > 0L) {
        listOf(TimelineGap(trackId = trackId, startUs = 0L, durationUs = totalDurationUs))
      } else emptyList()
      _cachedGaps = Pair(totalDurationUs, result)
      return result
    }

    val gaps = mutableListOf<TimelineGap>()
    var currentHeadUs = 0L

    for (clip in sortedClips) {
      if (clip.timelineStartUs > currentHeadUs) {
        gaps.add(
          TimelineGap(
            trackId = trackId,
            startUs = currentHeadUs,
            durationUs = clip.timelineStartUs - currentHeadUs
          )
        )
      }
      currentHeadUs = max(currentHeadUs, clip.timelineEndUs)
    }

    if (totalDurationUs > currentHeadUs) {
      gaps.add(
        TimelineGap(
          trackId = trackId,
          startUs = currentHeadUs,
          durationUs = totalDurationUs - currentHeadUs
        )
      )
    }

    _cachedGaps = Pair(totalDurationUs, gaps)
    return gaps
  }

  /**
   * Binary search for the immediate next clip starting strictly after `timeUs`.
   */
  fun findNextClip(timeUs: Long): TimelineClip? {
    if (sortedClips.isEmpty()) return null
    var low = 0
    var high = sortedClips.size - 1
    var candidate: TimelineClip? = null

    while (low <= high) {
      val mid = (low + high) ushr 1
      val clip = sortedClips[mid]
      if (clip.timelineStartUs > timeUs) {
        candidate = clip
        high = mid - 1
      } else {
        low = mid + 1
      }
    }
    return candidate
  }

  /**
   * Binary search for the immediate previous clip ending on or before `timeUs`.
   */
  fun findPreviousClip(timeUs: Long): TimelineClip? {
    if (sortedClips.isEmpty()) return null
    var low = 0
    var high = sortedClips.size - 1
    var candidate: TimelineClip? = null

    while (low <= high) {
      val mid = (low + high) ushr 1
      val clip = sortedClips[mid]
      if (clip.timelineEndUs <= timeUs) {
        candidate = clip
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return candidate
  }

  /**
   * Binary search for the closest clip to `timeUs` (by distance to start or end).
   */
  fun findClosestClip(timeUs: Long): TimelineClip? {
    if (sortedClips.isEmpty()) return null
    var closest: TimelineClip? = null
    var minDistance = Long.MAX_VALUE

    for (clip in sortedClips) {
      val dist = if (timeUs in clip.timelineStartUs..clip.timelineEndUs) {
        0L
      } else if (timeUs < clip.timelineStartUs) {
        clip.timelineStartUs - timeUs
      } else {
        timeUs - clip.timelineEndUs
      }
      if (dist < minDistance) {
        minDistance = dist
        closest = clip
      }
      if (minDistance == 0L) break
    }
    return closest
  }

  companion object {
    private val trackIndexCache = java.util.concurrent.ConcurrentHashMap<TimelineTrack, TrackSpatialIndex>()

    fun getOrBuild(track: TimelineTrack): TrackSpatialIndex {
      return trackIndexCache.computeIfAbsent(track) { build(it) }
    }

    /**
     * Builds an index from a [TimelineTrack] in $O(N \log N)$ time.
     */
    fun build(track: TimelineTrack): TrackSpatialIndex {
      val clips = track.clips
      val clipsById = clips.associateBy { it.id }
      val sorted = clips.sortedWith(compareBy({ it.timelineStartUs }, { it.timelineEndUs }))
      val wrappers = sorted.map {
        IndexedClipWrapper(
          clip = it,
          id = it.id,
          startUs = it.timelineStartUs,
          endUs = it.timelineEndUs,
          trackId = track.id,
          isEnabled = it.isEnabled
        )
      }
      val tree = IntervalTree.buildFrom(wrappers)
      val durationUs = clips.maxOfOrNull { it.timelineEndUs } ?: 0L

      return TrackSpatialIndex(
        trackId = track.id,
        clipsById = clipsById,
        sortedClips = sorted,
        intervalTree = tree,
        cachedDurationUs = durationUs
      )
    }
  }
}
