# Phase 6 — Final Audit

## Requirements mapped

### Save / Save As
- `Ctrl+S` is wired to the persistence coordinator.
- Save is refused for read-only documents unless the user explicitly chooses Save As.
- Save validates the in-memory document model before native serialization.
- Native engine serialization runs off the Compose UI thread.
- Desktop saves use a sibling temporary file and atomic move where the OS supports it, with a safe rename fallback.
- Android SAF Save As writes a validated temporary PDF and then copies it through the selected content URI.
- Successful writes are independently reopened and validated before being reported as successful.

### Conflict handling
- The opened source gets a baseline fingerprint.
- Before normal Ctrl+S, the current external fingerprint is compared against the baseline.
- External changes produce Reload / Keep My Changes / Save As choices.
- Keep My Changes is an explicit overwrite action rather than a silent overwrite.

### Recovery
- Dirty sessions are checkpointed every 15 seconds into a separate recovery area.
- Recovery stores the serializable document state and source identity metadata.
- On next launch, the first recovery candidate is presented with Restore / Discard.
- Successful save removes its recovery entry.
- Source files are never used as the autosave target.

### Recent files
- Filename/source/opened time are stored.
- Last page, zoom and fingerprint are persisted.
- Reopening a recent source resumes the last page/zoom when available.

### Printing
- Windows uses the AWT/Java printing stack and native printer dialog.
- Android uses `PrintManager` / `PrintDocumentAdapter`.
- UI supports page ranges, copies, paper size, orientation, scaling, odd/even selection and color mode.
- Final action enters the system printing workflow; it is not a fake preview or exported-image substitute.

### Export / integrity
- PDF persistence remains behind the `PdfEngine` adapter boundary.
- Save validation checks reopening, page count, page geometry and output size.
- Failed desktop writes do not replace the original until the temporary output has been validated.

## Verification performed
- Phase 6 model test: `PHASE6_MODELS_PASS`.
- Kotlin source brace-balance scan: `PASS` across `composeApp/src`.
- Save/print expect/actual API parity scan: `PASS`.
- No Phase 6 placeholder workflow strings remain in `composeApp/src`.
- ZIP integrity checked after packaging.

## Environment limitation
The Gradle wrapper requested Gradle 9.6.0 from `services.gradle.org`. This sandbox has no working DNS/network path to that host, so a full platform build could not be truthfully marked successful here. The repository contains the exact existing wrapper/build configuration for a connected Windows/Android development environment.
