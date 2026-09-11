# PDF EVERYTHING — MASTER PRODUCT-READY BUILD PROMPT

> **Purpose:** This document is a master implementation prompt for an AI coding agent / senior product engineer. Feed the entire document to the coding agent as the product specification and execution contract.
>
> **Product:** `PDF Everything`
>
> **Target:** A serious, offline-first, cross-platform PDF application that feels like a real desktop document editor rather than a web PDF viewer. It must ship as a Windows `.exe` installer/package and an Android `.apk`, with the architecture prepared for future macOS/Linux/iOS builds without rewriting the core document engine.

---

## 0. EXECUTION MODE — READ THIS FIRST

You are not being asked to create a demo, prototype, landing page, static PDF viewer, or UI concept. This must be a genuine PDF editor with real document-level editing.

You are acting as a **senior desktop/mobile document-software engineer, PDF engine architect, UX engineer, QA lead, and release engineer** working on a product that must be usable by a normal person without developer knowledge.

### Absolute rule

> **Every visible feature must either work for real or be clearly marked as unavailable. Never fake functionality. Never add a button whose backend behavior is missing. Never make a shortcut appear to work while it actually does nothing. Never simulate saving, printing, editing, or file association. **REAL PDF EDITING IS MANDATORY:** when the user edits supported PDF content, the actual PDF objects/content must be modified and serialized into the saved PDF; visual-only overlays, screenshots, canvas-only edits, HTML/DOM tricks, or flattened-page replacements must NOT be used as substitutes for real editing.**

The product must prioritize real behavior over visual spectacle.

### Required mindset

Build in this order:

`Core document model → PDF engine → commands/undo → rendering → editing → Cut tools → file I/O → platform integration → keyboard/touch input → print/export → persistence → performance → visual polish → QA → packaging.`

Do not start by making a beautiful shell and then trying to attach functionality later.

---

# 1. PRODUCT VISION

Create **PDF Everything**, a professional PDF workspace with the mental model of Microsoft Word/Adobe Acrobat/modern document editors, but with a cleaner and more approachable interface.

The application must support:

- opening PDFs;
- viewing PDFs;
- navigating pages;
- selecting/copying text;
- editing existing PDF text when the document model permits it;
- adding and editing text;
- moving/resizing objects;
- image insertion and replacement;
- annotations;
- comments;
- drawing/markup;
- highlighting;
- shapes;
- redaction/whiteout workflows where technically appropriate;
- page reordering;
- page insertion;
- page deletion;
- page extraction;
- page duplication;
- page rotation;
- crop/cut;
- split PDF;
- merge PDFs;
- compress/optimize;
- metadata editing;
- form interaction where supported;
- export;
- printing;
- keyboard shortcuts;
- mouse interactions;
- touch interactions;
- tablet-friendly interaction;
- drag & drop on desktop;
- Android share/open intents;
- Windows file association;
- recent files;
- autosave/recovery;
- undo/redo;
- offline operation;
- predictable error messages;
- reliable large-document handling.

The app should feel like a **real application people install and trust with documents**.

---

# 2. TARGET PLATFORMS

## 2.1 Primary

### Windows

- Windows 10+
- Windows 11+
- x64
- ARM64 where the chosen native engine and dependencies support it
- installer plus portable/dev build as appropriate
- file association for `.pdf`
- drag-and-drop PDF opening
- Explorer “Open with” support
- `Ctrl+O`, `Ctrl+S`, `Ctrl+P`, etc.
- native print dialog
- clipboard integration
- window state persistence
- multi-window readiness

### Android

- modern Android devices
- ARM64 primary
- sensible behavior on lower-memory devices
- APK build for direct installation
- future Play Store packaging readiness
- Android document picker
- Open With / share intent handling for PDFs
- share/export intent
- system back behavior
- touch-first editing
- pinch zoom
- two-finger pan where appropriate
- long-press selection
- touch handles

## 2.2 Future-ready

Architect the core so that macOS, Linux, iOS and additional Android configurations can be added without changing the document model.

---

# 3. RECOMMENDED ARCHITECTURE

Use a cross-platform UI framework with strong Windows + Android support. **Flutter/Dart is the preferred default implementation**, unless there is a documented technical reason to choose another stack.

The current product architecture should be:

```text
┌─────────────────────────────────────────────────────────────┐
│                     PDF EVERYTHING UI                       │
│                    Flutter / platform UI                    │
├─────────────────────────────────────────────────────────────┤
│                    Application Services                     │
│                                                             │
│ Open / Save / Export / Print / Search / Recent / Settings  │
├─────────────────────────────────────────────────────────────┤
│                   Document Command Layer                    │
│      Command + Undo/Redo + Transactions + History          │
├─────────────────────────────────────────────────────────────┤
│                      Document Model                         │
│ Pages / Text / Images / Vectors / Annotations / Forms      │
├─────────────────────────────────────────────────────────────┤
│                       PDF Core API                           │
│ Parse / Render / Edit / Serialize / Optimize / Validate    │
├─────────────────────────────────────────────────────────────┤
│                 Native PDF Engine Adapter                   │
│                 C/C++/Rust/FFI abstraction                  │
├─────────────────────────────────────────────────────────────┤
│                   Platform Integration                      │
│ Windows shell / Print / Clipboard / Android intents        │
└─────────────────────────────────────────────────────────────┘
```

## 3.1 Critical architecture rule: do not bind UI directly to PDF internals

Never do this:

```text
Button → PDF library method → repaint everything
```

Instead:

```text
User action
  ↓
UI interaction
  ↓
Command
  ↓
Document transaction
  ↓
Document model mutation
  ↓
Dirty state
  ↓
Undo stack
  ↓
Incremental render / invalidation
  ↓
UI refresh
```

This is required for reliable undo/redo and for future features.

---

# 4. PDF ENGINE STRATEGY

The PDF engine is the most important technical decision.

## 4.1 Engine abstraction

Create an interface such as:

```text
PdfEngine
 ├── open()
 ├── inspect()
 ├── renderPage()
 ├── getText()
 ├── getObjects()
 ├── getAnnotations()
 ├── getForms()
 ├── modify()
 ├── addObject()
 ├── removeObject()
 ├── updateObject()
 ├── save()
 ├── saveIncremental()
 ├── export()
 ├── optimize()
 └── validate()
```

Do not expose the concrete third-party library throughout the application.

The app must have an adapter boundary so the PDF engine can be replaced without rewriting the UI.

## 4.1A REAL PDF EDITING — NON-NEGOTIABLE

The editor must operate on the actual PDF document structure whenever the selected content is technically editable.

Required principle:

```text
Select PDF object
  ↓
Modify actual PDF object/content
  ↓
Document transaction
  ↓
Undo/redo history
  ↓
Serialize valid PDF
  ↓
Save
  ↓
Close → Reopen → Verify
```

Do NOT implement “editing” by merely drawing replacement text/images over the page, taking a screenshot, flattening the page into an image, or keeping changes only in the UI. Those techniques may be used only for explicitly labeled annotations/markup where appropriate.

For existing text, preserve font, encoding, position, size, spacing, color, transformation and surrounding layout as accurately as the PDF engine permits. If exact structural editing is impossible for a particular PDF object, report that limitation clearly instead of silently performing fake editing.

A saved edited PDF must remain correct when opened in PDF Everything again and in an independent PDF viewer.

## 4.2 Licensing gate

Before selecting a PDF engine, document:

- license;
- commercial-use restrictions;
- redistribution requirements;
- Android support;
- Windows support;
- ARM64 support;
- text extraction quality;
- image handling;
- transparency;
- embedded fonts;
- annotations;
- forms;
- encryption/password PDFs;
- incremental save;
- malformed-PDF tolerance;
- rendering quality;
- edit/write capabilities.

Do not accidentally ship a component whose license conflicts with the intended distribution model.

If a commercial SDK is required for reliable deep editing, isolate it behind the adapter and make the licensing requirement explicit instead of silently depending on it.

## 4.3 Rendering

Rendering must be page-based and cache-aware.

Required behavior:

- viewport-aware rendering;
- only render visible pages at full resolution;
- render neighboring pages at a lower priority;
- recycle off-screen page surfaces;
- cache thumbnails separately;
- cache zoomed tiles if beneficial;
- avoid rendering the entire PDF into memory;
- support very large documents;
- avoid blocking the UI thread;
- cancel stale render tasks;
- prioritize the page currently under the cursor/touch point.

