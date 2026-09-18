# PDF Everything — Phase 1–4 Deep Audit

Source of truth: `prompt.md` (especially §§3–12, §§16–17, §§31–32, §§37–40, §49, §54, §58–60, §69, §72–73, §99 and §115).

## Executive result

Phase 1/2: implemented at source level, with one environment-only verification limit: full Gradle build cannot run here because the wrapper distribution is not locally available and the environment cannot resolve `services.gradle.org`.

Phase 3: command/history, keyboard, clipboard, selection, text and image model editing are present at source level. PDF byte-level serialization is intentionally outside Phase 3 and belongs to Phase 6.

Phase 4: the previously empty `features/cut` area is now implemented end-to-end at the in-memory document-model/workspace boundary, including CUT modes and page organizer operations. Phase 4 mutations are command/history based and invalidate render state.

## Phase 1 — Foundation

Implemented: KMP/Compose bootstrap, platform open flow, desktop drag/drop, Android URI handling, recent files, document model, PDF engine adapter, isolated PDFBox implementations, light/dark UI and navigation.

Not claimed here because they are later phases: Windows file-association packaging, Android release packaging, save/export/print, autosave/recovery.

## Phase 2 — Viewer

Implemented: PDF rendering, page navigation, 25–600% zoom, fit modes, thumbnails, search with options/highlights, single/continuous/two-page/organizer/presentation modes, bounded render/thumbnail caches, background rendering/prefetch, stale-result guards, rotate view and dark reading mode.

The previous audit marked exact pinch-focus anchoring as partial. The current implementation already applies centroid-aware scroll compensation during pinch; no pixel-level device verification was possible in this environment, so it remains a manual QA item rather than a missing implementation.

## Phase 3 — Command/Edit

Implemented: `DocumentCommand`, bounded undo/redo history, transactions, mode-aware shortcuts, clipboard contracts, object/text selection, text editing model and image editing model. UI work is routed through the command/controller layer.

The project does not falsely claim Phase 6 disk serialization/reopen validation; those are intentionally later.

## Phase 4 — CUT / ORGANIZE

### CUT workspace

Implemented dedicated modes:

- Crop Page
- Content Trim
- Split
- Extract
- Delete Area

Crop supports numeric X/Y/W/H, A4/A5/Letter/Legal presets, original/portrait/landscape ratios, margin insets, reset, lock aspect ratio, and non-destructive preview handles for corners, edges and center move.

Content Trim computes a content bounding box from structured page objects, offers an adjustable margin, manual adjustment, preview and a background-object inclusion switch.

Delete Area removes intersecting structured editable objects from the model and explicitly labels the operation as not secure redaction.

Split supports every-N page plans plus custom break-page input and a button that derives breaks from the current page selection. Plans are previewed before any document mutation.

Extract builds a new in-memory document from selected pages while leaving the source document unchanged. Page order is preserved according to the selected index list in the core helper, so a later save pipeline can support ordered extraction.

Section cutting supports a grid-based page-to-panels preview using the same crop-box model, preserving row-major reading order.

### Page Organizer

Implemented:

- thumbnail view;
- multi-select;
- desktop drag and mobile long-press drag;
- delete;
- duplicate with regenerated IDs;
- rotate left/right;
- insert blank page;
- merge another PDF into the model;
- replace one page from another PDF;
- move selected pages first/last;
- odd/even selection;
- reverse selected page positions;
- page labels.

### History / invalidation

All Phase 4 mutations use `SnapshotDocumentCommand`-style commands. This means the same history stack used by Phase 3 can undo/redo page operations as a single user action. Page mutations update render-state versions and set dirty state through `DocumentHistory`.

## What is intentionally deferred

Phase 4 does not implement final PDF byte-stream serialization, Save/Save As/Export, transactional safe-save, post-save reopen validation, or real Ctrl+P output. Those requirements are assigned to Phase 6 by the master prompt.

This is deliberate: the current Phase 4 code mutates the structured model honestly instead of pretending that a UI crop or white rectangle has already rewritten the PDF file.

## Verification performed

Core Phase 4 smoke suite compiled with `kotlinc` and passed:

`PHASE4_SMOKE_PASS`

Covered: crop + undo, reorder/reindex, extraction, split-by-N, custom split points, section cutting.

Full Gradle verification was attempted separately and is blocked by the environment's inability to download the requested Gradle 9.6 distribution. No pre-existing APK/JAR was treated as proof of the modified source compiling.
