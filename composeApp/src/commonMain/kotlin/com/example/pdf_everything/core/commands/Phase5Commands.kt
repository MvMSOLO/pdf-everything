package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.*

/** Real document-model commands for Phase 5. Persistence is intentionally delegated to the PDF engine/save layer. */

class AddAnnotationCommand(private val pageIndex: Int, private val annotation: PdfAnnotation) : DocumentCommand() {
    override val description = "Add ${annotation.type.name.lowercase()} annotation"
    private var before: Page? = null
    override fun canExecute(document: Document): Boolean = pageIndex in document.pages.indices && annotation.bounds.width > 0f && annotation.bounds.height > 0f
    override fun execute(document: Document): Document {
        if (before == null) before = document.pages[pageIndex]
        val p = document.pages[pageIndex]
        val updated = p.copy(
            annotations = p.annotations.filterNot { it.id == annotation.id } + annotation.copy(pageIndex = pageIndex),
            objects = p.objects.filterNot { it is PdfObject.AnnotationObject && it.id == annotation.id } + PdfObject.AnnotationObject(
                id = annotation.id,
                bounds = annotation.bounds,
                subtype = annotation.type.name,
                opacity = annotation.style.opacity
            ),
            renderState = p.renderState.copy(version = p.renderState.version + 1, invalidated = true)
        )
        return document.copy(pages = document.pages.mapIndexed { i, page -> if (i == pageIndex) updated else page })
    }
    override fun undo(document: Document): Document = before?.let { snap -> document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) snap.copy(renderState = snap.renderState.copy(version = snap.renderState.version + 1, invalidated = true)) else p }) } ?: document
}

class UpdateAnnotationCommand(private val pageIndex: Int, private val beforeAnnotation: PdfAnnotation, private val afterAnnotation: PdfAnnotation) : DocumentCommand() {
    override val description = "Update annotation"
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.annotations?.any { it.id == beforeAnnotation.id } == true
    override fun execute(document: Document): Document = replace(document, afterAnnotation)
    override fun undo(document: Document): Document = replace(document, beforeAnnotation)
    private fun replace(document: Document, annotation: PdfAnnotation): Document {
        val page = document.pages[pageIndex]
        val next = page.copy(
            annotations = page.annotations.map { if (it.id == beforeAnnotation.id) annotation.copy(pageIndex = pageIndex) else it },
            objects = page.objects.map { if (it.id == beforeAnnotation.id && it is PdfObject.AnnotationObject) it.copy(bounds = annotation.bounds, subtype = annotation.type.name, opacity = annotation.style.opacity) else it },
            renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
        )
        return document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) next else p })
    }
}

class DeleteAnnotationCommand(private val pageIndex: Int, private val annotationId: String) : DocumentCommand() {
    override val description = "Delete annotation"
    private var removed: PdfAnnotation? = null
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.annotations?.any { it.id == annotationId } == true
    override fun execute(document: Document): Document {
        val page = document.pages[pageIndex]
        removed = removed ?: page.annotations.first { it.id == annotationId }
        return document.copy(pages = document.pages.mapIndexed { i, p ->
            if (i == pageIndex) p.copy(
                annotations = p.annotations.filterNot { it.id == annotationId },
                objects = p.objects.filterNot { it.id == annotationId },
                renderState = p.renderState.copy(version = p.renderState.version + 1, invalidated = true)
            ) else p
        })
    }
    override fun undo(document: Document): Document = removed?.let { ann ->
        document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(annotations = p.annotations + ann, renderState = p.renderState.copy(version = p.renderState.version + 1, invalidated = true)) else p })
    } ?: document
}

class UpdateMetadataCommand(private val metadata: DocumentMetadata) : DocumentCommand() {
    override val description = "Edit metadata"
    private var before: DocumentMetadata? = null
    override fun execute(document: Document): Document { before = before ?: document.metadata; return document.copy(metadata = metadata) }
    override fun undo(document: Document): Document = document.copy(metadata = before ?: document.metadata)
}

class AddBookmarkCommand(private val parentId: String?, private val item: OutlineItem) : DocumentCommand() {
    override val description = "Add bookmark"
    private var before: List<OutlineItem>? = null
    override fun execute(document: Document): Document {
        before = before ?: document.outline
        return document.copy(outline = appendOutline(document.outline, parentId, item))
    }
    override fun undo(document: Document): Document = document.copy(outline = before ?: document.outline)
}

