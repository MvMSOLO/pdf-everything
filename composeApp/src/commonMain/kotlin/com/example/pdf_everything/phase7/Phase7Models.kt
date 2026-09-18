package com.example.pdf_everything.phase7

import com.example.pdf_everything.core.document.DocumentSource

/**
 * OS-level PDF association state. This deliberately distinguishes registration
 * from being the current default: Windows controls the default-user choice.
 */
enum class DefaultPdfAssociationStatus {
    NOT_REGISTERED,
    REGISTERED_NOT_DEFAULT,
    DEFAULT_APP,
    UNKNOWN,
    UNSUPPORTED
}

data class OpenRequest(
    val source: DocumentSource,
    val origin: OpenOrigin,
    val canPersistAccess: Boolean = false,
    val wasCopiedToWorkspace: Boolean = false
)

enum class OpenOrigin {
    FILE_DIALOG,
    DRAG_AND_DROP,
    RECENT_FILE,
    STARTUP_ARGUMENT,
    WINDOWS_ASSOCIATION,
    ANDROID_DOCUMENT_PICKER,
    ANDROID_VIEW_INTENT,
    ANDROID_SEND_INTENT
}

internal fun isPdfSource(source: DocumentSource): Boolean = when (source) {
    is DocumentSource.FilePath -> source.path.endsWith(".pdf", ignoreCase = true)
    is DocumentSource.ContentUri -> true
}