---

# 5. DOCUMENT MODEL

Implement an internal document abstraction.

## 5.1 Document

```text
Document
 ├── documentId
 ├── sourcePath
 ├── sourceUri
 ├── title
 ├── metadata
 ├── pageCount
 ├── permissions
 ├── securityInfo
 ├── dirtyState
 ├── version
 ├── pages[]
 ├── attachments[]
 ├── outline
 ├── formModel
 └── audit/history metadata
```

## 5.2 Page

```text
Page
 ├── pageId
 ├── index
 ├── mediaBox
 ├── cropBox
 ├── bleedBox
 ├── trimBox
 ├── artBox
 ├── rotation
 ├── background
 ├── objects[]
 ├── annotations[]
 ├── widgets[]
 └── renderState
```

## 5.3 Objects

Represent editable content where the PDF engine can expose it:

```text
PdfObject
 ├── TextObject
 ├── ImageObject
 ├── VectorObject
 ├── PathObject
 ├── ShapeObject
 ├── AnnotationObject
 ├── FormWidget
 └── UnknownObject
```

Every object should preserve:

- bounding box;
- transformation matrix;
- z-order;
- opacity;
- rotation;
- resource references;
- original identity where possible;
- editability state;
- selection state.

For objects the engine cannot safely modify, keep them as `UnknownObject` and preserve them during save instead of destroying them.

---

# 6. CORE USER EXPERIENCE

The interface must feel **document-first**, similar in mental model to Word/Acrobat, not like a mobile photo editor.

## 6.1 Desktop layout

Use a professional application shell:

```text
┌──────────────────────────────────────────────────────────────┐
│ Title / file name      Quick actions           Window controls│
├──────────────────────────────────────────────────────────────┤
│ File │ Home │ Edit │ Annotate │ Organize │ View │ Tools      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│ Left panel │              Document canvas           │ Right   │
│ thumbnails │                                      │ inspector│
│            │         ┌───────────────┐            │          │
│            │         │               │            │          │
│            │         │      PDF      │            │          │
│            │         │      PAGE     │            │          │
│            │         │               │            │          │
│            │         └───────────────┘            │          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ page 4 / 18     zoom 125%       selection status     fit view│
└──────────────────────────────────────────────────────────────┘
```

## 6.2 Mobile layout

Do not simply shrink the desktop UI.

Use a mobile document shell:

```text
┌───────────────────────────────┐
│ ←  filename.pdf     ⋯   Save │
├───────────────────────────────┤
│                               │
│           PDF PAGE            │
│                               │
│                               │
├───────────────────────────────┤
│  ◀     Page 3 / 20      ▶     │
├───────────────────────────────┤
│ Edit  Annotate  Organize More │
└───────────────────────────────┘
```

Bottom action sheets should expose secondary tools without overwhelming the screen.

---

# 7. FILE OPENING — REAL APPLICATION BEHAVIOR

## 7.1 Open methods

Support:

- File > Open
- `Ctrl+O`
- drag-and-drop
- double-click `.pdf` from Windows Explorer
- Windows Open With
- Windows default app association
- Android file picker
- Android share/open intent
- recent file list
- app startup with file argument
- command-line file opening on desktop where practical

## 7.2 Windows PDF association

Register `.pdf` with an application-specific ProgID during installation.

Do not forcibly steal the default every time the app launches.

Provide:

- First-run option to make PDF Everything the default PDF app;
- Settings → Default apps status;
- “Make PDF Everything default” action;
- “Open Windows default-app settings” fallback.

Never silently reclaim the default association.

Support launching the app with a PDF path supplied by the OS.

## 7.3 Android open handling

Support:

- `ACTION_VIEW`/document-open flows appropriate for PDF files;
- incoming content URIs;
- persisted access where Android permits it;
- temporary cache handling;
- safe copy-to-app workspace when a provider only grants temporary access.

---

# 8. COMMAND SYSTEM

This is mandatory.

All modifying actions must become commands.

Examples:

```text
InsertTextCommand
DeleteObjectCommand
MoveObjectCommand
ResizeObjectCommand
RotateObjectCommand
InsertImageCommand
DeletePageCommand
DuplicatePageCommand
MovePageCommand
RotatePageCommand
CropPageCommand
SplitDocumentCommand
MergeDocumentsCommand
AddAnnotationCommand
EditAnnotationCommand
DeleteAnnotationCommand
EditMetadataCommand
```

Each command must support:

```text
execute()
undo()
redo()
canExecute()
description
```

## 8.1 Undo/redo

Desktop:

- `Ctrl+Z`
- `Ctrl+Y`
- `Ctrl+Shift+Z`

Mobile:

- visible Undo/Redo controls in editing mode;
- optional multi-touch history gesture only if it does not conflict with system gestures.

Undo must restore:

- object position;
- text edits;
- page order;
- crop state;
- annotations;
- image operations;
- metadata;
- structural operations.

For large document operations, use transaction checkpoints rather than duplicating the entire document in RAM.

---

# 9. KEYBOARD SHORTCUTS — REAL, NOT DECORATIVE

Implement a centralized shortcut registry.

## File

| Shortcut | Action |
|---|---|
| `Ctrl+O` | Open |
| `Ctrl+S` | Save |
| `Ctrl+Shift+S` | Save As |
| `Ctrl+P` | Print |
| `Ctrl+W` | Close document/tab |
| `Ctrl+N` | New blank document if supported |

## Edit

| Shortcut | Action |
|---|---|
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+C` | Copy |
| `Ctrl+X` | Cut selected editable object/text |
| `Ctrl+V` | Paste |
| `Ctrl+A` | Select all within current editing context |
| `Delete` | Delete selection |
| `Backspace` | Delete selection/text |

## Search

| Shortcut | Action |
|---|---|
| `Ctrl+F` | Search |
| `F3` | Next result |
| `Shift+F3` | Previous result |

## View

| Shortcut | Action |
|---|---|
| `Ctrl++` | Zoom in |
| `Ctrl+-` | Zoom out |
| `Ctrl+0` | Fit page |
| `Ctrl+1` | 100% |
| `Home` | First page |
| `End` | Last page |
| `PageUp` | Previous page |
| `PageDown` | Next page |

## Navigation

- arrow keys for selection movement;
- Shift + arrow for keyboard selection where applicable;
- mouse wheel for scroll;
- `Space` + drag for pan if supported;
- middle mouse drag for pan if supported.

### Shortcut architecture

Do not hardcode keyboard events inside random widgets.

Use:

```text
KeyboardEvent
  ↓
ShortcutResolver
  ↓
CommandDispatcher
  ↓
