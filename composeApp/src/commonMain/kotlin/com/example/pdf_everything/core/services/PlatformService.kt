package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.DocumentSource

/**
 * Platform abstraction — expect/actual for services that differ between
 * Android and Desktop (JVM).
 *
 * Per spec §28‑§29:
 *   - File picking (open / save)
 *   - File association registration
 *   - Native launch ("open in external app")
 *   - Storage info (available space)
 *   - Platform name / isDesktop shortcuts
 */

expect class PlatformService() {

    /** Platform identifier: "android" or "desktop" */
    val platformName: String

    val isDesktop: Boolean
    val isMobile: Boolean

    // ── File pickers ──────────────────────────────────────────────────

    /**
     * Launch an open-file picker filtered to PDF MIME type.
     * @return a [DocumentSource] if the user selected a file, or null on cancel.
     */
    suspend fun pickOpenFile(
        title: String = "Open PDF",
        allowedExtensions: List<String> = listOf("pdf")
    ): DocumentSource?

    /**
     * Launch a save-file picker.
     * @return a file-path string if the user chose a location, or null on cancel.
     */
    suspend fun pickSaveFile(
        title: String = "Save PDF",
        defaultName: String = "document.pdf",
        allowedExtensions: List<String> = listOf("pdf")
    ): String?

    // ── External app launch ──────────────────────────────────────────

    /** Open the given file with the OS-default handler. */
    suspend fun launchExternal(source: DocumentSource): Boolean

    // ── Storage ───────────────────────────────────────────────────────

    /** Available bytes on the volume that contains [path]. */
    fun availableStorageBytes(path: String): Long

    /** Total bytes on the volume that contains [path]. */
    fun totalStorageBytes(path: String): Long

    // ── File associations ─────────────────────────────────────────────

    /**
     * Register this app as a handler for PDF files (called once on install
     * or first launch). Desktop-only; no-op on Android.
     */
    fun registerFileAssociation()
}