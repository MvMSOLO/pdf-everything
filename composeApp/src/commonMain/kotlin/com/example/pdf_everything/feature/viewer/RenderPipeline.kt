package com.example.pdf_everything.feature.viewer

import com.example.pdf_everything.core.document.*
import com.example.pdf_everything.core.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.abs

/* ═══════════════════════════════════════════════════════════════════════
 *  Render Pipeline — per spec §57
 *
 *  VisibleViewport → PagePriorityQueue → RenderScheduler →
 *  RenderWorkers → Surface/TileCache → UI compositor
 *
 *  Key behaviors:
 *    - Cancel stale renders when viewport changes rapidly
 *    - Prioritize currently visible pages (high), neighbors (medium), far (low)
 *    - Tag every render result with docVersion/pageVersion/viewportKey
 *      so stale content cannot overwrite current edits (spec §57)
 *    - Support tile-based rendering for zoomed-in pages
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Render versioning ─────────────────────────────────────────────────

/** Tags each render result so stale content is rejected. */
data class RenderVersionTag(
    val documentId: String,
    val documentVersion: Int,
    val pageIndex: Int,
    val pageVersion: Int,
    val viewportKey: Long  // hash of viewport state at request time
) {
    fun isCurrent(other: RenderVersionTag): Boolean =
        documentId == other.documentId &&
        documentVersion == other.documentVersion &&
        pageIndex == other.pageIndex &&
        pageVersion == other.pageVersion &&
        viewportKey == other.viewportKey
}

// ── Render request & result ─────────────────────────────────────────────

enum class RenderPriority { HIGH, MEDIUM, LOW }

data class RenderRequest(
    val tag: RenderVersionTag,
    val config: RenderConfig,
    val priority: RenderPriority,
    val tile: TileKey? = null  // null = full-page render
)

data class TileKey(
    val pageId: String,
    val x: Int, val y: Int,
    val tileSize: Int  // pixels
)

