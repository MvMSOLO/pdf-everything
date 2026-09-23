package com.example.pdf_everything

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfSaveReport
import com.example.pdf_everything.pdf_engine.api.PdfValidationReport
import com.example.pdf_everything.phase6.Phase6OperationResult
import com.example.pdf_everything.phase6.Phase6Platform
import com.example.pdf_everything.phase6.PrintRequest
import com.example.pdf_everything.phase6.PrintPaperSize
import com.example.pdf_everything.phase6.PrintScaling
import com.example.pdf_everything.phase6.RecoveryEntry
import com.example.pdf_everything.phase6.SaveAsRequest
import com.example.pdf_everything.phase6.SaveFailureReason
import com.example.pdf_everything.phase6.SourceFingerprint
import com.example.pdf_everything.phase7.DefaultPdfAssociationStatus
import java.io.File
import java.io.FileOutputStream

actual val isDesktop: Boolean = false

object AndroidFilePicker {
    var callback: ((DocumentSource) -> Unit)? = null
    var launcher: (() -> Unit)? = null
    var pendingSource: DocumentSource? = null
    fun deliver(source: DocumentSource) {
        val cb = callback
        callback = null
        if (cb != null) cb(source) else pendingSource = source
    }
    fun attachCallback(cb: (DocumentSource) -> Unit) {
        callback = cb
        pendingSource?.let { source -> pendingSource = null; deliver(source) }
    }
}

actual fun defaultPdfAssociationStatus(): DefaultPdfAssociationStatus = DefaultPdfAssociationStatus.UNSUPPORTED
actual fun openWindowsDefaultAppSettings(): Boolean = false
actual fun hasSeenDesktopDefaultAppPrompt(): Boolean = true
actual fun markDesktopDefaultAppPromptSeen() = Unit

object AndroidIncomingPdf {
    fun prepare(activity: Activity, intent: Intent, uri: Uri): DocumentSource = {
        val flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val persistable = (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        if (persistable && flags != 0) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(uri, flags)
                return@runCatching true
            }.getOrDefault(false).let { persisted ->
                if (persisted) return DocumentSource.ContentUri(uri.toString())
            }
        }
        return copyToWorkspaceOrThrow(activity, uri)
    }

    fun preparePickedDocument(activity: Activity, uri: Uri): DocumentSource {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching { activity.contentResolver.takePersistableUriPermission(uri, flags); true }.getOrDefault(false)
        return if (persisted) DocumentSource.ContentUri(uri.toString()) else copyToWorkspaceOrThrow(activity, uri)
    }

    fun copyToWorkspaceOrThrow(activity: Activity, uri: Uri): DocumentSource {
        val root = File(activity.filesDir, "pdf-workspace/incoming").also { it.mkdirs() }
        val safeName = queryDisplayName(activity, uri).orEmpty()
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
            .ifBlank { "incoming-${System.currentTimeMillis()}.pdf" }
        val target = File(root, "${System.currentTimeMillis()}-${safeName}")
        activity.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        } ?: error("Unable to read shared PDF URI")
        require(target.isFile && target.length() > 0L) { "Shared PDF is empty or unavailable" }
        return DocumentSource.FilePath(target.absolutePath)
    }

    private fun queryDisplayName(activity: Activity, uri: Uri): String? = runCatching {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

actual fun requestPdfSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit) {
    AndroidSaveAs.callback = onSelected
    (ActivityHolder.activity as? MainActivity)?.launchSaveAs(request.suggestedName)
}

actual fun printPdfDocument(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit) =
    AndroidPrinter.print(engine, document, request, onFinished)

actual fun requestImageOpen(onSelected: (DocumentSource) -> Unit) {
    AndroidFilePicker.attachCallback(onSelected)
    (ActivityHolder.activity as? MainActivity)?.launchImagePicker()
}

actual fun requestPdfOpen(onSelected: (DocumentSource) -> Unit) {
    AndroidFilePicker.attachCallback(onSelected)
    (ActivityHolder.activity as? MainActivity)?.launchPdfPicker()
}

