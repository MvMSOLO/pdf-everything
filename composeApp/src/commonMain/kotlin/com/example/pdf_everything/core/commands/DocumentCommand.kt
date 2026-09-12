package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.*

// ─────────────────────────────────────────────────────────────
//  Core Command interface
// ─────────────────────────────────────────────────────────────

/**
 * Every mutating action on the document model must be a DocumentCommand.
 *
 * Lifecycle: create → canExecute() → execute() → (undo()/redo() as needed)
 *
 * Commands are the ONLY way the document model may be mutated from the UI layer.
 * This guarantees undo/redo works and that every change can be tracked.
 */
interface DocumentCommand {
    val commandId: String
    val description: String

    fun canExecute(): Boolean
    fun execute(document: Document): Document
    fun undo(document: Document): Document
    fun redo(document: Document): Document = execute(document)
}

// ─────────────────────────────────────────────────────────────
//  Text commands
// ─────────────────────────────────────────────────────────────

data class InsertTextCommand(
    override val commandId: String,
    override val description: String = "Insert text",
    val pageId: String,
    val textObject: TextObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && textObject.text.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectAdded(pageId, textObject)
    override fun undo(document: Document): Document = document.withObjectRemoved(pageId, textObject.objectId)
}

data class DeleteObjectCommand(
    override val commandId: String,
    override val description: String = "Delete object",
    val pageId: String,
    val objectId: String,
    private val snapshot: PdfObject? = null  // store before delete for undo
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectRemoved(pageId, objectId)
    override fun undo(document: Document): Document {
        if (snapshot == null) return document
        return document.withObjectAdded(pageId, snapshot)
    }
}

data class MoveObjectCommand(
    override val commandId: String,
    override val description: String = "Move object",
    val pageId: String,
    val objectId: String,
    val oldPosition: PdfPoint,
    val newPosition: PdfPoint
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        val dx = newPosition.x - oldPosition.x
        val dy = newPosition.y - oldPosition.y
        val moved = obj.withBoundingBox(obj.boundingBox.offset(dx, dy))
        return document.withObjectUpdated(pageId, moved)
    }
    override fun undo(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        val dx = oldPosition.x - newPosition.x
        val dy = oldPosition.y - newPosition.y
        val moved = obj.withBoundingBox(obj.boundingBox.offset(dx, dy))
        return document.withObjectUpdated(pageId, moved)
    }
}

data class ResizeObjectCommand(
    override val commandId: String,
    override val description: String = "Resize object",
    val pageId: String,
    val objectId: String,
    val oldBounds: PdfRect,
    val newBounds: PdfRect
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty() && newBounds.area > 0
    override fun execute(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        return document.withObjectUpdated(pageId, obj.withBoundingBox(newBounds))
    }
    override fun undo(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        return document.withObjectUpdated(pageId, obj.withBoundingBox(oldBounds))
    }
}

data class RotateObjectCommand(
    override val commandId: String,
    override val description: String = "Rotate object",
    val pageId: String,
    val objectId: String,
    val oldRotation: Float,
    val newRotation: Float
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        // We use a rotation transform around the center of the bounding box
        val center = PdfPoint(obj.boundingBox.centerX, obj.boundingBox.centerY)
        val rotMatrix = PdfMatrix.translation(-center.x, -center.y)
            .multiply(PdfMatrix.rotation(newRotation - oldRotation))
            .multiply(PdfMatrix.translation(center.x, center.y))
        return document.withObjectUpdated(pageId, obj.withTransform(rotMatrix).let {
            when (it) {
                is TextObject -> it.copy(rotation = newRotation)
                is ImageObject -> it.copy(rotation = newRotation)
                is ShapeObject -> it.copy(rotation = newRotation)
                else -> it
            }
        })
    }
    override fun undo(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val obj = page.allObjects.find { it.objectId == objectId } ?: return document
        val center = PdfPoint(obj.boundingBox.centerX, obj.boundingBox.centerY)
        val rotMatrix = PdfMatrix.translation(-center.x, -center.y)
            .multiply(PdfMatrix.rotation(oldRotation - newRotation))
            .multiply(PdfMatrix.translation(center.x, center.y))
        return document.withObjectUpdated(pageId, obj.withTransform(rotMatrix).let {
            when (it) {
                is TextObject -> it.copy(rotation = oldRotation)
                is ImageObject -> it.copy(rotation = oldRotation)
                is ShapeObject -> it.copy(rotation = oldRotation)
                else -> it
            }
        })
    }
}

