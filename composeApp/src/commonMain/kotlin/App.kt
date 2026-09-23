import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toDp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.pdf_everything.RecentFile
import com.example.pdf_everything.RenderMemoryCache
import com.example.pdf_everything.consumeStartupPdfSource
import com.example.pdf_everything.installDesktopFileDrop
import com.example.pdf_everything.loadRecentFiles
import com.example.pdf_everything.rememberRecentFile
import com.example.pdf_everything.requestPdfSaveAs
import com.example.pdf_everything.printPdfDocument
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentMetadata
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.BoxSet
import com.example.pdf_everything.core.document.RectF
import com.example.pdf_everything.core.editor.EditorController
import com.example.pdf_everything.core.editor.Phase3EditorScreen
import com.example.pdf_everything.features.cut.Phase4Workspace
import com.example.pdf_everything.features.cut.CutTool
import com.example.pdf_everything.features.phase5.Phase5Workspace
import com.example.pdf_everything.core.clipboard.PlatformClipboard
import com.example.pdf_everything.core.shortcuts.EditorMode
import com.example.pdf_everything.core.shortcuts.ShortcutAction
import com.example.pdf_everything.core.shortcuts.ShortcutContext
import com.example.pdf_everything.core.shortcuts.ShortcutRegistry
import com.example.pdf_everything.core.search.searchDocument
import com.example.pdf_everything.pdf_engine.api.PdfDocumentInfo
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfPageInfo
import com.example.pdf_everything.pdf_engine.api.RenderViewport
import com.example.pdf_everything.pdf_engine.api.SearchMatch
import com.example.pdf_everything.pdf_engine.api.SearchOptions
import com.example.pdf_everything.pdf_engine.api.createPdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfPersistenceCoordinator
import com.example.pdf_everything.pdf_engine.api.RenderPriority
import com.example.pdf_everything.pdf_engine.api.RenderScheduler
import com.example.pdf_everything.pdf_engine.api.LocalPdfRenderScheduler
import com.example.pdf_everything.phase6.Phase6PersistenceManager
import com.example.pdf_everything.phase6.RecoveryAutosaveCoordinator
import com.example.pdf_everything.phase6.PrintRequest
import com.example.pdf_everything.phase6.PrintScaling
import com.example.pdf_everything.phase6.PrintPaperSize
import com.example.pdf_everything.phase6.PrintPageParity
import com.example.pdf_everything.phase6.PageRange
import com.example.pdf_everything.phase7.DefaultPdfAssociationStatus
import com.example.pdf_everything.phase6.SaveAsRequest
import com.example.pdf_everything.phase6.SaveFailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap

private enum class WorkspaceTab { Home, View, Edit, Cut, Organize, Phase5 }

private enum class ViewMode { Single, Continuous, TwoPage, Organizer, Presentation }
private enum class FitMode { Page, Width, Height, Actual }

@Composable
fun App() {
    var darkReading by remember { mutableStateOf(isSystemInDarkTheme()) }
    MaterialTheme(colorScheme = if (darkReading) darkColorScheme() else lightColorScheme()) {
        PdfWorkspace(darkReading = darkReading, onToggleReading = { darkReading = !darkReading })
    }
}