actual fun consumeStartupPdfSource(): DocumentSource? = null
actual fun installDesktopFileDrop(onSelected: (DocumentSource) -> Unit) {}
actual fun loadRecentFiles(): List<RecentFile> = ActivityHolder.activity?.let { RecentFileStorage.load(it) }.orEmpty()
actual fun rememberRecentFile(file: RecentFile) { ActivityHolder.activity?.let { RecentFileStorage.remember(it, file) } }
actual fun clearRecentFiles() { ActivityHolder.activity?.let { RecentFileStorage.clear(it) } }

object ActivityHolder { var activity: Activity? = null }

object AndroidIntentFiles {
    fun extractPdfSource(activity: Activity, intent: Intent?): DocumentSource? {
        val sourceUri = extractPdfUri(intent) ?: return null
        val mime = intent?.type.orEmpty()
        if (mime.isNotBlank() && !mime.equals("application/pdf", true)) return null
        val prepared = runCatching { AndroidIncomingPdf.prepare(activity, intent ?: return null, sourceUri) }.getOrNull()
        return prepared
    }

    fun extractPdfUri(intent: Intent?): Uri? = intent?.let { incoming ->
        val direct = when (incoming.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> incoming.data
            Intent.ACTION_SEND -> incoming.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE -> incoming.getParcelableArrayListExtraCompat<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            else -> null
        }
        direct ?: incoming.clipData?.let { clip ->
            (0 until clip.itemCount).asSequence().mapNotNull { index -> clip.getItemAt(index).uri }.firstOrNull()
        }
    }
}

private object RecentFileStorage {
    private const val PREF = "pdf_everything_recent"
    private const val KEY = "items"
    fun load(activity: Activity): List<RecentFile> = runCatching {
        val raw = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE).getString(KEY, "") ?: ""
        raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val encodedSource = parts[1]
            val source = if (encodedSource.startsWith("file://")) DocumentSource.FilePath(Uri.parse(encodedSource).path ?: encodedSource.removePrefix("file://")) else if (encodedSource.startsWith("content://")) DocumentSource.ContentUri(encodedSource) else DocumentSource.ContentUri(encodedSource)
            RecentFile(
                name = parts[0], source = source, openedAtEpochMs = parts[2].toLongOrNull() ?: 0L,
                lastPage = parts.getOrNull(3)?.toIntOrNull() ?: 0, lastZoom = parts.getOrNull(4)?.toFloatOrNull() ?: 1f,
                fingerprint = parts.getOrNull(5)?.takeIf(String::isNotBlank)
            )
        }.sortedByDescending { it.openedAtEpochMs }.take(20)
    }.getOrDefault(emptyList())
    fun remember(activity: Activity, item: RecentFile) {
        val all = (listOf(item) + load(activity)).distinctBy { it.source.toString() }.take(20)
        activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE).edit().putString(
            KEY, all.joinToString("\n") {
                val sourceValue = when (val source = it.source) {
                    is DocumentSource.ContentUri -> source.uri
                    is DocumentSource.FilePath -> Uri.fromFile(File(source.path)).toString()
                }
                "${it.name.replace("\t", " ")}\t$sourceValue\t${it.openedAtEpochMs}\t${it.lastPage}\t${it.lastZoom}\t${it.fingerprint.orEmpty()}"
            }
        ).apply()
    }
    fun clear(activity: Activity) = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE).edit().remove(KEY).apply()
}

object AndroidSaveAs {
    var callback: ((String?) -> Unit)? = null
    fun deliver(uri: Uri?) = callback?.also { cb -> callback = null; cb(uri?.toString()) }
}

