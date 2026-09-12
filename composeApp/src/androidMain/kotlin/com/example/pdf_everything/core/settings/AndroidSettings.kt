package com.example.pdf_everything.core.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of [MultiplatformSettings] backed by
 * SharedPreferences (lightweight, synchronous, file-based).
 *
 * For a production build you would swap this for DataStore (async),
 * but SharedPreferences is simpler and works without extra deps.
 */
class AndroidSettings(
    context: Context
) : MultiplatformSettings {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pdf_everything_settings", Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int =
        prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}