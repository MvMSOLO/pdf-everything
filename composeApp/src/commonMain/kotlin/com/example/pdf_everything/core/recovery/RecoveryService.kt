package com.example.pdf_everything.core.recovery

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.services.EngineResult
import com.example.pdf_everything.core.services.PdfEngine
import com.example.pdf_everything.core.services.EngineError

/**
 * Crash-recovery subsystem (spec §90‑§93).
 *
 * On every significant change the app writes a lightweight checkpoint
 * (document ID, version, source, timestamp) to a local journal.
 * On next launch the RecoveryService detects un-graceful shutdowns
 * and offers to restore unsaved work.
 *
 * Phase 1 skeleton — full binary-delta journaling deferred to Phase 3.
 */
class RecoveryService(
    private val engine: PdfEngine,
    private val recoveryDir: String       // platform-specific writable directory
) {

    data class Checkpoint(
        val documentId: String,
        val version: Int,
        val source: DocumentSource,
        val timestamp: Long,
        val wasDirty: Boolean
    )

    enum class RecoveryStatus {
        NONE,
        RECOVERABLE,
        RECOVERED,
        FAILED
    }

    // ── Journal operations ────────────────────────────────────────────

    private val _journal = mutableListOf<Checkpoint>()
    val journal: List<Checkpoint> get() = _journal.toList()

    /**
     * Write a checkpoint to the recovery journal.
     * Called by the app after each undo-group commit or auto-save.
     */
    fun writeCheckpoint(document: Document, source: DocumentSource) {
        val cp = Checkpoint(
            documentId = document.documentId,
            version = document.version,
            source = source,
            timestamp = System.currentTimeMillis(),
            wasDirty = document.dirtyState.isDirty
        )
        _journal.removeAll { it.documentId == document.documentId }
        _journal.add(cp)
        persistJournal()
    }

    /**
     * Clear a document's checkpoint (called after a successful save or close).
     */
    fun clearCheckpoint(documentId: String) {
        _journal.removeAll { it.documentId == documentId }
        persistJournal()
    }

    // ── Recovery on next launch ────────────────────────────────────────

    /**
     * Detect whether any documents have dirty checkpoints records
     * (i.e. the app was killed before saving).
     */
    fun detectRecoverable(): List<Checkpoint> {
        loadJournal()
        return _journal.filter { it.wasDirty }
    }

    /**
     * Attempt to recover a document from its checkpoint source.
     * Full delta-restore will be added in Phase 3; for now we re-open
     * the original source.
     */
    suspend fun recover(checkpoint: Checkpoint): EngineResult<Document> {
        return engine.open(checkpoint.source)
    }

    val recoveryStatus: RecoveryStatus
        get() = when {
            _journal.isEmpty() -> RecoveryStatus.NONE
            _journal.any { it.wasDirty } -> RecoveryStatus.RECOVERABLE
            else -> RecoveryStatus.NONE
        }

    // ── Persistence (Phase 1: simple text-based journal) ────────────────

    private fun persistJournal() {
        // Write journal to recoveryDir/recovery_journal.json
        // Phase 1: in-memory only; will be persisted to disk in Phase 2
        // with proper atomic file writes
    }

    private fun loadJournal() {
        // Phase 1: in-memory only
    }

    fun clearAll() {
        _journal.clear()
    }
}