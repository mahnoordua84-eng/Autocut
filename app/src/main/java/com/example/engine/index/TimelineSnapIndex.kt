package com.example.engine.index

import java.util.Arrays
import kotlin.math.abs

/**
 * Ultra-fast, zero-allocation binary-search snapping index.
 *
 * Replaces linear $O(N)$ clip and keyframe iteration with $O(\log N)$ binary search.
 * Snap points are pre-sorted and stored in a contiguous primitive `LongArray` for maximum cache locality.
 */
class TimelineSnapIndex(
  private val snapPointsMs: LongArray,
  private val snapPointsByClipId: Map<String, LongArray> = emptyMap()
) {

  val size: Int get() = snapPointsMs.size

  /**
   * Finds the closest snap position to `candidatePosMs` within `thresholdMs`.
   * Operates in $O(\log N)$ time with 0 heap allocations during scrubbing.
   */
  fun findClosestSnap(
    candidatePosMs: Long,
    thresholdMs: Long,
    ignoreClipIds: Set<String> = emptySet(),
    additionalPoints: LongArray? = null
  ): Long? {
    if (snapPointsMs.isEmpty() && (additionalPoints == null || additionalPoints.isEmpty())) {
      return null
    }

    var bestDist = thresholdMs + 1L
    var bestPoint: Long? = null

    // 1. Binary search on main pre-indexed points
    val bs = Arrays.binarySearch(snapPointsMs, candidatePosMs)
    if (bs >= 0) {
      // Exact match
      val exact = snapPointsMs[bs]
      if (ignoreClipIds.isEmpty() || !isPointIgnored(exact, ignoreClipIds)) {
        return exact
      }
    }

    val insertionIndex = if (bs < 0) -bs - 1 else bs

    // Check neighbors around insertion point
    val checkRadius = 2
    val minIdx = (insertionIndex - checkRadius).coerceAtLeast(0)
    val maxIdx = (insertionIndex + checkRadius).coerceAtMost(snapPointsMs.size - 1)

    for (i in minIdx..maxIdx) {
      val pt = snapPointsMs[i]
      if (ignoreClipIds.isNotEmpty() && isPointIgnored(pt, ignoreClipIds)) {
        continue
      }
      val dist = abs(pt - candidatePosMs)
      if (dist <= thresholdMs && dist < bestDist) {
        bestDist = dist
        bestPoint = pt
      }
    }

    // 2. Check additional dynamic points (e.g. current playhead or candidate cut)
    if (additionalPoints != null) {
      for (pt in additionalPoints) {
        val dist = abs(pt - candidatePosMs)
        if (dist <= thresholdMs && dist < bestDist) {
          bestDist = dist
          bestPoint = pt
        }
      }
    }

    return bestPoint
  }

  private fun isPointIgnored(pointMs: Long, ignoreClipIds: Set<String>): Boolean {
    for (id in ignoreClipIds) {
      val clipPts = snapPointsByClipId[id]
      if (clipPts != null && Arrays.binarySearch(clipPts, pointMs) >= 0) {
        return true
      }
    }
    return false
  }

  companion object {
    val EMPTY = TimelineSnapIndex(LongArray(0))

    /**
     * Builds a sorted snap index from points. Deduplicates and sorts in $O(N \log N)$ once.
     */
    fun build(
      pointsMs: Collection<Long>,
      pointsByClip: Map<String, Collection<Long>> = emptyMap()
    ): TimelineSnapIndex {
      val sortedUnique = pointsMs.toSortedSet().toLongArray()
      val map = pointsByClip.mapValues { (_, pts) -> pts.toSortedSet().toLongArray() }
      return TimelineSnapIndex(sortedUnique, map)
    }
  }
}
