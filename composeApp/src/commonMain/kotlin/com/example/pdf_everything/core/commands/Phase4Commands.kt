package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.RectF
import kotlin.math.max

private fun touch(page: Page): Page = page.copy(
    renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
)

private fun reindex(pages: List<Page>): List<Page> = pages.mapIndexed { i, page ->
    page.copy(
        index = i,
        annotations = page.annotations.map { it.copy(pageIndex = i) },
        widgets = page.widgets.map { it.copy(pageIndex = i) },
        renderState = page.renderState.invalidate()
    )
}

private fun remapFormPages(document: Document, oldToNew: Map<Int, Int>, removed: Set<Int> = emptySet()): Document {
    val fields = document.formModel.fields.mapNotNull { field ->
        val old = field.pageIndex ?: return@mapNotNull field
        if (old in removed) return@mapNotNull null
        field.copy(pageIndex = oldToNew[old])
    }
    return document.copy(formModel = document.formModel.copy(fields = fields))
}

public abstract class SnapshotDocumentCommand(override val description: String) : DocumentCommand() {
    private var before: Document? = null
    private var after: Document? = null
    protected abstract fun transform(document: Document): Document

    override fun execute(document: Document): Document {
        if (after == null) {
            before = document
            after = transform(document)
        }
        return after!!
    }

    override fun undo(document: Document): Document = before ?: document
    override fun redo(document: Document): Document = after ?: transform(document)
}

class CropPagesCommand(
    private val pageIndices: List<Int>,
    private val requestedRect: RectF
) : SnapshotDocumentCommand("Crop page") {
    override fun canExecute(document: Document): Boolean = pageIndices.isNotEmpty() && pageIndices.all { it in document.pages.indices }

    override fun transform(document: Document): Document {
        val selected = pageIndices.toSet()
        val pages = document.pages.mapIndexed { index, page ->
            if (index !in selected) return@mapIndexed page
            val media = page.boxes.media
            val rect = clampRect(requestedRect, media)
            page.copy(
                boxes = page.boxes.copy(crop = rect, trim = rect, art = rect),
                renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
            )
        }
        return document.copy(pages = pages)
    }

    private fun clampRect(rect: RectF, media: RectF): RectF {
        val left = rect.left.coerceIn(media.left, media.right)
        val top = rect.top.coerceIn(media.top, media.bottom)
        val right = rect.right.coerceIn(left, media.right)
        val bottom = rect.bottom.coerceIn(top, media.bottom)
        return RectF(left, top, max(left + 1f, right), max(top + 1f, bottom))
    }
}

class TrimPagesToContentCommand(
    private val pageIndices: List<Int>,
    private val margin: Float,
    private val includeBackgroundObjects: Boolean = true
) : SnapshotDocumentCommand("Trim page to content") {
    override fun canExecute(document: Document): Boolean = pageIndices.any { it in document.pages.indices }

    override fun transform(document: Document): Document {
        val selected = pageIndices.toSet()
        return document.copy(pages = document.pages.mapIndexed { index, page ->
            if (index !in selected) page else {
                val candidates = page.objects.filter { includeBackgroundObjects || it.zOrder != Int.MIN_VALUE }
                val content = candidates.takeIf { it.isNotEmpty() }?.let {
                    RectF(
                        it.minOf { o -> o.bounds.left } - margin,
                        it.minOf { o -> o.bounds.top } - margin,
                        it.maxOf { o -> o.bounds.right } + margin,
                        it.maxOf { o -> o.bounds.bottom } + margin
                    )
                }
                if (content == null) page else page.copy(
                    boxes = page.boxes.copy(crop = clamp(content, page.boxes.media), trim = clamp(content, page.boxes.media)),
                    renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
                )
            }
        })
    }

    private fun clamp(r: RectF, media: RectF): RectF = RectF(
        r.left.coerceIn(media.left, media.right - 1f),
        r.top.coerceIn(media.top, media.bottom - 1f),
        r.right.coerceIn(media.left + 1f, media.right),
        r.bottom.coerceIn(media.top + 1f, media.bottom)
    )
}

