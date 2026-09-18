# Phase 5 — Annotation / Forms / Metadata / Bookmarks

Phase 5 is implemented as a real structured document feature set, not a visual mock.

## Annotation tools

`PdfAnnotation`, `AnnotationType`, `AnnotationStyle`, `AnnotationComment` and `OffsetPoint` provide typed state for markup and comment annotations. Supported tool classifications include Highlight, Underline, StrikeOut, Squiggly, Note, FreeText, Ink, Rectangle, Circle, Line, Stamp and Redaction.

The Phase 5 workspace creates annotations through `EditorController` and `DocumentCommand`, so creation/deletion participates in the shared undo/redo stack. Coordinates and page ownership are explicit. The page preview renders annotation bounds so model state is immediately visible.

## Comments

Comments are attached to annotations as `AnnotationComment` values containing author, subject, contents, creation/modification timestamps and nested reply storage. Note annotations use the same comment contract.

## Forms

The platform PDF engines inspect supported AcroForm fields using PDFBox. Field type, fully qualified name, alternate name, widget bounds, current value, required/read-only flags and choice options are imported into `FormModel`/`FormField`.

Form edits use `FillFormFieldCommand` and `ResetFormFieldCommand`, making session edits undoable and preventing read-only fields from being mutated.

## Metadata

Title, author, subject, keywords, creator and producer are editable in the Phase 5 workspace through `UpdateMetadataCommand`.

## Bookmarks

`OutlineItem`, `BookmarkDestination` and `BookmarkStyle` represent hierarchical PDF outlines. Existing native bookmarks are read by both platform engines and destination pages are resolved. The workspace supports root bookmark creation, navigation and deletion; add/update/delete/reorder commands are available in the core layer.

## Phase 4 compatibility improvements

Page reorder/delete now remaps or removes bookmark destinations. Page annotation page indices are normalized after structural mutations. Merged pages retain their source document ID.

## Persistence boundary

The master prompt assigns Save/Save As/Export/Print/autosave/recovery to Phase 6. Therefore Phase 5 keeps edits in the structured document command state while importing real native annotation/form/bookmark data from PDFBox. No fake success is reported for disk persistence.

## Phase 5.1–5.3 deep model completion

The document model now exposes canonical `documentId`, `sourcePath`, and `sourceUri` projections, persistent dirty-state semantics, audit revision metadata, save fingerprints, typed embedded attachments, richer security/permission flags, and document-level validation.

Pages preserve all five PDF page boxes (`media`, `crop`, `bleed`, `trim`, `art`), rotation, background, stable IDs, source provenance, independent page labels, typed annotations/widgets, and a generation-aware render state. Structural normalization repairs physical indices and page ownership after reorder/merge operations without changing stable IDs.

`PdfObject` is a sealed, serializable hierarchy with concrete Text/Image/Vector/Path/Shape/Annotation/FormWidget/Unknown variants. Every object carries geometry, affine transform, z-order, opacity, rotation, single and plural resource references, original/native identity, explicit editability, and selection/focus state. Unknown/native objects carry preservation keys rather than being silently converted into editable fakes.

The platform open pipeline now hydrates the model from engine inspection, including page boxes, permissions/security information, native outline/forms/annotations, and embedded-file attachment inventory where the platform engine exposes it.
