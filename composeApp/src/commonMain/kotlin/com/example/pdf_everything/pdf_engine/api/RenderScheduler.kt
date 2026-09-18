package com.example.pdf_everything.pdf_engine.api

import kotlinx.coroutines.*

class RenderScheduler(
    private val engine: PdfEngine,
    private val cache: com.example.pdf_everything.RenderMemoryCache,
    parentScope: CoroutineScope
) : AutoCloseable {
    private var generation = 0L
    private val generationLock = Any()
    private val scope = parentScope + SupervisorJob(parentScope.coroutineContext[Job])
    private val activeJobs = mutableMapOf<String, Job>()

    fun cancelAll() { synchronized(generationLock) { generation++ }; synchronized(activeJobs) { activeJobs.values.forEach(Job::cancel); activeJobs.clear() } }

    fun request(
        pageIndex: Int,
        viewport: RenderViewport,
        priority: RenderPriority,
        thumbnail: Boolean = false,
        onResult: (RenderedPage) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val key = viewport.cacheKey.ifBlank { "${pageIndex}:${viewport.widthPx}:${viewport.scale}:${viewport.tileX}:${viewport.tileY}" }
        val existing = if (viewport.tileX != null) cache.tile(key) else if (thumbnail) cache.thumbnail(key) else cache.page(key)
        if (existing != null) { onResult(existing); return }
        val requestGeneration = synchronized(generationLock) { generation }
        lateinit var job: Job
        synchronized(activeJobs) {
            if (activeJobs[key]?.isActive == true) return
            job = scope.launch(Dispatchers.Default + CoroutineName("pdf-render-$priority-$pageIndex")) {
            try {
                when (priority) { RenderPriority.CURRENT -> Unit; RenderPriority.VISIBLE -> delay(8); RenderPriority.NEIGHBOR -> delay(80); RenderPriority.THUMBNAIL -> delay(180) }
                if (requestGeneration != synchronized(generationLock) { generation }) return@launch
                val rendered = engine.renderPage(pageIndex, viewport)
                if (requestGeneration != synchronized(generationLock) { generation }) return@launch
                when {
                    viewport.tileX != null -> cache.putTile(key, rendered)
                    thumbnail -> cache.putThumbnail(key, rendered)
                    else -> cache.putPage(key, rendered)
                }
                withContext(Dispatchers.Main.immediate) { onResult(rendered) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) { withContext(Dispatchers.Main.immediate) { onError(error) } }
            finally { synchronized(activeJobs) { activeJobs.remove(key) } }
            }
            activeJobs[key] = job
        }
    }

    override fun close() { cancelAll(); scope.cancel() }
}

enum class RenderPriority { CURRENT, VISIBLE, NEIGHBOR, THUMBNAIL }
