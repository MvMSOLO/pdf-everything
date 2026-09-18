package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.Document

/**
 * Single application-facing entry point for document mutations.
 * UI code should create a DocumentCommand and hand it here instead of
 * mutating Document state directly.
 */
class CommandDispatcher(
    private val history: DocumentHistory = DocumentHistory()
) {
    data class Result(
        val document: Document,
        val changed: Boolean,
        val error: Throwable? = null
    )

    fun dispatch(document: Document, command: DocumentCommand): Result {
        if (!command.canExecute(document)) {
            return Result(document, changed = false, error = IllegalStateException("Command cannot be executed: ${command.description}"))
        }
        return runCatching {
            val updated = history.execute(document, command)
            Result(updated, changed = updated != document)
        }.getOrElse { Result(document, changed = false, error = it) }
    }

    fun undo(document: Document): Result = runCatching {
        val updated = history.undo(document)
        Result(updated, updated != document)
    }.getOrElse { Result(document, false, it) }

    fun redo(document: Document): Result = runCatching {
        val updated = history.redo(document)
        Result(updated, updated != document)
    }.getOrElse { Result(document, false, it) }

    fun clear() = history.clear()
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
}
