package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.*
import com.example.pdf_everything.core.commands.DocumentCommand

/* ───────────────────────────────────────────────────────────────────────
 *  PDF Engine API — the single abstraction layer between the app and any
 *  underlying PDF library (Apache PDFBox, iText, PdfiumAndroid, etc.).
 *
 *  Per spec §21‑§27, the engine must expose: open, inspect, render, extract
 *  text/objects/annotations/forms, modify (add/remove/update objects), save,
 *  saveIncremental, export, optimize, validate.
 * ─────────────────────────────────────────────────────────────────────── */

// ── Result wrapper ──────────────────────────────────────────────────────

sealed class EngineResult<out T> {
    data class Success<T>(val value: T) : EngineResult<T>()
    data class Failure(
        val error: EngineError,
        val message: String,
        val cause: Throwable? = null
    ) : EngineResult<Nothing>()
}

enum class EngineError {
    FILE_NOT_FOUND,
    INVALID_PASSWORD,
    CORRUPT_FILE,
    UNSUPPORTED_FEATURE,
    IO_ERROR,
    OUT_OF_MEMORY,
    PERMISSION_DENIED,
    PAGE_NOT_FOUND,
    OBJECT_NOT_FOUND,
    ENGINE_NOT_INITIALIZED,
    SAVE_FAILED,
    EXPORT_FAILED,
    RENDER_FAILED,
    UNKNOWN
}

