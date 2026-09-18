package com.example.pdf_everything.core.document

import kotlinx.serialization.Serializable

/**
 * Canonical document geometry. PDF uses a bottom-left origin, while some UI layers use
 * a top-left origin; the model deliberately stores geometry without attaching a UI coordinate system.
 */
@Serializable
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun normalized(): RectF = RectF(
        minOf(left, right),
        minOf(top, bottom),
        maxOf(left, right),
        maxOf(top, bottom)
    )

    fun translate(dx: Float, dy: Float): RectF = copy(
        left = left + dx,
        right = right + dx,
        top = top + dy,
        bottom = bottom + dy
    )

    fun inset(amount: Float): RectF = RectF(
        left + amount, top + amount, right - amount, bottom - amount
    ).normalized()

    fun intersects(other: RectF): Boolean =
        normalized().let { a -> other.normalized().let { b ->
            a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        } }
}

@Serializable
data class Matrix2D(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f
) {
    fun isIdentity(): Boolean = a == 1f && b == 0f && c == 0f && d == 1f && e == 0f && f == 0f
}

/** Persistent dirty-state rather than a UI-only boolean. */
@Serializable
enum class DirtyState {
    CLEAN,
    DIRTY,
    SAVING,
    SAVE_FAILED
}

@Serializable
data class AuditHistoryMetadata(
    val createdAtEpochMs: Long = 0L,
    val lastModifiedAtEpochMs: Long = 0L,
    val lastSavedAtEpochMs: Long = 0L,
    val revision: Long = 0L,
    val lastCommand: String? = null,
    val commandCount: Long = 0L,
    val sourceFingerprint: String? = null,
    val lastSavedFingerprint: String? = null
) {
    fun recordCommand(description: String, timestamp: Long, nextRevision: Long = revision + 1L): AuditHistoryMetadata = copy(
        lastModifiedAtEpochMs = timestamp,
        revision = nextRevision,
        lastCommand = description,
        commandCount = commandCount + 1L
    )

    fun recordSave(timestamp: Long, fingerprint: String?): AuditHistoryMetadata = copy(
        lastSavedAtEpochMs = timestamp,
        lastSavedFingerprint = fingerprint
    )
}

/** Backward-compatible alias for older code paths. */
@Serializable
data class HistoryMetadata(
    val importedAtEpochMs: Long = 0L,
    val audit: AuditHistoryMetadata = AuditHistoryMetadata()
)

@Serializable
sealed class DocumentSource {
    @Serializable
    data class FilePath(val path: String) : DocumentSource()

    @Serializable
    data class ContentUri(val uri: String) : DocumentSource()
}

@Serializable
data class DocumentPermissions(
    val canPrint: Boolean = true,
    val canCopy: Boolean = true,
    val canModify: Boolean = true,
    val canAnnotate: Boolean = true,
    val canFillForms: Boolean = true,
    val canAssemble: Boolean = true,
    val canExtractForAccessibility: Boolean = true,
    val canHighQualityPrint: Boolean = true
)

@Serializable
data class SecurityInfo(
    val encrypted: Boolean = false,
    val passwordRequired: Boolean = false,
    val algorithm: String = "",
    val keyLengthBits: Int? = null,
    val permissionsEnforced: Boolean = false,
    val certificateProtected: Boolean = false
)

@Serializable
data class DocumentMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val creationDateEpochMs: Long? = null,
    val modificationDateEpochMs: Long? = null,
    val trapped: String? = null,
    val custom: Map<String, String> = emptyMap()
)

@Serializable
data class PdfAttachment(
    val attachmentId: String,
    val fileName: String,
    val description: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val relationship: String? = null,
    val sourceResourceRef: String? = null,
    val checksum: String? = null
)

@Serializable
data class Document(
    /** Stable internal document identity; the existing `id` field is retained for source compatibility. */
    val id: String,
    val name: String,
    val source: DocumentSource? = null,
    val title: String = "",
    val metadata: DocumentMetadata = DocumentMetadata(),
    val permissions: DocumentPermissions = DocumentPermissions(),
    val securityInfo: SecurityInfo = SecurityInfo(),
    val pages: List<Page> = emptyList(),
    val attachments: List<PdfAttachment> = emptyList(),
    val outline: List<OutlineItem> = emptyList(),
    val formModel: FormModel = FormModel(),
    val historyMetadata: HistoryMetadata = HistoryMetadata(),
    val version: Long = 0L,
    val dirty: Boolean = false,
    val dirtyState: DirtyState = if (dirty) DirtyState.DIRTY else DirtyState.CLEAN
) {
    val documentId: String get() = id
    val pageCount: Int get() = pages.size
    val sourcePath: String? get() = (source as? DocumentSource.FilePath)?.path
    val sourceUri: String? get() = (source as? DocumentSource.ContentUri)?.uri

    /** Returns pages with a canonical zero-based index; page IDs themselves remain stable. */
    fun normalizedPages(): Document = copy(
        pages = pages.mapIndexed { index, page -> page.copy(index = index) },
        dirty = dirty,
        dirtyState = dirtyState
    )

    fun withDirtyState(state: DirtyState): Document = copy(
        dirtyState = state,
        dirty = state != DirtyState.CLEAN
    )

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (id.isBlank()) errors += "Document ID is blank"
        if (pages.any { it.id.isBlank() }) errors += "A page has a blank page ID"
        val duplicatePageIds = pages.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicatePageIds.isNotEmpty()) errors += "Duplicate page IDs: ${duplicatePageIds.joinToString()}"
        pages.forEachIndexed { index, page -> errors += page.validate(index).map { "page[$index]: $it" } }
        errors += validateOutline(outline, pageCount)
        errors += validateForms(formModel, pageCount)
        return errors
    }
}

