package com.example.pdf_everything.feature.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * Desktop (JVM) actual: decodes encoded image bytes (PNG/JPEG) into
 * Compose [ImageBitmap] using Skia's [Image.makeFromEncoded].
 *
 * The Desktop Compose runtime uses Skia under the hood, so we can
 * leverage Skia directly for efficient image decoding.
 *
 * If the bytes cannot be decoded, returns null so the caller falls
 * back to a placeholder.
 */
actual fun createImageBitmapFromBytes(
    bytes: ByteArray,
    width: Int,
    height: Int
): ImageBitmap? {
    return try {
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        // Skia decode failed — corrupted / unsupported format.
        // Fall back to null so PageCanvas draws the placeholder grid.
        null
    }
}
