package com.example.pdf_everything.core.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.print.PrinterJob
import java.awt.print.Book
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterException
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.Paper

/**
 * Real desktop printing per spec §18.
 *
 * Uses java.awt.print.PrinterJob + PDFBox's PDFRenderer to render pages
 * at the printer's resolution.  Supports:
 *   - printer selection via native dialog
 *   - page range
 *   - copies
 *   - orientation / paper size
 *   - scaling (fit-to-printable-area / actual-size / custom)
 *   - color/grayscale from printer capabilities
 */
class DesktopPrintService(
    private val pdfBoxAdapter: PdfBoxAdapter
) {

    /** Print settings passed from the UI. */
    data class PrintConfig(
        val documentId: String,
        val pageRange: IntRange? = null,       // null = all pages
        val copies: Int = 1,
        val orientation: PrintOrientation = PrintOrientation.AUTO,
        val scaling: PrintScaling = PrintScaling.FIT_TO_PRINTABLE,
        val customScale: Float = 1.0f,          // only used when scaling = CUSTOM
        val colorMode: PrintColorMode = PrintColorMode.AUTO
    )

    enum class PrintOrientation { AUTO, PORTRAIT, LANDSCAPE }
    enum class PrintScaling { FIT_TO_PRINTABLE, ACTUAL_SIZE, CUSTOM }
    enum class PrintColorMode { AUTO, COLOR, GRAYSCALE }

    /**
     * Show the native print dialog and print the document.
     *
     * Returns true if printing was accepted, false if user cancelled,
     * or throws on print failure.
     */
    fun print(config: PrintConfig): Boolean {
        val pdDoc = pdfBoxAdapter.getPDDocument(config.documentId)
            ?: throw EngineException(
                EngineError.IO_ERROR, "Document not open: ${config.documentId}"
            )
        val renderer = pdfBoxAdapter.getPDFRenderer(config.documentId)
            ?: throw EngineException(
                EngineError.IO_ERROR, "Renderer not available: ${config.documentId}"
            )

        val job = PrinterJob.getPrinterJob()
        job.jobName = "PDF Everything — ${config.documentId}"

        // Build the Book of printable pages
        val book = Book()
        val totalPages = pdDoc.numberOfPages
        val range = config.pageRange ?: (0 until totalPages)

        for (pageIndex in range) {
            if (pageIndex < 0 || pageIndex >= totalPages) continue
            val page = pdDoc.getPage(pageIndex)
            val pageFormat = derivePageFormat(page, config)
            val printable = PdfPagePrintable(renderer, pageIndex, config)
            book.append(printable, pageFormat, config.copies)
        }

        job.setPageable(book)

        // Show native print dialog — returns true if user confirms
        if (!job.printDialog()) return false

        try {
            job.print()
        } catch (e: PrinterException) {
            throw EngineException(EngineError.IO_ERROR, "Print failed: ${e.message}", e)
        }

        return true
    }

    /** Derive a PageFormat from the PDF page dimensions and print config. */
    private fun derivePageFormat(
        page: org.apache.pdfbox.pdmodel.PDPage,
        config: PrintConfig
    ): PageFormat {
        val format = PageFormat()
        val paper = Paper()

        val mediaBox = page.mediaBox
        val pdfWidthPt = mediaBox.width   // 1 pt = 1/72 inch
        val pdfHeightPt = mediaBox.height

        // Convert PDF points to inches (72 pts/inch)
        val widthInch = pdfWidthPt / 72.0
        val heightInch = pdfHeightPt / 72.0

        // Set paper size in points
        paper.setSize(pdfWidthPt, pdfHeightPt)

        // Default imageable area (1/4 inch margins)
        val margin = 18.0  // 0.25 inch in points
        paper.setImageableArea(margin, margin,
            pdfWidthPt - 2 * margin,
            pdfHeightPt - 2 * margin)

        format.paper = paper

        // Orientation
        when (config.orientation) {
            PrintOrientation.PORTRAIT -> format.orientation = PageFormat.PORTRAIT
            PrintOrientation.LANDSCAPE -> format.orientation = PageFormat.LANDSCAPE
            PrintOrientation.AUTO -> {
                // Auto: landscape if page is wider than tall
                if (pdfWidthPt > pdfHeightPt) {
                    format.orientation = PageFormat.LANDSCAPE
                } else {
                    format.orientation = PageFormat.PORTRAIT
                }
            }
        }

        return format
    }

    /**
     * Printable wrapper that renders a single PDF page via PDFBox.
     */
    private class PdfPagePrintable(
        private val renderer: PDFRenderer,
        private val pageIndex: Int,
        private val config: PrintConfig
    ) : Printable {

        override fun print(graphics: Graphics, pageFormat: PageFormat, pIndex: Int): Int {
            if (pIndex != 0) return NO_SUCH_PAGE  // we only handle our assigned page

            val g2d = graphics as Graphics2D
            g2d.translate(pageFormat.imageableX, pageFormat.imageableY)

            val imageableW = pageFormat.imageableWidth
            val imageableH = pageFormat.imageableHeight

            // Determine DPI for rendering
            val printerRes = g2d.deviceConfiguration.transform.scaleX * 72.0
            val renderDpi = (if (printerRes > 0) printerRes else 300.0).toFloat().coerceIn(72f, 600f)

            // Render the PDF page at the target DPI
            val grayscale = config.colorMode == PrintColorMode.GRAYSCALE
            val bImage = renderer.renderImageWithDPI(pageIndex, renderDpi,
                if (grayscale) org.apache.pdfbox.rendering.ImageType.GRAY else org.apache.pdfbox.rendering.ImageType.RGB
            )

            // Scale the rendered image to fit the imageable area
            val scale = when (config.scaling) {
                PrintScaling.FIT_TO_PRINTABLE -> {
                    val sx = imageableW / bImage.width
                    val sy = imageableH / bImage.height
                    minOf(sx, sy)
                }
                PrintScaling.ACTUAL_SIZE -> 1.0
                PrintScaling.CUSTOM -> config.customScale.toDouble()
            }

            val drawW = bImage.width * scale
            val drawH = bImage.height * scale

            // Center on the imageable area
            val dx = (imageableW - drawW) / 2.0
            val dy = (imageableH - drawH) / 2.0

            g2d.drawImage(bImage, dx.toInt(), dy.toInt(), drawW.toInt(), drawH.toInt(), null)

            return PAGE_EXISTS
        }
    }
}