data class InsertImageCommand(
    override val commandId: String,
    override val description: String = "Insert image",
    val pageId: String,
    val imageObject: ImageObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectAdded(pageId, imageObject)
    override fun undo(document: Document): Document = document.withObjectRemoved(pageId, imageObject.objectId)
}

// ─────────────────────────────────────────────────────────────
//  Page commands
// ─────────────────────────────────────────────────────────────

data class DeletePageCommand(
    override val commandId: String,
    override val description: String = "Delete page",
    val pageId: String,
    val pageIndex: Int = -1,  // PDFBox uses index-based access
    private val pageSnapshot: Page? = null  // for undo
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty()
    override fun execute(document: Document): Document = document.withPageRemoved(pageId)
    override fun undo(document: Document): Document {
        if (pageSnapshot == null) return document
        return document.withPageAdded(pageSnapshot)
    }
}

data class DuplicatePageCommand(
    override val commandId: String,
    override val description: String = "Duplicate page",
    val pageId: String,
    val newPageId: String
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && newPageId.isNotEmpty()
    override fun execute(document: Document): Document {
        val page = document.pages.find { it.pageId == pageId } ?: return document
        val duplicate = page.copy(pageId = newPageId, index = document.pages.size)
        return document.withPageAdded(duplicate)
    }
    override fun undo(document: Document): Document = document.withPageRemoved(newPageId)
}

data class MovePageCommand(
    override val commandId: String,
    override val description: String = "Move page",
    val fromIndex: Int,
    val toIndex: Int
) : DocumentCommand {
    override fun canExecute(): Boolean = fromIndex != toIndex
    override fun execute(document: Document): Document = document.withPageReordered(fromIndex, toIndex)
    override fun undo(document: Document): Document = document.withPageReordered(toIndex, fromIndex)
}

data class RotatePageCommand(
    override val commandId: String,
    override val description: String = "Rotate page",
    val pageId: String,
    val pageIndex: Int = -1,  // PDFBox uses index-based access
    val oldRotation: PageRotation,
    val newRotation: PageRotation,
    val rotation: PageRotation = newRotation  // convenience: the rotation to apply
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && oldRotation != newRotation
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) { it.withRotation(newRotation) }
    override fun undo(document: Document): Document =
        document.withPageUpdated(pageId) { it.withRotation(oldRotation) }
}

data class CropPageCommand(
    override val commandId: String,
    override val description: String = "Crop page",
    val pageId: String,
    val pageIndex: Int = -1,  // PDFBox uses index-based access
    val previousCropBox: PdfRect?,
    val nextCropBox: PdfRect,
    val cropBox: PdfRect = nextCropBox  // convenience alias
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && nextCropBox.area > 0
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) { it.withCropBox(nextCropBox) }
    override fun undo(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(boxes = it.boxes.copy(cropBox = previousCropBox))
        }
}

// ─────────────────────────────────────────────────────────────
//  Document-level commands
// ─────────────────────────────────────────────────────────────

data class SplitDocumentCommand(
    override val commandId: String,
    override val description: String = "Split document",
    val splitAfterPage: Int
    // Result is two new documents; the original is not mutated directly.
    // This command is handled at a higher level that creates two new Document instances.
) : DocumentCommand {
    override fun canExecute(): Boolean = splitAfterPage >= 0
    override fun execute(document: Document): Document = document  // no-op on original; handled at service level
    override fun undo(document: Document): Document = document      // handled at service level
}

