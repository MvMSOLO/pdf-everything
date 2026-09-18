package com.example.pdf_everything.core.search

import com.example.pdf_everything.pdf_engine.api.SearchMatch
import com.example.pdf_everything.pdf_engine.api.SearchOptions
import com.example.pdf_everything.pdf_engine.api.SearchRect

/** Platform adapters provide one logical glyph/character stream per PDF page. */
data class IndexedChar(val value: Char, val rect: SearchRect)

class PageTextSearchIndex(private val chars: List<IndexedChar>) {
    private val text = chars.joinToString(separator = "") { it.value.toString() }

    fun plainText(): String = text

    fun search(pageIndex: Int, query: String, options: SearchOptions): List<SearchMatch> {
        val q = query.trim()
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val source = text
        val needle = q
        if (needle.isEmpty()) return emptyList()

        val results = mutableListOf<SearchMatch>()
        var from = 0
        while (from <= source.length - needle.length) {
            val found = findNext(source, needle, from, options.caseSensitive)
            if (found < 0) break
            val end = found + needle.length
            val wholeWordOk = !options.wholeWord || isWholeWord(source, found, end)
            if (wholeWordOk) {
                val rects = chars.subList(found.coerceAtMost(chars.size), end.coerceAtMost(chars.size))
                    .map { it.rect }
                    .groupBy { it.topLineKey() }
                    .values
                    .map { line -> union(line) }
                results += SearchMatch(pageIndex, found, end, rects)
            }
            from = found + 1
        }
        return results
    }

    private fun findNext(source: String, needle: String, start: Int, caseSensitive: Boolean): Int {
        if (needle.length > source.length) return -1
        for (i in start..(source.length - needle.length)) {
            if (source.regionMatches(i, needle, 0, needle.length, ignoreCase = !caseSensitive)) return i
        }
        return -1
    }

    private fun isWholeWord(value: String, start: Int, end: Int): Boolean {
        fun word(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        val before = start == 0 || !word(value[start - 1])
        val after = end >= value.length || !word(value[end])
        return before && after
    }

    private fun SearchRect.topLineKey(): Int = (top * 4f).toInt()

    private fun union(rects: List<SearchRect>): SearchRect = SearchRect(
        left = rects.minOf { it.left },
        top = rects.minOf { it.top },
        right = rects.maxOf { it.right },
        bottom = rects.maxOf { it.bottom }
    )
}
