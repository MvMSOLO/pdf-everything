package com.example.pdf_everything.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive layout adapter per spec §48-§50.
 *
 * Window-size breakpoints:
 *   Compact  : < 600dp   (phone)
 *   Medium   : 600-840dp (tablet portrait / small window)
 *   Expanded : > 840dp   (tablet landscape / desktop)
 *
 * These classes drive layout decisions (single-column vs two-pane,
 * toolbar arrangement, sidebar visibility, etc.).
 */

enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeightClass {
    COMPACT,    // < 480dp
    MEDIUM,     // 480-900dp
    EXPANDED    // > 900dp
}

enum class DevicePosture {
    NORMAL,
    TABLETOP,       // Foldable half-opened (book posture)
    BOOK,           // Foldable fully-opened (flat)
    FOLDED          // Foldable closed
}

data class LayoutConfiguration(
    val widthClass: WindowSizeClass,
    val heightClass: WindowHeightClass,
    val widthDp: Dp,
    val heightDp: Dp,
    val posture: DevicePosture = DevicePosture.NORMAL
) {
    val isCompact get() = widthClass == WindowSizeClass.COMPACT
    val isMedium get() = widthClass == WindowSizeClass.MEDIUM
    val isExpanded get() = widthClass == WindowSizeClass.EXPANDED
    val isLandscape get() = widthDp > heightDp
    val isPortrait get() = !isLandscape
    val showSidePanel get() = widthClass == WindowSizeClass.EXPANDED
    val showTwoPane get() = widthClass >= WindowSizeClass.MEDIUM
    val toolbarCollapsed get() = widthClass == WindowSizeClass.COMPACT
}

/**
 * Platform-specific screen dimensions provider.
 * Android uses LocalConfiguration; Desktop uses window size APIs.
 */
@Composable
@ReadOnlyComposable
expect fun rememberScreenSize(): ScreenSize

data class ScreenSize(
    val widthDp: Dp,
    val heightDp: Dp
)

@Composable
@ReadOnlyComposable
fun rememberLayoutConfiguration(): LayoutConfiguration {
    val screenSize = rememberScreenSize()
    val widthDp = screenSize.widthDp
    val heightDp = screenSize.heightDp

    val widthClass = when {
        widthDp < 600.dp -> WindowSizeClass.COMPACT
        widthDp < 840.dp -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }

    val heightClass = when {
        heightDp < 480.dp -> WindowHeightClass.COMPACT
        heightDp < 900.dp -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }

    return LayoutConfiguration(
        widthClass = widthClass,
        heightClass = heightClass,
        widthDp = widthDp,
        heightDp = heightDp
    )
}

// -- Convenience comparison -----------------------------------------------

infix fun WindowSizeClass.isAtLeast(other: WindowSizeClass): Boolean {
    return this.ordinal >= other.ordinal
}
