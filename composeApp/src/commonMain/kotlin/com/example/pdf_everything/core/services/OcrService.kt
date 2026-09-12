package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.DocumentSource

data class OcrResult(
    val extractedText: String,
    val confidence: Float,
    val language: String
)

class OcrService {

    suspend fun performOcr(
        source: DocumentSource,
        pageIndices: List<Int>? = null,
        language: String = "eng"
    ): OcrResult {
        return OcrResult(
            extractedText = "Extracted OCR text content from document page(s).",
            confidence = 0.95f,
            language = language
        )
    }
}
