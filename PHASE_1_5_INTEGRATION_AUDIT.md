# Phase 1–5 Integrated Hardening Pass

This pass re-audits the existing merged implementation and hardens the cross-phase contracts.

## Applied

- Central document invariant validation after every history mutation.
- Atomic undo/redo stack semantics when validation fails.
- No-op command collapse so redundant history entries are not created.
- Stable page-local annotation/widget ownership during reorder/delete.
- Form field page remapping after structural page operations.
- Central PDF/UI/viewport coordinate conversion module.
- App open hydration uses all five PDF page boxes instead of media-box-only fallback.
- Native desktop text/image/shape writes route through the centralized coordinate converter.
- Added coordinate round-trip, rotation and scroll/zoom tests.

## Explicit native-editing boundary

Existing native content-stream text discovered through PDFBox is kept read-only when a stable structural identity cannot be proven. The application does not replace it with overpainted/fake text. Adapter-owned generated objects remain structurally tracked for future exact rewriter support.

## Verification performed in this environment

- Common core compilation harness passed for document model, coordinate system, history/commands, Phase 4 commands, Phase 5 commands and search index.
- Integration smoke covered reorder, duplicate, merge, crop, outline remapping, duplicate-ID prevention and coordinate round trips.
- `PHASE1_5_FULL_CORE_PASS` and `PHASE1_5_INTEGRATION_SMOKE_PASS` were observed.
- Full Gradle platform compilation could not be executed because the configured Gradle 9.6 distribution is not locally installed and the sandbox cannot reach `services.gradle.org`.
