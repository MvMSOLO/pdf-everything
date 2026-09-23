package com.example.pdf_everything.pdf_engine.api

import com.example.pdf_everything.pdf_engine.DesktopPdfEngine

actual fun createPdfEngine(): PdfEngine = DesktopPdfEngine()
