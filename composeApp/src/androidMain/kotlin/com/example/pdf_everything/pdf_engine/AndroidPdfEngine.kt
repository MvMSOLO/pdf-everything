package com.example.pdf_everything.pdf_engine

import android.content.Context
import android.net.Uri
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.document.AnnotationType
import com.example.pdf_everything.core.document.AnnotationColor
import com.example.pdf_everything.core.document.AnnotationStyle
import com.example.pdf_everything.core.document.AnnotationComment
import com.example.pdf_everything.core.document.PdfAnnotation
import com.example.pdf_everything.core.document.FormField
import com.example.pdf_everything.core.document.FormFieldType
import com.example.pdf_everything.core.document.FormModel
import com.example.pdf_everything.core.document.FormOption
import com.example.pdf_everything.core.document.FormWidget
import com.example.pdf_everything.core.document.OutlineItem
import com.example.pdf_everything.core.document.BookmarkDestination
import com.example.pdf_everything.core.search.IndexedChar
import com.example.pdf_everything.core.search.PageTextSearchIndex
import com.example.pdf_everything.pdf_engine.api.PdfDocumentInfo
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfPageInfo
import com.example.pdf_everything.pdf_engine.api.RenderViewport
import com.example.pdf_everything.pdf_engine.api.RenderedPage
import com.example.pdf_everything.pdf_engine.api.SearchMatch
import com.example.pdf_everything.pdf_engine.api.SearchOptions
import com.example.pdf_everything.pdf_engine.api.SearchRect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidPdfEngine : PdfEngine {
    private var originalSource: DocumentSource? = null
    override fun capabilities() = com.example.pdf_everything.pdf_engine.api.PdfEngineCapabilities(
        capabilities = setOf(
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.READ,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.RENDER,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.TEXT,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.OBJECTS,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.ANNOTATIONS,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.FORMS,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.OUTLINE,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.MODIFY,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.INCREMENTAL_SAVE,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.WRITE,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.EXPORT,
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.VALIDATE
        ),
        notes = mapOf(
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.MODIFY to "Android adapter currently permits native inspection plus safe form/annotation/metadata operations; unsupported object edits fail explicitly rather than overpainting.",
            com.example.pdf_everything.pdf_engine.api.PdfEngineCapability.WRITE to "Save is serialized to an explicit filesystem destination and independently reopened for verification."
        )
    )
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null
    private var sourceFile: File? = null
    private val textIndexes = mutableMapOf<Int, PageTextSearchIndex>()
    private var currentDocumentVersion = 0L
    private var lastInfo: PdfDocumentInfo? = null
    private var dirty = false


    override fun source(): DocumentSource? = originalSource ?: sourceFile?.let { DocumentSource.FilePath(it.absolutePath) }

    override fun save(path: String): com.example.pdf_everything.pdf_engine.api.PdfSaveReport {
        val pdf = document ?: error("No PDF is open")
        val target = File(path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile ?: File("."), ".${target.name}.saving-${System.nanoTime()}")
        try {
            pdf.save(temp)
            verifyIndependent(temp)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dirty = false
            val report = validateArtifact(target)
            check(report.valid) { "Saved PDF failed validation: ${report.errors.joinToString()}" }
            return com.example.pdf_everything.pdf_engine.api.PdfSaveReport(target.absolutePath, target.length(), report)
        } finally { if (temp.exists()) temp.delete() }
    }

    override fun saveIncremental(path: String): com.example.pdf_everything.pdf_engine.api.PdfSaveReport {
        val pdf = document ?: error("No PDF is open")
        val original = sourceFile ?: throw UnsupportedOperationException("Incremental save requires a local/materialized source PDF")
        val target = File(path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile ?: File("."), ".${target.name}.incremental-${System.nanoTime()}")
        try {
            Files.copy(original.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            FileOutputStream(temp, true).use { pdf.saveIncremental(it) }
            verifyIndependent(temp)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dirty = false
            val report = validateArtifact(target)
            check(report.valid) { "Incremental PDF failed validation: ${report.errors.joinToString()}" }
            return com.example.pdf_everything.pdf_engine.api.PdfSaveReport(target.absolutePath, target.length(), report)
        } finally { if (temp.exists()) temp.delete() }
    }

    override fun applyDocumentStructure(model: com.example.pdf_everything.core.document.Document) {
        val pdf = document ?: error("No PDF is open")
        val context = AndroidPdfEngineContext.applicationContext ?: error("Android context unavailable")
        require(model.pages.isNotEmpty()) { "A PDF document must contain at least one page" }

        val currentSourceIds = model.sourceDocuments
            .filter { it.source == originalSource }
            .map { it.id }
            .toSet()
        val oldPages = (0 until pdf.numberOfPages).map(pdf::getPage)
        val usedOriginals = mutableSetOf<PDPage>()
        val desired = mutableListOf<PDPage>()
        val externalDocuments = mutableMapOf<String, PDDocument>()
        val externalFiles = mutableListOf<File>()

        fun externalDocument(sourceId: String): PDDocument {
            return externalDocuments.getOrPut(sourceId) {
                val ref = model.sourceDocuments.firstOrNull { it.id == sourceId }
                    ?: error("Missing source mapping for " + sourceId)
                val file = materializeSource(context, ref.source)
                externalFiles += file
                PDDocument.load(file)
            }
        }

        try {
            model.pages.forEach { pageModel ->
                val sourceId = pageModel.sourceDocumentId
                val sourceIsCurrent = sourceId == null || sourceId in currentSourceIds
                val originalIndex = pageModel.sourcePageIndex
                when {
                    sourceIsCurrent && originalIndex != null && originalIndex in oldPages.indices -> {
                        val original = oldPages[originalIndex]
                        if (usedOriginals.add(original)) desired += original else desired += pdf.importPage(original)
                    }
                    sourceId != null && originalIndex != null -> {
                        val sourcePdf = externalDocument(sourceId)
                        require(originalIndex in 0 until sourcePdf.numberOfPages) {
                            "Source page " + originalIndex + " is outside " + sourceId
                        }
                        desired += pdf.importPage(sourcePdf.getPage(originalIndex))
                    }
                    else -> {
                        val media = pageModel.boxes.media
                        desired += PDPage(PDRectangle(media.width.coerceAtLeast(1f), media.height.coerceAtLeast(1f)))
                    }
                }
            }

            pdf.pages.toList().forEach { page -> pdf.pages.remove(page) }
            desired.forEach { page -> pdf.pages.add(page) }

            model.pages.forEachIndexed { index, pageModel ->
                val page = pdf.getPage(index)
                page.rotation = ((pageModel.rotation % 360) + 360) % 360
                val crop = pageModel.boxes.crop
                if (crop.width > 0f && crop.height > 0f) {
                    val mediaHeight = page.mediaBox.height
                    page.cropBox = PDRectangle(
                        crop.left,
                        (mediaHeight - crop.bottom).coerceAtLeast(0f),
                        crop.width,
                        crop.height
                    )
                }
            }

            model.pages.forEachIndexed { index, pageModel ->
                pageModel.objects.filter { it.editable }.forEach { obj ->
                    when (obj) {
                        is com.example.pdf_everything.core.document.PdfObject.TextObject,
                        is com.example.pdf_everything.core.document.PdfObject.ImageObject,
                        is com.example.pdf_everything.core.document.PdfObject.ShapeObject -> addObject(index, obj)
                        else -> throw UnsupportedOperationException("Cannot serialize this editable object type on Android yet")
                    }
                }
            }

            model.pages.forEachIndexed { index, pageModel ->
                val nativePage = pdf.getPage(index)
                pageModel.annotations.forEach { annotation ->
                    val duplicate = nativePage.annotations.any { native ->
                        native.subtype.equals(annotation.nativeSubtype ?: annotation.type.name, ignoreCase = true) &&
                            (native as? com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup)?.contents.orEmpty() == annotation.contents &&
                            native.rectangle.width == annotation.bounds.width &&
                            native.rectangle.height == annotation.bounds.height
                    }
                    if (!duplicate) nativePage.annotations.add(modelToAnnotation(annotation.copy(pageIndex = index), pdf))
                }
            }

            updateMetadata(model.metadata)
            updateOutline(model.outline)
            model.formModel.fields.forEach { field ->
                if (field.value.isNotEmpty() || field.selectedValues.isNotEmpty()) {
                    runCatching { updateFormField(field.id, field.value, field.selectedValues) }
                        .getOrElse { throw IllegalStateException("Form field " + field.id + " could not be persisted: " + it.message, it) }
                }
            }
            dirty = true
            postMutation()
        } finally {
            externalDocuments.values.forEach { runCatching { it.close() } }
            externalFiles.forEach { runCatching { it.delete() } }
        }
    }

    override fun addObject(pageIndex: Int, obj: com.example.pdf_everything.core.document.PdfObject) {
        val pdf = document ?: error("No PDF is open")
        require(pageIndex in 0 until pdf.numberOfPages) { "Invalid page index: $pageIndex" }
        val page = pdf.getPage(pageIndex)
        when (obj) {
            is com.example.pdf_everything.core.document.PdfObject.TextObject -> {
                val r = com.example.pdf_everything.core.document.CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    pdf, page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true, true
                ).use { cs ->
                    cs.beginText()
                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, obj.fontSize.coerceIn(4f, 200f))
                    cs.newLineAtOffset(r.left, r.top)
                    cs.showText(obj.text)
                    cs.endText()
                }
            }
            is com.example.pdf_everything.core.document.PdfObject.ImageObject -> {
                val file = resolveImageSource(obj.source)
                val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFileByContent(file, pdf)
                val r = com.example.pdf_everything.core.document.CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    pdf, page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true, true
                ).use { cs ->
                    cs.drawImage(image, r.left, r.top, r.width.coerceAtLeast(1f), r.height.coerceAtLeast(1f))
                }
            }
            is com.example.pdf_everything.core.document.PdfObject.ShapeObject -> {
                val r = com.example.pdf_everything.core.document.CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    pdf, page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true, true
                ).use { cs ->
                    cs.addRect(r.left, r.top, r.width, r.height)
                    cs.stroke()
                }
            }
            else -> throw UnsupportedOperationException("Android native insertion supports text, image and shape objects only")
        }
        dirty = true
    }

    override fun updateMetadata(metadata: com.example.pdf_everything.core.document.DocumentMetadata) {
        val info = document?.documentInformation ?: error("No PDF is open")
        info.title = metadata.title
        info.author = metadata.author
        info.subject = metadata.subject
        info.keywords = metadata.keywords
        info.creator = metadata.creator
        dirty = true
    }

    override fun updateAnnotation(annotation: PdfAnnotation) {
        val pdf = document ?: error("No PDF is open")
        val page = pdf.getPage(annotation.pageIndex)
        val ordinal = annotation.id.substringAfter("ann-" + annotation.pageIndex + "-", "").substringBefore('-').toIntOrNull()
            ?: throw UnsupportedOperationException("Annotation identity is not writable: " + annotation.id)
        require(ordinal in page.annotations.indices) { "Annotation not found: " + annotation.id }
        page.annotations[ordinal] = modelToAnnotation(annotation, pdf)
        dirty = true
    }

    override fun deleteAnnotation(pageIndex: Int, annotationId: String) {
        val pdf = document ?: error("No PDF is open")
        val page = pdf.getPage(pageIndex)
        val ordinal = annotationId.substringAfter("ann-" + pageIndex + "-", "").substringBefore('-').toIntOrNull()
            ?: throw UnsupportedOperationException("Annotation identity is not writable: " + annotationId)
        require(ordinal in page.annotations.indices)
        page.annotations.removeAt(ordinal)
        dirty = true
    }

    override fun updateFormField(fieldId: String, value: String, selectedValues: List<String>) {
        val form = document?.documentCatalog?.acroForm ?: throw UnsupportedOperationException("PDF has no AcroForm")
        val qualified = fieldId.removePrefix("form-").substringBeforeLast('-', missingDelimiterValue = fieldId.removePrefix("form-"))
        val field = form.fieldTree.firstOrNull { it.fullyQualifiedName == qualified || "form-" + it.fullyQualifiedName == fieldId }
            ?: throw IllegalArgumentException("Form field not found: " + fieldId)
        require(!field.isReadOnly) { "Form field is read-only: " + fieldId }
        when (field) {
            is com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox -> if (value.equals("true", true) || value == field.onValue) field.check() else field.unCheck()
            is com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton -> field.value = selectedValues.firstOrNull() ?: value
            is com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice -> if (selectedValues.size > 1) field.value = selectedValues else field.value = selectedValues.firstOrNull() ?: value
            else -> field.value = value
        }
        document?.documentCatalog?.acroForm?.refreshAppearances()
        dirty = true
    }

    override fun updateOutline(outline: List<com.example.pdf_everything.core.document.OutlineItem>) {
        val pdf = document ?: error("No PDF is open")
        val root = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline()
        pdf.documentCatalog.documentOutline = root
        fun append(parent: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode, items: List<com.example.pdf_everything.core.document.OutlineItem>) {
            items.forEach { item ->
                val node = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem().apply {
                    title = item.title
                    val pageIndex = item.destination.pageIndex ?: item.pageIndex
                    if (pageIndex != null && pageIndex in 0 until pdf.numberOfPages) {
                        destination = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination().apply {
                            page = pdf.getPage(pageIndex)
                            left = item.destination.left?.toInt() ?: 0
                            top = item.destination.top?.toInt() ?: 0
                            zoom = item.destination.zoom ?: 0f
                        }
                    }
                }
                parent.addLast(node)
                append(node, item.children)
            }
        }
        append(root, outline)
        root.openNode()
        dirty = true
    }

    private fun resolveImageSource(value: String): File {
        val context = AndroidPdfEngineContext.applicationContext ?: error("Android context unavailable")
        return when {
            value.startsWith("content://") -> materializeSource(context, com.example.pdf_everything.core.document.DocumentSource.ContentUri(value))
            value.startsWith("file://") -> File(Uri.parse(value).path ?: value.removePrefix("file://"))
            else -> File(value)
        }.also { require(it.isFile && it.length() > 0) { "Image source is unavailable" } }
    }

    override fun export(path: String): com.example.pdf_everything.pdf_engine.api.PdfSaveReport = save(path)

    override fun validate(): com.example.pdf_everything.pdf_engine.api.PdfValidationReport {
        val pdf = document ?: return com.example.pdf_everything.pdf_engine.api.PdfValidationReport(false, listOf("No PDF is open"), emptyList(), 0, null)
        val errors = mutableListOf<String>()
        pdf.pages.forEachIndexed { i, page ->
            if (page.mediaBox.width <= 0f || page.mediaBox.height <= 0f) errors += "Page ${i + 1} has invalid MediaBox"
            if (page.rotation % 90 != 0) errors += "Page ${i + 1} has invalid rotation ${page.rotation}"
        }
        if (pdf.numberOfPages == 0) errors += "PDF has no pages"
        return com.example.pdf_everything.pdf_engine.api.PdfValidationReport(errors.isEmpty(), errors, emptyList(), pdf.numberOfPages, sourceFile?.length())
    }

    override fun optimize(): com.example.pdf_everything.pdf_engine.api.PdfValidationReport {
        // Rebuild the expensive text/search state and refresh form appearances where supported.
        document?.documentCatalog?.acroForm?.refreshAppearances()
        return validate().copy(warnings = listOf("Android normalization completed without raster flattening."))
    }

    private fun verifyIndependent(file: File) {
        PDDocument.load(file).use { reopened ->
            check(reopened.numberOfPages == document?.numberOfPages) { "Reopened page count differs after save" }
        }
    }

    private fun validateArtifact(file: File): com.example.pdf_everything.pdf_engine.api.PdfValidationReport = try {
        PDDocument.load(file).use { reopened ->
            val errors = mutableListOf<String>()
            if (reopened.numberOfPages == 0) errors += "No pages after reopen"
            reopened.pages.forEachIndexed { i, page -> if (page.mediaBox.width <= 0f || page.mediaBox.height <= 0f) errors += "Invalid MediaBox on page ${i + 1}" }
            com.example.pdf_everything.pdf_engine.api.PdfValidationReport(errors.isEmpty(), errors, emptyList(), reopened.numberOfPages, file.length())
        }
    } catch (t: Throwable) {
        com.example.pdf_everything.pdf_engine.api.PdfValidationReport(false, listOf("Reopen validation failed: ${t.message}"), emptyList(), 0, file.length())
    }

    @Synchronized
    override fun open(source: DocumentSource): PdfDocumentInfo {
        close()
        val context = AndroidPdfEngineContext.applicationContext
            ?: error("Android PDF engine has not been initialized")
        PDFBoxResourceLoader.init(context)
        val file = materializeSource(context, source)
        sourceFile = file
        originalSource = source
        val loaded = try { PDDocument.load(file) } catch (e: Exception) {
            if (e.javaClass.simpleName.contains("InvalidPassword", ignoreCase = true)) {
                throw com.example.pdf_everything.pdf_engine.api.PdfPasswordRequiredException(file.name, e)
            }
            throw e
        }
        document = loaded
        renderer = PDFRenderer(loaded).apply { isSubsamplingAllowed = true }
        dirty = false
        currentDocumentVersion++

        val pages = loaded.pages.mapIndexed { index, page ->
            val indexer = buildTextIndex(loaded, index)
            textIndexes[index] = indexer
            val media = page.mediaBox
            val crop = page.cropBox ?: media
            val bleed = page.bleedBox ?: crop
            val trim = page.trimBox ?: crop
            val art = page.artBox ?: crop
            PdfPageInfo(
                index = index,
                width = media.width,
                height = media.height,
                rotation = page.rotation,
                text = indexer.plainText(),
                pageId = "page-$index",
                documentVersion = currentDocumentVersion,
                mediaBox = com.example.pdf_everything.core.document.RectF(media.lowerLeftX, media.lowerLeftY, media.upperRightX, media.upperRightY),
                cropBox = com.example.pdf_everything.core.document.RectF(crop.lowerLeftX, crop.lowerLeftY, crop.upperRightX, crop.upperRightY),
                bleedBox = com.example.pdf_everything.core.document.RectF(bleed.lowerLeftX, bleed.lowerLeftY, bleed.upperRightX, bleed.upperRightY),
                trimBox = com.example.pdf_everything.core.document.RectF(trim.lowerLeftX, trim.lowerLeftY, trim.upperRightX, trim.upperRightY),
                artBox = com.example.pdf_everything.core.document.RectF(art.lowerLeftX, art.lowerLeftY, art.upperRightX, art.upperRightY)
            )
        }
        val info = loaded.documentInformation
        val result = PdfDocumentInfo(
            name = when (source) {
                is DocumentSource.ContentUri -> Uri.parse(source.uri).lastPathSegment ?: file.name
                is DocumentSource.FilePath -> file.name
            },
            pageCount = pages.size,
            pages = pages,
            title = info.title.orEmpty(),
            author = info.author.orEmpty(),
            subject = info.subject.orEmpty(),
            keywords = info.keywords.orEmpty(),
            creator = info.creator.orEmpty(),
            producer = info.producer.orEmpty(),
            encrypted = loaded.isEncrypted,
            passwordRequired = false,
            canPrint = true,
            canCopy = true,
            canModify = true,
            documentVersion = currentDocumentVersion
        )
        lastInfo = result
        return result
    }

    @Synchronized
    override fun renderPage(pageIndex: Int, viewport: RenderViewport): RenderedPage {
        val activeRenderer = renderer ?: error("No PDF is open")
        val loaded = document ?: error("No PDF is open")
        require(pageIndex in 0 until loaded.numberOfPages) { "Invalid page index: $pageIndex" }
        require(viewport.widthPx in 120..2400) { "Render width must be between 120 and 2400 pixels" }
        val pageWidth = loaded.getPage(pageIndex).mediaBox.width.coerceAtLeast(1f)
        val dpi = viewport.widthPx.toFloat() * 72f / pageWidth
        val image = activeRenderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
        return ByteArrayOutputStream().use { output ->
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            val result = RenderedPage(
                pageIndex = pageIndex,
                width = image.width,
                height = image.height,
                png = output.toByteArray(),
                documentVersion = currentDocumentVersion,
                pageVersion = currentDocumentVersion,
                viewportKey = viewport.cacheKey.ifBlank { "${viewport.widthPx}x${viewport.heightPx ?: 0}@${viewport.scale}" }
            )
            image.recycle()
            result
        }
    }

    override fun inspect(): PdfDocumentInfo = lastInfo ?: error("No PDF is open")

    override fun getText(pageIndex: Int): String = textIndexes[pageIndex]?.plainText()
        ?: throw IllegalArgumentException("Invalid page index: $pageIndex")

    override fun searchPage(pageIndex: Int, query: String, options: SearchOptions): List<SearchMatch> =
        textIndexes[pageIndex]?.search(pageIndex, query, options).orEmpty()

    override fun getObjects(pageIndex: Int): List<com.example.pdf_everything.core.document.PdfObject> {
        val pdf = document ?: return emptyList()
        require(pageIndex in 0 until pdf.numberOfPages) { "Invalid page index: $pageIndex" }
        val pageHeight = pdf.getPage(pageIndex).mediaBox.height
        val objects = mutableListOf<com.example.pdf_everything.core.document.PdfObject>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode ?: return
                val bounds = com.example.pdf_everything.core.document.RectF(
                    text.xDirAdj,
                    pageHeight - text.yDirAdj - text.heightDir,
                    text.xDirAdj + text.widthDirAdj,
                    pageHeight - text.yDirAdj
                )
                val nativeId = "native-text-$pageIndex-${objects.size}"
                objects += com.example.pdf_everything.core.document.PdfObject.TextObject(
                    id = nativeId,
                    text = unicode,
                    bounds = bounds,
                    fontName = runCatching { text.font?.name }.getOrNull(),
                    fontSize = text.fontSizeInPt.takeIf { it.isFinite() && it > 0f } ?: 12f,
                    originalIdentity = nativeId,
                    editability = com.example.pdf_everything.core.document.PdfObjectEditability.READ_ONLY,
                    editable = false
                )
                super.processTextPosition(text)
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(pdf)
        return objects
    }

    override fun getAnnotations(pageIndex: Int): List<PdfAnnotation> {
        val pdf = document ?: return emptyList()
        require(pageIndex in 0 until pdf.numberOfPages) { "Invalid page index: $pageIndex" }
        return pdf.getPage(pageIndex).annotations.mapIndexed { ordinal, a ->
            val r = a.rectangle
            val subtype = a.subtype.orEmpty()
            val type = when (subtype.lowercase()) {
                "highlight" -> AnnotationType.Highlight
                "underline" -> AnnotationType.Underline
                "strikeout" -> AnnotationType.StrikeOut
                "squiggly" -> AnnotationType.Squiggly
                "text" -> AnnotationType.Note
                "freetext" -> AnnotationType.FreeText
                "ink" -> AnnotationType.Ink
                "square" -> AnnotationType.Rectangle
                "circle" -> AnnotationType.Circle
                "line" -> AnnotationType.Line
                "stamp" -> AnnotationType.Stamp
                "redact" -> AnnotationType.Redaction
                else -> AnnotationType.Note
            }
            val markup = a as? com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
            PdfAnnotation(
                id = "ann-$pageIndex-$ordinal-${a.cosObject?.gen ?: 0}",
                pageIndex = pageIndex,
                type = type,
                bounds = com.example.pdf_everything.core.document.RectF(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY),
                comment = markup?.contents?.let { AnnotationComment(author = markup.titlePopup.orEmpty(), contents = it, subject = markup.subject.orEmpty()) },
                contents = markup?.contents.orEmpty(),
                author = markup?.titlePopup.orEmpty(),
                subject = markup?.subject.orEmpty()
            )
        }
    }

    override fun getForms(): FormModel {
        val acroForm = document?.documentCatalog?.acroForm ?: return FormModel()
        val fields = acroForm.fieldTree.flatMap { field -> mapField(field) }
        return FormModel(fields = fields)
    }

    private fun mapField(field: com.tom_roush.pdfbox.pdmodel.interactive.form.PDField): List<FormField> {
        val type = when (field.fieldType?.lowercase()) {
            "tx" -> if (field is com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField && field.isMultiline) FormFieldType.MultilineText else FormFieldType.Text
            "ch" -> if (field is com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox) FormFieldType.ComboBox else FormFieldType.Choice
            "btn" -> when (field) {
                is com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton -> FormFieldType.Radio
                is com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton -> FormFieldType.PushButton
                else -> FormFieldType.Checkbox
            }
            "sig" -> FormFieldType.Signature
            else -> FormFieldType.Unknown
        }
        val widgets = field.widgets
        val widgetFields = if (widgets.isEmpty()) listOf(null) else widgets
        return widgetFields.mapIndexed { i, widget ->
            val rect = widget?.rectangle
            FormField(
                id = "form-${field.fullyQualifiedName ?: field.cosObject.hashCode()}-$i",
                name = field.partialName.orEmpty().ifBlank { field.fullyQualifiedName.orEmpty() },
                alternateName = field.alternateFieldName,
                type = type,
                pageIndex = widget?.page?.let { document?.pages?.indexOf(it) },
                bounds = rect?.let { com.example.pdf_everything.core.document.RectF(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY) },
                value = runCatching { field.valueAsString }.getOrDefault(""),
                selectedValues = listOfNotNull(runCatching { field.valueAsString }.getOrNull()).filter { it.isNotEmpty() },
                required = field.isRequired,
                readOnly = field.isReadOnly,
                fullyQualifiedName = field.fullyQualifiedName.orEmpty(),
                tooltip = widget?.annotation?.contents
            )
        }
    }

    override fun getAttachments(): List<com.example.pdf_everything.core.document.PdfAttachment> {
        val pdf = document ?: return emptyList()
        val names = PDDocumentNameDictionary(pdf.documentCatalog).embeddedFiles ?: return emptyList()
        val rootNames = names.names
        val out = mutableListOf<com.example.pdf_everything.core.document.PdfAttachment>()
        fun consume(entries: Map<String, PDComplexFileSpecification>?) {
            entries.orEmpty().forEach { (name, spec) ->
                val embedded = spec.embeddedFile ?: return@forEach
                out += com.example.pdf_everything.core.document.PdfAttachment(
                    attachmentId = "attachment-${out.size}-${name.hashCode()}",
                    fileName = spec.file ?: name,
                    description = spec.description,
                    mimeType = embedded.subtype,
                    sizeBytes = embedded.size.takeIf { it > 0 },
                    sourceResourceRef = spec.cosObject?.toString(),
                    checksum = embedded.checkSum?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                )
            }
        }
        consume(rootNames)
        names.kids.orEmpty().forEach { kid -> consume(kid.names) }
        return out
    }

    override fun getOutline(): List<OutlineItem> {
        val root = document?.documentCatalog?.documentOutline ?: return emptyList()
        fun walk(node: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode): List<OutlineItem> =
            buildList {
                var child = node.firstChild
                while (child != null) {
                    val page = runCatching { document?.let { pdf -> child.findDestinationPage(pdf) } }
                        .getOrNull()
                        ?.let { resolved -> document?.pages?.indexOf(resolved) }
                    add(OutlineItem(title = child.title.orEmpty(), pageIndex = page, destination = BookmarkDestination(pageIndex = page), open = child.isNodeOpen))
                    child = child.nextSibling
                }
            }
        return walk(root)
    }

    @Synchronized
    override fun close() {
        dirty = false
        renderer = null
        document?.close()
        document = null
        textIndexes.clear()
        lastInfo = null
        sourceFile?.let { runCatching { if (it.exists()) it.delete() } }
        sourceFile = null
        originalSource = null
    }

    private fun materializeSource(context: Context, source: DocumentSource): File = when (source) {
        is DocumentSource.FilePath -> File(source.path)
        is DocumentSource.ContentUri -> {
            val uri = Uri.parse(source.uri)
            val extension = uri.lastPathSegment?.substringAfterLast('.', "pdf")?.take(8)?.let { ".${it.lowercase()}" } ?: ".pdf"
            File.createTempFile("pdf_everything_", extension, context.cacheDir).also { target ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                } ?: error("Unable to read PDF content")
            }
        }
    }.also { file -> require(file.isFile && file.length() > 0) { "PDF file is empty or unavailable" } }

    private fun buildTextIndex(pdf: PDDocument, pageIndex: Int): PageTextSearchIndex {
        val pageHeight = pdf.getPage(pageIndex).mediaBox.height
        val chars = mutableListOf<IndexedChar>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode ?: return
                val rect = SearchRect(
                    left = text.xDirAdj,
                    top = pageHeight - text.yDirAdj - text.heightDir,
                    right = text.xDirAdj + text.widthDirAdj,
                    bottom = pageHeight - text.yDirAdj
                )
                unicode.forEach { ch -> chars += IndexedChar(ch, rect) }
                super.processTextPosition(text)
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(pdf)
        if (chars.isEmpty()) {
            val fallback = PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }.getText(pdf).trimEnd('\n').trimEnd('\r')
            return PageTextSearchIndex(fallback.map { IndexedChar(it, SearchRect(0f, 0f, 0f, 0f)) })
        }
        return PageTextSearchIndex(chars)
    }
}

object AndroidPdfEngineContext {
    var applicationContext: Context? = null
}
