package com.example.engine.index

import com.example.domain.model.CoreTimelineState
import com.example.domain.model.TimelineClip
import com.example.domain.model.TimelineGap
import com.example.domain.model.TimelineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance global index for [CoreTimelineState].
 *
 * Replaces linear searches and flatMaps with $O(1)$ and $O(\log N)$ indexed structures:
 * - Clip lookup by ID across all tracks: $O(1)$
 * - Track lookup by clip ID: $O(1)$
 * - Playhead clip queries: $O(\log N + K)$ across all tracks
 * - Viewport visible-range queries: $O(\log N + K)$
 * - Total duration: $O(1)$
 * - Snapping index: $O(\log N)$ binary search
 */
class MasterTimelineIndex private constructor(
  private val trackIndices: Map<String, TrackSpatialIndex>,
  private val clipIdToClip: Map<String, TimelineClip>,
  private val clipIdToTrack: Map<String, TimelineTrack>,
  private val globalIntervalTree: IntervalTree<IndexedClipWrapper<TimelineClip>>,
  val cachedDurationUs: Long,
  val snapIndex: TimelineSnapIndex
) {

  val totalClipsCount: Int get() = clipIdToClip.size
  val totalTracksCount: Int get() = trackIndices.size

  @Volatile
  private var _allGapsCache: List<TimelineGap>? = null

  @Volatile
  private var _allOverlapsCache: Map<String, List<ClipOverlap<TimelineClip>>>? = null

  /**
   * Fast $O(1)$ lookup of any clip across any track by ID.
   */
  fun findClip(clipId: String): TimelineClip? = clipIdToClip[clipId]

  /**
   * Fast $O(1)$ lookup of the track containing a specific clip ID.
   */
  fun findTrackForClip(clipId: String): TimelineTrack? = clipIdToTrack[clipId]

  /**
   * Fast $O(1)$ lookup of track index by track ID.
   */
  fun getTrackIndex(trackId: String): TrackSpatialIndex? = trackIndices[trackId]

  /**
   * Returns all active clips intersecting the playhead position.
   * Runs in $O(\log N + K)$ instead of scanning every clip on every track.
   */
  fun getClipsAtPlayhead(playheadUs: Long): List<TimelineClip> {
    if (playheadUs < 0L || playheadUs >= cachedDurationUs) return emptyList()
    return globalIntervalTree.queryPoint(playheadUs).map { it.clip }.filter { it.isEnabled }
  }

  /**
   * Visible range query for viewport culling.
   * Returns all clips intersecting `[startUs, endUs]` across all tracks in $O(\log N + K)$ time.
   */
  fun getClipsInVisibleRange(startUs: Long, endUs: Long): List<TimelineClip> {
    if (endUs <= 0L || startUs >= cachedDurationUs) return emptyList()
    return globalIntervalTree.queryRange(startUs, endUs).map { it.clip }
  }

  /**
   * Visible range query returning clips grouped by track.
   */
  fun getClipsInVisibleRangeByTrack(startUs: Long, endUs: Long): Map<String, List<TimelineClip>> {
    val visible = getClipsInVisibleRange(startUs, endUs)
    return visible.groupBy { it.trackId }
  }

  /**
   * Lazy cached computation of all gaps across all tracks.
   */
  fun findAllGaps(timelineDurationUs: Long = cachedDurationUs): List<TimelineGap> {
    val existing = _allGapsCache
    if (existing != null) return existing
    val gaps = trackIndices.values.flatMap { it.findGaps(timelineDurationUs) }
    _allGapsCache = gaps
    return gaps
  }

  /**
   * Lazy cached computation of all overlaps across all tracks.
   */
  fun findAllOverlaps(): Map<String, List<ClipOverlap<TimelineClip>>> {
    val existing = _allOverlapsCache
    if (existing != null) return existing
    val result = trackIndices.mapValues { (_, trackIdx) -> trackIdx.findOverlaps() }
    _allOverlapsCache = result
    return result
  }

  companion object {
    private val indexCache = ConcurrentHashMap<CoreTimelineState, MasterTimelineIndex>()

    /**
     * Retrieves or builds the index for a [CoreTimelineState].
     */
    fun getOrBuild(state: CoreTimelineState): MasterTimelineIndex {
      return indexCache.computeIfAbsent(state) { s -> build(s) }
    }

    /**
     * Builds a new index for [CoreTimelineState] in $O(N \log N)$ time.
     */
    fun build(state: CoreTimelineState): MasterTimelineIndex {
      val trackIndexMap = HashMap<String, TrackSpatialIndex>(state.tracks.size)
      val clipMap = HashMap<String, TimelineClip>()
      val trackMap = HashMap<String, TimelineTrack>()
      val allClipWrappers = mutableListOf<IndexedClipWrapper<TimelineClip>>()
      val snapPointsMs = mutableSetOf<Long>()
      val pointsByClip = HashMap<String, MutableList<Long>>()

      // Boundary snap points
      snapPointsMs.add(0L)
      snapPointsMs.add(state.durationUs / 1000L)

      var maxEnd = 0L

      for (track in state.tracks) {
        val tIdx = TrackSpatialIndex.build(track)
        trackIndexMap[track.id] = tIdx
        if (tIdx.cachedDurationUs > maxEnd) {
          maxEnd = tIdx.cachedDurationUs
        }

        for (clip in track.clips) {
          clipMap[clip.id] = clip
          trackMap[clip.id] = track

          val wrapper = IndexedClipWrapper(
            clip = clip,
            id = clip.id,
            startUs = clip.timelineStartUs,
            endUs = clip.timelineEndUs,
            trackId = track.id,
            isEnabled = clip.isEnabled
          )
          allClipWrappers.add(wrapper)

          // Collect snap points for this clip
          val clipSnapList = mutableListOf<Long>()
          val startMs = clip.timelineStartMs
          val endMs = clip.timelineEndMs
          snapPointsMs.add(startMs)
          snapPointsMs.add(endMs)
          clipSnapList.add(startMs)
          clipSnapList.add(endMs)

          // Keyframe snap points
          for (kf in clip.keyframes) {
            val kfMs = startMs + kf.timeMs
            snapPointsMs.add(kfMs)
            clipSnapList.add(kfMs)
          }

          pointsByClip[clip.id] = clipSnapList
        }
      }

      val globalTree = IntervalTree.buildFrom(allClipWrappers)
      val finalDurationUs = maxOf(state.durationUs, maxEnd)
      val snapIdx = TimelineSnapIndex.build(snapPointsMs, pointsByClip)

      return MasterTimelineIndex(
        trackIndices = trackIndexMap,
        clipIdToClip = clipMap,
        clipIdToTrack = trackMap,
        globalIntervalTree = globalTree,
        cachedDurationUs = finalDurationUs,
        snapIndex = snapIdx
      )
    }

    /**
     * Asynchronously build index in background dispatcher to avoid blocking UI threads.
     */
    suspend fun buildAsync(state: CoreTimelineState): MasterTimelineIndex =
      withContext(Dispatchers.Default) {
        build(state)
      }
  }
}
