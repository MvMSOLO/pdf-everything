package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.commands.DocumentCommand
import com.example.pdf_everything.core.document.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageTree
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.PDFTextStripperByArea
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Apache PDFBox 2.0.33 backed PdfEngineAdapter for Desktop (JVM).
 *
 * Phase 1 covers: open, close, renderPage, getText, getMetadata,
 * getPageCount, getOutline, save, export(PNG/PDF/TEXT).
 *
 * Edit commands (doExecuteCommand, doAddObject, etc.) throw
 * UnsupportedOperationException for now — full editing in Phase 2+.
 */
class PdfBoxAdapter : PdfEngineAdapter() {

    // docId → PDDocument handle
    private val docs = mutableMapOf<String, PDDocument>()
    // docId → PDFRenderer
    private val renderers = mutableMapOf<String, PDFRenderer>()
    // docId → original file path (needed for incremental save)
    private val filePaths = mutableMapOf<String, String>()

    // ── Lifecycle hooks ─────────────────────────────────────────

    override suspend fun doInitialize() {
        // PDFBox has no global init; nothing to do
    }

    override suspend fun doShutdown() {
        docs.values.forEach { runCatching { it.close() } }
        docs.clear()
        renderers.clear()
        filePaths.clear()
    }

    override suspend fun detectCapabilities(): EngineCapabilities = EngineCapabilities(
        canEditText = true,
        canEditImages = true,
        canEditAnnotations = true,
        canEditForms = true,
        canReorderPages = true,
        canDeletePages = true,
        canInsertPages = true,
        canRotatePages = true,
        canFlattenForms = true,
        canIncrementalSave = true,
        supportedExportFormats = setOf(ExportFormat.PDF, ExportFormat.PNG, ExportFormat.TEXT)
    )

    // ── Document I/O ────────────────────────────────────────────

    override suspend fun doOpen(source: DocumentSource, password: String?): Document {
        val doc: PDDocument = when (source) {
            is DocumentSource.FilePath -> {
                val file = File(source.path)
                if (!file.exists()) throw EngineException(
                    EngineError.FILE_NOT_FOUND, "File not found: ${source.path}"
                )
                PDDocument.load(file, password)
            }
            is DocumentSource.ByteArraySource -> {
                PDDocument.load(source.bytes)
            }
            is DocumentSource.ContentUri -> {
                // Content URIs are Android-only; on desktop try as file path
                val path = source.uri.removePrefix("file://")
                PDDocument.load(File(path))
            }
        }

        if (doc.isEncrypted && password == null) {
            // Will still open for viewing with empty password; let it through
        }

        val docId = UUID.randomUUID().toString()
        docs[docId] = doc
        renderers[docId] = PDFRenderer(doc)

        // Track original file path for incremental save
        if (source is DocumentSource.FilePath) {
            filePaths[docId] = source.path
        }

        val metadata = doExtractMetadata(doc)
        val outline = doExtractOutline(doc)
        val pages = doExtractPages(doc)
        val permissions = doExtractPermissions(doc)

        val name = when (source) {
            is DocumentSource.FilePath -> source.path.substringAfterLast('/')
            is DocumentSource.ByteArraySource -> source.displayName
            is DocumentSource.ContentUri -> source.displayName
        }

        return Document(
            documentId = docId,
            source = source,
            name = name,
            metadata = metadata,
            pageCount = doc.numberOfPages,
            permissions = permissions,
            pages = pages,
            outline = outline
        )
    }

    override suspend fun doClose(documentId: String) {
        docs.remove(documentId)?.close()
        renderers.remove(documentId)
        filePaths.remove(documentId)
    }

    override suspend fun doSave(documentId: String, target: DocumentSource, config: SaveConfig) {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        when (target) {
            is DocumentSource.FilePath -> doc.save(File(target.path))
            is DocumentSource.ByteArraySource -> {
                val baos = ByteArrayOutputStream()
                doc.save(baos)
            }
            is DocumentSource.ContentUri -> {
                val path = target.uri.removePrefix("file://")
                doc.save(File(path))
            }
        }
    }