class UpdateBookmarkCommand(private val itemId: String, private val updated: OutlineItem) : DocumentCommand() {
    override val description = "Update bookmark"
    private var before: List<OutlineItem>? = null
    override fun execute(document: Document): Document { before = before ?: document.outline; return document.copy(outline = updateOutline(document.outline, itemId) { updated.copy(id = itemId) }) }
    override fun undo(document: Document): Document = document.copy(outline = before ?: document.outline)
}

class DeleteBookmarkCommand(private val itemId: String) : DocumentCommand() {
    override val description = "Delete bookmark"
    private var before: List<OutlineItem>? = null
    override fun execute(document: Document): Document { before = before ?: document.outline; return document.copy(outline = deleteOutline(document.outline, itemId)) }
    override fun undo(document: Document): Document = document.copy(outline = before ?: document.outline)
}

class ReorderBookmarkCommand(private val itemId: String, private val newParentId: String?, private val newIndex: Int) : DocumentCommand() {
    override val description = "Move bookmark"
    private var before: List<OutlineItem>? = null
    override fun execute(document: Document): Document {
        before = before ?: document.outline
        val (item, remaining) = detachOutline(document.outline, itemId)
        return if (item == null) document else document.copy(outline = insertOutline(remaining, newParentId, newIndex, item))
    }
    override fun undo(document: Document): Document = document.copy(outline = before ?: document.outline)
}

class FillFormFieldCommand(private val fieldId: String, private val value: String, private val selectedValues: List<String> = emptyList()) : DocumentCommand() {
    override val description = "Fill form field"
    private var before: FormModel? = null
    override fun canExecute(document: Document): Boolean = document.formModel.fields.any { it.id == fieldId && !it.readOnly }
    override fun execute(document: Document): Document {
        before = before ?: document.formModel
        val updatedFields = document.formModel.fields.map {
            if (it.id != fieldId) it else it.copy(value = value, selectedValues = selectedValues.ifEmpty { listOf(value).filter { v -> v.isNotEmpty() } }, exportValue = value.ifEmpty { it.exportValue })
        }
        return document.copy(formModel = document.formModel.copy(fields = updatedFields))
    }
    override fun undo(document: Document): Document = document.copy(formModel = before ?: document.formModel)
}

class ResetFormFieldCommand(private val fieldId: String) : DocumentCommand() {
    override val description = "Reset form field"
    private var before: FormModel? = null
    override fun canExecute(document: Document): Boolean = document.formModel.fields.any { it.id == fieldId && !it.readOnly }
    override fun execute(document: Document): Document {
        before = before ?: document.formModel
        return document.copy(formModel = document.formModel.copy(fields = document.formModel.fields.map { if (it.id == fieldId) it.copy(value = "", selectedValues = emptyList()) else it }))
    }
    override fun undo(document: Document): Document = document.copy(formModel = before ?: document.formModel)
}

private fun appendOutline(items: List<OutlineItem>, parentId: String?, child: OutlineItem): List<OutlineItem> =
    if (parentId == null) items + child
    else items.map { if (it.id == parentId) it.copy(children = it.children + child) else it.copy(children = appendOutline(it.children, parentId, child)) }

private fun updateOutline(items: List<OutlineItem>, id: String, transform: (OutlineItem) -> OutlineItem): List<OutlineItem> =
    items.map { item -> if (item.id == id) transform(item) else item.copy(children = updateOutline(item.children, id, transform)) }

private fun deleteOutline(items: List<OutlineItem>, id: String): List<OutlineItem> =
    items.filterNot { it.id == id }.map { it.copy(children = deleteOutline(it.children, id)) }

private fun detachOutline(items: List<OutlineItem>, id: String): Pair<OutlineItem?, List<OutlineItem>> {
    for (index in items.indices) {
        val item = items[index]
        if (item.id == id) return item to items.toMutableList().apply { removeAt(index) }
        val (found, children) = detachOutline(item.children, id)
        if (found != null) return found to items.mapIndexed { i, current -> if (i == index) current.copy(children = children) else current }
    }
    return null to items
}

private fun insertOutline(items: List<OutlineItem>, parentId: String?, index: Int, item: OutlineItem): List<OutlineItem> {
    if (parentId == null) return items.toMutableList().apply { add(index.coerceIn(0, size), item) }
    return items.map { current -> if (current.id == parentId) current.copy(children = current.children.toMutableList().apply { add(index.coerceIn(0, size), item) }) else current.copy(children = insertOutline(current.children, parentId, index, item)) }
}
