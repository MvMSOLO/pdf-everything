package com.example.pdf_everything.features.cut

import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.RectF

/** Coordinates use the app's top-left page coordinate system. */
data class CropFrame(
    val rect: RectF,
    val lockAspect: Boolean = false,
    val preset: CropPreset = CropPreset.Free
)

enum class CropPreset { Free, A4, A5, Letter, Legal, Original, Portrait, Landscape }

enum class CutTool { Crop, Trim, Split, Extract, DeleteArea }

data class SplitPlan(val groups: List<IntRange>) {
    val partCount: Int get() = groups.size
}

data class PageSection(val rect: RectF, val label: String)

data class SectionCutPlan(val sections: List<PageSection>)

fun pageContentBounds(page: Page, includeBackgroundObjects: Boolean = true, padding: Float = 0f): RectF? {
    val candidates = page.objects.filter { includeBackgroundObjects || it.zOrder != Int.MIN_VALUE }
    if (candidates.isEmpty()) return null
    val left = candidates.minOf { it.bounds.left }
    val top = candidates.minOf { it.bounds.top }
    val right = candidates.maxOf { it.bounds.right }
    val bottom = candidates.maxOf { it.bounds.bottom }
    return RectF(
        (left - padding).coerceAtLeast(page.boxes.media.left),
        (top - padding).coerceAtLeast(page.boxes.media.top),
        (right + padding).coerceAtMost(page.boxes.media.right),
        (bottom + padding).coerceAtMost(page.boxes.media.bottom)
    )
}

fun pageRectToAspectFit(page: Page, ratio: Float, landscape: Boolean): RectF {
    val media = page.boxes.media
    val mediaW = media.width
    val mediaH = media.height
    val targetRatio = if (landscape) ratio.coerceAtLeast(0.01f) else (1f / ratio.coerceAtLeast(0.01f))
    return if (mediaW / mediaH > targetRatio) {
        val h = mediaH
        val w = h * targetRatio
        val left = media.left + (mediaW - w) / 2f
        RectF(left, media.top, left + w, media.bottom)
    } else {
        val w = mediaW
        val h = w / targetRatio
        val top = media.top + (mediaH - h) / 2f
        RectF(media.left, top, media.right, top + h)
    }
}
