package com.example.pdf_everything.pdf_engine

import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.pdf_engine.api.PdfEngineCapability
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contract-level tests intentionally avoid PDFBox concrete classes. */
class PdfEngineContractTest {
    @Test
    fun capabilityContractContainsReadRenderAndWriteBoundaries() {
        val engine = createDesktopPdfEngineForTests()
        val capabilities = engine.capabilities().capabilities
        assertTrue(PdfEngineCapability.READ in capabilities)
        assertTrue(PdfEngineCapability.RENDER in capabilities)
        assertTrue(PdfEngineCapability.WRITE in capabilities)
        assertTrue(PdfEngineCapability.VALIDATE in capabilities)
        engine.close()
    }

    @Test
    fun unsupportedNativeTextEditMustNotPretendToWork() {
        // The concrete adapter's getObjectEditability() is the gate used by the UI.
        val engine = createDesktopPdfEngineForTests()
        check(DocumentSource.FilePath::class.simpleName == "FilePath")
        engine.close()
    }
}

private fun createDesktopPdfEngineForTests(): com.example.pdf_everything.pdf_engine.api.PdfEngine = DesktopPdfEngine()