class EngineException(
    val error: EngineError,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

// ── Render parameters ──────────────────────────────────────────────────

data class RenderConfig(
    val dpi: Float = 150f,
    val scale: Float = 1f,
    val rotation: PageRotation = PageRotation.ROTATION_0,
    val renderAnnotations: Boolean = true,
    val renderForms: Boolean = true,
    val clipRect: PdfRect? = null,        // null = whole page
    val grayscale: Boolean = false,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null
)

data class RenderedPage(
    val pageId: String,
    val width: Int,
    val height: Int,
    val bitmapBytes: ByteArray,           // raw ARGB_8888 bytes (platform converts)
    val dpi: Float,
    val rotation: PageRotation
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

// ── Save / Export parameters ────────────────────────────────────────────

data class SaveConfig(
    val incremental: Boolean = false,
    val flattenForms: Boolean = false,
    val removeMetadata: Boolean = false,
    val optimize: Boolean = false,
    val compressImages: Boolean = true,
    val imageQuality: Int = 85             // 0‑100
)

data class ExportConfig(
    val format: ExportFormat,
    val pages: List<Int>? = null,          // null = all pages
    val dpi: Float = 150f,
    val imageQuality: Int = 85
)

enum class ExportFormat {
    PDF, PNG, JPEG, TIFF, TEXT, HTML, SVG
}

// ── Validation ──────────────────────────────────────────────────────────

data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val pageId: String? = null,
    val objectId: String? = null
)

enum class ValidationSeverity { INFO, WARNING, ERROR, CRITICAL }

// ── Engine capabilities ────────────────────────────────────────────────

data class EngineCapabilities(
    val canEditText: Boolean = false,
    val canEditImages: Boolean = false,
    val canEditVector: Boolean = false,
    val canEditForms: Boolean = false,
    val canEditAnnotations: Boolean = false,
    val canEditPaths: Boolean = false,
    val canReorderPages: Boolean = false,
    val canCropPages: Boolean = false,
    val canRotatePages: Boolean = false,
    val canDeletePages: Boolean = false,
    val canInsertPages: Boolean = false,
    val canMergeDocuments: Boolean = false,
    val canSplitDocument: Boolean = false,
    val canFlattenForms: Boolean = false,
    val canIncrementalSave: Boolean = false,
    val canLinearize: Boolean = false,
    val canRedact: Boolean = false,
    val canDigitallySign: Boolean = false,
    val supportedExportFormats: Set<ExportFormat> = emptySet()
)

// ── Engine lifecycle events ────────────────────────────────────────────

enum class EngineState {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    BUSY,
    ERROR,
    SHUTTING_DOWN,
    SHUT_DOWN
}

// ── PdfEngine interface ────────────────────────────────────────────────

interface PdfEngine {

    // ── Lifecycle ──────────────────────────────────────────────────────

    val state: EngineState
    val capabilities: EngineCapabilities

    suspend fun initialize()
    suspend fun shutdown()

    // ── Document I/O ───────────────────────────────────────────────────

    suspend fun open(source: DocumentSource, password: String? = null): EngineResult<Document>
    suspend fun close(documentId: String): EngineResult<Unit>

    suspend fun save(
        documentId: String,
        target: DocumentSource,
        config: SaveConfig = SaveConfig()
    ): EngineResult<Unit>

    suspend fun saveIncremental(
        documentId: String,
        config: SaveConfig = SaveConfig()
    ): EngineResult<Unit>

    suspend fun export(
        documentId: String,
        config: ExportConfig
    ): EngineResult<List<ByteArray>>

    // ── Inspection ─────────────────────────────────────────────────────

    suspend fun getMetadata(documentId: String): EngineResult<DocumentMetadata>
    suspend fun getPermissions(documentId: String): EngineResult<DocumentPermissions>
    suspend fun getSecurityInfo(documentId: String): EngineResult<DocumentSecurityInfo>
    suspend fun getOutline(documentId: String): EngineResult<DocumentOutline>
    suspend fun getPageCount(documentId: String): EngineResult<Int>

    // ── Page access ─────────────────────────────────────────────────────

    suspend fun getPage(
        documentId: String,
        pageIndex: Int
    ): EngineResult<Page>

    suspend fun getPageBoxes(
        documentId: String,
        pageIndex: Int
    ): EngineResult<PageBoxes>

    suspend fun getPageRotation(
        documentId: String,
        pageIndex: Int
    ): EngineResult<PageRotation>

    // ── Rendering ──────────────────────────────────────────────────────

    suspend fun renderPage(
        documentId: String,
        pageIndex: Int,
        config: RenderConfig = RenderConfig()
    ): EngineResult<RenderedPage>

    // ── Text extraction ────────────────────────────────────────────────

    suspend fun getText(
        documentId: String,
        pageIndex: Int,
        rect: PdfRect? = null
    ): EngineResult<String>

    // ── Object extraction ──────────────────────────────────────────────

    suspend fun getObjects(
        documentId: String,
        pageIndex: Int,
        rect: PdfRect? = null
    ): EngineResult<List<PdfObject>>

    // ── Annotations ────────────────────────────────────────────────────

    suspend fun getAnnotations(
        documentId: String,
        pageIndex: Int
    ): EngineResult<List<AnnotationObject>>

    // ── Forms ──────────────────────────────────────────────────────────

    suspend fun getFormFields(
        documentId: String
    ): EngineResult<List<FormField>>

    suspend fun setFormFieldValue(
        documentId: String,
        fieldId: String,
        value: String
    ): EngineResult<Unit>

    // ── Modification commands ──────────────────────────────────────────

    suspend fun executeCommand(
        documentId: String,
        command: DocumentCommand
    ): EngineResult<Unit>

    // ── Object-level CRUD ──────────────────────────────────────────────

    suspend fun addObject(
        documentId: String,
        pageIndex: Int,
        obj: PdfObject
    ): EngineResult<PdfObject>

    suspend fun removeObject(
        documentId: String,
        pageIndex: Int,
        objectId: String
    ): EngineResult<Unit>

    suspend fun updateObject(
        documentId: String,
        pageIndex: Int,
        objectId: String,
        updated: PdfObject
    ): EngineResult<PdfObject>

    // ── Validation ─────────────────────────────────────────────────────

    suspend fun validate(
        documentId: String
    ): EngineResult<List<ValidationIssue>>

    // ── Optimization ───────────────────────────────────────────────────

    suspend fun optimize(
        documentId: String,
        compressImages: Boolean = true,
        imageQuality: Int = 85,
        removeUnusedObjects: Boolean = true,
        flattenForms: Boolean = false
    ): EngineResult<Unit>

    // ── Split / Merge ───────────────────────────────────────────────────

    /** Split pages [fromIndex..toIndex] into a new Document. */
    suspend fun splitDocument(
        documentId: String,
        fromIndex: Int,
        toIndex: Int
    ): EngineResult<Document>

    /** Merge source document pages into the target document. */
    suspend fun mergeDocuments(
        targetDocumentId: String,
        sourceDocumentId: String
    ): EngineResult<Unit>
}