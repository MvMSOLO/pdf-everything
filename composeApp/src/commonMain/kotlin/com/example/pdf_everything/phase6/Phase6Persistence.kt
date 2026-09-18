package com.example.pdf_everything.phase6

import com.example.pdf_everything.core.document.DirtyState
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentModelService
import com.example.pdf_everything.pdf_engine.api.PdfPersistenceCoordinator
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import kotlinx.serialization.json.Json

/** Central Phase 6 coordinator: conflict detection, atomic persistence, recovery journal, export and print. */
class Phase6PersistenceManager(
    private val engine: PdfEngine,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) {
    fun currentFingerprint(document: Document): SourceFingerprint? = document.source?.let { Phase6Platform.fingerprint(it) }
    fun currentFingerprint(source: com.example.pdf_everything.core.document.DocumentSource): SourceFingerprint? = Phase6Platform.fingerprint(source)

    fun checkExternalConflict(document: Document): Phase6OperationResult {
        val source = document.source ?: return Phase6OperationResult(false, "Document has no source path", SaveFailureReason.IO)
        val baseline = document.historyMetadata.audit.lastSavedFingerprint
            ?: document.historyMetadata.audit.sourceFingerprint
            ?: return Phase6OperationResult(true, "No baseline fingerprint available")
        val current = Phase6Platform.fingerprint(source)?.value
            ?: return Phase6OperationResult(false, "The source file can no longer be inspected", SaveFailureReason.IO)
        return if (current == baseline) Phase6OperationResult(true, "Source has not changed outside PDF Everything")
        else Phase6OperationResult(false, "The PDF was changed outside PDF Everything", SaveFailureReason.EXTERNAL_CONFLICT)
    }

    fun save(document: Document, targetPath: String, incremental: Boolean = false, allowExternalConflict: Boolean = false): Document {
        require(document.permissions.canModify) { "This document is read-only; use Save As to create a writable copy." }
        val conflict = if (!allowExternalConflict && targetPath == document.sourcePath) checkExternalConflict(document) else Phase6OperationResult(true, "Explicit save/Save As destination")
        if (!conflict.ok) error(conflict.message)
        val working = DocumentModelService.validateOrThrow(document).withDirtyState(DirtyState.SAVING)
        // Critical persistence invariant: apply the in-memory document model to native PDF structure before serialization.
        engine.applyDocumentStructure(working)
        val report = Phase6Platform.persist(engine, working, targetPath, incremental)
        check(report.validation.valid) { "Save validation failed: ${report.validation.errors.joinToString("; ")}" }
        val fingerprint = Phase6Platform.fingerprint(com.example.pdf_everything.core.document.DocumentSource.FilePath(targetPath))?.value
        return DocumentModelService.markClean(working, nowMs(), fingerprint)
            .copy(source = com.example.pdf_everything.core.document.DocumentSource.FilePath(targetPath))
    }

    fun createRecovery(document: Document): RecoveryEntry? {
        if (!document.dirty || document.pages.isEmpty()) return null
        val now = nowMs()
        val id = "${document.id}-recovery-${document.version}"
        val sourceFp = currentFingerprint(document)?.value
        val entry = RecoveryEntry(id, document.name, document.source, now, now, id, sourceFp)
        return if (Phase6Platform.writeRecovery(entry, json.encodeToString(Document.serializer(), document))) entry else null
    }

    fun restoreRecovery(entry: RecoveryEntry): Document? = Phase6Platform.readRecovery(entry)?.let {
        runCatching { json.decodeFromString(Document.serializer(), it) }.getOrNull()
    }

    fun deleteRecovery(entry: RecoveryEntry): Boolean = Phase6Platform.deleteRecovery(entry)

    fun recoverableEntries(): List<RecoveryEntry> = Phase6Platform.listRecoveries().sortedByDescending { it.updatedAtEpochMs }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
