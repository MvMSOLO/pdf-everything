package com.example.pdf_everything.feature.viewer

import com.example.pdf_everything.core.document.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
 *  Coordinate Conversion System
 *
 *  Converts between 4 coordinate systems:
 *    1. PDF coordinates  — origin bottom-left, units = points (1/72")
 *    2. Page coordinates — origin top-left, units = points (flip Y from PDF)
 *    3. Viewport coordinates — origin top-left, in pixels (accounts for zoom & scroll)
 *    4. Screen coordinates — absolute position on display
 *
 *  Per spec §17: stable cursor anchor during zoom requires correct
 *  coordinate conversion at all zoom levels.
 * ═══════════════════════════════════════════════════════════════════════ */

data class ViewportOffset(
    val x: Float,
    val y: Float
)

data class ScreenPoint(
    val x: Float,
    val y: Float
)

data class ViewportRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * Converts coordinates between PDF, Page, Viewport, and Screen spaces.
 */
class CoordinateConverter(
    val pageWidth: Float,       // PDF points
    val pageHeight: Float,      // PDF points
    val pageRotation: PageRotation,
    val viewportWidth: Float,   // pixels
    val viewportHeight: Float,  // pixels
    val zoomScale: Float,
    val scrollOffsetX: Float,   // pixels
    val scrollOffsetY: Float    // pixels
) {
    /** DPI-to-points conversion factor. */
    private val screenToPdfScale: Float
        get() = 72f / 96f  // assuming 96 DPI screen; refined per-platform

    /** The scale factor from PDF points to viewport pixels. */
    val pdfToViewportScale: Float
        get() = zoomScale

    // ── Effective page dimensions after rotation ─────────────────────

    val effectiveWidth: Float
        get() = when (pageRotation) {
            PageRotation.ROTATION_0, PageRotation.ROTATION_180 -> pageWidth
            PageRotation.ROTATION_90, PageRotation.ROTATION_270 -> pageHeight
        }

    val effectiveHeight: Float
        get() = when (pageRotation) {
            PageRotation.ROTATION_0, PageRotation.ROTATION_180 -> pageHeight
            PageRotation.ROTATION_90, PageRotation.ROTATION_270 -> pageWidth
        }

    /** Page rendered size in viewport pixels. */
    val renderedWidth: Float
        get() = effectiveWidth * zoomScale

    val renderedHeight: Float
        get() = effectiveHeight * zoomScale

    // ── PDF → Page (flip Y, rotate) ─────────────────────────────────

    fun pdfToPage(pdfPoint: PdfPoint): Pair<Float, Float> {
        val (px, py) = applyRotation(pdfPoint.x, pdfPoint.y)
        // Flip Y: PDF origin is bottom-left, page origin is top-left
        val pageY = effectiveHeight - py
        return Pair(px, pageY)
    }

    fun pageToPdf(pageX: Float, pageY: Float): PdfPoint {
        // Flip Y back
        val pdfY = effectiveHeight - pageY
        val (px, py) = reverseRotation(pdfY, pageX)  // swap x/y after rotation reverse
        return PdfPoint(px, py)
    }

    // ── Page → Viewport ──────────────────────────────────────────────

    fun pageToViewport(pageX: Float, pageY: Float): Pair<Float, Float> {
        val vx = pageX * zoomScale - scrollOffsetX
        val vy = pageY * zoomScale - scrollOffsetY
        return Pair(vx, vy)
    }

    fun viewportToPage(viewportX: Float, viewportY: Float): Pair<Float, Float> {
        val pageX = (viewportX + scrollOffsetX) / zoomScale
        val pageY = (viewportY + scrollOffsetY) / zoomScale
        return Pair(pageX, pageY)
    }

    // ── PDF → Viewport ───────────────────────────────────────────────

    fun pdfToViewport(pdfPoint: PdfPoint): Pair<Float, Float> {
        val (pageX, pageY) = pdfToPage(pdfPoint)
        return pageToViewport(pageX, pageY)
    }

    fun viewportToPdf(viewportX: Float, viewportY: Float): PdfPoint {
        val (pageX, pageY) = viewportToPage(viewportX, viewportY)
        return pageToPdf(pageX, pageY)
    }

    // ── PDF Rect → Viewport Rect ─────────────────────────────────────

    fun pdfRectToViewport(pdfRect: PdfRect): ViewportRect {
        val (tlx, tly) = pdfToViewport(PdfPoint(pdfRect.x, pdfRect.y + pdfRect.height))
        val (brx, bry) = pdfToViewport(PdfPoint(pdfRect.x + pdfRect.width, pdfRect.y))
        return ViewportRect(tlx, tly, brx - tlx, bry - tly)
    }

    // ── Rotation helpers ─────────────────────────────────────────────

    private fun applyRotation(x: Float, y: Float): Pair<Float, Float> {
        return when (pageRotation) {
            PageRotation.ROTATION_0 -> Pair(x, y)
            PageRotation.ROTATION_90 -> Pair(pageHeight - y, x)
            PageRotation.ROTATION_180 -> Pair(pageWidth - x, pageHeight - y)
            PageRotation.ROTATION_270 -> Pair(y, pageWidth - x)
        }
    }

    private fun reverseRotation(x: Float, y: Float): Pair<Float, Float> {
        return when (pageRotation) {
            PageRotation.ROTATION_0 -> Pair(x, y)
            PageRotation.ROTATION_90 -> Pair(y, pageWidth - x)
            PageRotation.ROTATION_180 -> Pair(pageWidth - x, pageHeight - y)
            PageRotation.ROTATION_270 -> Pair(pageHeight - y, x)
        }
    }

    // ── Anchor point calculation for zoom (spec §17) ──────────────────

    /**
     * Given a point under the cursor/finger in viewport space,
     * compute the new scroll offset after zoom so the point stays anchored.
     *
     * This implements the "stable focus while zooming" requirement.
     */
    fun computeZoomAnchor(
        anchorViewportX: Float,
        anchorViewportY: Float,
        newZoomScale: Float
    ): Pair<Float, Float> {
        // Convert anchor to page space (invariant under zoom change)
        val (pageX, pageY) = viewportToPage(anchorViewportX, anchorViewportY)

        // New viewport position of that page point at new zoom
        val newViewportX = pageX * newZoomScale - scrollOffsetX
        val newViewportY = pageY * newZoomScale - scrollOffsetY

        // The scroll offset needs to adjust so anchor stays at same screen position
        val scrollDeltaX = newViewportX - anchorViewportX
        val scrollDeltaY = newViewportY - anchorViewportY

        return Pair(
            scrollOffsetX + scrollDeltaX,
            scrollOffsetY + scrollDeltaY
        )
    }

    /** Create a new converter with updated zoom & scroll, preserving page geometry. */
    fun withZoomAndScroll(
        newZoomScale: Float,
        newScrollX: Float,
        newScrollY: Float
    ): CoordinateConverter = CoordinateConverter(
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        pageRotation = pageRotation,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        zoomScale = newZoomScale,
        scrollOffsetX = newScrollX,
        scrollOffsetY = newScrollY
    )
}