package com.example.pdf_everything.core.search

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.TextObject

/**
 * Full-text search across document pages.
 *
 * Supports:
 *  - case-sensitive / case-insensitive
 *  - whole-word matching
 *  - result navigation (next/previous)
 *  - match count per page
 */
data class SearchResult(
    val pageIndex: Int,
    val pageId: String,
    val objectId: String?,  // null if matched in extracted text, not a specific object
    val matchStart: Int,
    val matchEnd: Int,
    val matchedText: String
)

data class SearchState(
    val query: String = "",
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val currentIndex: Int = -1,
    val isSearching: Boolean = false
) {
    val hasResults get() = results.isNotEmpty()
    val currentResult get() = results.getOrNull(currentIndex)
    val resultCount get() = results.size
    val currentPageIndex get() = currentResult?.pageIndex
}

class SearchEngine {

    fun search(
        document: Document,
        query: String,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()
        val searchQuery = if (caseSensitive) query else query.lowercase()

        for (page in document.pages) {
            // Search in text objects
            for (obj in page.objects) {
                if (obj is TextObject) {
                    val text = if (caseSensitive) obj.text else obj.text.lowercase()
                    findMatches(text, searchQuery, wholeWord, page.index, page.pageId, obj.objectId, results)
                }
            }
            // Also search annotations content
            for (anno in page.annotations) {
                val content = anno.contents ?: continue
                val text = if (caseSensitive) content else content.lowercase()
                findMatches(text, searchQuery, wholeWord, page.index, page.pageId, anno.objectId, results)
            }
        }
        return results
    }

    private fun findMatches(
        text: String,
        query: String,
        wholeWord: Boolean,
        pageIndex: Int,
        pageId: String,
        objectId: String,
        results: MutableList<SearchResult>
    ) {
        var startIndex = 0
        while (startIndex <= text.length - query.length) {
            val found = text.indexOf(query, startIndex)
            if (found == -1) break
            val end = found + query.length
            if (wholeWord) {
                val beforeIsBoundary = found == 0 || !text[found - 1].isLetterOrDigit()
                val afterIsBoundary = end >= text.length || !text[end].isLetterOrDigit()
                if (!beforeIsBoundary || !afterIsBoundary) {
                    startIndex = found + 1
                    continue
                }
            }
            results.add(SearchResult(
                pageIndex = pageIndex,
                pageId = pageId,
                objectId = objectId,
                matchStart = found,
                matchEnd = end,
                matchedText = text.substring(found, end)
            ))
            startIndex = end
        }
    }

    fun nextResult(state: SearchState): SearchState {
        if (state.results.isEmpty()) return state
        val next = (state.currentIndex + 1) % state.results.size
        return state.copy(currentIndex = next)
    }

    fun previousResult(state: SearchState): SearchState {
        if (state.results.isEmpty()) return state
        val prev = if (state.currentIndex <= 0) state.results.size - 1 else state.currentIndex - 1
        return state.copy(currentIndex = prev)
    }
}