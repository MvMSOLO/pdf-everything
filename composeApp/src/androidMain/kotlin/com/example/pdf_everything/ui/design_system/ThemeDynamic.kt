package com.example.pdf_everything.ui.design_system

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual: resolves Material You dynamic colors on Android 12+.
 * Falls back to null on older versions (static scheme will be used).
 */
actual fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    return try {
        // We need a Context; this is a top-level function so we use a
        // stored reference set during Application init.
        val ctx = _androidContext ?: return null
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Set by the Android Application / Activity at startup.
 */
internal var _androidContext: Context? = null