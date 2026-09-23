package com.example.pdf_everything.core.editor

import com.example.pdf_everything.core.clipboard.InternalClipboard
import com.example.pdf_everything.core.clipboard.ObjectClipboard
import com.example.pdf_everything.core.clipboard.PlatformClipboard
import com.example.pdf_everything.core.commands.DeleteObjectsCommand
import com.example.pdf_everything.core.commands.DocumentCommand
import com.example.pdf_everything.core.commands.DocumentHistory
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.commands.EditTextObjectCommand
import com.example.pdf_everything.core.commands.InsertImageCommand
import com.example.pdf_everything.core.commands.MoveObjectCommand
import com.example.pdf_everything.core.commands.ResizeObjectCommand
import com.example.pdf_everything.core.commands.RotateObjectCommand
import com.example.pdf_everything.core.commands.InsertTextCommand
import com.example.pdf_everything.core.commands.UpdateObjectCommand
import com.example.pdf_everything.core.commands.CropPagesCommand
import com.example.pdf_everything.core.commands.TrimPagesToContentCommand
import com.example.pdf_everything.core.commands.TrimPagesCommand
import com.example.pdf_everything.core.commands.DeleteAreaCommand
import com.example.pdf_everything.core.commands.ReorderPagesCommand
import com.example.pdf_everything.core.commands.DeletePagesCommand
import com.example.pdf_everything.core.commands.DuplicatePagesCommand
import com.example.pdf_everything.core.commands.RotatePagesCommand
import com.example.pdf_everything.core.commands.InsertBlankPageCommand
import com.example.pdf_everything.core.commands.MergeDocumentsCommand
import com.example.pdf_everything.core.commands.ReplacePageCommand
import com.example.pdf_everything.core.commands.SetPageLabelCommand
import com.example.pdf_everything.core.commands.AddAnnotationCommand
import com.example.pdf_everything.core.commands.UpdateAnnotationCommand
import com.example.pdf_everything.core.commands.DeleteAnnotationCommand
import com.example.pdf_everything.core.commands.UpdateMetadataCommand
import com.example.pdf_everything.core.commands.AddBookmarkCommand
import com.example.pdf_everything.core.commands.UpdateBookmarkCommand
import com.example.pdf_everything.core.commands.DeleteBookmarkCommand
import com.example.pdf_everything.core.commands.ReorderBookmarkCommand
import com.example.pdf_everything.core.commands.FillFormFieldCommand
import com.example.pdf_everything.core.commands.ResetFormFieldCommand
import com.example.pdf_everything.core.commands.extractPages
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.PdfAnnotation
import com.example.pdf_everything.core.document.DocumentMetadata
import com.example.pdf_everything.core.document.OutlineItem
import com.example.pdf_everything.core.document.RectF
import com.example.pdf_everything.core.selection.Selection
import com.example.pdf_everything.core.selection.SelectionType
import com.example.pdf_everything.core.selection.SelectionModel
import kotlin.math.max

class EditorController(initialDocument: Document? = null) {
    var document: Document? = initialDocument
        private set
    val history = DocumentHistory()
    private val commandDispatcher = CommandDispatcher(history)
    val selectionModel = SelectionModel()
    val internalClipboard = InternalClipboard()

    fun setDocument(value: Document?) {
        document = value
        commandDispatcher.clear()
        selectionModel.clear()
        internalClipboard.clear()
    }

    fun dispatch(command: DocumentCommand): Boolean {
        val current = document ?: return false
        if (!command.canExecute(current)) return false
        val result = commandDispatcher.dispatch(current, command)
        if (result.changed) document = result.document
        return result.changed
    }

    fun undo(): Boolean {
        val current = document ?: return false
        val result = commandDispatcher.undo(current)
        if (result.changed) document = result.document
        return result.changed
    }

    fun redo(): Boolean {
        val current = document ?: return false
        val result = commandDispatcher.redo(current)
        if (result.changed) document = result.document
        return result.changed
    }

    fun copySelection(): Boolean {
        val doc = document ?: return false
        val selection = selectionModel.current
        val page = selection.pageIndex?.let { doc.pages.getOrNull(it) } ?: return false
        if (selection.type == SelectionType.TextRange) {
            val start = selection.textStart ?: return false
            val end = selection.textEnd ?: return false
            val text = page.text.substring(start.coerceIn(0, page.text.length), end.coerceIn(0, page.text.length))
            if (text.isEmpty()) return false
            PlatformClipboard.writeText(text)
            return true
        }
        val objects = page.objects.filter { it.id in selection.selectedIds }
        if (objects.isEmpty()) return false
        internalClipboard.put(ObjectClipboard(objects, page.index))
        PlatformClipboard.writeObject(ObjectClipboard(objects, page.index))
        return true
    }