class TrimPagesCommand(
    private val pageIndices: List<Int>,
    private val requestedRect: RectF
) : SnapshotDocumentCommand("Trim page") {
    override fun canExecute(document: Document): Boolean = pageIndices.isNotEmpty() && pageIndices.all { it in document.pages.indices }
    override fun transform(document: Document): Document = document.copy(pages = document.pages.mapIndexed { index, page ->
        if (index !in pageIndices.toSet()) page else {
            val m = page.boxes.media
            val r = RectF(
                requestedRect.left.coerceIn(m.left, m.right - 1f),
                requestedRect.top.coerceIn(m.top, m.bottom - 1f),
                requestedRect.right.coerceIn(m.left + 1f, m.right),
                requestedRect.bottom.coerceIn(m.top + 1f, m.bottom)
            )
            page.copy(boxes = page.boxes.copy(trim = r, crop = r), renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true))
        }
    })
}

class DeleteAreaCommand(
    private val pageIndex: Int,
    private val area: RectF
) : SnapshotDocumentCommand("Delete area") {
    override fun canExecute(document: Document): Boolean = pageIndex in document.pages.indices

    override fun transform(document: Document): Document {
        val page = document.pages[pageIndex]
        val updated = page.objects.filterNot { intersects(it.bounds, area) }
        return document.copy(pages = document.pages.mapIndexed { index, p ->
            if (index == pageIndex) touch(p.copy(objects = updated)) else p
        })
    }

    private fun intersects(a: RectF, b: RectF): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
}

class ReorderPagesCommand(private val order: List<Int>) : SnapshotDocumentCommand("Reorder pages") {
    override fun canExecute(document: Document): Boolean = order.size == document.pageCount && order.toSet() == document.pages.indices.toSet()
    override fun transform(document: Document): Document {
        val newPages = reindex(order.map { document.pages[it] })
        val oldToNew = order.withIndex().associate { it.value to it.index }
        val reordered = document.copy(
            pages = newPages,
            outline = remapOutline(document.outline, oldToNew, removed = emptySet())
        )
        return remapFormPages(reordered, oldToNew)
    }
}

class DeletePagesCommand(private val pageIndices: List<Int>) : SnapshotDocumentCommand("Delete pages") {
    override fun canExecute(document: Document): Boolean = pageIndices.isNotEmpty() && pageIndices.all { it in document.pages.indices } && pageIndices.size < document.pageCount
    override fun transform(document: Document): Document {
        val remove = pageIndices.toSet()
        val keptOld = document.pages.indices.filter { it !in remove }
        val oldToNew = keptOld.withIndex().associate { it.value to it.index }
        val trimmed = document.copy(
            pages = reindex(keptOld.map { document.pages[it] }),
            outline = remapOutline(document.outline, oldToNew, remove)
        )
        return remapFormPages(trimmed, oldToNew, remove)
    }
}

class DuplicatePagesCommand(private val pageIndices: List<Int>) : SnapshotDocumentCommand("Duplicate pages") {
    override fun canExecute(document: Document): Boolean = pageIndices.isNotEmpty() && pageIndices.all { it in document.pages.indices }
    override fun transform(document: Document): Document {
        val copies = pageIndices.map { index ->
            val original = document.pages[index]
            copyPage(original)
        }
        return document.copy(pages = reindex(document.pages + copies))
    }

    private fun copyPage(page: Page): Page {
        val suffix = kotlin.random.Random.nextLong().toString(16)
        return page.copy(
            id = "${page.id}-copy-$suffix",
            annotations = page.annotations.map { it.copy(id = "${it.id}-copy-$suffix") },
            widgets = page.widgets.map { it.copy(id = "${it.id}-copy-$suffix") },
            objects = page.objects.map { objectCopy(it, suffix) },
            renderState = page.renderState.invalidate()
        )
    }

