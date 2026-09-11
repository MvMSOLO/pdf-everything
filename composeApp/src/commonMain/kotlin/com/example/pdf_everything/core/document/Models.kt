package com.example.pdf_everything.core.document

import kotlin.math.PI

// ─────────────────────────────────────────────────────────────
//  Coordinate & geometry primitives
// ─────────────────────────────────────────────────────────────

/** Immutable 2-D point in PDF user-space coordinates (origin bottom-left). */
data class PdfPoint(val x: Float, val y: Float)

/** Immutable axis-aligned rectangle in PDF user-space coordinates. */
data class PdfRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val left get() = x
    val top get() = y
    val right get() = x + width
    val bottom get() = y + height
    val centerX get() = x + width / 2f
    val centerY get() = y + height / 2f
    val area get() = width * height
    val isEmpty get() = width <= 0f || height <= 0f

    fun contains(px: Float, py: Float): Boolean =
        px in left..right && py in top..bottom

    fun intersects(other: PdfRect): Boolean =
        !(other.right <= left || other.left >= right || other.bottom <= top || other.top >= bottom)

    fun union(other: PdfRect): PdfRect = PdfRect(
        x = minOf(left, other.left),
        y = minOf(top, other.top),
        width = maxOf(right, other.right) - minOf(left, other.left),
        height = maxOf(bottom, other.bottom) - minOf(top, other.top)
    )

    fun inset(dx: Float, dy: Float): PdfRect =
        PdfRect(x + dx, y + dy, width - 2 * dx, height - 2 * dy)

    fun offset(dx: Float, dy: Float): PdfRect =
        PdfRect(x + dx, y + dy, width, height)

    companion object {
        val ZERO = PdfRect(0f, 0f, 0f, 0f)
    }
}