data class RenderResult(
    val tag: RenderVersionTag,
    val request: RenderRequest,
    val bitmapBytes: ByteArray?,  // null if cancelled/failed
    val width: Int,
    val height: Int,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

// ── Visible viewport ──────────────────────────────────────────────────

/** Describes the currently visible portion of the document. */
data class VisibleViewport(
    val firstVisiblePage: Int,
    val lastVisiblePage: Int,
    val totalPages: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val zoomScale: Float
) {
    /** Center page of the visible range. */
    val centerPage: Int get() = (firstVisiblePage + lastVisiblePage) / 2

    fun viewportKey(): Long {
        var hash = 17L
        hash = 31L * hash + firstVisiblePage
        hash = 31L * hash + lastVisiblePage
        hash = 31L * hash + viewportWidth
        hash = 31L * hash + viewportHeight
        hash = 31L * hash + zoomScale.toBits().toLong()
        return hash
    }
}

// ── Page priority queue ──────────────────────────────────────────────

/**
 * Priority queue that orders render requests.
 * HIGH = currently visible pages, MEDIUM = neighbors, LOW = far pages.
 * Re-sorts when viewport changes.
 */
class PagePriorityQueue {
    private val queue = mutableListOf<RenderRequest>()

    @Synchronized
    fun enqueue(request: RenderRequest) {
        // Remove any existing request for same page+tile with lower or equal priority
        val existingIdx = queue.indexOfFirst {
            it.tag.pageIndex == request.tag.pageIndex &&
            it.tile == request.tile &&
            it.tag.documentId == request.tag.documentId
        }
        if (existingIdx >= 0) {
            val existing = queue[existingIdx]
            // If the new request has a newer version, replace
            if (request.tag.viewportKey != existing.tag.viewportKey ||
                request.tag.documentVersion > existing.tag.documentVersion) {
                queue.removeAt(existingIdx)
            }
        }
        queue.add(request)
        sortByPriority()
    }

    @Synchronized
    fun dequeue(): RenderRequest? {
        if (queue.isEmpty()) return null
        return queue.removeAt(0)
    }

    @Synchronized
    fun peek(): RenderRequest? = queue.firstOrNull()

    @Synchronized
    fun cancelStale(currentTag: RenderVersionTag) {
        queue.removeAll { req ->
            req.tag.documentId == currentTag.documentId &&
            !req.tag.isCurrent(currentTag) &&
            req.tag.pageIndex != currentTag.pageIndex
        }
    }

    @Synchronized
    fun reprioritize(
        viewport: VisibleViewport,
        documentId: String,
        documentVersion: Int,
        pages: List<Page>
    ) {
        // Reassign priorities based on viewport
        val updated = queue.map { req ->
            if (req.tag.documentId != documentId) return@map req
            val pageIdx = req.tag.pageIndex
            val newPriority = when {
                pageIdx in viewport.firstVisiblePage..viewport.lastVisiblePage -> RenderPriority.HIGH
                abs(pageIdx - viewport.centerPage) <= 2 -> RenderPriority.MEDIUM
                else -> RenderPriority.LOW
            }
            val newTag = req.tag.copy(
                documentVersion = documentVersion,
                viewportKey = viewport.viewportKey()
            )
            req.copy(priority = newPriority, tag = newTag)
        }
        queue.clear()
        queue.addAll(updated)
        sortByPriority()
    }

    @Synchronized
    fun clear() {
        queue.clear()
    }

    @Synchronized
    fun size(): Int = queue.size

    private fun sortByPriority() {
        queue.sortBy { it.priority.ordinal }  // HIGH=0 < MEDIUM=1 < LOW=2
    }
}

// ── Render scheduler ─────────────────────────────────────────────────

/**
 * Coordinates render requests: pulls from priority queue,
 * dispatches to workers, cancels stale work.
 */
class RenderScheduler(
    private val engine: PdfEngine?,
    private val workerCount: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val pageVersionTracker: PageVersionTracker = PageVersionTracker()
) {
    /** Convenience constructor without engine (for UI preview / testing) */
    constructor(
        maxConcurrentRenders: Int = 2,
        pdfEngine: PdfEngine? = null
    ) : this(
        engine = pdfEngine,
        workerCount = maxConcurrentRenders
    )
    private val priorityQueue = PagePriorityQueue()
    private val resultChannel = Channel<RenderResult>(capacity = Channel.UNLIMITED)
    private val activeJobs = mutableMapOf<Int, Job>()  // pageIndex → Job
    private val cancelledJobs = mutableSetOf<Job>()

    val results: Flow<RenderResult> = resultChannel.receiveAsFlow()

    fun submitViewport(
        viewport: VisibleViewport,
        documentId: String,
        documentVersion: Int,
        pages: List<Page>,
        config: RenderConfig
    ) {
        val viewportKey = viewport.viewportKey()

        // Cancel all active jobs that are now stale
        cancelStaleJobs(documentId, documentVersion, viewportKey)

        // Re-prioritize existing queue entries
        priorityQueue.reprioritize(viewport, documentId, documentVersion, pages)

        // Enqueue visible pages
        for (pageIdx in viewport.firstVisiblePage..viewport.lastVisiblePage) {
            if (pageIdx !in pages.indices) continue
            val page = pages[pageIdx]
            val tag = RenderVersionTag(
                documentId = documentId,
                documentVersion = documentVersion,
                pageIndex = pageIdx,
                pageVersion = pageVersionTracker.getVersion(page.pageId),
                viewportKey = viewportKey
            )
            priorityQueue.enqueue(RenderRequest(
                tag = tag,
                config = config.copy(
                    maxWidth = viewport.viewportWidth,
                    maxHeight = viewport.viewportHeight,
                    scale = viewport.zoomScale
                ),
                priority = RenderPriority.HIGH
            ))
        }

        // Enqueue neighbor pages
        val neighborRange = maxOf(0, viewport.firstVisiblePage - 2)..minOf(pages.lastIndex, viewport.lastVisiblePage + 2)
        for (pageIdx in neighborRange) {
            if (pageIdx in viewport.firstVisiblePage..viewport.lastVisiblePage) continue
            if (pageIdx !in pages.indices) continue
            val page = pages[pageIdx]
            val tag = RenderVersionTag(
                documentId = documentId,
                documentVersion = documentVersion,
                pageIndex = pageIdx,
                pageVersion = pageVersionTracker.getVersion(page.pageId),
                viewportKey = viewportKey
            )
            priorityQueue.enqueue(RenderRequest(
                tag = tag,
                config = config,
                priority = RenderPriority.MEDIUM
            ))
        }
    }

    fun submitSinglePage(
        documentId: String,
        documentVersion: Int,
        page: Page,
        config: RenderConfig,
        priority: RenderPriority = RenderPriority.HIGH
    ) {
        val tag = RenderVersionTag(
            documentId = documentId,
            documentVersion = documentVersion,
            pageIndex = page.index,
            pageVersion = pageVersionTracker.getVersion(page.pageId),
            viewportKey = 0L  // single-page request, not viewport-tracked
        )
        priorityQueue.enqueue(RenderRequest(
            tag = tag,
            config = config,
            priority = priority
        ))
    }

    private fun cancelStaleJobs(documentId: String, documentVersion: Int, viewportKey: Long) {
        val currentTag = RenderVersionTag(
            documentId = documentId,
            documentVersion = documentVersion,
            pageIndex = -1,
            pageVersion = -1,
            viewportKey = viewportKey
        )
        priorityQueue.cancelStale(currentTag)

        // Cancel running jobs for pages that are no longer high priority
        activeJobs.entries.toList().forEach { (pageIdx, job) ->
            if (!job.isActive) {
                activeJobs.remove(pageIdx)
                return@forEach
            }
            // Let high-priority pages continue; cancel medium/low if stale
            // More aggressive: cancel if viewportKey doesn't match
            cancelledJobs.add(job)
            job.cancel()
            activeJobs.remove(pageIdx)
        }
    }

    fun startWorkers() {
        repeat(workerCount) { workerId ->
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val request = priorityQueue.dequeue() ?: run {
                        delay(50)  // idle wait
                        continue
                    }
                    if (cancelledJobs.any { it.isActive }) {
                        cancelledJobs.removeAll { !it.isActive }
                        continue
                    }
                    val job = launch {
                        renderWorker(workerId, request)
                    }
                    activeJobs[request.tag.pageIndex] = job
                    job.join()
                    activeJobs.remove(request.tag.pageIndex)
                }
            }
        }
    }

    private suspend fun renderWorker(workerId: Int, request: RenderRequest) {
        try {
            val result = engine?.renderPage(
                documentId = request.tag.documentId,
                pageIndex = request.tag.pageIndex,
                config = request.config
            )
            if (result == null) {
                resultChannel.send(RenderResult(
                    tag = request.tag,
                    request = request,
                    bitmapBytes = null,
                    width = 0,
                    height = 0,
                    isError = true,
                    errorMessage = "PdfEngine not available"
                ))
                return
            }
            when (result) {
                is EngineResult.Success -> {
                    val rendered = result.value
                    resultChannel.send(RenderResult(
                        tag = request.tag,
                        request = request,
                        bitmapBytes = rendered.bitmapBytes,
                        width = rendered.width,
                        height = rendered.height
                    ))
                }
                is EngineResult.Failure -> {
                    resultChannel.send(RenderResult(
                        tag = request.tag,
                        request = request,
                        bitmapBytes = null,
                        width = 0,
                        height = 0,
                        isError = true,
                        errorMessage = result.message
                    ))
                }
            }
        } catch (e: CancellationException) {
            // Render was cancelled (stale), don't send result
        } catch (e: Exception) {
            resultChannel.send(RenderResult(
                tag = request.tag,
                request = request,
                bitmapBytes = null,
                width = 0,
                height = 0,
                isError = true,
                errorMessage = e.message
            ))
        }
    }

    /**
     * Convenience method: submit a single page render request.
     * Used by ViewerScreen for on-demand rendering.
     */
    fun requestRender(
        pageIndex: Int,
        config: RenderConfig,
        documentId: String = "",
        documentVersion: Int = 0,
        pageId: String = "page_$pageIndex"
    ) {
        if (engine == null) return  // no engine available
        val tag = RenderVersionTag(
            documentId = documentId,
            documentVersion = documentVersion,
            pageIndex = pageIndex,
            pageVersion = pageVersionTracker.getVersion(pageId),
            viewportKey = 0L
        )
        priorityQueue.enqueue(RenderRequest(
            tag = tag,
            config = config,
            priority = RenderPriority.HIGH
        ))
    }

    fun shutdown() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        priorityQueue.clear()
        resultChannel.close()
    }
}

// ── Page version tracking ────────────────────────────────────────────

/**
 * Tracks page edit version. Each modification bumps the version
 * so render pipeline can detect stale renders.
 */
class PageVersionTracker {
    private val versions = mutableMapOf<String, Int>()  // pageId → version

    fun getVersion(pageId: String): Int = versions[pageId] ?: 0

    fun bumpVersion(pageId: String): Int {
        val new = (versions[pageId] ?: 0) + 1
        versions[pageId] = new
        return new
    }

    fun reset(pageId: String) {
        versions.remove(pageId)
    }

    fun resetAll() {
        versions.clear()
    }
}