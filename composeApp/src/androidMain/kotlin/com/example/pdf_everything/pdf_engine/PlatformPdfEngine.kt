package com.example.pdf_everything.pdf_engine.api

import com.example.pdf_everything.pdf_engine.AndroidPdfEngine

actual fun createPdfEngine(): PdfEngine = AndroidPdfEngine()
