package com.example.pdf_everything.pdf_engine.api

import com.example.pdf_everything.core.document.*
import kotlinx.serialization.Serializable

@Serializable
data class PdfPageInfo(
    val index: Int,
    val width: Float,
    val height: Float,
    val rotation: Int,
    val text: String,
    val pageId: String = "page-$index",
    val documentVersion: Long = 0L,
    val mediaBox: RectF = RectF(0f, 0f, width, height),
    val cropBox: RectF = mediaBox,
    val bleedBox: RectF = cropBox,
    val trimBox: RectF = cropBox,
    val artBox: RectF = cropBox
)

@Serializable
data class PdfDocumentInfo(
    val name: String,
    val pageCount: Int,
    val pages: List<PdfPageInfo>,
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val encrypted: Boolean = false,
    val passwordRequired: Boolean = false,
    val canPrint: Boolean = true,
    val canCopy: Boolean = true,
    val canModify: Boolean = true,
    val documentVersion: Long = 1L
)

data class RenderViewport(
    val widthPx: Int,
    val heightPx: Int? = null,
    val scale: Float = 1f,
    val cacheKey: String = "",
    val tileX: Int? = null,
    val tileY: Int? = null,
    val tileWidthPx: Int? = null,
    val tileHeightPx: Int? = null
)

data class RenderedPage(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val png: ByteArray,
    val documentVersion: Long,
    val pageVersion: Long,
    val viewportKey: String
)

data class SearchOptions(val caseSensitive: Boolean = false, val wholeWord: Boolean = false)
data class SearchRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class SearchMatch(val pageIndex: Int, val start: Int, val end: Int, val rects: List<SearchRect>)

enum class PdfEngineCapability { READ, RENDER, TEXT, OBJECTS, ANNOTATIONS, FORMS, OUTLINE, MODIFY, WRITE, INCREMENTAL_SAVE, EXPORT, OPTIMIZE, VALIDATE }

data class PdfEngineCapabilities(
    val capabilities: Set<PdfEngineCapability>,
    val notes: Map<PdfEngineCapability, String> = emptyMap()
) {
    fun supports(capability: PdfEngineCapability) = capability in capabilities
}

data class PdfValidationReport(val valid: Boolean, val errors: List<String>, val warnings: List<String>, val pageCount: Int, val fileSizeBytes: Long? = null)
data class PdfSaveReport(val path: String, val bytesWritten: Long, val validation: PdfValidationReport)

data class PdfEditability(val value: PdfObjectEditability, val reason: String = "")

interface PdfTransaction : AutoCloseable {
    fun commit()
    fun rollback()
    val active: Boolean
}

interface PdfEngine {
    fun capabilities(): PdfEngineCapabilities
    fun source(): DocumentSource? = null
    fun open(source: DocumentSource): PdfDocumentInfo
    fun inspect(): PdfDocumentInfo
    fun renderPage(pageIndex: Int, viewport: RenderViewport): RenderedPage
    fun getText(pageIndex: Int): String
    fun searchPage(pageIndex: Int, query: String, options: SearchOptions): List<SearchMatch>

    fun getObjects(pageIndex: Int): List<PdfObject> = emptyList()
    fun getObjectEditability(obj: PdfObject): PdfEditability = PdfEditability(if (obj.editable) PdfObjectEditability.EDITABLE else PdfObjectEditability.UNSUPPORTED)
    fun getAnnotations(pageIndex: Int): List<PdfAnnotation> = emptyList()
    fun getForms(): FormModel = FormModel()
    fun getOutline(): List<OutlineItem> = emptyList()
    fun getAttachments(): List<PdfAttachment> = emptyList()

    fun beginTransaction(label: String = "PDF edit"): PdfTransaction = object : PdfTransaction {
        private var done = false
        override val active get() = !done
        override fun commit() { done = true }
        override fun rollback() { done = true }
        override fun close() { if (!done) rollback() }
    }

    fun modify(pageIndex: Int, obj: PdfObject): Unit = unsupported("modify", "No exact structural mapping exists for this object type")
    fun addObject(pageIndex: Int, obj: PdfObject): Unit = unsupported("addObject", "Object type is not supported by this adapter")
    fun removeObject(pageIndex: Int, objectId: String): Unit = unsupported("removeObject", "Object identity is not safely mappable")
    fun updateObject(pageIndex: Int, obj: PdfObject): Unit = unsupported("updateObject", "Object identity is not safely mappable")
    fun updateMetadata(metadata: DocumentMetadata): Unit = unsupported("updateMetadata", "Adapter does not expose writable metadata")
    fun updateAnnotation(annotation: PdfAnnotation): Unit = unsupported("updateAnnotation", "Annotation type is not supported by this adapter")
    fun deleteAnnotation(pageIndex: Int, annotationId: String): Unit = unsupported("deleteAnnotation", "Annotation identity is not safely mappable")
    fun updateFormField(fieldId: String, value: String, selectedValues: List<String> = emptyList()): Unit = unsupported("updateFormField", "Form field is not writable by this adapter")
    fun updateOutline(outline: List<OutlineItem>): Unit = unsupported("updateOutline", "Outline is not writable by this adapter")
    fun applyDocumentStructure(document: Document): Unit = unsupported("applyDocumentStructure", "The adapter cannot safely map this document model to native PDF structure")

    fun save(path: String): PdfSaveReport = unsupported("save", "Write capability is unavailable")
    fun saveIncremental(path: String): PdfSaveReport = unsupported("saveIncremental", "Incremental save is unavailable")
    fun export(path: String): PdfSaveReport = save(path)
    fun optimize(): PdfValidationReport = validate()
    fun validate(): PdfValidationReport = unsupported("validate", "Validation is unavailable")
    fun optimizeTo(path: String): PdfSaveReport = export(path)
    fun close() {}

    private fun <T> unsupported(name: String, reason: String): T =
        throw UnsupportedOperationException("$name unsupported: $reason")
}

fun PdfEngine.renderPage(pageIndex: Int, width: Int, documentVersion: Long = 0L): RenderedPage =
    renderPage(pageIndex, RenderViewport(widthPx = width, scale = 1f, cacheKey = "legacy-$width-$documentVersion"))
