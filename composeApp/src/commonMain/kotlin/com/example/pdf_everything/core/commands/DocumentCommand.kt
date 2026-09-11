package com.example.pdf_everything.core.commands

abstract class DocumentCommand {
    abstract fun execute()
    abstract fun undo()
    abstract fun redo()
    abstract fun canExecute(): Boolean
}