@Composable
private fun PdfWorkspace(darkReading: Boolean, onToggleReading: () -> Unit) {
    var engine by remember { mutableStateOf(createPdfEngine()) }
    val scope = rememberCoroutineScope()
    val editor = remember { EditorController() }
    val shortcutRegistry = remember { ShortcutRegistry() }
    var editorRevision by remember { mutableStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val cache = remember { RenderMemoryCache() }
    val renderScheduler = remember(engine, cache) { RenderScheduler(engine, cache, scope) }
    DisposableEffect(renderScheduler) { onDispose { renderScheduler.close() } }
    var document by remember { mutableStateOf<Document?>(null) }
    var info by remember { mutableStateOf<PdfDocumentInfo?>(null) }
    var selectedPage by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var fitMode by remember { mutableStateOf(FitMode.Actual) }
    var viewRotation by remember { mutableStateOf(0) }
    var viewMode by remember { mutableStateOf(ViewMode.Single) }
    var activeTab by remember { mutableStateOf(WorkspaceTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOptions by remember { mutableStateOf(SearchOptions()) }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSearchOptions by remember { mutableStateOf(false) }
    var zoomInput by remember { mutableStateOf("100") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var recentFiles by remember { mutableStateOf(loadRecentFiles()) }
    val searchFocusRequester = remember { FocusRequester() }
    val phase6 = remember(engine) { Phase6PersistenceManager(engine) }
    val autosave = remember(phase6) { RecoveryAutosaveCoordinator(phase6) }
    var recoveryEntries by remember { mutableStateOf(phase6.recoverableEntries()) }
    var conflictDialog by remember { mutableStateOf(false) }
    var printDialog by remember { mutableStateOf(false) }
    var pendingSaveAs by remember { mutableStateOf(false) }
    var showDefaultAppPrompt by remember { mutableStateOf(isDesktop && !hasSeenDesktopDefaultAppPrompt()) }
    var defaultAssociationStatus by remember { mutableStateOf(defaultPdfAssociationStatus()) }

    DisposableEffect(engine) { onDispose { engine.close() } }

    fun open(source: DocumentSource) {
        if (!source.toString().contains(".pdf", ignoreCase = true) && source !is DocumentSource.ContentUri) {
            error = "Only PDF documents can be opened here"
            return
        }
        isLoading = true
        error = null
        searchMatches = emptyList()
        currentSearchIndex = 0
        scope.launch {
            var candidate: PdfEngine? = null
            try {
                val opened = withContext(Dispatchers.Default) {
                    candidate = createPdfEngine()
                    val engineCandidate = candidate ?: error("PDF engine was not created")
                    val documentInfo = engineCandidate.open(source)
                    if (documentInfo.pageCount <= 0) error("PDF contains no pages")
                    val validation = engineCandidate.validate()
                    if (!validation.valid) error(validation.errors.joinToString("; "))
                    documentInfo
                }
                val candidateEngine = candidate ?: error("PDF engine was not created")
                val now = currentTimeMillisCompat()
                val openedDocument = Document(
                    id = "${source.hashCode()}-$now",
                    name = opened.name,
                    source = source,
                    title = opened.title,
                    metadata = DocumentMetadata(
                        title = opened.title,
                        author = opened.author,
                        subject = opened.subject,
                        keywords = opened.keywords,
                        creator = opened.creator,
                        producer = opened.producer
                    ),
                    permissions = DocumentPermissions(
                        canPrint = opened.canPrint,
                        canCopy = opened.canCopy,
                        canModify = opened.canModify,
                        canAnnotate = opened.canModify,
                        canFillForms = opened.canModify,
                        canAssemble = opened.canModify
                    ),
                    securityInfo = SecurityInfo(
                        encrypted = opened.encrypted,
                        passwordRequired = opened.passwordRequired
                    ),
                    sourceDocuments = listOf(
                        com.example.pdf_everything.core.document.SourceDocumentRef("source", source)
                    ),
                    pages = opened.pages.map { page ->
                        Page(
                            id = page.pageId,
                            index = page.index,
                            boxes = BoxSet(media = page.mediaBox, crop = page.cropBox, bleed = page.bleedBox, trim = page.trimBox, art = page.artBox),
                            rotation = page.rotation,
                            objects = runCatching {
                                val nativeObjects = candidateEngine.getObjects(page.index)
                                val formObjects = candidateEngine.getForms().fields.filter { it.pageIndex == page.index && it.bounds != null }.map { field ->
                                    com.example.pdf_everything.core.document.PdfObject.FormWidgetObject(
                                        id = "widget-object-${field.id}", fieldId = field.id, fieldType = field.type.name, bounds = field.bounds!!,
                                        value = field.value, originalIdentity = field.id,
                                        editability = if (field.readOnly) com.example.pdf_everything.core.document.PdfObjectEditability.READ_ONLY else com.example.pdf_everything.core.document.PdfObjectEditability.EDITABLE,
                                        editable = !field.readOnly
                                    )
                                }
                                nativeObjects + formObjects
                            }.getOrElse { throw IllegalStateException("Unable to hydrate objects on page ${page.index + 1}: ${it.message}", it) },
                            annotations = runCatching { candidateEngine.getAnnotations(page.index) }
                                .getOrElse { throw IllegalStateException("Unable to hydrate annotations on page ${page.index + 1}: ${it.message}", it) },
                            widgets = runCatching { candidateEngine.getForms().fields.filter { it.pageIndex == page.index }.map { field ->
                                com.example.pdf_everything.core.document.FormWidget(
                                    id = field.id, fieldType = field.type.name, bounds = field.bounds ?: RectF(0f, 0f, 0f, 0f),
                                    value = field.value, fieldId = field.id, pageIndex = page.index, readOnly = field.readOnly, required = field.required
                                )
                            } }.getOrElse { throw IllegalStateException("Unable to hydrate form widgets on page ${page.index + 1}: ${it.message}", it) },
                            text = page.text,
                            sourcePageIndex = page.index,
                            sourceDocumentId = "source"
                        )
                    },
                    attachments = runCatching { candidateEngine.getAttachments() }.getOrElse { throw IllegalStateException("Unable to read embedded attachments: ${it.message}", it) },
                    outline = runCatching { candidateEngine.getOutline() }.getOrElse { throw IllegalStateException("Unable to read PDF outline: ${it.message}", it) },
                    formModel = runCatching { candidateEngine.getForms() }.getOrElse { throw IllegalStateException("Unable to read PDF forms: ${it.message}", it) },
                    historyMetadata = com.example.pdf_everything.core.document.HistoryMetadata(
                        importedAtEpochMs = now,
                        audit = com.example.pdf_everything.core.document.AuditHistoryMetadata(
                            createdAtEpochMs = now, lastModifiedAtEpochMs = now,
                            sourceFingerprint = phase6.currentFingerprint(source)?.value
                        )
                    ),
                    version = opened.documentVersion, dirty = false, dirtyState = DirtyState.CLEAN
                )
                val normalizedDocument = com.example.pdf_everything.core.document.DocumentModelService.validateOrThrow(openedDocument)
                val oldEngine = engine
                engine = candidateEngine
                candidate = null
                runCatching { oldEngine.close() }
                document = normalizedDocument
                info = opened
                editor.setDocument(normalizedDocument)
                editorRevision++
                cache.clear()
                val resume = recentFiles.firstOrNull { it.source == source }
                selectedPage = (resume?.lastPage ?: 0).coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
                zoom = (resume?.lastZoom ?: 1f).coerceIn(0.25f, 6f)
                zoomInput = "${(zoom * 100).toInt()}"
                fitMode = FitMode.Actual
                viewRotation = 0
                viewMode = ViewMode.Single
                activeTab = WorkspaceTab.View
                rememberRecentFile(RecentFile(opened.name, source, now, selectedPage, zoom, phase6.currentFingerprint(source)?.value))
                recentFiles = loadRecentFiles()
                recoveryEntries = phase6.recoverableEntries()
                autosave.reset()
            } catch (throwable: Throwable) {
                candidate?.let { runCatching { it.close() } }
                error = throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open this PDF"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(document?.id, document?.version, document?.dirty) {
        while (isActive) {
            delay(15_000L)
            val current = document ?: continue
            if (current.dirty) {
                withContext(Dispatchers.Default) { autosave.maybeAutosave(current, currentTimeMillisCompat()) }
                recoveryEntries = phase6.recoverableEntries()
            }
        }
    }

    LaunchedEffect(Unit) {
        installDesktopFileDrop(::open)
        consumeStartupPdfSource()?.let(::open)
        if (isDesktop) defaultAssociationStatus = defaultPdfAssociationStatus()
    }

    LaunchedEffect(searchQuery, searchOptions, info?.documentVersion) {
        searchJob?.cancel()
        val currentInfo = info
        val query = searchQuery.trim()
        if (currentInfo == null || query.isEmpty()) {
            searchMatches = emptyList()
            currentSearchIndex = 0
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchJob = launch(Dispatchers.Default) {
            val matches = searchDocument(engine, currentInfo.pageCount, query, searchOptions)
            withContext(Dispatchers.Main) {
                searchMatches = matches
                currentSearchIndex = 0
                if (matches.isNotEmpty()) selectedPage = matches.first().pageIndex
                isSearching = false
            }
        }
    }

    fun jumpSearch(delta: Int) {
        if (searchMatches.isEmpty()) return
        val next = (currentSearchIndex + delta + searchMatches.size) % searchMatches.size
        currentSearchIndex = next
        selectedPage = searchMatches[next].pageIndex
    }

    fun updateRecentForCurrent() {
        val current = document ?: return
        val fingerprint = phase6.currentFingerprint(current)?.value
        rememberRecentFile(RecentFile(current.name, current.source ?: return, currentTimeMillisCompat(), selectedPage, zoom, fingerprint))
        recentFiles = loadRecentFiles()
    }

    fun saveToDestination(target: String) {
        val current = document ?: return
        scope.launch {
            document = com.example.pdf_everything.phase6.Phase6SaveState.saving(current)
            runCatching {
                withContext(Dispatchers.Default) { phase6.save(current, target, incremental = false) }
            }.onSuccess { saved ->
                // Reopen the destination through the adapter so subsequent Ctrl+S uses the new source identity.
                val targetSource = if (target.startsWith("content://")) DocumentSource.ContentUri(target) else DocumentSource.FilePath(target)
                withContext(Dispatchers.Default) { engine.close(); engine.open(targetSource) }
                document = saved.copy(source = targetSource, dirty = false, dirtyState = com.example.pdf_everything.core.document.DirtyState.CLEAN)
                editor.setDocument(document)
                info = engine.inspect()
                cache.clear()
                updateRecentForCurrent()
                autosave.clearAfterSuccessfulSave()
                recoveryEntries = phase6.recoverableEntries()
                pendingSaveAs = false
                statusMessage = "Saved As ${info?.name ?: saved.name} • validated after reopen"
            }.onFailure { t ->
                document = com.example.pdf_everything.phase6.Phase6SaveState.failed(current)
                pendingSaveAs = false
                statusMessage = "Save As failed: ${t.message ?: "Unknown error"}"
            }
        }
    }


    fun saveCurrentDocument(forceConflict: Boolean = false) {
        val current = document ?: run { statusMessage = "No document is open"; return }
        if (!current.permissions.canModify) { statusMessage = "This PDF is read-only. Use Save As to create a writable copy."; return }
        val source = current.source
        if (source == null) { requestPdfSaveAs(SaveAsRequest(current.name)) { path -> if (path != null) saveToDestination(path) }; return }
        scope.launch {
            if (!forceConflict) {
                val conflict = withContext(Dispatchers.Default) { phase6.checkExternalConflict(current) }
                if (!conflict.ok && conflict.failureReason == SaveFailureReason.EXTERNAL_CONFLICT) { conflictDialog = true; return@launch }
            }
            document = com.example.pdf_everything.phase6.Phase6SaveState.saving(current)
            runCatching {
                withContext(Dispatchers.Default) { phase6.save(current, current.sourcePath ?: source.toString(), incremental = false, allowExternalConflict = forceConflict) }
            }.onSuccess { saved ->
                document = saved
                editor.setDocument(saved)
                info = withContext(Dispatchers.Default) { engine.inspect() }
                cache.clear()
                updateRecentForCurrent()
                autosave.clearAfterSuccessfulSave()
                recoveryEntries = phase6.recoverableEntries()
                statusMessage = "Saved ${info?.name ?: saved.name} • validated after reopen"
            }.onFailure { t ->
                document = com.example.pdf_everything.phase6.Phase6SaveState.failed(current)
                statusMessage = "Save failed: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun beginSaveAs() {
        val current = document ?: return
        pendingSaveAs = true
        requestPdfSaveAs(SaveAsRequest(current.name, current.sourcePath), ::saveToDestination)
    }

    fun beginPrint() { printDialog = true }

    fun submitPrint(request: PrintRequest) {
        val current = document ?: return
        printDialog = false
        printPdfDocument(engine, current, request) { result ->
            statusMessage = result.message
        }
    }

    fun importPdfForPhase4(replacePageIndex: Int?) {
        requestPdfOpen { source ->
            scope.launch {
                val secondary = createPdfEngine()
                runCatching {
                    withContext(Dispatchers.Default) {
                        val otherInfo = secondary.open(source)
                        Document(
                            id = "merge-${source.hashCode()}-${currentTimeMillisCompat()}",
                            name = otherInfo.name,
                            source = source,
                            title = otherInfo.title,
                            metadata = DocumentMetadata(otherInfo.title, otherInfo.author, otherInfo.subject, otherInfo.keywords, otherInfo.creator, otherInfo.producer),
                            pages = otherInfo.pages.map { p ->
                                Page(
                                    id = p.pageId,
                                    index = p.index,
                                    boxes = BoxSet(media = p.mediaBox, crop = p.cropBox, bleed = p.bleedBox, trim = p.trimBox, art = p.artBox),
                                    rotation = p.rotation,
                                    objects = runCatching { secondary.getObjects(p.index) }.getOrDefault(emptyList()),
                                    text = p.text,
                                    sourcePageIndex = p.index,
                                    sourceDocumentId = "merged-source"
                                )
                            }
                        )
                    }
                }.onSuccess { other ->
                    val changed = if (replacePageIndex != null && other.pages.isNotEmpty()) {
                        editor.replacePage(
                            replacePageIndex.coerceIn(0, (editor.document?.pageCount ?: 1) - 1),
                            other.pages.first(),
                            other.sourceDocuments
                        )
                    } else {
                        editor.mergeDocument(other)
                    }
                    if (changed) {
                        document = editor.document
                        selectedPage = selectedPage.coerceIn(0, (editor.document?.pageCount ?: 1) - 1)
                        editorRevision++
                        cache.clear()
                        searchMatches = emptyList()
                        statusMessage = if (replacePageIndex != null) "Page replaced - unsaved" else "Merged ${other.pageCount} pages - unsaved"
                    }
                }.onFailure { statusMessage = it.message ?: "Import failed" }
                secondary.close()
            }
        }
    }

    val currentHits = searchMatches.filter { it.pageIndex == selectedPage }

    if (conflictDialog) {
        AlertDialog(
            onDismissRequest = { conflictDialog = false },
            title = { Text("File changed outside PDF Everything") },
            text = { Text("The source PDF changed after it was opened. Choose whether to reload it, keep your edits by explicitly overwriting, or save a separate copy.") },
            confirmButton = { TextButton(onClick = { conflictDialog = false; document?.source?.let(::open) }) { Text("Reload") } },
            dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { conflictDialog = false; beginSaveAs() }) { Text("Save As…") }; TextButton(onClick = { conflictDialog = false; saveCurrentDocument(forceConflict = true) }) { Text("Keep My Changes") } } }
        )
    }
    if (recoveryEntries.isNotEmpty() && document == null) {
        val candidate = recoveryEntries.first()
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Recovered document") },
            text = { Text("PDF Everything found an unsaved editing session for ${candidate.documentName}. Restore the editing session or discard the recovery copy.") },
            confirmButton = { TextButton(onClick = {
                val restored = phase6.restoreRecovery(candidate)
                if (restored != null) {
                    restored.source?.let { source -> open(source) }
                    document = restored
                    editor.setDocument(restored)
                    selectedPage = 0
                    zoom = 1f
                    statusMessage = "Recovered unsaved session • verify and Save As when ready"
                }
                recoveryEntries = phase6.recoverableEntries()
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { phase6.deleteRecovery(candidate); recoveryEntries = phase6.recoverableEntries() }) { Text("Discard") } }
        )
    }
    if (printDialog && document != null) {
        PrintSettingsDialog(
            pageCount = document!!.pageCount,
            onCancel = { printDialog = false },
            onPrint = ::submitPrint
        )
    }
    if (showDefaultAppPrompt && isDesktop) {
        AlertDialog(
            onDismissRequest = { markDesktopDefaultAppPromptSeen(); showDefaultAppPrompt = false },
            title = { Text("Use PDF Everything for PDF files?") },
            text = { Text("Windows controls the default PDF application. PDF Everything is registered as a PDF-capable application; choosing it as the default is done through Windows Settings.") },
            confirmButton = {
                Button(onClick = {
                    markDesktopDefaultAppPromptSeen()
                    showDefaultAppPrompt = false
                    openWindowsDefaultAppSettings()
                }) { Text("Open Windows settings") }
            },
            dismissButton = {
                TextButton(onClick = { markDesktopDefaultAppPromptSeen(); showDefaultAppPrompt = false }) { Text("Not now") }
            }
        )
    }

    CompositionLocalProvider(LocalPdfRenderScheduler provides renderScheduler) {
    Scaffold(
        modifier = Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val action = shortcutRegistry.resolve(
                event,
                ShortcutContext(
                    mode = if (activeTab == WorkspaceTab.Edit) EditorMode.ObjectEdit else EditorMode.Browse,
                    hasDocument = document != null,
                    canUndo = editor.history.canUndo,
                    canRedo = editor.history.canRedo,
                    hasSelection = !editor.selectionModel.current.isEmpty
                )
            )
            when (action) {
                ShortcutAction.Open -> { requestPdfOpen(::open); true }
                ShortcutAction.Search -> { searchFocusRequester.requestFocus(); true }
                ShortcutAction.Undo -> { if (editor.undo()) { document = editor.document; editorRevision++; statusMessage = "Undo" }; true }
                ShortcutAction.Redo -> { if (editor.redo()) { document = editor.document; editorRevision++; statusMessage = "Redo" }; true }
                ShortcutAction.Copy -> { statusMessage = if (editor.copySelection()) "Copied" else "Nothing selectable"; true }
                ShortcutAction.Cut -> {
                    val ok = editor.cutSelection(); document = editor.document; editorRevision++; statusMessage = if (ok) "Cut" else "Nothing editable selected"; true
                }
                ShortcutAction.Paste -> {
                    val ok = editor.paste(); document = editor.document; editorRevision++; statusMessage = if (ok) "Pasted" else "Clipboard content is unsupported"; true
                }
                ShortcutAction.Delete -> {
                    val ok = editor.deleteSelection(); document = editor.document; editorRevision++; if (ok) editor.selectionModel.clear(); statusMessage = if (ok) "Deleted" else "Nothing selected"; true
                }
                ShortcutAction.SelectAll -> {
                    val p = document?.pages?.getOrNull(selectedPage); val ids = p?.objects?.map { it.id }.orEmpty(); if (p != null && ids.isNotEmpty()) editor.selectionModel.selectObjects(selectedPage, ids, null, true, true); editorRevision++; statusMessage = if (ids.isNotEmpty()) "All objects selected" else "No objects on page"; true
                }
                ShortcutAction.NextSearch -> { jumpSearch(1); true }
                ShortcutAction.PreviousSearch -> { jumpSearch(-1); true }
                ShortcutAction.PreviousPage, ShortcutAction.MoveSelectionLeft -> { if (activeTab != WorkspaceTab.Edit) selectedPage = (selectedPage - 1).coerceAtLeast(0); else editor.moveObject(selectedPage, editor.selectionModel.current.selectedIds.firstOrNull() ?: return@onPreviewKeyEvent false, -2f, 0f).also { document = editor.document; editorRevision++ }; true }
                ShortcutAction.NextPage, ShortcutAction.MoveSelectionRight -> { if (activeTab != WorkspaceTab.Edit) selectedPage = (selectedPage + 1).coerceAtMost((info?.pageCount ?: 1) - 1); else editor.moveObject(selectedPage, editor.selectionModel.current.selectedIds.firstOrNull() ?: return@onPreviewKeyEvent false, 2f, 0f).also { document = editor.document; editorRevision++ }; true }
                ShortcutAction.MoveSelectionUp -> { editor.selectionModel.current.selectedIds.firstOrNull()?.let { id -> editor.moveObject(selectedPage, id, 0f, -2f); document = editor.document; editorRevision++ }; true }
                ShortcutAction.MoveSelectionDown -> { editor.selectionModel.current.selectedIds.firstOrNull()?.let { id -> editor.moveObject(selectedPage, id, 0f, 2f); document = editor.document; editorRevision++ }; true }
                ShortcutAction.FirstPage -> { selectedPage = 0; true }
                ShortcutAction.LastPage -> { selectedPage = ((info?.pageCount ?: 1) - 1).coerceAtLeast(0); true }
                ShortcutAction.Save -> { saveCurrentDocument(); true }
                ShortcutAction.SaveAs -> { beginSaveAs(); true }
                ShortcutAction.Print -> { beginPrint(); true }
                ShortcutAction.Close, ShortcutAction.New -> { statusMessage = "${action.name} workflow is not available while a document is open"; true }
                else -> false
            }
        },
        topBar = {
            Column {
                if (viewMode != ViewMode.Presentation) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "PDF")
                        Column(Modifier.widthIn(max = 260.dp)) {
                            Text(document?.name ?: "PDF Everything", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                when {
                                    isLoading -> "Opening document…"
                                    info == null -> "Offline PDF workspace"
                                    else -> "${info!!.pageCount} pages • ${document?.title?.ifBlank { "Untitled" } ?: "Untitled"}"
                                },
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (info != null) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                placeholder = { Text("Search document") },
                                modifier = Modifier.width(250.dp).height(54.dp).focusRequester(searchFocusRequester)
                            )
                            Button(onClick = { jumpSearch(-1) }, enabled = searchMatches.isNotEmpty()) { Text("‹") }
                            Text(
                                if (searchMatches.isEmpty()) "0 / 0" else "${currentSearchIndex + 1} / ${searchMatches.size}",
                                fontSize = 12.sp
                            )
                            Button(onClick = { jumpSearch(1) }, enabled = searchMatches.isNotEmpty()) { Text("›") }
                            Box {
                                IconButton(onClick = { showSearchOptions = !showSearchOptions }) {
                                    Icon(Icons.Default.GridView, contentDescription = "Search options")
                                }
                                DropdownMenu(expanded = showSearchOptions, onDismissRequest = { showSearchOptions = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Case sensitive") },
                                        leadingIcon = { Checkbox(searchOptions.caseSensitive, null) },
                                        onClick = { searchOptions = searchOptions.copy(caseSensitive = !searchOptions.caseSensitive) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Whole word") },
                                        leadingIcon = { Checkbox(searchOptions.wholeWord, null) },
                                        onClick = { searchOptions = searchOptions.copy(wholeWord = !searchOptions.wholeWord) }
                                    )
                                }
                            }
                            if (isSearching) LinearProgressIndicator(Modifier.width(60.dp))
                        }
                        Button(onClick = { requestPdfOpen(::open) }) { Text("Open PDF") }
                        if (info != null) {
                            Button(onClick = { saveCurrentDocument() }) { Text("Save") }
                            Button(onClick = { beginSaveAs() }) { Text("Save As") }
                            Button(onClick = { beginPrint() }) { Text("Print") }
                        }
                        if (info != null) Button(onClick = { activeTab = WorkspaceTab.Edit }) { Text("Edit") }
                        if (info != null) Button(onClick = { activeTab = WorkspaceTab.Phase5 }) { Text("Review") }
                    }
                    statusMessage?.let { message ->
                        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    TabRow(selectedTabIndex = WorkspaceTab.entries.indexOf(activeTab)) {
                        WorkspaceTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == WorkspaceTab.entries.indexOf(activeTab),
                                onClick = { activeTab = tab },
                                text = { Text(when (tab) { WorkspaceTab.Home -> "Home"; WorkspaceTab.View -> "Viewer"; WorkspaceTab.Edit -> "Edit"; WorkspaceTab.Cut -> "Cut"; WorkspaceTab.Organize -> "Organize"; WorkspaceTab.Phase5 -> "Review" }) }
                            )
                        }
                    }
                    if (info != null && activeTab == WorkspaceTab.View) {
                        ViewerToolbar(
                            selectedPage = selectedPage,
                            pageCount = info!!.pageCount,
                            zoom = zoom,
                            viewMode = viewMode,
                            fitMode = fitMode,
                            zoomInput = zoomInput,
                            onPrevious = { selectedPage = (selectedPage - 1).coerceAtLeast(0) },
                            onNext = { selectedPage = (selectedPage + 1).coerceAtMost(info!!.pageCount - 1) },
                            onZoomOut = { zoom = (zoom - 0.1f).coerceAtLeast(0.25f); fitMode = FitMode.Actual; zoomInput = "${(zoom * 100).toInt()}" },
                            onZoomIn = { zoom = (zoom + 0.1f).coerceAtMost(6f); fitMode = FitMode.Actual; zoomInput = "${(zoom * 100).toInt()}" },
                            onFit = { fitMode = FitMode.Page },
                            onFitWidth = { fitMode = FitMode.Width },
                            onFitHeight = { fitMode = FitMode.Height },
                            onActual = { fitMode = FitMode.Actual; zoom = 1f; zoomInput = "100" },
                            onRotateView = { viewRotation = (viewRotation + 90) % 360 },
                            onZoomInput = { value ->
                                zoomInput = value.filter(Char::isDigit).take(3)
                                value.toIntOrNull()?.let {
                                    zoom = (it / 100f).coerceIn(0.25f, 6f)
                                    fitMode = FitMode.Actual
                                }
                            },
                            onMode = { viewMode = it },
                            onToggleReading = onToggleReading,
                            viewRotation = viewRotation,
                            darkReading = darkReading
                        )
                        Divider()
                    }
                }
            }
        },
        content = { padding ->
        when {
            viewMode == ViewMode.Presentation && info != null -> PresentationViewer(
                modifier = Modifier.fillMaxSize().padding(padding),
                engine = engine,
                info = info!!,
                pageIndex = selectedPage,
                cache = cache,
                onPrevious = { selectedPage = (selectedPage - 1).coerceAtLeast(0) },
                onNext = { selectedPage = (selectedPage + 1).coerceAtMost(info!!.pageCount - 1) },
                hits = currentHits,
                onExit = { viewMode = ViewMode.Single }
            )
            activeTab == WorkspaceTab.Edit && document != null && info != null -> Phase3EditorScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                engine = engine,
                info = info!!,
                document = document!!,
                editor = editor,
                selectedPage = selectedPage,
                zoom = zoom,
                cache = cache,
                revision = editorRevision,
                onChanged = { document = editor.document; editorRevision++; statusMessage = "Edited — unsaved" },
                onPageChanged = { selectedPage = it }
            )
            activeTab == WorkspaceTab.Cut && document != null && info != null -> Phase4Workspace(
                document = document!!,
                engine = engine,
                selectedPage = selectedPage.coerceIn(0, document!!.pageCount - 1),
                cache = cache,
                editor = editor,
                onChanged = {
                    document = editor.document
                    selectedPage = selectedPage.coerceIn(0, (editor.document?.pageCount ?: 1) - 1)
                    editorRevision++
                    cache.clear()
                    statusMessage = "CUT change applied - unsaved"
                },
                onPageChanged = { selectedPage = it.coerceIn(0, (document?.pageCount ?: 1) - 1) },
                onImportPdf = ::importPdfForPhase4
            )
            activeTab == WorkspaceTab.Organize && document != null && info != null -> Phase4Workspace(
                document = document!!,
                engine = engine,
                selectedPage = selectedPage.coerceIn(0, document!!.pageCount - 1),
                cache = cache,
                editor = editor,
                startTool = CutTool.Extract,
                onChanged = { document = editor.document; editorRevision++; cache.clear(); statusMessage = "Organizer change applied - unsaved" },
                onPageChanged = { selectedPage = it.coerceIn(0, (document?.pageCount ?: 1) - 1) },
                onImportPdf = ::importPdfForPhase4
            )
            activeTab == WorkspaceTab.Phase5 && document != null && info != null -> Phase5Workspace(
                modifier = Modifier.fillMaxSize().padding(padding),
                document = document!!,
                info = info!!,
                selectedPage = selectedPage.coerceIn(0, document!!.pageCount - 1),
                editor = editor,
                engine = engine,
                onChanged = { message -> document = editor.document; editorRevision++; cache.clear(); statusMessage = "$message — unsaved" },
                onPageChanged = { selectedPage = it.coerceIn(0, (document?.pageCount ?: 1) - 1) }
            )
            activeTab == WorkspaceTab.Home -> HomeScreen(
                Modifier.fillMaxSize().padding(padding),
                document,
                info,
                error,
                isLoading,
                recentFiles,
                onOpen = { requestPdfOpen(::open) },
                onOpenPage = { selectedPage = it; activeTab = WorkspaceTab.View },
                onOpenRecent = ::open,
                defaultAssociationStatus = defaultAssociationStatus,
                onOpenDefaultSettings = { openWindowsDefaultAppSettings(); defaultAssociationStatus = defaultPdfAssociationStatus() }
            )
            else -> ViewerScreen(
                Modifier.fillMaxSize().padding(padding),
                engine,
                info,
                selectedPage,
                zoom,
                fitMode,
                viewMode,
                cache,
                currentHits,
                onSelectPage = { selectedPage = it },
                onEnterPresentation = { viewMode = ViewMode.Presentation },
                viewRotation = viewRotation
            )
        }
        }
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    document: Document?,
    info: PdfDocumentInfo?,
    error: String?,
    isLoading: Boolean,
    recentFiles: List<RecentFile>,
    onOpen: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onOpenRecent: (DocumentSource) -> Unit,
    defaultAssociationStatus: DefaultPdfAssociationStatus,
    onOpenDefaultSettings: () -> Unit
) {
    Box(modifier.background(MaterialTheme.colorScheme.surface)) {
        when {
            isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            error != null -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Could not open PDF", style = MaterialTheme.typography.titleLarge)
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onOpen) { Text("Try another PDF") }
            }
            document == null || info == null -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("PDF Everything", style = MaterialTheme.typography.headlineMedium)
                Text("Offline-first PDF foundation + viewer")
                Button(onClick = onOpen) { Text("Choose PDF") }
                if (isDesktop) {
                    Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Windows PDF association", style = MaterialTheme.typography.titleMedium)
                            Text(when (defaultAssociationStatus) {
                                DefaultPdfAssociationStatus.DEFAULT_APP -> "PDF Everything is the current default PDF app."
                                DefaultPdfAssociationStatus.REGISTERED_NOT_DEFAULT -> "Registered with Windows; another app is currently the default."
                                DefaultPdfAssociationStatus.NOT_REGISTERED -> "PDF Everything is not registered as a PDF app."
                                DefaultPdfAssociationStatus.UNKNOWN -> "Windows did not expose the current default status."
                                DefaultPdfAssociationStatus.UNSUPPORTED -> "Windows default-app integration is unavailable on this platform."
                            })
                            TextButton(onClick = onOpenDefaultSettings) { Text("Open Default Apps settings") }
                        }
                    }
                }
                if (recentFiles.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium)
                    recentFiles.take(5).forEach { recent ->
                        Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().clickable { onOpenRecent(recent.source) }) {
                            Text(recent.name, Modifier.padding(12.dp))
                        }
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(document.name, style = MaterialTheme.typography.headlineSmall)
                Text("${info.pageCount} pages")
                Surface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Document metadata", style = MaterialTheme.typography.titleMedium)
                        Text("Title: ${info.title.ifBlank { "—" }}")
                        Text("Author: ${info.author.ifBlank { "—" }}")
                        Text("Subject: ${info.subject.ifBlank { "—" }}")
                        Text("Producer: ${info.producer.ifBlank { "—" }}")
                        Text("Encrypted: ${if (info.encrypted) "Yes" else "No"}")
                    }
                }
                Button(onClick = { onOpenPage(0) }) { Text("Open in Viewer") }
            }
        }
    }
}