@Serializable
data class BoxSet(
    val media: RectF,
    val crop: RectF = media,
    val bleed: RectF = crop,
    val trim: RectF = crop,
    val art: RectF = crop
) {
    fun allValid(): Boolean = listOf(media, crop, bleed, trim, art).none(RectF::isEmpty)
}

@Serializable
data class RenderState(
    val version: Long = 0L,
    val invalidated: Boolean = true,
    val rendering: Boolean = false,
    val lastRenderedScale: Float? = null,
    val lastRenderedViewportKey: String? = null,
    val lastRenderedAtEpochMs: Long? = null,
    val renderGeneration: Long = 0L
) {
    fun invalidate(): RenderState = copy(
        version = version + 1L,
        invalidated = true,
        rendering = false,
        renderGeneration = renderGeneration + 1L
    )
}

@Serializable
data class Page(
    val id: String,
    val index: Int,
    val boxes: BoxSet,
    val rotation: Int = 0,
    val background: String? = null,
    val objects: List<PdfObject> = emptyList(),
    val annotations: List<PdfAnnotation> = emptyList(),
    val widgets: List<FormWidget> = emptyList(),
    val renderState: RenderState = RenderState(),
    val text: String = "",
    val sourcePageIndex: Int? = null,
    val sourceDocumentId: String? = null,
    val label: String? = null
) {
    fun validate(expectedIndex: Int? = null): List<String> {
        val errors = mutableListOf<String>()
        val normalizedRotation = ((rotation % 360) + 360) % 360
        if (normalizedRotation !in setOf(0, 90, 180, 270)) errors += "Rotation must be 0/90/180/270"
        if (!boxes.allValid()) errors += "Page boxes must be non-empty"
        if (expectedIndex != null && index != expectedIndex) errors += "Index is $index but expected $expectedIndex"
        val objectIds = mutableSetOf<String>()
        objects.forEach { obj ->
            if (!objectIds.add(obj.id)) errors += "Duplicate object ID: ${obj.id}"
            errors += obj.validate().map { "object[${obj.id}]: $it" }
        }
        val annotationIds = mutableSetOf<String>()
        annotations.forEach { annotation ->
            if (!annotationIds.add(annotation.id)) errors += "Duplicate annotation ID: ${annotation.id}"
            if (annotation.pageIndex != index) errors += "Annotation ${annotation.id} belongs to page ${annotation.pageIndex}, expected $index"
            if (annotation.bounds.isEmpty) errors += "Annotation ${annotation.id} has an empty bounds"
            if (!annotation.style.opacity.isFinite() || annotation.style.opacity !in 0f..1f) errors += "Annotation ${annotation.id} opacity is invalid"
        }
        val widgetIds = mutableSetOf<String>()
        widgets.forEach { widget ->
            if (!widgetIds.add(widget.id)) errors += "Duplicate widget ID: ${widget.id}"
            if (widget.pageIndex != null && widget.pageIndex != index) errors += "Widget ${widget.id} belongs to page ${widget.pageIndex}, expected $index"
            if (widget.bounds.isEmpty) errors += "Widget ${widget.id} has an empty bounds"
        }
        return errors
    }
}

@Serializable
enum class PdfObjectEditability {
    EDITABLE,
    READ_ONLY,
    UNSUPPORTED
}

@Serializable
data class ObjectSelectionState(
    val selected: Boolean = false,
    val active: Boolean = false,
    val focused: Boolean = false
)

@Serializable
data class PdfResourceReference(
    val resourceId: String,
    val kind: String,
    val name: String? = null,
    val external: Boolean = false
)

