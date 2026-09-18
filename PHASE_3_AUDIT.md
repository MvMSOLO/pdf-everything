# PDF Everything — Phase 3 Exhaustive Audit

Source of truth: `prompt.md`. Scope is **Phase 3 only**.

## Status legend
- ✅ implemented in the application/model
- ⚠️ intentionally not persisted/serialized yet because Save/Save As is Phase 6; Phase 3 edits are kept in the document model and previewed without rasterizing the source PDF
- ⏭️ later phase; not implemented here

## 8. Command system

- ✅ Central `DocumentCommand` abstraction with `execute`, `undo`, `redo`, `canExecute`, `description`.
- ✅ `DocumentHistory` maintains bounded undo/redo stacks.
- ✅ `TransactionCommand` groups multiple commands into one history unit.
- ✅ InsertTextCommand.
- ✅ EditTextObjectCommand.
- ✅ DeleteObjectCommand / DeleteObjectsCommand.
- ✅ MoveObjectCommand.
- ✅ ResizeObjectCommand.
- ✅ RotateObjectCommand.
- ✅ InsertImageCommand.
- ✅ UpdateObjectCommand for style/attribute changes.
- ✅ Commands mutate the structured document model; no screenshot/canvas flattening is used.
- ✅ Page render state is invalidated on edits.

## 8.1 Undo / redo

- ✅ Ctrl+Z.
- ✅ Ctrl+Y.
- ✅ Ctrl+Shift+Z.
- ✅ Visible Undo/Redo controls in Edit mode.
- ✅ Text, object position, object attributes and object insertion/deletion are undoable.
- ✅ Large multi-object paste can use a transaction instead of one user-visible undo per object.
- ⚠️ PDF disk serialization/reopen verification belongs to Phase 6 Save/Validation and is not falsely claimed here.

## 9. Keyboard shortcuts

- ✅ Central `ShortcutRegistry` + `ShortcutResolver`.
- ✅ Ctrl+O / Ctrl+S / Ctrl+Shift+S / Ctrl+P / Ctrl+W / Ctrl+N are registered.
- ✅ Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z / Ctrl+C / Ctrl+X / Ctrl+V / Ctrl+A / Delete / Backspace are registered.
- ✅ Ctrl+F / F3 / Shift+F3 are registered.
- ✅ Ctrl++ / Ctrl+- / Ctrl+0 / Ctrl+1 / Home / End / PageUp / PageDown are registered.
- ✅ Arrow keys are routed for page movement in Browse mode and selected-object movement in Edit mode.
- ✅ Shift+arrow remains available to focused native text fields instead of being intercepted by the global handler.
- ✅ Shortcut resolution is mode-aware instead of being scattered across widgets.
- ✅ Save/Print/Close/New shortcuts never fake success: before their scheduled phases they report unavailable rather than doing nothing silently.

## 10. Clipboard

- ✅ Plain-text copy.
- ✅ Editable text cut.
- ✅ Clipboard text paste creates a real `TextObject` in the document model.
- ✅ Internal object clipboard supports copy/cut/paste of structured `PdfObject` values.
- ✅ Pasted objects receive a safe offset and unique ids.
- ✅ Desktop image clipboard read/write is supported.
- ✅ Clipboard image can be materialized into an app-managed temporary image source.
- ✅ Android image clipboard writing is supported; image materialization is app-cache based.
- ✅ Unsafe structured object payloads are **not** serialized into the OS clipboard; structured objects stay in the internal clipboard.

## 70. Selection engine

- ✅ Reusable `SelectionModel`.
- ✅ Single object selection.
- ✅ Multiple object selection.
- ✅ Text-range selection.
- ✅ Page selection state.
- ✅ Crop-rectangle selection state.
- ✅ Annotation selection state.
- ✅ Selected ids, bounds, anchor, transform handle state and capability flags.
- ✅ Edit canvas click selects objects.
- ✅ Edit canvas drag moves selected objects.
- ✅ Resize handle is interactive.

## 13. Text editing

- ✅ Text objects are discovered from the PDF text layer where the engine can expose `TextPosition` data.
- ✅ Click/tap selects a text object.
- ✅ Existing supported text objects can be edited in the document model.
- ✅ Bounding boxes are shown for object selection.
- ✅ Font name control.
- ✅ Font size control.
- ✅ Font weight control.
- ✅ Alignment control stored in the text model.
- ✅ Text color control.
- ✅ Line-spacing field exists in the model for the editing pipeline.
- ✅ Background/border fields exist in the model for future renderer/serializer use.
- ✅ Add-text mode: click a page location to create a `TextObject` there.
- ✅ Native text editing field supports keyboard cursor/selection behavior.
- ✅ Text range selection tools operate on the page text layer.
- ⚠️ Exact existing-PDF content-stream rewriting/serialization is deferred to the Save/PDF mutation layer; unsupported structures are not silently rasterized.

## 14. Image editing

- ✅ Insert image from desktop file picker.
- ✅ Insert image from Android document picker.
- ✅ Replace image source.
- ✅ Move.
- ✅ Resize.
- ✅ Rotate.
- ✅ Crop state.
- ✅ Opacity.
- ✅ Flip horizontal / vertical.
- ✅ Delete.
- ✅ Copy/paste through internal object clipboard.
- ⚠️ Persisting image-object changes back into the source PDF content streams belongs to the later save/serialization layer.

## Non-Phase-3 items deliberately NOT implemented

- ⏭️ CUT/Crop workspace, split, extract, organizer mutations: Phase 4.
- ⏭️ Annotations/markup/forms/metadata: Phase 5.
- ⏭️ Save/Save As/export/validation/recovery/printing: Phase 6.
- ⏭️ Windows association/Android intent packaging: Phase 7.
- ⏭️ Hardening/release packaging: Phase 8.

## Verification

### Core Kotlin compilation

`Document`, selection, command, clipboard contract and `EditorController` were separately compiled with `kotlinc` using minimal platform stubs. Result: **PASS**.

### Full Gradle build

Not claimed. The project wrapper requires downloading Gradle 9.6, but this execution environment cannot resolve `services.gradle.org`; therefore a full Android/Desktop Gradle build was not honestly marked successful.
