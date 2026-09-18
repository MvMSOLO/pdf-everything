package com.example.pdf_everything.ui.shared

import androidx.compose.runtime.Composable
import com.example.pdf_everything.isDesktop

@Composable
fun ResponsiveLayoutAdapter(
    mobileLayout: @Composable () -> Unit,
    desktopLayout: @Composable () -> Unit
) {
    if (isDesktop) {
        desktopLayout()
    } else {
        mobileLayout()
    }
}