actual object Phase6Platform {
    private fun recoveryRoot(): File? = ActivityHolder.activity?.filesDir?.resolve("recovery")?.also(File::mkdirs)

    actual fun fingerprint(source: DocumentSource): SourceFingerprint? = when (source) {
        is DocumentSource.FilePath -> runCatching {
            val file = File(source.path)
            if (!file.isFile) return@runCatching null
            SourceFingerprint(source, ${file.length()}:${file.lastModified()}, System.currentTimeMillis())
        }.getOrNull()
        is DocumentSource.ContentUri -> runCatching {
            val activity = ActivityHolder.activity ?: return@runCatching null
            activity.contentResolver.query(Uri.parse(source.uri), arrayOf(OpenableColumns.SIZE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@runCatching null
                val size = c.getLong(0)
                val modified = c.getLong(1)
                SourceFingerprint(source, "$size:$modified:${source.uri}", System.currentTimeMillis())
            }
        }.getOrNull()
    }

    actual fun persist(engine: PdfEngine, document: Document, target: String, incremental: Boolean): PdfSaveReport {
        val uri = runCatching { Uri.parse(target) }.getOrNull()
        if (uri?.scheme != "content") return if (incremental) engine.saveIncremental(target) else engine.save(target)
        val activity = ActivityHolder.activity ?: error("Android activity unavailable")
        val temp = File.createTempFile("pdf-everything-save-", ".pdf", activity.cacheDir)
        try {
            val report = engine.save(temp.absolutePath)
            check(report.validation.valid) { "Temporary PDF failed validation" }
            activity.contentResolver.openOutputStream(uri, "wt")?.use { output -> temp.inputStream().use { input -> input.copyTo(output, 1024 * 1024) } }
                ?: error("Cannot open Save As destination for writing")
            val bytes = activity.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: report.bytesWritten
            return PdfSaveReport(target, bytes.coerceAtLeast(0L), PdfValidationReport(true, emptyList(), emptyList(), report.validation.pageCount, bytes))
        } finally { temp.delete() }
    }

    actual fun requestSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit) {
        val activity = ActivityHolder.activity as? MainActivity
        if (activity == null) {
            onSelected(null)
            return
        }
        AndroidSaveAs.callback = onSelected
        activity.launchSaveAs(request.suggestedName)
    }

    actual fun writeRecovery(entry: RecoveryEntry, payload: String): Boolean = runCatching {
        val root = recoveryRoot() ?: return@runCatching false
        val sourceValue = when (val source = entry.source) {
            is DocumentSource.ContentUri -> source.uri
            is DocumentSource.FilePath -> "file://" + source.path
            null -> ""
        }
        File(root, "${entry.id}.json").writeText(payload)
        File(root, "${entry.id}.meta").writeText(
            listOf(entry.id, entry.documentName, sourceValue, entry.createdAtEpochMs, entry.updatedAtEpochMs, entry.payloadPath, entry.sourceFingerprint.orEmpty()).joinToString("\n")
        )
        true
    }.getOrDefault(false)

    actual fun listRecoveries(): List<RecoveryEntry> = runCatching {
        recoveryRoot()?.listFiles { f -> f.extension == "meta" }.orEmpty().mapNotNull { f ->
            val p = f.readLines()
            if (p.size < 6) null else {
                val source = p[2].takeIf(String::isNotBlank)?.let {
                    when {
                        it.startsWith("content://") -> DocumentSource.ContentUri(it)
                        it.startsWith("file://") -> DocumentSource.FilePath(it.removePrefix("file://"))
                        it.startsWith("FilePath(path=") -> DocumentSource.FilePath(it.removePrefix("FilePath(path=").removeSuffix(")"))
                        it.startsWith("ContentUri(uri=") -> DocumentSource.ContentUri(it.removePrefix("ContentUri(uri=").removeSuffix(")"))
                        else -> DocumentSource.ContentUri(it)
                    }
                }
                RecoveryEntry(
                    p[0], p[1], source,
                    p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L, p[5], p.getOrNull(6)?.takeIf(String::isNotBlank)
                )
            }
        }
    }.getOrDefault(emptyList())

    actual fun readRecovery(entry: RecoveryEntry): String? = runCatching { recoveryRoot()?.resolve("${entry.id}.json")?.takeIf(File::isFile)?.readText() }.getOrNull()
    actual fun deleteRecovery(entry: RecoveryEntry): Boolean = runCatching { (recoveryRoot()?.resolve("${entry.id}.json")?.delete() == true) || (recoveryRoot()?.resolve("${entry.id}.meta")?.delete() == true) }.getOrDefault(false)
    actual fun print(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit) = AndroidPrinter.print(engine, document, request, onFinished)
}

