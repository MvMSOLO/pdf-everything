package com.example.pdf_everything.feature.viewer

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android actual: decodes raw ARGB_8888 byte arrays into Compose [ImageBitmap]
 * using Android's [BitmapFactory].
 *
 * The bytes are expected to be in PNG or JPEG format (as produced by
 * PdfiumAdapter rendering). If decoding fails, returns null so the
 * caller can fall back to a placeholder.
 */
actual fun createImageBitmapFromBytes(
    bytes: ByteArray,
    width: Int,
    height: Int
): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            // Don't scale — we want exact pixel dimensions from the renderer
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return null

        // Safety check: if the decoded bitmap dimensions don't match expectations,
        // we still return it — the caller will scale it to fit the viewport.
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        // BitmapFactory can throw for corrupted / truncated data.
        // Returning null triggers the placeholder path in PageCanvas.
        null
    }
}