/** 2-D affine transformation matrix (a b c d e f per PDF spec). */
data class PdfMatrix(
    val a: Float = 1f, val b: Float = 0f,
    val c: Float = 0f, val d: Float = 1f,
    val e: Float = 0f, val f: Float = 0f
) {
    fun multiply(m: PdfMatrix): PdfMatrix = PdfMatrix(
        a = a * m.a + b * m.c,       b = a * m.b + b * m.d,
        c = c * m.a + d * m.c,       d = c * m.b + d * m.d,
        e = e * m.a + f * m.c + m.e, f = e * m.b + f * m.d + m.f
    )

    fun transform(p: PdfPoint): PdfPoint = PdfPoint(
        x = a * p.x + c * p.y + e,
        y = b * p.x + d * p.y + f
    )

    fun transform(r: PdfRect): PdfRect {
        val tl = transform(PdfPoint(r.x, r.y))
        val br = transform(PdfPoint(r.x + r.width, r.y + r.height))
        return PdfRect(
            x = minOf(tl.x, br.x),
            y = minOf(tl.y, br.y),
            width = kotlin.math.abs(br.x - tl.x),
            height = kotlin.math.abs(br.y - tl.y)
        )
    }

    val determinant get() = a * d - b * c
    val isIdentity get() = this == IDENTITY

    fun inverse(): PdfMatrix {
        val det = determinant
        require(det != 0f) { "Matrix is not invertible" }
        val invDet = 1f / det
        return PdfMatrix(
            a = d * invDet,            b = -b * invDet,
            c = -c * invDet,           d = a * invDet,
            e = (c * f - d * e) * invDet, f = (b * e - a * f) * invDet
        )
    }

    companion object {
        val IDENTITY = PdfMatrix()
        fun translation(tx: Float, ty: Float) = PdfMatrix(e = tx, f = ty)
        fun scaling(sx: Float, sy: Float) = PdfMatrix(a = sx, d = sy)
        fun rotation(degrees: Float): PdfMatrix {
            val rad = degrees * PI.toFloat() / 180f
            val cos = kotlin.math.cos(rad)
            val sin = kotlin.math.sin(rad)
            return PdfMatrix(a = cos, b = sin, c = -sin, d = cos)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Document security & permissions
// ─────────────────────────────────────────────────────────────

enum class PdfPermission(val bit: Int) {
    PRINT(4),
    MODIFY(5),
    COPY(6),
    ADD_ANNOTATIONS(7),
    FILL_FORMS(9),
    EXTRACT(10),
    ASSEMBLE(11),
    PRINT_HIGH_RESOLUTION(12)
}

data class DocumentPermissions(
    val isEncrypted: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val permissions: Set<PdfPermission> = emptySet(),
    val encryptionMethod: String? = null,
    val keyLength: Int = 0
) {
    fun hasPermission(p: PdfPermission): Boolean = p in permissions
    val canPrint get() = hasPermission(PdfPermission.PRINT)
    val canModify get() = hasPermission(PdfPermission.MODIFY)
    val canCopy get() = hasPermission(PdfPermission.COPY)
    val canAnnotate get() = hasPermission(PdfPermission.ADD_ANNOTATIONS)
}

data class DocumentSecurityInfo(
    val permissions: DocumentPermissions = DocumentPermissions(),
    val isReadOnly: Boolean = false
)

// ─────────────────────────────────────────────────────────────
//  Metadata
// ─────────────────────────────────────────────────────────────

data class DocumentMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: String? = null,
    val modificationDate: String? = null,
    val pdfVersion: String? = null,
    val customProperties: Map<String, String> = emptyMap()
)

// ─────────────────────────────────────────────────────────────
//  Document outline / bookmarks
// ─────────────────────────────────────────────────────────────

data class OutlineEntry(
    val title: String,
    val destinationPageIndex: Int,
    val children: List<OutlineEntry> = emptyList()
)

data class DocumentOutline(
    val entries: List<OutlineEntry> = emptyList()
)

// ─────────────────────────────────────────────────────────────
//  Page boxes (per PDF spec)
// ─────────────────────────────────────────────────────────────

enum class PageRotation(val degrees: Int) {
    ROTATION_0(0),
    ROTATION_90(90),
    ROTATION_180(180),
    ROTATION_270(270);

    companion object {
        fun fromDegrees(d: Int): PageRotation = when (d % 360) {
            0 -> ROTATION_0
            90 -> ROTATION_90
            180 -> ROTATION_180
            270 -> ROTATION_270
            else -> ROTATION_0
        }
    }
}

data class PageBoxes(
    val mediaBox: PdfRect = PdfRect(0f, 0f, 612f, 792f),   // US Letter default
    val cropBox: PdfRect? = null,
    val bleedBox: PdfRect? = null,
    val trimBox: PdfRect? = null,
    val artBox: PdfRect? = null
) {
    /** Effective visible area: cropBox if set, else mediaBox. */
    val effectiveBox: PdfRect get() = cropBox ?: mediaBox
    val width get() = effectiveBox.width
    val height get() = effectiveBox.height
}

// ─────────────────────────────────────────────────────────────
//  PDF objects on a page
// ─────────────────────────────────────────────────────────────

/** Editability state for any PDF object. */
enum class Editability {
    FULLY_EDITABLE,
    PARTIALLY_EDITABLE,
    READ_ONLY,
    UNKNOWN
}

/** Selection state for any PDF object. */
enum class SelectionState {
    NOT_SELECTED,
    SELECTED,
    ACTIVE_EDITING
}

/** Base for every object that lives on a PDF page. */
sealed class PdfObject {
    abstract val objectId: String
    abstract val boundingBox: PdfRect
    abstract val transform: PdfMatrix
    abstract val zOrder: Int
    abstract val opacity: Float
    abstract val rotation: Float
    abstract val editability: Editability
    abstract val selectionState: SelectionState
    abstract val resourceRef: String?

    abstract fun withBoundingBox(bb: PdfRect): PdfObject
    abstract fun withTransform(m: PdfMatrix): PdfObject
    abstract fun withSelection(s: SelectionState): PdfObject
    abstract fun withZOrder(z: Int): PdfObject
}

data class TextObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.FULLY_EDITABLE,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val text: String,
    val fontFamily: String?,
    val fontSize: Float,
    val fontWeight: String?,
    val fontColor: Long,          // ARGB packed
    val alignment: TextAlignment,
    val lineHeight: Float,
    val isEmbeddedFont: Boolean,
    val originalEncoding: String?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

data class ImageObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.FULLY_EDITABLE,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val imageWidth: Int,
    val imageHeight: Int,
    val bitsPerComponent: Int,
    val colorSpace: String,
    val isJpeg: Boolean,
    val streamId: String?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

data class VectorObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.READ_ONLY,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val pathData: String?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

data class PathObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.READ_ONLY,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val strokeColor: Long?,
    val fillColor: Long?,
    val lineWidth: Float,
    val lineCap: Int,
    val lineJoin: Int,
    val dashArray: List<Float>?,
    val dashPhase: Float
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

data class ShapeObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.PARTIALLY_EDITABLE,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val shapeType: ShapeType,
    val strokeColor: Long?,
    val fillColor: Long?,
    val lineWidth: Float
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

enum class ShapeType { RECTANGLE, ELLIPSE, LINE, ARROW, POLYGON }

data class AnnotationObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.FULLY_EDITABLE,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val annotationType: AnnotationType,
    val color: Long,
    val author: String?,
    val contents: String?,
    val timestamp: String?,
    val isPopup: Boolean,
    val replyTo: String?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

enum class AnnotationType {
    HIGHLIGHT, UNDERLINE, STRIKEOUT, FREEHAND, RECTANGLE, ELLIPSE, LINE,
    ARROW, TEXT_NOTE, STICKY_NOTE, STAMP, LINK, SOUND, ATTACHMENT
}

data class FormWidgetObject(
    override val objectId: String,
    override val boundingBox: PdfRect,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.FULLY_EDITABLE,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val widgetType: FormWidgetType,
    val fieldName: String,
    val fieldValue: String?,
    val isReadOnly: Boolean,
    val isRequired: Boolean,
    val alternateName: String?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

enum class FormWidgetType {
    TEXT_FIELD, CHECKBOX, RADIO_BUTTON, COMBO_BOX, LIST_BOX, BUTTON, SIGNATURE
}

data class UnknownObject(
    override val objectId: String,
    override val boundingBox: PdfRect = PdfRect.ZERO,
    override val transform: PdfMatrix = PdfMatrix.IDENTITY,
    override val zOrder: Int = 0,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val editability: Editability = Editability.UNKNOWN,
    override val selectionState: SelectionState = SelectionState.NOT_SELECTED,
    override val resourceRef: String? = null,
    val rawType: String?,
    val rawDictId: Int?
) : PdfObject() {
    override fun withBoundingBox(bb: PdfRect) = copy(boundingBox = bb)
    override fun withTransform(m: PdfMatrix) = copy(transform = m)
    override fun withSelection(s: SelectionState) = copy(selectionState = s)
    override fun withZOrder(z: Int) = copy(zOrder = z)
}

// ─────────────────────────────────────────────────────────────
//  Page render state
// ─────────────────────────────────────────────────────────────

enum class RenderState {
    NOT_RENDERED,
    RENDERING,
    RENDERED,
    DIRTY,
    ERROR
}

data class PageRenderState(
    val state: RenderState = RenderState.NOT_RENDERED,
    val lastRenderVersion: Int = 0,
    val renderTimestamp: Long = 0L,
    val errorMessage: String? = null
)

// ─────────────────────────────────────────────────────────────
//  Page
// ─────────────────────────────────────────────────────────────

data class Page(
    val pageId: String,
    val index: Int,
    val boxes: PageBoxes = PageBoxes(),
    val rotation: PageRotation = PageRotation.ROTATION_0,
    val background: Long? = null,       // ARGB or null for transparent
    val objects: List<PdfObject> = emptyList(),
    val annotations: List<AnnotationObject> = emptyList(),
    val widgets: List<FormWidgetObject> = emptyList(),
    val renderState: PageRenderState = PageRenderState()
) {
    val width get() = boxes.width
    val height get() = boxes.height

    /** All objects including annotations and form widgets, ordered by zOrder. */
    val allObjects: List<PdfObject> by lazy {
        (objects + annotations + widgets).sortedBy { it.zOrder }
    }

    fun withObjectAdded(obj: PdfObject): Page = copy(
        objects = objects + obj
    )

    fun withObjectRemoved(objectId: String): Page = copy(
        objects = objects.filter { it.objectId != objectId },
        annotations = annotations.filter { it.objectId != objectId },
        widgets = widgets.filter { it.objectId != objectId }
    )

    fun withObjectUpdated(obj: PdfObject): Page {
        val inMain = objects.any { it.objectId == obj.objectId }
        val inAnno = annotations.any { it.objectId == obj.objectId }
        val inWidget = widgets.any { it.objectId == obj.objectId }
        return when {
            inMain -> copy(objects = objects.map { if (it.objectId == obj.objectId) obj else it })
            inAnno -> copy(annotations = annotations.map { if (it.objectId == obj.objectId) obj as AnnotationObject else it })
            inWidget -> copy(widgets = widgets.map { if (it.objectId == obj.objectId) obj as FormWidgetObject else it })
            else -> this
        }
    }

    fun withRenderState(rs: PageRenderState) = copy(renderState = rs)
    fun withCropBox(cb: PdfRect) = copy(boxes = boxes.copy(cropBox = cb))
    fun withRotation(r: PageRotation) = copy(rotation = r)
}

// ─────────────────────────────────────────────────────────────
//  Document
// ─────────────────────────────────────────────────────────────

/** Source from which the document was opened. */
sealed class DocumentSource {
    data class FilePath(val path: String) : DocumentSource()
    data class ContentUri(val uri: String, val displayName: String = "document.pdf") : DocumentSource()
    data class ByteArraySource(val bytes: ByteArray, val displayName: String) : DocumentSource()
}

data class DirtyState(
    val isDirty: Boolean = false,
    val lastSavedVersion: Int = 0
)

data class Document(
    val documentId: String,
    val source: DocumentSource? = null,
    val name: String,
    val metadata: DocumentMetadata = DocumentMetadata(),
    val pageCount: Int = 0,
    val permissions: DocumentPermissions = DocumentPermissions(),
    val securityInfo: DocumentSecurityInfo = DocumentSecurityInfo(),
    val dirtyState: DirtyState = DirtyState(),
    val version: Int = 0,
    val pages: List<Page> = emptyList(),
    val attachments: List<String> = emptyList(),
    val outline: DocumentOutline = DocumentOutline(),
    val formModel: FormModel? = null,
    val auditLog: List<AuditEntry> = emptyList()
) {
    fun markDirty(): Document = copy(
        dirtyState = dirtyState.copy(isDirty = true),
        version = version + 1
    )

    fun markSaved(): Document = copy(
        dirtyState = dirtyState.copy(isDirty = false, lastSavedVersion = version)
    )

    fun withPageAdded(page: Page): Document = copy(
        pages = pages + page,
        pageCount = pages.size + 1
    ).markDirty()

    fun withPageRemoved(pageId: String): Document = copy(
        pages = pages.filter { it.pageId != pageId },
        pageCount = pages.size - 1
    ).markDirty()

    fun withPageReordered(fromIndex: Int, toIndex: Int): Document {
        if (fromIndex == toIndex || fromIndex !in pages.indices || toIndex !in pages.indices) return this
        val mutable = pages.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return copy(pages = mutable.mapIndexed { i, p -> p.copy(index = i) }, pageCount = mutable.size).markDirty()
    }

    fun withPageUpdated(pageId: String, transform: (Page) -> Page): Document {
        val idx = pages.indexOfFirst { it.pageId == pageId }
        if (idx == -1) return this
        val updated = transform(pages[idx])
        val newPages = pages.toMutableList()
        newPages[idx] = updated
        return copy(pages = newPages).markDirty()
    }

    fun withObjectAdded(pageId: String, obj: PdfObject): Document =
        withPageUpdated(pageId) { it.withObjectAdded(obj) }

    fun withObjectRemoved(pageId: String, objectId: String): Document =
        withPageUpdated(pageId) { it.withObjectRemoved(objectId) }

    fun withObjectUpdated(pageId: String, obj: PdfObject): Document =
        withPageUpdated(pageId) { it.withObjectUpdated(obj) }

    val isDirty get() = dirtyState.isDirty
    val isEncrypted get() = permissions.isEncrypted
    val isReadOnly get() = securityInfo.isReadOnly
}

// ─────────────────────────────────────────────────────────────
//  Form model
// ─────────────────────────────────────────────────────────────

data class FormModel(
    val fields: List<FormField> = emptyList(),
    val hasAcroForm: Boolean = false
)

data class FormField(
    val fieldName: String,
    val fieldType: FormWidgetType,
    val value: String? = null,
    val isReadOnly: Boolean = false,
    val isRequired: Boolean = false
)

// ─────────────────────────────────────────────────────────────
//  Audit log
// ─────────────────────────────────────────────────────────────

data class AuditEntry(
    val timestamp: Long,
    val action: String,
    val description: String,
    val documentVersion: Int
)
