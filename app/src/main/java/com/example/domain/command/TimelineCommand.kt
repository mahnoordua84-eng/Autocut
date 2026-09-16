package com.example.domain.command

import com.example.domain.model.*
import com.example.engine.history.TimelineActionType
import java.util.UUID

/**
 * Base interface for all reversible timeline commands.
 * Commands must be strictly non-destructive, purely data-driven,
 * and contain no heavy bitmap/audio buffers in memory.
 */
interface TimelineCommand {
  val id: String
  val name: String
  val description: String
  val actionType: TimelineActionType
  val timestampMs: Long
  val affectedClipIds: Set<String>
  val affectedTrackIds: Set<String>

  /**
   * Executes the command forwards, producing the updated timeline state.
   */
  fun execute(state: CoreTimelineState): CoreTimelineState

  /**
   * Reverses the command, restoring the exact previous timeline state.
   */
  fun undo(state: CoreTimelineState): CoreTimelineState

  /**
   * Redoes the command. Defaults to invoking [execute].
   */
  fun redo(state: CoreTimelineState): CoreTimelineState = execute(state)
}

/**
 * Metadata entry for UI history inspection.
 */
data class CommandHistoryEntry(
  val id: String,
  val name: String,
  val description: String,
  val actionType: TimelineActionType,
  val timestampMs: Long,
  val affectedClipCount: Int,
  val isCompound: Boolean = false
)

/**
 * Composite / Batch command grouping multiple sub-commands into an atomic transaction.
 * On execute: runs sub-commands in forward order.
 * On undo: runs sub-commands in reverse order.
 */
data class CompoundCommand(
  override val id: String = UUID.randomUUID().toString(),
  override val name: String,
  override val description: String = name,
  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT,
  override val timestampMs: Long = System.currentTimeMillis(),
  val commands: List<TimelineCommand> = emptyList()
) : TimelineCommand {

  override val affectedClipIds: Set<String>
    get() = commands.flatMap { it.affectedClipIds }.toSet()

  override val affectedTrackIds: Set<String>
    get() = commands.flatMap { it.affectedTrackIds }.toSet()

  override fun execute(state: CoreTimelineState): CoreTimelineState {
    var currentState = state
    for (cmd in commands) {
      currentState = cmd.execute(currentState)
    }
    return currentState
  }

  override fun undo(state: CoreTimelineState): CoreTimelineState {
    var currentState = state
    // Undo in reverse order for atomic stack consistency
    for (cmd in commands.reversed()) {
      currentState = cmd.undo(currentState)
    }
    return currentState
  }

  override fun redo(state: CoreTimelineState): CoreTimelineState {
    var currentState = state
    for (cmd in commands) {
      currentState = cmd.redo(currentState)
    }
    return currentState
  }
}

/**
 * Lightweight structural state transition command.
 * Guarantees 100% exact state restoration for complex multi-faceted edits
 * while avoiding memory overhead (stores metadata references only).
 */
data class StateSnapshotCommand(
  override val id: String = UUID.randomUUID().toString(),
  override val name: String,
  override val description: String = name,
  override val actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT,
  override val timestampMs: Long = System.currentTimeMillis(),
  override val affectedClipIds: Set<String> = emptySet(),
  override val affectedTrackIds: Set<String> = emptySet(),
  val beforeState: CoreTimelineState,
  val afterState: CoreTimelineState
) : TimelineCommand {

  override fun execute(state: CoreTimelineState): CoreTimelineState = afterState

  override fun undo(state: CoreTimelineState): CoreTimelineState = beforeState

  override fun redo(state: CoreTimelineState): CoreTimelineState = afterState
}
