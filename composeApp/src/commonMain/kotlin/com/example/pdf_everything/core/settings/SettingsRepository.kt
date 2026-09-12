package com.example.pdf_everything.core.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Cross-platform settings persistence per spec §44.
 *
 * Uses [MultiplatformSettings] as the underlying storage backend:
 *   - Android: DataStore (via multiplatform-settings-datastore)
 *   - Desktop: Java Preferences (via multiplatform-settings-jvm)
 *
 * All properties are observable (Compose mutableStateOf) so the UI
 * reacts to changes automatically.  Every setter also persists the
 * value to disk immediately.
 */
class SettingsRepository(
    private val delegate: MultiplatformSettings
) {
    // ── General ───────────────────────────────────────────────────

    var darkTheme by mutableStateOf(delegate.getBoolean(KEY_DARK_THEME, false))
        private set

    fun setDarkTheme(value: Boolean) {
        darkTheme = value
        delegate.putBoolean(KEY_DARK_THEME, value)
    }

    var dynamicColor by mutableStateOf(delegate.getBoolean(KEY_DYNAMIC_COLOR, true))
        private set

    fun setDynamicColor(value: Boolean) {
        dynamicColor = value
        delegate.putBoolean(KEY_DYNAMIC_COLOR, value)
    }

    var language by mutableStateOf(delegate.getString(KEY_LANGUAGE, "System"))
        private set

    fun setLanguage(value: String) {
        language = value
        delegate.putString(KEY_LANGUAGE, value)
    }

    // ── View ──────────────────────────────────────────────────────

    var showPageNumbers by mutableStateOf(delegate.getBoolean(KEY_SHOW_PAGE_NUMBERS, true))
        private set

    fun setShowPageNumbers(value: Boolean) {
        showPageNumbers = value
        delegate.putBoolean(KEY_SHOW_PAGE_NUMBERS, value)
    }

    var defaultZoomMode by mutableStateOf(delegate.getString(KEY_DEFAULT_ZOOM, "Fit Width"))
        private set

    fun setDefaultZoomMode(value: String) {
        defaultZoomMode = value
        delegate.putString(KEY_DEFAULT_ZOOM, value)
    }

    var smoothScrolling by mutableStateOf(delegate.getBoolean(KEY_SMOOTH_SCROLL, true))
        private set

    fun setSmoothScrolling(value: Boolean) {
        smoothScrolling = value
        delegate.putBoolean(KEY_SMOOTH_SCROLL, value)
    }

    var snapToPage by mutableStateOf(delegate.getBoolean(KEY_SNAP_TO_PAGE, false))
        private set

    fun setSnapToPage(value: Boolean) {
        snapToPage = value
        delegate.putBoolean(KEY_SNAP_TO_PAGE, value)
    }

    // ── Editing ──────────────────────────────────────────────────

    var annotationToolbar by mutableStateOf(delegate.getBoolean(KEY_ANNOT_TOOLBAR, true))
        private set

    fun setAnnotationToolbar(value: Boolean) {
        annotationToolbar = value
        delegate.putBoolean(KEY_ANNOT_TOOLBAR, value)
    }

    var autoSave by mutableStateOf(delegate.getBoolean(KEY_AUTO_SAVE, true))
        private set

    fun setAutoSave(value: Boolean) {
        autoSave = value
        delegate.putBoolean(KEY_AUTO_SAVE, value)
    }

    var autoSaveInterval by mutableStateOf(delegate.getInt(KEY_AUTO_SAVE_INTERVAL, 30))
        private set

    fun setAutoSaveInterval(value: Int) {
        autoSaveInterval = value
        delegate.putInt(KEY_AUTO_SAVE_INTERVAL, value)
    }

    // ── Performance ──────────────────────────────────────────────

    var hardwareAccel by mutableStateOf(delegate.getBoolean(KEY_HW_ACCEL, true))
        private set

    fun setHardwareAccel(value: Boolean) {
        hardwareAccel = value
        delegate.putBoolean(KEY_HW_ACCEL, value)
    }

    var cacheSizeMb by mutableStateOf(delegate.getInt(KEY_CACHE_SIZE, 256))
        private set

    fun setCacheSizeMb(value: Int) {
        cacheSizeMb = value
        delegate.putInt(KEY_CACHE_SIZE, value)
    }

    // ── Files ─────────────────────────────────────────────────────

    var maxRecentFiles by mutableStateOf(delegate.getInt(KEY_MAX_RECENT, 20))
        private set

    fun setMaxRecentFiles(value: Int) {
        maxRecentFiles = value
        delegate.putInt(KEY_MAX_RECENT, value)
    }

    var defaultSaveDir by mutableStateOf(delegate.getString(KEY_DEFAULT_SAVE_DIR, "Documents/PDF Everything"))
        private set

    fun setDefaultSaveDir(value: String) {
        defaultSaveDir = value
        delegate.putString(KEY_DEFAULT_SAVE_DIR, value)
    }

    // ── Key constants ─────────────────────────────────────────────

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SHOW_PAGE_NUMBERS = "show_page_numbers"
        private const val KEY_DEFAULT_ZOOM = "default_zoom"
        private const val KEY_SMOOTH_SCROLL = "smooth_scrolling"
        private const val KEY_SNAP_TO_PAGE = "snap_to_page"
        private const val KEY_ANNOT_TOOLBAR = "annotation_toolbar"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_AUTO_SAVE_INTERVAL = "auto_save_interval"
        private const val KEY_HW_ACCEL = "hardware_accel"
        private const val KEY_CACHE_SIZE = "cache_size_mb"
        private const val KEY_MAX_RECENT = "max_recent_files"
        private const val KEY_DEFAULT_SAVE_DIR = "default_save_dir"
    }
}

/**
 * Platform-agnostic settings delegate interface.
 * Android uses DataStore; Desktop uses java.util.prefs.Preferences.
 */
interface MultiplatformSettings {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}