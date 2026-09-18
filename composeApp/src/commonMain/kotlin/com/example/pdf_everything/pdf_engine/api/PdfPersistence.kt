package com.example.pdf_everything.pdf_engine.api

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource

/**
 * End-to-end persistence coordinator. It deliberately refuses a save when the
 * in-memory editor contains operations the selected native adapter cannot map.
 */
class PdfPersistenceCoordinator(private val engine: PdfEngine) {
    fun persist(document: Document, targetPath: String, incremental: Boolean = false): PdfSaveReport {
        val source = document.source ?: throw IllegalStateException("Document has no source")
        val current = engine.source()
        if (current != null && current != source) {
            throw IllegalStateException("Engine source and editor source differ; reopen the document before saving")
        }
        engine.applyDocumentStructure(document)
        return if (incremental) engine.saveIncremental(targetPath) else engine.save(targetPath)
    }

    fun persistAndReopen(document: Document, targetPath: String): PdfDocumentInfo {
        persist(document, targetPath, incremental = false)
        engine.close()
        return engine.open(DocumentSource.FilePath(targetPath))
    }
}
