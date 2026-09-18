package com.example.pdf_everything.phase6

import com.example.pdf_everything.core.document.DocumentSource
import kotlinx.serialization.Serializable

@Serializable
data class SourceFingerprint(
    val source: DocumentSource,
    val value: String,
    val capturedAtEpochMs: Long
)

@Serializable
enum class PrintPageParity { ALL, ODD, EVEN }

@Serializable
enum class PrintPaperSize { A4, LETTER, LEGAL }

@Serializable
enum class PrintScaling { FIT_TO_PRINTABLE_AREA, ACTUAL_SIZE, CUSTOM }

@Serializable
data class PageRange(val first: Int, val last: Int) {
    init { require(first >= 0 && last >= first) }
    fun contains(page: Int): Boolean = page in first..last
}

@Serializable
data class PrintRequest(
    val ranges: List<PageRange> = emptyList(),
    val copies: Int = 1,
    val paperSize: PrintPaperSize = PrintPaperSize.A4,
    val landscape: Boolean = false,
    val mediaName: String? = null,
    val scaling: PrintScaling = PrintScaling.FIT_TO_PRINTABLE_AREA,
    val customScalePercent: Int = 100,
    val parity: PrintPageParity = PrintPageParity.ALL,
    val collate: Boolean = true,
    val color: Boolean = true
) {
    fun selectedPages(pageCount: Int): List<Int> {
        val base = if (ranges.isEmpty()) (0 until pageCount).toList() else ranges.flatMap { range ->
            (range.first..range.last).filter { it in 0 until pageCount }
        }.distinct()
        return when (parity) {
            PrintPageParity.ALL -> base
            PrintPageParity.ODD -> base.filter { ((it + 1) and 1) == 1 }
            PrintPageParity.EVEN -> base.filter { ((it + 1) and 1) == 0 }
        }
    }
}

@Serializable
data class RecoveryEntry(
    val id: String,
    val documentName: String,
    val source: DocumentSource?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val payloadPath: String,
    val sourceFingerprint: String? = null
)

@Serializable
data class SaveAsRequest(val suggestedName: String, val currentPath: String? = null)

@Serializable
enum class SaveFailureReason { READ_ONLY, EXTERNAL_CONFLICT, VALIDATION, IO, UNSUPPORTED, UNKNOWN }

data class Phase6OperationResult(
    val ok: Boolean,
    val message: String,
    val failureReason: SaveFailureReason? = null
)
