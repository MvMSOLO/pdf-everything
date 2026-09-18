# Phase 8 — Command System / History Hardening

## Implemented

- All document mutations flow through `DocumentCommand` / `CommandDispatcher`.
- `execute`, `undo`, `redo`, `canExecute`, and `description` are part of the common command contract.
- `EditorController` now delegates mutations and history navigation to the centralized dispatcher.
- `TransactionCommand` validates commands sequentially against the document state produced by earlier commands, rather than validating every child against the original state.
- Transaction execution remains atomic at the model boundary because `Document` is immutable: a failed command cannot partially mutate the original document.
- History only accepts a command after model validation succeeds.
- Failed undo/redo returns the command to the correct history stack.
- No-op commands do not create user-visible history entries.
- Composite operations remain one history entry for merge/split/multi-page and multi-object actions.
- Large-operation history uses immutable structural sharing through the Kotlin data model instead of deep-copying the whole byte-level PDF into RAM.

## Architecture

`KeyboardEvent / UI action -> ShortcutResolver or UI interaction -> CommandDispatcher -> DocumentCommand -> DocumentModelService validation -> render invalidation`

Platform-native persistence remains below the document model. A command changes the structured model; Save/Save As later applies that model to the native PDF engine and verifies the resulting artifact.

## Remaining product gate

Phase 8 itself is implemented, but the whole product is not declared release-ready until the platform build and golden-path integration tests are executed on connected Windows and Android environments. The current sandbox cannot download the Gradle distribution.