Command
```

Resolve conflicts based on current mode:

```text
Browse mode
Text edit mode
Object edit mode
Crop mode
Annotation mode
Page organizer mode
```

---

# 10. COPY / CUT / PASTE — IMPORTANT DISTINCTION

There are multiple meanings of “cut”. Implement them separately.

## 10.1 Text cut

When a text selection is actively editable:

- `Ctrl+C` copies selected text;
- `Ctrl+X` removes selected editable text and copies it;
- `Ctrl+V` inserts clipboard text at the active text cursor.

## 10.2 Object cut

When a page object is selected:

- `Ctrl+C` copies the object;
- `Ctrl+X` removes it and stores it in the internal clipboard;
- `Ctrl+V` creates a new object at a sensible offset.

## 10.3 External clipboard

Support:

- plain text;
- image clipboard where platform APIs allow it;
- internal object clipboard metadata.

Never serialize unsafe internal object data directly into an untrusted external clipboard format.

---

# 11. THE SIGNATURE FEATURE — “CUT” WORKSPACE

This is one of the main differentiators of PDF Everything.

Do **not** make “Cut” a single crop button.

Create a dedicated `CUT` workspace with multiple modes.

## 11.1 Cut workspace

Desktop:

```text
┌──────────────────────────────────────────────────────────┐
│ CUT MODE                                                  │
├──────────────────────────────────────────────────────────┤
│ Crop Page | Content Trim | Split | Extract | Delete Area │
├──────────────────────────────────────────────────────────┤
│                                                          │
│                page with active cut frame                │
│                                                          │
├──────────────────────────────────────────────────────────┤
│ X 72     Y 108     W 940     H 1320                     │
│ Aspect: Free   Rotation: 0°      Apply    Reset          │
└──────────────────────────────────────────────────────────┘
```

Mobile:

```text
┌───────────────────────────────┐
│ Cut                      Done │
├───────────────────────────────┤
│                               │
│     [ crop frame handles ]    │
│                               │
├───────────────────────────────┤
│ Crop  Trim  Split  Extract    │
├───────────────────────────────┤
│ Aspect ▾   Reset   Apply       │
└───────────────────────────────┘
```

## 11.2 Crop Page

Users can drag:

- top-left;
- top-right;
- bottom-left;
- bottom-right;
- edge handles;
- center move handle.

Controls:

- numeric X/Y/Width/Height;
- lock aspect ratio;
- A4;
- A5;
- Letter;
- Legal;
- original page ratio;
- portrait;
- landscape;
- margins;
- reset to original page box.

Display the crop boundary clearly.

Never destroy content merely because the user temporarily moved a crop frame.

Use a non-destructive preview until the user presses `Apply`.

## 11.3 Content Trim

A different mode from Crop Page.

Analyze visible page content and propose the smallest bounding rectangle around relevant content while respecting a configurable margin.

Allow:

- auto-detect;
- manual adjustment;
- margin slider;
- exclude background objects;
- preview.

## 11.4 Delete Area / Whiteout

Create a rectangle selection over page content.

The UI must clearly distinguish:

- visual whiteout overlay;
- destructive removal where the engine can truly remove content;
- redaction workflow.

Do not call a white rectangle “secure redaction”.

For sensitive content, provide a real redaction workflow only when the PDF engine can guarantee underlying content is removed from the final file.

## 11.5 Split

Split the document by:

- every N pages;
- selected pages;
- page ranges;
- bookmarks/outlines when supported;
- custom split points.

Preview resulting files before writing.

## 11.6 Extract

Extract selected pages to a new PDF.

Examples:

```text
1-3
5
8-11
```

Support reorder before extraction.

## 11.7 Cut page into sections

Optional advanced operation:

- split a page into 2, 4, 6, or custom rectangular panels;
- preserve reading order where practical;
- export each crop as separate pages;
- useful for scans, posters and multi-column scans.

---

# 12. PAGE ORGANIZER

Create a thumbnail panel / organizer.

Required operations:

- reorder by drag;
- multiple select;
- delete;
- duplicate;
- rotate left;
- rotate right;
- extract;
- insert blank page;
- insert another PDF;
- replace page;
- move selection to first/last;
- select odd pages;
- select even pages;
- reverse selection;
- page labels where supported.

Desktop should support direct thumbnail drag-and-drop.

Mobile should use long-press and multi-select mode.

---

# 13. TEXT EDITING

This is one of the hardest parts. Implement honestly.

## 13.1 Existing text

When the underlying PDF structure allows object-level editing:

- click/tap text;
- select text object;
- show bounding box;
- edit characters;
- preserve style as much as possible;
- preserve font metrics;
- respect embedded fonts;
- adjust font size;
- font family where legal/available;
- font weight;
- alignment;
- text color;
- baseline/line-height as appropriate.

## 13.2 Reflow rule

Do not pretend PDF text behaves like Word paragraphs.

PDFs are coordinate-based documents. The editor should preserve coordinates and use a controlled text-editing model.

For difficult PDFs:

- show an “object not safely editable” state;
- allow overlay text replacement where appropriate;
- preserve the original underlying content unless the user explicitly chooses redaction/whiteout.

## 13.3 Add text

Support:

- click to place;
- drag to create text box;
- resize handles;
- font controls;
- alignment;
- color;
- line spacing;
- background/fill optional;
- border optional;
- opacity.

Desktop:

- text cursor;
- selection;
- keyboard navigation;
- `Ctrl+C/X/V`.

Mobile:

- long press;
- text selection handles;
- system keyboard;
- floating formatting bar.

---

# 14. IMAGE EDITING

Support:

- insert image;
- replace image;
- move;
- resize;
- crop image within object bounds;
- rotate;
- opacity;
- flip horizontal/vertical where supported;
- delete;
- copy/paste.

Preserve image quality intelligently.

Do not blindly re-encode every embedded image when saving if the engine can preserve original streams.

---

# 15. ANNOTATIONS & MARKUP

Implement real PDF annotations where supported:

- highlight;
- underline;
- strikeout;
- freehand pen;
- rectangle;
- ellipse;
- line;
- arrow;
- text note;
- sticky note;
- comment/reply model where supported;
- stamp;
- link editing where supported.

Properties:

- color;
- opacity;
- line width;
- author;
- timestamp;
- popup/note content.

Do not flatten annotations automatically on every save.

Provide:

`Save with editable annotations`

and, when appropriate,

`Flatten annotations`.

---

# 16. SEARCH

`Ctrl+F` must open an actual document search.

Requirements:

- full-text extraction;
- incremental search;
- next/previous;
- result count;
- current page indicator;
- highlight all matches;
- case-sensitive option;
- whole-word option;
- match navigation;
- search across document.

Desktop shortcut:

`Ctrl+F`

Mobile:

search icon in top bar.

For scanned PDFs:

- detect lack of text layer;
- show OCR option if OCR is implemented;
- never falsely report text results when none exist.

---

# 17. ZOOM / VIEW / NAVIGATION

Required zoom controls:

- fit page;
- fit width;
- fit height;
- actual size / 100%;
- custom percentage;
- zoom in/out;
- pinch-to-zoom on mobile;
- double-tap zoom on mobile;
- smooth scroll;
- page-by-page mode;
- continuous mode;
- two-page mode on desktop;
- single-page mode;
- rotate view;
- dark reading mode where technically safe.

Maintain stable focus while zooming:

> The point under the cursor/finger should stay visually anchored when zoom changes.

---

# 18. PRINTING — `CTRL+P` MUST BE REAL

This is mandatory.

`Ctrl+P` must invoke the native print flow, not a fake preview popup.

Required options:

- printer selection;
- page range;
- copies;
- orientation;
- paper size;
- scaling;
- fit to printable area;
- actual size;
- custom scale;
- selected pages;
- odd/even pages where platform supports it;
- color/grayscale according to printer capabilities;
- collate when supported.

Use platform-native printing APIs wherever possible.

On Windows, print through the operating system/printer subsystem.

On Android, integrate with the Android printing framework/available native print flows rather than generating a useless image file and calling that printing.

Before release, test against at least:

- virtual PDF printer;
- physical/network printer where available;
- several page sizes;
- landscape documents;
- mixed page rotation;
- large multi-page documents.

---

# 19. SAVE / SAVE AS / EXPORT

## Save

`Ctrl+S`

Must:

- write to the current document path;
- preserve document integrity;
- preserve supported features;
- update modified state;
- update recent-files state;
- handle failed saves safely.

## Save As

`Ctrl+Shift+S`

Support:

- new filename;
- new directory;
- extension normalization;
- overwrite confirmation;
- conflict handling.

## Export

Possible export targets:

- PDF;
- flattened PDF;
- page images;
- PNG/JPEG where appropriate;
- text extraction;
- images extracted from the PDF.

Export operations must never silently replace the source file.

---

# 20. AUTOSAVE / CRASH RECOVERY

Create a recovery subsystem.

When a modified document has unsaved changes:

- save a recovery snapshot periodically;
- store enough metadata to restore the editing session;
- recover after application crash;
- show recovery UI on next launch.

Example:

```text
Recovered document

PDF Everything found an unsaved editing session.

[Restore]   [Discard]
```

Recovery files must be safely cleaned up after successful save.

---

# 21. RECENT FILES

Store:

- filename;
- URI/path;
- last opened time;
- thumbnail if allowed;
- last page;
- last zoom;
- document fingerprint/hash where useful.

If a path no longer exists:

- show missing state;
- offer locate/relink;
- do not silently delete the history entry.

---

# 22. MULTI-DOCUMENT EXPERIENCE

Desktop should support tabs.

Example:

```text
┌ invoice.pdf ─── contract.pdf ─── notes.pdf ── + ┐
```

Requirements:

- one app window, multiple documents;
- per-document undo stack;
- per-document zoom/page state;
- dirty indicator;
- close tab confirmation when unsaved;
- reopen previous tabs option.

Architect for multi-window later, but do not overcomplicate the first release unnecessarily.

---

# 23. DRAG & DROP

Windows:

- drop PDF into app;
- drop multiple PDFs;
- drop image into document to insert;
- drop PDF into page organizer to merge/insert;
- show clear drop target feedback.

Android:

- support document provider/share flows;
- accept compatible incoming content.

---

# 24. MERGE PDF

Create a merge workflow:

```text
Files
 ├── A.pdf
 ├── B.pdf
 └── C.pdf