data class MergeDocumentsCommand(
    override val commandId: String,
    override val description: String = "Merge documents",
    val sourceDocumentIds: List<String>
) : DocumentCommand {
    override fun canExecute(): Boolean = sourceDocumentIds.size >= 2
    override fun execute(document: Document): Document = document  // handled at service level
    override fun undo(document: Document): Document = document      // handled at service level
}

// ─────────────────────────────────────────────────────────────
//  Annotation commands
// ─────────────────────────────────────────────────────────────

data class AddAnnotationCommand(
    override val commandId: String,
    override val description: String = "Add annotation",
    val pageId: String,
    val pageIndex: Int = -1,
    val annotation: AnnotationObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty()
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations + annotation)
        }
    override fun undo(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.filter { a -> a.objectId != annotation.objectId })
        }
}

data class EditAnnotationCommand(
    override val commandId: String,
    override val description: String = "Edit annotation",
    val pageId: String,
    val annotationId: String,
    val oldAnnotation: AnnotationObject,
    val newAnnotation: AnnotationObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && annotationId.isNotEmpty()
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.map { a ->
                if (a.objectId == annotationId) newAnnotation else a
            })
        }
    override fun undo(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.map { a ->
                if (a.objectId == annotationId) oldAnnotation else a
            })
        }
}

data class DeleteAnnotationCommand(
    override val commandId: String,
    override val description: String = "Delete annotation",
    val pageId: String,
    val pageIndex: Int = -1,  // PDFBox uses index-based access
    val annotationId: String,
    val objectId: String = annotationId,  // alias for engine access
    private val snapshot: AnnotationObject? = null
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && annotationId.isNotEmpty()
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.filter { a -> a.objectId != annotationId })
        }
    override fun undo(document: Document): Document {
        if (snapshot == null) return document
        return document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations + snapshot)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Metadata command
// ─────────────────────────────────────────────────────────────

data class EditMetadataCommand(
    override val commandId: String,
    override val description: String = "Edit metadata",
    val oldMetadata: DocumentMetadata,
    val newMetadata: DocumentMetadata
) : DocumentCommand {
    override fun canExecute(): Boolean = true
    override fun execute(document: Document): Document = document.copy(metadata = newMetadata).markDirty()
    override fun undo(document: Document): Document = document.copy(metadata = oldMetadata).markDirty()
}

// ─────────────────────────────────────────────────────────────
//  Additional commands needed by PdfBoxAdapter
// ─────────────────────────────────────────────────────────────

data class ReorderPageCommand(
    override val commandId: String,
    override val description: String = "Reorder page",
    val fromIndex: Int,
    val toIndex: Int
) : DocumentCommand {
    override fun canExecute(): Boolean = fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0
    override fun execute(document: Document): Document = document.withPageReordered(fromIndex, toIndex)
    override fun undo(document: Document): Document = document.withPageReordered(toIndex, fromIndex)
}

data class InsertPageCommand(
    override val commandId: String,
    override val description: String = "Insert blank page",
    val afterPageIndex: Int,  // -1 = append at end
    val newPageId: String = "p_new_${System.currentTimeMillis()}"
) : DocumentCommand {
    override fun canExecute(): Boolean = true
    override fun execute(document: Document): Document {
        val newPage = Page(
            pageId = newPageId,
            index = if (afterPageIndex < 0) document.pages.size else afterPageIndex + 1,
            boxes = PageBoxes(mediaBox = PdfRect(0f, 0f, 612f, 792f)),
            rotation = PageRotation.ROTATION_0
        )
        return document.withPageAdded(newPage)
    }
    override fun undo(document: Document): Document = document.withPageRemoved(newPageId)
}

