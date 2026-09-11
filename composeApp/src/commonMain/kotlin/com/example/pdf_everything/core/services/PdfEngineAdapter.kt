package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.*
import com.example.pdf_everything.core.commands.DocumentCommand

/**
 * Abstract adapter that bridges PdfEngine to concrete back-ends
 * (Apache PDFBox on Desktop, PdfiumAndroid on Android, etc.).
 *
 * Subclass and implement each `do*` method for the target library.
 * The adapter adds capability reporting, error wrapping, and
 * state-machine enforcement so callers never see raw library exceptions.
 */
abstract class PdfEngineAdapter : PdfEngine {

    private var _state: EngineState = EngineState.NOT_INITIALIZED
    override val state: EngineState get() = _state

    private var _capabilities: EngineCapabilities = EngineCapabilities()
    override val capabilities: EngineCapabilities get() = _capabilities

    // Active documents held by this adapter
    private val openDocuments = mutableMapOf<String, Document>()

    // ── Lifecycle ────────────────────────────────────────────────────────

    override suspend fun initialize() {
        transition(EngineState.NOT_INITIALIZED, EngineState.INITIALIZING)
        try {
            doInitialize()
            _capabilities = detectCapabilities()
            transition(EngineState.INITIALIZING, EngineState.READY)
        } catch (e: Exception) {
            _state = EngineState.ERROR
            throw EngineException(EngineError.ENGINE_NOT_INITIALIZED, "Init failed", e)
        }
    }

    override suspend fun shutdown() {
        transition(EngineState.READY, EngineState.SHUTTING_DOWN)
        try {
            // Close every open document
            for (docId in openDocuments.keys.toList()) {
                runCatching { close(docId) }
            }
            doShutdown()
            transition(EngineState.SHUTTING_DOWN, EngineState.SHUT_DOWN)
        } catch (e: Exception) {
            _state = EngineState.ERROR
            throw EngineException(EngineError.UNKNOWN, "Shutdown failed", e)
        }
    }

    // ── Document I/O ────────────────────────────────────────────────────

    override suspend fun open(source: DocumentSource, password: String?): EngineResult<Document> {
        requireReady()
        return try {
            val doc = doOpen(source, password)
            openDocuments[doc.documentId] = doc
            EngineResult.Success(doc)
        } catch (e: EngineException) {
            EngineResult.Failure(e.error, e.message ?: "Open failed", e.cause)
        } catch (e: Exception) {
            EngineResult.Failure(EngineError.IO_ERROR, "Open failed: ${e.message}", e)
        }
    }

    override suspend fun close(documentId: String): EngineResult<Unit> {
        return try {
            doClose(documentId)
            openDocuments.remove(documentId)
            EngineResult.Success(Unit)
        } catch (e: Exception) {
            EngineResult.Failure(EngineError.IO_ERROR, "Close failed: ${e.message}", e)
        }
    }

    override suspend fun save(documentId: String, target: DocumentSource, config: SaveConfig): EngineResult<Unit> {
        requireReady()
        requireCapability { it.canEditText || it.canEditImages || it.canEditAnnotations }
        return wrap { doSave(documentId, target, config) }
    }

    override suspend fun saveIncremental(documentId: String, config: SaveConfig): EngineResult<Unit> {
        requireReady()
        requireCapability { it.canIncrementalSave }
        return wrap { doSaveIncremental(documentId, config) }
    }

    override suspend fun export(documentId: String, config: ExportConfig): EngineResult<List<ByteArray>> {
        requireReady()
        requireCapability { config.format in it.supportedExportFormats }
        return wrap { doExport(documentId, config) }
    }

    // ── Inspection ──────────────────────────────────────────────────────

    override suspend fun getMetadata(documentId: String) =
        wrap { doGetMetadata(documentId) }

    override suspend fun getPermissions(documentId: String) =
        wrap { doGetPermissions(documentId) }

    override suspend fun getSecurityInfo(documentId: String) =
        wrap { doGetSecurityInfo(documentId) }

    override suspend fun getOutline(documentId: String) =
        wrap { doGetOutline(documentId) }

    override suspend fun getPageCount(documentId: String) =
        wrap { doGetPageCount(documentId) }

    // ── Page access ─────────────────────────────────────────────────────

    override suspend fun getPage(documentId: String, pageIndex: Int) =
        wrap { doGetPage(documentId, pageIndex) }

    override suspend fun getPageBoxes(documentId: String, pageIndex: Int) =
        wrap { doGetPageBoxes(documentId, pageIndex) }

    override suspend fun getPageRotation(documentId: String, pageIndex: Int) =
        wrap { doGetPageRotation(documentId, pageIndex) }

    // ── Rendering ──────────────────────────────────────────────────────

    override suspend fun renderPage(documentId: String, pageIndex: Int, config: RenderConfig) =
        wrap { doRenderPage(documentId, pageIndex, config) }

    // ── Text extraction ────────────────────────────────────────────────

    override suspend fun getText(documentId: String, pageIndex: Int, rect: PdfRect?) =
        wrap { doGetText(documentId, pageIndex, rect) }

    // ── Object extraction ──────────────────────────────────────────────

    override suspend fun getObjects(documentId: String, pageIndex: Int, rect: PdfRect?) =
        wrap { doGetObjects(documentId, pageIndex, rect) }

    // ── Annotations ────────────────────────────────────────────────────

    override suspend fun getAnnotations(documentId: String, pageIndex: Int) =
        wrap { doGetAnnotations(documentId, pageIndex) }

    // ── Forms ──────────────────────────────────────────────────────────

    override suspend fun getFormFields(documentId: String) =
        wrap { doGetFormFields(documentId) }

