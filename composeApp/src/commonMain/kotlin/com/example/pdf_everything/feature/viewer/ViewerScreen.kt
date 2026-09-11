package com.example.pdf_everything.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.PageRotation
import com.example.pdf_everything.core.search.SearchEngine
import com.example.pdf_everything.core.search.SearchResult
import com.example.pdf_everything.core.services.PdfEngine
import com.example.pdf_everything.core.services.RenderConfig
import com.example.pdf_everything.ui.design_system.Spacing
import com.example.pdf_everything.ui.design_system.PdfIcons
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
 *  ViewerScreen — Main PDF viewer screen per spec §15–§68
 *
 *  Integrates all viewer components:
 *    - ViewportState (zoom + scroll + page position)
 *    - ViewModeState (single/continuous/two-page/organizer/presentation)
 *    - RenderScheduler (background page rendering)
 *    - PageCache (LRU cached bitmaps)
 *    - ThumbnailPanel (side panel with page thumbnails)
 *    - PageCanvas (main page rendering surface)
 *    - ZoomControlsBar (zoom UI)
 *    - SearchBar (search UI)
 *    - PageNavigationBar (page navigation)
 * ═══════════════════════════════════════════════════════════════════════ */

@Composable
fun ViewerScreen(
    document: Document?,
    pdfEngine: PdfEngine?,
    onBack: () -> Unit,
    onEdit: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (document == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    PdfIcons.Pdf,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "No document loaded",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Open a PDF file to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val pages = document.pages
    val totalPages = pages.size

    // ── Core state ─────────────────────────────────────────────
    val viewportState = remember { ViewportState() }
    val viewModeState = remember { ViewModeState() }
    val pageCache = remember { PageCache() }
    val thumbnailCache = remember { ThumbnailCache() }
    val searchUIState = remember { SearchUIState() }

    // ── Render pipeline ────────────────────────────────────────
    val renderScheduler = remember {
        RenderScheduler(
            maxConcurrentRenders = 4,
            pdfEngine = pdfEngine
        )
    }

    val scope = rememberCoroutineScope()

    // Update viewport with document page geometry
    LaunchedEffect(document.documentId, viewportState.currentPage) {
        viewportState.updatePageCount(totalPages)
        if (viewportState.currentPage in pages.indices) {
            viewportState.updatePageGeometry(pages[viewportState.currentPage])
        }
        viewportState.goToPage(0)
        for (i in 0 until minOf(3, totalPages)) {
            renderScheduler.requestRender(
                pageIndex = i,
                config = RenderConfig(
                    dpi = 150f,
                    scale = viewportState.zoomScale,
                    rotation = pages[i].rotation,
                    renderAnnotations = true,
                    renderForms = true,
                    grayscale = false
                )
            )
        }
    }

    // Keep viewport geometry in sync with current page
    LaunchedEffect(viewportState.currentPage) {
        if (viewportState.currentPage in pages.indices) {
            viewportState.updatePageGeometry(pages[viewportState.currentPage])
        }
    }

    // ── Search integration ────────────────────────────────────
    val searchEngine = remember { SearchEngine() }
    var searchHighlights by remember { mutableStateOf(emptyList<SearchHighlight>()) }

    fun performSearch() {
        val state = searchUIState.searchState
        if (state.query.isBlank()) return
        searchUIState.setSearching(true)
        scope.launch {
            val results = searchEngine.search(
                document = document,
                query = state.query,
                caseSensitive = state.caseSensitive,
                wholeWord = state.wholeWord
            )
            searchUIState.setSearchResults(results)
            searchHighlights = results.map { result ->
                SearchHighlight(
                    pageIndex = result.pageIndex,
                    matchStart = result.matchStart,
                    matchEnd = result.matchEnd,
                    matchedText = result.matchedText
                )
            }
        }
    }

    // ── Handle zoom changes ───────────────────────────────────
    fun handleZoomModeChange(mode: ZoomMode) {
        viewportState.setZoomMode(mode)
        scope.launch {
            val currentPage = viewportState.currentPage
            if (currentPage in pages.indices) {
                renderScheduler.requestRender(
                    pageIndex = currentPage,
                    config = RenderConfig(
                        dpi = 150f,
                        scale = viewportState.zoomScale,
                        rotation = pages[currentPage].rotation,
                        renderAnnotations = true,
                        renderForms = true,
                        grayscale = false
                    )
                )
            }
        }
    }

    fun handleZoomPercentage(percentage: Int) {
        val scale = percentage / 100f
        viewportState.setZoomScale(scale.coerceIn(viewportState.minZoom, viewportState.maxZoom))
    }

    fun handleGoToPage(pageIndex: Int) {
        viewportState.goToPage(pageIndex.coerceIn(0, totalPages - 1))
        scope.launch {
            val cp = viewportState.currentPage
            if (cp in pages.indices) {
                renderScheduler.requestRender(
                    pageIndex = cp,
                    config = RenderConfig(
                        dpi = 150f,
                        scale = viewportState.zoomScale,
                        rotation = pages[cp].rotation,
                        renderAnnotations = true,
                        renderForms = true,
                        grayscale = false
                    )
                )
            }
        }
    }

    // ── Thumbnail panel state ──────────────────────────────────
    var isThumbnailPanelVisible by remember { mutableStateOf(true) }
    var thumbnailPanelState by remember { mutableStateOf(ThumbnailPanelState()) }

    // ── Cycle view mode helper ───────────────────────────────
    fun cycleViewMode() {
        val modes = ViewMode.entries
        val currentIdx = modes.indexOf(viewModeState.currentMode)
        val nextIdx = (currentIdx + 1) % modes.size
        viewModeState.setMode(modes[nextIdx])
    }

    // ── Main layout ───────────────────────────────────────────
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AnimatedVisibility(
                visible = viewModeState.showChrome,
                enter = slideInVertically(),
                exit = slideOutVertically()
            ) {
                ViewerTopBar(
                    documentTitle = document.metadata.title ?: document.name,
                    viewModeState = viewModeState,
                    searchUIState = searchUIState,
                    isThumbnailPanelVisible = isThumbnailPanelVisible,
                    onBack = onBack,
                    onToggleThumbnails = { isThumbnailPanelVisible = !isThumbnailPanelVisible },
                    onToggleViewMode = { cycleViewMode() },
                    onToggleFullscreen = { viewModeState.toggleFullscreen() },
                    onSearchClick = { searchUIState.showSearchBar() },
                    onEdit = onEdit?.let { { it(document.documentId) } }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = viewModeState.showChrome,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ViewerBottomBar(
                    viewportState = viewportState,
                    totalPages = totalPages,
                    viewModeState = viewModeState,
                    onGoToPage = ::handleGoToPage,
                    onPreviousPage = { viewportState.previousPage() },
                    onNextPage = { viewportState.nextPage() },
                    onFirstPage = { viewportState.firstPage() },
                    onLastPage = { viewportState.lastPage() },
                    onZoomModeChange = ::handleZoomModeChange,
                    onZoomPercentageChange = ::handleZoomPercentage,
                    onZoomIn = { viewportState.zoomIn() },
                    onZoomOut = { viewportState.zoomOut() }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Thumbnail side panel ───────────────────────
            AnimatedVisibility(
                visible = isThumbnailPanelVisible && viewModeState.showChrome,
                enter = slideInHorizontally(),
                exit = slideOutHorizontally()
            ) {
                ThumbnailPanel(
                    pages = pages,
                    currentPageIndex = viewportState.currentPage,
                    thumbnailCache = thumbnailCache,
                    panelState = thumbnailPanelState,
                    onPageClick = { pageIndex -> handleGoToPage(pageIndex) },
                    onPageLongPress = { pageIndex -> thumbnailPanelState.selectPage(pageIndex) },
                    onRequestThumbnail = { pageIndex ->
                        scope.launch {
                            renderScheduler.requestRender(
                                pageIndex = pageIndex,
                                config = RenderConfig(
                                    dpi = 36f,
                                    scale = 1f,
                                    rotation = if (pageIndex in pages.indices) pages[pageIndex].rotation else PageRotation.ROTATION_0,
                                    renderAnnotations = true,
                                    renderForms = true,
                                    grayscale = false
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .width(140.dp)
                        .fillMaxHeight()
                )
            }

            // ── Main content area ──────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar
                    AnimatedVisibility(
                        visible = searchUIState.isSearchBarVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        SearchBar(
                            searchUIState = searchUIState,
                            onSearch = { _, _, _ -> performSearch() },
                            onNextResult = {
                                searchUIState.nextResult()
                                val idx = searchUIState.searchState.currentIndex
                                if (idx >= 0) {
                                    val result = searchUIState.searchState.results.getOrNull(idx)
                                    if (result != null) {
                                        handleGoToPage(result.pageIndex)
                                    }
                                }
                            },
                            onPreviousResult = {
                                searchUIState.previousResult()
                                val idx = searchUIState.searchState.currentIndex
                                if (idx >= 0) {
                                    val result = searchUIState.searchState.results.getOrNull(idx)
                                    if (result != null) {
                                        handleGoToPage(result.pageIndex)
                                    }
                                }
                            },
                            onClose = { searchUIState.hideSearchBar() }
                        )
                    }

                    // ── Page canvas ─────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (viewModeState.currentMode) {
                            ViewMode.SINGLE_PAGE -> {
                                val converter = viewportState.currentConverter()
                                PageCanvas(
                                    page = pages.getOrNull(viewportState.currentPage),
                                    viewportState = viewportState,
                                    pageCache = pageCache,
                                    converter = converter,
                                    searchHighlights = searchHighlights.filter {
                                        it.pageIndex == viewportState.currentPage
                                    },
                                    onZoomChanged = { _, _, _ ->
                                        scope.launch {
                                            val cp = viewportState.currentPage
                                            if (cp in pages.indices) {
                                                renderScheduler.requestRender(
                                                    pageIndex = cp,
                                                    config = RenderConfig(
                                                        dpi = 150f,
                                                        scale = viewportState.zoomScale,
                                                        rotation = pages[cp].rotation,
                                                        renderAnnotations = true,
                                                        renderForms = true,
                                                        grayscale = false
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ViewMode.CONTINUOUS -> {
                                ContinuousPageCanvas(
                                    pages = pages,
                                    viewportState = viewportState,
                                    pageCache = pageCache,
                                    searchHighlights = searchHighlights,
                                    onRequestRender = { pageIndex ->
                                        scope.launch {
                                            if (pageIndex in pages.indices) {
                                                renderScheduler.requestRender(
                                                    pageIndex = pageIndex,
                                                    config = RenderConfig(
                                                        dpi = 150f,
                                                        scale = viewportState.zoomScale,
                                                        rotation = pages[pageIndex].rotation,
                                                        renderAnnotations = true,
                                                        renderForms = true,
                                                        grayscale = false
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onPageChanged = { handleGoToPage(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ViewMode.TWO_PAGE -> {
                                TwoPageCanvas(
                                    pages = pages,
                                    viewportState = viewportState,
                                    pageCache = pageCache,
                                    searchHighlights = searchHighlights,
                                    onRequestRender = { pageIndex ->
                                        scope.launch {
                                            if (pageIndex in pages.indices) {
                                                renderScheduler.requestRender(
                                                    pageIndex = pageIndex,
                                                    config = RenderConfig(
                                                        dpi = 150f,
                                                        scale = viewportState.zoomScale,
                                                        rotation = pages[pageIndex].rotation,
                                                        renderAnnotations = true,
                                                        renderForms = true,
                                                        grayscale = false
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ViewMode.ORGANIZER -> {
                                OrganizerView(
                                    pages = pages,
                                    pageCache = pageCache,
                                    currentPage = viewportState.currentPage,
                                    onPageClick = { handleGoToPage(it) },
                                    onRequestRender = { pageIndex ->
                                        scope.launch {
                                            if (pageIndex in pages.indices) {
                                                renderScheduler.requestRender(
                                                    pageIndex = pageIndex,
                                                    config = RenderConfig(
                                                        dpi = 72f,
                                                        scale = 1f,
                                                        rotation = pages[pageIndex].rotation,
                                                        renderAnnotations = true,
                                                        renderForms = true,
                                                        grayscale = false
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ViewMode.PRESENTATION -> {
                                val converter = viewportState.currentConverter()
                                PageCanvas(
                                    page = pages.getOrNull(viewportState.currentPage),
                                    viewportState = viewportState,
                                    pageCache = pageCache,
                                    converter = converter,
                                    searchHighlights = emptyList(),
                                    onZoomChanged = { _, _, _ -> },
                                    modifier = Modifier.fillMaxSize()
                                )

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = viewModeState.showNavigationOverlay,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    PresentationOverlay(
                                        currentPage = viewportState.currentPage,
                                        totalPages = totalPages,
                                        onNextPage = { viewportState.nextPage() },
                                        onPreviousPage = { viewportState.previousPage() },
                                        onExit = { viewModeState.setMode(ViewMode.SINGLE_PAGE) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── Top bar composable ────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(
    documentTitle: String,
    viewModeState: ViewModeState,
    searchUIState: SearchUIState,
    isThumbnailPanelVisible: Boolean,
    onBack: () -> Unit,
    onToggleThumbnails: () -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSearchClick: () -> Unit,
    onEdit: (() -> Unit)?
) {
    TopAppBar(
        title = {
            Text(
                text = documentTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(PdfIcons.Back, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onToggleThumbnails) {
                Icon(
                    if (isThumbnailPanelVisible) PdfIcons.Menu
                    else PdfIcons.More,
                    contentDescription = "Toggle thumbnails"
                )
            }

            IconButton(onClick = onToggleViewMode) {
                Icon(
                    viewModeIcon(viewModeState.currentMode),
                    contentDescription = "View mode: ${viewModeState.currentMode.name}"
                )
            }

            IconButton(onClick = onSearchClick) {
                Icon(PdfIcons.Search, contentDescription = "Search")
            }

            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(PdfIcons.Edit, contentDescription = "Edit")
                }
            }

            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (viewModeState.isFullscreen) PdfIcons.FullscreenExit
                    else PdfIcons.Fullscreen,
                    contentDescription = "Fullscreen"
                )
            }
        }
    )
}

/* ── Bottom bar composable ──────────────────────────────────────────── */

@Composable
private fun ViewerBottomBar(
    viewportState: ViewportState,
    totalPages: Int,
    viewModeState: ViewModeState,
    onGoToPage: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onFirstPage: () -> Unit,
    onLastPage: () -> Unit,
    onZoomModeChange: (ZoomMode) -> Unit,
    onZoomPercentageChange: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Surface(
        tonalElevation = Spacing.xs,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PageNavigationBar(
                currentPage = viewportState.currentPage,
                totalPages = totalPages,
                onGoToPage = onGoToPage,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onFirstPage = onFirstPage,
                onLastPage = onLastPage
            )

            ZoomControlsBar(
                viewportState = viewportState,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onZoomModeChange = onZoomModeChange,
                onZoomPercentageChange = onZoomPercentageChange
            )
        }
    }
}

/* ── Organizer view (grid of thumbnails) ───────────────────────────── */

@Composable
private fun OrganizerView(
    pages: List<Page>,
    pageCache: PageCache,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onRequestRender: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.padding(Spacing.sm),
        contentPadding = PaddingValues(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(pages.size) { index ->
            val page = pages[index]

            // Request thumbnail render on first composition
            LaunchedEffect(index) {
                onRequestRender(index)
            }

            Surface(
                onClick = { onPageClick(index) },
                shape = MaterialTheme.shapes.small,
                color = if (index == currentPage)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                border = if (index == currentPage)
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else
                    null
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(Spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.77f)
                            .padding(Spacing.xs)
                    ) {
                        // Placeholder for thumbnail
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ── Presentation overlay ──────────────────────────────────────────── */

@Composable
private fun PresentationOverlay(
    currentPage: Int,
    totalPages: Int,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousPage, enabled = currentPage > 0) {
                Icon(PdfIcons.ArrowLeft, contentDescription = "Previous")
            }

            Text(
                text = "${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            IconButton(onClick = onNextPage, enabled = currentPage < totalPages - 1) {
                Icon(PdfIcons.ArrowRight, contentDescription = "Next")
            }

            TextButton(onClick = onExit) {
                Text("Exit", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/* ── Helper ──────────────────────────────────────────────────────── */

private fun viewModeIcon(mode: ViewMode) = when (mode) {
    ViewMode.SINGLE_PAGE -> PdfIcons.SinglePage
    ViewMode.CONTINUOUS -> PdfIcons.Continuous
    ViewMode.TWO_PAGE -> PdfIcons.TwoPage
    ViewMode.ORGANIZER -> PdfIcons.Organizer
    ViewMode.PRESENTATION -> PdfIcons.Presentation
}
