package com.example.pdf_everything.core.services

/**
 * Platform factory for obtaining a [PdfEngine] instance.
 *
 * Desktop actual returns [PdfBoxAdapter]; Android actual throws
 * [NotImplementedError] for Phase 1 (PdfiumAndroid comes in Phase 2).
 */
expect fun createPdfEngine(): PdfEngine
