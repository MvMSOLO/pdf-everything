# PDF Everything — Spec Audit Report (Phase 1 & 2)

**Date:** 2026-09-12  
**Scope:** Full 120-section prompt.md audit against source code  
**Build status:** ✅ BUILD SUCCESSFUL (desktop target)  
**Source lines:** ~8,132 Kotlin  

---

## Summary

The Phase 1 (Foundation) and Phase 2 (Viewer) code compiles and provides the correct architectural skeleton. However, many spec sections require **actual working implementations** that are currently missing, stubbed, or placeholder-only. The spec's §0 mandate — *no fake/stub buttons, only real functionality* — is the primary audit criterion.

---

## ✅ What IS Implemented (Correct & Real)

| Spec Section | Feature | Status |
|---|---|---|
| §60 | Project structure (commonMain/desktopMain/androidMain) | ✅ Correct KMP layout |
| §59 | AppState with Document, CommandDispatcher, HistoryManager, etc. | ✅ Single source of truth |
| §3.1 / §61 | Command pattern (DocumentCommand interface) | ✅ Interface + 15+ concrete commands |
| §62 | Transaction for atomic multi-step undo | ✅ Transaction + TransactionBuilder |
| §3.1 | CommandDispatcher (dispatch/undo/redo) | ✅ Wired through HistoryManager |
| §59 | HistoryManager (undo/redo stacks, max depth 200) | ✅ Complete |
| §0 | No hardcoded fake buttons in core logic | ✅ Core is clean |
| §109/110 | DocumentSource sealed (FilePath/ContentUri/ByteArraySource) | ✅ Matches spec |
| §105 | Document versioning (version field, markDirty) | ✅ Increments on mutation |
| §61 | CropPageCommand with undo | ✅ Stores prev/next cropBox |
| §61 | MovePageCommand, RotatePageCommand, DeletePageCommand | ✅ All with undo |
| §63 | PdfPoint, PdfRect, PdfMatrix (coordinate geometry) | ✅ Rich geometry primitives |
| §45 | DirtyState tracking (isDirty, lastSavedVersion) | ✅ markDirty/markSaved |
| §44 | Settings screen route exists | ⚠️ Stub text only |
| §20 | RecoveryService with journal | ✅ Tab-delimited text journal (kotlinx-serialization not in catalog) |
| §20 | FileSystemProvider abstraction | ✅ Common interface for persistence |
| §31-33 | DocumentFileService (open/save/saveAs/recent/autoSave) | ✅ Full service skeleton |
| §55 | Editability enum (FULLY_EDITABLE…UNKNOWN) | ✅ Matches spec |
| §104 | Capability system partial (editability per object) | ⚠️ Not centralized Capability class |
| §57 | RenderPipeline concept | ✅ Pipeline architecture exists |
| §57 | PageCache | ✅ Exists |
| §57 | ViewportState | ✅ Exists |
| §66 | ViewMode enum | ✅ Exists |
| §35/36 | PdfTopBar/PdfBottomBar | ✅ Real composable implementations |
| §38-43 | AppRouter with back stack | ✅ Home/Viewer/Editor/FormFill/Settings/About routes |
| §65 | Theme (Light/Dark) | ✅ Theme.kt + ThemeDynamic per platform |
| §69 | Tool modes referenced | ⚠️ Enum not yet centralized |

---

## ❌ Critical Gaps (Spec §0 Violations & P0 Missing Features)

### 1. §0 / §87 — Fake/Stub UI in App.kt
- **Settings route:** Shows `Text("Settings – coming in Phase 2")` — this is a stub
- **About route:** Shows `Text("About – coming in Phase 2")` — this is a stub  
- **FormFill route:** Shows `Text("Form Fill – coming in Phase 2")` — this is a stub
- **Editor route:** Delegates to ViewerScreen as placeholder
- **Fix:** Remove stub routes or implement real screens per §44/§83

### 2. §9 — Centralized Shortcut Registry Missing
- No `ShortcutRegistry` or `ShortcutService` exists
- Shortcuts are not centralized — per spec, ALL keyboard shortcuts must go through one registry
- Current undo/redo wired via TopBar buttons but no keyboard event handling

### 3. §22 — Multi-Document Tabs (Desktop) Missing
- No tab UI component
- AppState tracks only a single `currentDocument`
- Spec requires multiple documents open simultaneously with tab switching

### 4. §4.3 — Viewport-Aware Rendering Incomplete
- RenderPipeline, PageCache, ViewportState exist but are not fully wired
- No priority queue (current/neighbor/far page priorities) per §57
- No render cancellation for off-screen pages