    private fun objectCopy(obj: PdfObject, suffix: String): PdfObject = when (obj) {
        is PdfObject.TextObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.ImageObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.VectorObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.PathObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.ShapeObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.AnnotationObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.FormWidgetObject -> obj.copy(id = "${obj.id}-copy-$suffix")
        is PdfObject.UnknownObject -> obj.copy(id = "${obj.id}-copy-$suffix")
    }
}

class RotatePagesCommand(private val pageIndices: List<Int>, private val clockwise: Boolean) : SnapshotDocumentCommand("Rotate pages") {
    override fun canExecute(document: Document): Boolean = pageIndices.isNotEmpty() && pageIndices.all { it in document.pages.indices }
    override fun transform(document: Document): Document {
        val selected = pageIndices.toSet()
        val delta = if (clockwise) 90 else 270
        return document.copy(pages = document.pages.mapIndexed { index, page ->
            if (index in selected) page.copy(rotation = (page.rotation + delta) % 360, renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)) else page
        })
    }
}

class InsertBlankPageCommand(private val position: Int, private val width: Float, private val height: Float) : SnapshotDocumentCommand("Insert blank page") {
    override fun canExecute(document: Document): Boolean = position in 0..document.pageCount && width > 1f && height > 1f
    override fun transform(document: Document): Document {
        val blank = Page(
            id = "blank-${kotlin.random.Random.nextLong().toString(16)}",
            index = position,
            boxes = com.example.pdf_everything.core.document.BoxSet(RectF(0f, 0f, width, height)),
            text = ""
        )
        val pages = document.pages.toMutableList().apply { add(position, blank) }
        return document.copy(pages = reindex(pages))
    }
}

class MergeDocumentsCommand(private val incoming: Document) : SnapshotDocumentCommand("Merge PDF") {
    override fun canExecute(document: Document): Boolean = incoming.pageCount > 0
    override fun transform(document: Document): Document {
        val namespace = "merge-source-${kotlin.random.Random.nextLong().toString(16)}"
        val sourceRefs = incoming.sourceDocuments.ifEmpty {
            incoming.source?.let { listOf(com.example.pdf_everything.core.document.SourceDocumentRef("source", it)) }.orEmpty()
        }
        val idMap = sourceRefs.associate { it.id to "${namespace}:${it.id}" }
        val remappedSources = sourceRefs.map { it.copy(id = idMap.getValue(it.id)) }
        val fallbackSourceId = remappedSources.firstOrNull()?.id
        val copies = incoming.pages.map { page ->
            val suffix = kotlin.random.Random.nextLong().toString(16)
            val mappedSourceId = page.sourceDocumentId?.let { idMap[it] } ?: fallbackSourceId
            page.copy(
                id = "merge-${suffix}",
                sourceDocumentId = mappedSourceId,
                annotations = page.annotations.map { it.copy(id = "merge-ann-${suffix}-${it.id.hashCode()}") },
                widgets = page.widgets.map { it.copy(id = "merge-widget-${suffix}-${it.id.hashCode()}") },
                objects = page.objects.map { obj ->
                    when (obj) {
                        is PdfObject.TextObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.ImageObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.VectorObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.PathObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.ShapeObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.AnnotationObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.FormWidgetObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                        is PdfObject.UnknownObject -> obj.copy(id = "merge-${suffix}-${obj.id.hashCode()}")
                    }
                }
            )
        }
        return document.copy(
            sourceDocuments = document.sourceDocuments + remappedSources,
            pages = reindex(document.pages + copies)
        )
    }
}

class SetPageLabelCommand(private val pageIndex: Int, private val label: String?) : SnapshotDocumentCommand("Set page label") {
    override fun canExecute(document: Document): Boolean = pageIndex in document.pages.indices
    override fun transform(document: Document): Document = document.copy(pages = document.pages.mapIndexed { index, page -> if (index == pageIndex) page.copy(label = label?.trim()?.takeIf { it.isNotEmpty() }) else page })
}

