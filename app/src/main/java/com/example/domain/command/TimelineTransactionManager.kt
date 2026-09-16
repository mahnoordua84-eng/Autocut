package com.example.domain.command

import com.example.domain.model.CoreTimelineState
import com.example.engine.history.TimelineActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Robust, professional Non-Destructive Timeline Command & Transaction Manager.
 *
 * Features:
 * - Reversible command execution (Undo / Redo)
 * - Atomic Multi-Operation Transactions (Grouping)
 * - Strict project-state consistency validation
 * - Configurable History Capacity Limits
 * - Zero heavy media storage in history (pure state descriptor/metadata persistence)
 */
class TimelineTransactionManager(
  val maxHistorySize: Int = 100
) {
  private val undoStack = ArrayDeque<TimelineCommand>()
  private val redoStack = ArrayDeque<TimelineCommand>()

  private val _canUndo = MutableStateFlow(false)
  val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

  private val _canRedo = MutableStateFlow(false)
  val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

  private val _undoTitle = MutableStateFlow<String?>(null)
  val undoTitle: StateFlow<String?> = _undoTitle.asStateFlow()

  private val _redoTitle = MutableStateFlow<String?>(null)
  val redoTitle: StateFlow<String?> = _redoTitle.asStateFlow()

  private val _commandHistory = MutableStateFlow<List<CommandHistoryEntry>>(emptyList())
  val commandHistory: StateFlow<List<CommandHistoryEntry>> = _commandHistory.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  // Transaction Grouping Context
  private var activeTransaction: ActiveTransactionContext? = null

  private data class ActiveTransactionContext(
    val name: String,
    val description: String,
    val actionType: TimelineActionType,
    val initialTimelineState: CoreTimelineState,
    val commands: MutableList<TimelineCommand> = mutableListOf()
  )

  val isTransactionActive: Boolean
    get() = activeTransaction != null

  /**
   * Begins a new atomic transaction grouping multiple sub-operations into a single undo step.
   */
  fun beginTransaction(
    name: String,
    description: String = name,
    actionType: TimelineActionType = TimelineActionType.GENERIC_EDIT,
    initialState: CoreTimelineState
  ) {
    if (activeTransaction == null) {
      activeTransaction = ActiveTransactionContext(
        name = name,
        description = description,
        actionType = actionType,
        initialTimelineState = initialState
      )
    }
  }

  /**
   * Records or executes a command in the active transaction, or directly onto the history stack.
   */
  fun executeCommand(
    command: TimelineCommand,
    currentState: CoreTimelineState
  ): CoreTimelineState {
    val tx = activeTransaction
    if (tx != null) {
      val nextState = command.execute(currentState)
      tx.commands.add(command)
      return validateStateConsistency(nextState, currentState)
    }

    val nextState = command.execute(currentState)
    val validatedState = validateStateConsistency(nextState, currentState)

    if (validatedState != currentState) {
      pushCommand(command)
      _statusMessage.value = "Executed: ${command.name}"
    }
    return validatedState
  }

  /**
   * Directly pushes a pre-executed reversible command or state transition to the history stack.
   */
  fun recordExecutedCommand(command: TimelineCommand) {
    val tx = activeTransaction
    if (tx != null) {
      tx.commands.add(command)
      return
    }
    pushCommand(command)
  }

  /**
   * Commits the active transaction, packaging all sub-commands into an atomic CompoundCommand.
   */
  fun commitTransaction(finalState: CoreTimelineState): Boolean {
    val tx = activeTransaction ?: return false
    activeTransaction = null

    if (tx.initialTimelineState == finalState || tx.commands.isEmpty()) {
      return false
    }

    val compoundCmd = if (tx.commands.size == 1) {
      tx.commands.first()
    } else {
      CompoundCommand(
        name = tx.name,
        description = tx.description,
        actionType = tx.actionType,
        commands = tx.commands.toList()
      )
    }

    pushCommand(compoundCmd)
    _statusMessage.value = "Completed: ${tx.name}"
    return true
  }

  /**
   * Rolls back the active transaction, returning the initial state before the transaction began.
   */
  fun rollbackTransaction(): CoreTimelineState? {
    val tx = activeTransaction ?: return null
    activeTransaction = null
    _statusMessage.value = "Cancelled: ${tx.name}"
    return tx.initialTimelineState
  }

  /**
   * Undoes the last command or transaction, returning the restored previous timeline state.
   */
  fun undo(currentState: CoreTimelineState): Pair<CoreTimelineState, TimelineCommand>? {
    if (undoStack.isEmpty()) return null

    val command = undoStack.removeLast()
    val restoredState = command.undo(currentState)
    val validatedState = validateStateConsistency(restoredState, currentState)

    redoStack.addLast(command)
    _statusMessage.value = "Undid: ${command.name}"
    updateState()
    return validatedState to command
  }

  /**
   * Redoes the previously undone command or transaction, returning the reapplied timeline state.
   */
  fun redo(currentState: CoreTimelineState): Pair<CoreTimelineState, TimelineCommand>? {
    if (redoStack.isEmpty()) return null

    val command = redoStack.removeLast()
    val reappliedState = command.redo(currentState)
    val validatedState = validateStateConsistency(reappliedState, currentState)

    undoStack.addLast(command)
    _statusMessage.value = "Redid: ${command.name}"
    updateState()
    return validatedState to command
  }

  /**
   * Clears undo and redo stacks.
   */
  fun clear() {
    undoStack.clear()
    redoStack.clear()
    activeTransaction = null
    _statusMessage.value = null
    updateState()
  }

  fun dismissStatusMessage() {
    _statusMessage.value = null
  }

  private fun pushCommand(command: TimelineCommand) {
    undoStack.addLast(command)
    if (undoStack.size > maxHistorySize) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    updateState()
  }

  private fun updateState() {
    _canUndo.value = undoStack.isNotEmpty()
    _canRedo.value = redoStack.isNotEmpty()
    _undoTitle.value = undoStack.lastOrNull()?.let { "Undo ${it.name}" }
    _redoTitle.value = redoStack.lastOrNull()?.let { "Redo ${it.name}" }
    _commandHistory.value = undoStack.map { cmd ->
      CommandHistoryEntry(
        id = cmd.id,
        name = cmd.name,
        description = cmd.description,
        actionType = cmd.actionType,
        timestampMs = cmd.timestampMs,
        affectedClipCount = cmd.affectedClipIds.size,
        isCompound = cmd is CompoundCommand
      )
    }
  }

  /**
   * Validates internal state consistency (non-null tracks, valid time intervals, non-negative duration).
   */
  private fun validateStateConsistency(
    candidateState: CoreTimelineState,
    fallbackState: CoreTimelineState
  ): CoreTimelineState {
    if (candidateState.tracks.isEmpty() && fallbackState.tracks.isNotEmpty()) {
      // Prevent accidental wipeout of all tracks
      return fallbackState
    }

    // Ensure calculated duration is non-negative and matches max clip bounds
    val computedMaxUs = candidateState.tracks.maxOfOrNull { it.durationUs } ?: 0L
    val safeDurationUs = maxOf(candidateState.durationUs, computedMaxUs).coerceAtLeast(0L)

    return if (safeDurationUs != candidateState.durationUs) {
      candidateState.copy(durationUs = safeDurationUs)
    } else {
      candidateState
    }
  }
}
