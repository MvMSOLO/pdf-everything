package com.example.pdf_everything.core.selection

import com.example.pdf_everything.core.document.RectF

enum class SelectionType { None, SingleObject, MultipleObjects, TextRange, Page, CropRectangle, Annotation }

data class Selection(
    val type: SelectionType = SelectionType.None,
    val pageIndex: Int? = null,
    val selectedIds: List<String> = emptyList(),
    val textStart: Int? = null,
    val textEnd: Int? = null,
    val bounds: RectF? = null,
    val anchor: Int = 0,
    val transformHandles: Boolean = false,
    val canCopy: Boolean = false,
    val canCut: Boolean = false,
    val canDelete: Boolean = false
) {
    val isEmpty: Boolean get() = type == SelectionType.None
}

class SelectionModel {
    var current: Selection = Selection()
        private set

    fun clear() { current = Selection() }

    fun selectObject(pageIndex: Int, id: String, bounds: RectF, canCut: Boolean, canDelete: Boolean) {
        current = Selection(
            type = SelectionType.SingleObject,
            pageIndex = pageIndex,
            selectedIds = listOf(id),
            bounds = bounds,
            transformHandles = true,
            canCopy = true,
            canCut = canCut,
            canDelete = canDelete
        )
    }

    fun selectObjects(pageIndex: Int, ids: List<String>, bounds: RectF?, canCut: Boolean, canDelete: Boolean) {
        current = Selection(
            type = if (ids.size == 1) SelectionType.SingleObject else SelectionType.MultipleObjects,
            pageIndex = pageIndex,
            selectedIds = ids,
            bounds = bounds,
            transformHandles = true,
            canCopy = ids.isNotEmpty(),
            canCut = canCut,
            canDelete = canDelete
        )
    }

    fun selectPage(pageIndex: Int, bounds: RectF?) {
        current = Selection(SelectionType.Page, pageIndex = pageIndex, bounds = bounds, canCopy = false, canCut = false, canDelete = false)
    }

    fun selectCrop(pageIndex: Int, bounds: RectF) {
        current = Selection(SelectionType.CropRectangle, pageIndex = pageIndex, bounds = bounds, transformHandles = true)
    }

    fun selectAnnotation(pageIndex: Int, id: String, bounds: RectF) {
        current = Selection(SelectionType.Annotation, pageIndex = pageIndex, selectedIds = listOf(id), bounds = bounds, transformHandles = true, canCopy = true, canCut = true, canDelete = true)
    }

    fun selectText(pageIndex: Int, start: Int, end: Int, bounds: RectF? = null) {
        val safeStart = minOf(start, end).coerceAtLeast(0)
        val safeEnd = maxOf(start, end).coerceAtLeast(safeStart)
        current = Selection(
            type = SelectionType.TextRange,
            pageIndex = pageIndex,
            textStart = safeStart,
            textEnd = safeEnd,
            bounds = bounds,
            canCopy = safeEnd > safeStart,
            canCut = safeEnd > safeStart,
            canDelete = safeEnd > safeStart
        )
    }

    fun moveSelection(deltaX: Float, deltaY: Float) {
        val b = current.bounds ?: return
        current = current.copy(bounds = b.copy(left = b.left + deltaX, right = b.right + deltaX, top = b.top + deltaY, bottom = b.bottom + deltaY))
    }
}
