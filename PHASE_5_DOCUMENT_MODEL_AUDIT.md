# Phase 5 — Document Model (5.1–5.3) Deep Audit

Status: implemented as a structured, serializable internal model and hydrated from the PDF engine boundary.

## 5.1 Document
- Stable document ID, source path/URI projections, title, metadata, page count, permissions and security state.
- Typed embedded attachments instead of string-only placeholders.
- Hierarchical outline and typed form model.
- Persistent dirty state (`CLEAN`, `DIRTY`, `SAVING`, `SAVE_FAILED`).
- Monotonic document version plus audit/revision metadata, command counters and save/source fingerprints.
- Structural validation for IDs, page indices, outline destinations and form references.

## 5.2 Page
- Stable page ID with canonical physical index.
- Media/Crop/Bleed/Trim/Art boxes.
- Rotation/background.
- Typed editable objects, annotations and form widgets.
- Generation-aware render state to invalidate stale cached output after mutation.
- Source page/document provenance for reorder, duplicate, replace and merge operations.

## 5.3 Objects
- Text, Image, Vector, Path, Shape, Annotation and FormWidget object representations.
- Common preservation attributes: bounding box, affine transform, z-order, opacity, rotation, resource references, original/native identity, editability and selection/focus state.
- Explicit `UnknownObject` for native content that cannot be safely mapped. It carries native/preservation identifiers so the source PDF remains authoritative rather than converting unknown content into fake editable pixels.
- Model normalization and validation are centralized in `DocumentModelService`.

## Engine hydration
The application open pipeline now hydrates page boxes, security/permission state, forms, annotations, bookmarks and attachment inventory from engine inspection when the platform exposes them. Native page boxes are not replaced by UI defaults.


### Native hydration details

Desktop and Android engine adapters read document-level embedded-file name trees into typed attachment records. Desktop follows the PDFBox 3.x `PDDocumentNameDictionary`/embedded-files tree contract; Android mirrors the same model through PdfBox-Android. The page inspection DTO carries all five page boxes so the internal `Page` model is not forced to invent crop/bleed/trim/art geometry.
