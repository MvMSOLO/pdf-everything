package com.example.pdf_everything.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinateSystemTest {
    @Test fun pdfAndUiRoundTrip() {
        val source = RectF(20f, 100f, 220f, 300f)
        val ui = CoordinateSystem.pdfRectToUi(source, 800f)
        val pdf = CoordinateSystem.uiRectToPdf(ui, 800f)
        assertEquals(source, pdf)
    }

    @Test fun rotatedDimensionsSwapForQuarterTurns() {
        assertEquals(800f to 600f, CoordinateSystem.rotatedPageSize(600f, 800f, 90))
        assertEquals(600f to 800f, CoordinateSystem.rotatedPageSize(600f, 800f, 180))
    }

    @Test fun viewportMappingRespectsScrollAndScale() {
        val page = CoordinateSystem.viewportToPage(CoordinateSystem.Point(100f, 120f), 20f, 40f, 2f)
        assertEquals(60f, page.x)
        assertEquals(80f, page.y)
        val viewport = CoordinateSystem.pageToViewport(page, 20f, 40f, 2f)
        assertEquals(100f, viewport.x)
        assertEquals(120f, viewport.y)
    }
}
