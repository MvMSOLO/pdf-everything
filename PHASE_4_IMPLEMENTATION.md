# PDF Everything — Phase 4 Implementation

## Scope
Phase 4 from `prompt.md`: the signature **CUT** workspace and **PAGE ORGANIZER**.

## Implemented

### CUT workspace
- Dedicated responsive CUT / ORGANIZE workspace in Compose Multiplatform.
- Crop Page with center-move, edge and corner handles.
- Numeric X/Y/Width/Height controls.
- Aspect lock and presets: A4, A5, Letter, Legal, original, portrait, landscape.
- Margin insets and reset.
- Non-destructive crop preview until Apply.
- Content Trim with automatic structured-content bounds, configurable margin, background-object inclusion and manual adjustment.
- Delete Area for structured editable-object removal, with explicit non-secure-redaction warning.
- Split planning by every N pages.
- Split planning by custom break pages.
- Split-break generation from selected organizer pages.
- Extract selected pages to a new in-memory document without mutating the source.
- Page-to-sections preview using grid crops with row-major reading order.

### PAGE ORGANIZER
- Multi-select page state.
- Reorder pages.
- Desktop direct-drag and mobile long-press drag.
- Delete pages.
- Duplicate pages with regenerated page/object ids.
- Rotate left/right.
- Insert blank page.
- Merge another PDF into the document model.
- Replace a selected page from another PDF.
- Move selection to first/last.
- Select odd/even pages.
- Reverse selected page positions.
- User-facing page labels stored independently from physical page indices.

### Command/history integration
All Phase 4 document mutations are `DocumentCommand` actions. The shared history stack therefore supports Ctrl+Z/Ctrl+Y for page operations as well as Phase 3 edits. Render-state versions are invalidated after mutations.

## Architectural boundary
Phase 4 owns the structured in-memory document transformation and preview UX. Phase 6 owns transactional PDF serialization, Save / Save As / Export, print output, safe-save, validation and reopen verification. The implementation intentionally does not claim disk persistence that does not exist yet.

## Verification
Pure Kotlin smoke tests passed for crop + undo, reorder/reindex, extraction, every-N splitting, custom split points and section cutting.

Result: `PHASE4_SMOKE_PASS`.

A full Gradle build could not be proven in this environment because the Gradle 9.6 distribution is not locally available and `services.gradle.org` cannot be resolved. Existing build artifacts were not used as compile evidence.