    override suspend fun setFormFieldValue(documentId: String, fieldId: String, value: String) =
        wrap { doSetFormFieldValue(documentId, fieldId, value) }

    // ── Modification commands ──────────────────────────────────────────

    override suspend fun executeCommand(documentId: String, command: DocumentCommand) =
        wrap { doExecuteCommand(documentId, command) }

    // ── Object-level CRUD ─────────────────────────────────────────────

    override suspend fun addObject(documentId: String, pageIndex: Int, obj: PdfObject) =
        wrap { doAddObject(documentId, pageIndex, obj) }

    override suspend fun removeObject(documentId: String, pageIndex: Int, objectId: String) =
        wrap { doRemoveObject(documentId, pageIndex, objectId) }

    override suspend fun updateObject(documentId: String, pageIndex: Int, objectId: String, updated: PdfObject) =
        wrap { doUpdateObject(documentId, pageIndex, objectId, updated) }

    // ── Validation ─────────────────────────────────────────────────────

    override suspend fun validate(documentId: String) =
        wrap { doValidate(documentId) }

    // ── Optimization ───────────────────────────────────────────────────

    override suspend fun optimize(
        documentId: String,
        compressImages: Boolean,
        imageQuality: Int,
        removeUnusedObjects: Boolean,
        flattenForms: Boolean
    ) = wrap { doOptimize(documentId, compressImages, imageQuality, removeUnusedObjects, flattenForms) }

    // ══════════════════════════════════════════════════════════════════
    //  Abstract hooks — subclass must implement for concrete library
    // ══════════════════════════════════════════════════════════════════

    protected abstract suspend fun doInitialize()
    protected abstract suspend fun doShutdown()
    protected abstract suspend fun detectCapabilities(): EngineCapabilities

    protected abstract suspend fun doOpen(source: DocumentSource, password: String?): Document
    protected abstract suspend fun doClose(documentId: String)
    protected abstract suspend fun doSave(documentId: String, target: DocumentSource, config: SaveConfig)
    protected abstract suspend fun doSaveIncremental(documentId: String, config: SaveConfig)
    protected abstract suspend fun doExport(documentId: String, config: ExportConfig): List<ByteArray>

    protected abstract suspend fun doGetMetadata(documentId: String): DocumentMetadata
    protected abstract suspend fun doGetPermissions(documentId: String): DocumentPermissions
    protected abstract suspend fun doGetSecurityInfo(documentId: String): DocumentSecurityInfo
    protected abstract suspend fun doGetOutline(documentId: String): DocumentOutline
    protected abstract suspend fun doGetPageCount(documentId: String): Int

    protected abstract suspend fun doGetPage(documentId: String, pageIndex: Int): Page
    protected abstract suspend fun doGetPageBoxes(documentId: String, pageIndex: Int): PageBoxes
    protected abstract suspend fun doGetPageRotation(documentId: String, pageIndex: Int): PageRotation

    protected abstract suspend fun doRenderPage(documentId: String, pageIndex: Int, config: RenderConfig): RenderedPage
    protected abstract suspend fun doGetText(documentId: String, pageIndex: Int, rect: PdfRect?): String
    protected abstract suspend fun doGetObjects(documentId: String, pageIndex: Int, rect: PdfRect?): List<PdfObject>

    protected abstract suspend fun doGetAnnotations(documentId: String, pageIndex: Int): List<AnnotationObject>
    protected abstract suspend fun doGetFormFields(documentId: String): List<FormField>
    protected abstract suspend fun doSetFormFieldValue(documentId: String, fieldId: String, value: String)

    protected abstract suspend fun doExecuteCommand(documentId: String, command: DocumentCommand)
    protected abstract suspend fun doAddObject(documentId: String, pageIndex: Int, obj: PdfObject): PdfObject
    protected abstract suspend fun doRemoveObject(documentId: String, pageIndex: Int, objectId: String)
    protected abstract suspend fun doUpdateObject(documentId: String, pageIndex: Int, objectId: String, updated: PdfObject): PdfObject

    protected abstract suspend fun doValidate(documentId: String): List<ValidationIssue>

    protected abstract suspend fun doOptimize(
        documentId: String,
        compressImages: Boolean,
        imageQuality: Int,
        removeUnusedObjects: Boolean,
        flattenForms: Boolean
    )

    // ── Internal helpers ───────────────────────────────────────────────

    private fun transition(from: EngineState, to: EngineState) {
        check(_state == from) { "Expected $from but was $_state" }
        _state = to
    }

    private fun requireReady() {
        check(_state == EngineState.READY) { "Engine not ready (state=$_state)" }
    }

    private fun requireCapability(predicate: (EngineCapabilities) -> Boolean) {
        check(predicate(_capabilities)) { "Engine lacks required capability" }
    }

    protected inline fun <T> wrap(block: () -> T): EngineResult<T> {
        return try {
            EngineResult.Success(block())
        } catch (e: EngineException) {
            EngineResult.Failure(e.error, e.message ?: "Engine error", e.cause)
        } catch (e: NoSuchElementException) {
            EngineResult.Failure(EngineError.PAGE_NOT_FOUND, e.message ?: "Not found", e)
        } catch (e: IllegalArgumentException) {
            EngineResult.Failure(EngineError.UNSUPPORTED_FEATURE, e.message ?: "Unsupported", e)
        } catch (e: OutOfMemoryError) {
            EngineResult.Failure(EngineError.OUT_OF_MEMORY, "Out of memory", null)
        } catch (e: Exception) {
            EngineResult.Failure(EngineError.UNKNOWN, e.message ?: "Unknown error", e)
        }
    }
}