@Serializable
sealed class PdfObject {
    abstract val id: String
    abstract val bounds: RectF
    abstract val transform: Matrix2D
    abstract val zOrder: Int
    abstract val opacity: Float
    abstract val rotation: Float
    abstract val resourceRef: String?
    abstract val resourceReferences: List<PdfResourceReference>
    abstract val originalIdentity: String?
    abstract val editability: PdfObjectEditability
    abstract val selection: ObjectSelectionState
    abstract val editable: Boolean

    /** Backward-compatible read-only view of [editability]. */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (id.isBlank()) errors += "Object ID is blank"
        if (bounds.isEmpty) errors += "Bounding box is empty"
        if (!opacity.isFinite() || opacity !in 0f..1f) errors += "Opacity must be between 0 and 1"
        if (!rotation.isFinite()) errors += "Rotation must be finite"
        return errors
    }

    @Serializable
    data class TextObject(
        override val id: String,
        val text: String,
        override val bounds: RectF,
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE,
        val fontName: String? = null,
        val fontSize: Float = 12f,
        val color: String? = null,
        val fontWeight: String = "normal",
        val alignment: String = "left",
        val lineSpacing: Float = 1f,
        val background: String? = null,
        val borderColor: String? = null,
        val encoding: String? = null,
        val glyphIds: List<Int> = emptyList(),
        val characterSpacing: Float = 0f,
        val wordSpacing: Float = 0f
    ) : PdfObject()

    @Serializable
    data class ImageObject(
        override val id: String,
        val source: String,
        override val bounds: RectF,
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE,
        val crop: RectF? = null,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false,
        val mimeType: String? = null,
        val pixelWidth: Int? = null,
        val pixelHeight: Int? = null,
        val colorSpace: String? = null,
        val bitsPerComponent: Int? = null,
        val interpolation: Boolean? = null
    ) : PdfObject()

    @Serializable
    data class VectorObject(
        override val id: String,
        override val bounds: RectF,
        val pathData: String = "",
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE,
        val strokeColor: String? = null,
        val fillColor: String? = null,
        val strokeWidth: Float = 1f,
        val fillRule: String = "nonzero"
    ) : PdfObject()

    @Serializable
    data class PathObject(
        override val id: String,
        override val bounds: RectF,
        val pathData: String = "",
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE,
        val strokeColor: String? = null,
        val fillColor: String? = null,
        val strokeWidth: Float = 1f,
        val closed: Boolean = false
    ) : PdfObject()

    @Serializable
    data class ShapeObject(
        override val id: String,
        override val bounds: RectF,
        val shape: String,
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE,
        val strokeColor: String? = null,
        val fillColor: String? = null,
        val strokeWidth: Float = 1f,
        val cornerRadius: Float = 0f
    ) : PdfObject()

    @Serializable
    data class AnnotationObject(
        override val id: String,
        override val bounds: RectF,
        val subtype: String,
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE
    ) : PdfObject()

    /**
     * Form widgets are first-class page content in the internal model, while [Page.widgets]
     * retains the canonical field descriptors needed by form editing.
     */
    @Serializable
    data class FormWidgetObject(
        override val id: String,
        override val bounds: RectF,
        val fieldId: String,
        val fieldType: String,
        val value: String = "",
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.EDITABLE,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = editability == PdfObjectEditability.EDITABLE
    ) : PdfObject()

    /**
     * Unknown/native object. The source PDF remains authoritative for this object so a save
     * can preserve it even though PDF Everything cannot structurally modify it.
     */
    @Serializable
    data class UnknownObject(
        override val id: String,
        override val bounds: RectF,
        val description: String = "Unsupported PDF object",
        val nativeType: String? = null,
        val preservationKey: String? = null,
        val rawObjectReference: String? = null,
        override val transform: Matrix2D = Matrix2D(),
        override val zOrder: Int = 0,
        override val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val resourceRef: String? = null,
        override val resourceReferences: List<PdfResourceReference> = emptyList(),
        override val originalIdentity: String? = id,
        override val editability: PdfObjectEditability = PdfObjectEditability.UNSUPPORTED,
        override val selection: ObjectSelectionState = ObjectSelectionState(),
        override val editable: Boolean = false
    ) : PdfObject()
}

@Serializable
data class FormWidget(
    val id: String,
    val fieldType: String,
    val bounds: RectF,
    val value: String = "",
    val fieldId: String = id,
    val pageIndex: Int? = null,
    val appearanceState: String? = null,
    val readOnly: Boolean = false,
    val required: Boolean = false
)

@Serializable
enum class AnnotationType { Highlight, Underline, StrikeOut, Squiggly, Note, FreeText, Ink, Rectangle, Circle, Line, Stamp, Redaction }

@Serializable
data class AnnotationColor(
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 0f,
    val alpha: Float = 1f
) {
    fun clamped(): AnnotationColor = AnnotationColor(
        red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f)
    )
}

