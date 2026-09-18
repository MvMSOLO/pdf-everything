# PDF Everything — Phase 1–5 Deep Implementation Audit

Source of truth: `prompt.md`, especially §115 plus the detailed product requirements earlier in the document.

## Audit method

Phase 1–4 were re-read at source level rather than trusting the previous audit labels. The current code was inspected across the document model, PDF engine API/platform adapters, command/history layer, viewer/editor UI, CUT workspace, search/cache path and application routing. Phase 5 requirements were then implemented against the same architecture.

This audit distinguishes between:
- implemented source behavior;
- native PDF reading behavior;
- in-memory editable document state;
- later persistence work explicitly owned by Phase 6.

## Phase 1 — Foundation

Implemented and rechecked:
- KMP/Compose project structure;
- common platform abstraction;
- desktop and Android PDF engine factories;
- desktop drag/drop and launch source flow;
- Android ContentUri materialization;
- recent-file model;
- core `Document`, `Page`, `DocumentSource`, permissions and security state;
- PDFBox-backed native engine boundaries;
- initial metadata/security inspection;
- light/dark application shell.

Improvement made during this audit: Phase 5 native data is now read through the engine abstraction instead of being invented in the UI. Existing PDF annotations, AcroForm fields and outline/bookmark destinations are mapped into typed application models when a document opens.

## Phase 2 — Viewer

Implemented and rechecked:
- page rendering through the platform PDFBox engine;
- single, continuous, two-page, organizer and presentation modes;
- zoom and fit modes;
- thumbnail rendering;
- search and highlight rectangles;
- bounded page/thumbnail cache;
- async rendering and stale-result checks;
- pinch centroid compensation;
- rotation state;
- responsive viewer layout.

Improvement made: the continuous viewer used the lazy-column API consistently instead of feeding grid-only item builders into a column. Viewer/search data paths remain separated from edit-state commands.

## Phase 3 — Command/Edit system

Implemented and rechecked:
- `DocumentCommand`;
- `DocumentHistory` with bounded undo/redo;
- `TransactionCommand`;
- text selection and text insertion/editing model;
- image insertion/update model;
- object move/resize/rotate/delete;
- clipboard contracts and internal object clipboard;
- mode-aware keyboard shortcut registry;
- render invalidation after model edits.

Important boundary: Phase 3 commands operate on the structured model; disk serialization belongs to Phase 6 and is not falsely represented as complete here.

## Phase 4 — CUT / ORGANIZE

Implemented and rechecked:
- dedicated CUT workspace;
- crop page with numeric controls, presets, aspect locking and handles;
- content trim from structured content bounds;
- delete-area structured object removal;
- split-by-N and custom break planning;
- selection-derived split breaks;
- ordered extraction;
- grid section cutting;
- multi-select organizer;
- page reorder/delete/duplicate/rotate;
- blank-page insertion;
- merge/replace model operations;
- first/last, odd/even and reverse selection actions;
- page labels.

Improvement made: page structural mutations now maintain annotation page ownership and remap bookmark destination indices after reorder/delete. Merged pages keep source-document provenance. This prevents the common silent bug where a bookmark or annotation stays attached to a stale physical index after page organization.

## Phase 5 — Annotation / Forms / Metadata

Implemented end-to-end at the document-model + native inspection + UI level:

### Annotation tools
Typed `AnnotationType`/`PdfAnnotation`/`AnnotationStyle` models cover:
- highlight;
- underline;
- strikeout;
- squiggly;
- note;
- free text;
- ink;
- rectangle/circle/line/stamp/redaction classifications.

The Phase 5 workspace provides selectable annotation tools, content/comments, author/subject fields, coordinates, creation timestamps and delete/undo behavior. Annotation state is visible in a live rendered page preview with bounds.

### Comments
Comments are represented explicitly through `AnnotationComment`, including author, subject, content, timestamps and reply storage. Note annotations use this same structure instead of a separate fake comment system.

### Forms
`FormField`, `FormOption`, `FormFieldType` and `FormModel` model supported AcroForm fields. The native PDFBox adapters discover field type, name, tooltip, widget bounds, value, required/read-only flags and choice options. The workspace provides real text/multiline/choice-style editing controls plus reset and undo through document commands.

### Metadata
`UpdateMetadataCommand` makes title, author, subject, keywords, creator and producer editable through the Phase 5 workspace and undoable through the shared history stack.

### Bookmarks
`OutlineItem`, `BookmarkDestination` and `BookmarkStyle` model hierarchical outlines. Native PDFBox inspection imports existing outline items and resolves their destination pages. The workspace can add root bookmarks, navigate to their page and delete them; all operations are command/history based.

## Verification

A standalone Kotlin core compilation was run against the updated document model and Phase 1–5 command stack:

`PHASE1_5_CORE_PASS`

The Phase 4 + Phase 5 core command source compiled successfully with `kotlinc`.

A full Gradle desktop/Android build was attempted, but this environment cannot download the configured Gradle 9.6 distribution because `services.gradle.org` is unreachable. Therefore no claim is made that a full platform build was executed here.

## Known later-phase boundary

The master prompt assigns Save/Save As/Export/Print/autosave/recovery to Phase 6. Phase 5 therefore does not pretend to have serialized every model mutation to disk. Native PDFBox reading of existing annotations/forms/bookmarks is real; Phase 5 editing is maintained as structured command state ready for the Phase 6 serialization pipeline.
