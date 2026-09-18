# Phase 6 — Save / Print / Recovery

## Implemented
- Ctrl+S persistent save with document validation and external-change detection.
- Ctrl+Shift+S native Save As on Windows and Android SAF CreateDocument.
- Atomic desktop writes through a temporary sibling file and atomic rename fallback.
- Android Save As to `content://` through a validated temporary PDF followed by resolver-backed copy.
- Post-save reopen and validation in the engine layer.
- Read-only guard; edits are redirected to Save As rather than pretending they are permanent.
- Recovery snapshots stored separately from the source document every 15 seconds while dirty.
- Recovery restore/discard UI on next launch.
- Recovery cleanup after successful Save / Save As.
- Recent files persist name, source, opened time, last page, zoom and fingerprint.
- External conflict detection with Reload / Keep My Changes / Save As choices.
- Real Windows `PrinterJob` pipeline with native printer dialog, page ranges, copies, paper size, orientation, scaling, color mode, parity and collate.
- Real Android `PrintManager` + `PrintDocumentAdapter` pipeline with page-range selection and paper/orientation/color/scaling controls.
- Save progress is executed away from the Compose UI thread; UI reports status rather than inventing progress percentages.

## Safety invariants
- Original source is never overwritten by autosave.
- Export/Save As requires an explicit destination.
- External file changes are never silently overwritten.
- A successful save is not reported until the resulting PDF has been reopened and validated.
- Recovery state is removed only after a successful persistence operation.
