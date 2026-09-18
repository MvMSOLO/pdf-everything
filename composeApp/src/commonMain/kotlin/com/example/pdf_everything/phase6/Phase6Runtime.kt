package com.example.pdf_everything.phase6

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DirtyState

class RecoveryAutosaveCoordinator(
    private val persistence: Phase6PersistenceManager,
    private val intervalMs: Long = 15_000L
) {
    private var lastWriteAt = 0L
    private var latest: RecoveryEntry? = null

    fun maybeAutosave(document: Document, nowMs: Long): RecoveryEntry? {
        if (!document.dirty || nowMs - lastWriteAt < intervalMs) return latest
        lastWriteAt = nowMs
        latest = persistence.createRecovery(document)
        return latest
    }

    fun clearAfterSuccessfulSave(): Boolean {
        val entry = latest ?: return false
        latest = null
        return persistence.deleteRecovery(entry)
    }

    fun reset() { lastWriteAt = 0L; latest = null }
}

object Phase6SaveState {
    fun saving(document: Document): Document = document.copy(dirty = true, dirtyState = DirtyState.SAVING)
    fun failed(document: Document): Document = document.copy(dirty = true, dirtyState = DirtyState.SAVE_FAILED)
}
