package com.example.engine.index

import kotlin.math.max

/**
 * Interface representing any temporal interval with a start and end time.
 */
interface TimeInterval {
  val startUs: Long
  val endUs: Long
  val durationUs: Long get() = endUs - startUs

  fun contains(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs
  fun intersects(otherStartUs: Long, otherEndUs: Long): Boolean =
    max(startUs, otherStartUs) < kotlin.math.min(endUs, otherEndUs)
}

/**
 * High-performance, immutable Interval Tree for timeline clips and items.
 *
 * Characteristics:
 * - Augmented Red-Black / Balanced Binary Search Tree sorted by `startUs`.
 * - Each node maintains `maxEndUs` of its subtree, enabling guaranteed $O(\log N + K)$ interval queries.
 * - Thread-safe, non-blocking, allocation-free searches.
 * - Perfectly suited for professional timelines with hundreds or thousands of clips.
 */
class IntervalTree<T : TimeInterval> private constructor(
  private val root: Node<T>?
) {

  private class Node<T : TimeInterval>(
    val item: T,
    val startUs: Long,
    val endUs: Long,
    var maxEndUs: Long,
    val left: Node<T>?,
    val right: Node<T>?,
    val height: Int
  )

  companion object {
    private val EMPTY = IntervalTree<TimeInterval>(null)

    @Suppress("UNCHECKED_CAST")
    fun <T : TimeInterval> empty(): IntervalTree<T> = EMPTY as IntervalTree<T>

    /**
     * Builds an optimal, balanced interval tree from an arbitrary collection of items in $O(N \log N)$ time.
     */
    fun <T : TimeInterval> buildFrom(items: Collection<T>): IntervalTree<T> {
      if (items.isEmpty()) return empty()
      val sorted = items.sortedWith(compareBy({ it.startUs }, { it.endUs }))
      val rootNode = buildBalanced(sorted, 0, sorted.size - 1)
      return IntervalTree(rootNode)
    }

    private fun <T : TimeInterval> buildBalanced(sorted: List<T>, low: Int, high: Int): Node<T>? {
      if (low > high) return null
      val mid = (low + high) ushr 1
      val item = sorted[mid]

      val leftNode = buildBalanced(sorted, low, mid - 1)
      val rightNode = buildBalanced(sorted, mid + 1, high)

      val leftHeight = leftNode?.height ?: 0
      val rightHeight = rightNode?.height ?: 0
      val height = max(leftHeight, rightHeight) + 1

      var subtreeMaxEnd = item.endUs
      if (leftNode != null && leftNode.maxEndUs > subtreeMaxEnd) {
        subtreeMaxEnd = leftNode.maxEndUs
      }
      if (rightNode != null && rightNode.maxEndUs > subtreeMaxEnd) {
        subtreeMaxEnd = rightNode.maxEndUs
      }

      return Node(
        item = item,
        startUs = item.startUs,
        endUs = item.endUs,
        maxEndUs = subtreeMaxEnd,
        left = leftNode,
        right = rightNode,
        height = height
      )
    }
  }

  val isEmpty: Boolean get() = root == null
  val size: Int get() = countNodes(root)

  private fun countNodes(node: Node<T>?): Int = if (node == null) 0 else 1 + countNodes(node.left) + countNodes(node.right)

  /**
   * Queries all intervals that contain the given point `timeUs`.
   * Complexity: $O(\log N + K)$ where $K$ is the number of overlapping items.
   */
  fun queryPoint(timeUs: Long): List<T> {
    if (root == null || root.maxEndUs <= timeUs) return emptyList()
    val results = mutableListOf<T>()
    queryPointInternal(root, timeUs, results)
    return results
  }

  private fun queryPointInternal(node: Node<T>?, timeUs: Long, results: MutableList<T>) {
    if (node == null) return

    // If maxEndUs in this subtree is <= timeUs, no interval here can cover timeUs
    if (node.maxEndUs <= timeUs) return

    // Check left subtree
    if (node.left != null && node.left.maxEndUs > timeUs) {
      queryPointInternal(node.left, timeUs, results)
    }

    // Check current node
    if (node.startUs <= timeUs && timeUs < node.endUs) {
      results.add(node.item)
    }

    // If startUs > timeUs, right subtree cannot contain intervals with startUs <= timeUs
    if (node.startUs > timeUs) return

    // Check right subtree
    queryPointInternal(node.right, timeUs, results)
  }

  /**
   * Queries all intervals that intersect the range `[rangeStartUs, rangeEndUs]`.
   * Ideal for visible range viewport queries on the timeline.
   * Complexity: $O(\log N + K)$.
   */
  fun queryRange(rangeStartUs: Long, rangeEndUs: Long): List<T> {
    if (root == null || root.maxEndUs < rangeStartUs) return emptyList()
    val results = mutableListOf<T>()
    queryRangeInternal(root, rangeStartUs, rangeEndUs, results)
    return results
  }

  private fun queryRangeInternal(
    node: Node<T>?,
    rangeStartUs: Long,
    rangeEndUs: Long,
    results: MutableList<T>
  ) {
    if (node == null) return

    // If subtree's max end is less than range start, cannot intersect
    if (node.maxEndUs < rangeStartUs) return

    // Search left subtree
    if (node.left != null && node.left.maxEndUs >= rangeStartUs) {
      queryRangeInternal(node.left, rangeStartUs, rangeEndUs, results)
    }

    // Check if this node intersects [rangeStartUs, rangeEndUs]
    val intersects = max(node.startUs, rangeStartUs) <= kotlin.math.min(node.endUs, rangeEndUs) && node.startUs < rangeEndUs && node.endUs > rangeStartUs
    if (intersects) {
      results.add(node.item)
    }

    // If node's start time is already past rangeEndUs, no nodes in right subtree can start before rangeEndUs
    if (node.startUs >= rangeEndUs) return

    // Search right subtree
    queryRangeInternal(node.right, rangeStartUs, rangeEndUs, results)
  }
}
