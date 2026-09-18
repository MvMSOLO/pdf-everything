# PDF Everything

PDF Everything is an offline-first document-first PDF workspace targeting Windows and Android, implemented with Kotlin Multiplatform + Compose Multiplatform and a platform-isolated PDF engine.

## Implemented product layers

- Phase 1: foundation / platform shell / PDF engine boundary
- Phase 2: viewer / rendering / navigation / search
- Phase 3: document commands / selection / clipboard / editing model
- Phase 4: CUT / crop / trim / split / extract / organizer
- Phase 5: annotations / forms / metadata / bookmarks / document model hardening
- Phase 6: save / Save As / export / recovery / recent files / native printing
- Phase 7: Windows file association / startup file opening / Android document intents and URI handling
- Phase 8: centralized command dispatcher / transaction validation / undo-redo hardening

## Release gate

The repository is **implementation-complete for the audited feature layers but not self-certified as a release artifact** until a connected environment runs the full Gradle Windows and Android builds plus the golden-path integration tests.

The current toolchain is pinned in `gradle/libs.versions.toml` and the Gradle wrapper. See the phase audit documents for feature-level limitations.
