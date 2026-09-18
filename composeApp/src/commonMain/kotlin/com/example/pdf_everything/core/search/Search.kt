package com.example.pdf_everything.core.search

import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.SearchMatch
import com.example.pdf_everything.pdf_engine.api.SearchOptions

fun searchDocument(
    engine: PdfEngine,
    pageCount: Int,
    query: String,
    options: SearchOptions
): List<SearchMatch> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return buildList {
        for (page in 0 until pageCount) addAll(engine.searchPage(page, q, options))
    }
}