@Serializable
data class AnnotationStyle(
    val color: AnnotationColor = AnnotationColor(),
    val opacity: Float = 1f,
    val strokeWidth: Float = 2f,
    val fill: Boolean = false,
    val fillColor: AnnotationColor? = null,
    val fontSize: Float = 12f,
    val lineEndings: String? = null,
    val blendMode: String? = null
)

@Serializable
data class AnnotationComment(
    val author: String = "",
    val contents: String = "",
    val subject: String = "",
    val createdAtEpochMs: Long = 0L,
    val modifiedAtEpochMs: Long = 0L,
    val replies: List<AnnotationComment> = emptyList()
)

@Serializable
data class PdfAnnotation(
    val id: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val bounds: RectF,
    val style: AnnotationStyle = AnnotationStyle(),
    val comment: AnnotationComment? = null,
    val vertices: List<OffsetPoint> = emptyList(),
    val contents: String = "",
    val author: String = "",
    val subject: String = "",
    val flags: Int = 0,
    val printable: Boolean = true,
    val locked: Boolean = false,
    val replyToId: String? = null,
    val originalIdentity: String? = id,
    val nativeSubtype: String? = null
)

@Serializable
data class OffsetPoint(val x: Float, val y: Float)

@Serializable
enum class FormFieldType { Text, MultilineText, Checkbox, Radio, Choice, ComboBox, PushButton, Signature, Unknown }

@Serializable
data class FormOption(val exportValue: String, val displayValue: String = exportValue, val selected: Boolean = false)

@Serializable
data class FormField(
    val id: String,
    val name: String,
    val alternateName: String? = null,
    val type: FormFieldType = FormFieldType.Unknown,
    val pageIndex: Int? = null,
    val bounds: RectF? = null,
    val value: String = "",
    val selectedValues: List<String> = emptyList(),
    val options: List<FormOption> = emptyList(),
    val required: Boolean = false,
    val readOnly: Boolean = false,
    val exportValue: String? = null,
    val fullyQualifiedName: String = name,
    val tooltip: String? = null,
    val defaultValue: String? = null,
    val mappingName: String? = null,
    val maxLength: Int? = null,
    val multiSelect: Boolean = false,
    val editable: Boolean = !readOnly,
    val widgetIds: List<String> = emptyList()
)

@Serializable
data class FormModel(
    val fields: List<FormField> = emptyList(),
    val calculationOrder: List<String> = emptyList(),
    val needAppearances: Boolean = false,
    val signatureFieldIds: List<String> = emptyList(),
    val globalSubmitUrl: String? = null
)

@Serializable
data class BookmarkStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: AnnotationColor? = null
)

@Serializable
data class BookmarkDestination(
    val pageIndex: Int? = null,
    val top: Float? = null,
    val left: Float? = null,
    val zoom: Float? = null,
    val namedDestination: String? = null,
    val fitMode: String = "XYZ"
)

@Serializable
data class OutlineItem(
    val id: String = "outline-${kotlin.random.Random.nextLong().toString(16)}",
    val title: String,
    val pageIndex: Int? = null,
    val children: List<OutlineItem> = emptyList(),
    val destination: BookmarkDestination = BookmarkDestination(pageIndex = pageIndex),
    val style: BookmarkStyle = BookmarkStyle(),
    val open: Boolean = true,
    val originalIdentity: String? = id
)

private fun validateOutline(items: List<OutlineItem>, pageCount: Int, path: String = "outline", ids: MutableSet<String> = mutableSetOf()): List<String> {
    val errors = mutableListOf<String>()
    items.forEachIndexed { index, item ->
        val currentPath = "$path[$index]"
        if (item.id.isBlank()) errors += "$currentPath has blank ID"
        if (!ids.add(item.id)) errors += "$currentPath has duplicate ID ${item.id}"
        if (item.title.isBlank()) errors += "$currentPath has blank title"
        if (item.pageIndex != item.destination.pageIndex) errors += "$currentPath pageIndex and destination.pageIndex differ"
        item.destination.pageIndex?.let { page ->
            if (page !in 0 until pageCount) errors += "$currentPath points to page $page outside 0..${pageCount - 1}"
        }
        errors += validateOutline(item.children, pageCount, "$currentPath.children")
    }
    return errors
}

private fun validateForms(model: FormModel, pageCount: Int): List<String> {
    val errors = mutableListOf<String>()
    val ids = mutableSetOf<String>()
    model.fields.forEach { field ->
        if (!ids.add(field.id)) errors += "Duplicate form field ID: ${field.id}"
        field.pageIndex?.let { page -> if (page !in 0 until pageCount) errors += "Form field ${field.id} points to invalid page $page" }
        if (field.readOnly && field.editable) errors += "Form field ${field.id} is read-only but marked editable"
    }
    return errors
}
