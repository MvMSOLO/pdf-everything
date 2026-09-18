package com.example.pdf_everything

import com.example.pdf_everything.core.document.DocumentSource
import kotlinx.serialization.Serializable

@Serializable
data class RecentFile(
    val name: String,
    val source: DocumentSource,
    val openedAtEpochMs: Long,
    val lastPage: Int = 0,
    val lastZoom: Float = 1f,
    val fingerprint: String? = null
)

expect fun loadRecentFiles(): List<RecentFile>
expect fun rememberRecentFile(file: RecentFile)
expect fun clearRecentFiles()

expect fun consumeStartupPdfSource(): DocumentSource?
expect fun installDesktopFileDrop(onSelected: (DocumentSource) -> Unit)
