package com.example.pdf_everything.ui.shared

import androidx.compose.runtime.Composable
import com.example.pdf_everything.isDesktop

@Composable
fun ResponsiveLayoutAdapter(
    mobileLayout: @Composable () -> Unit,
    desktopLayout: @Composable () -> Unit
) {
    // Basic implementation for now, can be refined with WindowSizeClass
    // For now we use the platform detection
    
    if (isDesktop) {
        desktopLayout()
    } else {
        mobileLayout()
    }
}
