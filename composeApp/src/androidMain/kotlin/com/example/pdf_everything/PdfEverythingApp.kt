package com.example.pdf_everything

import android.app.Application
import com.example.pdf_everything.ui.design_system._androidContext

/**
 * Android Application class.
 *
 * Sets the global [androidContext] used by the dynamic-color theme resolver
 * (ThemeDynamic.kt) so Material You colors are available on Android 12+.
 */
class PdfEverythingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        _androidContext = this
    }
}
