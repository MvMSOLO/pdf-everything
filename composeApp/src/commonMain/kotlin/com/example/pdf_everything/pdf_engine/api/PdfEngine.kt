package com.example.pdf_everything.pdf_engine.api

import com.example.pdf_everything.core.document.PdfObject

abstract class PdfEngine {
    abstract fun open(path: String)
    abstract fun renderPage(pageIndex: Int)
    abstract fun getText(pageIndex: Int): String
    abstract fun modify(pageIndex: Int, obj: PdfObject)
    abstract fun save(path: String)
}