```

Allow:

- add files;
- reorder files;
- remove files;
- inspect page count;
- optionally preview thumbnails;
- merge;
- Save As.

Warn before overwriting an existing destination.

---

# 25. COMPRESS / OPTIMIZE

Create a document optimization panel.

Controls:

- lossless optimization;
- image quality preset;
- downsample images;
- remove unused resources;
- subset fonts only where safe;
- remove redundant metadata where requested;
- flatten optional layers/annotations only when explicitly selected.

Show estimated output size when practical.

Example:

```text
Original: 84.2 MB
Estimated: 19.7 MB
Quality: High

[Preview] [Optimize] [Cancel]
```

Never promise an exact size before processing.

---

# 26. SECURITY / DOCUMENT SAFETY

PDFs are complex untrusted input.

Treat every opened PDF as untrusted data.

Required:

- safe parser boundaries;
- malformed document handling;
- resource limits;
- decompression-bomb protections;
- large image limits;
- maximum render surface sizes;
- cancellation;
- no arbitrary external execution from PDF contents;
- safe link handling;
- confirmation for external URLs;
- password prompt for encrypted documents;
- permission information display.

Never execute embedded PDF scripts merely because a PDF contains them.

---

# 27. PASSWORD / ENCRYPTED PDF FLOW

When a protected PDF is opened:

```text
Encrypted PDF

Password required.

[________________]

[Open]
```

Handle:

- wrong password;
- user cancellation;
- unsupported encryption;
- permissions restrictions;
- save restrictions.

Clearly communicate what the application can and cannot modify.

---

# 28. FORMS

Where supported by the selected PDF engine:

- text fields;
- checkboxes;
- radio buttons;
- combo boxes;
- signature fields;
- reset forms;
- tab navigation;
- save filled forms.

Do not flatten forms during ordinary saving unless explicitly requested.

---

# 29. DOCUMENT METADATA

Provide:

- title;
- author;
- subject;
- keywords;
- creator;
- producer;
- creation date;
- modification date;
- page count;
- PDF version where available.

Edit metadata through a clear inspector.

---

# 30. BOOKMARKS / OUTLINE

Support viewing and, if the engine allows it, editing:

- bookmark tree;
- add bookmark;
- rename;
- delete;
- reorder;
- change destination;
- collapse/expand.

Navigation must jump to the right page/position.

---

# 31. THUMBNAILS

Thumbnail pane must:

- lazy-load;
- cache;
- display current page state;
- reflect page rotation;
- update after crop/page modifications;
- support drag reorder;
- support multi-select;
- remain responsive on hundreds/thousands of pages.

For 1000+ page PDFs, never synchronously render all thumbnails at once.

---

# 32. PERFORMANCE TARGETS

Set realistic engineering targets:

### Startup

- fast cold startup;
- no unnecessary network dependency;
- show application shell quickly;
- defer expensive services.

### Opening

- show first visible page as quickly as possible;
- render remaining pages in background;
- provide progress for expensive operations.

### Interaction

- scrolling should remain responsive;
- zoom should not trigger full-document redraws;
- selection handles should remain smooth;
- page organizer should remain usable with large documents.

### Memory

Do not load every rendered page as a full-resolution bitmap.

Use:

- LRU caches;
- size budgets;
- tile rendering where useful;
- cancellation;
- resource disposal.

---

# 33. LOW-END DEVICE MODE

Include a performance setting:

`Settings → Performance → Memory Saver`

Possible effects:

- smaller render cache;
- lower thumbnail resolution;
- reduced prefetch distance;
- deferred image decoding;
- disable decorative animations.

The document itself must still render correctly.

---

# 34. UI DESIGN SYSTEM

The interface must resemble a **real productivity application**.

Avoid:

- giant decorative gradients;
- excessive glassmorphism;
- flashy gaming UI;
- massive hero sections;
- “AI-generated SaaS template” appearance;
- unnecessary rounded cards everywhere;
- controls floating without hierarchy.

Use:

- clear toolbar hierarchy;
- compact menus;
- familiar iconography;
- predictable selection states;
- restrained shadows;
- professional spacing;
- readable typography;
- keyboard-accessible controls.

The design target is:

`Word / Acrobat / modern professional editor`

—not a social-media app.

---

# 35. DESKTOP TOOLBAR STRUCTURE

Recommended tabs:

```text
File
Home
Edit
Annotate
Organize
Cut
View
Tools
```

### Home

- Open
- Save
- Save As
- Print
- Undo
- Redo
- Add Text
- Add Image
- Highlight

### Edit

- Select
- Text
- Image
- Object
- Copy
- Cut
- Paste
- Delete

### Annotate

- Highlight
- Underline
- Strikeout
- Pen
- Shape
- Comment
- Stamp

### Organize

- Page thumbnails
- Insert
- Delete
- Rotate
- Duplicate
- Reorder
- Merge
- Split
- Extract

### Cut

- Crop
- Trim
- Delete Area
- Split Page
- Extract Selection

### View

- Zoom
- Fit
- Page mode
- Two-page
- Rotate view
- Full screen

### Tools

- Search
- Metadata
- Compress
- OCR if implemented
- Export
- Security/document information

---

# 36. MOBILE TOOL SYSTEM

Mobile should expose only the most common actions by default.

Primary bottom bar:

```text
Open | Edit | Cut | Annotate | Organize | More
```

`More` opens a bottom sheet with:

- Search
- Export
- Print
- Metadata
- Compress
- Document info
- Settings
- Help

Context-sensitive toolbar must replace irrelevant controls.

Example:

When a text object is selected:

```text
Bold | Italic | Size | Color | Align | Copy | Cut | Delete
```

When a page is in crop mode:

```text
Reset | Aspect | Apply
```

---

# 37. TOUCH INTERACTION SPEC

Implement carefully tested touch behavior.

### Navigation

- one finger scrolls document;
- pinch zooms;
- double tap zooms intelligently;
- two-finger gestures must not accidentally edit content.

### Object selection

- tap object;
- visible handles;
- drag to move;
- handles resize;
- rotation handle if space allows.

### Text

- tap to select text box;
- tap inside editable text to enter text mode;
- long press for native-style selection where supported.

### Page organizer

- long press → selection mode;
- drag selected page to reorder;
- multi-select supported.

### Crop

Handles must be large enough for fingers.

Add subtle haptic feedback if the platform supports it and if the action benefits from it.

---

# 38. MOUSE INTERACTION SPEC

Support:

- left click selection;
- drag selection;
- wheel scrolling;
- wheel + Ctrl zoom;
- right click context menu;
- double click text/object;
- middle-click pan where appropriate.

Selection boxes must not flicker during scrolling.

---

# 39. CONTEXT MENUS

Right-click a text selection:

```text
Copy
Cut
Search web (optional, only when explicitly invoked)
Highlight
Underline
Add comment
```

Right-click an object:

```text
Copy
Cut
Paste
Duplicate
Delete
Properties
Rotate
Move to front
Move to back
```

Right-click a page thumbnail:

```text
Insert page
Duplicate
Delete
Rotate
Extract
Split from here
Crop
Properties
```

Do not show irrelevant actions as enabled.

---

# 40. STATUS / DIRTY STATE

The user must always know:

- saved;
- modified;
- saving;
- save failed;
- recovering;
- locked/read-only.

Example:

```text
invoice.pdf  •  Modified
```

When saved:

```text
invoice.pdf  ✓ Saved
```

Do not rely only on a tiny spinner.

---

# 41. ERROR HANDLING

Never show raw stack traces to normal users.

Bad:

```text
NullReferenceException at PdfDocument.cpp:1837
```

Good:

```text
Could not save this PDF.

The file may be locked by another application or the destination may be unavailable.

