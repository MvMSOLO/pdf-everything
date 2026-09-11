package com.example.pdf_everything.core.files

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.services.EngineResult
import com.example.pdf_everything.core.services.PdfEngine
import com.example.pdf_everything.core.services.PlatformService
import com.example.pdf_everything.core.services.SaveConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-level service for opening and saving PDF documents.
 *
 * Orchestrates PlatformService (file picking) + PdfEngine (parsing / rendering).
 * Per spec §31‑§33:
 *   - Open from file picker, content URI, byte array, or recent-file list
 *   - Save (full / incremental)
 *   - Auto-save timer
 *   - Crash recovery checkpoint
 *   - Recent-files registry
 */
class DocumentFileService(
    private val engine: PdfEngine,
    private val platform: PlatformService
) {

    // ── Active document tracking ──────────────────────────────────────

    private val _openDocuments = mutableMapOf<String, DocumentSession>()
    val openDocuments: Map<String, DocumentSession> get() = _openDocuments

    // ── Recent files ──────────────────────────────────────────────────

    private val _recentFiles = mutableListOf<RecentFile>()
    val recentFiles: List<RecentFile> get() = _recentFiles.toList()

    // ── Auto-save ─────────────────────────────────────────────────────

    private var autoSaveIntervalMs: Long = DEFAULT_AUTO_SAVE_MS
    private var autoSaveJob: Job? = null

    // ── Open ───────────────────────────────────────────────────────────

    suspend fun openFromPicker(
        password: String? = null
    ): EngineResult<Document> {
        val source = platform.pickOpenFile() ?: return EngineResult.Failure(
            com.example.pdf_everything.core.services.EngineError.FILE_NOT_FOUND,
            "No file selected"
        )
        return openDocument(source, password)
    }

    suspend fun openDocument(
        source: DocumentSource,
        password: String? = null
    ): EngineResult<Document> {
        val result = engine.open(source, password)
        when (result) {
            is EngineResult.Success -> {
                val doc = result.value
                _openDocuments[doc.documentId] = DocumentSession(
                    document = doc,
                    source = source,
                    lastSavedVersion = doc.version
                )
                addToRecent(source, doc)
            }
            is EngineResult.Failure -> { /* error already wrapped */ }
        }
        return result
    }

    /** Open from a byte array (e.g. shared intent payload). */
    suspend fun openFromBytes(
        bytes: ByteArray,
        displayName: String = "shared.pdf",
        password: String? = null
    ): EngineResult<Document> {
        val source = DocumentSource.ByteArraySource(bytes, displayName)
        return openDocument(source, password)
    }

    // ── Save ───────────────────────────────────────────────────────────

    suspend fun saveDocument(
        documentId: String,
        config: SaveConfig = SaveConfig()
    ): EngineResult<Unit> {
        val session = _openDocuments[documentId] ?: return EngineResult.Failure(
            com.example.pdf_everything.core.services.EngineError.IO_ERROR,
            "Document not open: $documentId"
        )
        val target = session.source
        val result = engine.save(documentId, target, config)
        if (result is EngineResult.Success) {
            session.lastSavedVersion = session.document.version
            session.lastSavedTimestamp = System.currentTimeMillis()
        }
        return result
    }

    suspend fun saveDocumentAs(
        documentId: String,
        config: SaveConfig = SaveConfig()
    ): EngineResult<Unit> {
        val savePath = platform.pickSaveFile() ?: return EngineResult.Failure(
            com.example.pdf_everything.core.services.EngineError.IO_ERROR,
            "Save location not selected"
        )
        val target = DocumentSource.FilePath(savePath)
        val result = engine.save(documentId, target, config)
        if (result is EngineResult.Success) {
            val session = _openDocuments[documentId]
            if (session != null) {
                session.source = target
                session.lastSavedVersion = session.document.version
                session.lastSavedTimestamp = System.currentTimeMillis()
                addToRecent(target, session.document)
            }
        }
        return result
    }

    suspend fun saveIncremental(
        documentId: String,
        config: SaveConfig = SaveConfig()
    ): EngineResult<Unit> {
        return engine.saveIncremental(documentId, config)
    }

    // ── Close ──────────────────────────────────────────────────────────

    suspend fun closeDocument(documentId: String): EngineResult<Unit> {
        val result = engine.close(documentId)
        if (result is EngineResult.Success) {
            _openDocuments.remove(documentId)
        }
        return result
    }

    // ── Auto-save ──────────────────────────────────────────────────────

    fun setAutoSaveInterval(intervalMs: Long) {
        autoSaveIntervalMs = intervalMs.coerceIn(5000L, 600_000L)
    }

    fun startAutoSave(
        documentId: String,
        scope: CoroutineScope
    ) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (true) {
                delay(autoSaveIntervalMs)
                val session = _openDocuments[documentId]
                if (session != null && session.document.dirtyState.isDirty) {
                    saveDocument(documentId)
                }
            }
        }
    }

    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    // ── Recent files ───────────────────────────────────────────────────

    private fun addToRecent(source: DocumentSource, doc: Document) {
        val name = when (source) {
            is DocumentSource.FilePath -> source.path.substringAfterLast('/')
            is DocumentSource.ContentUri -> source.displayName
            is DocumentSource.ByteArraySource -> source.displayName
        }
        // Remove existing entry for same path, then prepend
        _recentFiles.removeAll { it.name == name && it.source == source }
        _recentFiles.add(0, RecentFile(
            name = name,
            source = source,
            lastOpened = System.currentTimeMillis(),
            pageCount = doc.pages.size
        ))
        // Cap at 20 entries
        if (_recentFiles.size > 20) _recentFiles.removeLast()
    }

    // ── Session data ──────────────────────────────────────────────────

    class DocumentSession(
        val document: Document,
        var source: DocumentSource,
        var lastSavedVersion: Int,
        var lastSavedTimestamp: Long = System.currentTimeMillis()
    )

    data class RecentFile(
        val name: String,
        val source: DocumentSource,
        val lastOpened: Long,
        val pageCount: Int
    )

    companion object {
        const val DEFAULT_AUTO_SAVE_MS = 30_000L  // 30 seconds
    }
}