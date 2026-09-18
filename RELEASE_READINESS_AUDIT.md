# PDF Everything — Release Readiness Audit

## Scope

The master `prompt.md` was reread section-by-section through the release/definition-of-done gates. Phase 8 was re-hardened and the whole stack was checked for fake UI, command bypasses, unsafe file lifecycle, toolchain mismatch, and packaging gaps.

## Result

**Not yet self-certified as a shipped product from this sandbox.** The implementation now has a reproducible release configuration, but a real Windows machine and Android device/emulator must execute the complete Gradle build and golden-path runtime tests before claiming release readiness.

## Corrected blockers

- Kotlin/Compose/AGP version skew was removed. The project is pinned to Kotlin 2.4.20 + Compose Multiplatform 1.12.0 + AGP 9.3.1 + Gradle 9.6.0.
- JVM targets were aligned to Java 17 for Android/Desktop packaging.
- Dynamic `1.3.+` dependency ranges were removed from adaptive components.
- Unused KSP/Room/Moshi code-generation build hooks were removed.
- Desktop PDFBox was aligned to 3.0.8.
- Desktop opening is candidate-first: failed incoming PDFs no longer close the currently open document.
- Phase 8 now has a single `CommandDispatcher` and sequential transaction validation.
- Release pipeline builds a release APK and real Windows EXE/MSI installers instead of only a debug APK/distributable directory.

## Honest limitations remaining

- Existing native PDF text is not declared editable when the engine cannot establish a stable content-stream object identity. This is intentional: fake white-overpaint/replacement text is forbidden by the product contract.
- Some native object/annotation transformations are capability-gated and refuse unsafe approximations.
- Full platform runtime verification cannot be completed in this sandbox because the Gradle distribution cannot be downloaded.

## Current build commands

```text
./gradlew :composeApp:allTests
./gradlew :composeApp:assembleRelease
./gradlew :composeApp:packageExe :composeApp:packageMsi
```

For Windows, `build_all.bat` executes the release pipeline and checks that APK, EXE and MSI artifacts actually exist.