[Try Again] [Save As…] [Details]
```

Developer details can be available under `Details` or diagnostic export.

---

# 42. ACCESSIBILITY

Support:

- keyboard navigation;
- visible focus indicators;
- accessible labels;
- readable contrast;
- scalable UI text;
- sensible hit targets;
- reduced motion option;
- screen-reader semantics where supported;
- no color-only status communication.

---

# 43. LOCALIZATION

Design the app for localization from day one.

Strings must not be hardcoded throughout widgets.

Initial language:

- English

Architecture ready for:

- Uzbek;
- Russian;
- additional languages.

Date/number formatting must use locale-aware formatting.

---

# 44. SETTINGS

Create a real Settings screen.

Sections:

### General

- default open behavior;
- reopen previous documents;
- confirm before closing modified docs;
- language;
- theme.

### View

- default zoom;
- page layout;
- sidebar default state;
- continuous/page mode.

### Editing

- snap behavior;
- selection mode;
- default font;
- default annotation style.

### Performance

- memory saver;
- thumbnail quality;
- prefetch distance.

### Files

- default save folder;
- recent-file history size;
- recovery behavior.

### Shortcuts

Show searchable keyboard shortcuts.

### About

- version;
- build;
- licenses;
- diagnostic information.

---

# 45. FILE FORMAT PRESERVATION RULES

When opening and saving PDFs:

1. Preserve as much original structure as possible.
2. Avoid full reserialization if incremental save is safely supported.
3. Preserve unsupported objects/resources whenever possible.
4. Preserve page dimensions and rotations.
5. Preserve annotations when requested.
6. Preserve forms when requested.
7. Preserve metadata unless modified.
8. Never silently flatten everything.
9. Never silently rasterize the whole document unless the user explicitly chooses rasterization/export.
10. Never replace the source until the new file has been written and validated.

## Safe-save algorithm

```text
Source PDF
   ↓
Create temporary output
   ↓
Write modified document
   ↓
Flush
   ↓
Validate output structure
   ↓
Optional reopen test
   ↓
Atomic replace / rename
   ↓
Update recent state
```

On failure:

- keep original intact;
- keep temporary file for recovery if useful;
- show actionable message.

---

# 46. VALIDATION AFTER SAVE

After writing a PDF:

- reopen it with the engine;
- verify page count;
- verify modified objects still exist;
- optionally verify each page can render;
- verify file is not truncated;
- verify output path exists;
- verify permissions/size.

For large documents, use a configurable quick/full validation mode.

---

# 47. LARGE DOCUMENT TESTING

Mandatory test documents:

- 1 page;
- 5 pages;
- 50 pages;
- 100 pages;
- 500 pages;
- 1000+ pages;
- scanned PDFs;
- text-heavy PDFs;
- image-heavy PDFs;
- mixed orientation PDFs;
- password-protected PDFs;
- PDFs containing forms;
- PDFs with annotations;
- malformed PDFs;
- PDFs with unusual fonts;
- PDFs with transparent graphics;
- PDFs with very large embedded images.

---

# 48. TEST MATRIX

Create automated tests and manual QA.

## Unit tests

- document model;
- commands;
- undo/redo;
- crop geometry;
- split ranges;
- page ordering;
- selection logic;
- shortcut resolution;
- file-state logic;
- safe-save logic.

## Integration tests

- open/save;
- save as;
- merge;
- split;
- crop;
- extract;
- print invocation;
- clipboard;
- platform file association;
- Android incoming files.

## Visual tests

- toolbar states;
- page rendering;
- selection boxes;
- crop handles;
- mobile layouts;
- dark/light mode.

## Regression tests

Every bug fixed must become a regression test where practical.

---

# 49. CUT MODE QA CASES

Test all of these:

### Crop

- crop smaller than original;
- crop almost entire page;
- crop to A4;
- crop rotated page;
- crop multiple selected pages;
- apply then undo;
- save then reopen;
- crop after annotation.

### Split

- one split point;
- many split points;
- invalid range;
- first-page split;
- last-page split;
- split 1000-page PDF.

### Extract

- single page;
- multiple pages;
- reordered selection;
- duplicates if supported;
- extraction from password document.

### Delete area / redaction

- preview before apply;
- undo;
- save/reopen;
- verify underlying content behavior matches the declared mode.

---

# 50. FILE ASSOCIATION QA — WINDOWS

Test:

1. Install PDF Everything.
2. Right-click a PDF → Open with PDF Everything.
3. Double-click a PDF.
4. Reboot Windows.
5. Double-click another PDF.
6. Open from Explorer while app is already running.
7. Open several PDFs.
8. Uninstall.
9. Reinstall.
10. Change another application as default.
11. Confirm PDF Everything does not silently retake the default.
12. Use Settings → Make default.
13. Use Windows Default Apps UI.

The application must handle command-line/open-file arguments reliably.

---

# 51. ANDROID QA

Test:

- file manager → PDF Everything;
- browser/downloads → Open with PDF Everything;
- share sheet → PDF Everything;
- content URI with no direct filesystem path;
- offline opening;
- back navigation;
- rotation;
- app killed while PDF open;
- app restored;
- low RAM;
- large PDF;
- external storage permission model.

---

# 52. WINDOWS PACKAGING

Deliver:

- development build;
- release `.exe` installer;
- versioned release artifacts;
- icons;
- uninstall support;
- application metadata;
- file association registration;
- safe upgrade behavior.

Prefer a standard Windows installer technology appropriate to the selected framework.

Installer should:

- let user choose installation directory where appropriate;
- create Start Menu shortcut optionally;
- create desktop shortcut optionally;
- register PDF association;
- provide uninstaller.

Do not bundle an unnecessary browser runtime if the app architecture does not need one.

---

# 53. ANDROID PACKAGING

Deliver:

- debug APK for development;
- release APK;
- versionCode;
- versionName;
- app icon;
- splash screen;
- signed release configuration separated from secrets;
- ABI strategy.

Do not commit signing keys or passwords to the repository.

Prepare release configuration for future AAB generation even if the immediate deliverable is APK.

---

# 54. OFFLINE-FIRST REQUIREMENT

Core PDF workflows must not require internet.

The following must work offline:

- open;
- view;
- search text;
- edit supported content;
- crop;
- split;
- merge;
- annotations;
- save;
- export;
- print where platform printer access exists.

Do not add an artificial cloud account requirement.

---

# 55. NO-BULLSHIT FEATURE POLICY

Whenever the selected engine does not support a requested feature reliably, do not fabricate it.

Use one of these states:

```text
SUPPORTED
PARTIALLY_SUPPORTED
NOT_SUPPORTED
REQUIRES_CONVERSION
REQUIRES_OCR
READ_ONLY
```

The UI should explain the limitation in plain language.

Example:

```text
This PDF stores the text as a single scanned image.
Direct text editing is unavailable for this page.

[Run OCR]   [Add Text Overlay]
```

---

# 56. OPTIONAL OCR ARCHITECTURE

Treat OCR as an optional isolated subsystem.

```text
OCRService
 ├── detectTextLayer()
 ├── recognizePage()
 ├── recognizeSelection()
 └── applyTextLayer()
```

Keep OCR optional because it may significantly increase APK/installer size.

If OCR is included:

- provide progress;
- allow cancel;
- process page-by-page;
- do not block UI;
- preserve original image;
- make OCR text searchable;
- clearly differentiate OCR confidence/limitations.

---

# 57. PERFORMANCE-CRITICAL RENDER LOOP

Use a pipeline conceptually like:

```text
VisibleViewport
    ↓
PagePriorityQueue
    ↓
RenderScheduler
    ├── CurrentPage: highest priority
    ├── NeighboringPages: medium priority
    └── FarPages: low priority
    ↓
RenderWorkers
    ↓
Surface/TileCache
    ↓
UI compositor
```

When the user rapidly scrolls:

- cancel obsolete page renders;
- prioritize newly visible pages;
- prevent old requests from overwriting newer tiles.

Every async render result must be tagged with a document version/page version/viewport key so stale content cannot overwrite current edits.

---

# 58. EDIT INVALIDATION

Do not re-render the entire document after every small change.

When a text object moves:

```text
Object changed
   ↓
Calculate old bounds + new bounds
   ↓
Invalidate union region
   ↓
Re-render affected tile/page region
```

For structural page operations, invalidate affected pages and their thumbnails.

---

# 59. STATE MANAGEMENT

Choose a predictable state architecture.

Suggested conceptual domains:

```text
AppState
 ├── SessionState
 ├── WindowState
 ├── DocumentState
 ├── SelectionState
 ├── ToolState
 ├── HistoryState
 ├── SearchState
 ├── SettingsState
 └── RecoveryState
