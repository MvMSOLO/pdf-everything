package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.DocumentModelService
import kotlin.time.Clock

abstract class DocumentCommand {
    abstract val description: String
    abstract fun execute(document: Document): Document
    abstract fun undo(document: Document): Document
    open fun redo(document: Document): Document = execute(document)
    open fun canExecute(document: Document): Boolean = true
}

class DocumentHistory(private val limit: Int = 200) {
    private val undoStack = ArrayDeque<DocumentCommand>()
    private val redoStack = ArrayDeque<DocumentCommand>()

    fun execute(document: Document, command: DocumentCommand): Document {
        require(command.canExecute(document)) { "Command cannot be executed: ${command.description}" }
        val candidate = command.execute(document)
        if (candidate == document) return document
        val updated = DocumentModelService.validateOrThrow(candidate)
        undoStack.add(command)
        redoStack.clear()
        while (undoStack.size > limit) undoStack.removeFirst()
        return DocumentModelService.markCommandApplied(updated, command.description, Clock.System.now().toEpochMilliseconds())
    }

    fun undo(document: Document): Document {
        val command = undoStack.removeLastOrNull() ?: return document
        return runCatching {
            val updated = DocumentModelService.validateOrThrow(command.undo(document))
            redoStack.add(command)
            DocumentModelService.markCommandApplied(updated, "Undo: ${command.description}", Clock.System.now().toEpochMilliseconds())
        }.getOrElse {
            undoStack.add(command)
            throw IllegalStateException("Undo failed for ${command.description}: ${it.message}", it)
        }
    }

    fun redo(document: Document): Document {
        val command = redoStack.removeLastOrNull() ?: return document
        return runCatching {
            val updated = DocumentModelService.validateOrThrow(command.redo(document))
            undoStack.add(command)
            DocumentModelService.markCommandApplied(updated, "Redo: ${command.description}", Clock.System.now().toEpochMilliseconds())
        }.getOrElse {
            redoStack.add(command)
            throw IllegalStateException("Redo failed for ${command.description}: ${it.message}", it)
        }
    }

    fun clear() { undoStack.clear(); redoStack.clear() }
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

class TransactionCommand(
    override val description: String,
    private val commands: List<DocumentCommand>
) : DocumentCommand() {
    override fun canExecute(document: Document): Boolean {
        var current = document
        for (command in commands) {
            if (!command.canExecute(current)) return false
            current = runCatching { command.execute(current) }.getOrElse { return false }
        }
        return true
    }

    override fun execute(document: Document): Document {
        var current = document
        for (command in commands) current = command.execute(current)
        return current
    }

    override fun undo(document: Document): Document {
        var current = document
        for (command in commands.asReversed()) current = command.undo(current)
        return current
    }

    override fun redo(document: Document): Document = execute(document)
}

private fun Document.mapPage(pageIndex: Int, transform: (Page) -> Page): Document {
    require(pageIndex in pages.indices) { "Invalid page index: $pageIndex" }
    return copy(pages = pages.mapIndexed { index, page -> if (index == pageIndex) transform(page) else page })
}

private fun Page.touch(): Page = copy(renderState = renderState.copy(version = renderState.version + 1, invalidated = true))

class InsertTextCommand(
    private val pageIndex: Int,
    private val text: String,
    private val bounds: com.example.pdf_everything.core.document.RectF,
    private val fontName: String? = null,
    private val fontSize: Float = 12f,
    private val color: String? = null,
    private val objectId: String
) : DocumentCommand() {
    override val description = "Insert text"
    private var beforePage: Page? = null
    private var afterPage: Page? = null
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex) != null && text.isNotEmpty()
    override fun execute(document: Document): Document {
        if (afterPage == null) {
            val page = document.pages[pageIndex]
            beforePage = page
            val obj = PdfObject.TextObject(objectId, text, bounds, fontName = fontName, fontSize = fontSize, color = color)
            afterPage = page.copy(
                objects = page.objects + obj,
                text = page.text + if (page.text.isEmpty()) text else "\n$text",
                renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
            )
        }
        return document.mapPage(pageIndex) { afterPage!! }
    }
    override fun undo(document: Document): Document = beforePage?.let { snapshot -> document.mapPage(pageIndex) { snapshot.touch() } } ?: document
}

