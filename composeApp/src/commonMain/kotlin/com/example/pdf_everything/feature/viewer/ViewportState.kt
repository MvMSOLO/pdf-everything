package com.example.pdf_everything.feature.viewer

import androidx.compose.runtime.*
import com.example.pdf_everything.core.document.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
 *  Viewport State — zoom, scroll, page offset tracking.
 *
 *  Per spec §17: zoom controls (fit page/width/height/actual-size/custom-%),
 *  pinch-to-zoom, stable cursor anchor.
 *  Per spec §57: viewport-aware rendering pipeline input.
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Zoom mode ──────────────────────────────────────────────────────────

enum class ZoomMode {
    CUSTOM,        // user-defined percentage
    FIT_PAGE,      // scale so full page fits in viewport
    FIT_WIDTH,     // scale so page width = viewport width
    FIT_HEIGHT,    // scale so page height = viewport height
    ACTUAL_SIZE    // 100% (1.0)
}

// ── Viewport state ──────────────────────────────────────────────────────

class ViewportState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
    initialZoomMode: ZoomMode = ZoomMode.FIT_PAGE
) {
    // ── Current page ────────────────────────────────────────────────

    private var _currentPage by mutableIntStateOf(initialPage)
    val currentPage: Int get() = _currentPage

    // ── Zoom ────────────────────────────────────────────────────────

    private var _zoomScale by mutableFloatStateOf(initialZoom)
    val zoomScale: Float get() = _zoomScale

    private var _zoomMode by mutableStateOf(initialZoomMode)
    val zoomMode: ZoomMode get() = _zoomMode

    private var _zoomPercentage by mutableIntStateOf((initialZoom * 100).roundToInt())
    val zoomPercentage: Int get() = _zoomPercentage

    // ── Scroll ──────────────────────────────────────────────────────

    private var _scrollOffsetX by mutableFloatStateOf(0f)
    val scrollOffsetX: Float get() = _scrollOffsetX

    private var _scrollOffsetY by mutableFloatStateOf(0f)
    val scrollOffsetY: Float get() = _scrollOffsetY

    // ── Viewport dimensions ──────────────────────────────────────────

    private var _viewportWidth by mutableIntStateOf(0)
    val viewportWidth: Int get() = _viewportWidth

    private var _viewportHeight by mutableIntStateOf(0)
    val viewportHeight: Int get() = _viewportHeight

    // ── Page dimensions (from document) ──────────────────────────────

    private var _pageWidth by mutableFloatStateOf(612f)   // US Letter default
    private var _pageHeight by mutableFloatStateOf(792f)

    val pageWidth: Float get() = _pageWidth
    val pageHeight: Float get() = _pageHeight

    private var _pageRotation by mutableStateOf(PageRotation.ROTATION_0)
    val pageRotation: PageRotation get() = _pageRotation

    // ── Zoom limits ─────────────────────────────────────────────────

    var minZoom: Float = 0.1f
    var maxZoom: Float = 10f
    var zoomStep: Float = 0.25f  // for zoom in/out buttons

    // ── Page count ──────────────────────────────────────────────────

    private var _pageCount by mutableIntStateOf(1)
    val pageCount: Int get() = _pageCount

    // ════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════

    fun updateViewportSize(width: Int, height: Int) {
        _viewportWidth = width
        _viewportHeight = height
        recalculateZoomForMode()
    }

    fun updatePageGeometry(page: Page) {
        _pageWidth = page.width
        _pageHeight = page.height
        _pageRotation = page.rotation
        recalculateZoomForMode()
    }

    fun updatePageCount(count: Int) {
        _pageCount = count
        if (_currentPage >= count) {
            _currentPage = max(0, count - 1)
        }
    }

    // ── Page navigation ─────────────────────────────────────────────

    fun goToPage(pageIndex: Int) {
        _currentPage = pageIndex.coerceIn(0, max(0, _pageCount - 1))
        _scrollOffsetX = 0f
        _scrollOffsetY = 0f
    }

    fun nextPage() {
        if (_currentPage < _pageCount - 1) {
            _currentPage++
            _scrollOffsetX = 0f
            _scrollOffsetY = 0f
        }
    }

    fun previousPage() {
        if (_currentPage > 0) {
            _currentPage--
            _scrollOffsetX = 0f
            _scrollOffsetY = 0f
        }
    }

    fun firstPage() { goToPage(0) }
    fun lastPage() { goToPage(_pageCount - 1) }

    // ── Zoom controls ──────────────────────────────────────────────

    fun setZoomMode(mode: ZoomMode) {
        _zoomMode = mode
        recalculateZoomForMode()
    }

    fun setZoomScale(scale: Float, anchorX: Float? = null, anchorY: Float? = null) {
        _zoomMode = ZoomMode.CUSTOM
        val clamped = scale.coerceIn(minZoom, maxZoom)
        if (anchorX != null && anchorY != null) {
            val converter = currentConverter()
            val (newScrollX, newScrollY) = converter.computeZoomAnchor(
                anchorViewportX = anchorX,
                anchorViewportY = anchorY,
                newZoomScale = clamped
            )
            _scrollOffsetX = newScrollX
            _scrollOffsetY = newScrollY
        }
        _zoomScale = clamped
        _zoomPercentage = (clamped * 100).roundToInt()
    }

    fun zoomIn(anchorX: Float? = null, anchorY: Float? = null) {
        setZoomScale(_zoomScale + zoomStep, anchorX, anchorY)
    }

    fun zoomOut(anchorX: Float? = null, anchorY: Float? = null) {
        setZoomScale(_zoomScale - zoomStep, anchorX, anchorY)
    }

    fun setZoomPercentage(percentage: Int) {
        setZoomScale(percentage / 100f)
    }

    // ── Scroll ───────────────────────────────────────────────────────

    fun setScrollOffset(x: Float, y: Float) {
        _scrollOffsetX = x
        _scrollOffsetY = y
    }

    fun scrollBy(dx: Float, dy: Float) {
        _scrollOffsetX += dx
        _scrollOffsetY += dy
    }

    // ── Computed properties ──────────────────────────────────────────

    val renderedPageWidth: Float
        get() {
            val w = if (_pageRotation == PageRotation.ROTATION_90 ||
                        _pageRotation == PageRotation.ROTATION_270) _pageHeight else _pageWidth
            return w * _zoomScale
        }

    val renderedPageHeight: Float
        get() {
            val h = if (_pageRotation == PageRotation.ROTATION_90 ||
                        _pageRotation == PageRotation.ROTATION_270) _pageWidth else _pageHeight
            return h * _zoomScale
        }

    // ── Viewport descriptor for render pipeline ─────────────────────

    fun toVisibleViewport(totalPages: Int): VisibleViewport {
        // In single-page mode, only current page is visible
        // In continuous mode, multiple pages may be visible
        return VisibleViewport(
            firstVisiblePage = _currentPage,
            lastVisiblePage = _currentPage,
            totalPages = totalPages,
            viewportWidth = _viewportWidth,
            viewportHeight = _viewportHeight,
            zoomScale = _zoomScale
        )
    }

    fun currentConverter(): CoordinateConverter = CoordinateConverter(
        pageWidth = _pageWidth,
        pageHeight = _pageHeight,
        pageRotation = _pageRotation,
        viewportWidth = _viewportWidth.toFloat(),
        viewportHeight = _viewportHeight.toFloat(),
        zoomScale = _zoomScale,
        scrollOffsetX = _scrollOffsetX,
        scrollOffsetY = _scrollOffsetY
    )

    // ── Internal ────────────────────────────────────────────────────

    private fun recalculateZoomForMode() {
        if (_viewportWidth <= 0 || _viewportHeight <= 0) return

        val effectiveW = if (_pageRotation == PageRotation.ROTATION_90 ||
                            _pageRotation == PageRotation.ROTATION_270)
            _pageHeight else _pageWidth
        val effectiveH = if (_pageRotation == PageRotation.ROTATION_90 ||
                            _pageRotation == PageRotation.ROTATION_270)
            _pageWidth else _pageHeight

        val newScale = when (_zoomMode) {
            ZoomMode.FIT_PAGE -> {
                val scaleX = _viewportWidth.toFloat() / effectiveW
                val scaleY = _viewportHeight.toFloat() / effectiveH
                min(scaleX, scaleY)
            }
            ZoomMode.FIT_WIDTH -> _viewportWidth.toFloat() / effectiveW
            ZoomMode.FIT_HEIGHT -> _viewportHeight.toFloat() / effectiveH
            ZoomMode.ACTUAL_SIZE -> 1f
            ZoomMode.CUSTOM -> _zoomScale  // keep current
        }

        _zoomScale = newScale.coerceIn(minZoom, maxZoom)
        _zoomPercentage = (_zoomScale * 100).roundToInt()
    }
}