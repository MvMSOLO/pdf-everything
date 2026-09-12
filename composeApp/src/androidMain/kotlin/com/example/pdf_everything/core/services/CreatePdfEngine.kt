package com.example.pdf_everything.core.services

actual fun createPdfEngine(): PdfEngine {
    return PdfiumAndroidAdapter()
}
