package com.example.pdf_everything.phase6

import kotlin.test.Test
import kotlin.test.assertEquals

class Phase6ModelsTest {
    @Test
    fun selectedPagesSupportsRangesAndParity() {
        val request = PrintRequest(ranges = listOf(PageRange(0, 4)), parity = PrintPageParity.ODD)
        assertEquals(listOf(0, 2, 4), request.selectedPages(6))
    }

    @Test
    fun selectedPagesDropsInvalidPagesAndDuplicates() {
        val request = PrintRequest(ranges = listOf(PageRange(3, 10), PageRange(0, 2), PageRange(2, 4)))
        assertEquals(listOf(3, 4, 5, 6, 7, 8, 9, 0, 1, 2), request.selectedPages(10))
    }
}
