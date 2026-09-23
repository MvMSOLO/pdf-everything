package com.example.pdf_everything

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfSaveReport

/** Platform boundary for filesystem identity, Save As dialogs, printing, and recovery storage. */
expect object Phase6Platform {
    fun fingerprint(source: DocumentSource): SourceFingerprint?
    fun requestSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit)
    fun writeRecovery(entry: RecoveryEntry, payload: String): Boolean
    fun listRecoveries(): List<RecoveryEntry>
    fun readRecovery(entry: RecoveryEntry): String?
    fun deleteRecovery(entry: RecoveryEntry): Boolean
    fun persist(engine: PdfEngine, document: Document, target: String, incremental: Boolean): PdfSaveReport
    fun print(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit)
}