class ReplacePageCommand(
    private val targetIndex: Int,
    private val incoming: Page,
    private val incomingSources: List<com.example.pdf_everything.core.document.SourceDocumentRef> = emptyList()
) : SnapshotDocumentCommand("Replace page") {
    override fun canExecute(document: Document): Boolean = targetIndex in document.pages.indices
    override fun transform(document: Document): Document {
        val namespace = "replace-source-${kotlin.random.Random.nextLong().toString(16)}"
        val sourceRefs = incomingSources
        val idMap = sourceRefs.associate { it.id to "${namespace}:${it.id}" }
        val page = incoming.copy(
            id = "replace-${kotlin.random.Random.nextLong().toString(16)}",
            sourceDocumentId = incoming.sourceDocumentId?.let { idMap[it] } ?: sourceRefs.firstOrNull()?.let { "${namespace}:${it.id}" }
        )
        return document.copy(
            sourceDocuments = document.sourceDocuments + sourceRefs.map { it.copy(id = idMap.getValue(it.id)) },
            pages = reindex(document.pages.mapIndexed { index, current -> if (index == targetIndex) page else current })
        )
    }
}

private fun remapOutline(items: List<com.example.pdf_everything.core.document.OutlineItem>, oldToNew: Map<Int, Int>, removed: Set<Int>): List<com.example.pdf_everything.core.document.OutlineItem> =
    items.mapNotNull { item ->
        val old = item.pageIndex
        val newIndex = when {
            old == null -> null
            old in removed -> return@mapNotNull null
            else -> oldToNew[old]
        }
        item.copy(pageIndex = newIndex, destination = item.destination.copy(pageIndex = newIndex), children = remapOutline(item.children, oldToNew, removed))
    }

fun extractPages(document: Document, pageIndices: List<Int>): Document {
    val valid = pageIndices.filter { it in document.pages.indices }
    return document.copy(
        id = "${document.id}-extract",
        name = "${document.name.substringBeforeLast('.', document.name)}-extract.pdf",
        pages = reindex(valid.map { document.pages[it] }),
        version = 0L,
        dirty = false
    )
}

fun buildSplitPlan(pageCount: Int, everyN: Int): List<IntRange> {
    if (pageCount <= 0 || everyN <= 0) return emptyList()
    return (0 until pageCount).step(everyN).map { start -> start..minOf(pageCount - 1, start + everyN - 1) }
}

fun cutPageIntoSections(document: Document, pageIndex: Int, columns: Int, rows: Int): Document {
    require(pageIndex in document.pages.indices) { "Invalid page index" }
    require(columns in 1..6 && rows in 1..6) { "Section grid must be 1..6" }
    val page = document.pages[pageIndex]
    val media = page.boxes.crop
    val sectionPages = buildList {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val left = media.left + media.width * column / columns
                val right = media.left + media.width * (column + 1) / columns
                val top = media.top + media.height * row / rows
                val bottom = media.top + media.height * (row + 1) / rows
                add(page.copy(
                    id = "section-${kotlin.random.Random.nextLong().toString(16)}",
                    boxes = page.boxes.copy(crop = RectF(left, top, right, bottom), trim = RectF(left, top, right, bottom), art = RectF(left, top, right, bottom)),
                    renderState = page.renderState.copy(version = page.renderState.version + 1, invalidated = true)
                ))
            }
        }
    }
    return document.copy(
        id = "${document.id}-sections",
        name = "${document.name.substringBeforeLast('.', document.name)}-sections.pdf",
        pages = sectionPages.mapIndexed { index, p -> p.copy(index = index) },
        version = 0L,
        dirty = false
    )
}

fun buildSplitPlanFromBreaks(pageCount: Int, breakAfterZeroBasedPages: List<Int>): List<IntRange> {
    if (pageCount <= 0) return emptyList()
    val cuts = (breakAfterZeroBasedPages + (pageCount - 1))
        .filter { it in 0 until pageCount }
        .distinct()
        .sorted()
    val result = mutableListOf<IntRange>()
    var start = 0
    for (cut in cuts) {
        if (cut >= start) {
            result += start..cut
            start = cut + 1
        }
    }
    if (start < pageCount) result += start until pageCount
    return result
}
