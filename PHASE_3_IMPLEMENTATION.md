# Phase 3 implementation notes

Phase 3 is layered on top of the existing Phase 1–2 project.

## New common layers

- `core/selection/Selection.kt` — shared selection state for object/text/page/crop/annotation selections.
- `core/clipboard/Clipboard.kt` — internal object clipboard + platform clipboard contract.
- `core/shortcuts/Shortcuts.kt` — centralized shortcut registry and mode-aware resolver.
- `core/editor/EditorController.kt` — command dispatch, document mutation, clipboard operations and object editing.
- `core/editor/Phase3Composables.kt` — Edit workspace, object inspector, text-range tools and interactive page editor.
- `core/editor/Phase3RenderedImage.kt` — editor page rendering.

## Platform additions

- Desktop image picker and OS clipboard support.
- Android image picker and clipboard support.

## Model additions

`TextObject` now includes font weight, alignment, line spacing, background and border metadata.
`ImageObject` now includes crop and flip state.

## Important boundary

Phase 3 edits are structured mutations of the internal document model. Phase 6 owns transactional save/serialization/validation, so this phase does not pretend that a modified in-memory document has already been written back to disk.
