package com.example.pdf_everything.core.document

data class Document(
    val id: String,
    val name: String,
    val pages: List<Page>
)

data class Page(
    val index: Int,
    val objects: List<PdfObject>
)

sealed class PdfObject {
    data class TextObject(val text: String, val x: Float, val y: Float) : PdfObject()
    data class ImageObject(val url: String, val x: Float, val y: Float) : PdfObject()
}