data class SetFormFieldValueCommand(
    override val commandId: String,
    override val description: String = "Set form field value",
    val fieldName: String,
    val oldValue: String?,
    val newValue: String
) : DocumentCommand {
    override fun canExecute(): Boolean = fieldName.isNotEmpty()
    override fun execute(document: Document): Document {
        val currentModel = document.formModel ?: FormModel()
        return document.copy(formModel = currentModel.withFieldUpdated(fieldName, newValue)).markDirty()
    }
    override fun undo(document: Document): Document {
        val currentModel = document.formModel ?: FormModel()
        return document.copy(formModel = currentModel.withFieldUpdated(fieldName, oldValue ?: "")).markDirty()
    }
}

data class FlattenFormCommand(
    override val commandId: String,
    override val description: String = "Flatten form fields",
) : DocumentCommand {
    override fun canExecute(): Boolean = true
    override fun execute(document: Document): Document =
        document.copy(formModel = FormModel(fields = emptyList(), hasAcroForm = false)).markDirty()
    override fun undo(document: Document): Document = document  // cannot un-flatten
}

data class UpdateAnnotationCommand(
    override val commandId: String,
    override val description: String = "Update annotation",
    val pageId: String,
    val pageIndex: Int = -1,
    val objectId: String,
    val oldAnnotation: AnnotationObject,
    val newAnnotation: AnnotationObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.map { a ->
                if (a.objectId == objectId) newAnnotation else a
            })
        }
    override fun undo(document: Document): Document =
        document.withPageUpdated(pageId) {
            it.copy(annotations = it.annotations.map { a ->
                if (a.objectId == objectId) oldAnnotation else a
            })
        }
}

// Phase 2 text editing stubs (engine throws UnsupportedOperationException)
data class AddTextCommand(
    override val commandId: String,
    override val description: String = "Add text",
    val pageId: String,
    val textObject: TextObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && textObject.text.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectAdded(pageId, textObject)
    override fun undo(document: Document): Document = document.withObjectRemoved(pageId, textObject.objectId)
}

data class DeleteTextCommand(
    override val commandId: String,
    override val description: String = "Delete text",
    val pageId: String,
    val objectId: String
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectRemoved(pageId, objectId)
    override fun undo(document: Document): Document = document  // requires snapshot
}

data class UpdateTextCommand(
    override val commandId: String,
    override val description: String = "Update text",
    val pageId: String,
    val objectId: String,
    val newText: TextObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectUpdated(pageId, newText)
    override fun undo(document: Document): Document = document  // requires snapshot
}

data class AddImageCommand(
    override val commandId: String,
    override val description: String = "Add image",
    val pageId: String,
    val imageObject: ImageObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectAdded(pageId, imageObject)
    override fun undo(document: Document): Document = document.withObjectRemoved(pageId, imageObject.objectId)
}

data class DeleteImageCommand(
    override val commandId: String,
    override val description: String = "Delete image",
    val pageId: String,
    val objectId: String
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectRemoved(pageId, objectId)
    override fun undo(document: Document): Document = document
}

data class UpdateImageCommand(
    override val commandId: String,
    override val description: String = "Update image",
    val pageId: String,
    val objectId: String,
    val newImage: ImageObject
) : DocumentCommand {
    override fun canExecute(): Boolean = pageId.isNotEmpty() && objectId.isNotEmpty()
    override fun execute(document: Document): Document = document.withObjectUpdated(pageId, newImage)
    override fun undo(document: Document): Document = document
}

data class OptimizeDocumentCommand(
    override val commandId: String,
    override val description: String = "Optimize document",
    val compressImages: Boolean = true,
    val imageQuality: Int = 75,
    val removeUnusedObjects: Boolean = true,
    val flattenForms: Boolean = false
) : DocumentCommand {
    override fun canExecute(): Boolean = true
    override fun execute(document: Document): Document = document.markDirty()  // actual work done in engine
    override fun undo(document: Document): Document = document  // cannot undo optimization
}
