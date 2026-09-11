package com.example.pdf_everything.feature.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.core.document.*
import com.example.pdf_everything.core.services.RenderConfig
import com.example.pdf_everything.ui.design_system.Spacing
import kotlin.math.*

/* ═══════════════════════════════════════════════════════════════════════
 *  PageCanvas — renders a single PDF page onto Compose Canvas.
 *
 *  This is the primary display composable for the viewer.
 *  It:
 *    - Draws the cached page bitmap from PageCache
 *    - Handles pinch-to-zoom with stable anchor (spec §17)
 *    - Handles scroll / pan
 *    - Draws search highlights
 *    - Draws selection rectangles
 *    - Shows loading / error states
 * ═══════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageCanvas(
    page: Page?,
    viewportState: ViewportState,
    pageCache: PageCache,
    converter: CoordinateConverter,
    searchHighlights: List<SearchHighlight> = emptyList(),
    selectionRects: List<PdfRect> = emptyList(),
    onZoomChanged: (Float, Float?, Float?) -> Unit = { _, _, _ -> },
    onScrollChanged: (Float, Float) -> Unit = { _, _ -> },
    onTap: (Offset) -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPageCache by produceState<CachedPageBitmap?>(null, page?.pageId, viewportState.zoomScale) {
        if (page != null) {
            value = pageCache.getPageByIndex(page.index)
        }
    }

    val isLoading = page != null && currentPageCache == null &&
                    page.renderState.state != RenderState.RENDERED

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (page == null) {
            // No page loaded
            Text(
                text = "No page to display",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        // Main canvas with gesture handling


        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset -> onTap(offset) },
                        onDoubleTap = { offset ->
                            // Double-tap zoom: toggle between fit-page and 2x
                            val currentZoom = viewportState.zoomScale
                            val newZoom = if (currentZoom > 1.5f) {
                                viewportState.setZoomMode(ZoomMode.FIT_PAGE)
                                viewportState.zoomScale
                            } else {
                                2f
                            }
                            onZoomChanged(newZoom, offset.x, offset.y)
                            onDoubleTap(offset)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { center, pan, zoom, rotation ->
                        val newScale = (viewportState.zoomScale * zoom).coerceIn(
                            viewportState.minZoom, viewportState.maxZoom
                        )
                        viewportState.setZoomScale(newScale)
                        viewportState.scrollBy(pan.x, pan.y)
                        onZoomChanged(newScale, null, null)
                        onScrollChanged(viewportState.scrollOffsetX, viewportState.scrollOffsetY)
                    }
                }
        ) {
            val renderW = viewportState.renderedPageWidth
            val renderH = viewportState.renderedPageHeight

            // Center the page in viewport
            val offsetX = (size.width - renderW) / 2f - viewportState.scrollOffsetX
            val offsetY = (size.height - renderH) / 2f - viewportState.scrollOffsetY

            // Page background (white)
            drawRect(
                color = Color.White,
                topLeft = Offset(offsetX, offsetY),
                size = Size(renderW, renderH)
            )

            // Draw page shadow
            drawRect(
                color = Color(0x1A000000),  // 10% black shadow
                topLeft = Offset(offsetX + 2f, offsetY + 2f),
                size = Size(renderW, renderH)
            )

            // Draw cached bitmap if available
            val cachedBitmap = currentPageCache
            if (cachedBitmap != null && cachedBitmap.bitmapBytes.isNotEmpty()) {
                try {
                    // Build ImageBitmap from raw ARGB bytes
                    val imageBitmap = createImageBitmapFromBytes(
                        cachedBitmap.bitmapBytes,
                        cachedBitmap.width,
                        cachedBitmap.height
                    )
                    if (imageBitmap != null) {
                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                            dstSize = IntSize(renderW.roundToInt(), renderH.roundToInt())
                        )
                    }
                } catch (_: Exception) {
                    // Bitmap decode failed — draw placeholder grid
                    drawPagePlaceholder(offsetX, offsetY, renderW, renderH)
                }
            } else {
                // No cache — draw placeholder
                drawPagePlaceholder(offsetX, offsetY, renderW, renderH)
            }

            // Draw search highlights
            // Note: precise rect highlighting requires text layout info from PdfEngine (Phase 3)
            // For now, draw a semi-transparent yellow banner on pages with matches
            searchHighlights.forEach { highlight ->
                if (highlight.pageIndex == page.index) {
                    val bannerH = 28f * (renderH / 792f)
                    val matchIndex = searchHighlights.indexOf(highlight)
                    val yOffset = offsetY + 16f + matchIndex * (bannerH + 4f)
                    drawRect(
                        color = Color(0x80FFFF00),  // semi-transparent yellow
                        topLeft = Offset(offsetX + 12f, yOffset),
                        size = Size(renderW - 24f, bannerH),
                        style = Stroke(width = 1f)
                    )
                    // Draw match text indicator
                    drawRect(
                        color = Color(0x40FFD700),
                        topLeft = Offset(offsetX + 12f, yOffset),
                        size = Size(renderW - 24f, bannerH)
                    )
                }
            }

            // Draw selection rectangles
            selectionRects.forEach { rect ->
                val vr = converter.pdfRectToViewport(rect)
                drawRect(
                    color = Color(0x330066CC),  // semi-transparent blue
                    topLeft = Offset(vr.x, vr.y),
                    size = Size(vr.width, vr.height)
                )
                drawRect(
                    color = Color(0x990066CC),  // border
                    topLeft = Offset(vr.x, vr.y),
                    size = Size(vr.width, vr.height),
                    style = Stroke(width = 2f)
                )
            }

            // Page border
            drawRect(
                color = Color(0x1A000000),
                topLeft = Offset(offsetX, offsetY),
                size = Size(renderW, renderH),
                style = Stroke(width = 1f)
            )
        }

        // Loading overlay
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Continuous scroll canvas ────────────────────────────────────────────

/**
 * Multi-page canvas for Continuous view mode.
 * Renders pages in a vertical scrollable list.
 */
