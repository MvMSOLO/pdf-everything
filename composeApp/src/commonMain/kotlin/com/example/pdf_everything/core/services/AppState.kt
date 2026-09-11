package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.history.HistoryManager
import com.example.pdf_everything.core.recovery.RecoveryService

/**
 * Application-wide state — single source of truth for the current
 * [Document], [PdfEngine], [CommandDispatcher], [HistoryManager],
 * [DocumentFileService] and [RecoveryService].
 *
 * Created once at app start and passed down through the Compose tree.
 */
class AppState(
    val pdfEngine: PdfEngine,
    val commandDispatcher: CommandDispatcher,
    val historyManager: HistoryManager,
    val documentFileService: DocumentFileService,
    val recoveryService: RecoveryService,
) {
    /** Currently open document – null when nothing is loaded. */
    var currentDocument: Document? = null
        private set

    /** Open a new document, replacing whatever was previously open. */
    fun openDocument(doc: Document) {
        currentDocument = doc
        historyManager.clear()
    }

    /** Update the current document in-place (e.g. after undo/redo).
     *  Unlike openDocument this does NOT clear the history stack.
     */
    fun updateDocument(doc: Document) {
        currentDocument = doc
    }

    /** Close the current document. */
    fun closeDocument() {
        currentDocument = null
        historyManager.clear()
    }
}
