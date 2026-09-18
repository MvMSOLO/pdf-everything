package com.example.pdf_everything.core.document

import kotlin.math.max

/**
 * Centralised coordinate conversion boundary. Model/UI rectangles use a top-left origin;
 * native PDF rectangles use the PDF bottom-left origin. All page placement code should use this module.
 */
object CoordinateSystem {
    data class Point(val x: Float, val y: Float)

    fun pdfToUi(point: Point, pageHeight: Float): Point =
        Point(point.x, pageHeight - point.y)

    fun uiToPdf(point: Point, pageHeight: Float): Point =
        Point(point.x, pageHeight - point.y)

    fun pdfRectToUi(rect: RectF, pageHeight: Float): RectF = RectF(
        rect.left,
        pageHeight - rect.bottom,
        rect.right,
        pageHeight - rect.top
    ).normalized()

    fun uiRectToPdf(rect: RectF, pageHeight: Float): RectF {
        val normal = rect.normalized()
        return RectF(
            normal.left,
            pageHeight - normal.bottom,
            normal.right,
            pageHeight - normal.top
        ).normalized()
    }

    fun viewportToPage(point: Point, scrollX: Float, scrollY: Float, scale: Float): Point {
        require(scale.isFinite() && scale > 0f) { "Scale must be positive" }
        return Point((point.x + scrollX) / scale, (point.y + scrollY) / scale)
    }

    fun pageToViewport(point: Point, scrollX: Float, scrollY: Float, scale: Float): Point {
        require(scale.isFinite() && scale > 0f) { "Scale must be positive" }
        return Point(point.x * scale - scrollX, point.y * scale - scrollY)
    }

    fun rotatePagePoint(point: Point, pageWidth: Float, pageHeight: Float, rotation: Int): Point {
        return when (((rotation % 360) + 360) % 360) {
            0 -> point
            90 -> Point(pageHeight - point.y, point.x)
            180 -> Point(pageWidth - point.x, pageHeight - point.y)
            270 -> Point(point.y, pageWidth - point.x)
            else -> error("Unsupported page rotation: $rotation")
        }
    }

    fun rotatedPageSize(pageWidth: Float, pageHeight: Float, rotation: Int): Pair<Float, Float> =
        if ((((rotation % 360) + 360) % 360) % 180 == 0) pageWidth to pageHeight else pageHeight to pageWidth

    fun clampToPage(point: Point, width: Float, height: Float): Point =
        Point(point.x.coerceIn(0f, max(0f, width)), point.y.coerceIn(0f, max(0f, height)))
}
