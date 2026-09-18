package com.example.pdf_everything

import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.phase6.Phase6OperationResult
import com.example.pdf_everything.phase6.PrintRequest
import com.example.pdf_everything.phase6.SaveAsRequest

import com.example.pdf_everything.phase7.DefaultPdfAssociationStatus

expect val isDesktop: Boolean

expect fun requestPdfOpen(onSelected: (DocumentSource) -> Unit)

expect fun requestImageOpen(onSelected: (DocumentSource) -> Unit)

expect fun requestPdfSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit)

expect fun printPdfDocument(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit)

expect fun defaultPdfAssociationStatus(): DefaultPdfAssociationStatus
expect fun openWindowsDefaultAppSettings(): Boolean
expect fun hasSeenDesktopDefaultAppPrompt(): Boolean
expect fun markDesktopDefaultAppPromptSeen()
