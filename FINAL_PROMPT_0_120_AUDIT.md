# PDF Everything — Master Prompt 0–120 Audit

Date: 2026-09-16

## Executive result

The codebase has been re-audited against the complete `prompt.md` specification, including the P0 release gates and Phase 8 command system. The implementation has been hardened and its build configuration has been brought onto a supported toolchain combination.

**Release status: Release Candidate, not self-certified production release.**

The remaining release gate is environmental rather than a hidden fake feature: this sandbox cannot download the Gradle 9.6.0 distribution from `services.gradle.org`, so current-source Windows/Android compilation cannot be executed here. A connected Windows CI runner is configured to perform the real release builds and artifact checks.

## Cross-phase status

| Area | Status | Evidence / implementation |
|---|---|---|
| Foundation / architecture | Hardened | Common document model + platform engine adapter |
| PDF engine abstraction | Hardened | `PdfEngine` common contract + platform adapters |
| Real editing safety | Capability-gated | Unsupported native text/object edits are refused instead of fake overlays |
| Rendering | Implemented | viewport render + priority scheduler + bounded caches |
| Document model | Implemented | Document/Page/PdfObject hierarchy + provenance + validation |
| Core UX | Implemented | Desktop document shell + dedicated mobile shell |
| File opening | Implemented | Windows association/startup/drag-drop + Android VIEW/OPEN_DOCUMENT/share flows |
| Command system (Phase 8) | Hardened | `CommandDispatcher`, history validation, sequential transactions |
| Save / recovery / print | Implemented in code | Safe-save, reopen validation, recovery and native print adapters |
| Build configuration | Corrected | Kotlin 2.4.20 + Compose 1.12.0 + AGP 9.3.1 + Gradle 9.6.0 + Java 17 target |
| CI packaging | Corrected | Release APK + Windows EXE + MSI |
| Full release artifact verification here | BLOCKED | Gradle distribution download unavailable in sandbox |

## Important prompt-specific corrections

1. The project uses Kotlin Multiplatform + Compose Multiplatform instead of the prompt's preferred Flutter default. This is a deliberate existing-stack choice; Compose Multiplatform is stable for Android and JVM desktop, and current 1.12.0 supports Windows 10 x64/arm64 and Android.
2. The dependency set was pinned instead of using dynamic versions.
3. Kotlin 2.4.20 was selected because it is the current stable Kotlin release.
4. AGP 9.3.1 was selected because the Kotlin Multiplatform compatibility guide lists it as a supported Android Gradle Plugin for Kotlin 2.4.20.
5. Desktop PDFBox was upgraded to 3.0.8.
6. Java target was raised to 17 to match current AGP/package requirements.
7. KSP/Room/Moshi code-generation hooks that had no source usage were removed from the build to reduce unnecessary failure surface.
8. Desktop PDF open is now candidate-first so a failed incoming file cannot destroy the current session.
9. All document mutations outside command/editor implementation were scanned; no direct document assignment was found in unrelated UI/platform source.
10. A gross Kotlin delimiter scan is clean across `composeApp/src`.

## Known technical limitations that remain honest

- Existing arbitrary PDF text is not declared structurally editable when the engine cannot identify and rewrite its original content stream safely.
- Some native annotation/object transformations remain capability-gated where exact appearance-preserving mapping cannot be guaranteed.
- Real device/printer/Windows Explorer association runtime tests must be executed on their target operating systems.

## Golden paths still required on target machines

`Windows: Explorer double-click PDF → Open → Edit supported object → Ctrl+C/X/V → Ctrl+Z/Y → Cut → Crop → Save → Reopen → Ctrl+P`

`Android: share/open PDF → Open → touch edit → Cut → Save/Export → Share`

These are explicitly part of the master prompt's definition of done and must be treated as release acceptance tests rather than mocked unit tests.
