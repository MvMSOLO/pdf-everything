package com.example.pdf_everything.core.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.pdf_everything.RenderMemoryCache
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.pdf_engine.api.PdfPageInfo
import com.example.pdf_everything.pdf_engine.api.RenderViewport
import com.example.pdf_everything.pdf_engine.api.LocalPdfRenderScheduler
import com.example.pdf_everything.pdf_engine.api.RenderPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.toComposeImageBitmap

@Composable
fun Phase3RenderedImage(engine: PdfEngine, page: PdfPageInfo, width: Int, cache: RenderMemoryCache) {
    val key = "p3|${page.documentVersion}|${page.pageId}|$width"
    var rendered by remember(key) { mutableStateOf(cache.page(key)) }
    val scheduler = LocalPdfRenderScheduler.current
    LaunchedEffect(key, scheduler) {
        if (rendered == null) {
            val viewport = RenderViewport(widthPx = width, scale = width / page.width, cacheKey = key)
            if (scheduler != null) {
                scheduler.request(page.index, viewport, RenderPriority.VISIBLE, onResult = { result ->
                    if (result.documentVersion == page.documentVersion && result.viewportKey == key) rendered = result
                }) { error -> /* UI remains in loading state; caller can retry through recomposition. */ }
            } else {
                runCatching { withContext(Dispatchers.Default) { engine.renderPage(page.index, viewport) } }
                    .onSuccess { result -> if (result.documentVersion == page.documentVersion && result.viewportKey == key) { cache.putPage(key, result); rendered = result } }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        rendered?.let { result ->
            val bitmap: ImageBitmap = Image.makeFromEncoded(result.png).toComposeImageBitmap()
            Image(bitmap, "PDF page ${page.index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } ?: LinearProgressIndicator(Modifier.fillMaxSize())
    }
}