@Composable
private fun ViewerToolbar(
    selectedPage: Int,
    pageCount: Int,
    zoom: Float,
    viewMode: ViewMode,
    fitMode: FitMode,
    zoomInput: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFit: () -> Unit,
    onFitWidth: () -> Unit,
    onFitHeight: () -> Unit,
    onActual: () -> Unit,
    onZoomInput: (String) -> Unit,
    onMode: (ViewMode) -> Unit,
    onToggleReading: () -> Unit,
    darkReading: Boolean,
    onRotateView: () -> Unit,
    viewRotation: Int
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        IconButton(enabled = selectedPage > 0, onClick = onPrevious) { Icon(Icons.Default.ArrowBack, "Previous page") }
        IconButton(enabled = selectedPage < pageCount - 1, onClick = onNext) { Icon(Icons.Default.ArrowForward, "Next page") }
        Text("Page ${selectedPage + 1} / $pageCount", fontSize = 12.sp)
        IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, "Zoom out") }
        OutlinedTextField(value = zoomInput, onValueChange = onZoomInput, singleLine = true, suffix = { Text("%") }, modifier = Modifier.width(86.dp).height(50.dp))
        IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, "Zoom in") }
        FitButton("Page", fitMode == FitMode.Page, onFit)
        FitButton("Width", fitMode == FitMode.Width, onFitWidth)
        FitButton("Height", fitMode == FitMode.Height, onFitHeight)
        FitButton("100%", fitMode == FitMode.Actual, onActual)
        Spacer(Modifier.weight(1f))
        ViewMode.entries.forEach { mode ->
            Button(onClick = { onMode(mode) }) {
                Text(when (mode) { ViewMode.Single -> "Single"; ViewMode.Continuous -> "Continuous"; ViewMode.TwoPage -> "Two-page"; ViewMode.Organizer -> "Organizer"; ViewMode.Presentation -> "Presentation" }, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onRotateView) {
            Text("↻", fontSize = 20.sp)
        }
        IconButton(onClick = onToggleReading) {
            Icon(if (darkReading) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle reading mode")
        }
    }
}

@Composable
private fun FitButton(label: String, active: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(label, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ViewerScreen(
    modifier: Modifier,
    engine: PdfEngine,
    info: PdfDocumentInfo?,
    selectedPage: Int,
    zoom: Float,
    fitMode: FitMode,
    viewMode: ViewMode,
    cache: RenderMemoryCache,
    hits: List<SearchMatch>,
    onSelectPage: (Int) -> Unit,
    onEnterPresentation: () -> Unit,
    viewRotation: Int
) {
    if (info == null) {
        Box(modifier) { Text("Open a PDF to use the viewer", Modifier.align(Alignment.Center)) }
        return
    }
    Row(modifier) {
        if (viewMode != ViewMode.Presentation) {
            ThumbnailPane(engine, info.pages, selectedPage, cache, onSelectPage)
            Divider(Modifier.fillMaxHeight().width(1.dp))
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            when (viewMode) {
                ViewMode.Single -> SinglePageViewer(engine, info, selectedPage, zoom, fitMode, cache, hits, viewRotation)
                ViewMode.Continuous -> ContinuousViewer(engine, info, zoom, fitMode, cache, selectedPage, onSelectPage, hits, viewRotation)
                ViewMode.TwoPage -> TwoPageViewer(engine, info, selectedPage, zoom, fitMode, cache, onSelectPage, hits, viewRotation)
                ViewMode.Organizer -> OrganizerViewer(engine, info, cache, selectedPage, onSelectPage, viewRotation)
                ViewMode.Presentation -> PresentationViewer(Modifier.fillMaxSize(), engine, info, selectedPage, cache, {}, {}, hits, onEnterPresentation)
            }
        }
    }
}

@Composable
private fun ThumbnailPane(engine: PdfEngine, pages: List<PdfPageInfo>, selectedPage: Int, cache: RenderMemoryCache, onSelect: (Int) -> Unit) {
    Column(Modifier.width(214.dp).fillMaxHeight().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Thumbnails", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text("${pages.size}", fontSize = 11.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            itemsIndexed(pages, key = { _, it -> it.pageId }) { _, page ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onSelect(page.index) }.border(if (page.index == selectedPage) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                    tonalElevation = if (page.index == selectedPage) 3.dp else 0.dp
                ) {
                    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PdfPageImage(engine, page, 120, cache, thumbnail = true, hits = emptyList(), Modifier.fillMaxWidth().height(138.dp))
                        Text("${page.index + 1} • ${page.rotation}°", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePageViewer(engine: PdfEngine, info: PdfDocumentInfo, pageIndex: Int, zoom: Float, fitMode: FitMode, cache: RenderMemoryCache, hits: List<SearchMatch>, viewRotation: Int) {
    val page = info.pages[pageIndex]
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val fit = calculateFitZoom(page, viewportW, viewportH, fitMode)
        val activeZoom = (if (fitMode == FitMode.Actual) zoom else fit).coerceIn(0.25f, 6f)
        PrefetchNeighbors(engine, info, pageIndex, activeZoom, cache)
        ZoomablePdfSurface(engine, page, activeZoom, cache, hits, viewRotation)
    }
}

@Composable
private fun PrefetchNeighbors(engine: PdfEngine, info: PdfDocumentInfo, pageIndex: Int, zoom: Float, cache: RenderMemoryCache) {
    LaunchedEffect(info.documentVersion, pageIndex, zoom) {
        val width = (info.pages[pageIndex].width * (96f / 72f) * zoom * 0.55f).toInt().coerceIn(180, 900)
        val scheduler = LocalPdfRenderScheduler.current
        listOf(pageIndex - 1, pageIndex + 1).filter { it in 0 until info.pageCount }.forEach { neighbor ->
            val page = info.pages[neighbor]
            val key = "${page.documentVersion}|${page.pageId}|${width}|prefetch"
            if (scheduler != null) {
                scheduler.request(neighbor, RenderViewport(width, scale = width / page.width, cacheKey = key), RenderPriority.NEIGHBOR)
            } else if (cache.page(key) == null) {
                runCatching { engine.renderPage(neighbor, RenderViewport(width, scale = width / page.width, cacheKey = key)) }
                    .onSuccess { if (it.documentVersion == page.documentVersion && it.viewportKey == key) cache.putPage(key, it) }
            }
        }
    }
}

@Composable
private fun ContinuousViewer(engine: PdfEngine, info: PdfDocumentInfo, zoom: Float, fitMode: FitMode, cache: RenderMemoryCache, selectedPage: Int, onSelectPage: (Int) -> Unit, hits: List<SearchMatch>, viewRotation: Int) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(selectedPage) { state.animateScrollToItem(selectedPage) }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(info.pages, key = { it.pageId }) { page ->
            Column(Modifier.fillMaxWidth().clickable { onSelectPage(page.index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Page ${page.index + 1}", fontSize = 12.sp)
                PdfPageImage(engine, page, (page.width * zoom).toInt().coerceIn(240, 2400), cache, false, hits.filter { it.pageIndex == page.index }, Modifier.fillMaxWidth().widthIn(max = 1100.dp).aspectRatio(page.width / page.height).graphicsLayer(rotationZ = viewRotation.toFloat()))
            }
        }
    }
}

@Composable
private fun TwoPageViewer(engine: PdfEngine, info: PdfDocumentInfo, selectedPage: Int, zoom: Float, fitMode: FitMode, cache: RenderMemoryCache, onSelectPage: (Int) -> Unit, hits: List<SearchMatch>, viewRotation: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth.value < 850f) {
            SinglePageViewer(engine, info, selectedPage, zoom, fitMode, cache, hits, viewRotation)
        } else {
            val left = if (selectedPage % 2 == 0) selectedPage else selectedPage - 1
            LazyColumn(Modifier.fillMaxSize().padding(18.dp)) {
                item(key = "spread-$left") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        listOf(left, left + 1).forEach { index ->
                            if (index < info.pageCount) {
                                val page = info.pages[index]
                                Column(Modifier.weight(1f).clickable { onSelectPage(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Page ${index + 1}", fontSize = 12.sp)
                                    PdfPageImage(engine, page, (page.width * zoom).toInt().coerceIn(220, 1600), cache, false, hits.filter { it.pageIndex == index }, Modifier.fillMaxWidth().aspectRatio(page.width / page.height).graphicsLayer(rotationZ = viewRotation.toFloat()))
                                }
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizerViewer(engine: PdfEngine, info: PdfDocumentInfo, cache: RenderMemoryCache, selectedPage: Int, onSelectPage: (Int) -> Unit, viewRotation: Int) {
    LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        gridItems(info.pages, key = { it.pageId }) { page ->
            Surface(Modifier.clickable { onSelectPage(page.index) }.border(if (page.index == selectedPage) 2.dp else 1.dp, if (page.index == selectedPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PdfPageImage(engine, page, 230, cache, true, emptyList(), Modifier.fillMaxWidth().aspectRatio(page.width / page.height).graphicsLayer(rotationZ = viewRotation.toFloat()))
                    Text("Page ${page.index + 1}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PresentationViewer(modifier: Modifier, engine: PdfEngine, info: PdfDocumentInfo, pageIndex: Int, cache: RenderMemoryCache, onPrevious: () -> Unit, onNext: () -> Unit, hits: List<SearchMatch>, onExit: () -> Unit) {
    BoxWithConstraints(modifier.background(Color.Black)) {
        val page = info.pages[pageIndex]
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PdfPageImage(engine, page, (maxWidth.value * 2.0f).toInt().coerceIn(400, 3200), cache, false, hits, Modifier.fillMaxWidth().padding(12.dp).aspectRatio(page.width / page.height))
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPrevious, enabled = pageIndex > 0) { Text("Previous") }
                Text("${pageIndex + 1} / ${info.pageCount}", color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                Button(onClick = onNext, enabled = pageIndex < info.pageCount - 1) { Text("Next") }
                Button(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

@Composable
private fun ZoomablePdfSurface(engine: PdfEngine, page: PdfPageInfo, zoom: Float, cache: RenderMemoryCache, hits: List<SearchMatch>, viewRotation: Int) {
    var gestureZoom by remember(page.pageId) { mutableStateOf(zoom) }
    LaunchedEffect(zoom) { gestureZoom = zoom }
    val effectiveZoom = gestureZoom.coerceIn(0.25f, 6f)
    val density = LocalDensity.current
    val widthPx = (page.width * (96f / 72f) * effectiveZoom).toInt().coerceIn(240, 3600)
    val heightPx = (page.height * (96f / 72f) * effectiveZoom).toInt().coerceAtLeast(1)
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val rotated90 = viewRotation % 180 != 0
    val surfaceWidth = if (rotated90) heightDp else widthDp
    val surfaceHeight = if (rotated90) widthDp else heightDp
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val gestureScope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().pointerInput(page.pageId) {
        detectTransformGestures { centroid, pan, zoomChange, _ ->
            val oldZoom = gestureZoom
            val newZoom = (oldZoom * zoomChange).coerceIn(0.25f, 6f)
            val ratio = if (oldZoom == 0f) 1f else newZoom / oldZoom
            gestureZoom = newZoom
            gestureScope.launch {
                withFrameNanos { }
                horizontal.scrollBy(centroid.x * (ratio - 1f) - pan.x)
                vertical.scrollBy(centroid.y * (ratio - 1f) - pan.y)
            }
        }
    }.pointerInput(page.pageId) {
        detectTapGestures(onDoubleTap = { gestureZoom = if (gestureZoom < 1.75f) 2f else 1f })
    }) {
        Box(Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical), contentAlignment = Alignment.Center) {
            PdfPageImage(engine, page, widthPx, cache, false, hits, Modifier.width(surfaceWidth).height(surfaceHeight).graphicsLayer(rotationZ = viewRotation.toFloat()))
        }
    }
}

@Composable
private fun PdfPageImage(engine: PdfEngine, page: PdfPageInfo, width: Int, cache: RenderMemoryCache, thumbnail: Boolean, hits: List<SearchMatch>, modifier: Modifier) {
    val key = "${page.documentVersion}|${page.pageId}|${width}|${if (thumbnail) "thumb" else "page"}"
    var rendered by remember(key) { mutableStateOf(if (thumbnail) cache.thumbnail(key) else cache.page(key)) }
    var failed by remember(key) { mutableStateOf<String?>(null) }
    val scheduler = LocalPdfRenderScheduler.current
    LaunchedEffect(key, scheduler) {
        if (rendered == null) {
            val viewport = RenderViewport(widthPx = width, scale = width / page.width, cacheKey = key)
            if (scheduler != null) {
                scheduler.request(
                    pageIndex = page.index, viewport = viewport,
                    priority = if (thumbnail) RenderPriority.THUMBNAIL else RenderPriority.CURRENT,
                    thumbnail = thumbnail,
                    onResult = { result ->
                        if (result.documentVersion == page.documentVersion && result.viewportKey == key) rendered = result
                    },
                    onError = { failed = it.message ?: "Render failed" }
                )
            } else {
                runCatching { withContext(Dispatchers.Default) { engine.renderPage(page.index, viewport) } }
                    .onSuccess { result ->
                        if (result.documentVersion != page.documentVersion || result.viewportKey != key) return@onSuccess
                        if (thumbnail) cache.putThumbnail(key, result) else cache.putPage(key, result)
                        rendered = result
                    }.onFailure { failed = it.message ?: "Render failed" }
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        when {
            rendered != null -> {
                val bitmap: ImageBitmap = Image.makeFromEncoded(rendered!!.png).toComposeImageBitmap()
                Box(Modifier.fillMaxSize()) {
                    Image(bitmap, "PDF page ${page.index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (hits.isNotEmpty() && !thumbnail) SearchHighlightLayer(page, hits)
                }
            }
            failed != null -> Text("Render error\n$failed", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            else -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

@Composable
private fun SearchHighlightLayer(page: PdfPageInfo, hits: List<SearchMatch>) {
    Canvas(Modifier.fillMaxSize().alpha(0.72f)) {
        val sx = size.width / page.width.coerceAtLeast(1f)
        val sy = size.height / page.height.coerceAtLeast(1f)
        hits.forEach { hit ->
            hit.rects.forEach { rect ->
                if (rect.right <= 0f && rect.bottom <= 0f) return@forEach
                drawRect(Color.Yellow.copy(alpha = 0.42f), topLeft = androidx.compose.ui.geometry.Offset(rect.left * sx, rect.top * sy), size = androidx.compose.ui.geometry.Size((rect.right - rect.left) * sx, (rect.bottom - rect.top) * sy))
            }
        }
    }
}

private fun calculateFitZoom(page: PdfPageInfo, containerWidthPx: Float, containerHeightPx: Float, mode: FitMode): Float {
    if (mode == FitMode.Actual) return 1f
    val actualWidthPx = page.width.coerceAtLeast(1f) * (96f / 72f)
    val actualHeightPx = page.height.coerceAtLeast(1f) * (96f / 72f)
    val horizontal = containerWidthPx / actualWidthPx
    val vertical = containerHeightPx / actualHeightPx
    return when (mode) {
        FitMode.Page -> minOf(horizontal, vertical)
        FitMode.Width -> horizontal
        FitMode.Height -> vertical
        FitMode.Actual -> 1f
    }.coerceIn(0.25f, 6f)
}

private fun currentTimeMillisCompat(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

@Composable
private fun PrintSettingsDialog(pageCount: Int, onCancel: () -> Unit, onPrint: (PrintRequest) -> Unit) {
    var rangeText by remember(pageCount) { mutableStateOf("1-$pageCount") }
    var copies by remember { mutableStateOf("1") }
    var paperSize by remember { mutableStateOf(PrintPaperSize.A4) }
    var landscape by remember { mutableStateOf(false) }
    var scaling by remember { mutableStateOf(PrintScaling.FIT_TO_PRINTABLE_AREA) }
    var customScale by remember { mutableStateOf("100") }
    var parity by remember { mutableStateOf(PrintPageParity.ALL) }
    var color by remember { mutableStateOf(true) }
    fun parseRanges(value: String): List<PageRange> {
        return value.split(',').flatMap { token ->
            val t = token.trim()
            if (t.isBlank()) emptyList() else if ('-' in t) {
                val pair = t.split('-', limit = 2)
                val a = pair.getOrNull(0)?.trim()?.toIntOrNull()?.minus(1)
                val b = pair.getOrNull(1)?.trim()?.toIntOrNull()?.minus(1)
                if (a != null && b != null && a >= 0 && b >= a) listOf(PageRange(a, b)) else emptyList()
            } else t.toIntOrNull()?.minus(1)?.takeIf { it >= 0 }?.let { listOf(PageRange(it, it)) }.orEmpty()
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Print") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rangeText, { rangeText = it }, label = { Text("Pages (e.g. 1-3,5)") }, singleLine = true)
                OutlinedTextField(copies, { copies = it.filter(Char::isDigit).take(3) }, label = { Text("Copies") }, singleLine = true)
                Text("Paper")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { paperSize = PrintPaperSize.A4 }) { Text("A4") }
                    TextButton(onClick = { paperSize = PrintPaperSize.LETTER }) { Text("Letter") }
                    TextButton(onClick = { paperSize = PrintPaperSize.LEGAL }) { Text("Legal") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(landscape, { landscape = it }); Text("Landscape") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(color, { color = it }); Text("Color") }
                Text("Scaling")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { scaling = PrintScaling.FIT_TO_PRINTABLE_AREA }) { Text("Fit") }
                    TextButton(onClick = { scaling = PrintScaling.ACTUAL_SIZE }) { Text("Actual") }
                    TextButton(onClick = { scaling = PrintScaling.CUSTOM }) { Text("Custom") }
                }
                if (scaling == PrintScaling.CUSTOM) OutlinedTextField(customScale, { customScale = it.filter(Char::isDigit).take(3) }, label = { Text("Scale %") }, singleLine = true)
                Text("Pages parity")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { parity = PrintPageParity.ALL }) { Text("All") }
                    TextButton(onClick = { parity = PrintPageParity.ODD }) { Text("Odd") }
                    TextButton(onClick = { parity = PrintPageParity.EVEN }) { Text("Even") }
                }
                Text("The next step opens the system printer dialog.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            onPrint(PrintRequest(parseRanges(rangeText), copies.toIntOrNull()?.coerceIn(1, 999) ?: 1, paperSize, landscape, scaling = scaling, customScalePercent = customScale.toIntOrNull()?.coerceIn(10, 400) ?: 100, parity = parity, color = color))
        }) { Text("Print") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