    fun cutSelection(): Boolean {
        val selection = selectionModel.current
        if (selection.isEmpty) return false
        if (!copySelection()) return false
        val pageIndex = selection.pageIndex ?: return false
        if (selection.type == SelectionType.TextRange) {
            val page = document?.pages?.getOrNull(pageIndex) ?: return false
            val start = selection.textStart ?: return false
            val end = selection.textEnd ?: return false
            val original = page.text
            if (start >= end || start !in 0..original.length || end > original.length) return false
            return dispatch(TextEditRangeCommand(pageIndex, start, end, ""))
        }
        return dispatch(DeleteObjectsCommand(pageIndex, selection.selectedIds))
    }

    fun paste(): Boolean {
        val doc = document ?: return false
        val selection = selectionModel.current
        val pageIndex = selection.pageIndex ?: 0
        val page = doc.pages.getOrNull(pageIndex) ?: return false
        val external = PlatformClipboard.read()
        val objects = internalClipboard.get() ?: external.objectPayload
        if (objects != null && objects.objects.isNotEmpty()) {
            val offset = max(8f, page.boxes.media.width * 0.01f)
            val commands = objects.objects.mapIndexed { index, original ->
                val shifted = shiftObject(original, offset, offset, "${original.id}-paste-${doc.version}-$index")
                InsertOrUpdatePastedObjectCommand(pageIndex, shifted)
            }
            if (commands.size == 1) return dispatch(commands.first())
            return dispatch(com.example.pdf_everything.core.commands.TransactionCommand("Paste objects", commands))
        }
        external.image?.let { png ->
            val source = PlatformClipboard.materializeImage(png)
            if (source != null) return dispatch(InsertImageCommand(pageIndex, source.toString(), defaultInsertBounds(page), newObjectId("image")))
        }
        val text = external.text ?: return false
        return dispatch(InsertTextCommand(pageIndex, text, defaultInsertBounds(page), fontSize = 12f, objectId = newObjectId("text")))
    }

    fun selectTextRange(pageIndex: Int, start: Int, end: Int) {
        val page = document?.pages?.getOrNull(pageIndex) ?: return
        val safeStart = start.coerceIn(0, page.text.length)
        val safeEnd = end.coerceIn(safeStart, page.text.length)
        selectionModel.selectText(pageIndex, safeStart, safeEnd)
    }

    fun insertText(pageIndex: Int, text: String, bounds: RectF, fontName: String?, fontSize: Float, color: String?): Boolean =
        dispatch(InsertTextCommand(pageIndex, text, bounds, fontName, fontSize, color, newObjectId("text")))

    fun editText(pageIndex: Int, objectId: String, newText: String): Boolean = dispatch(EditTextObjectCommand(pageIndex, objectId, newText))

    fun insertImage(pageIndex: Int, source: String, bounds: RectF): Boolean =
        dispatch(InsertImageCommand(pageIndex, source, bounds, newObjectId("image")))

