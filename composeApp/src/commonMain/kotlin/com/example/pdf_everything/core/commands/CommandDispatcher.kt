package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.history.HistoryManager

/**
 * Central dispatcher for all document commands.
 *
 * Flow: UI → CommandDispatcher.dispatch(command) → HistoryManager + Document mutation
 *
 * Every mutating action must go through this dispatcher to guarantee:
 *  - undo/redo works
 *  - audit trail exists
 *  - state is consistent
 */
class CommandDispatcher(
    private val historyManager: HistoryManager
) {
    
    fun dispatch(command: DocumentCommand, document: Document): Document {
        if (!command.canExecute()) return document
        val result = command.execute(document)
        historyManager.push(command)
        return result
    }

    fun undo(document: Document): Document {
        val command = historyManager.undo() ?: return document
        return command.undo(document)
    }

    fun redo(document: Document): Document {
        val command = historyManager.redo() ?: return document
        return command.redo(document)
    }

    val canUndo: Boolean get() = historyManager.canUndo
    val canRedo: Boolean get() = historyManager.canRedo
    val undoDescription: String? get() = historyManager.undoDescription
    val redoDescription: String? get() = historyManager.redoDescription
}}