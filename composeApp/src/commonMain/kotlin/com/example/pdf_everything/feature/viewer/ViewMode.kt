package com.example.pdf_everything.feature.viewer

import androidx.compose.runtime.*

/* ═══════════════════════════════════════════════════════════════════════
 *  View Modes — per spec §66 & §67
 *
 *  Supported modes:
 *    - SinglePage:  one page centered
 *    - Continuous:  vertical scrolling through all pages
 *    - TwoPage:    side-by-side spreads on sufficiently wide screens
 *    - Organizer:   thumbnail grid for page management
 *    - Presentation: full-screen, minimal chrome
 * ═══════════════════════════════════════════════════════════════════════ */

enum class ViewMode {
    SINGLE_PAGE,
    CONTINUOUS,
    TWO_PAGE,
    ORGANIZER,
    PRESENTATION
}

/**
 * State holder for the current view mode and its transitions.
 */
class ViewModeState(
    initialMode: ViewMode = ViewMode.SINGLE_PAGE
) {
    private var _currentMode by mutableStateOf(initialMode)
    val currentMode: ViewMode get() = _currentMode

    private var _isFullscreen by mutableStateOf(false)
    val isFullscreen: Boolean get() = _isFullscreen

    private var _showChrome by mutableStateOf(true)  // toolbars, panels
    val showChrome: Boolean get() = _showChrome

    private var _showNavigationOverlay by mutableStateOf(false)
    val showNavigationOverlay: Boolean get() = _showNavigationOverlay

    fun setMode(mode: ViewMode) {
        _currentMode = mode
        when (mode) {
            ViewMode.PRESENTATION -> {
                _isFullscreen = true
                _showChrome = false
            }
            ViewMode.ORGANIZER -> {
                _showChrome = true
            }
            else -> {
                _showChrome = true
                _isFullscreen = false
            }
        }
    }

    fun toggleFullscreen() {
        if (_isFullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
    }

    fun enterFullscreen() {
        _isFullscreen = true
        _showChrome = false
        _currentMode = ViewMode.PRESENTATION
    }

    fun exitFullscreen() {
        _isFullscreen = false
        _showChrome = true
        _currentMode = ViewMode.SINGLE_PAGE
    }

    /**
     * In presentation mode, tap to temporarily show navigation overlay.
     */
    fun showNavigationOverlay() {
        if (_isFullscreen) {
            _showNavigationOverlay = true
        }
    }

    fun hideNavigationOverlay() {
        _showNavigationOverlay = false
    }

    fun toggleNavigationOverlay() {
        _showNavigationOverlay = !_showNavigationOverlay
    }

    fun toggleChrome() {
        _showChrome = !_showChrome
    }

    val isOrganizer: Boolean get() = _currentMode == ViewMode.ORGANIZER
    val isPresentation: Boolean get() = _currentMode == ViewMode.PRESENTATION
    val supportsSideBySide: Boolean get() = _currentMode == ViewMode.TWO_PAGE
    val isScrollable: Boolean get() = _currentMode == ViewMode.CONTINUOUS
}