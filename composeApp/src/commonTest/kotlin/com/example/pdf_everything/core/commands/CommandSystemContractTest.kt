package com.example.pdf_everything.core.commands

import com.example.pdf_everything.core.document.BoxSet
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.core.document.DocumentMetadata
import com.example.pdf_everything.core.document.Page
import com.example.pdf_everything.core.document.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandSystemContractTest {
    private fun document(): Document = Document(
        id = "doc-1",
        name = "test.pdf",
        pages = listOf(
            Page("p0", 0, BoxSet(RectF(0f, 0f, 600f, 800f))),
            Page("p1", 1, BoxSet(RectF(0f, 0f, 600f, 800f)))
        )
    )

    @Test
    fun dispatcher_executes_and_undoes() {
        val history = DocumentHistory()
        val dispatcher = CommandDispatcher(history)
        val result = dispatcher.dispatch(document(), com.example.pdf_everything.core.commands.SetPageLabelCommand(0, "Cover"))
        assertTrue(result.changed)
        assertEquals("Cover", result.document.pages[0].label)
        val undone = dispatcher.undo(result.document)
        assertTrue(undone.changed)
        assertEquals(null, undone.document.pages[0].label)
    }

    @Test
    fun redo_stack_is_cleared_after_new_command() {
        val dispatcher = CommandDispatcher(DocumentHistory())
        val first = dispatcher.dispatch(document(), SetPageLabelCommand(0, "A")).document
        val undone = dispatcher.undo(first).document
        assertTrue(dispatcher.canRedo)
        val changed = dispatcher.dispatch(undone, SetPageLabelCommand(1, "B"))
        assertTrue(changed.changed)
        assertFalse(dispatcher.canRedo)
    }

    @Test
    fun no_op_does_not_enter_history() {
        val dispatcher = CommandDispatcher(DocumentHistory())
        val source = document()
        val result = dispatcher.dispatch(source, UpdateMetadataCommand(DocumentMetadata()))
        assertFalse(result.changed)
        assertFalse(dispatcher.canUndo)
    }

    @Test
    fun transaction_validates_against_intermediate_state() {
        val source = document()
        val transaction = TransactionCommand(
            "Sequential test",
            listOf(
                SetPageLabelCommand(0, "A"),
                SetPageLabelCommand(0, "B")
            )
        )
        val dispatcher = CommandDispatcher(DocumentHistory())
        val result = dispatcher.dispatch(source, transaction)
        assertTrue(result.changed)
        assertEquals("B", result.document.pages[0].label)
        assertTrue(dispatcher.canUndo)
        assertEquals(null, dispatcher.undo(result.document).document.pages[0].label)
    }
}