```

Avoid one giant global state object.

Keep document state independent from UI widget lifecycle.

---

# 60. PROJECT STRUCTURE

A suggested structure:

```text
pdf_everything/
├── app/
│   ├── app.dart
│   ├── router/
│   └── bootstrap/
├── core/
│   ├── document/
│   ├── commands/
│   ├── history/
│   ├── geometry/
│   ├── clipboard/
│   ├── search/
│   ├── files/
│   ├── recovery/
│   └── services/
├── pdf_engine/
│   ├── api/
│   ├── adapter/
│   └── native/
├── features/
│   ├── viewer/
│   ├── editor/
│   ├── cut/
│   ├── organizer/
│   ├── annotations/
│   ├── forms/
│   ├── search/
│   ├── print/
│   ├── export/
│   ├── merge/
│   ├── split/
│   ├── settings/
│   └── recent_files/
├── platform/
│   ├── windows/
│   └── android/
├── ui/
│   ├── design_system/
│   ├── desktop/
│   ├── mobile/
│   └── shared/
├── assets/
└── test/
```

Refactor names to match the chosen framework, but preserve the architectural boundaries.

---

# 61. COMMAND EXAMPLES

## Crop command

```pseudo
class CropPageCommand implements DocumentCommand {
    PageId pageId
    Rect previousCropBox
    Rect nextCropBox

    execute(document) {
        page = document.getPage(pageId)
        previousCropBox = page.cropBox
        page.cropBox = nextCropBox
    }

    undo(document) {
        document.getPage(pageId).cropBox = previousCropBox
    }
}
```

## Move object

```pseudo
class MoveObjectCommand {
    ObjectId objectId
    Point oldPosition
    Point newPosition

    execute(document) {
        document.move(objectId, newPosition)
    }

    undo(document) {
        document.move(objectId, oldPosition)
    }
}
```

Use the actual language/framework syntax in implementation.

---

# 62. TRANSACTIONS

Complex actions must be atomic.

Example: moving 20 selected pages.

Instead of 20 unrelated user-visible undo steps:

```text
Move 20 pages
```

should become one undo transaction.

Likewise:

- merge;
- split;
- multi-page crop;
- multi-object deletion;
- multi-object formatting.

---

# 63. PAGE COORDINATE SYSTEM

PDFs commonly use their own coordinate system, while UI frameworks use another.

Define one explicit conversion module:

```text
PdfCoordinates
UiCoordinates
ViewportCoordinates
ScreenCoordinates
```

All conversions should go through tested geometry functions.

Mandatory tests:

- rotated page;
- zoomed page;
- scrolled page;
- cropped page;
- high-DPI desktop;
- Android density variation.

Never scatter coordinate conversion formulas across widgets.

---

# 64. HIGH-DPI / DISPLAY SCALING

Windows must support:

- 100%;
- 125%;
- 150%;
- 200%;
- mixed-DPI monitors where the chosen framework supports it.

UI controls must not become tiny or blurry.

PDF rendering should remain crisp at high zoom.

---

# 65. THEMING

Provide:

- Light;
- Dark;
- System.

PDF content itself must not be forcibly recolored in normal reading mode.

A dark-reading option may apply a visual transformation only when explicitly selected, but editing/export must preserve original document colors.

---

# 66. DOCUMENT VIEW MODES

Implement:

### Single page

One page centered.

### Continuous

Vertical scrolling through pages.

### Two-page

Side-by-side spreads on sufficiently wide screens.

### Organizer

Thumbnail grid.

### Presentation/full-screen

Minimal chrome for reading.

---

# 67. PRESENTATION / FULL-SCREEN

Provide a distraction-free mode.

Desktop:

- F11 or menu action;
- hide toolbars;
- fit content;
- page navigation overlay.

Mobile:

- tap to show/hide controls.

Do not break existing navigation shortcuts.

---

# 68. PRINT PREVIEW

The print flow may include an in-app print preview if technically useful, but the final `Print` action must connect to real platform printing.

Print preview should show:

- page count;
- selected range;
- scaling;
- orientation;
- target printer if available.

---

# 69. CONTEXT-AWARE TOOL MODE

At any moment, the UI must answer:

> “What am I currently editing?”

Modes:

```text
Browse
Text
Object
Image
Annotation
Cut
Page Organizer
Form
```

The cursor/icon/status must reflect the active tool.

Escape should return to a safe selection/browse state when appropriate.

---

# 70. SELECTION ENGINE

Create one reusable selection model.

It must support:

- single object;
- multiple objects;
- text ranges;
- page selections;
- crop rectangle;
- annotation selection.

Selection should expose:

```text
SelectionType
SelectedIds
Bounds
Anchor
TransformHandles
CanCopy
CanCut
CanDelete
```

---

# 71. SNAP / ALIGN

For object editing provide optional:

- snap to page edges;
- snap to center;
- snap to guides;
- alignment guides;
- equal spacing;
- align left/right/center/top/bottom.

Make snap configurable and easy to turn off.

---

# 72. PAGE BOUNDARY & CUT VISUALIZATION

In Cut mode, visually differentiate:

- page boundary;
- crop boundary;
- printable area if relevant;
- selected region;
- outside-crop dimmed area.

Show numeric dimensions when available.

The crop interaction must remain usable at different zoom levels.

---

# 73. NON-DESTRUCTIVE PREVIEW

For expensive or risky operations, use preview first:

```text
User changes crop
       ↓
Preview state
       ↓
Apply
       ↓
Command committed
```

If the user taps Cancel, the document remains unchanged.

---

# 74. AUTO-SAVE VS SOURCE FILE SAFETY

Do not autosave directly over the original source unless the user explicitly enables a setting that allows this behavior.

Default:

- autosave recovery session separately;
- explicit `Ctrl+S` writes the intended source/destination.

---

# 75. CONFLICT HANDLING

If another process modifies the file externally:

Detect where practical and show:

```text
This file was changed outside PDF Everything.

[Reload] [Keep My Changes] [Save As…]
```

Never silently overwrite external changes.

---

# 76. READ-ONLY FILES

If a document is read-only:

- show read-only state;
- editing commands disabled or clearly redirected to Save As;
- do not let the user think edits are permanent when they cannot be saved.

---

# 77. DOCUMENT INFO PANEL

Display:

- page count;
- file size;
- page size;
- PDF version;
- encrypted/password state;
- permissions;
- title/author;
- fonts if available;
- image count if available.

Add:

`Open file location`

Desktop.

Android:

`Share`

where applicable.

---

# 78. QUICK ACTIONS

Expose more than 20 genuinely useful conveniences. Minimum set:

1. Recent files
2. Reopen last page
3. Remember zoom
4. Drag & drop
5. Default PDF association
6. Ctrl+O
7. Ctrl+S
8. Ctrl+Shift+S
9. Ctrl+P
10. Ctrl+C
11. Ctrl+X
12. Ctrl+V
13. Undo/redo
14. Search
15. Fit width
16. Fit page
17. Continuous view
18. Two-page view
19. Page thumbnails
20. Reorder pages
21. Delete pages
22. Duplicate pages
23. Rotate pages
24. Extract pages
25. Split document
26. Merge PDFs
27. Cut/Crop workspace
28. Content trim
29. Image insertion
30. Text insertion
31. Annotation tools
32. Metadata editor
33. Compression
34. Recovery after crash
35. Native print
36. Android share/open
37. Windows Open With/default-app support
38. Password PDF handling
39. Read-only detection
40. Save validation

---

# 79. DOCUMENT ACTIONS SEARCH

Add a command palette / action search on desktop, e.g.:

`Ctrl+Shift+P`

Examples:

```text
Crop page
Split document
Merge PDFs
Print
Fit width
Insert page
Rotate page
Compress PDF
Open settings
```

This is optional for the first visible release but the command system should support it from the start.

---

# 80. ONBOARDING

Do not create a long tutorial.

First launch:

```text
PDF Everything

Open a PDF to get started.

