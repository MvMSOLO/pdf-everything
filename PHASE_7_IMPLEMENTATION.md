# Phase 7 — File Opening / OS Integration

## Implemented
- Desktop File > Open through the common `requestPdfOpen` boundary.
- Ctrl+O through `ShortcutAction.Open`.
- Desktop drag-and-drop of `.pdf` files.
- Startup PDF arguments, including a PDF path supplied by the shell.
- Windows installer association metadata via jpackage `fileAssociation(application/pdf, pdf, ...)` and MSI/EXE packaging.
- Windows association status detection without writing UserChoice.
- First-run Default Apps prompt and Settings fallback.
- Windows Open With / Explorer launch path compatibility through the installed PDF association.
- Android `ACTION_VIEW`, `ACTION_EDIT`, `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, and `ClipData` PDF inputs.
- Android `ACTION_OPEN_DOCUMENT` / `OpenDocument` file picker.
- Persistable URI permission when the provider grants it.
- Durable private workspace copy when permission is temporary or persistable permission is unavailable.
- Recent-file persistence for both content URIs and materialized local workspace files.

## Safety / correctness guarantees
- The app never writes Windows `UserChoice` to silently take over `.pdf`.
- Incoming Android URIs are not assumed to remain readable forever.
- Temporary-share PDFs are copied into the app-owned workspace before the transient grant is lost.
- PDF MIME validation is applied to incoming intents.
- Open errors remain visible to the existing document-opening error path rather than silently replacing the active document.

## Packaging
Compose Desktop native distributions now request both EXE and MSI outputs and declare `.pdf` as an application file association, which delegates installer-time association creation to jpackage.