    fun replaceImage(pageIndex: Int, objectId: String, source: String): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } as? PdfObject.ImageObject ?: return false
        return dispatch(UpdateObjectCommand(pageIndex, before, before.copy(source = source)))
    }

    fun cropImage(pageIndex: Int, objectId: String, crop: RectF?): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } as? PdfObject.ImageObject ?: return false
        return dispatch(UpdateObjectCommand(pageIndex, before, before.copy(crop = crop)))
    }

    fun setImageOpacity(pageIndex: Int, objectId: String, opacity: Float): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } as? PdfObject.ImageObject ?: return false
        return dispatch(UpdateObjectCommand(pageIndex, before, before.copy(opacity = opacity.coerceIn(0f, 1f))))
    }

    fun flipImage(pageIndex: Int, objectId: String, horizontal: Boolean): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } as? PdfObject.ImageObject ?: return false
        return dispatch(UpdateObjectCommand(pageIndex, before, if (horizontal) before.copy(flipHorizontal = !before.flipHorizontal) else before.copy(flipVertical = !before.flipVertical)))
    }

    fun moveObject(pageIndex: Int, objectId: String, dx: Float, dy: Float): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } ?: return false
        val after = shiftObject(before, dx, dy, before.id)
        return dispatch(MoveObjectCommand(pageIndex, before, after))
    }

    fun resizeObject(pageIndex: Int, objectId: String, newBounds: RectF): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } ?: return false
        val after = copyWithBounds(before, newBounds)
        return dispatch(ResizeObjectCommand(pageIndex, before, after))
    }

    fun rotateObject(pageIndex: Int, objectId: String, degrees: Float): Boolean {
        val before = document?.pages?.getOrNull(pageIndex)?.objects?.firstOrNull { it.id == objectId } ?: return false
        return dispatch(RotateObjectCommand(pageIndex, before, copyWithRotation(before, before.rotation + degrees)))
    }

    fun deleteSelection(): Boolean = selectionModel.current.pageIndex?.let { page ->
        when (selectionModel.current.type.name) {
            "TextRange" -> {
                val s = selectionModel.current.textStart ?: return@let false
                val e = selectionModel.current.textEnd ?: return@let false
                dispatch(TextEditRangeCommand(page, s, e, ""))
            }
            else -> dispatch(DeleteObjectsCommand(page, selectionModel.current.selectedIds))
        }
    } ?: false

    // Phase 4: CUT / ORGANIZE operations are structured document transactions.
    fun cropPages(pageIndices: List<Int>, rect: RectF): Boolean = dispatch(CropPagesCommand(pageIndices, rect))

    fun trimPagesToContent(pageIndices: List<Int>, margin: Float, includeBackgroundObjects: Boolean = true): Boolean =
        dispatch(TrimPagesToContentCommand(pageIndices, margin.coerceAtLeast(0f), includeBackgroundObjects))

    fun trimPages(pageIndices: List<Int>, rect: RectF): Boolean = dispatch(TrimPagesCommand(pageIndices, rect))

    fun deleteArea(pageIndex: Int, rect: RectF): Boolean = dispatch(DeleteAreaCommand(pageIndex, rect))

    fun reorderPages(order: List<Int>): Boolean = dispatch(ReorderPagesCommand(order))

    fun deletePages(pageIndices: List<Int>): Boolean = dispatch(DeletePagesCommand(pageIndices.distinct().sorted()))

    fun duplicatePages(pageIndices: List<Int>): Boolean = dispatch(DuplicatePagesCommand(pageIndices.distinct()))

    fun rotatePages(pageIndices: List<Int>, clockwise: Boolean = true): Boolean =
        dispatch(RotatePagesCommand(pageIndices.distinct(), clockwise))

    fun insertBlankPage(position: Int, width: Float, height: Float): Boolean =
        dispatch(InsertBlankPageCommand(position, width, height))

    fun mergeDocument(incoming: Document): Boolean = dispatch(MergeDocumentsCommand(incoming))

    fun replacePage(
        targetIndex: Int,
        incoming: com.example.pdf_everything.core.document.Page,
        incomingSources: List<com.example.pdf_everything.core.document.SourceDocumentRef> = emptyList()
    ): Boolean = dispatch(ReplacePageCommand(targetIndex, incoming, incomingSources))

    fun setPageLabel(pageIndex: Int, label: String?): Boolean = dispatch(SetPageLabelCommand(pageIndex, label))

    fun extractDocument(pageIndices: List<Int>): Document? = document?.let { extractPages(it, pageIndices) }

    fun addAnnotation(pageIndex: Int, annotation: PdfAnnotation): Boolean = dispatch(AddAnnotationCommand(pageIndex, annotation))

    fun updateAnnotation(pageIndex: Int, before: PdfAnnotation, after: PdfAnnotation): Boolean = dispatch(UpdateAnnotationCommand(pageIndex, before, after))

    fun deleteAnnotation(pageIndex: Int, annotationId: String): Boolean = dispatch(DeleteAnnotationCommand(pageIndex, annotationId))

    fun setMetadata(metadata: DocumentMetadata): Boolean = dispatch(UpdateMetadataCommand(metadata))

    fun addBookmark(parentId: String?, item: OutlineItem): Boolean = dispatch(AddBookmarkCommand(parentId, item))

    fun updateBookmark(itemId: String, item: OutlineItem): Boolean = dispatch(UpdateBookmarkCommand(itemId, item))

    fun deleteBookmark(itemId: String): Boolean = dispatch(DeleteBookmarkCommand(itemId))

    fun moveBookmark(itemId: String, newParentId: String?, newIndex: Int): Boolean = dispatch(ReorderBookmarkCommand(itemId, newParentId, newIndex))

    fun fillFormField(fieldId: String, value: String, selectedValues: List<String> = emptyList()): Boolean = dispatch(FillFormFieldCommand(fieldId, value, selectedValues))

    fun resetFormField(fieldId: String): Boolean = dispatch(ResetFormFieldCommand(fieldId))



    private fun shiftObject(obj: PdfObject, dx: Float, dy: Float, id: String): PdfObject = copyWithId(copyWithBounds(obj, obj.bounds.copy(left = obj.bounds.left + dx, right = obj.bounds.right + dx, top = obj.bounds.top + dy, bottom = obj.bounds.bottom + dy)), id)
    private fun copyWithBounds(obj: PdfObject, bounds: RectF): PdfObject = when (obj) {
        is PdfObject.TextObject -> obj.copy(bounds = bounds)
        is PdfObject.ImageObject -> obj.copy(bounds = bounds)
        is PdfObject.VectorObject -> obj.copy(bounds = bounds)
        is PdfObject.PathObject -> obj.copy(bounds = bounds)
        is PdfObject.ShapeObject -> obj.copy(bounds = bounds)
        is PdfObject.AnnotationObject -> obj.copy(bounds = bounds)
        is PdfObject.FormWidgetObject -> obj.copy(bounds = bounds)
        is PdfObject.UnknownObject -> obj.copy(bounds = bounds)
    }
    private fun copyWithRotation(obj: PdfObject, rotation: Float): PdfObject = when (obj) {
        is PdfObject.TextObject -> obj.copy(rotation = rotation)
        is PdfObject.ImageObject -> obj.copy(rotation = rotation)
        is PdfObject.VectorObject -> obj.copy(rotation = rotation)
        is PdfObject.PathObject -> obj.copy(rotation = rotation)
        is PdfObject.ShapeObject -> obj.copy(rotation = rotation)
        is PdfObject.AnnotationObject -> obj.copy(rotation = rotation)
        is PdfObject.FormWidgetObject -> obj.copy(rotation = rotation)
        is PdfObject.UnknownObject -> obj.copy(rotation = rotation)
    }
    private fun copyWithId(obj: PdfObject, id: String): PdfObject = when (obj) {
        is PdfObject.TextObject -> obj.copy(id = id)
        is PdfObject.ImageObject -> obj.copy(id = id)
        is PdfObject.VectorObject -> obj.copy(id = id)
        is PdfObject.PathObject -> obj.copy(id = id)
        is PdfObject.ShapeObject -> obj.copy(id = id)
        is PdfObject.AnnotationObject -> obj.copy(id = id)
        is PdfObject.FormWidgetObject -> obj.copy(id = id)
        is PdfObject.UnknownObject -> obj.copy(id = id)
    }
    private fun defaultInsertBounds(page: com.example.pdf_everything.core.document.Page): RectF = RectF(36f, 36f, minOf(page.boxes.media.width - 36f, 360f), 72f)
    private fun newObjectId(prefix: String): String = "$prefix-${document?.version ?: 0}-${kotlin.random.Random.nextLong().toString(16)}"
}

