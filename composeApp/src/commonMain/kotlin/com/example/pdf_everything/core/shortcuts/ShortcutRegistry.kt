package com.example.pdf_everything.core.shortcuts

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Centralized keyboard shortcut registry per spec §9.
 *
 * Supports mode-aware shortcut dispatch: the same key combo
 * may resolve to different actions depending on the current
 * mode (View, Edit, FormFill).
 */

enum class ShortcutAction {
    OPEN,
    SAVE,
    SAVE_AS,
    PRINT,
    UNDO,
    REDO,
    CLOSE_TAB,
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
    FIND,
    NEXT_PAGE,
    PREV_PAGE,
    ROTATE_CW,
    ROTATE_CCW,
    DELETE_PAGE,
    FULLSCREEN,
    SWITCH_TAB_NEXT,
    SWITCH_TAB_PREV,
    NEW_TAB,
    ESCAPE
}

enum class ShortcutMode {
    GLOBAL,
    VIEW,
    EDIT,
    FORM_FILL
}

/** Modifier key type for desktop shortcut dispatch. */
enum class KeyModifier { Ctrl, Alt, Meta, Shift }

data class ShortcutBinding(
    val action: ShortcutAction,
    val mode: ShortcutMode,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val key: String  // key name per Compose KeyEvent.key
) {
    /** Human-readable string like "Ctrl+S" */
    val display: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
            append(key)
        }
}

class ShortcutRegistry {

    private val bindings = mutableListOf<ShortcutBinding>()
    private var _currentMode by mutableStateOf(ShortcutMode.GLOBAL)
    val currentMode: ShortcutMode get() = _currentMode

    fun setMode(mode: ShortcutMode) { _currentMode = mode }

    /** Register a shortcut binding. Later registrations override earlier ones for the same action+mode. */
    fun register(binding: ShortcutBinding) {
        val existing = bindings.indexOfFirst { it.action == binding.action && it.mode == binding.mode }
        if (existing >= 0) bindings[existing] = binding else bindings.add(binding)
    }

    /** Resolve a key event to an action, considering the current mode. */
    fun resolve(
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean,
        key: String
    ): ShortcutAction? {
        return resolveInternal(ctrl, alt, shift, key)
    }

    /** Resolve a Compose [Key] + modifier list (desktop shortcut handler). */
    fun resolve(
        key: androidx.compose.ui.input.key.Key,
        modifiers: List<KeyModifier>
    ): ShortcutBinding? {
        val ctrl = modifiers.contains(KeyModifier.Ctrl)
        val alt = modifiers.contains(KeyModifier.Alt)
        val shift = modifiers.contains(KeyModifier.Shift)
        val keyName = keyToName(key)
        val modeOrder = listOf(_currentMode, ShortcutMode.GLOBAL)
        for (mode in modeOrder) {
            val match = bindings.find {
                it.mode == mode &&
                it.ctrl == ctrl &&
                it.alt == alt &&
                it.shift == shift &&
                it.key.equals(keyName, ignoreCase = true)
            }
            if (match != null) return match
        }
        return null
    }

    private fun resolveInternal(
        // Try current mode first, then GLOBAL
        val modeOrder = listOf(_currentMode, ShortcutMode.GLOBAL)
        for (mode in modeOrder) {
            val match = bindings.find {
                it.mode == mode &&
                it.ctrl == ctrl &&
                it.alt == alt &&
                it.shift == shift &&
                it.key.equals(key, ignoreCase = true)
            }
            if (match != null) return match.action
        }
        return null
    }

    /** Get all bindings for display in a shortcuts settings panel. */
    fun getAllBindings(): List<ShortcutBinding> = bindings.toList()

    /** Get bindings for a specific action. */
    fun getBindingsFor(action: ShortcutAction): List<ShortcutBinding> =
        bindings.filter { it.action == action }

