package com.example.pdf_everything.core.history

import com.example.pdf_everything.core.commands.DocumentCommand

/**
 * Manages undo/redo stacks for document commands.
 *
 * Contract:
 *  - push(command) adds to undo stack, clears redo stack
 *  - undo() pops from undo stack, pushes to redo stack, returns the command
 *  - redo() pops from redo stack, pushes to undo stack, returns the command
 *
 * Optional max depth to prevent unbounded memory growth.
 */
class HistoryManager(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH
) {
    private val undoStack = mutableListOf<DocumentCommand>()
    private val redoStack = mutableListOf<DocumentCommand>()

    fun push(command: DocumentCommand) {
        undoStack.add(command)
        if (undoStack.size > maxDepth) {
            undoStack.removeAt(0)
        }
        redoStack.clear()  // any new action invalidates the redo chain
    }

    fun undo(): DocumentCommand? {
        if (undoStack.isEmpty()) return null
        val command = undoStack.removeLast()
        redoStack.add(command)
        return command
    }

    fun redo(): DocumentCommand? {
        if (redoStack.isEmpty()) return null
        val command = redoStack.removeLast()
        undoStack.add(command)
        return command
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDescription: String? get() = undoStack.lastOrNull()?.description
    val redoDescription: String? get() = redoStack.lastOrNull()?.description

    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 200
    }
}