private class TextEditRangeCommand(
    private val pageIndex: Int,
    private val start: Int,
    private val end: Int,
    private val replacement: String
) : DocumentCommand() {
    override val description = "Edit selected text"
    private var beforePage: com.example.pdf_everything.core.document.Page? = null
    private var afterPage: com.example.pdf_everything.core.document.Page? = null
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.let { start in 0..it.text.length && end in start..it.text.length } == true
    override fun execute(document: Document): Document {
        if (afterPage == null) {
            val page = document.pages[pageIndex]
            val updatedText = page.text.substring(0, start) + replacement + page.text.substring(end)
            beforePage = page
            afterPage = page.copy(text = updatedText, renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true))
        }
        return document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) afterPage!! else p })
    }
    override fun undo(document: Document): Document = beforePage?.let { snapshot -> document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) snapshot.copy(renderState = snapshot.renderState.copy(version = snapshot.renderState.version + 1, invalidated = true)) else p }) } ?: document
}

private class InsertOrUpdatePastedObjectCommand(private val pageIndex: Int, private val pastedObject: PdfObject) : DocumentCommand() {
    override val description = "Paste object"
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex) != null
    override fun execute(document: Document): Document = document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(objects = p.objects + pastedObject, renderState = p.renderState.copy(version = p.renderState.version + 1, invalidated = true)) else p })
    override fun undo(document: Document): Document = document.copy(pages = document.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(objects = p.objects.filterNot { it.id == pastedObject.id }) else p })
}