    companion object {
        /** Map a Compose [Key] to its shortcut name string. */
        fun keyToName(key: androidx.compose.ui.input.key.Key): String =
            when (key) {
                androidx.compose.ui.input.key.Key.Enter -> "Enter"
                androidx.compose.ui.input.key.Key.Escape -> "Escape"
                androidx.compose.ui.input.key.Key.Tab -> "Tab"
                androidx.compose.ui.input.key.Key.Delete -> "Delete"
                androidx.compose.ui.input.key.Key.PageUp -> "PageUp"
                androidx.compose.ui.input.key.Key.PageDown -> "PageDown"
                androidx.compose.ui.input.key.Key.F1 -> "F1"
                else -> key.name.removePrefix("Key_")
            }

        /** Create a registry pre-loaded with standard shortcuts per spec §9. */
        fun createDefault(): ShortcutRegistry = ShortcutRegistry().apply {
            // ── Global shortcuts ─────────────────────────
            register(ShortcutBinding(ShortcutAction.OPEN, ShortcutMode.GLOBAL, ctrl = true, key = "O"))
            register(ShortcutBinding(ShortcutAction.SAVE, ShortcutMode.GLOBAL, ctrl = true, key = "S"))
            register(ShortcutBinding(ShortcutAction.SAVE_AS, ShortcutMode.GLOBAL, ctrl = true, shift = true, key = "S"))
            register(ShortcutBinding(ShortcutAction.PRINT, ShortcutMode.GLOBAL, ctrl = true, key = "P"))
            register(ShortcutBinding(ShortcutAction.UNDO, ShortcutMode.GLOBAL, ctrl = true, key = "Z"))
            register(ShortcutBinding(ShortcutAction.REDO, ShortcutMode.GLOBAL, ctrl = true, shift = true, key = "Z"))
            register(ShortcutBinding(ShortcutAction.CLOSE_TAB, ShortcutMode.GLOBAL, ctrl = true, key = "W"))
            register(ShortcutBinding(ShortcutAction.FULLSCREEN, ShortcutMode.GLOBAL, key = "F11"))
            register(ShortcutBinding(ShortcutAction.NEW_TAB, ShortcutMode.GLOBAL, ctrl = true, key = "T"))
            register(ShortcutBinding(ShortcutAction.SWITCH_TAB_NEXT, ShortcutMode.GLOBAL, ctrl = true, key = "Tab"))
            register(ShortcutBinding(ShortcutAction.SWITCH_TAB_PREV, ShortcutMode.GLOBAL, ctrl = true, shift = true, key = "Tab"))
            register(ShortcutBinding(ShortcutAction.ESCAPE, ShortcutMode.GLOBAL, key = "Escape"))

            // ── View mode shortcuts ───────────────────────
            register(ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutMode.VIEW, ctrl = true, key = "+"))
            register(ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutMode.VIEW, ctrl = true, key = "="))
            register(ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutMode.VIEW, ctrl = true, key = "-"))
            register(ShortcutBinding(ShortcutAction.ZOOM_RESET, ShortcutMode.VIEW, ctrl = true, key = "0"))
            register(ShortcutBinding(ShortcutAction.FIND, ShortcutMode.VIEW, ctrl = true, key = "F"))
            register(ShortcutBinding(ShortcutAction.NEXT_PAGE, ShortcutMode.VIEW, key = "PageDown"))
            register(ShortcutBinding(ShortcutAction.PREV_PAGE, ShortcutMode.VIEW, key = "PageUp"))
            register(ShortcutBinding(ShortcutAction.ROTATE_CW, ShortcutMode.VIEW, ctrl = true, shift = true, key = "R"))
            register(ShortcutBinding(ShortcutAction.ROTATE_CCW, ShortcutMode.VIEW, ctrl = true, alt = true, shift = true, key = "R"))
            register(ShortcutBinding(ShortcutAction.DELETE_PAGE, ShortcutMode.VIEW, ctrl = true, key = "Delete"))

            // ── Edit mode shortcuts ───────────────────────
            register(ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutMode.EDIT, ctrl = true, key = "+"))
            register(ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutMode.EDIT, ctrl = true, key = "-"))
            register(ShortcutBinding(ShortcutAction.FIND, ShortcutMode.EDIT, ctrl = true, key = "F"))

            // ── FormFill mode shortcuts ───────────────────
            register(ShortcutBinding(ShortcutAction.ZOOM_IN, ShortcutMode.FORM_FILL, ctrl = true, key = "+"))
            register(ShortcutBinding(ShortcutAction.ZOOM_OUT, ShortcutMode.FORM_FILL, ctrl = true, key = "-"))
        }
    }
}
