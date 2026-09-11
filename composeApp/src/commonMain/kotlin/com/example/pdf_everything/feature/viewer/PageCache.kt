package com.example.pdf_everything.feature.viewer

import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.PageRotation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/* ═══════════════════════════════════════════════════════════════════════
 *  Page & Tile Cache — LRU cache for rendered page bitmaps.
 *
 *  Per spec §57 & §4.3:
 *    - Viewport-aware: only cache what's needed near viewport
 *    - Tile-based: when zoomed in, only render visible tiles
 *    - Recycle off-screen surfaces
 *    - Tag cached entries with version to reject stale renders
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Cached page bitmap ────────────────────────────────────────────────

data class CachedPageBitmap(
    val pageId: String,
    val pageIndex: Int,
    val bitmapBytes: ByteArray,
    val width: Int,
    val height: Int,
    val dpi: Float,
    val rotation: PageRotation,
    val documentVersion: Int,
    val pageVersion: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

data class CachedTileBitmap(
    val tileKey: TileKey,
    val bitmapBytes: ByteArray,
    val width: Int,
    val height: Int,
    val documentVersion: Int,
    val pageVersion: Int,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

// ── LRU Page Cache ────────────────────────────────────────────────────

class PageCache(
    private val maxPageEntries: Int = 20,
    private val maxTileEntries: Int = 60,
    private val maxMemoryBytes: Long = 256L * 1024L * 1024L  // 256 MB
) {
    private val pageCache = linkedMapOf<String, CachedPageBitmap>()  // access-ordered LRU
    private val tileCache = linkedMapOf<TileKey, CachedTileBitmap>()
    private val mutex = Mutex()

    private var totalMemoryBytes: Long = 0L

    suspend fun putPage(entry: CachedPageBitmap) {
        mutex.withLock {
            // Remove old entry if exists
            pageCache.remove(entry.pageId)?.let { old ->
                totalMemoryBytes -= old.bitmapBytes.size.toLong()
            }
            pageCache[entry.pageId] = entry
            totalMemoryBytes += entry.bitmapBytes.size.toLong()
            evictIfNeeded()
        }
    }

    suspend fun getPage(pageId: String): CachedPageBitmap? {
        mutex.withLock {
            val entry = pageCache.remove(pageId) ?: return null
            // Re-insert at end (most recently used)
            pageCache[pageId] = entry
            return entry
        }
    }

    suspend fun getPageByIndex(pageIndex: Int): CachedPageBitmap? {
        mutex.withLock {
            val entry = pageCache.values.find { it.pageIndex == pageIndex } ?: return null
            pageCache.remove(entry.pageId)
            pageCache[entry.pageId] = entry
            return entry
        }
    }

    suspend fun putTile(entry: CachedTileBitmap) {
        mutex.withLock {
            tileCache.remove(entry.tileKey)?.let { old ->
                totalMemoryBytes -= old.bitmapBytes.size.toLong()
            }
            tileCache[entry.tileKey] = entry
            totalMemoryBytes += entry.bitmapBytes.size.toLong()
            evictIfNeeded()
        }
    }

    suspend fun getTile(key: TileKey): CachedTileBitmap? {
        mutex.withLock {
            val entry = tileCache.remove(key) ?: return null
            tileCache[key] = entry
            return entry
        }
    }

    suspend fun invalidatePage(pageId: String) {
        mutex.withLock {
            pageCache.remove(pageId)?.let { old ->
                totalMemoryBytes -= old.bitmapBytes.size.toLong()
            }
            // Also remove tiles for this page
            val tilesToRemove = tileCache.keys.filter { it.pageId == pageId }
            tilesToRemove.forEach { key ->
                tileCache.remove(key)?.let { old ->
                    totalMemoryBytes -= old.bitmapBytes.size.toLong()
                }
            }
        }
    }

    /**
     * Invalidate entries whose version doesn't match current.
     * Per spec §57: stale content must not overwrite current edits.
     */
    suspend fun invalidateStale(documentVersion: Int, pageVersions: Map<String, Int>) {
        mutex.withLock {
            val stalePages = pageCache.entries.filter { (key, entry) ->
                entry.documentVersion != documentVersion ||
                (pageVersions[key] != null && entry.pageVersion != pageVersions[key])
            }
            stalePages.forEach { (key, entry) ->
                pageCache.remove(key)
                totalMemoryBytes -= entry.bitmapBytes.size.toLong()
            }

            val staleTiles = tileCache.entries.filter { (_, entry) ->
                entry.documentVersion != documentVersion
            }
            staleTiles.forEach { (key, entry) ->
                tileCache.remove(key)
                totalMemoryBytes -= entry.bitmapBytes.size.toLong()
            }
        }
    }

    /** Remove pages far from the current viewport. */
    suspend fun recycleOffScreen(keepPageIndices: Set<Int>) {
        mutex.withLock {
            val toRemove = pageCache.entries
                .filter { it.value.pageIndex !in keepPageIndices }
                .sortedBy { it.value.timestampMs }  // oldest first
            for ((key, entry) in toRemove) {
                if (pageCache.size <= maxPageEntries / 2) break
                pageCache.remove(key)
                totalMemoryBytes -= entry.bitmapBytes.size.toLong()
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            pageCache.clear()
            tileCache.clear()
            totalMemoryBytes = 0L
        }
    }

    suspend fun size(): Pair<Int, Int> {
        mutex.withLock { return Pair(pageCache.size, tileCache.size) }
    }

    private fun evictIfNeeded() {
        // Evict oldest entries until under limits
        while (pageCache.size > maxPageEntries && pageCache.isNotEmpty()) {
            val eldest = pageCache.entries.first()
            pageCache.remove(eldest.key)
            totalMemoryBytes -= eldest.value.bitmapBytes.size.toLong()
        }
        while (tileCache.size > maxTileEntries && tileCache.isNotEmpty()) {
            val eldest = tileCache.entries.first()
            tileCache.remove(eldest.key)
            totalMemoryBytes -= eldest.value.bitmapBytes.size.toLong()
        }
        // Memory-based eviction
        while (totalMemoryBytes > maxMemoryBytes) {
            if (tileCache.isNotEmpty()) {
                val eldest = tileCache.entries.first()
                tileCache.remove(eldest.key)
                totalMemoryBytes -= eldest.value.bitmapBytes.size.toLong()
            } else if (pageCache.isNotEmpty()) {
                val eldest = pageCache.entries.first()
                pageCache.remove(eldest.key)
                totalMemoryBytes -= eldest.value.bitmapBytes.size.toLong()
            } else {
                break
            }
        }
    }
}

// ── Thumbnail Cache ───────────────────────────────────────────────────

data class CachedThumbnail(
    val pageId: String,
    val pageIndex: Int,
    val bitmapBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotation: PageRotation,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class ThumbnailCache(
    private val maxEntries: Int = 200
) {
    private val cache = linkedMapOf<String, CachedThumbnail>()
    private val mutex = Mutex()

    suspend fun put(entry: CachedThumbnail) {
        mutex.withLock {
            cache[entry.pageId] = entry
            while (cache.size > maxEntries) {
                cache.remove(cache.keys.first())
            }
        }
    }

    suspend fun get(pageId: String): CachedThumbnail? {
        mutex.withLock {
            val entry = cache.remove(pageId) ?: return null
            cache[pageId] = entry  // re-insert at end (most recently accessed)
            return entry
        }
    }

    suspend fun invalidate(pageId: String) {
        mutex.withLock { cache.remove(pageId) }
    }

    suspend fun clear() {
        mutex.withLock { cache.clear() }
    }
}