object AndroidPrinter {
    fun print(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit) {
        val activity = ActivityHolder.activity ?: run { onFinished(Phase6OperationResult(false, "Android activity unavailable", SaveFailureReason.IO)); return }
        if (!document.permissions.canPrint) { onFinished(Phase6OperationResult(false, "Printing is disabled by PDF permissions", SaveFailureReason.UNSUPPORTED)); return }
        val pages = request.selectedPages(document.pageCount)
        if (pages.isEmpty()) { onFinished(Phase6OperationResult(false, "No pages selected", SaveFailureReason.UNKNOWN)); return }
        val manager = activity.getSystemService(Activity.PRINT_SERVICE) as? PrintManager
            ?: run { onFinished(Phase6OperationResult(false, "Android PrintManager unavailable", SaveFailureReason.UNSUPPORTED)); return }
        val baseMedia = when (request.paperSize) {
            PrintPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PrintPaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PrintPaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        }
        val media = if (request.landscape) when (request.paperSize) {
            PrintPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4.asLandscape()
            PrintPaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER.asLandscape()
            PrintPaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL.asLandscape()
        } else baseMedia
        val color = if (request.color) PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: android.os.Bundle?) {
                if (cancellationSignal.isCanceled) callback.onLayoutCancelled()
                else callback.onLayoutFinished(PrintDocumentInfo.Builder(document.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages.size).build(), oldAttributes == null)
            }
            override fun onWrite(pageRanges: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                Thread {
                    val pdf = AndroidPdfDocument(PrintAttributes.Builder().setMediaSize(media).setColorMode(color).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
                    var cancelled = false
                    try {
                        pages.forEachIndexed { outIndex, sourceIndex ->
                            if (cancellationSignal.isCanceled) { cancelled = true; return@forEachIndexed }
                            val size = when (request.paperSize) { PrintPaperSize.A4 ->  { 595 to 842 }; PrintPaperSize.LETTER -> { 612 to 792 }; PrintPaperSize.LEGAL -> { 612 to 1008 } }
                            val pageWidth = if (request.landscape) size.second else size.first
                            val pageHeight = if (request.landscape) size.first else size.second
                            val pi = AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, outIndex).create()
                            val page = pdf.startPage(pi)
                            val rendered = engine.renderPage(sourceIndex, com.example.pdf_everything.pdf_engine.api.RenderViewport(1600, cacheKey = "print-$sourceIndex"))
                            val bitmap = BitmapFactory.decodeByteArray(rendered.png, 0, rendered.png.size) ?: error("Unable to decode print render")
                            val scale = when (request.scaling) {
                                PrintScaling.ACTUAL_SIZE -> 1f
                                PrintScaling.CUSTOM -> request.customScalePercent.coerceIn(10, 400) / 100f
                                PrintScaling.FIT_TO_PRINTABLE_AREA -> minOf(pi.pageWidth.toFloat() / bitmap.width, pi.pageHeight.toFloat() / bitmap.height)
                            }
                            val w = bitmap.width * scale; val h = bitmap.height * scale
                            val l = (pi.pageWidth - w) / 2f; val t = (pi.pageHeight - h) / 2f
                            page.canvas.drawColor(Color.WHITE)
                            page.canvas.drawBitmap(bitmap, null, android.graphics.RectF(l, t, l + w, t + h), null)
                            pdf.finishPage(page); bitmap.recycle()
                        }
                        if (cancelled) callback.onWriteCancelled() else {
                            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        }
                    } catch (t: Throwable) { callback.onWriteFailed(t.message ?: "Android print failed") } finally { pdf.close() }
                }.start()
            }
        }
        runCatching { manager.print(document.name, adapter, PrintAttributes.Builder().setMediaSize(media).setColorMode(color).build()) }
            .onSuccess { onFinished(Phase6OperationResult(true, "Android print flow opened")) }
            .onFailure { onFinished(Phase6OperationResult(false, "Android print failed: ${it.message}", SaveFailureReason.IO)) }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)
