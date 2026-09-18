package com.example.pdf_everything.core.clipboard

import com.example.pdf_everything.core.document.PdfObject
import com.example.pdf_everything.core.document.DocumentSource
import kotlinx.serialization.Serializable

@Serializable
data class ObjectClipboard(
    val objects: List<PdfObject> = emptyList(),
    val sourcePageIndex: Int = 0
)

data class ClipboardSnapshot(
    val text: String? = null,
    val image: ByteArray? = null,
    val objectPayload: ObjectClipboard? = null
)

expect object PlatformClipboard {
    fun read(): ClipboardSnapshot
    fun writeText(text: String)
    fun writeObject(payload: ObjectClipboard)
    fun writeImage(png: ByteArray)
    fun materializeImage(png: ByteArray): DocumentSource?
}

class InternalClipboard {
    private var payload: ObjectClipboard? = null
    fun put(payload: ObjectClipboard) { this.payload = payload }
    fun get(): ObjectClipboard? = payload
    fun clear() { payload = null }
}
