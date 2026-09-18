package com.example.pdf_everything.core.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

enum class EditorMode { Browse, TextEdit, ObjectEdit, Crop, Annotation, PageOrganizer }
enum class ShortcutAction {
    Open, Save, SaveAs, Print, Close, New,
    Undo, Redo, Copy, Cut, Paste, SelectAll, Delete,
    Search, NextSearch, PreviousSearch,
    ZoomIn, ZoomOut, FitPage, ActualSize,
    FirstPage, LastPage, PreviousPage, NextPage,
    MoveSelectionLeft, MoveSelectionRight, MoveSelectionUp, MoveSelectionDown
}

data class ShortcutBinding(val key: Key, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false, val action: ShortcutAction)

data class ShortcutContext(val mode: EditorMode, val hasDocument: Boolean, val canUndo: Boolean, val canRedo: Boolean, val hasSelection: Boolean)

class ShortcutRegistry {
    private val bindings = listOf(
        ShortcutBinding(Key.O, ctrl = true, action = ShortcutAction.Open),
        ShortcutBinding(Key.S, ctrl = true, action = ShortcutAction.Save),
        ShortcutBinding(Key.S, ctrl = true, shift = true, action = ShortcutAction.SaveAs),
        ShortcutBinding(Key.P, ctrl = true, action = ShortcutAction.Print),
        ShortcutBinding(Key.W, ctrl = true, action = ShortcutAction.Close),
        ShortcutBinding(Key.N, ctrl = true, action = ShortcutAction.New),
        ShortcutBinding(Key.Z, ctrl = true, action = ShortcutAction.Undo),
        ShortcutBinding(Key.Y, ctrl = true, action = ShortcutAction.Redo),
        ShortcutBinding(Key.Z, ctrl = true, shift = true, action = ShortcutAction.Redo),
        ShortcutBinding(Key.C, ctrl = true, action = ShortcutAction.Copy),
        ShortcutBinding(Key.X, ctrl = true, action = ShortcutAction.Cut),
        ShortcutBinding(Key.V, ctrl = true, action = ShortcutAction.Paste),
        ShortcutBinding(Key.A, ctrl = true, action = ShortcutAction.SelectAll),
        ShortcutBinding(Key.Delete, action = ShortcutAction.Delete),
        ShortcutBinding(Key.Backspace, action = ShortcutAction.Delete),
        ShortcutBinding(Key.F, ctrl = true, action = ShortcutAction.Search),
        ShortcutBinding(Key.F3, action = ShortcutAction.NextSearch),
        ShortcutBinding(Key.F3, shift = true, action = ShortcutAction.PreviousSearch),
        ShortcutBinding(Key.Plus, ctrl = true, action = ShortcutAction.ZoomIn),
        ShortcutBinding(Key.Minus, ctrl = true, action = ShortcutAction.ZoomOut),
        ShortcutBinding(Key.Zero, ctrl = true, action = ShortcutAction.FitPage),
        ShortcutBinding(Key.One, ctrl = true, action = ShortcutAction.ActualSize),
        ShortcutBinding(Key.Home, action = ShortcutAction.FirstPage),
        ShortcutBinding(Key.End, action = ShortcutAction.LastPage),
        ShortcutBinding(Key.PageUp, action = ShortcutAction.PreviousPage),
        ShortcutBinding(Key.PageDown, action = ShortcutAction.NextPage),
        ShortcutBinding(Key.DirectionLeft, action = ShortcutAction.MoveSelectionLeft),
        ShortcutBinding(Key.DirectionRight, action = ShortcutAction.MoveSelectionRight),
        ShortcutBinding(Key.DirectionUp, action = ShortcutAction.MoveSelectionUp),
        ShortcutBinding(Key.DirectionDown, action = ShortcutAction.MoveSelectionDown)
    )

    fun resolve(event: KeyEvent, context: ShortcutContext): ShortcutAction? {
        val match = bindings.firstOrNull { binding ->
            binding.key == event.key && binding.ctrl == event.isCtrlPressed && binding.shift == event.isShiftPressed && binding.alt == event.isAltPressed && isAllowed(binding.action, context)
        }
        return match?.action
    }

    private fun isAllowed(action: ShortcutAction, context: ShortcutContext): Boolean = when (action) {
        ShortcutAction.Open, ShortcutAction.New, ShortcutAction.Search -> true
        ShortcutAction.Undo -> context.canUndo
        ShortcutAction.Redo -> context.canRedo
        ShortcutAction.Copy, ShortcutAction.Cut, ShortcutAction.Delete, ShortcutAction.SelectAll,
        ShortcutAction.Paste, ShortcutAction.MoveSelectionLeft, ShortcutAction.MoveSelectionRight,
        ShortcutAction.MoveSelectionUp, ShortcutAction.MoveSelectionDown -> context.hasDocument
        ShortcutAction.Save, ShortcutAction.SaveAs, ShortcutAction.Print, ShortcutAction.Close,
        ShortcutAction.ZoomIn, ShortcutAction.ZoomOut, ShortcutAction.FitPage, ShortcutAction.ActualSize,
        ShortcutAction.FirstPage, ShortcutAction.LastPage, ShortcutAction.PreviousPage, ShortcutAction.NextPage -> context.hasDocument
        ShortcutAction.NextSearch, ShortcutAction.PreviousSearch -> true
    }
}