[Open PDF]
[Merge PDFs]
[Create Blank PDF]
```

Small contextual tips may appear the first time the user enters Cut mode or annotation mode.

Store dismissed tips locally.

---

# 81. BLANK DOCUMENT

If supported, create a blank PDF with selectable page size:

- A4;
- A5;
- Letter;
- Legal;
- custom dimensions.

Allow:

- background color;
- portrait/landscape.

The blank document must use the same document model as imported PDFs.

---

# 82. EXTERNAL LINKS

When a PDF contains a hyperlink:

- recognize it;
- show link cursor/visual cue;
- activate only on clear click/tap;
- allow opening externally;
- optionally show confirmation for untrusted destinations.

Do not automatically navigate away while the user is selecting text.

---

# 83. DIAGNOSTICS

Include a safe diagnostic report generator for bug reports.

Report should contain:

- app version;
- platform;
- architecture;
- engine version;
- feature capability summary;
- recent operation categories;
- non-sensitive performance metrics.

Do not include document contents by default.

---

# 84. LOGGING

Create structured logs.

Levels:

```text
TRACE
DEBUG
INFO
WARN
ERROR
```

Production logs should avoid sensitive document data.

Provide a support export option that redacts paths where appropriate.

---

# 85. CRASH REPORTING

Architecture should allow future crash reporting, but do not require a cloud service for local functionality.

Any crash reporting provider must be isolated and optional.

---

# 86. BUILD PIPELINE

Create reproducible commands such as:

```bash
flutter pub get
flutter analyze
flutter test
flutter build windows --release
flutter build apk --release
```

Adapt to the selected stack.

Add CI stages:

```text
format
lint
unit-test
integration-test
build-windows
build-android
artifact-check
```

---

# 87. STATIC QUALITY GATES

Before release:

- no analyzer errors;
- no test failures;
- no known critical memory leak;
- no placeholder UI;
- no fake buttons;
- no unhandled `TODO` for core functionality;
- no accidental debug banners;
- no dev servers required for runtime;
- no external network required for core PDF functionality;
- no secrets in source control.

---

# 88. RELEASE CHECKLIST

## Product

- [ ] Open PDF
- [ ] View PDF
- [ ] Search
- [ ] Edit text where supported
- [ ] Add text
- [ ] Insert image
- [ ] Copy/cut/paste
- [ ] Undo/redo
- [ ] Annotations
- [ ] Page organizer
- [ ] Cut workspace
- [ ] Crop
- [ ] Split
- [ ] Extract
- [ ] Merge
- [ ] Save
- [ ] Save As
- [ ] Print
- [ ] Export
- [ ] Metadata
- [ ] Recovery
- [ ] Recent files
- [ ] Password PDFs
- [ ] File association
- [ ] Android open/share

## Windows

- [ ] Installer
- [ ] Uninstaller
- [ ] PDF association
- [ ] Open With
- [ ] drag/drop
- [ ] native print
- [ ] keyboard shortcuts

## Android

- [ ] APK
- [ ] file picker
- [ ] incoming PDF intent
- [ ] share flow
- [ ] touch editing
- [ ] pinch zoom
- [ ] back behavior
- [ ] low-memory behavior

---

# 89. IMPORTANT: DO NOT BUILD A WEB APP WRAPPED IN A SHELL

The application must feel native enough for a desktop productivity application.

Do not:

- require localhost servers;
- require a browser to be installed beyond OS requirements;
- create a web page that happens to be installed as an `.exe`;
- use an `<iframe>` for the main PDF editing experience;
- depend on an online viewer for core functionality.

The core workflow must be local.

---

# 90. IMPORTANT: DO NOT RASTERIZE EVERYTHING

A common bad implementation is:

```text
PDF → PNG image → edit image → export image/PDF
```

Do not use this as the default architecture.

That destroys:

- searchable text;
- vector graphics;
- selectable text;
- document semantics;
- form fields;
- annotation structure;
- quality.

Rasterization may exist as an explicit export/conversion feature only.

---

# 91. IMPORTANT: PDF IS NOT WORD

The UX can resemble Word, but the engine must respect the PDF format.

Do not promise:

“every PDF behaves exactly like Word.”

Instead:

- preserve PDF structure;
- edit supported objects directly;
- use controlled overlays/replacement when direct editing is unsafe;
- communicate limitations.

This honesty is part of the product quality.

---

# 92. PRODUCT-LEVEL MICROINTERACTIONS

Keep animations subtle and functional.

Examples:

- page selection gently highlights;
- crop handles respond immediately;
- save indicator changes state smoothly;
- toolbar tooltip appears after a sensible delay;
- page insertion shows a clear positional indicator;
- drag reorder shows insertion line;
- destructive action requires confirmation when appropriate;
- undo briefly shows the restored action.

Do not prioritize flashy motion over responsiveness.

---

# 93. EMPTY / LOADING / ERROR STATES

Every major panel must have:

### Empty state

Useful action, not empty decoration.

### Loading state

Progressive and cancellable.

### Error state

Actionable message + recovery option.

### Unsupported state

Explain why and show alternatives if available.

---

# 94. USER FLOW — OPEN → EDIT → CUT → SAVE

The primary golden path must feel effortless:

```text
Open PDF
   ↓
Render first page quickly
   ↓
User selects Edit
   ↓
Selects text/object
   ↓
Makes change
   ↓
Ctrl+Z / Ctrl+Y works
   ↓
User enters CUT
   ↓
Adjusts crop using handles
   ↓
Preview updates instantly
   ↓
Apply
   ↓
Save
   ↓
PDF validation
   ↓
“Saved” state
   ↓
Reopen file
   ↓
Changes are still present
```

This exact flow must be manually tested before declaring the product ready.

---

# 95. USER FLOW — PRINT

```text
Open PDF
   ↓
Ctrl+P
   ↓
Native print flow
   ↓
Choose pages
   ↓
Choose printer
   ↓
Print
```

Test that the actual printer receives the expected pages and orientation.

---

# 96. USER FLOW — WINDOWS DOUBLE CLICK

```text
Install PDF Everything
   ↓
Make default (user choice)
   ↓
Double-click file.pdf
   ↓
OS launches PDF Everything
   ↓
App receives path
   ↓
Document opens
```

This must work after reboot and with the app already open.

---

# 97. USER FLOW — ANDROID OPEN

```text
Download PDF
   ↓
Open with / Share
   ↓
PDF Everything
   ↓
Receive content URI
   ↓
Open safely
   ↓
Edit / Cut / Save As / Share
```

Do not assume every Android document provider gives a normal filesystem path.

---

# 98. PRODUCT NAVIGATION PRINCIPLE

Every operation should follow:

```text
Discover → Preview → Apply → Undo if needed → Save
```

For destructive operations:

```text
Select → Confirm → Execute → Validation
```

---

# 99. PRIORITY TIERS

## P0 — MUST WORK FOR V1

- PDF opening
- PDF rendering
- page navigation
- zoom
- search
- selection
- copy/paste
- text insertion/editing where supported
- image insertion
- annotations
- page organizer
- crop/cut workspace
- split
- extract
- merge
- save/save as
- undo/redo
- native print
- Windows PDF association
- Android document opening
- APK
- Windows EXE installer
- error handling
- recovery

## P1 — STRONGLY RECOMMENDED

- metadata editor
- compression
- forms
- bookmarks editing
- action palette
- OCR
- advanced alignment/snap
- presentation mode

## P2 — FUTURE

- digital signatures
- redaction certifications/audit workflows
- batch processing
- cloud connectors
- collaboration
- plugins
- macros

Do not let P2 work destabilize P0.

---

# 100. BATCH OPERATIONS — OPTIONAL V1.1

Architecture should support batch workflows later:

```text
100 PDFs
  ↓
Apply crop preset
  ↓
Optimize
  ↓
Rename
  ↓
Export
```

Do not expose a broken batch UI in V1 merely to claim a feature.

---

# 101. FUTURE PLUGIN ARCHITECTURE

Potential future plugins:

```text
OCR
Digital Signature
Office Conversion
Cloud Storage
Batch Tools
AI Assistance
```

Core document engine must not depend on these.

---

# 102. AI FEATURES — KEEP OPTIONAL

Do not require AI for basic PDF work.

Future optional capabilities could include:

- summarize document;
- explain selected text;
- find key fields;
- classify document;
- suggest crop bounds.

But the offline PDF editor must remain complete without AI.

---

# 103. CUT AI-ASSISTED AUTO-DETECT — FUTURE

A useful future enhancement:

```text
Detect content bounds
Detect scan edges
Detect document corners
Suggest crop
```

The regular manual Cut tool must work without any AI dependency.

---

# 104. INTERNAL CAPABILITY SYSTEM

Implement capability checks:

```text
Capability
 ├── canEditText
 ├── canEditImages
 ├── canEditAnnotations
 ├── canEditForms
 ├── canEncrypt
 ├── canIncrementalSave
 ├── canPrint
 ├── canOCR
 └── canModifyPageBoxes
