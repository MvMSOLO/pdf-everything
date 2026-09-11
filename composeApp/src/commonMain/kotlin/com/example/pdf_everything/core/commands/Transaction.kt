package com.example.pdf_everything.core.commands

/**
 * Groups multiple commands into a single undo-able transaction.
 *
 * Example: Moving 20 selected pages should be one undo step, not 20.
 */
data class Transaction(
    val transactionId: String,
    val description: String,
    val commands: List<DocumentCommand>
) : DocumentCommand {

    override val commandId: String = transactionId
    override val description: String = description

    override fun canExecute(): Boolean = commands.isNotEmpty() && commands.all { it.canExecute() }

    override fun execute(document: com.example.pdf_everything.core.document.Document): com.example.pdf_everything.core.document.Document {
        var current = document
        for (cmd in commands) {
            current = cmd.execute(current)
        }
        return current
    }

    override fun undo(document: com.example.pdf_everything.core.document.Document): com.example.pdf_everything.core.document.Document {
        var current = document
        for (cmd in commands.reversed()) {
            current = cmd.undo(current)
        }
        return current
    }

    override fun redo(document: com.example.pdf_everything.core.document.Document): com.example.pdf_everything.core.document.Document =
        execute(document)
}

/**
 * Builder for accumulating commands into a transaction.
 */
class TransactionBuilder(
    private val transactionId: String,
    private val description: String
) {
    private val commands = mutableListOf<DocumentCommand>()

    fun add(command: DocumentCommand): TransactionBuilder {
        commands.add(command)
        return this
    }

    fun build(): Transaction = Transaction(transactionId, description, commands.toList())
}