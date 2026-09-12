package com.example.pdf_everything.core.settings

import java.util.prefs.Preferences

/**
 * Desktop implementation of [MultiplatformSettings] backed by
 * java.util.prefs.Preferences (registry on Windows, .prefs file on Linux).
 */
class DesktopSettings : MultiplatformSettings {

    private val prefs: Preferences =
        Preferences.userRoot().node("com/example/pdf_everything")

    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
        prefs.flush()
    }

    override fun getInt(key: String, default: Int): Int =
        prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
        prefs.flush()
    }

    override fun getString(key: String, default: String): String =
        prefs.get(key, default)

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
        prefs.flush()
    }
}