class EditTextObjectCommand(
    private val pageIndex: Int,
    private val objectId: String,
    private val newText: String
) : DocumentCommand() {
    override val description = "Edit text"
    private var beforePage: Page? = null
    private var afterPage: Page? = null
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.objects?.any { it.id == objectId && it is PdfObject.TextObject && it.editable } == true
    override fun execute(document: Document): Document {
        if (afterPage == null) {
            val page = document.pages[pageIndex]
            val target = page.objects.first { it.id == objectId } as PdfObject.TextObject
            beforePage = page
            afterPage = page.copy(
                objects = page.objects.map { if (it.id == objectId) target.copy(text = newText) else it },
                text = page.text.replaceFirst(target.text, newText),
                renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
            )
        }
        return document.mapPage(pageIndex) { afterPage!! }
    }
    override fun undo(document: Document): Document = beforePage?.let { snapshot -> document.mapPage(pageIndex) { snapshot.touch() } } ?: document
}

open class MoveObjectCommand(
    private val pageIndex: Int,
    private val before: PdfObject,
    private val after: PdfObject
) : DocumentCommand() {
    override val description = "Move object"
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.objects?.any { it.id == before.id && it.editable } == true
    override fun execute(document: Document): Document = replace(document, after)
    override fun undo(document: Document): Document = replace(document, before)
    private fun replace(document: Document, obj: PdfObject): Document = document.mapPage(pageIndex) { page -> page.copy(objects = page.objects.map { if (it.id == obj.id) obj else it }).touch() }
}

class ResizeObjectCommand(
    pageIndex: Int,
    before: PdfObject,
    after: PdfObject
) : MoveObjectCommand(pageIndex, before, after) {
    override val description = "Resize object"
}

class RotateObjectCommand(
    pageIndex: Int,
    before: PdfObject,
    after: PdfObject
) : MoveObjectCommand(pageIndex, before, after) {
    override val description = "Rotate object"
}

class UpdateObjectCommand(
    private val pageIndex: Int,
    private val before: PdfObject,
    private val after: PdfObject
) : DocumentCommand() {
    override val description = "Update object"
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex)?.objects?.any { it.id == before.id } == true
    override fun execute(document: Document): Document = replace(document, after)
    override fun undo(document: Document): Document = replace(document, before)
    private fun replace(document: Document, obj: PdfObject): Document = document.mapPage(pageIndex) { page -> page.copy(objects = page.objects.map { if (it.id == obj.id) obj else it }).touch() }
}

class InsertImageCommand(
    private val pageIndex: Int,
    private val source: String,
    private val bounds: com.example.pdf_everything.core.document.RectF,
    private val objectId: String
) : DocumentCommand() {
    override val description = "Insert image"
    private var beforePage: Page? = null
    private var afterPage: Page? = null
    override fun canExecute(document: Document): Boolean = document.pages.getOrNull(pageIndex) != null && source.isNotBlank()
    override fun execute(document: Document): Document {
        if (afterPage == null) {
            val page = document.pages[pageIndex]
            beforePage = page
            afterPage = page.copy(objects = page.objects + PdfObject.ImageObject(objectId, source, bounds), renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true))
        }
        return document.mapPage(pageIndex) { afterPage!! }
    }
    override fun undo(document: Document): Document = beforePage?.let { snapshot -> document.mapPage(pageIndex) { snapshot.touch() } } ?: document
}

class DeleteObjectsCommand(private val pageIndex: Int, private val objectIds: List<String>) : DocumentCommand() {
    override val description = "Delete selected objects"
    private var beforePage: Page? = null
    private var afterPage: Page? = null
    override fun canExecute(document: Document): Boolean = objectIds.isNotEmpty() && document.pages.getOrNull(pageIndex)?.objects?.any { it.id in objectIds } == true
    override fun execute(document: Document): Document {
        if (afterPage == null) {
            val page = document.pages[pageIndex]
            beforePage = page
            afterPage = page.copy(objects = page.objects.filterNot { it.id in objectIds }, renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true))
        }
        return document.mapPage(pageIndex) { afterPage!! }
    }
    override fun undo(document: Document): Document = beforePage?.let { snapshot -> document.mapPage(pageIndex) { snapshot.touch() } } ?: document
}
