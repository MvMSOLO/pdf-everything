package com.example.pdf_everything.core.clipboard

import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.PdfPoint
import com.example.pdf_everything.core.document.PdfRect

/**
 * Internal clipboard for PDF objects (separate from platform system clipboard).
 *
 * Stores copied/cut objects so they can be pasted with an offset.
 * Also supports plain text for interoperability.
 */
class ClipboardManager {
    
    data class ClipboardEntry(
        val objects: List<PdfObject>,
        val sourcePageId: String?,
        val isCut: Boolean,    // true if this was a Cut operation (original will be removed)
        val timestamp: Long
    )

    private var internalClipboard: ClipboardEntry? = null
    private var plainText: String? = null

    fun copyObjects(objects: List<PdfObject>, sourcePageId: String?) {
        internalClipboard = ClipboardEntry(
            objects = objects,
            sourcePageId = sourcePageId,
            isCut = false,
            timestamp = System.currentTimeMillis()
        )
    }

    fun cutObjects(objects: List<PdfObject>, sourcePageId: String?) {
        internalClipboard = ClipboardEntry(
            objects = objects,
            sourcePageId = sourcePageId,
            isCut = true,
            timestamp = System.currentTimeMillis()
        )
    }

    fun copyText(text: String) {
        plainText = text
    }

    fun pasteObjects(offset: PdfPoint = PdfPoint(10f, 10f)): List<PdfObject> {
        val entry = internalClipboard ?: return emptyList()
        return entry.objects.map { obj ->
            val newId = "paste_${System.currentTimeMillis()}_${obj.objectId}"
            val offsetBb = obj.boundingBox.offset(offset.x, offset.y)
            when (obj) {
                is com.example.pdf_everything.core.document.TextObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.ImageObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.VectorObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.PathObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.ShapeObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.AnnotationObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.FormWidgetObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
                is com.example.pdf_everything.core.document.UnknownObject -> obj.copy(objectId = newId, boundingBox = offsetBb)
            }
        }
    }

    fun pasteText(): String? = plainText

    val hasObjects: Boolean get() = internalClipboard != null && internalClipboard!!.objects.isNotEmpty()
    val hasText: Boolean get() = plainText != null
    val isCutOperation: Boolean get() = internalClipboard?.isCut == true
    val sourcePageId: String? get() = internalClipboard?.sourcePageId

    fun clear() {
        internalClipboard = null
        plainText = null
    }
}