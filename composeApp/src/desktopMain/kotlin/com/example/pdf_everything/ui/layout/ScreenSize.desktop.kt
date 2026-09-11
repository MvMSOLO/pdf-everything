package com.example.pdf_everything.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment

@Composable
@ReadOnlyComposable
actual fun rememberScreenSize(): ScreenSize {
    // On desktop, use the primary screen resolution as a reasonable default.
    // Compose for Desktop doesn't have LocalConfiguration, so we read from AWT.
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screen = ge.defaultScreenDevice
    val mode = screen.displayMode
    // Approximate dp from pixels (assuming 1x density on desktop)
    return ScreenSize(
        widthDp = Dp(mode.width.toFloat()),
        heightDp = Dp(mode.height.toFloat())
    )
}
