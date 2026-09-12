package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.history.HistoryManager
import com.example.pdf_everything.core.recovery.RecoveryService
import com.example.pdf_everything.core.shortcuts.ShortcutRegistry
import com.example.pdf_everything.core.shortcuts.ShortcutMode
import com.example.pdf_everything.core.settings.SettingsRepository

/**
 * Application-wide state — single source of truth for the current
 * [Document], [PdfEngine], [CommandDispatcher], [HistoryManager],
 * [DocumentFileService], [RecoveryService] and [ShortcutRegistry].
 *
 * Per spec §22: supports multi-document tabs.
 */
class AppState(
    val pdfEngine: PdfEngine,
    val commandDispatcher: CommandDispatcher,
    val historyManager: HistoryManager,
    val documentFileService: DocumentFileService,
    val recoveryService: RecoveryService,
    val shortcutRegistry: ShortcutRegistry = ShortcutRegistry.createDefault(),
    val settingsRepository: SettingsRepository = SettingsRepository(object : com.example.pdf_everything.core.settings.MultiplatformSettings {
        override fun getBoolean(key: String, default: Boolean) = default
        override fun putBoolean(key: String, value: Boolean) {}
        override fun getInt(key: String, default: Int) = default
        override fun putInt(key: String, value: Int) {}
        override fun getString(key: String, default: String) = default
        override fun putString(key: String, value: String) {}
    })
) {
    // ── Multi-document tab support per spec §22 ──────────────────

    data class DocumentTab(
        val documentId: String,
        val name: String,
        val isDirty: Boolean = false
    )

    private val _tabs = mutableListOf<DocumentTab>()
    val tabs: List<DocumentTab> get() = _tabs.toList()

    private var _activeTabIndex: Int = -1
    val activeTabIndex: Int get() = _activeTabIndex

    val activeTab: DocumentTab? get() = _tabs.getOrNull(_activeTabIndex)

    // ── Legacy single-document accessors ─────────────────────────
    /** Currently active document – null when nothing is loaded. */
    var currentDocument: Document? = null
        private set

    // Per-document undo history (docId → version snapshot)
    private val docVersions = mutableMapOf<String, Int>()

    /** Open a new document in a new tab. */
    fun openDocument(doc: Document) {
        // If already open, just switch to that tab
        val existingIdx = _tabs.indexOfFirst { it.documentId == doc.documentId }
        if (existingIdx >= 0) {
            _activeTabIndex = existingIdx
            currentDocument = doc
            return
        }
        _tabs.add(DocumentTab(
            documentId = doc.documentId,
            name = doc.name,
            isDirty = doc.isDirty
        ))
        _activeTabIndex = _tabs.size - 1
        currentDocument = doc
        historyManager.clear()
        docVersions[doc.documentId] = doc.version
    }

    /** Update the current document in-place (e.g. after undo/redo).
     *  Unlike openDocument this does NOT clear the history stack.
     */
    fun updateDocument(doc: Document) {
        currentDocument = doc
        // Update tab dirty state
        val idx = _tabs.indexOfFirst { it.documentId == doc.documentId }
        if (idx >= 0) {
            _tabs[idx] = _tabs[idx].copy(isDirty = doc.isDirty, name = doc.name)
        }
    }

    /** Switch to a tab by index. */
    fun switchTab(index: Int) {
        if (index < 0 || index >= _tabs.size) return
        _activeTabIndex = index
        // We only track one document at a time in currentDocument for Phase 1;
        // the tab list tracks which documents are "open".
        // Full per-tab Document instances require engine re-open per tab (Phase 2).
    }

    /** Switch to the next tab. */
    fun switchNextTab() {
        if (_tabs.size <= 1) return
        switchTab((_activeTabIndex + 1) % _tabs.size)
    }

    /** Switch to the previous tab. */
    fun switchPrevTab() {
        if (_tabs.size <= 1) return
        switchTab((_activeTabIndex - 1).coerceAtLeast(0))
    }

    /** Close a tab by index. */
    fun closeTab(index: Int) {
        if (index < 0 || index >= _tabs.size) return
        val wasActive = index == _activeTabIndex
        _tabs.removeAt(index)
        if (_tabs.isEmpty()) {
            _activeTabIndex = -1
            currentDocument = null
            historyManager.clear()
        } else if (wasActive) {
            _activeTabIndex = (index).coerceAtMost(_tabs.size - 1)
        } else if (_activeTabIndex > index) {
            _activeTabIndex--
        }
    }

    /** Close the currently active tab. */
    fun closeActiveTab() {
        closeTab(_activeTabIndex)
    }

    /** Close the current document and remove its tab. */
    fun closeDocument() {
        closeActiveTab()
    }

    // ── Shortcut mode sync ───────────────────────────────────────

    fun updateShortcutModeFromRoute(routeName: String) {
        shortcutRegistry.setMode(when (routeName) {
            "Viewer" -> ShortcutMode.VIEW
            "Editor" -> ShortcutMode.EDIT
            "FormFill" -> ShortcutMode.FORM_FILL
            else -> ShortcutMode.GLOBAL
        })
    }
}