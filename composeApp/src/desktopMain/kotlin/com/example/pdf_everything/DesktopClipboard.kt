package com.example.pdf_everything.core.clipboard

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val OBJECT_PREFIX = "PDF_EVERYTHING_OBJECT_v1:"

actual object PlatformClipboard {
    private fun clipboard(): Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    actual fun read(): ClipboardSnapshot {
        val c = clipboard()
        val t = runCatching { c.getContents(null) }.getOrNull() ?: return ClipboardSnapshot()
        val text = if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) runCatching { t.getTransferData(DataFlavor.stringFlavor) as String }.getOrNull() else null
        val obj: ObjectClipboard? = null
        val image = if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) runCatching { encodePng(t.getTransferData(DataFlavor.imageFlavor) as BufferedImage) }.getOrNull() else null
        return ClipboardSnapshot(text = text?.takeUnless { it.startsWith(OBJECT_PREFIX) }, image = image, objectPayload = obj)
    }

    actual fun writeText(text: String) { clipboard().setContents(StringSelection(text), null) }
    actual fun writeObject(payload: ObjectClipboard) { clipboard().setContents(StringSelection("PDF Everything object — paste inside PDF Everything"), null) }
    actual fun writeImage(png: ByteArray) {
        val image = javax.imageio.ImageIO.read(png.inputStream()) ?: return
        clipboard().setContents(object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor): Any = if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedOperationException()
        }, null)
    }

    private fun encodePng(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out -> ImageIO.write(image, "png", out); out.toByteArray() }
    actual fun materializeImage(png: ByteArray): com.example.pdf_everything.core.document.DocumentSource? = runCatching {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "pdf-everything-clipboard").apply { mkdirs() }
        val file = java.io.File.createTempFile("clipboard-", ".png", dir)
        file.writeBytes(png)
        com.example.pdf_everything.core.document.DocumentSource.FilePath(file.absolutePath)
    }.getOrNull()

}