@Composable
fun ContinuousPageCanvas(
    pages: List<Page>,
    viewportState: ViewportState,
    pageCache: PageCache,
    searchHighlights: List<SearchHighlight> = emptyList(),
    onRequestRender: (pageIndex: Int) -> Unit,
    onPageChanged: (currentPageIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track which pages are visible for on-demand rendering
    var firstVisiblePage by remember { mutableIntStateOf(0) }
    var lastVisiblePage by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrollable vertical layout of all pages
        val scrollState = rememberScrollableState { delta ->
            // Track scroll position to determine visible pages
            delta
        }

        // For continuous mode we use a simple approach: render current page + neighbors
        LaunchedEffect(viewportState.currentPage) {
            val range = maxOf(0, viewportState.currentPage - 2)..
                       minOf(pages.lastIndex, viewportState.currentPage + 2)
            for (i in range) {
                val cached = pageCache.getPageByIndex(i)
                if (cached == null) {
                    onRequestRender(i)
                }
            }
        }

        // Simplified continuous layout: show current page centered
        // Full continuous scroll will be refined with LazyColumn approach
        PageCanvas(
            page = pages.getOrNull(viewportState.currentPage),
            viewportState = viewportState,
            pageCache = pageCache,
            converter = viewportState.currentConverter(),
            searchHighlights = searchHighlights,
            modifier = Modifier.fillMaxSize()
        )

        // Page indicator at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.lg),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Page ${viewportState.currentPage + 1} / ${pages.size}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ── Two-page spread canvas ─────────────────────────────────────────────

@Composable
fun TwoPageCanvas(
    pages: List<Page>,
    viewportState: ViewportState,
    pageCache: PageCache,
    searchHighlights: List<SearchHighlight> = emptyList(),
    onRequestRender: (pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val leftPageIndex = viewportState.currentPage
    val rightPageIndex = viewportState.currentPage + 1

    // Ensure both pages are rendered
    LaunchedEffect(leftPageIndex, rightPageIndex) {
        if (leftPageIndex in pages.indices) onRequestRender(leftPageIndex)
        if (rightPageIndex in pages.indices) onRequestRender(rightPageIndex)
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageCanvas(
            page = pages.getOrNull(leftPageIndex),
            viewportState = viewportState,
            pageCache = pageCache,
            converter = viewportState.currentConverter(),
            searchHighlights = searchHighlights.filter { it.pageIndex == leftPageIndex },
            modifier = Modifier.weight(1f)
        )

        if (rightPageIndex in pages.indices) {
            Spacer(Modifier.width(4.dp))
            PageCanvas(
                page = pages.getOrNull(rightPageIndex),
                viewportState = viewportState,
                pageCache = pageCache,
                converter = viewportState.currentConverter(),
                searchHighlights = searchHighlights.filter { it.pageIndex == rightPageIndex },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

data class SearchHighlight(
    val pageIndex: Int,
    val matchStart: Int,
    val matchEnd: Int,
    val matchedText: String
)

/**
 * Creates a Compose ImageBitmap from raw ARGB_8888 byte array.
 * This is platform-specific; we provide a common stub that
 * returns null — actual implementations go in platform source sets.
 */
expect fun createImageBitmapFromBytes(bytes: ByteArray, width: Int, height: Int): ImageBitmap?

private fun DrawScope.drawPagePlaceholder(
    offsetX: Float,
    offsetY: Float,
    renderW: Float,
    renderH: Float
) {
    // Light gray background
    drawRect(
        color = Color(0xFFF5F5F5),
        topLeft = Offset(offsetX, offsetY),
        size = Size(renderW, renderH)
    )

    // Grid lines to indicate "rendering in progress"
    val gridSpacing = 40f * (renderW / 612f)  // scale grid to page size
    var y = offsetY
    while (y < offsetY + renderH) {
        drawLine(
            color = Color(0x1A000000),
            start = Offset(offsetX, y),
            end = Offset(offsetX + renderW, y),
            strokeWidth = 0.5f
        )
        y += gridSpacing
    }
    var x = offsetX
    while (x < offsetX + renderW) {
        drawLine(
            color = Color(0x1A000000),
            start = Offset(x, offsetY),
            end = Offset(x, offsetY + renderH),
            strokeWidth = 0.5f
        )
        x += gridSpacing
    }

    // "Loading" text placeholder
    // (drawText is limited in Canvas; we skip text in placeholder)
}