    override suspend fun doSaveIncremental(documentId: String, config: SaveConfig) {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        val path = filePaths[documentId]
            ?: throw EngineException(
                EngineError.UNSUPPORTED_FEATURE,
                "Incremental save requires the original file path; document was not opened from a file"
            )
        val file = File(path)
        if (!file.exists()) throw EngineException(
            EngineError.FILE_NOT_FOUND, "Original file no longer exists: $path"
        )
        // PDFBox 2.0.33 incremental save
        doc.saveIncremental(FileInputStream(file), FileOutputStream(file))
    }

    override suspend fun doExport(documentId: String, config: ExportConfig): List<ByteArray> {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.EXPORT_FAILED, "Document not open: $documentId"
        )
        val pageIndices = config.pages ?: (0 until doc.numberOfPages).toList()

        return when (config.format) {
            ExportFormat.PDF -> {
                // Export selected pages as a new PDF using importPage (deep copy)
                val newDoc = PDDocument()
                for (idx in pageIndices) {
                    val page = doc.getPage(idx)
                    newDoc.importPage(page)
                }
                val baos = ByteArrayOutputStream()
                newDoc.save(baos)
                newDoc.close()
                listOf(baos.toByteArray())
            }
            ExportFormat.PNG -> {
                val renderer = renderers[documentId] ?: throw EngineException(
                    EngineError.RENDER_FAILED, "No renderer for $documentId"
                )
                pageIndices.map { idx ->
                    val img: BufferedImage = renderer.renderImageWithDPI(idx, config.dpi)
                    val baos = ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(img, "PNG", baos)
                    baos.toByteArray()
                }
            }
            ExportFormat.JPEG -> {
                val renderer = renderers[documentId] ?: throw EngineException(
                    EngineError.RENDER_FAILED, "No renderer for $documentId"
                )
                pageIndices.map { idx ->
                    val img: BufferedImage = renderer.renderImageWithDPI(idx, config.dpi)
                    val baos = ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(img, "JPEG", baos)
                    baos.toByteArray()
                }
            }
            ExportFormat.TEXT -> {
                val stripper = PDFTextStripper()
                pageIndices.map { idx ->
                    stripper.startPage = idx + 1  // PDFBox uses 1-based
                    stripper.endPage = idx + 1
                    stripper.getText(doc).toByteArray(Charsets.UTF_8)
                }
            }
            else -> throw EngineException(
                EngineError.UNSUPPORTED_FEATURE, "Export format ${config.format} not supported"
            )
        }
    }

    // ── Inspection ───────────────────────────────────────────────

    override suspend fun doGetMetadata(documentId: String): DocumentMetadata {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        return doExtractMetadata(doc)
    }

    override suspend fun doGetPermissions(documentId: String): DocumentPermissions {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        return doExtractPermissions(doc)
    }

    override suspend fun doGetSecurityInfo(documentId: String): DocumentSecurityInfo {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        val perms = doExtractPermissions(doc)
        return DocumentSecurityInfo(
            permissions = perms,
            isReadOnly = false
        )
    }

    override suspend fun doGetOutline(documentId: String): DocumentOutline {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        return doExtractOutline(doc)
    }

    override suspend fun doGetPageCount(documentId: String): Int {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        return doc.numberOfPages
    }

    // ── Page access ─────────────────────────────────────────────

    override suspend fun doGetPage(documentId: String, pageIndex: Int): Page {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.PAGE_NOT_FOUND, "Document not open: $documentId"
        )
        if (pageIndex < 0 || pageIndex >= doc.numberOfPages)
            throw EngineException(EngineError.PAGE_NOT_FOUND, "Page index out of range: $pageIndex")
        return doExtractSinglePage(doc, pageIndex)
    }

    override suspend fun doGetPageBoxes(documentId: String, pageIndex: Int): PageBoxes {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.PAGE_NOT_FOUND, "Document not open: $documentId"
        )
        val pdPage = doc.getPage(pageIndex)
        return pdPageToPageBoxes(pdPage)
    }

    override suspend fun doGetPageRotation(documentId: String, pageIndex: Int): PageRotation {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.PAGE_NOT_FOUND, "Document not open: $documentId"
        )
        val pdPage = doc.getPage(pageIndex)
        return PageRotation.fromDegrees(pdPage.rotation)
    }

    // ── Rendering ───────────────────────────────────────────────

    override suspend fun doRenderPage(documentId: String, pageIndex: Int, config: RenderConfig): RenderedPage {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.RENDER_FAILED, "Document not open: $documentId"
        )
        val renderer = renderers[documentId] ?: throw EngineException(
            EngineError.RENDER_FAILED, "No renderer for $documentId"
        )
        val pdPage = doc.getPage(pageIndex)

        val effectiveRotation = config.rotation?.degrees ?: pdPage.rotation

        val dpi = config.dpi * config.scale
        val img: BufferedImage = renderer.renderImageWithDPI(pageIndex, dpi)

        val raster = img.raster
        val w = img.width
        val h = img.height
        val pixelCount = w * h
        val argb = IntArray(pixelCount)
        img.getRGB(0, 0, w, h, argb, 0, w)

        val bytes = ByteArray(pixelCount * 4)
        for (i in 0 until pixelCount) {
            val p = argb[i]
            val off = i * 4
            bytes[off]     = (p shr 16 and 0xFF).toByte()
            bytes[off + 1] = (p shr 8  and 0xFF).toByte()
            bytes[off + 2] = (p        and 0xFF).toByte()
            bytes[off + 3] = (p ushr 24        ).toByte()
        }

        return RenderedPage(
            pageId = "p$pageIndex",
            width = w,
            height = h,
            bitmapBytes = bytes,
            dpi = dpi,
            rotation = PageRotation.fromDegrees(effectiveRotation)
        )
    }

    // ── Text extraction ─────────────────────────────────────────

    override suspend fun doGetText(documentId: String, pageIndex: Int, rect: PdfRect?): String {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        if (rect != null) {
            // Use PDFTextStripperByArea for region-based extraction
            val stripper = PDFTextStripperByArea()
            stripper.addRegion("region", java.awt.Rectangle(
                rect.x.roundToInt(),
                rect.y.roundToInt(),
                rect.width.roundToInt(),
                rect.height.roundToInt()
            ))
            val pdPage = doc.getPage(pageIndex)
            stripper.extractRegions(pdPage)
            return stripper.getTextForRegion("region")
        } else {
            val stripper = PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            return stripper.getText(doc)
        }
    }

    // ── Object extraction (Phase 1: minimal) ────────────────────

    override suspend fun doGetObjects(documentId: String, pageIndex: Int, rect: PdfRect?): List<PdfObject> {
        return emptyList()
    }

    // ── Annotations (Phase 1: read-only listing) ───────────────

    override suspend fun doGetAnnotations(documentId: String, pageIndex: Int): List<AnnotationObject> {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        val pdPage = doc.getPage(pageIndex)
        return pdPage.annotations.mapNotNull { anno ->
            val cosDict = anno.getCOSObject()
            val subType = cosDict.getString(COSName.SUBTYPE) ?: return@mapNotNull null
            val rect = anno.getRectangle()
            val bbox = PdfRect(
                rect.lowerLeftX,
                rect.lowerLeftY,
                rect.width.toFloat(),
                rect.height.toFloat()
            )
            // Build a stable object ID from COS object or UUID fallback
            val objectId = try {
                val cosObj = cosDict as? COSObject
                cosObj?.let { "anno_${it.objectNumber}_${it.generationNumber}" }
            } catch (_: Exception) { null } ?: UUID.randomUUID().toString()

            AnnotationObject(
                objectId = objectId,
                boundingBox = bbox,
                annotationType = mapAnnotationType(subType),
                color = 0xFF000000L,
                author = anno.getCreator(),
                contents = anno.getContents(),
                timestamp = anno.getModificationDate()?.timeInMillis?.toString(),
                isPopup = false,
                replyTo = cosDict.getString(COSName.getFObj("IRT"))
            )
        }
    }

    // ── Forms (Phase 1: read-only listing) ─────────────────────

    override suspend fun doGetFormFields(documentId: String): List<FormField> {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        val acroForm: PDAcroForm? = doc.documentCatalog.acroForm
        if (acroForm == null) return emptyList()
        return acroForm.fields.map { field ->
            FormField(
                fieldName = field.partialFieldName,
                fieldType = mapFormWidgetType(field.fieldType),
                value = field.value?.toString(),
                isReadOnly = field.isReadOnly,
                isRequired = field.isRequired
            )
        }
    }

    override suspend fun doSetFormFieldValue(documentId: String, fieldId: String, value: String) {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        val acroForm = doc.documentCatalog.acroForm
            ?: throw EngineException(EngineError.UNSUPPORTED_FEATURE, "No form in document")
        val field = acroForm.fields.find { it.partialFieldName == fieldId }
            ?: throw EngineException(EngineError.OBJECT_NOT_FOUND, "Field not found: $fieldId")
        field.setValue(value)
    }

    // ── Modification commands (Phase 1: throw) ────────────────

    override suspend fun doExecuteCommand(documentId: String, command: DocumentCommand) {
        throw UnsupportedOperationException("Edit commands are Phase 2")
    }

    override suspend fun doAddObject(documentId: String, pageIndex: Int, obj: PdfObject): PdfObject {
        throw UnsupportedOperationException("Object editing is Phase 2")
    }

    override suspend fun doRemoveObject(documentId: String, pageIndex: Int, objectId: String) {
        throw UnsupportedOperationException("Object editing is Phase 2")
    }

    override suspend fun doUpdateObject(documentId: String, pageIndex: Int, objectId: String, updated: PdfObject): PdfObject {
        throw UnsupportedOperationException("Object editing is Phase 2")
    }

    override suspend fun doValidate(documentId: String): List<ValidationIssue> {
        val doc = docs[documentId] ?: return listOf(
            ValidationIssue(ValidationSeverity.ERROR, "DOC-001", "Document not open")
        )
        val issues = mutableListOf<ValidationIssue>()
        if (doc.numberOfPages == 0) {
            issues.add(ValidationIssue(ValidationSeverity.WARNING, "PAGE-001", "Document has zero pages"))
        }
        return issues
    }

    override suspend fun doOptimize(
        documentId: String,
        compressImages: Boolean,
        imageQuality: Int,
        removeUnusedObjects: Boolean,
        flattenForms: Boolean
    ) {
        val doc = docs[documentId] ?: throw EngineException(
            EngineError.IO_ERROR, "Document not open: $documentId"
        )
        // Phase 1: no-op for optimization
    }

    // ════════════════════════════════════════════════════════════
    //  Private helpers — PDFBox 2.0.33 correct API
    // ════════════════════════════════════════════════════════════

    private fun doExtractMetadata(doc: PDDocument): DocumentMetadata {
        val info = doc.documentCatalog.documentInformation ?: return DocumentMetadata()
        return DocumentMetadata(
            title = info.title,
            author = info.author,
            subject = info.subject,
            keywords = info.keywords,
            creator = info.creator,
            producer = info.producer,
            creationDate = info.getCreationDate()?.timeInMillis?.toString(),
            modificationDate = info.getModificationDate()?.timeInMillis?.toString(),
            pdfVersion = doc.version?.toString()
        )
    }

    private fun doExtractPermissions(doc: PDDocument): DocumentPermissions {
        val access = doc.currentAccessPermission ?: return DocumentPermissions()
        val perms = mutableSetOf<PdfPermission>()
        if (access.canPrint()) perms.add(PdfPermission.PRINT)
        if (access.canModify()) perms.add(PdfPermission.MODIFY)
        if (access.canExtractContent()) perms.add(PdfPermission.COPY)
        if (access.canModifyAnnotations()) perms.add(PdfPermission.ADD_ANNOTATIONS)
        if (access.canFillInForm()) perms.add(PdfPermission.FILL_FORMS)
        if (access.canExtractForAccessibility()) perms.add(PdfPermission.EXTRACT)
        if (access.canAssemble()) perms.add(PdfPermission.ASSEMBLE)
        if (access.canPrintDegraded()) perms.add(PdfPermission.PRINT_HIGH_RESOLUTION)
        return DocumentPermissions(
            isEncrypted = doc.isEncrypted,
            isPasswordProtected = doc.isEncrypted,
            permissions = perms,
            encryptionMethod = if (doc.isEncrypted) "PDFBox" else null,
            keyLength = 0
        )
    }

    private fun doExtractOutline(doc: PDDocument): DocumentOutline {
        val outlineRoot = doc.documentCatalog.documentOutline ?: return DocumentOutline()
        val entries = mutableListOf<OutlineEntry>()
        var node: PDOutlineItem? = outlineRoot.firstChild
        while (node != null) {
            entries.add(outlineNodeToEntry(node, doc))
            node = node.nextSibling
        }
        return DocumentOutline(entries)
    }

    private fun outlineNodeToEntry(
        node: PDOutlineItem,
        doc: PDDocument
    ): OutlineEntry {
        val destPageIndex = resolveDestinationPageIndex(node, doc)
        val childEntries = mutableListOf<OutlineEntry>()
        var child: PDOutlineItem? = node.firstChild
        while (child != null) {
            childEntries.add(outlineNodeToEntry(child, doc))
            child = child.nextSibling
        }
        return OutlineEntry(
            title = node.getTitle() ?: "",
            destinationPageIndex = destPageIndex,
            children = childEntries
        )
    }

    private fun resolveDestinationPageIndex(node: PDOutlineItem, doc: PDDocument): Int {
        val dest = node.getDestination()
        if (dest is PDPageDestination) {
            val page = dest.getPage()
            if (page != null) {
                return doc.pages.indexOf(page).coerceAtLeast(0)
            }
            // Some destinations store a page number instead
            val pageRef = dest.getPageNumber()
            if (pageRef >= 0) return pageRef
        }
        // Try the action's destination
        val action = node.action
        if (action != null) {
            val actionDest = action.destination
            if (actionDest is PDPageDestination) {
                val page = actionDest.getPage()
                if (page != null) {
                    return doc.pages.indexOf(page).coerceAtLeast(0)
                }
            }
        }
        return 0
    }

    private fun doExtractPages(doc: PDDocument): List<Page> {
        return (0 until doc.numberOfPages).map { idx ->
            doExtractSinglePage(doc, idx)
        }
    }

    private fun doExtractSinglePage(doc: PDDocument, pageIndex: Int): Page {
        val pdPage = doc.getPage(pageIndex)
        val boxes = pdPageToPageBoxes(pdPage)
        return Page(
            pageId = "p$pageIndex",
            index = pageIndex,
            boxes = boxes,
            rotation = PageRotation.fromDegrees(pdPage.rotation)
        )
    }

    private fun pdPageToPageBoxes(pdPage: PDPage): PageBoxes {
        val media = pdPage.mediaBox
        val mediaRect = PdfRect(media.lowerLeftX, media.lowerLeftY, media.width, media.height)
        val crop = pdPage.cropBox
        val bleed = pdPage.bleedBox
        val trim = pdPage.trimBox
        val art = pdPage.artBox
        return PageBoxes(
            mediaBox = mediaRect,
            cropBox = if (crop == media) null else PdfRect(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height),
            bleedBox = if (bleed == media) null else PdfRect(bleed.lowerLeftX, bleed.lowerLeftY, bleed.width, bleed.height),
            trimBox = if (trim == media) null else PdfRect(trim.lowerLeftX, trim.lowerLeftY, trim.width, trim.height),
            artBox = if (art == media) null else PdfRect(art.lowerLeftX, art.lowerLeftY, art.width, art.height)
        )
    }

    private fun mapAnnotationType(pdfboxSubtype: String): AnnotationType = when (pdfboxSubtype.uppercase()) {
        "HIGHLIGHT" -> AnnotationType.HIGHLIGHT
        "UNDERLINE" -> AnnotationType.UNDERLINE
        "STRIKEOUT" -> AnnotationType.STRIKEOUT
        "FREETEXT", "INK" -> AnnotationType.FREEHAND
        "SQUARE" -> AnnotationType.RECTANGLE
        "CIRCLE" -> AnnotationType.ELLIPSE
        "LINE" -> AnnotationType.LINE
        "TEXT" -> AnnotationType.STICKY_NOTE
        "LINK" -> AnnotationType.LINK
        "STAMP" -> AnnotationType.STAMP
        "FILEATTACHMENT" -> AnnotationType.ATTACHMENT
        else -> AnnotationType.TEXT_NOTE
    }

    private fun mapFormWidgetType(pdfboxType: String?): FormWidgetType = when (pdfboxType?.uppercase()) {
        "TX" -> FormWidgetType.TEXT_FIELD
        "CB", "CHK" -> FormWidgetType.CHECKBOX
        "RB" -> FormWidgetType.RADIO_BUTTON
        "CH", "COMBO" -> FormWidgetType.COMBO_BOX
        "LB", "LIST" -> FormWidgetType.LIST_BOX
        "BTN", "PUSH" -> FormWidgetType.BUTTON
        "SIG" -> FormWidgetType.SIGNATURE
        else -> FormWidgetType.TEXT_FIELD
    }
}
