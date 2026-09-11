package com.example.pdf_everything.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
@ReadOnlyComposable
actual fun rememberScreenSize(): ScreenSize {
    val configuration = LocalConfiguration.current
    return ScreenSize(
        widthDp = configuration.screenWidthDp.dp,
        heightDp = configuration.screenHeightDp.dp
    )
}
