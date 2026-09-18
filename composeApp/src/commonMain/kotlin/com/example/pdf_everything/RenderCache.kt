package com.example.pdf_everything

import com.example.pdf_everything.pdf_engine.api.RenderedPage

class RenderMemoryCache(
    private val pageLimit: Int = 18,
    private val thumbnailLimit: Int = 80,
    private val tileLimit: Int = 64
) {
    private val lock = Any()
    private val pages = lru(pageLimit)
    private val thumbnails = lru(thumbnailLimit)
    private val tiles = lru(tileLimit)

    private fun lru(limit: Int) = object : LinkedHashMap<String, RenderedPage>(limit, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RenderedPage>?): Boolean = size > limit
    }

    fun page(key: String): RenderedPage? = synchronized(lock) { pages[key] }
    fun putPage(key: String, value: RenderedPage) = synchronized(lock) { pages[key] = value }
    fun thumbnail(key: String): RenderedPage? = synchronized(lock) { thumbnails[key] }
    fun putThumbnail(key: String, value: RenderedPage) = synchronized(lock) { thumbnails[key] = value }
    fun tile(key: String): RenderedPage? = synchronized(lock) { tiles[key] }
    fun putTile(key: String, value: RenderedPage) = synchronized(lock) { tiles[key] = value }
    fun clear() = synchronized(lock) { pages.clear(); thumbnails.clear(); tiles.clear() }
    fun clearDocument(documentVersion: Long) = synchronized(lock) {
        pages.entries.removeAll { it.value.documentVersion != documentVersion }
        thumbnails.entries.removeAll { it.value.documentVersion != documentVersion }
        tiles.entries.removeAll { it.value.documentVersion != documentVersion }
    }
}
