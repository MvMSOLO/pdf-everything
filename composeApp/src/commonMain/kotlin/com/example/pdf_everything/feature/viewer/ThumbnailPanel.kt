package com.example.pdf_everything.feature.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.PageRotation
import com.example.pdf_everything.feature.viewer.CachedThumbnail
import com.example.pdf_everything.ui.design_system.*

/* ═══════════════════════════════════════════════════════════════════════
 *  Thumbnail Panel — per spec §31 & §12
 *
 *  Requirements:
 *    - Lazy-load thumbnails (render on demand, not all at once)
 *    - Cache rendered thumbnails (ThumbnailCache)
 *    - Reflect page rotation
 *    - Current page highlight
 *    - Drag reorder stub (prepare for Phase 3)
 *    - Multi-select stub (prepare for Phase 3)
 *    - Remain responsive on 1000+ page documents (LazyColumn)
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Thumbnail panel state ─────────────────────────────────────────────

class ThumbnailPanelState {
    private var _selectedPages by mutableStateOf(setOf<Int>())
    val selectedPages: Set<Int> get() = _selectedPages

    private var _isMultiSelectMode by mutableStateOf(false)
    val isMultiSelectMode: Boolean get() = _isMultiSelectMode

    fun selectPage(pageIndex: Int) {
        if (_isMultiSelectMode) {
            _selectedPages = if (pageIndex in _selectedPages) {
                _selectedPages - pageIndex
            } else {
                _selectedPages + pageIndex
            }
        } else {
            _selectedPages = setOf(pageIndex)
        }
    }

    fun enterMultiSelect() {
        _isMultiSelectMode = true
    }

    fun exitMultiSelect() {
        _isMultiSelectMode = false
        _selectedPages = emptySet()
    }

    fun selectAll(count: Int) {
        _selectedPages = (0 until count).toSet()
    }

    fun selectOdd(count: Int) {
        _selectedPages = (0 until count step 2).toSet()
    }

    fun selectEven(count: Int) {
        _selectedPages = (1 until count step 2).toSet()
    }

    fun reverseSelection(count: Int) {
        _selectedPages = (0 until count).filter { it !in _selectedPages }.toSet()
    }

    fun clearSelection() {
        _selectedPages = emptySet()
    }
}

// ── Thumbnail composable ────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThumbnailPanel(
    pages: List<Page>,
    currentPageIndex: Int,
    thumbnailCache: ThumbnailCache,
    panelState: ThumbnailPanelState,
    onPageClick: (Int) -> Unit,
    onPageLongPress: (Int) -> Unit = {},
    onRequestThumbnail: (pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Scroll to current page when it changes
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex in pages.indices) {
            listState.animateScrollToItem(currentPageIndex)
        }
    }

    // Determine which thumbnails are visible and need rendering
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    LaunchedEffect(visibleItems.size, pages.size) {
        visibleItems.forEach { item ->
            if (item.index in pages.indices) {
                val page = pages[item.index]
                // Check if cached
                val cached = thumbnailCache.get(page.pageId) // suspend call inside LaunchedEffect
                if (cached == null) {
                    onRequestThumbnail(item.index)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with page count
        PdfSectionHeader(
            title = "Pages (${pages.size})",
            action = {
                if (panelState.isMultiSelectMode) {
                    Row {
                        TextButton(onClick = { panelState.exitMultiSelect() }) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { panelState.selectAll(pages.size) }) {
                            Text("All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    IconButton(onClick = { panelState.enterMultiSelect() }) {
                        Icon(
                            imageVector = PdfIcons.Menu,
                            contentDescription = "Multi-select",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        // Thumbnail grid/list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = Spacing.md,
                vertical = Spacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            itemsIndexed(
                pages,
                key = { _, page -> page.pageId }
            ) { index, page ->
                val isSelected = index in panelState.selectedPages || index == currentPageIndex

                ThumbnailItem(
                    page = page,
                    pageNumber = index + 1,
                    isSelected = isSelected,
                    isCurrentPage = index == currentPageIndex,
                    onClick = {
                        if (panelState.isMultiSelectMode) {
                            panelState.selectPage(index)
                        } else {
                            onPageClick(index)
                        }
                    },
                    onLongPress = {
                        if (!panelState.isMultiSelectMode) {
                            panelState.enterMultiSelect()
                            panelState.selectPage(index)
                        }
                        onPageLongPress(index)
                    },
                    thumbnailCache = thumbnailCache
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailItem(
    page: Page,
    pageNumber: Int,
    isSelected: Boolean,
    isCurrentPage: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    thumbnailCache: ThumbnailCache
) {
    val cached by produceState<CachedThumbnail?>(null, page.pageId) {
        value = thumbnailCache.get(page.pageId)
    }

    val borderColor = when {
        isCurrentPage -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = when {
        isCurrentPage -> 2.5.dp
        isSelected -> 2.dp
        else -> 1.dp
    }
    val bgColor = when {
        isCurrentPage -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview area
            val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(
                        if (page.rotation == PageRotation.ROTATION_90 ||
                            page.rotation == PageRotation.ROTATION_270) 1.414f
                        else 0.707f  // A4 height/width ratio
                    )
                    .drawWithContent {
                        if (cached != null) {
                            // Draw cached thumbnail bitmap
                            // In KMP we use Canvas drawImage — placeholder renders colored rect
                            drawContent()
                        } else {
                            // Placeholder while loading
                            drawRect(
                                color = placeholderColor,
                                alpha = 0.5f
                            )
                            drawContent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rotation indicator
                if (page.rotation != PageRotation.ROTATION_0) {
                    Icon(
                        imageVector = PdfIcons.ArrowRight,
                        contentDescription = "Rotated ${page.rotation.degrees}°",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            // Page info
            Column {
                Text(
                    text = "Page $pageNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentPage) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${page.width.toInt()} × ${page.height.toInt()} pt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multi-select checkbox
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

