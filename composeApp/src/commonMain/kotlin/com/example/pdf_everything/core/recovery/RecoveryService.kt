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
 * Phase 1 — simple text-line journal; full binary-delta journaling deferred to Phase 3.
 */
class RecoveryService(
    private val engine: PdfEngine,
    private val fileSystemProvider: FileSystemProvider
) {

    data class Checkpoint(
        val documentId: String,
        val version: Int,
        val sourceType: String,   // "FilePath", "ContentUri", "ByteArraySource"
        val sourcePath: String?,  // FilePath path or ContentUri uri
        val sourceDisplayName: String?,
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
            sourceType = when (source) {
                is DocumentSource.FilePath -> "FilePath"
                is DocumentSource.ContentUri -> "ContentUri"
                is DocumentSource.ByteArraySource -> "ByteArraySource"
            },
            sourcePath = when (source) {
                is DocumentSource.FilePath -> source.path
                is DocumentSource.ContentUri -> source.uri
                else -> null
            },
            sourceDisplayName = when (source) {
                is DocumentSource.ContentUri -> source.displayName
                is DocumentSource.ByteArraySource -> source.displayName
                else -> null
            },
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
        val source: DocumentSource = when (checkpoint.sourceType) {
            "FilePath" -> {
                val path = checkpoint.sourcePath ?: return EngineResult.Failure(
                    EngineError.FILE_NOT_FOUND, "Missing file path for recovery")
                DocumentSource.FilePath(path)
            }
            "ContentUri" -> DocumentSource.ContentUri(
                checkpoint.sourcePath ?: "",
                checkpoint.sourceDisplayName ?: "document.pdf"
            )
            "ByteArraySource" -> {
                // ByteArray cannot be persisted in Phase 1 journal;
                // recovery not possible without the original bytes.
                return EngineResult.Failure(
                    EngineError.UNSUPPORTED_FEATURE,
                    "Cannot recover ByteArraySource without original bytes"
                )
            }
            else -> return EngineResult.Failure(
                EngineError.UNKNOWN,
                "Unknown source type: ${checkpoint.sourceType}"
            )
        }
        return engine.open(source)
    }

    val recoveryStatus: RecoveryStatus
        get() = when {
            _journal.isEmpty() -> RecoveryStatus.NONE
            _journal.any { it.wasDirty } -> RecoveryStatus.RECOVERABLE
            else -> RecoveryStatus.NONE
        }

    // ── Persistence — simple delimiter-separated lines ──────────────────

    private fun persistJournal() {
        val lines = _journal.map { cp ->
            listOf(
                cp.documentId,
                cp.version.toString(),
                cp.sourceType,
                cp.sourcePath ?: "",
                cp.sourceDisplayName ?: "",
                cp.timestamp.toString(),
                cp.wasDirty.toString()
            ).joinToString("\t")
        }
        fileSystemProvider.writeText(JOURNAL_FILE, lines.joinToString("\n"))
    }

    private fun loadJournal() {
        val text = fileSystemProvider.readText(JOURNAL_FILE) ?: return
        _journal.clear()
        for (line in text.lines()) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size < 7) continue
            try {
                _journal.add(Checkpoint(
                    documentId = parts[0],
                    version = parts[1].toInt(),
                    sourceType = parts[2],
                    sourcePath = parts[3].ifBlank { null },
                    sourceDisplayName = parts[4].ifBlank { null },
                    timestamp = parts[5].toLong(),
                    wasDirty = parts[6].toBoolean()
                ))
            } catch (_: Exception) {
                // Skip corrupt lines
            }
        }
    }

    fun clearAll() {
        _journal.clear()
        fileSystemProvider.delete(JOURNAL_FILE)
    }

    companion object {
        private const val JOURNAL_FILE = "recovery_journal.txt"
    }
}