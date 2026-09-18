package com.example.pdf_everything

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.phase6.*
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.PrinterJob
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.Chromaticity
import javax.print.attribute.standard.MediaSizeName
import javax.print.attribute.standard.SheetCollate

actual object Phase6Platform {
    private val recoveryRoot = File(System.getProperty("user.home"), ".pdf-everything/recovery")

    actual fun fingerprint(source: DocumentSource): SourceFingerprint? = when (source) {
        is DocumentSource.FilePath -> fingerprintFile(File(source.path))
        is DocumentSource.ContentUri -> null
    }

    private fun fingerprintFile(file: File): SourceFingerprint? {
        if (!file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val first = ByteArray(16 * 1024)
        val last = ByteArray(16 * 1024)
        FileInputStream(file).use { input ->
            var read = 0
            while (read < first.size) {
                val n = input.read(first, read, first.size - read)
                if (n <= 0) break
                read += n
            }
            digest.update(first, 0, read)
        }
        if (file.length() > last.size) FileInputStream(file).use { input ->
            var skipped = 0L
            while (skipped < file.length() - last.size) {
                val n = input.skip(file.length() - last.size - skipped)
                if (n <= 0L) break
                skipped += n
            }
            var read = 0
            while (read < last.size) {
                val n = input.read(last, read, last.size - read)
                if (n <= 0) break
                read += n
            }
            digest.update(last, 0, read)
        } else digest.update(first, 0, minOf(file.length().toInt(), first.size))
        val value = "${file.length()}:${file.lastModified()}:${digest.digest().toHex()}"
        return SourceFingerprint(DocumentSource.FilePath(file.absolutePath), value, System.currentTimeMillis())
    }

    actual fun persist(engine: PdfEngine, document: Document, target: String, incremental: Boolean) =
        if (incremental) engine.saveIncremental(target) else engine.save(target)

    actual fun requestSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Save PDF As", FileDialog.SAVE).apply {
            file = request.suggestedName.ifBlank { "document.pdf" }
            isVisible = true
        }
        val selected = if (dialog.file != null && dialog.directory != null) {
            var name = dialog.file
            if (!name.endsWith(".pdf", true)) name += ".pdf"
            File(dialog.directory, name).absolutePath
        } else null
        onSelected(selected)
    }

    actual fun writeRecovery(entry: RecoveryEntry, payload: String): Boolean = runCatching {
        recoveryRoot.mkdirs()
        val target = File(recoveryRoot, "${entry.id}.json")
        FileOutputStream(target).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val meta = File(recoveryRoot, "${entry.id}.meta")
        meta.writeText(listOf(entry.id, entry.documentName.replace("\n", " "), entry.source?.toString().orEmpty(), entry.createdAtEpochMs, entry.updatedAtEpochMs, entry.payloadPath, entry.sourceFingerprint.orEmpty()).joinToString("\n"))
    }.isSuccess

    actual fun listRecoveries(): List<RecoveryEntry> = runCatching {
        if (!recoveryRoot.isDirectory) return emptyList()
        recoveryRoot.listFiles { f -> f.extension == "meta" }.orEmpty().mapNotNull { meta ->
            val p = meta.readLines()
            if (p.size < 6) return@mapNotNull null
            RecoveryEntry(p[0], p[1], p[2].takeIf { it.isNotBlank() }?.let { DocumentSource.FilePath(it) }, p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L, p[5], p.getOrNull(6)?.takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    actual fun readRecovery(entry: RecoveryEntry): String? = runCatching { File(recoveryRoot, "${entry.id}.json").takeIf(File::isFile)?.readText() }.getOrNull()

    actual fun deleteRecovery(entry: RecoveryEntry): Boolean = runCatching {
        val a = File(recoveryRoot, "${entry.id}.json").delete()
        val b = File(recoveryRoot, "${entry.id}.meta").delete()
        a || b
    }.getOrDefault(false)

    actual fun print(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit) {
        Thread {
            runCatching {
                if (GraphicsEnvironment.isHeadless()) error("Printing is unavailable in headless mode")
                require(document.permissions.canPrint) { "Printing is disabled by PDF permissions" }
                val pages = request.selectedPages(document.pageCount)
                require(pages.isNotEmpty()) { "No pages selected for printing" }
                val job = PrinterJob.getPrinterJob()
                job.jobName = document.name
                job.copies = request.copies.coerceIn(1, 999)
                val media = when (request.paperSize) {
                    PrintPaperSize.A4 -> java.awt.print.Paper().apply { setSize(595.276f, 841.89f); setImageableArea(0f, 0f, 595.276, 841.89) }
                    PrintPaperSize.LETTER -> java.awt.print.Paper().apply { setSize(612f, 792f); setImageableArea(0.0, 0.0, 612.0, 792.0) }
                    PrintPaperSize.LEGAL -> java.awt.print.Paper().apply { setSize(612f, 1008f); setImageableArea(0.0, 0.0, 612.0, 1008.0) }
                }
                val default = job.defaultPage()
                val format = default.apply {
                    paper = media
                    orientation = if (request.landscape) PageFormat.LANDSCAPE else PageFormat.PORTRAIT
                }
                job.setPrintable({ graphics, pageFormat, printIndex ->
                    if (printIndex !in pages.indices) return@setPrintable java.awt.print.Printable.NO_SUCH_PAGE
                    val sourcePage = pages[printIndex]
                    val rendered = engine.renderPage(sourcePage, com.example.pdf_everything.pdf_engine.api.RenderViewport(widthPx = 1800, scale = 1f, cacheKey = "print-$sourcePage"))
                    val image = ImageIO.read(rendered.png.inputStream()) ?: return@setPrintable java.awt.print.Printable.NO_SUCH_PAGE
                    val g = graphics as Graphics2D
                    val imageableW = pageFormat.imageableWidth
                    val imageableH = pageFormat.imageableHeight
                    val scaleBase = when (request.scaling) {
                        PrintScaling.FIT_TO_PRINTABLE_AREA -> minOf(imageableW / image.width, imageableH / image.height)
                        PrintScaling.ACTUAL_SIZE -> 72.0 / 150.0
                        PrintScaling.CUSTOM -> (72.0 / 150.0) * (request.customScalePercent.coerceIn(10, 400) / 100.0)
                    }
                    val scale = scaleBase.coerceAtLeast(0.01)
                    val w = image.width * scale
                    val h = image.height * scale
                    val x = pageFormat.imageableX + (imageableW - w) / 2.0
                    val y = pageFormat.imageableY + (imageableH - h) / 2.0
                    g.drawImage(image, x.toInt(), y.toInt(), w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), null)
                    java.awt.print.Printable.PAGE_EXISTS
                }, format)
                val attributes = HashPrintRequestAttributeSet()
                attributes.add(when (request.paperSize) {
                    PrintPaperSize.A4 -> MediaSizeName.ISO_A4
                    PrintPaperSize.LETTER -> MediaSizeName.NA_LETTER
                    PrintPaperSize.LEGAL -> MediaSizeName.NA_LEGAL
                })
                attributes.add(if (request.color) Chromaticity.COLOR else Chromaticity.MONOCHROME)
                attributes.add(if (request.collate) SheetCollate.COLLATED else SheetCollate.UNCOLLATED)
                if (!job.printDialog(attributes)) return@runCatching
                job.print(attributes)
            }.onSuccess { onFinished(Phase6OperationResult(true, "Print job submitted successfully")) }
             .onFailure { onFinished(Phase6OperationResult(false, "Print failed: ${it.message}", SaveFailureReason.IO)) }
        }.apply { isDaemon = true; name = "pdf-everything-print" }.start()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
