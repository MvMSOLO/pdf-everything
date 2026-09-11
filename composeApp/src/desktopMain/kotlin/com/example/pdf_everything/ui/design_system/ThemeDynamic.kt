package com.example.pdf_everything.ui.design_system

import androidx.compose.material3.ColorScheme

/**
 * Desktop actual: dynamic color is not supported, always returns null
 * (static light/dark scheme will be used instead).
 */
actual fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null