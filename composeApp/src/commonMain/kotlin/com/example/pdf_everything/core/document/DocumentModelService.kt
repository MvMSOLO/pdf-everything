package com.example.pdf_everything.core.document

/**
 * Document-model utilities that keep structural invariants in one place instead of scattering
 * index/id repair logic throughout UI code.
 */
object DocumentModelService {
    fun normalize(document: Document): Document {
        val pageCount = document.pages.size
        val normalizedPages = document.pages.mapIndexed { index, page ->
            page.copy(
                index = index,
                annotations = page.annotations.map { it.copy(pageIndex = index) },
                widgets = page.widgets.map { it.copy(pageIndex = index) },
                renderState = page.renderState.invalidate()
            )
        }
        val normalizedForms = document.formModel.copy(
            fields = document.formModel.fields.map { field ->
                field.copy(pageIndex = field.pageIndex?.takeIf { it in 0 until pageCount })
            }
        )
        val normalized = document.copy(pages = normalizedPages, formModel = normalizedForms)
        val errors = normalized.validate()
        check(errors.isEmpty()) { "Document normalization produced invalid state: ${errors.joinToString("; ")}" }
        return normalized
    }

    fun selectObject(document: Document, pageIndex: Int, objectId: String, additive: Boolean = false): Document {
        if (pageIndex !in document.pages.indices) return document
        val pages = document.pages.mapIndexed { index, page ->
            if (index != pageIndex) page
            else page.copy(objects = page.objects.map { obj ->
                val selected = if (additive) {
                    obj.selection.copy(selected = if (obj.id == objectId) !obj.selection.selected else obj.selection.selected)
                } else obj.selection.copy(selected = obj.id == objectId)
                copyObjectWithSelection(obj, selected)
            }, renderState = page.renderState.invalidate())
        }
        return document.copy(pages = pages)
    }

    fun markClean(document: Document, savedAtEpochMs: Long, fingerprint: String?): Document = document.copy(
        dirty = false,
        dirtyState = DirtyState.CLEAN,
        historyMetadata = document.historyMetadata.copy(
            audit = document.historyMetadata.audit.recordSave(savedAtEpochMs, fingerprint)
        )
    )

    fun markCommandApplied(document: Document, description: String, timestamp: Long): Document {
        val nextRevision = document.historyMetadata.audit.revision + 1L
        return document.copy(
            dirty = true,
            dirtyState = DirtyState.DIRTY,
            version = document.version + 1L,
            historyMetadata = document.historyMetadata.copy(
                audit = document.historyMetadata.audit.recordCommand(description, timestamp, nextRevision)
            )
        )
    }

    fun identityMap(document: Document): Map<String, Int> =
        document.pages.withIndex().associate { (index, page) -> page.id to index }

    fun findPageIndex(document: Document, pageId: String): Int? =
        document.pages.indexOfFirst { it.id == pageId }.takeIf { it >= 0 }

    fun validateOrThrow(document: Document): Document {
        val normalized = normalize(document)
        val errors = normalized.validate()
        check(errors.isEmpty()) { "Document invariant violation: ${errors.joinToString("; ")}" }
        return normalized
    }

    private fun copyObjectWithSelection(obj: PdfObject, selection: ObjectSelectionState): PdfObject = when (obj) {
        is PdfObject.TextObject -> obj.copy(selection = selection)
        is PdfObject.ImageObject -> obj.copy(selection = selection)
        is PdfObject.VectorObject -> obj.copy(selection = selection)
        is PdfObject.PathObject -> obj.copy(selection = selection)
        is PdfObject.ShapeObject -> obj.copy(selection = selection)
        is PdfObject.AnnotationObject -> obj.copy(selection = selection)
        is PdfObject.FormWidgetObject -> obj.copy(selection = selection)
        is PdfObject.UnknownObject -> obj.copy(selection = selection)
    }
}