```

UI actions must consult capability state.

Do not display controls that cannot work for the current document/engine combination unless they are useful disabled explanations.

---

# 105. DOCUMENT VERSIONING

Every mutation should advance an internal document version.

Example:

```text
Document version 41
Page 3 version 8
Object 3A91 version 2
```

Use versions to reject stale async updates.

---

# 106. THREADING

Never perform heavy parsing/rendering/compression directly on the UI thread.

Heavy work includes:

- PDF parsing;
- rasterization;
- OCR;
- compression;
- merge;
- split;
- validation;
- image decoding.

Use isolates/background workers/native threads appropriate to the platform.

Provide cancellation.

---

# 107. LONG OPERATION UI

For long operations:

```text
Compressing PDF…
██████████░░░░░░░ 61%

[Cancel]
```

If percentage is not reliable, use an indeterminate progress state instead of fake numbers.

---

# 108. SAVE PROGRESS

For large PDFs:

```text
Saving…
Page 411 / 960
```

Only show meaningful progress when it can be measured.

---

# 109. FILE NAMES

Normalize extension handling.

Examples:

- `invoice.pdf`
- `invoice_edited.pdf`
- `invoice_edited (1).pdf`

Avoid accidental:

`invoice.pdf.pdf`

---

# 110. PATH / URI SAFETY

Never assume:

```text
file path == URI
```

Represent separately where necessary.

Desktop:

`FilePath`

Android:

`ContentUri`

Provide a common `DocumentSource` abstraction.

---

# 111. CLIPBOARD SECURITY

Clear internal clipboard references when no longer needed.

Avoid storing sensitive full-document content longer than necessary.

---

# 112. TEMP FILE MANAGEMENT

Use an app-managed temp directory.

Requirements:

- unique IDs;
- cleanup on successful completion;
- crash recovery scanning;
- size limits;
- no collision;
- safe permissions.

---

# 113. RESOURCE DISPOSAL

Every native handle / surface / document / stream must have deterministic disposal.

Especially:

- PDF document handles;
- render surfaces;
- image buffers;
- native streams;
- print resources;
- background workers.

Leak tests are required.

---

# 114. FINAL QUALITY BAR

The application is not “done” merely because:

- it compiles;
- a PDF opens;
- the UI looks nice;
- one test PDF works.

It is done only when the core workflows survive:

- save/reopen;
- undo/redo;
- large documents;
- malformed input;
- password PDFs;
- file association;
- printing;
- Android content URIs;
- low-memory conditions;
- repeated edits;
- app restart;
- crash recovery.

---

# 115. FINAL DEVELOPER INSTRUCTION

Implement the product in phases, but maintain a runnable application after each phase.

### Phase 1 — Foundation

- project bootstrap;
- theme;
- navigation;
- platform layer;
- document model;
- PDF engine adapter;
- file opening.

### Phase 2 — Viewer

- renderer;
- page navigation;
- zoom;
- thumbnails;
- search;
- view modes.

### Phase 3 — Command/Edit system

- selection;
- command dispatcher;
- undo/redo;
- text insertion/editing;
- image operations;
- clipboard;
- shortcuts.

### Phase 4 — CUT / ORGANIZE

- dedicated Cut mode;
- crop;
- trim;
- split;
- extract;
- page organizer;
- merge.

### Phase 5 — Annotation / Forms / Metadata

- annotation tools;
- comments;
- forms where supported;
- metadata;
- bookmarks.

### Phase 6 — Save / Print / Recovery

- save;
- save as;
- validation;
- print;
- autosave/recovery;
- conflict handling.

### Phase 7 — Platform integration

- Windows file association;
- Open With;
- shell launch;
- Android intents;
- native share;
- packaging.

### Phase 8 — Hardening

- performance;
- memory;
- security;
- accessibility;
- localization;
- tests;
- release builds.

---

# 116. WHAT THE AI CODING AGENT MUST RETURN AFTER IMPLEMENTATION

At the end of the build, provide:

## A. Project tree

Show the final meaningful project structure.

## B. Build commands

Exact commands to produce:

```text
Windows EXE/installer
Android APK
```

## C. Environment requirements

List exact:

- SDKs;
- toolchains;
- native build tools;
- required versions.

## D. Known limitations

Be honest.

Example:

```text
Direct editing of text embedded inside certain scanned/image-only PDFs is not supported without OCR.
```

## E. QA report

Show which tests passed.

## F. Packaging status

Confirm that actual artifacts were generated, not merely configured.

---

# 117. DEFINITION OF DONE

The first release is considered **PRODUCT READY** only when all of the following are true:

```text
[✓] App launches on Windows
[✓] App launches on Android
[✓] PDF opens from inside app
[✓] PDF opens through OS file association on Windows
[✓] PDF opens through Android document/share flow
[✓] First page renders correctly
[✓] Multi-page navigation works
[✓] Zoom works
[✓] Search works
[✓] Text selection works where a text layer exists
[✓] Ctrl+C works
[✓] Ctrl+X works where the active selection is editable
[✓] Ctrl+V works
[✓] Ctrl+Z works
[✓] Ctrl+Y works
[✓] Ctrl+P opens real printing
[✓] Cut/Crop workspace works
[✓] Crop can be previewed and applied
[✓] Split works
[✓] Extract works
[✓] Page reorder works
[✓] Page delete works
[✓] Page rotation works
[✓] Merge works
[✓] Save works
[✓] Save As works
[✓] Saved documents reopen correctly
[✓] Recovery works after simulated crash/session interruption
[✓] Android touch operations work
[✓] Windows mouse/keyboard operations work
[✓] Large PDFs do not freeze the UI unnecessarily
[✓] Unsupported features are communicated honestly
[✓] Release build generated
[✓] Windows installer generated
[✓] Android APK generated
```

---

# 118. FINAL COMMAND TO THE AI DEVELOPER

> **Build `PDF Everything` as a genuine local-first PDF productivity application, not a mock.**
>
> Start by selecting and documenting the most appropriate PDF engine and its legal/license implications. Put it behind an engine adapter. Build the document model and command system before polishing the UI. Make the viewer fast and stable. Then implement true editing for supported PDF structures, a first-class `CUT` workspace with crop/trim/split/extract capabilities, real copy/cut/paste behavior, real keyboard shortcuts, real native printing, robust save/recovery, Windows PDF file association, Android document-intent handling, and production packaging.
>
> The interface should visually and behaviorally resemble a serious Word/Acrobat-class productivity application: familiar hierarchy, dense but readable toolbars, responsive document canvas, thumbnails, inspectors, contextual controls, and strong keyboard/touch support.
>
> **Do not hide unfinished functionality behind polished visuals.** A smaller set of correctly implemented features is better than 100 fake controls.
>
> **Do not silently rasterize documents.** Preserve PDF structure whenever possible.
>
> **Do not silently overwrite user files.** Use transactional safe-save and validation.
>
> **Do not make the app dependent on the internet for basic PDF work.**
>
> **Do not force PDF Everything to become the default application without the user's explicit choice.**
>
> **Do not claim “supports every PDF” unless the engine and tests prove it.**
>
> **Prioritize correctness, data safety, responsiveness, recoverability, and real functionality above visual gimmicks.**
>
> Finally, test the complete golden paths:
>
> `Double-click PDF → Open → Edit → Ctrl+C/X/V → Ctrl+Z/Y → Cut → Crop → Save → Reopen → Ctrl+P`
>
> and
>
> `Android file/share → Open → Touch edit → Cut → Save/Export → Share`.
>
> These two flows are the core acceptance tests for the product.

---

# 119. CURRENT PLATFORM NOTE

The preferred Flutter approach is based on its current supported deployment model: Flutter officially supports Android and Windows deployment, including Windows x64/Arm64 on supported Windows versions. Keep the implementation aligned with the current stable SDK rather than hardcoding stale version assumptions. citeturn562434search0turn562434search1

Windows default-app registration should follow the OS's user-driven default-app/file-association model rather than repeatedly forcing ownership from inside the application. citeturn562434search2

---

# 120. ONE-LINE PRODUCT DEFINITION

**PDF Everything = a real offline-first PDF editor + viewer + organizer + Cut/Crop workstation + annotator + printer + file manager experience, shipped as a proper Windows application and Android application, with one coherent document engine and no fake functionality.**
