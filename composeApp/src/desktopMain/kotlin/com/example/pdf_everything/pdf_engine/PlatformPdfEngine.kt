package com.example.pdf_everything.pdf_engine

import com.example.pdf_everything.pdf_engine.api.PdfEngine

actual fun createPdfEngine(): PdfEngine = DesktopPdfEngine()