### 5. §8 — Command Implementations Missing for P0 Features
- No `ExtractPagesCommand` (spec §8/§78 #24)
- No `SplitDocumentCommand` real implementation (currently no-op on original doc)
- No `MergeDocumentsCommand` real implementation (currently no-op)
- No `InsertImageCommand` wiring to engine

### 6. §18 — Printing Not Implemented
- No `PrintService` or platform print integration
- Ctrl+P has no handler

### 7. §25 — Compress/Optimize Not Implemented
- No compression service exists

### 8. §26 — Security/Encryption Not Implemented  
- DocumentPermissions model exists but no encryption/decryption service
- No password prompt UI

### 9. §7 — File Opening from OS Not Implemented
- Desktop main.kt has no command-line argument parsing
- No Windows file association handling
- No Android intent handling in MainActivity.kt

### 10. §23 — Drag & Drop Not Implemented
- No drop target composable for desktop

### 11. §21 — Recent Files Not Persisted
- RecentFiles list is in-memory only; disappears on restart
- FileSystemProvider exists but not wired to persist recent files

### 12. §81 — Blank PDF Creation Partial
- CreatePdfEngine exists in commonMain and both platform sources
- No UI for selecting page size / orientation

### 13. §44 — Settings Screen Missing Content
- Route exists but shows placeholder text
- No General/View/Editing/Performance/Files/Shortcuts/About sections

### 14. §68 — Print Preview Missing
- No print preview UI

### 15. §77 — Document Info Panel Missing
- No info panel composable

### 16. §79 — Command Palette Missing
- No Ctrl+Shift+P action search

### 17. §82 — External Link Handling Missing
- No hyperlink recognition in viewer

### 18. §84 — Logging Not Implemented
- No structured log system (TRACE/DEBUG/INFO/WARN/ERROR)

### 19. §19 — Clipboard Manager Not Wired
- ClipboardManager interface exists but not connected to platform clipboards

### 20. §67 — Presentation/Full-Screen Mode Missing
- No F11 handler, no distraction-free mode

### 21. §70 — Selection Engine Missing
- No unified SelectionModel with SelectionType/SelectedIds/Bounds/etc.

### 22. §71 — Snap/Align Missing
- No snap-to-edge, snap-to-center, alignment guides

### 23. §72 — Cut Visualization Missing
- No crop boundary visualization, dimmed area, numeric dimensions

### 24. §74 — Auto-Save Safety Incomplete
- Auto-save timer exists but saves to original source (spec says default: save recovery separately)

### 25. §75 — Conflict Handling Missing
- No external file modification detection

### 26. §76 — Read-Only File Detection Missing
- DocumentSecurityInfo.isReadOnly exists but no platform check

---

## ⚠️ Moderate Gaps (P1 / Architecture)

| Spec | Gap | Notes |
|---|---|---|
| §58 | Edit invalidation (dirty region) | No dirty-region calculation after mutations |
| §63 | Coordinate converter | CoordinateConverter.kt exists but needs rotation/zoom/crop tests |
| §64 | High-DPI scaling | No density-aware rendering adjustments |
| §43 | Localization strings | All strings hardcoded in English; no string resource abstraction |
| §42 | Accessibility | No semantic labels, focus indicators, reduced-motion support |
| §46 | Validation after save | SaveConfig exists but no reopen-verify logic |
| §111 | Clipboard security | No auto-clear of clipboard refs |
| §112 | Temp file management | FileSystemProvider has no temp dir / cleanup logic |
| §113 | Resource disposal | No explicit close/dispose patterns for native handles |
| §56 | OCR architecture | No OCRService stub (marked optional, OK) |
| §100 | Batch operations | Not implemented (optional V1.1) |
| §83 | Diagnostics report | Missing |
| §85 | Crash reporting | Architecture note exists, no implementation (OK per spec) |

---

## Architecture Assessment

The codebase follows the spec's architectural intent well:
- **Command pattern** ✅ Correctly implemented with undo/redo
- **Document model** ✅ Immutable data classes, functional mutations
- **Engine adapter** ✅ PdfEngine interface + PdfBoxAdapter on desktop
- **KMP separation** ✅ commonMain / desktopMain / androidMain
- **Recovery journal** ✅ Tab-delimited text approach (workaround for missing kotlinx-serialization-json)

The main issue is **incomplete implementation** — the skeleton is correct but many features are placeholder/stub code that violates §0.

---

## Priority Fix Order

1. **Remove §0 violations:** Delete or replace stub routes (Settings, About, FormFill text placeholders)
2. **Add ShortcutRegistry** (§9) — centralized keyboard event handler
3. **Wire ViewportState to ViewerScreen** (§4.3/§57) — render pipeline with priority
4. **Implement multi-document tabs** (§22) on desktop
5. **Wire file opening** — command-line args on desktop (§7), Android intents (§51)
6. **Implement real Extract/Split/Merge** via PdfBoxAdapter (§8)
7. **Add PrintService** (§18) platform integration
8. **Add Settings screen** with real sections (§44)
9. **Implement CompressService** (§25)
10. **Implement SecurityService** (§26) password handling
11. **Persist recent files** via FileSystemProvider (§21)
12. **Add Document Info Panel** (§77)
13. **Add drag & drop** (§23)

---

## Build Commands

```bash
# Desktop compile
cd /nfs/105628662/temp/pdf-everything
JAVA_HOME=/usr/lib/jvm/jdk-17 ./gradlew compileKotlinDesktop --no-configuration-cache

# Android compile (not yet tested)
JAVA_HOME=/usr/lib/jvm/jdk-17 ./gradlew compileKotlinAndroid --no-configuration-cache
```

## Environment

- JDK: Temurin 17.0.20.1 at /usr/lib/jvm/jdk-17
- Kotlin: 2.2.10 (requires JDK 17+)
- PDFBox: 2.0.33 (desktop only)
- Compose Multiplatform: 1.8.2
- kotlinx-serialization-core: 1.9.0 (json NOT available — hence tab-delimited recovery journal)
