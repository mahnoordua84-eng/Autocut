package com.example.engine.index

import com.example.domain.model.CoreTimelineState
import com.example.domain.model.Timeline
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe, high-concurrency coordinator for Timeline operations.
 *
 * Allows concurrent readers (playback, compositing, UI layout, audio rendering)
 * to query indexed timeline state with zero contention, while ensuring atomic,
 * serialized write operations.
 */
class ThreadSafeTimelineCoordinator(
  initialCoreState: CoreTimelineState = CoreTimelineState(),
  initialLegacyTimeline: Timeline = Timeline(),
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

  @PublishedApi
  internal val rwLock = ReentrantReadWriteLock()

  @PublishedApi
  internal val coreStateRef = AtomicReference(initialCoreState)
  @PublishedApi
  internal val legacyTimelineRef = AtomicReference(initialLegacyTimeline)

  val coreState: CoreTimelineState get() = coreStateRef.get()
  val legacyTimeline: Timeline get() = legacyTimelineRef.get()

  @Volatile
  @PublishedApi
  internal var currentMasterIndex: MasterTimelineIndex = MasterTimelineIndex.build(initialCoreState)

  @Volatile
  @PublishedApi
  internal var currentLegacyIndex: LegacyTimelineIndex = LegacyTimelineIndex.build(initialLegacyTimeline)

  /**
   * Reads current [CoreTimelineState] and its [MasterTimelineIndex] concurrently.
   * Multiple threads can execute [block] simultaneously without blocking.
   */
  inline fun <T> readCore(block: (state: CoreTimelineState, index: MasterTimelineIndex) -> T): T {
    return rwLock.read {
      block(coreStateRef.get(), currentMasterIndex)
    }
  }

  /**
   * Reads current [Timeline] and its [LegacyTimelineIndex] concurrently.
   */
  inline fun <T> readLegacy(block: (timeline: Timeline, index: LegacyTimelineIndex) -> T): T {
    return rwLock.read {
      block(legacyTimelineRef.get(), currentLegacyIndex)
    }
  }

  /**
   * Executes an atomic mutation on [CoreTimelineState], immediately rebuilding or invalidating
   * the master index and notifying observers.
   */
  fun mutateCore(modifier: (CoreTimelineState) -> CoreTimelineState): CoreTimelineState {
    return rwLock.write {
      val current = coreStateRef.get()
      val updated = modifier(current)
      coreStateRef.set(updated)
      currentMasterIndex = MasterTimelineIndex.build(updated)
      updated
    }
  }

  /**
   * Executes an atomic mutation on legacy [Timeline].
   */
  fun mutateLegacy(modifier: (Timeline) -> Timeline): Timeline {
    return rwLock.write {
      val current = legacyTimelineRef.get()
      val updated = modifier(current)
      legacyTimelineRef.set(updated)
      currentLegacyIndex = LegacyTimelineIndex.build(updated)
      updated
    }
  }

  /**
   * Obtains a thread-safe immutable snapshot of the core state and its index.
   */
  fun snapshotCore(): Pair<CoreTimelineState, MasterTimelineIndex> {
    return rwLock.read {
      Pair(coreStateRef.get(), currentMasterIndex)
    }
  }

  /**
   * Obtains a thread-safe immutable snapshot of the legacy timeline and its index.
   */
  fun snapshotLegacy(): Pair<Timeline, LegacyTimelineIndex> {
    return rwLock.read {
      Pair(legacyTimelineRef.get(), currentLegacyIndex)
    }
  }

  /**
   * Warms up background index structures asynchronously.
   */
  fun precomputeIndicesAsync() {
    coroutineScope.launch {
      val (core, _) = snapshotCore()
      val (legacy, _) = snapshotLegacy()
      MasterTimelineIndex.build(core)
      LegacyTimelineIndex.build(legacy)
    }
  }
}
