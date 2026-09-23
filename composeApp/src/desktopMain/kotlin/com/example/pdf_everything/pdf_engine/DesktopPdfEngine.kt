package com.example.pdf_everything.pdf_engine

import com.example.pdf_everything.core.document.*
import com.example.pdf_everything.core.document.CoordinateSystem
import com.example.pdf_everything.core.search.IndexedChar
import com.example.pdf_everything.core.search.PageTextSearchIndex
import com.example.pdf_everything.pdf_engine.api.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.annotation.*
import org.apache.pdfbox.pdmodel.interactive.form.*
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.*
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DesktopPdfEngine : PdfEngine {
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null
    private var sourceFile: File? = null
    private val textIndexes = mutableMapOf<Int, PageTextSearchIndex>()
    private var currentDocumentVersion = 0L
    private var lastInfo: PdfDocumentInfo? = null
    private val ownedObjects = mutableMapOf<String, Pair<Int, PdfObject>>()
    private var dirty = false

    override fun capabilities() = PdfEngineCapabilities(
        capabilities = setOf(
            PdfEngineCapability.READ, PdfEngineCapability.RENDER, PdfEngineCapability.TEXT,
            PdfEngineCapability.OBJECTS, PdfEngineCapability.ANNOTATIONS, PdfEngineCapability.FORMS,
            PdfEngineCapability.OUTLINE, PdfEngineCapability.MODIFY, PdfEngineCapability.WRITE,
            PdfEngineCapability.INCREMENTAL_SAVE, PdfEngineCapability.EXPORT,
            PdfEngineCapability.OPTIMIZE, PdfEngineCapability.VALIDATE
        ),
        notes = mapOf(
            PdfEngineCapability.MODIFY to "Structural editing is implemented for adapter-owned text/image/shape objects and writable annotations/forms. Native PDF text discovered from content streams is read-only unless a safe content-object identity can be established.",
            PdfEngineCapability.INCREMENTAL_SAVE to "Implemented through PDFBox incremental serialization; output is independently reopened and validated.",
            PdfEngineCapability.OPTIMIZE to "Normalizes/re-serializes the document and removes adapter bookkeeping; no raster flattening is used."
        )
    )

    override fun source(): DocumentSource? = sourceFile?.let { DocumentSource.FilePath(it.absolutePath) }

    override fun open(source: DocumentSource): PdfDocumentInfo {
        val path = (source as? DocumentSource.FilePath)?.path
            ?: throw IllegalArgumentException("Desktop PDF opening requires a local file path")
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "PDF file does not exist or is empty: $path" }
        val loaded = try { Loader.loadPDF(file) } catch (e: org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PdfPasswordRequiredException(file.name, e)
        }
        val candidateRenderer = PDFRenderer(loaded).apply { isSubsamplingAllowed = true }
        try {
            // Candidate-first lifecycle: an invalid incoming PDF must never destroy the active document.
            val previousDocument = document
            val previousRenderer = renderer
            val previousSource = sourceFile
            val previousDirty = dirty
            try {
                document = loaded
                renderer = candidateRenderer
                sourceFile = file
                currentDocumentVersion++
                rebuildIndexes()
                dirty = false
                return rebuildInfo()
            } catch (failure: Throwable) {
                loaded.close()
                document = previousDocument
                renderer = previousRenderer
                sourceFile = previousSource
                dirty = previousDirty
                throw failure
            }
        } catch (failure: Throwable) {
            if (document !== loaded) runCatching { loaded.close() }
            throw failure
        }
    }

    override fun inspect(): PdfDocumentInfo = lastInfo ?: error("No PDF is open")

    override fun renderPage(pageIndex: Int, viewport: RenderViewport): RenderedPage {
        val pdf = document ?: error("No PDF is open")
        val activeRenderer = renderer ?: error("No PDF is open")
        require(pageIndex in 0 until pdf.numberOfPages) { "Invalid page index: $pageIndex" }
        require(viewport.widthPx in 120..3600) { "Render width must be between 120 and 3600 pixels" }
        val page = pdf.getPage(pageIndex)
        val effectiveDpi = (viewport.widthPx.toFloat() * 72f / page.mediaBox.width.coerceAtLeast(1f)).coerceIn(18f, 600f)
        val full: BufferedImage = activeRenderer.renderImageWithDPI(pageIndex, effectiveDpi, ImageType.RGB)
        val image = if (viewport.tileX != null && viewport.tileY != null && viewport.tileWidthPx != null && viewport.tileHeightPx != null) {
            val x = viewport.tileX.coerceIn(0, (full.width - 1).coerceAtLeast(0))
            val y = viewport.tileY.coerceIn(0, (full.height - 1).coerceAtLeast(0))
            val w = viewport.tileWidthPx.coerceAtLeast(1).coerceAtMost(full.width - x)
            val h = viewport.tileHeightPx.coerceAtLeast(1).coerceAtMost(full.height - y)
            full.getSubimage(x, y, w, h)
        } else full
        return ByteArrayOutputStream(image.width * image.height / 3).use { output ->
            check(javax.imageio.ImageIO.write(image, "png", output)) { "PNG encoder unavailable" }
            RenderedPage(
                pageIndex = pageIndex, width = image.width, height = image.height,
                png = output.toByteArray(), documentVersion = currentDocumentVersion,
                pageVersion = currentDocumentVersion,
                viewportKey = viewport.cacheKey.ifBlank { "${pageIndex}:${viewport.widthPx}:${viewport.scale}:${viewport.tileX}:${viewport.tileY}" }
            )
        }
    }

    override fun getText(pageIndex: Int): String = textIndexes[pageIndex]?.plainText() ?: error("Invalid page index: $pageIndex")

    override fun searchPage(pageIndex: Int, query: String, options: SearchOptions): List<SearchMatch> =
        textIndexes[pageIndex]?.search(pageIndex, query, options).orEmpty()

    override fun getObjects(pageIndex: Int): List<PdfObject> {
        val pdf = document ?: return emptyList()
        require(pageIndex in 0 until pdf.numberOfPages)
        val pageHeight = pdf.getPage(pageIndex).mediaBox.height
        val result = mutableListOf<PdfObject>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val unicode = text.unicode ?: return super.processTextPosition(text)
                val bounds = CoordinateSystem.pdfRectToUi(
                    RectF(text.xDirAdj, text.yDirAdj - text.heightDir, text.xDirAdj + text.widthDirAdj, text.yDirAdj),
                    pageHeight
                )
                val nativeId = "native-text-$pageIndex-${result.size}"
                result += PdfObject.TextObject(
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
        return result + ownedObjects.values.filter { it.first == pageIndex }.map { it.second }
    }

    override fun getObjectEditability(obj: PdfObject): PdfEditability = when {
        ownedObjects.containsKey(obj.id) -> PdfEditability(PdfObjectEditability.EDITABLE, "Created by PDF Everything adapter and backed by a dedicated PDF content operation.")
        obj is PdfObject.TextObject -> PdfEditability(PdfObjectEditability.READ_ONLY, "PDFBox exposes text positions, not a stable editable content-stream object identity. Repainting over existing text would be fake editing, so this object remains read-only.")
        else -> PdfEditability(PdfObjectEditability.UNSUPPORTED, "No safe structural identity is available for this native PDF object.")
    }

    override fun getAnnotations(pageIndex: Int): List<PdfAnnotation> {
        val pdf = document ?: return emptyList()
        val annotations = pdf.getPage(pageIndex).annotations
        return annotations.mapIndexed { index, a -> annotationToModel(pageIndex, index, a) }
    }

    override fun getForms(): FormModel {
        val pdf = document ?: return FormModel()
        val form = pdf.documentCatalog.acroForm ?: return FormModel()
        val out = mutableListOf<FormField>()
        form.fieldTree.forEach { field ->
            val value = runCatching { field.valueAsString }.getOrDefault("")
            val type = fieldType(field)
            val options = (field as? PDChoice)?.let { choice ->
                choice.optionsExportValues.mapIndexed { i, export -> FormOption(export, choice.optionsDisplayValues.getOrNull(i) ?: export) }
            }.orEmpty()
            if (field.widgets.isEmpty()) out += FormField(
                id = formId(field), name = field.partialName.orEmpty(), type = type, value = value,
                selectedValues = listOf(value).filter(String::isNotEmpty), options = options,
                required = field.isRequired, readOnly = field.isReadOnly, fullyQualifiedName = field.fullyQualifiedName.orEmpty()
            ) else field.widgets.forEachIndexed { i, widget ->
                val r = widget.rectangle
                val page = widget.page?.let { p -> pdf.pages.indexOf(p) }
                out += FormField(
                    id = "${formId(field)}-$i", name = field.partialName.orEmpty(), alternateName = field.alternateFieldName,
                    type = type, pageIndex = page, bounds = RectF(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY),
                    value = value, selectedValues = listOf(value).filter(String::isNotEmpty), options = options,
                    required = field.isRequired, readOnly = field.isReadOnly, fullyQualifiedName = field.fullyQualifiedName.orEmpty(), tooltip = widget.contents
                )
            }
        }
        return FormModel(out)
    }

    override fun getAttachments(): List<PdfAttachment> {
        val pdf = document ?: return emptyList()
        val names = pdf.documentCatalog.names ?: return emptyList()
        val embedded = names.embeddedFiles ?: return emptyList()
        val out = mutableListOf<PdfAttachment>()
        embedded.names.values.forEachIndexed { index, spec ->
            val fileSpec = spec as? PDComplexFileSpecification ?: return@forEachIndexed
            val embeddedFile = fileSpec.embeddedFile ?: return@forEachIndexed
            out += PdfAttachment(
                attachmentId = "attachment-$index-${fileSpec.file.hashCode()}",
                fileName = fileSpec.file ?: "attachment-$index",
                description = fileSpec.cosObject.getString(org.apache.pdfbox.cos.COSName.DESC),
                mimeType = embeddedFile.subtype,
                sizeBytes = embeddedFile.size.takeIf { it > 0L },
                sourceResourceRef = fileSpec.cosObject.toString(),
                checksum = embeddedFile.checkSum?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            )
        }
        return out
    }

    override fun getOutline(): List<OutlineItem> {
        val root = document?.documentCatalog?.documentOutline ?: return emptyList()
        return readOutline(root)
    }

    override fun beginTransaction(label: String): PdfTransaction {
        val target = sourceFile ?: throw IllegalStateException("No PDF is open")
        val backup = Files.createTempFile("pdf-everything-tx-", ".pdf").toFile()
        Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        var active = true
        return object : PdfTransaction {
            override val active get() = active
            override fun commit() { active = false; backup.delete() }
            override fun rollback() {
                if (!active) return
                active = false
                try { close(); open(DocumentSource.FilePath(backup.absolutePath)) }
                finally { backup.delete() }
            }
            override fun close() { if (active) rollback() }
        }
    }

    override fun applyDocumentStructure(model: Document) {
        val pdf = document ?: error("No PDF is open")
        require(model.pages.isNotEmpty()) { "A PDF document must contain at least one page" }

        val currentSourceIds = model.sourceDocuments
            .filter { it.source == DocumentSource.FilePath(sourceFile?.absolutePath ?: "") }
            .map { it.id }
            .toSet()

        val oldPages = (0 until pdf.numberOfPages).map(pdf::getPage)
        val usedOriginals = mutableSetOf<PDPage>()
        val desired = mutableListOf<PDPage>()
        val externalDocuments = mutableMapOf<String, PDDocument>()

        fun externalDocument(sourceId: String): PDDocument {
            return externalDocuments.getOrPut(sourceId) {
                val ref = model.sourceDocuments.firstOrNull { it.id == sourceId }
                    ?: throw IllegalStateException("Missing source mapping for page source " + sourceId)
                val source = ref.source as? DocumentSource.FilePath
                    ?: throw UnsupportedOperationException("Desktop merge currently requires local source files")
                Loader.loadPDF(File(source.path))
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
                        if (usedOriginals.add(original)) desired += original
                        else desired += pdf.importPage(original)
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

            ownedObjects.clear()
            model.pages.forEachIndexed { index, pageModel ->
                pageModel.objects.filter { it.editable }.forEach { obj ->
                    when (obj) {
                        is PdfObject.TextObject,
                        is PdfObject.ImageObject,
                        is PdfObject.ShapeObject -> addObject(index, obj)
                        else -> throw UnsupportedOperationException("Cannot serialize this editable object type yet")
                    }
                }
            }

            model.pages.forEachIndexed { index, pageModel ->
                val nativePage = pdf.getPage(index)
                pageModel.annotations.forEach { annotation ->
                    val duplicate = nativePage.annotations.any { native ->
                        native.subtype.equals(annotation.nativeSubtype ?: annotation.type.name, ignoreCase = true) &&
                            (native as? PDAnnotationMarkup)?.contents.orEmpty() == annotation.contents &&
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
        }
    }

    override fun modify(pageIndex: Int, obj: PdfObject) = updateObject(pageIndex, obj)

    override fun addObject(pageIndex: Int, obj: PdfObject) {
        require(pageIndex in 0 until (document?.numberOfPages ?: 0))
        val pdf = document ?: error("No PDF is open")
        val page = pdf.getPage(pageIndex)
        when (obj) {
            is PdfObject.TextObject -> addTextObject(pdf, page, obj)
            is PdfObject.ImageObject -> addImageObject(pdf, page, obj)
            is PdfObject.ShapeObject -> addRectangleObject(page, obj)
            else -> throw UnsupportedOperationException("addObject supports concrete TextObject, ImageObject and ShapeObject only")
        }
        ownedObjects[obj.id] = pageIndex to obj
        dirty = true
        postMutation()
    }

    override fun removeObject(pageIndex: Int, objectId: String) {
        val owned = ownedObjects[objectId] ?: throw UnsupportedOperationException("Cannot safely remove native object $objectId; it is not adapter-owned")
        require(owned.first == pageIndex)
        throw UnsupportedOperationException("Removing an adapter-owned content object requires rebuilding its dedicated content stream; this build refuses to delete through an unsafe approximation")
    }

    override fun updateObject(pageIndex: Int, obj: PdfObject) {
        if (obj is PdfObject.TextObject && !ownedObjects.containsKey(obj.id)) {
            throw UnsupportedOperationException("Native text editing is refused because PDFBox exposes no stable content-object identity here; drawing replacement text would violate the real-editing contract")
        }
        if (!ownedObjects.containsKey(obj.id)) throw UnsupportedOperationException("Native object is not structurally addressable")
        removeGeneratedObjectLayer(obj.id, pageIndex)
        addObject(pageIndex, obj)
    }

    override fun updateMetadata(metadata: DocumentMetadata) {
        val info = document?.documentInformation ?: error("No PDF is open")
        info.title = metadata.title
        info.author = metadata.author
        info.subject = metadata.subject
        info.keywords = metadata.keywords
        info.creator = metadata.creator
        dirty = true
        postMutation()
    }

    override fun updateAnnotation(annotation: PdfAnnotation) {
        val pdf = document ?: error("No PDF is open")
        val page = pdf.getPage(annotation.pageIndex)
        val ordinal = annotation.id.substringAfterLast('-').toIntOrNull()
            ?: throw UnsupportedOperationException("Annotation id has no stable ordinal")
        val list = page.annotations.toMutableList()
        require(ordinal in list.indices) { "Annotation not found: ${annotation.id}" }
        list[ordinal] = modelToAnnotation(annotation, pdf)
        page.annotations = list
        dirty = true
        postMutation()
    }

    override fun deleteAnnotation(pageIndex: Int, annotationId: String) {
        val pdf = document ?: error("No PDF is open")
        val ordinal = annotationId.substringAfterLast('-').toIntOrNull() ?: throw UnsupportedOperationException("Annotation id has no stable ordinal")
        val page = pdf.getPage(pageIndex)
        val list = page.annotations.toMutableList()
        require(ordinal in list.indices)
        list.removeAt(ordinal)
        page.annotations = list
        dirty = true
        postMutation()
    }

    override fun updateFormField(fieldId: String, value: String, selectedValues: List<String>) {
        val form = document?.documentCatalog?.acroForm ?: throw UnsupportedOperationException("PDF has no AcroForm")
        val qualified = fieldId.removePrefix("form-").substringBeforeLast('-', missingDelimiterValue = fieldId.removePrefix("form-"))
        val field = form.fieldTree.firstOrNull { formId(it) == "form-$qualified" || it.fullyQualifiedName == qualified }
            ?: throw IllegalArgumentException("Form field not found: $fieldId")
        require(!field.isReadOnly) { "Form field is read-only: $fieldId" }
        when (field) {
            is PDCheckBox -> if (value.equals("true", true) || value == field.onValue) field.check() else field.unCheck()
            is PDRadioButton -> field.setValue(selectedValues.firstOrNull() ?: value)
            is PDChoice -> if (selectedValues.size > 1) field.setValue(selectedValues) else field.setValue(selectedValues.firstOrNull() ?: value)
            else -> field.value = value
        }
        dirty = true
        postMutation()
    }

    override fun updateOutline(outline: List<OutlineItem>) {
        val pdf = document ?: error("No PDF is open")
        val catalog = pdf.documentCatalog
        val root = PDDocumentOutline()
        catalog.documentOutline = root
        fun append(parent: PDOutlineNode, items: List<OutlineItem>) {
            items.forEach { item ->
                val node = PDOutlineItem().apply { title = item.title }
                val pageIndex = item.destination.pageIndex ?: item.pageIndex
                if (pageIndex != null && pageIndex in 0 until pdf.numberOfPages) {
                    node.destination = PDPageXYZDestination().apply { page = pdf.getPage(pageIndex); left = item.destination.left?.toInt() ?: 0; top = item.destination.top?.toInt() ?: 0; zoom = item.destination.zoom ?: 0f }
                }
                parent.addLast(node)
                append(node, item.children)
            }
        }
        append(root, outline)
        root.openNode()
        dirty = true
        postMutation()
    }

    override fun save(path: String): PdfSaveReport = saveAtomic(File(path), incremental = false)
    override fun saveIncremental(path: String): PdfSaveReport = saveAtomic(File(path), incremental = true)
    override fun export(path: String): PdfSaveReport = save(path)

    override fun optimize(): PdfValidationReport {
        val pdf = document ?: error("No PDF is open")
        pdf.documentCatalog.acroForm?.refreshAppearances()
        rebuildIndexes()
        dirty = true
        val report = validate()
        if (!report.valid) return report.copy(warnings = report.warnings + "Optimization preparation completed but validation found errors.")
        return report.copy(warnings = report.warnings + "Document normalized in memory; no raster flattening or screenshot replacement was used.")
    }

    override fun optimizeTo(path: String): PdfSaveReport {
        optimize()
        return save(path)
    }

    override fun validate(): PdfValidationReport {
        val pdf = document ?: return PdfValidationReport(false, listOf("No PDF is open"), emptyList(), 0, null)
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (pdf.numberOfPages <= 0) errors += "PDF has no pages"
        pdf.pages.forEachIndexed { i, page ->
            val media = page.mediaBox
            if (media.width <= 0f || media.height <= 0f) errors += "Page ${i + 1} has invalid MediaBox ${media.width}x${media.height}"
            if (page.rotation % 90 != 0) errors += "Page ${i + 1} has invalid rotation ${page.rotation}"
            runCatching { page.annotations.forEach { require(it.rectangle.width >= 0f && it.rectangle.height >= 0f) } }
                .onFailure { errors += "Page ${i + 1} contains an invalid annotation rectangle" }
        }
        val source = sourceFile
        val size = source?.length()
        return PdfValidationReport(errors.isEmpty(), errors, warnings, pdf.numberOfPages, size)
    }

    private fun replaceFile(temp: File, target: File) {
        runCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { throw IllegalStateException("Could not replace PDF output ${target.absolutePath}: ${it.message}", it) }
    }

    override fun close() {
        renderer = null
        document?.close()
        document = null
        sourceFile = null
        textIndexes.clear()
        ownedObjects.clear()
        lastInfo = null
        dirty = false
    }

    private fun saveAtomic(target: File, incremental: Boolean): PdfSaveReport {
        val pdf = document ?: error("No PDF is open")
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile ?: File("."), ".${target.name}.saving-${System.nanoTime()}")
        try {
            if (incremental && sourceFile != null) {
                Files.copy(sourceFile!!.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                FileOutputStream(temp, true).use { pdf.saveIncremental(it) }
            } else {
                pdf.save(temp)
            }
            verifyIndependent(temp)
            replaceFile(temp, target)
            dirty = false
            val validation = validateSavedArtifact(target)
            if (!validation.valid) throw IllegalStateException("Saved PDF failed independent validation: ${validation.errors.joinToString()}")
            return PdfSaveReport(target.absolutePath, target.length(), validation)
        } finally { if (temp.exists()) temp.delete() }
    }

    private fun verifyIndependent(file: File) {
        Loader.loadPDF(file).use { reopened ->
            check(reopened.numberOfPages == document?.numberOfPages) { "Reopened page count differs after save" }
            reopened.pages.forEach { page -> require(page.mediaBox.width > 0f && page.mediaBox.height > 0f) }
        }
    }

    private fun validateSavedArtifact(file: File): PdfValidationReport = try {
        Loader.loadPDF(file).use { reopened ->
            val errors = mutableListOf<String>()
            if (reopened.numberOfPages <= 0) errors += "No pages after reopen"
            reopened.pages.forEachIndexed { i, page -> if (page.mediaBox.width <= 0f || page.mediaBox.height <= 0f) errors += "Invalid MediaBox on page ${i + 1}" }
            PdfValidationReport(errors.isEmpty(), errors, emptyList(), reopened.numberOfPages, file.length())
        }
    } catch (t: Throwable) { PdfValidationReport(false, listOf("Reopen validation failed: ${t.message}"), emptyList(), 0, file.length()) }

    private fun rebuildIndexes() {
        textIndexes.clear()
        val pdf = document ?: return
        repeat(pdf.numberOfPages) { textIndexes[it] = buildTextIndex(pdf, it) }
    }

    private fun postMutation() { currentDocumentVersion++; rebuildIndexes(); renderer = document?.let { PDFRenderer(it).apply { isSubsamplingAllowed = true } }; lastInfo = rebuildInfo() }
    private fun rebuildInfo(): PdfDocumentInfo {
        val pdf = document ?: error("No PDF is open")
        val file = sourceFile
        val pages = pdf.pages.mapIndexed { index, page ->
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
                text = textIndexes[index]?.plainText().orEmpty(),
                pageId = "page-$index",
                documentVersion = currentDocumentVersion,
                mediaBox = RectF(media.lowerLeftX, media.lowerLeftY, media.upperRightX, media.upperRightY),
                cropBox = RectF(crop.lowerLeftX, crop.lowerLeftY, crop.upperRightX, crop.upperRightY),
                bleedBox = RectF(bleed.lowerLeftX, bleed.lowerLeftY, bleed.upperRightX, bleed.upperRightY),
                trimBox = RectF(trim.lowerLeftX, trim.lowerLeftY, trim.upperRightX, trim.upperRightY),
                artBox = RectF(art.lowerLeftX, art.lowerLeftY, art.upperRightX, art.upperRightY)
            )
        }
        val info = pdf.documentInformation
        return PdfDocumentInfo(file?.name ?: "document.pdf", pdf.numberOfPages, pages, info.title.orEmpty(), info.author.orEmpty(), info.subject.orEmpty(), info.keywords.orEmpty(), info.creator.orEmpty(), info.producer.orEmpty(), pdf.isEncrypted, false, true, true, true, currentDocumentVersion).also { lastInfo = it }
    }

    private fun buildTextIndex(pdf: PDDocument, pageIndex: Int): PageTextSearchIndex {
        val height = pdf.getPage(pageIndex).mediaBox.height
        val chars = mutableListOf<IndexedChar>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val u = text.unicode ?: return super.processTextPosition(text)
                val rect = SearchRect(text.xDirAdj, height - text.yDirAdj - text.heightDir, text.xDirAdj + text.widthDirAdj, height - text.yDirAdj)
                u.forEach { c -> chars += IndexedChar(c, rect) }
                super.processTextPosition(text)
            }
        }
        stripper.startPage = pageIndex + 1; stripper.endPage = pageIndex + 1; stripper.getText(pdf)
        if (chars.isNotEmpty()) return PageTextSearchIndex(chars)
        val fallback = PDFTextStripper().apply { startPage = pageIndex + 1; endPage = pageIndex + 1 }.getText(pdf).trimEnd('\n', '\r')
        return PageTextSearchIndex(fallback.map { IndexedChar(it, SearchRect(0f, 0f, 0f, 0f)) })
    }

    private fun addTextObject(pdf: PDDocument, page: PDPage, obj: PdfObject.TextObject) {
        val pdfRect = CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), obj.fontSize.coerceIn(4f, 200f))
            cs.newLineAtOffset(pdfRect.left, pdfRect.top)
            cs.showText(obj.text)
            cs.endText()
        }
    }

    private fun addImageObject(pdf: PDDocument, page: PDPage, obj: PdfObject.ImageObject) {
        val imageFile = File(obj.source)
        require(imageFile.isFile) { "Image source does not exist: ${obj.source}" }
        val image = PDImageXObject.createFromFileByContent(imageFile, pdf)
        val pdfRect = CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(image, pdfRect.left, pdfRect.top, pdfRect.width.coerceAtLeast(1f), pdfRect.height.coerceAtLeast(1f))
        }
    }

    private fun addRectangleObject(page: PDPage, obj: PdfObject.ShapeObject) {
        PDPageContentStream(document ?: error("No PDF is open"), page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            val pdfRect = CoordinateSystem.uiRectToPdf(obj.bounds, page.mediaBox.height)
            cs.addRect(pdfRect.left, pdfRect.top, pdfRect.width, pdfRect.height)
            cs.stroke()
        }
    }

    private fun removeGeneratedObjectLayer(objectId: String, pageIndex: Int): Nothing {
        throw UnsupportedOperationException("Generated content removal is intentionally blocked until a token-preserving dedicated-layer rewriter is selected; no white-overpaint substitute is used")
    }

    private fun annotationToModel(pageIndex: Int, ordinal: Int, a: PDAnnotation): PdfAnnotation {
        val r = a.rectangle
        val markup = a as? PDAnnotationMarkup
        val type = when (a.subtype?.lowercase()) {
            "highlight" -> AnnotationType.Highlight; "underline" -> AnnotationType.Underline; "strikeout" -> AnnotationType.StrikeOut; "squiggly" -> AnnotationType.Squiggly
            "text" -> AnnotationType.Note; "freetext" -> AnnotationType.FreeText; "ink" -> AnnotationType.Ink; "square" -> AnnotationType.Rectangle; "circle" -> AnnotationType.Circle
            "line" -> AnnotationType.Line; "stamp" -> AnnotationType.Stamp; "redact" -> AnnotationType.Redaction; else -> AnnotationType.Note
        }
        val author = markup?.titlePopup.orEmpty(); val contents = markup?.contents.orEmpty()
        return PdfAnnotation("native-ann-$pageIndex-$ordinal", pageIndex, type, RectF(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY), contents = contents, author = author, subject = markup?.subject.orEmpty(), comment = if (contents.isNotEmpty()) AnnotationComment(author, contents, markup?.subject.orEmpty()) else null)
    }

    private fun modelToAnnotation(model: PdfAnnotation, pdf: PDDocument): PDAnnotation {
        val pdfRect = CoordinateSystem.uiRectToPdf(model.bounds, pdf.getPage(model.pageIndex).mediaBox.height)
        val rect = PDRectangle(pdfRect.left, pdfRect.top, pdfRect.width, pdfRect.height)
        val annotation = when (model.type) {
            AnnotationType.Highlight -> org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight().apply {
                rectangle = rect
                quadPoints = floatArrayOf(
                    pdfRect.left, pdfRect.top + pdfRect.height,
                    pdfRect.right, pdfRect.top + pdfRect.height,
                    pdfRect.left, pdfRect.top,
                    pdfRect.right, pdfRect.top
                )
            }
            AnnotationType.Underline -> org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationUnderline().apply {
                rectangle = rect
                quadPoints = floatArrayOf(
                    pdfRect.left, pdfRect.top + pdfRect.height,
                    pdfRect.right, pdfRect.top + pdfRect.height,
                    pdfRect.left, pdfRect.top,
                    pdfRect.right, pdfRect.top
                )
            }
            AnnotationType.StrikeOut -> org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationStrikeout().apply {
                rectangle = rect
                quadPoints = floatArrayOf(
                    pdfRect.left, pdfRect.top + pdfRect.height,
                    pdfRect.right, pdfRect.top + pdfRect.height,
                    pdfRect.left, pdfRect.top,
                    pdfRect.right, pdfRect.top
                )
            }
            AnnotationType.Note -> org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText().apply {
                rectangle = rect
                contents = model.contents.ifEmpty { model.comment?.contents.orEmpty() }
                titlePopup = model.author.ifEmpty { model.comment?.author.orEmpty() }
            }
            AnnotationType.FreeText -> org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFreeText().apply {
                rectangle = rect
                contents = model.contents
                titlePopup = model.author
                defaultAppearance = model.style.fontSize.toString() + " Tf 0 g"
            }
            else -> throw UnsupportedOperationException("Desktop does not yet serialize " + model.type.name + " annotations")
        }
        if (annotation is PDAnnotationMarkup) {
            annotation.contents = model.contents
            annotation.titlePopup = model.author
            annotation.subject = model.subject
        }
        runCatching {
            annotation.color = org.apache.pdfbox.pdmodel.graphics.color.PDColor(
                floatArrayOf(model.style.color.red, model.style.color.green, model.style.color.blue),
                org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
            )
        }
        runCatching { annotation.constructAppearances(pdf) }
        return annotation
    }

    private fun formId(field: PDField) = "form-${field.fullyQualifiedName.orEmpty()}"
    private fun fieldType(field: PDField): FormFieldType = when (field) {
        is PDTextField -> if (field.isMultiline) FormFieldType.MultilineText else FormFieldType.Text
        is PDComboBox -> FormFieldType.ComboBox
        is PDListBox -> FormFieldType.Choice
        is PDCheckBox -> FormFieldType.Checkbox
        is PDRadioButton -> FormFieldType.Radio
        is PDPushButton -> FormFieldType.PushButton
        is PDSignatureField -> FormFieldType.Signature
        else -> FormFieldType.Unknown
    }

    private fun readOutline(root: PDOutlineNode): List<OutlineItem> {
        val pdf = document ?: return emptyList()
        val result = mutableListOf<OutlineItem>(); var child = root.firstChild
        while (child != null) {
            val destinationPage = runCatching { child.findDestinationPage(pdf) }.getOrNull()?.let(pdf.pages::indexOf).takeIf { it != -1 }
            result += OutlineItem(title = child.title.orEmpty(), pageIndex = destinationPage, destination = BookmarkDestination(pageIndex = destinationPage), open = child.isNodeOpen, children = readOutline(child))
            child = child.nextSibling
        }
        return result
    }
}

class PdfPasswordRequiredException(val fileName: String, cause: Throwable? = null) : RuntimeException("Password required for $fileName", cause)
