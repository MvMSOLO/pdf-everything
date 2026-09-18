package com.example.pdf_everything.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentModelContractTest {
    @Test
    fun documentExposesCanonicalSourceAndCounts() {
        val document = Document(
            id = "doc-1",
            name = "example.pdf",
            source = DocumentSource.FilePath("/tmp/example.pdf"),
            pages = listOf(Page("p-1", 3, BoxSet(RectF(0f, 0f, 100f, 100f))))
        )
        assertEquals("doc-1", document.documentId)
        assertEquals("/tmp/example.pdf", document.sourcePath)
        assertEquals(null, document.sourceUri)
        assertEquals(1, document.pageCount)
        assertTrue(document.validate().any { "Index is 3" in it })
    }

    @Test
    fun normalizeRepairsPageAndAnnotationOwnership() {
        val document = Document(
            id = "doc-2",
            name = "normalized.pdf",
            pages = listOf(Page("p-a", 8, BoxSet(RectF(0f, 0f, 200f, 200f)), annotations = listOf(PdfAnnotation("a", 8, AnnotationType.Note, RectF(10f, 10f, 30f, 30f)))))
        )
        val normalized = DocumentModelService.normalize(document)
        assertEquals(0, normalized.pages.single().index)
        assertEquals(0, normalized.pages.single().annotations.single().pageIndex)
        assertTrue(normalized.validate().isEmpty())
    }

    @Test
    fun unknownObjectIsExplicitlyNonEditableAndPreservable() {
        val unknown = PdfObject.UnknownObject(
            id = "obj-x",
            bounds = RectF(0f, 0f, 20f, 20f),
            nativeType = "Pattern",
            preservationKey = "7:0",
            rawObjectReference = "7 0 R"
        )
        assertEquals(PdfObjectEditability.UNSUPPORTED, unknown.editability)
        assertTrue(!unknown.editable)
        assertEquals("7 0 R", unknown.rawObjectReference)
    }

    @Test
    fun objectModelCarriesSelectionResourcesAndOriginalIdentity() {
        val image = PdfObject.ImageObject(
            id = "img-1",
            source = "assets/image.png",
            bounds = RectF(10f, 20f, 110f, 120f),
            zOrder = 7,
            opacity = 0.75f,
            rotation = 15f,
            resourceRef = "Im1",
            resourceReferences = listOf(PdfResourceReference("Im1", "XObject", "Im1")),
            originalIdentity = "12:0",
            selection = ObjectSelectionState(selected = true, active = true)
        )
        assertEquals(7, image.zOrder)
        assertEquals(0.75f, image.opacity)
        assertEquals(15f, image.rotation)
        assertEquals("Im1", image.resourceRef)
        assertEquals("12:0", image.originalIdentity)
        assertTrue(image.selection.selected)
    }
}
